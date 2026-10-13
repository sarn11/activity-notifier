package com.activitynotifier.detection;

import com.activitynotifier.signals.Signal;
import com.activitynotifier.signals.SignalType;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ActivityTrackerTest
{
	private static final long BASE = 2000;

	private final Set<Detector> enabled = EnumSet.noneOf(Detector.class);
	private final Map<Detector, Long> extra = new EnumMap<>(Detector.class);
	private Duration reminderInterval;
	private int weaponTicks;
	private Instant now;
	private ActivityTracker tracker;

	@Before
	public void setUp()
	{
		enabled.clear();
		enabled.add(Detector.ANIMATION);
		extra.clear();
		reminderInterval = null;
		weaponTicks = 4;
		now = Instant.parse("2026-10-12T12:00:00Z");
		tracker = new ActivityTracker(
			d -> ActivityTracker.effectiveDelayMs(d, BASE, extra.getOrDefault(d, 0L), weaponTicks),
			enabled::contains,
			() -> reminderInterval);
	}

	private void advance(long ms)
	{
		now = now.plusMillis(ms);
	}

	private Optional<Signal> see(Detector detector, String name)
	{
		return tracker.observe(detector, new Activity(name, detector), now);
	}

	private Optional<Signal> tick()
	{
		return tracker.evaluate(now);
	}

	/**
	 * Observes once, then ticks every 600ms until a signal arrives; returns it.
	 */
	private Signal goInactive(Detector detector, String name)
	{
		see(detector, name);
		for (int i = 0; i < 100; i++)
		{
			advance(600);
			Optional<Signal> signal = tick();
			if (signal.isPresent())
			{
				return signal.get();
			}
		}
		throw new AssertionError("never went inactive");
	}

	// region US1: inactive

	@Test
	public void firstObservationIsSilent()
	{
		assertFalse(see(Detector.ANIMATION, "Woodcutting").isPresent());
		assertEquals(ActivityTracker.State.ACTIVE, tracker.getState());
	}

	@Test
	public void noSignalWhenNeverActive()
	{
		advance(60_000);
		assertFalse(tick().isPresent());
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
	}

	@Test
	public void inactiveExactlyOnceAfterEffectiveDelay()
	{
		see(Detector.ANIMATION, "Woodcutting");
		advance(BASE - 1);
		assertFalse(tick().isPresent());

		advance(1);
		Optional<Signal> signal = tick();
		assertTrue(signal.isPresent());
		assertEquals(SignalType.INACTIVE, signal.get().getType());
		assertEquals("Woodcutting", signal.get().getActivity());

		advance(60_000);
		assertFalse("no repeat while inactive", tick().isPresent());
	}

	@Test
	public void shortPauseEmitsNothing()
	{
		for (int i = 0; i < 20; i++)
		{
			see(Detector.ANIMATION, "Woodcutting");
			advance(1800);
			assertFalse(tick().isPresent());
		}
	}

	@Test
	public void extraDelayIsAdded()
	{
		extra.put(Detector.ANIMATION, 3000L);
		see(Detector.ANIMATION, "Fishing");
		advance(BASE + 2999);
		assertFalse(tick().isPresent());
		advance(1);
		assertTrue(tick().isPresent());
	}

	@Test
	public void disabledDetectorIsIgnored()
	{
		assertFalse(see(Detector.MOVEMENT, "Movement").isPresent());
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
	}

	@Test
	public void staysActiveWhileAnyDetectorIsLive()
	{
		enabled.add(Detector.XP_GAIN);
		extra.put(Detector.XP_GAIN, 3000L);
		see(Detector.ANIMATION, "Agility");
		see(Detector.XP_GAIN, "Agility");

		advance(BASE + 100);
		assertFalse("XP gain is still live", tick().isPresent());

		advance(3000);
		Optional<Signal> signal = tick();
		assertTrue(signal.isPresent());
		assertEquals(SignalType.INACTIVE, signal.get().getType());
	}

	@Test
	public void inactiveNamesMostRecentlyExpiredDetector()
	{
		enabled.add(Detector.INTERACTION);
		see(Detector.INTERACTION, "Combat");
		advance(500);
		see(Detector.ANIMATION, "Woodcutting");
		advance(BASE + 600);
		assertEquals("Woodcutting", tick().get().getActivity());
	}

	@Test
	public void resetIsSilent()
	{
		goInactive(Detector.ANIMATION, "Woodcutting");
		tracker.reset();
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
		advance(60_000);
		assertFalse(tick().isPresent());
	}

	@Test
	public void noRemindersWhenDisabled()
	{
		goInactive(Detector.ANIMATION, "Woodcutting");
		advance(600_000);
		assertFalse(tick().isPresent());
	}

	@Test
	public void oneReminderPerInterval()
	{
		reminderInterval = Duration.ofSeconds(60);
		goInactive(Detector.ANIMATION, "Woodcutting");

		advance(59_000);
		assertFalse(tick().isPresent());
		advance(1_000);
		Optional<Signal> reminder = tick();
		assertTrue(reminder.isPresent());
		assertEquals(SignalType.STILL_INACTIVE, reminder.get().getType());
		assertEquals("Woodcutting", reminder.get().getActivity());
		assertEquals(Duration.ofSeconds(60), reminder.get().getDuration());

		advance(600);
		assertFalse(tick().isPresent());
		advance(59_400);
		assertTrue(tick().isPresent());
	}

	// endregion

	// region Analysis I1: turning detectors off never causes Inactive

	@Test
	public void turningOffOnlyLiveDetectorDoesNotEmitInactive()
	{
		enabled.add(Detector.XP_GAIN);
		see(Detector.ANIMATION, "Woodcutting");
		enabled.remove(Detector.ANIMATION);

		advance(60_000);
		assertFalse(tick().isPresent());
		assertEquals(ActivityTracker.State.ACTIVE, tracker.getState());

		// a remaining enabled detector is then seen and expires: normal Inactive
		see(Detector.XP_GAIN, "Woodcutting");
		advance(BASE + 600);
		assertEquals(SignalType.INACTIVE, tick().get().getType());
	}

	@Test
	public void turningOffAllDetectorsWhileActiveIsSilent()
	{
		see(Detector.ANIMATION, "Woodcutting");
		enabled.clear();
		assertFalse(tick().isPresent());
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
	}

	@Test
	public void turningOffAllDetectorsWhileInactiveClears()
	{
		goInactive(Detector.ANIMATION, "Woodcutting");
		enabled.clear();
		Optional<Signal> signal = tick();
		assertTrue(signal.isPresent());
		assertEquals(SignalType.ACTIVE, signal.get().getType());
		assertEquals(ActivityTracker.CLEARED_DETECTION_OFF, signal.get().getFixedDetail());
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
	}

	// endregion

	// region US2: active

	@Test
	public void resumeEmitsActiveOnceWithDuration()
	{
		goInactive(Detector.ANIMATION, "Woodcutting");
		advance(83_000);

		Optional<Signal> signal = see(Detector.ANIMATION, "Mining");
		assertTrue(signal.isPresent());
		assertEquals(SignalType.ACTIVE, signal.get().getType());
		assertEquals("Mining", signal.get().getActivity());
		assertEquals(Duration.ofSeconds(83), signal.get().getDuration());

		assertFalse(see(Detector.ANIMATION, "Mining").isPresent());
	}

	@Test
	public void noActiveWithoutPrecedingInactive()
	{
		see(Detector.ANIMATION, "Woodcutting");
		advance(1000);
		assertFalse(see(Detector.ANIMATION, "Mining").isPresent());
	}

	@Test
	public void signalsStrictlyAlternateWhileFlapping()
	{
		SignalType expected = SignalType.INACTIVE;
		for (int cycle = 0; cycle < 10; cycle++)
		{
			Signal inactive = goInactive(Detector.ANIMATION, "Woodcutting");
			assertEquals(expected, inactive.getType());
			advance(300);
			Signal active = see(Detector.ANIMATION, "Woodcutting").get();
			assertEquals(SignalType.ACTIVE, active.getType());
		}
	}

	@Test
	public void clearWhileInactiveEmitsClearedActive()
	{
		goInactive(Detector.ANIMATION, "Woodcutting");
		Optional<Signal> signal = tracker.clear("Detection paused");
		assertTrue(signal.isPresent());
		assertEquals(SignalType.ACTIVE, signal.get().getType());
		assertEquals("Detection paused", signal.get().getFixedDetail());
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
	}

	@Test
	public void clearWhileActiveOrNeutralIsSilent()
	{
		assertFalse(tracker.clear("Plugin stopped").isPresent());
		see(Detector.ANIMATION, "Woodcutting");
		assertFalse(tracker.clear("Plugin stopped").isPresent());
		assertEquals(ActivityTracker.State.NEUTRAL, tracker.getState());
	}

	// endregion

	// region US4: per-activity tuning

	@Test
	public void specificDetectorUsesItsOwnExtraDelay()
	{
		enabled.clear();
		enabled.add(Detector.FISHING);
		enabled.add(Detector.WOODCUTTING);
		extra.put(Detector.FISHING, 3000L);

		see(Detector.FISHING, "Fishing");
		advance(BASE + 2999);
		assertFalse(tick().isPresent());
		advance(1);
		assertEquals("Fishing", tick().get().getActivity());

		see(Detector.WOODCUTTING, "Woodcutting");
		advance(BASE);
		Optional<Signal> signal = tick();
		assertTrue("woodcutting keeps the base delay", signal.isPresent());
		assertEquals(SignalType.INACTIVE, signal.get().getType());
	}

	@Test
	public void combatDelayAddsWeaponInterval()
	{
		assertEquals(BASE + 6 * 600, ActivityTracker.effectiveDelayMs(Detector.COMBAT, BASE, 0, 6));
		assertEquals(BASE + 500 + 4 * 600, ActivityTracker.effectiveDelayMs(Detector.COMBAT, BASE, 500, 4));
		assertEquals("only combat gets the weapon term", BASE, ActivityTracker.effectiveDelayMs(Detector.ANIMATION, BASE, 0, 6));
	}

	@Test
	public void slowWeaponGapIsNotInactivity()
	{
		enabled.clear();
		enabled.add(Detector.COMBAT);
		weaponTicks = 7;

		// a 7-tick weapon: hits land 4.2s apart, longer than the base delay alone
		for (int i = 0; i < 10; i++)
		{
			see(Detector.COMBAT, "Combat");
			advance(7 * 600);
			assertFalse(tick().isPresent());
		}

		advance(BASE);
		assertEquals(SignalType.INACTIVE, tick().get().getType());
	}

	@Test
	public void specificAndGeneralStayActiveUntilBothExpire()
	{
		enabled.add(Detector.FISHING);
		extra.put(Detector.FISHING, 3000L);
		see(Detector.FISHING, "Fishing");
		see(Detector.ANIMATION, "Fishing");

		advance(BASE + 100);
		assertFalse("fishing is still live", tick().isPresent());

		advance(2900);
		Optional<Signal> signal = tick();
		assertTrue(signal.isPresent());
		assertEquals("Fishing", signal.get().getActivity());
	}

	@Test
	public void simultaneousExpiryNamesSpecificDetector()
	{
		enabled.add(Detector.MAGIC_SKILLING);
		see(Detector.MAGIC_SKILLING, "Magic skilling");
		see(Detector.ANIMATION, "Magic");
		advance(BASE);
		assertEquals("Magic skilling", tick().get().getActivity());
	}

	@Test
	public void movementIgnoredWhenDisabled()
	{
		see(Detector.ANIMATION, "Woodcutting");
		for (int i = 0; i < 4; i++)
		{
			advance(600);
			assertFalse(see(Detector.MOVEMENT, "Movement").isPresent());
		}
		// movement did not extend activity: inactive at the animation's expiry
		assertEquals(SignalType.INACTIVE, tick().get().getType());
	}

	@Test
	public void movementKeepsActiveWhenEnabled()
	{
		enabled.add(Detector.MOVEMENT);
		extra.put(Detector.MOVEMENT, 2000L);
		see(Detector.ANIMATION, "Woodcutting");
		for (int i = 0; i < 10; i++)
		{
			advance(600);
			see(Detector.MOVEMENT, "Movement");
			assertFalse(tick().isPresent());
		}

		advance(BASE + 2000);
		assertEquals("Movement", tick().get().getActivity());
	}

	// endregion

	// region Loading screens

	@Test
	public void suspendedTimeDoesNotCountTowardsInactivity()
	{
		see(Detector.ANIMATION, "Woodcutting");
		advance(1000);
		tracker.suspend(now);
		advance(10_000);
		assertFalse("no evaluation while suspended", tick().isPresent());
		tracker.resume(now);

		advance(BASE - 1000 - 1);
		assertFalse(tick().isPresent());
		advance(1);
		assertTrue(tick().isPresent());
	}

	@Test
	public void inactiveStateSurvivesLoading()
	{
		goInactive(Detector.ANIMATION, "Woodcutting");
		tracker.suspend(now);
		advance(5000);
		tracker.resume(now);
		assertEquals(ActivityTracker.State.INACTIVE, tracker.getState());
		assertEquals(SignalType.ACTIVE, see(Detector.ANIMATION, "Woodcutting").get().getType());
	}

	// endregion
}
