package com.activitynotifier.resources;

import com.activitynotifier.signals.Signal;
import com.activitynotifier.signals.SignalFormatter;
import com.activitynotifier.signals.SignalType;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ResourceAlertTrackerTest
{
	private final ResourceAlertTracker hitpoints = new ResourceAlertTracker(ResourceType.HITPOINTS);
	private final ResourceAlertTracker special = new ResourceAlertTracker(ResourceType.SPECIAL_ATTACK);

	@Test
	public void firstSampleIsSilent()
	{
		assertFalse("below threshold at login", hitpoints.sample(5, 10).isPresent());
		assertEquals(ResourceAlertTracker.State.ALERTED, hitpoints.getState());

		ResourceAlertTracker prayer = new ResourceAlertTracker(ResourceType.PRAYER);
		assertFalse(prayer.sample(50, 10).isPresent());
		assertEquals(ResourceAlertTracker.State.NORMAL, prayer.getState());
	}

	@Test
	public void lowCrossingAlertsOnceThenRecoversOnce()
	{
		hitpoints.sample(50, 10);

		Optional<Signal> alert = hitpoints.sample(10, 10);
		assertTrue("at the threshold counts as low", alert.isPresent());
		assertEquals(SignalType.RESOURCE_ALERT, alert.get().getType());
		assertEquals("Activity Notifier: Low hitpoints - 10 (threshold 10)",
			SignalFormatter.format(alert.get(), "{value} (threshold {threshold})"));

		assertFalse("fluctuating below", hitpoints.sample(7, 10).isPresent());
		assertFalse(hitpoints.sample(9, 10).isPresent());

		Optional<Signal> recovered = hitpoints.sample(34, 10);
		assertTrue(recovered.isPresent());
		assertEquals(SignalType.RESOURCE_RECOVERED, recovered.get().getType());
		assertEquals("Activity Notifier: Hitpoints recovered - 34",
			SignalFormatter.format(recovered.get(), "{value}"));

		assertFalse(hitpoints.sample(40, 10).isPresent());
	}

	@Test
	public void highCrossingIsMirrored()
	{
		special.sample(50, 100);

		Optional<Signal> restored = special.sample(100, 100);
		assertTrue(restored.isPresent());
		assertEquals("Activity Notifier: Special attack restored - 100% (threshold 100%)",
			SignalFormatter.format(restored.get(), "{value} (threshold {threshold})"));
		assertFalse(special.sample(100, 100).isPresent());

		Optional<Signal> used = special.sample(50, 100);
		assertTrue(used.isPresent());
		assertEquals("Activity Notifier: Special attack used - 50%", SignalFormatter.format(used.get(), "{value}"));
		assertFalse(special.sample(60, 100).isPresent());
	}

	@Test
	public void clearWhileAlertedRecoversOnce()
	{
		hitpoints.sample(50, 10);
		hitpoints.sample(5, 10);

		Optional<Signal> cleared = hitpoints.clear("Detection paused");
		assertTrue(cleared.isPresent());
		assertEquals(SignalType.RESOURCE_RECOVERED, cleared.get().getType());
		assertEquals("Activity Notifier: Hitpoints recovered - Detection paused",
			SignalFormatter.format(cleared.get(), "{value}"));
		assertEquals(ResourceAlertTracker.State.UNKNOWN, hitpoints.getState());

		assertFalse(hitpoints.clear("Detection paused").isPresent());
	}

	@Test
	public void clearWhileNormalOrUnknownIsSilent()
	{
		assertFalse(hitpoints.clear("Plugin stopped").isPresent());
		hitpoints.sample(50, 10);
		assertFalse(hitpoints.clear("Plugin stopped").isPresent());
	}

	@Test
	public void turningAlertOffWhileAlertedClears()
	{
		hitpoints.sample(50, 10);
		hitpoints.sample(5, 10);
		assertTrue(hitpoints.clear(ResourceAlertTracker.CLEARED_ALERT_OFF).isPresent());

		// turned back on while still low: silent, like a fresh login
		assertFalse(hitpoints.sample(5, 10).isPresent());
	}

	@Test
	public void resetIsSilent()
	{
		hitpoints.sample(50, 10);
		hitpoints.sample(5, 10);
		hitpoints.reset();
		assertEquals(ResourceAlertTracker.State.UNKNOWN, hitpoints.getState());
		assertFalse(hitpoints.sample(50, 10).isPresent());
	}
}
