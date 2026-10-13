package com.activitynotifier.resources;

import com.activitynotifier.ActivityNotifierConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

/**
 * Resources that can raise threshold alerts (FR-023). Keywords are part of the public signal contract
 * (contracts/signals.md) and key names must match {@link ActivityNotifierConfig} (contracts/config.md).
 */
@Getter
@RequiredArgsConstructor
public enum ResourceType
{
	HITPOINTS(Direction.LOW, "Hitpoints", Unit.POINTS, "alertHitpoints", "hitpointsThreshold", 10,
		"Low hitpoints", "Hitpoints recovered"),
	PRAYER(Direction.LOW, "Prayer", Unit.POINTS, "alertPrayer", "prayerThreshold", 10,
		"Low prayer", "Prayer recovered"),
	RUN_ENERGY_LOW(Direction.LOW, "Run energy", Unit.PERCENT, "alertLowEnergy", "lowEnergyThreshold", 10,
		"Low run energy", "Run energy recovered"),
	RUN_ENERGY_HIGH(Direction.HIGH, "Run energy", Unit.PERCENT, "alertHighEnergy", "highEnergyThreshold", 90,
		"Run energy restored", "Run energy used"),
	OXYGEN(Direction.LOW, "Oxygen", Unit.PERCENT, "alertOxygen", "oxygenThreshold", 20,
		"Low oxygen", "Oxygen recovered"),
	SPECIAL_ATTACK(Direction.HIGH, "Special attack", Unit.PERCENT, "alertSpecial", "specialThreshold", 100,
		"Special attack restored", "Special attack used");

	public enum Direction
	{
		/**
		 * Alert when the value drops to or below the threshold.
		 */
		LOW,
		/**
		 * Alert when the value rises to or above the threshold ("restored").
		 */
		HIGH
	}

	public enum Unit
	{
		POINTS,
		PERCENT
	}

	private final Direction direction;
	private final String displayName;
	private final Unit unit;
	private final String enabledKey;
	private final String thresholdKey;
	private final int defaultThreshold;
	private final String alertKeyword;
	private final String recoveredKeyword;

	/**
	 * All resource alerts are off by default (FR-026).
	 */
	public boolean isEnabled(ConfigManager configManager)
	{
		Boolean value = configManager.getConfiguration(ActivityNotifierConfig.GROUP, enabledKey, Boolean.class);
		return value != null && value;
	}

	public int threshold(ConfigManager configManager)
	{
		Integer value = configManager.getConfiguration(ActivityNotifierConfig.GROUP, thresholdKey, Integer.class);
		return value == null ? defaultThreshold : Math.max(0, value);
	}

	/**
	 * Whether a value is on the alert side of the threshold.
	 */
	public boolean isAlerting(int value, int threshold)
	{
		return direction == Direction.LOW ? value <= threshold : value >= threshold;
	}

	/**
	 * Formats a value with its unit, e.g. {@code 9} or {@code 85%}.
	 */
	public String format(int value)
	{
		return unit == Unit.PERCENT ? value + "%" : Integer.toString(value);
	}
}
