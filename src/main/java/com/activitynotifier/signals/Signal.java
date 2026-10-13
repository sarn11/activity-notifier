package com.activitynotifier.signals;

import java.time.Duration;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * A message emitted on a state transition.
 */
@Value
@Builder
public class Signal
{
	SignalType type;

	/**
	 * Keyword that follows the "Activity Notifier: " prefix.
	 */
	String keyword;

	@Nullable
	String activity;

	@Nullable
	String resource;

	/**
	 * Current resource value, already formatted with its unit (e.g. "9" or "85%").
	 */
	@Nullable
	String value;

	/**
	 * Resource threshold, already formatted with its unit.
	 */
	@Nullable
	String threshold;

	@Nullable
	Duration duration;

	/**
	 * Damage of the hit that triggered a damage taken signal.
	 */
	@Nullable
	String damage;

	/**
	 * The player's hitpoints when a damage taken signal is sent.
	 */
	@Nullable
	String hitpoints;

	/**
	 * When set, used as the detail instead of the user's template (e.g. cleared signals).
	 */
	@Nullable
	String fixedDetail;

	public static Signal inactive(String activity)
	{
		return builder().type(SignalType.INACTIVE).keyword(SignalType.INACTIVE.getKeyword())
			.activity(activity).build();
	}

	public static Signal active(String activity, Duration inactiveFor)
	{
		return builder().type(SignalType.ACTIVE).keyword(SignalType.ACTIVE.getKeyword())
			.activity(activity).duration(inactiveFor).build();
	}

	public static Signal stillInactive(String activity, Duration inactiveFor)
	{
		return builder().type(SignalType.STILL_INACTIVE).keyword(SignalType.STILL_INACTIVE.getKeyword())
			.activity(activity).duration(inactiveFor).build();
	}

	/**
	 * An active signal sent to clear an outstanding inactive signal, e.g. when detection is paused.
	 */
	public static Signal cleared(String detail)
	{
		return builder().type(SignalType.ACTIVE).keyword(SignalType.ACTIVE.getKeyword())
			.fixedDetail(detail).build();
	}

	public static Signal idleLogout()
	{
		return builder().type(SignalType.IDLE_LOGOUT).keyword(SignalType.IDLE_LOGOUT.getKeyword()).build();
	}

	public static Signal sixHour()
	{
		return builder().type(SignalType.SIX_HOUR).keyword(SignalType.SIX_HOUR.getKeyword()).build();
	}

	public static Signal resourceAlert(String keyword, String resource, String value, String threshold)
	{
		return builder().type(SignalType.RESOURCE_ALERT).keyword(keyword)
			.resource(resource).value(value).threshold(threshold).build();
	}

	public static Signal resourceRecovered(String keyword, String resource, String value)
	{
		return builder().type(SignalType.RESOURCE_RECOVERED).keyword(keyword)
			.resource(resource).value(value).build();
	}

	public static Signal damageTaken(int damage, int minimum, int hitpoints)
	{
		return builder().type(SignalType.DAMAGE_TAKEN).keyword(SignalType.DAMAGE_TAKEN.getKeyword())
			.damage(String.valueOf(damage)).threshold(String.valueOf(minimum))
			.hitpoints(String.valueOf(hitpoints)).build();
	}

	/**
	 * A recovered signal sent to clear an outstanding resource alert, e.g. when the alert is turned off.
	 */
	public static Signal resourceCleared(String keyword, String resource, String detail)
	{
		return builder().type(SignalType.RESOURCE_RECOVERED).keyword(keyword)
			.resource(resource).fixedDetail(detail).build();
	}
}
