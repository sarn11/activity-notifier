package com.activitynotifier;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Config keys are a permanent contract (see specs/done/001-activity-detection/contracts/config.md,
 * specs/done/002-damage-notifier/contracts/config.md and specs/003-custom-animation-ids/contracts/config.md).
 * Never rename a keyName without a migration.
 */
@ConfigGroup(ActivityNotifierConfig.GROUP)
public interface ActivityNotifierConfig extends Config
{
	String GROUP = "activitynotifier";

	// region Sections

	@ConfigSection(
		name = "General",
		description = "General settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Signals",
		description = "How signals are delivered (e.g. to the Watchdog plugin) and their text",
		position = 10
	)
	String signalsSection = "signals";

	@ConfigSection(
		name = "General detectors",
		description = "Broad activity detectors, each individually toggleable",
		position = 20
	)
	String generalDetectorsSection = "generalDetectors";

	@ConfigSection(
		name = "Specific activities",
		description = "Individually watched activities",
		position = 30
	)
	String specificActivitiesSection = "specificActivities";

	@ConfigSection(
		name = "Extra delays",
		description = "Extra time added to the base delay before a detector counts as stopped",
		position = 40,
		closedByDefault = true
	)
	String delaysSection = "delays";

	@ConfigSection(
		name = "Resource alerts",
		description = "Alerts when a resource crosses a threshold, with a matching recovered signal",
		position = 50,
		closedByDefault = true
	)
	String resourcesSection = "resources";

	@ConfigSection(
		name = "Damage taken",
		description = "Alerts when you take damage",
		position = 55,
		closedByDefault = true
	)
	String damageSection = "damage";

	@ConfigSection(
		name = "Logout warnings",
		description = "Warnings before you are logged out",
		position = 60,
		closedByDefault = true
	)
	String logoutSection = "logout";

	// endregion

	// region General

