package com.activitynotifier.damage;

import com.activitynotifier.signals.Signal;
import com.activitynotifier.signals.SignalFormatter;
import com.activitynotifier.signals.SignalType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.runelite.api.HitsplatID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DamageAlertTrackerTest
{
	private static final DamageSettings ANY_HIT = new DamageSettings(1, true, Duration.ZERO);

	private final DamageAlertTracker tracker = new DamageAlertTracker();

	private static Instant at(long millis)
	{
		return Instant.EPOCH.plusMillis(millis);
	}

	private static String text(Signal signal)
	{
		return SignalFormatter.format(signal, "{damage}");
	}

	@Test
	public void directHitSendsOneSignal()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 7);

		List<Signal> signals = tracker.flush(ANY_HIT, 92, at(0));
		assertEquals(1, signals.size());
		assertEquals(SignalType.DAMAGE_TAKEN, signals.get(0).getType());
		assertEquals("Activity Notifier: Damage taken - 7", text(signals.get(0)));
	}

	@Test
	public void zeroHitsAndHealingAreIgnored()
	{
		tracker.record(HitsplatID.BLOCK_ME, 0);
		tracker.record(HitsplatID.HEAL, 5);
		tracker.record(HitsplatID.DAMAGE_ME, 0);

		assertTrue(tracker.flush(ANY_HIT, 92, at(0)).isEmpty());
	}

	@Test
	public void hitsInOneTickSendInRecordOrder()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 3);
		tracker.record(HitsplatID.DAMAGE_MAX_ME, 9);

		List<Signal> signals = tracker.flush(ANY_HIT, 80, at(0));
		assertEquals(2, signals.size());
		assertEquals("Activity Notifier: Damage taken - 3", text(signals.get(0)));
		assertEquals("Activity Notifier: Damage taken - 9", text(signals.get(1)));
	}

	@Test
	public void flushClearsPendingHits()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 4);
		tracker.flush(ANY_HIT, 90, at(0));

		assertTrue(tracker.flush(ANY_HIT, 90, at(600)).isEmpty());
	}

	@Test
	public void discardPendingDropsHits()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 4);
		tracker.discardPending();

		assertTrue(tracker.flush(ANY_HIT, 90, at(0)).isEmpty());
	}

	@Test
	public void minimumIsInclusive()
	{
		DamageSettings minimum15 = new DamageSettings(15, true, Duration.ZERO);

		tracker.record(HitsplatID.DAMAGE_ME, 14);
		assertTrue(tracker.flush(minimum15, 80, at(0)).isEmpty());

		tracker.record(HitsplatID.DAMAGE_ME, 15);
		assertEquals(1, tracker.flush(minimum15, 65, at(600)).size());
	}

	@Test
	public void hitsAreNeverAddedTogether()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 8);
		tracker.record(HitsplatID.DAMAGE_ME, 8);

		assertTrue(tracker.flush(new DamageSettings(15, true, Duration.ZERO), 80, at(0)).isEmpty());
	}

	@Test
	public void eachFlushUsesItsOwnMinimum()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 6);
		assertTrue(tracker.flush(new DamageSettings(15, true, Duration.ZERO), 80, at(0)).isEmpty());

		tracker.record(HitsplatID.DAMAGE_ME, 6);
		assertEquals(1, tracker.flush(new DamageSettings(5, true, Duration.ZERO), 74, at(600)).size());
	}

	@Test
	public void thresholdTokenIsTheMinimum()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 20);

		Signal signal = tracker.flush(new DamageSettings(15, true, Duration.ZERO), 50, at(0)).get(0);
		assertEquals("Activity Notifier: Damage taken - 20 (minimum 15)",
			SignalFormatter.format(signal, "{damage} (minimum {threshold})"));
	}

	@Test
	public void damageOverTimeCountsByDefault()
	{
		tracker.record(HitsplatID.POISON, 4);

		assertEquals(1, tracker.flush(ANY_HIT, 60, at(0)).size());
	}

	@Test
	public void damageOverTimeCanBeExcluded()
	{
		tracker.record(HitsplatID.POISON, 4);
		tracker.record(HitsplatID.VENOM, 6);
		tracker.record(HitsplatID.DAMAGE_ME, 3);

		List<Signal> signals = tracker.flush(new DamageSettings(1, false, Duration.ZERO), 60, at(0));
		assertEquals(1, signals.size());
		assertEquals("Activity Notifier: Damage taken - 3", text(signals.get(0)));
	}

	@Test
	public void cooldownSuppressesUntilItPasses()
	{
		DamageSettings cooldown10 = new DamageSettings(1, true, Duration.ofSeconds(10));

		tracker.record(HitsplatID.DAMAGE_ME, 5);
		assertEquals(1, tracker.flush(cooldown10, 90, at(0)).size());

		tracker.record(HitsplatID.DAMAGE_ME, 5);
		assertTrue(tracker.flush(cooldown10, 85, at(5_000)).isEmpty());

		tracker.record(HitsplatID.DAMAGE_ME, 5);
		assertTrue(tracker.flush(cooldown10, 80, at(9_999)).isEmpty());

		tracker.record(HitsplatID.DAMAGE_ME, 5);
		assertEquals("the boundary is inclusive", 1, tracker.flush(cooldown10, 75, at(10_000)).size());
	}

	@Test
	public void cooldownKeepsOnlyTheFirstHitOfATick()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 5);
		tracker.record(HitsplatID.DAMAGE_ME, 9);

		List<Signal> signals = tracker.flush(new DamageSettings(1, true, Duration.ofSeconds(10)), 80, at(0));
		assertEquals(1, signals.size());
		assertEquals("Activity Notifier: Damage taken - 5", text(signals.get(0)));
	}

	@Test
	public void hitsDuringCooldownAreDroppedNotQueued()
	{
		DamageSettings cooldown10 = new DamageSettings(1, true, Duration.ofSeconds(10));
		tracker.record(HitsplatID.DAMAGE_ME, 5);
		tracker.flush(cooldown10, 90, at(0));

		tracker.record(HitsplatID.DAMAGE_ME, 7);
		tracker.flush(cooldown10, 83, at(3_000));

		assertTrue(tracker.flush(cooldown10, 83, at(11_000)).isEmpty());
	}

	@Test
	public void noCooldownSendsEveryHit()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 5);
		tracker.record(HitsplatID.DAMAGE_ME, 9);

		assertEquals(2, tracker.flush(ANY_HIT, 80, at(0)).size());
	}

	@Test
	public void changedCooldownIsMeasuredFromTheLastSignal()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 5);
		tracker.flush(new DamageSettings(1, true, Duration.ofSeconds(30)), 90, at(0));

		tracker.record(HitsplatID.DAMAGE_ME, 5);
		assertEquals(1, tracker.flush(new DamageSettings(1, true, Duration.ofSeconds(5)), 85, at(6_000)).size());
	}

	@Test
	public void discardPendingKeepsTheCooldown()
	{
		DamageSettings cooldown10 = new DamageSettings(1, true, Duration.ofSeconds(10));
		tracker.record(HitsplatID.DAMAGE_ME, 5);
		tracker.flush(cooldown10, 90, at(0));
		tracker.discardPending();

		tracker.record(HitsplatID.DAMAGE_ME, 5);
		assertTrue(tracker.flush(cooldown10, 85, at(5_000)).isEmpty());
	}

	@Test
	public void hitpointsComeFromTheFlush()
	{
		tracker.record(HitsplatID.DAMAGE_ME, 12);

		Signal signal = tracker.flush(ANY_HIT, 38, at(0)).get(0);
		assertEquals("Activity Notifier: Damage taken - 12 damage, 38 HP left",
			SignalFormatter.format(signal, "{damage} damage, {hitpoints} HP left"));
	}
}
