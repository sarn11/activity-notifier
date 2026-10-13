package com.activitynotifier.warnings;

import com.activitynotifier.signals.Signal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;

/**
 * Single warnings before the idle logout and the six-hour session limit (FR-017, FR-018, research R9).
 * Thresholds match core Idle Notifier. Has no client dependency. Client thread only.
 */
@Slf4j
public class LogoutWarningTracker
{
	/**
	 * Warn this long before the idle logout: 20 seconds, in client ticks.
	 */
	static final int IDLE_BUFFER_TICKS = 20_000 / Constants.CLIENT_TICK_LENGTH;

	static final Duration SIX_HOUR_WARNING_AFTER = Duration.ofMinutes(340);

	/**
	 * Armed once idle ticks are seen below the warning point, so a warning needs a period of input first.
	 */
	private boolean idleArmed;

	private Instant loginAt;
	private boolean sessionArmed;

	/**
	 * @param idleTicks client ticks since the last mouse or keyboard input
	 * @param idleTimeoutTicks client ticks of idling before the game logs out
	 */
	public Optional<Signal> checkIdle(int idleTicks, int idleTimeoutTicks)
	{
		if (idleTicks < idleTimeoutTicks - IDLE_BUFFER_TICKS)
		{
			idleArmed = true;
			return Optional.empty();
		}

		if (!idleArmed)
		{
			return Optional.empty();
		}

		idleArmed = false;
		log.debug("Idle logout warning ({} of {} idle ticks)", idleTicks, idleTimeoutTicks);
		return Optional.of(Signal.idleLogout());
	}

	/**
	 * Starts the six-hour session timer.
	 */
	public void onLogin(Instant now)
	{
		loginAt = now;
		sessionArmed = true;
	}

	public Optional<Signal> checkSession(Instant now)
	{
		if (!sessionArmed || Duration.between(loginAt, now).compareTo(SIX_HOUR_WARNING_AFTER) < 0)
		{
			return Optional.empty();
		}

		sessionArmed = false;
		log.debug("Six hour logout warning (logged in at {})", loginAt);
		return Optional.of(Signal.sixHour());
	}

	/**
	 * Disarms both warnings (logout).
	 */
	public void reset()
	{
		idleArmed = false;
		loginAt = null;
		sessionArmed = false;
	}
}
