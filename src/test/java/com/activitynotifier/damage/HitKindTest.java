package com.activitynotifier.damage;

import net.runelite.api.HitsplatID;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HitKindTest
{
	@Test
	public void directDamage()
	{
		assertEquals(HitKind.DIRECT, HitKind.of(HitsplatID.DAMAGE_ME));
		assertEquals(HitKind.DIRECT, HitKind.of(HitsplatID.DAMAGE_MAX_ME));
		assertEquals(HitKind.DIRECT, HitKind.of(HitsplatID.DAMAGE_OTHER));
	}

	@Test
	public void poisonAndVenomAreDamageOverTime()
	{
		assertEquals(HitKind.DAMAGE_OVER_TIME, HitKind.of(HitsplatID.POISON));
		assertEquals(HitKind.DAMAGE_OVER_TIME, HitKind.of(HitsplatID.VENOM));
	}

	@Test
	public void everythingElseIsNone()
	{
		int[] none = {
			// only poison and venom count as damage over time (analysis U1)
			HitsplatID.DISEASE, HitsplatID.BURN, HitsplatID.BLEED,
			// 0 hits and healing
			HitsplatID.BLOCK_ME, HitsplatID.BLOCK_OTHER, HitsplatID.HEAL, HitsplatID.DISEASE_BLOCKED,
			// not hitpoints
			HitsplatID.CYAN_UP, HitsplatID.CYAN_DOWN, HitsplatID.PRAYER_DRAIN, HitsplatID.SANITY_DRAIN,
			HitsplatID.SANITY_RESTORE, HitsplatID.CORRUPTION, HitsplatID.DOOM,
			// NPC shield, armour and poise bars
			HitsplatID.DAMAGE_ME_ORANGE, HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_ME_POISE,
			// unknown
			-1,
		};
		for (int type : none)
		{
			assertEquals("hitsplat type " + type, HitKind.NONE, HitKind.of(type));
		}
	}
}
