package com.activitynotifier.warnings;

import com.activitynotifier.signals.Signal;
import com.activitynotifier.signals.SignalType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LogoutWarningTrackerTest
{
	private static final int TIMEOUT = 15_000;
	private static final int WARN_AT = TIMEOUT - LogoutWarningTracker.IDLE_BUFFER_TICKS;
	private static final Instant LOGIN = Instant.parse("2026-10-12T12:00:00Z");

	private final LogoutWarningTracker tracker = new LogoutWarningTracker();

	@Test
	public void bufferIsTwentySeconds()
	{
		assertEquals(1000, LogoutWarningTracker.IDLE_BUFFER_TICKS);
	}

	@Test
	public void idleWarnsOnceThenRearmsAfterInput()
	{
		assertFalse(tracker.checkIdle(0, TIMEOUT).isPresent());
		assertFalse(tracker.checkIdle(WARN_AT - 1, TIMEOUT).isPresent());

		Optional<Signal> warning = tracker.checkIdle(WARN_AT, TIMEOUT);
		assertTrue(warning.isPresent());
		assertEquals(SignalType.IDLE_LOGOUT, warning.get().getType());
		assertFalse("only once", tracker.checkIdle(WARN_AT + 500, TIMEOUT).isPresent());

		// input resets the client's idle ticks
		assertFalse(tracker.checkIdle(3, TIMEOUT).isPresent());
		assertTrue(tracker.checkIdle(WARN_AT, TIMEOUT).isPresent());
	}

	@Test
	public void idleDoesNotWarnWithoutSeeingActivityFirst()
	{
		// e.g. enabled mid-session while already idle past the warning point
		assertFalse(tracker.checkIdle(WARN_AT + 10, TIMEOUT).isPresent());
	}

	@Test
	public void sixHourWarnsOnceAt340Minutes()
	{
		tracker.onLogin(LOGIN);
		assertFalse(tracker.checkSession(LOGIN.plus(Duration.ofMinutes(339))).isPresent());

		Optional<Signal> warning = tracker.checkSession(LOGIN.plus(Duration.ofMinutes(340)));
		assertTrue(warning.isPresent());
		assertEquals(SignalType.SIX_HOUR, warning.get().getType());
		assertFalse(tracker.checkSession(LOGIN.plus(Duration.ofMinutes(345))).isPresent());
	}

	@Test
	public void sixHourNeedsALogin()
	{
		assertFalse(tracker.checkSession(LOGIN.plus(Duration.ofHours(10))).isPresent());
	}

	@Test
	public void nextLoginRearmsSixHour()
	{
		tracker.onLogin(LOGIN);
		assertTrue(tracker.checkSession(LOGIN.plus(Duration.ofMinutes(340))).isPresent());

		Instant relogin = LOGIN.plus(Duration.ofHours(6));
		tracker.onLogin(relogin);
		assertFalse(tracker.checkSession(relogin.plus(Duration.ofMinutes(1))).isPresent());
		assertTrue(tracker.checkSession(relogin.plus(Duration.ofMinutes(340))).isPresent());
	}

	@Test
	public void resetDisarmsBoth()
	{
		tracker.onLogin(LOGIN);
		tracker.checkIdle(0, TIMEOUT);
		tracker.reset();

		assertFalse(tracker.checkIdle(WARN_AT, TIMEOUT).isPresent());
		assertFalse(tracker.checkSession(LOGIN.plus(Duration.ofMinutes(340))).isPresent());
	}
}
