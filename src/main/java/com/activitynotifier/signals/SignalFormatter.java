package com.activitynotifier.signals;

import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Renders signal text: {@code "Activity Notifier: <Keyword> - <detail>"}.
 * The head is fixed so Watchdog patterns keep working when users customise the detail template.
 */
public final class SignalFormatter
{
	public static final String PREFIX = "Activity Notifier: ";
	private static final String SEPARATOR = " - ";

	private SignalFormatter()
	{
	}

	public static String head(String keyword)
	{
		return PREFIX + keyword;
	}

	public static String format(Signal signal, @Nullable String template)
	{
		String detail = signal.getFixedDetail() != null
			? signal.getFixedDetail()
			: render(template, signal);

		String head = head(signal.getKeyword());
		return detail.isEmpty() ? head : head + SEPARATOR + detail;
	}

	static String render(@Nullable String template, Signal signal)
	{
		if (template == null)
		{
			return "";
		}

		String out = template.trim();
		out = replace(out, "{activity}", signal.getActivity());
		out = replace(out, "{duration}", signal.getDuration() == null ? null : formatDuration(signal.getDuration()));
		out = replace(out, "{resource}", signal.getResource());
		out = replace(out, "{value}", signal.getValue());
		out = replace(out, "{threshold}", signal.getThreshold());
		return out;
	}

	private static String replace(String text, String token, @Nullable String value)
	{
		return value == null ? text : text.replace(token, value);
	}

	/**
	 * Formats as {@code 45s}, {@code 1m 23s} or {@code 1h 2m}.
	 */
	public static String formatDuration(Duration duration)
	{
		long totalSeconds = Math.max(0, duration.getSeconds());
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		if (hours > 0)
		{
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0)
		{
			return minutes + "m " + seconds + "s";
		}
		return seconds + "s";
	}
}
