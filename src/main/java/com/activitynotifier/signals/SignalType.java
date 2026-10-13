package com.activitynotifier.signals;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Signal types and their fixed keywords. Keywords are part of the public contract with Watchdog
 * (see specs/done/001-activity-detection/contracts/signals.md and
 * specs/done/002-damage-notifier/contracts/signals.md); changing one is a breaking change.
 */
@Getter
@RequiredArgsConstructor
public enum SignalType
{
	INACTIVE("Inactive"),
	ACTIVE("Active"),
	STILL_INACTIVE("Still inactive"),
	/**
	 * Keyword is per resource and supplied by the {@link Signal}.
	 */
	RESOURCE_ALERT(null),
	/**
	 * Keyword is per resource and supplied by the {@link Signal}.
	 */
	RESOURCE_RECOVERED(null),
	IDLE_LOGOUT("Idle logout soon"),
	SIX_HOUR("Six hour logout soon"),
	DAMAGE_TAKEN("Damage taken");

	private final String keyword;
}
