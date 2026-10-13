package com.activitynotifier.damage;

import net.runelite.api.HitsplatID;

/**
 * What a hitsplat on the local player counts as for the damage taken alert
 * (specs/done/002-damage-notifier/research.md R2). gameval has no hitsplat class, so
 * {@link HitsplatID} is the constant source.
 */
public enum HitKind
{
	/**
	 * A hit that takes hitpoints.
	 */
	DIRECT,
	/**
	 * Poison and venom, which can be excluded in settings.
	 */
	DAMAGE_OVER_TIME,
	/**
	 * Never counts: 0 hits, healing, other damage over time, drains and NPC shield/armour bars.
	 * Unknown types fall here too, so new game splats stay quiet.
	 */
	NONE;

	public static HitKind of(int hitsplatType)
	{
		switch (hitsplatType)
		{
			case HitsplatID.DAMAGE_ME:
			case HitsplatID.DAMAGE_MAX_ME:
			// the hit is already known to be on the local player, so any tint is damage to them
			case HitsplatID.DAMAGE_OTHER:
				return DIRECT;
			case HitsplatID.POISON:
			case HitsplatID.VENOM:
				return DAMAGE_OVER_TIME;
			default:
				return NONE;
		}
	}
}
