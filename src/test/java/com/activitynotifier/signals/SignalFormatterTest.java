package com.activitynotifier.signals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class SignalFormatterTest
{
	/**
	 * Resource keywords from contracts/signals.md. Replaced by ResourceType once it exists (T035/T038).
	 */
	private static final List<String> RESOURCE_KEYWORDS = Arrays.asList(
		"Low hitpoints", "Hitpoints recovered",
		"Low prayer", "Prayer recovered",
		"Low run energy", "Run energy recovered",
		"Run energy restored", "Run energy used",
		"Low oxygen", "Oxygen recovered",
		"Special attack restored", "Special attack used"
	);

	private static List<String> allHeads()
	{
		List<String> heads = new ArrayList<>();
		for (SignalType type : SignalType.values())
		{
			if (type.getKeyword() != null)
			{
				heads.add(SignalFormatter.head(type.getKeyword()));
			}
		}
		for (String keyword : RESOURCE_KEYWORDS)
		{
			heads.add(SignalFormatter.head(keyword));
		}
		return heads;
	}

	@Test
	public void headsContainNoGlobSpecialCharacters()
	{
		for (String head : allHeads())
		{
			for (char c : "[]{},!\\".toCharArray())
			{
				assertFalse("'" + head + "' contains '" + c + "'", head.indexOf(c) >= 0);
			}
		}
	}

	@Test
	public void noHeadIsSubstringOfAnother()
	{
		List<String> heads = allHeads();
		for (String a : heads)
		{
			for (String b : heads)
			{
				if (!a.equals(b))
				{
					assertFalse("'" + a + "' is contained in '" + b + "'",
						b.toLowerCase(Locale.ROOT).contains(a.toLowerCase(Locale.ROOT)));
				}
			}
		}
	}

	@Test
	public void formatsInactiveWithDefaultTemplate()
	{
		assertEquals("Activity Notifier: Inactive - Woodcutting",
			SignalFormatter.format(Signal.inactive("Woodcutting"), "{activity}"));
	}

	@Test
	public void formatsActiveWithDuration()
	{
		assertEquals("Activity Notifier: Active - Woodcutting (inactive for 1m 23s)",
			SignalFormatter.format(Signal.active("Woodcutting", Duration.ofSeconds(83)), "{activity} (inactive for {duration})"));
	}

	@Test
	public void clearedSignalIgnoresTemplate()
	{
		assertEquals("Activity Notifier: Active - Detection paused",
			SignalFormatter.format(Signal.cleared("Detection paused"), "{activity} (inactive for {duration})"));
	}

	@Test
	public void emptyTemplateRendersHeadOnly()
	{
		assertEquals("Activity Notifier: Inactive", SignalFormatter.format(Signal.inactive("Mining"), ""));
		assertEquals("Activity Notifier: Inactive", SignalFormatter.format(Signal.inactive("Mining"), "   "));
		assertEquals("Activity Notifier: Inactive", SignalFormatter.format(Signal.inactive("Mining"), null));
	}

	@Test
	public void unknownTokensAreLeftAsIs()
	{
		assertEquals("Activity Notifier: Inactive - Mining {nope}",
			SignalFormatter.format(Signal.inactive("Mining"), "{activity} {nope}"));
	}

	@Test
	public void resourceTokens()
	{
		Signal signal = Signal.builder()
			.type(SignalType.RESOURCE_ALERT)
			.keyword("Low hitpoints")
			.resource("Hitpoints")
			.value("9")
			.threshold("10")
			.build();
		assertEquals("Activity Notifier: Low hitpoints - Hitpoints 9 (threshold 10)",
			SignalFormatter.format(signal, "{resource} {value} (threshold {threshold})"));
	}

	@Test
	public void formatsDurations()
	{
		assertEquals("0s", SignalFormatter.formatDuration(Duration.ZERO));
		assertEquals("45s", SignalFormatter.formatDuration(Duration.ofSeconds(45)));
		assertEquals("1m 23s", SignalFormatter.formatDuration(Duration.ofSeconds(83)));
		assertEquals("5m 0s", SignalFormatter.formatDuration(Duration.ofMinutes(5)));
		assertEquals("1h 2m", SignalFormatter.formatDuration(Duration.ofMinutes(62).plusSeconds(30)));
		assertEquals("0s", SignalFormatter.formatDuration(Duration.ofSeconds(-5)));
	}
}