	@ConfigItem(
		keyName = "showSidePanel",
		name = "Show side panel",
		description = "Show the Activity Notifier button in the sidebar",
		position = 0,
		section = generalSection
	)
	default boolean showSidePanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pauseHotkey",
		name = "Pause hotkey",
		description = "Pauses or resumes detection. Pausing while inactive sends an active signal",
		position = 1,
		section = generalSection
	)
	default Keybind pauseHotkey()
	{
		return Keybind.NOT_SET;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "baseDelay",
		name = "Base delay",
		description = "How long no activity must be seen before you count as inactive, before extra delays",
		position = 2,
		section = generalSection
	)
	default int baseDelay()
	{
		return 2000;
	}

	// endregion

	// region Signals

	@ConfigItem(
		keyName = "chatSignals",
		name = "Chat messages",
		description = "Send signals as filtered game messages in the chat box, hidden when the Game chat filter is set to Filtered (Watchdog: Game Message alert, message type Spam or Any)",
		position = 0,
		section = signalsSection
	)
	default boolean chatSignals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notificationSignals",
		name = "RuneLite notification",
		description = "Send signals as RuneLite notifications (Watchdog: Notification Fired alert)",
		position = 1,
		section = signalsSection
	)
	default Notification notificationSignals()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "reminderEnabled",
		name = "Inactive reminders",
		description = "Repeat a 'Still inactive' signal while you stay inactive",
		position = 2,
		section = signalsSection
	)
	default boolean reminderEnabled()
	{
		return false;
	}

	@Range(min = 10)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "reminderInterval",
		name = "Reminder interval",
		description = "Time between 'Still inactive' reminders",
		position = 3,
		section = signalsSection
	)
	default int reminderInterval()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "templateInactive",
		name = "Inactive text",
		description = "Detail after 'Activity Notifier: Inactive - '. Tokens: {activity}",
		position = 4,
		section = signalsSection
	)
	default String templateInactive()
	{
		return "{activity}";
	}

	@ConfigItem(
		keyName = "templateActive",
		name = "Active text",
		description = "Detail after 'Activity Notifier: Active - '. Tokens: {activity}, {duration}",
		position = 5,
		section = signalsSection
	)
	default String templateActive()
	{
		return "{activity} (inactive for {duration})";
	}

	@ConfigItem(
		keyName = "templateReminder",
		name = "Reminder text",
		description = "Detail after 'Activity Notifier: Still inactive - '. Tokens: {activity}, {duration}",
		position = 6,
		section = signalsSection
	)
	default String templateReminder()
	{
		return "{activity} (for {duration})";
	}

	@ConfigItem(
		keyName = "templateResourceAlert",
		name = "Resource alert text",
		description = "Detail for resource alerts. Tokens: {resource}, {value}, {threshold}",
		position = 7,
		section = signalsSection
	)
	default String templateResourceAlert()
	{
		return "{value} (threshold {threshold})";
	}

	@ConfigItem(
		keyName = "templateResourceRecovered",
		name = "Resource recovered text",
		description = "Detail for resource recovered signals. Tokens: {resource}, {value}",
		position = 8,
		section = signalsSection
	)
	default String templateResourceRecovered()
	{
		return "{value}";
	}

	@ConfigItem(
		keyName = "templateIdleLogout",
		name = "Idle logout text",
		description = "Detail for the idle logout warning",
		position = 9,
		section = signalsSection
	)
	default String templateIdleLogout()
	{
		return "About to log out from idling";
	}

	@ConfigItem(
		keyName = "templateSixHour",
		name = "Six hour logout text",
		description = "Detail for the six hour logout warning",
		position = 10,
		section = signalsSection
	)
	default String templateSixHour()
	{
		return "About to reach the six hour limit";
	}

	@ConfigItem(
		keyName = "templateDamageTaken",
		name = "Damage taken text",
		description = "Detail for damage taken signals. Tokens: {damage}, {threshold}, {hitpoints}",
		position = 11,
		section = signalsSection
	)
	default String templateDamageTaken()
	{
		return "{damage}";
	}

	// endregion

	// region General detectors

	@ConfigItem(
		keyName = "detectAnimation",
		name = "Animation",
		description = "Skilling and action animations count as activity",
		position = 0,
		section = generalDetectorsSection
	)
	default boolean detectAnimation()
	{
		return true;
	}

	@ConfigItem(
		keyName = "customAnimationIds",
		name = "Custom animation IDs",
		description = "Comma-separated animation IDs that also count as animation activity, e.g. 5216, 13149. The side panel shows your last animation's ID",
		position = 1,
		section = generalDetectorsSection
	)
	default String customAnimationIds()
	{
		return "";
	}

	@ConfigItem(
		keyName = "detectInteraction",
		name = "Interaction",
		description = "Interacting with NPCs (including combat targets and fishing spots) counts as activity",
		position = 2,
		section = generalDetectorsSection
	)
	default boolean detectInteraction()
	{
		return true;
	}

	@ConfigItem(
		keyName = "detectMovement",
		name = "Movement",
		description = "Walking and running count as activity",
		position = 3,
		section = generalDetectorsSection
	)
	default boolean detectMovement()
	{
		return false;
	}

	@ConfigItem(
		keyName = "detectXpGain",
		name = "XP gain",
		description = "Gaining XP counts as activity",
		position = 4,
		section = generalDetectorsSection
	)
	default boolean detectXpGain()
	{
		return true;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "xpThreshold",
		name = "XP gain threshold",
		description = "Minimum XP in a single drop for the XP gain detector to count it",
		position = 5,
		section = generalDetectorsSection
	)
	default int xpThreshold()
	{
		return 1;
	}

	// endregion

	// region Specific activities

	@ConfigItem(keyName = "detectCooking", name = "Cooking", description = "Watch Cooking", position = 0, section = specificActivitiesSection)
	default boolean detectCooking()
	{
		return false;
	}

	@ConfigItem(keyName = "detectCrafting", name = "Crafting", description = "Watch Crafting", position = 1, section = specificActivitiesSection)
	default boolean detectCrafting()
	{
		return false;
	}

	@ConfigItem(keyName = "detectFishing", name = "Fishing", description = "Watch Fishing", position = 2, section = specificActivitiesSection)
	default boolean detectFishing()
	{
		return false;
	}

	@ConfigItem(keyName = "detectFiremaking", name = "Firemaking", description = "Watch Firemaking", position = 3, section = specificActivitiesSection)
	default boolean detectFiremaking()
	{
		return false;
	}

	@ConfigItem(keyName = "detectFletching", name = "Fletching", description = "Watch Fletching", position = 4, section = specificActivitiesSection)
	default boolean detectFletching()
	{
		return false;
	}

	@ConfigItem(keyName = "detectHerblore", name = "Herblore", description = "Watch Herblore", position = 5, section = specificActivitiesSection)
	default boolean detectHerblore()
	{
		return false;
	}

	@ConfigItem(keyName = "detectMining", name = "Mining", description = "Watch Mining", position = 6, section = specificActivitiesSection)
	default boolean detectMining()
	{
		return false;
	}

	@ConfigItem(keyName = "detectWoodcutting", name = "Woodcutting", description = "Watch Woodcutting", position = 7, section = specificActivitiesSection)
	default boolean detectWoodcutting()
	{
		return false;
	}

	@ConfigItem(keyName = "detectSmithing", name = "Smithing", description = "Watch Smithing", position = 8, section = specificActivitiesSection)
	default boolean detectSmithing()
	{
		return false;
	}

	@ConfigItem(keyName = "detectSailing", name = "Sailing (beta)", description = "Beta: not fully tested yet. Watch Sailing, including steering a ship", position = 9, section = specificActivitiesSection)
	default boolean detectSailing()
	{
		return false;
	}

	@ConfigItem(keyName = "detectCombat", name = "Combat", description = "Watch combat (accounts for weapon attack speed)", position = 10, section = specificActivitiesSection)
	default boolean detectCombat()
	{
		return false;
	}

	@ConfigItem(keyName = "detectMagicSkilling", name = "Magic skilling", description = "Watch skilling spells such as Lunar crafting spells", position = 11, section = specificActivitiesSection)
	default boolean detectMagicSkilling()
	{
		return false;
	}

	// endregion

	// region Extra delays

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayAnimation", name = "Animation", description = "Extra delay for the animation detector", position = 0, section = delaysSection)
	default int delayAnimation()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayInteraction", name = "Interaction", description = "Extra delay for the interaction detector", position = 1, section = delaysSection)
	default int delayInteraction()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayMovement", name = "Movement (grace)", description = "Grace delay after you stop walking or running", position = 2, section = delaysSection)
	default int delayMovement()
	{
		return 2000;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayXpGain", name = "XP gain", description = "Extra delay for the XP gain detector", position = 3, section = delaysSection)
	default int delayXpGain()
	{
		return 3000;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayCooking", name = "Cooking", description = "Extra delay for Cooking", position = 10, section = delaysSection)
	default int delayCooking()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayCrafting", name = "Crafting", description = "Extra delay for Crafting", position = 11, section = delaysSection)
	default int delayCrafting()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayFishing", name = "Fishing", description = "Extra delay for Fishing", position = 12, section = delaysSection)
	default int delayFishing()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayFiremaking", name = "Firemaking", description = "Extra delay for Firemaking", position = 13, section = delaysSection)
	default int delayFiremaking()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayFletching", name = "Fletching", description = "Extra delay for Fletching", position = 14, section = delaysSection)
	default int delayFletching()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayHerblore", name = "Herblore", description = "Extra delay for Herblore", position = 15, section = delaysSection)
	default int delayHerblore()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayMining", name = "Mining", description = "Extra delay for Mining", position = 16, section = delaysSection)
	default int delayMining()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayWoodcutting", name = "Woodcutting", description = "Extra delay for Woodcutting", position = 17, section = delaysSection)
	default int delayWoodcutting()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delaySmithing", name = "Smithing", description = "Extra delay for Smithing", position = 18, section = delaysSection)
	default int delaySmithing()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delaySailing", name = "Sailing (beta)", description = "Beta: not fully tested yet. Extra delay for Sailing", position = 19, section = delaysSection)
	default int delaySailing()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayCombat", name = "Combat", description = "Extra delay for combat, added on top of your weapon's attack interval", position = 20, section = delaysSection)
	default int delayCombat()
	{
		return 0;
	}

	@Range(min = 0)
	@Units(Units.MILLISECONDS)
	@ConfigItem(keyName = "delayMagicSkilling", name = "Magic skilling", description = "Extra delay for magic skilling", position = 21, section = delaysSection)
	default int delayMagicSkilling()
	{
		return 0;
	}

	// endregion

	// region Resource alerts

	@ConfigItem(keyName = "alertHitpoints", name = "Low hitpoints", description = "Alert when hitpoints drop to or below the threshold", position = 0, section = resourcesSection)
	default boolean alertHitpoints()
	{
		return false;
	}

	@Range(min = 0)
	@ConfigItem(keyName = "hitpointsThreshold", name = "Hitpoints threshold", description = "Hitpoints threshold", position = 1, section = resourcesSection)
	default int hitpointsThreshold()
	{
		return 10;
	}

	@ConfigItem(keyName = "alertPrayer", name = "Low prayer", description = "Alert when prayer drops to or below the threshold", position = 2, section = resourcesSection)
	default boolean alertPrayer()
	{
		return false;
	}

	@Range(min = 0)
	@ConfigItem(keyName = "prayerThreshold", name = "Prayer threshold", description = "Prayer threshold", position = 3, section = resourcesSection)
	default int prayerThreshold()
	{
		return 10;
	}

	@ConfigItem(keyName = "alertLowEnergy", name = "Low run energy", description = "Alert when run energy drops to or below the threshold", position = 4, section = resourcesSection)
	default boolean alertLowEnergy()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "lowEnergyThreshold", name = "Low run energy threshold", description = "Low run energy threshold", position = 5, section = resourcesSection)
	default int lowEnergyThreshold()
	{
		return 10;
	}

	@ConfigItem(keyName = "alertHighEnergy", name = "Run energy restored", description = "Alert when run energy rises to or above the threshold", position = 6, section = resourcesSection)
	default boolean alertHighEnergy()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "highEnergyThreshold", name = "Restored run energy threshold", description = "Restored run energy threshold", position = 7, section = resourcesSection)
	default int highEnergyThreshold()
	{
		return 90;
	}

	@ConfigItem(keyName = "alertOxygen", name = "Low oxygen", description = "Alert when oxygen drops to or below the threshold", position = 8, section = resourcesSection)
	default boolean alertOxygen()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "oxygenThreshold", name = "Oxygen threshold", description = "Oxygen threshold", position = 9, section = resourcesSection)
	default int oxygenThreshold()
	{
		return 20;
	}

	@ConfigItem(keyName = "alertSpecial", name = "Special attack restored", description = "Alert when special attack energy rises to or above the threshold", position = 10, section = resourcesSection)
	default boolean alertSpecial()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "specialThreshold", name = "Special attack threshold", description = "Special attack threshold", position = 11, section = resourcesSection)
	default int specialThreshold()
	{
		return 100;
	}

	// endregion

	// region Damage taken

	@ConfigItem(
		keyName = "alertDamage",
		name = "Damage taken",
		description = "Send a signal when you take damage of at least the minimum",
		position = 0,
		section = damageSection
	)
	default boolean alertDamage()
	{
		return false;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "damageThreshold",
		name = "Minimum damage",
		description = "Smallest single hit that sends a signal. Hits are never added together",
		position = 1,
		section = damageSection
	)
	default int damageThreshold()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "damageOverTime",
		name = "Count damage over time",
		description = "Count poison and venom damage",
		position = 2,
		section = damageSection
	)
	default boolean damageOverTime()
	{
		return true;
	}

	@Range(min = 0)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "damageCooldown",
		name = "Cooldown",
		description = "Minimum time between damage signals. 0 sends one for every qualifying hit",
		position = 3,
		section = damageSection
	)
	default int damageCooldown()
	{
		return 0;
	}

	// endregion

	// region Logout warnings

	@ConfigItem(
		keyName = "warnIdleLogout",
		name = "Idle logout warning",
		description = "Warn shortly before the game logs you out for idling",
		position = 0,
		section = logoutSection
	)
	default boolean warnIdleLogout()
	{
		return false;
	}

	@ConfigItem(
		keyName = "warnSixHour",
		name = "Six hour logout warning",
		description = "Warn shortly before the six hour session limit",
		position = 1,
		section = logoutSection
	)
	default boolean warnSixHour()
	{
		return false;
	}

	// endregion
}
