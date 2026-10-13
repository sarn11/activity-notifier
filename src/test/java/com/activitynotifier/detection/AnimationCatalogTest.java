package com.activitynotifier.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

public class AnimationCatalogTest
{
	@Test
	public void catalogIsPopulated()
	{
		assertTrue(AnimationCatalog.activities().size() > 250);
		assertEquals(16, AnimationCatalog.HELM_ANIMATIONS.size());
	}

	@Test
	public void everyEntryHasAName()
	{
		for (String name : AnimationCatalog.activities().values())
		{
			assertFalse(name == null || name.trim().isEmpty());
		}
	}

	@Test
	public void noConflictingDuplicates()
	{
		assertTrue(AnimationCatalog.CONFLICTS.toString(), AnimationCatalog.CONFLICTS.isEmpty());
	}

	@Test
	public void knownAnimations()
	{
		assertEquals("Woodcutting", AnimationCatalog.activityFor(AnimationID.HUMAN_WOODCUTTING_RUNE_AXE));
		assertEquals("Mining", AnimationCatalog.activityFor(AnimationID.HUMAN_MINING_DRAGON_PICKAXE));
		assertEquals("Fishing", AnimationCatalog.activityFor(AnimationID.HUMAN_HARPOON));
		assertEquals("Magic", AnimationCatalog.activityFor(AnimationID.LUNAR_HUMAN_MAGIC_SUMMON2));
		assertNull("idle is not an activity", AnimationCatalog.activityFor(-1));
	}

	@Test
	public void helmAnimationsAreNotGeneralActivities()
	{
		for (int helm : AnimationCatalog.HELM_ANIMATIONS)
		{
			assertTrue(AnimationCatalog.isHelmAnimation(helm));
			assertNull(AnimationCatalog.activityFor(helm));
		}
	}
}
