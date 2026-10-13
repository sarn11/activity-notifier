package com.activitynotifier.detection;

import com.activitynotifier.signals.Signal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Pure Active/Inactive state machine (data-model.md). Has no client dependency; all times are passed in.
 * Must only be used from one thread (the client thread).
 */
public class ActivityTracker
{
	/**
	 * Base delay + per-detector extra delay (+ weapon interval for combat), in milliseconds.
	 */
	@FunctionalInterface
	public interface DelayProvider
	{
		long effectiveDelayMs(Detector detector);
	}

	@FunctionalInterface
	public interface EnabledProvider
	{
		boolean isEnabled(Detector detector);
	}

	/**
	 * Reminder interval, or null when reminders are disabled.
	 */
	@FunctionalInterface
	public interface ReminderProvider
	{
		@Nullable
		Duration reminderInterval();
	}

	public enum State
	{
		NEUTRAL,
		ACTIVE,
		INACTIVE
	}

	public static final String CLEARED_DETECTION_OFF = "Detection off";

	private final DelayProvider delays;
	private final EnabledProvider enabled;
	private final ReminderProvider reminders;

	private final Map<Detector, Instant> lastSeen = new EnumMap<>(Detector.class);
	private final Map<Detector, Activity> lastActivity = new EnumMap<>(Detector.class);

	@Getter
	private State state = State.NEUTRAL;
	private Instant inactiveSince;
	private Instant lastReminderAt;
	private String inactiveActivity;
	private Instant suspendedAt;

	public ActivityTracker(DelayProvider delays, EnabledProvider enabled, ReminderProvider reminders)
	{
		this.delays = delays;
		this.enabled = enabled;
		this.reminders = reminders;
	}

	/**
	 * Records that a detector saw activity. Returns an Active signal when this resumes from Inactive.
	 */
	public Optional<Signal> observe(Detector detector, Activity activity, Instant now)
	{
		if (!enabled.isEnabled(detector))
		{
			return Optional.empty();
		}

		lastSeen.put(detector, now);
		lastActivity.put(detector, activity);

		switch (state)
		{
			case INACTIVE:
				Duration inactiveFor = Duration.between(inactiveSince, now);
				enterActive();
				return Optional.of(Signal.active(activity.getName(), inactiveFor));
			case NEUTRAL:
				enterActive();
				return Optional.empty();
			default:
				return Optional.empty();
		}
	}

	/**
	 * Called once per game tick. Returns an Inactive, Still inactive, or cleared signal when one is due.
	 */
	public Optional<Signal> evaluate(Instant now)
	{
		if (suspendedAt != null)
		{
			return Optional.empty();
		}

		// Turning a detector off forgets it, so a settings change can never cause Inactive (analysis I1)
		lastSeen.keySet().removeIf(d -> !enabled.isEnabled(d));
		lastActivity.keySet().retainAll(lastSeen.keySet());

		if (!anyDetectorEnabled())
		{
			State before = state;
			reset();
			return before == State.INACTIVE
				? Optional.of(Signal.cleared(CLEARED_DETECTION_OFF))
				: Optional.empty();
		}

		if (state == State.ACTIVE)
		{
			return evaluateActive(now);
		}

		if (state == State.INACTIVE)
		{
			return evaluateReminder(now);
		}

		return Optional.empty();
	}

	private Optional<Signal> evaluateActive(Instant now)
	{
		if (lastSeen.isEmpty())
		{
			// Only seen by detectors that are now off: wait for an enabled detector to be seen and expire
			return Optional.empty();
		}

		Detector mostRecentlyExpired = null;
		Instant latestExpiry = null;
		for (Map.Entry<Detector, Instant> entry : lastSeen.entrySet())
		{
			Instant expiry = entry.getValue().plusMillis(Math.max(0, delays.effectiveDelayMs(entry.getKey())));
			if (now.isBefore(expiry))
			{
				// still live
				return Optional.empty();
			}
			if (latestExpiry == null || expiry.isAfter(latestExpiry))
			{
				latestExpiry = expiry;
				mostRecentlyExpired = entry.getKey();
			}
		}

		state = State.INACTIVE;
		inactiveSince = now;
		lastReminderAt = now;
		inactiveActivity = lastActivity.get(mostRecentlyExpired).getName();
		return Optional.of(Signal.inactive(inactiveActivity));
	}

	private Optional<Signal> evaluateReminder(Instant now)
	{
		Duration interval = reminders.reminderInterval();
		if (interval == null || interval.isZero() || interval.isNegative())
		{
			return Optional.empty();
		}

		if (Duration.between(lastReminderAt, now).compareTo(interval) >= 0)
		{
			lastReminderAt = now;
			return Optional.of(Signal.stillInactive(inactiveActivity, Duration.between(inactiveSince, now)));
		}
		return Optional.empty();
	}

	/**
	 * Returns to neutral. Emits an Active signal with the given detail if an Inactive signal is outstanding
	 * (FR-012: pause, plugin shutdown).
	 */
	public Optional<Signal> clear(String detail)
	{
		State before = state;
		reset();
		return before == State.INACTIVE ? Optional.of(Signal.cleared(detail)) : Optional.empty();
	}

	/**
	 * Discards all state silently (logout, world hop).
	 */
	public void reset()
	{
		state = State.NEUTRAL;
		lastSeen.clear();
		lastActivity.clear();
		inactiveSince = null;
		lastReminderAt = null;
		inactiveActivity = null;
		suspendedAt = null;
	}

	/**
	 * Stops evaluation without losing state (loading screens).
	 */
	public void suspend(Instant now)
	{
		if (suspendedAt == null)
		{
			suspendedAt = now;
		}
	}

	/**
	 * Resumes after {@link #suspend}, shifting all timestamps by the suspended time so no detector
	 * expires because of the loading screen.
	 */
	public void resume(Instant now)
	{
		if (suspendedAt == null)
		{
			return;
		}

		Duration suspended = Duration.between(suspendedAt, now);
		suspendedAt = null;
		if (suspended.isNegative() || suspended.isZero())
		{
			return;
		}

		lastSeen.replaceAll((d, t) -> t.plus(suspended));
		if (inactiveSince != null)
		{
			inactiveSince = inactiveSince.plus(suspended);
		}
		if (lastReminderAt != null)
		{
			lastReminderAt = lastReminderAt.plus(suspended);
		}
	}

	private void enterActive()
	{
		state = State.ACTIVE;
		inactiveSince = null;
		lastReminderAt = null;
		inactiveActivity = null;
	}

	private boolean anyDetectorEnabled()
	{
		for (Detector detector : Detector.values())
		{
			if (enabled.isEnabled(detector))
			{
				return true;
			}
		}
		return false;
	}
}
