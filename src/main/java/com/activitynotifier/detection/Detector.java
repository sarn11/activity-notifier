package com.activitynotifier.detection;

import com.activitynotifier.ActivityNotifierConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

/**
 * A source of activity that can be toggled individually. Key names must match
 * {@link ActivityNotifierConfig} (contracts/config.md).
 */
@Getter
@RequiredArgsConstructor
public enum Detector
{
	ANIMATION(Group.GENERAL, "Animation", "detectAnimation", "delayAnimation", true, 0),
	INTERACTION(Group.GENERAL, "Interaction", "detectInteraction", "delayInteraction", true, 0),
	MOVEMENT(Group.GENERAL, "Movement", "detectMovement", "delayMovement", false, 2000),
	XP_GAIN(Group.GENERAL, "XP gain", "detectXpGain", "delayXpGain", true, 3000),

	COOKING(Group.SPECIFIC, "Cooking", "detectCooking", "delayCooking", false, 0),
	CRAFTING(Group.SPECIFIC, "Crafting", "detectCrafting", "delayCrafting", false, 0),
	FISHING(Group.SPECIFIC, "Fishing", "detectFishing", "delayFishing", false, 0),
	FIREMAKING(Group.SPECIFIC, "Firemaking", "detectFiremaking", "delayFiremaking", false, 0),
	FLETCHING(Group.SPECIFIC, "Fletching", "detectFletching", "delayFletching", false, 0),
	HERBLORE(Group.SPECIFIC, "Herblore", "detectHerblore", "delayHerblore", false, 0),
	MINING(Group.SPECIFIC, "Mining", "detectMining", "delayMining", false, 0),
	WOODCUTTING(Group.SPECIFIC, "Woodcutting", "detectWoodcutting", "delayWoodcutting", false, 0),
	SMITHING(Group.SPECIFIC, "Smithing", "detectSmithing", "delaySmithing", false, 0),
	SAILING(Group.SPECIFIC, "Sailing", "detectSailing", "delaySailing", false, 0),
	COMBAT(Group.SPECIFIC, "Combat", "detectCombat", "delayCombat", false, 0),
	MAGIC_SKILLING(Group.SPECIFIC, "Magic skilling", "detectMagicSkilling", "delayMagicSkilling", false, 0);

	public enum Group
	{
		GENERAL,
		SPECIFIC
	}

	private final Group group;
	private final String displayName;
	private final String enabledKey;
	private final String delayKey;
	private final boolean defaultEnabled;
	private final int defaultDelayMs;

	public boolean isEnabled(ConfigManager configManager)
	{
		Boolean value = configManager.getConfiguration(ActivityNotifierConfig.GROUP, enabledKey, Boolean.class);
		return value == null ? defaultEnabled : value;
	}

	public int extraDelayMs(ConfigManager configManager)
	{
		Integer value = configManager.getConfiguration(ActivityNotifierConfig.GROUP, delayKey, Integer.class);
		return value == null ? defaultDelayMs : Math.max(0, value);
	}
}
