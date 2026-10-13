package com.activitynotifier.damage;

import java.time.Duration;
import lombok.Value;

/**
 * The damage taken settings, read from config once per flush so the tracker never touches config.
 */
@Value
public class DamageSettings
{
	/**
	 * Smallest single hit that sends a signal (inclusive).
	 */
	int minimum;

	/**
	 * Whether poison and venom hits count.
	 */
	boolean countDamageOverTime;

	/**
	 * Minimum time between signals. {@link Duration#ZERO} sends one for every qualifying hit.
	 */
	Duration cooldown;
}
