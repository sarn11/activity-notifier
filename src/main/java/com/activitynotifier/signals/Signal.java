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
}
