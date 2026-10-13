package com.activitynotifier.detection;

import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Caches the equipped weapon's attack interval in game ticks, for the combat delay (FR-005, research R7).
 * Client thread only.
 */
@Slf4j
public class WeaponSpeed
{
	/**
	 * Unarmed attack interval, also used when a weapon's speed is unknown.
	 */
	public static final int DEFAULT_TICKS = 4;

	private final ItemManager itemManager;

	@Getter
	private int intervalTicks = DEFAULT_TICKS;

	@Inject
	WeaponSpeed(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * Re-reads the weapon slot of the worn-equipment container.
	 */
	public void update(@Nullable ItemContainer worn)
	{
		int ticks = DEFAULT_TICKS;
		Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon != null && weapon.getId() > 0)
		{
			ItemStats stats = itemManager.getItemStats(weapon.getId());
			ItemEquipmentStats equipment = stats == null ? null : stats.getEquipment();
			if (equipment != null && equipment.getAspeed() > 0)
			{
				ticks = equipment.getAspeed();
			}
		}

		if (ticks != intervalTicks)
		{
			log.debug("Weapon attack interval: {} -> {} ticks", intervalTicks, ticks);
			intervalTicks = ticks;
		}
	}

	public void reset()
	{
		intervalTicks = DEFAULT_TICKS;
	}
}
