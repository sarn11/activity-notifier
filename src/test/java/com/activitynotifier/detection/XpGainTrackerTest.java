package com.activitynotifier.detection;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;

public class XpGainTrackerTest
{
	private XpGainTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new XpGainTracker();
	}

	private static Map<Skill, Integer> xp(Skill skill, int value)
	{
		Map<Skill, Integer> map = new EnumMap<>(Skill.class);
		map.put(skill, value);
		return map;
	}

	@Test
	public void silentBeforeSeed()
	{
		// the login burst of StatChanged events arrives before the cache is seeded
		assertFalse(tracker.onXp(Skill.AGILITY, 1000, 1).isPresent());
		assertFalse(tracker.onXp(Skill.AGILITY, 2000, 1).isPresent());
	}

	@Test
	public void unchangedXpAfterSeedIsSilent()
	{
		tracker.seed(xp(Skill.AGILITY, 1000));
		assertFalse(tracker.onXp(Skill.AGILITY, 1000, 1).isPresent());
	}

	@Test
	public void firstSampleOfUnseededSkillIsSilent()
	{
		tracker.seed(xp(Skill.AGILITY, 1000));
		assertFalse(tracker.onXp(Skill.MINING, 5000, 1).isPresent());
		assertEquals(Optional.of(Skill.MINING), tracker.onXp(Skill.MINING, 5035, 1));
	}

	@Test
	public void gainAtOrAboveThresholdReturnsSkill()
	{
		tracker.seed(xp(Skill.AGILITY, 1000));
		assertEquals(Optional.of(Skill.AGILITY), tracker.onXp(Skill.AGILITY, 1010, 10));
		assertEquals(Optional.of(Skill.AGILITY), tracker.onXp(Skill.AGILITY, 1100, 10));
	}

	@Test
	public void gainBelowThresholdIsIgnored()
	{
		tracker.seed(xp(Skill.HITPOINTS, 1000));
		assertFalse(tracker.onXp(Skill.HITPOINTS, 1004, 5).isPresent());
		// the cache still advances, so small drops never add up
		assertFalse(tracker.onXp(Skill.HITPOINTS, 1008, 5).isPresent());
	}

	@Test
	public void resetRequiresReseeding()
	{
		tracker.seed(xp(Skill.AGILITY, 1000));
		tracker.reset();
		assertFalse(tracker.onXp(Skill.AGILITY, 1500, 1).isPresent());

		tracker.seed(xp(Skill.AGILITY, 1500));
		assertEquals(Optional.of(Skill.AGILITY), tracker.onXp(Skill.AGILITY, 1600, 1));
	}
}
