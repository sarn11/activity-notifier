package com.activitynotifier.resources;

import com.activitynotifier.signals.Signal;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Hysteresis state machine for one resource (data-model.md, FR-023 to FR-025): one alert per crossing
 * and one recovered signal when the value crosses back. Has no client dependency. Client thread only.
 */
@Slf4j
public class ResourceAlertTracker
{
	public enum State
	{
		UNKNOWN,
		NORMAL,
		ALERTED
	}

	public static final String CLEARED_ALERT_OFF = "Alert off";

	@Getter
	private final ResourceType type;

	@Getter
	private State state = State.UNKNOWN;

	public ResourceAlertTracker(ResourceType type)
	{
		this.type = type;
	}

	/**
	 * Records the current value. The first sample is silent, so logging in below a threshold never alerts.
	 */
	public Optional<Signal> sample(int value, int threshold)
	{
		boolean alerting = type.isAlerting(value, threshold);
		switch (state)
		{
			case UNKNOWN:
				state = alerting ? State.ALERTED : State.NORMAL;
				log.debug("{}: UNKNOWN -> {} (value {}, threshold {})", type, state, value, threshold);
				return Optional.empty();
			case NORMAL:
				if (alerting)
				{
					state = State.ALERTED;
					log.debug("{}: NORMAL -> ALERTED (value {}, threshold {})", type, value, threshold);
					return Optional.of(Signal.resourceAlert(type.getAlertKeyword(), type.getDisplayName(),
						type.format(value), type.format(threshold)));
				}
				return Optional.empty();
			case ALERTED:
				if (!alerting)
				{
					state = State.NORMAL;
					log.debug("{}: ALERTED -> NORMAL (value {}, threshold {})", type, value, threshold);
					return Optional.of(Signal.resourceRecovered(type.getRecoveredKeyword(), type.getDisplayName(),
						type.format(value)));
				}
				return Optional.empty();
			default:
				return Optional.empty();
		}
	}

	/**
	 * Returns to UNKNOWN. Emits a recovered signal with the given detail if an alert is outstanding
	 * (alert turned off, pause, plugin shutdown).
	 */
	public Optional<Signal> clear(String detail)
	{
		State before = state;
		state = State.UNKNOWN;
		if (before != State.ALERTED)
		{
			return Optional.empty();
		}
		log.debug("{}: ALERTED -> UNKNOWN ({})", type, detail);
		return Optional.of(Signal.resourceCleared(type.getRecoveredKeyword(), type.getDisplayName(), detail));
	}

	/**
	 * Discards state silently (logout, world hop).
	 */
	public void reset()
	{
		state = State.UNKNOWN;
	}
}
