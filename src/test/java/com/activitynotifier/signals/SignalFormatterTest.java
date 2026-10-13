package com.activitynotifier.signals;

import com.activitynotifier.resources.ResourceType;
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
	 * Resource keywords exactly as in contracts/signals.md, to catch an accidental keyword change.
	 */
	private static final List<String> CONTRACT_RESOURCE_KEYWORDS = Arrays.asList(
		"Low hitpoints", "Hitpoints recovered",
		"Low prayer", "Prayer recovered",
		"Low run energy", "Run energy recovered",
		"Run energy restored", "Run energy used",
		"Low oxygen", "Oxygen recovered",
		"Special attack restored", "Special attack used"
	);

	private static List<String> resourceKeywords()
	{
		List<String> keywords = new ArrayList<>();
		for (ResourceType type : ResourceType.values())
		{
			keywords.add(type.getAlertKeyword());
			keywords.add(type.getRecoveredKeyword());
		}
		return keywords;
	}

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
		for (String keyword : resourceKeywords())
		{
			heads.add(SignalFormatter.head(keyword));
		}
		return heads;
	}

	@Test
	public void resourceKeywordsMatchContract()
	{
		assertEquals(CONTRACT_RESOURCE_KEYWORDS, resourceKeywords());
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
	public void damageTakenTokens()
	{
		// the example rows of specs/002-damage-notifier/contracts/signals.md
		assertEquals("Activity Notifier: Damage taken - 12",
			SignalFormatter.format(Signal.damageTaken(12, 1, 38), "{damage}"));
		assertEquals("Activity Notifier: Damage taken - 12 damage, 38 HP left",
			SignalFormatter.format(Signal.damageTaken(12, 1, 38), "{damage} damage, {hitpoints} HP left"));
		assertEquals("Activity Notifier: Damage taken - 20 (minimum 15)",
			SignalFormatter.format(Signal.damageTaken(20, 15, 50), "{damage} (minimum {threshold})"));
		assertEquals("Activity Notifier: Damage taken",
			SignalFormatter.format(Signal.damageTaken(7, 1, 92), ""));
	}

	@Test
	public void damageTakenLeavesOtherTokens()
	{
		assertEquals("Activity Notifier: Damage taken - 5 {activity}",
			SignalFormatter.format(Signal.damageTaken(5, 1, 10), "{damage} {activity}"));
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
