package com.activitynotifier.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

public class CustomAnimationsTest
{
	@Test
	public void parsesCommaSeparatedIds()
	{
		assertEquals(ImmutableSet.of(5216, 13149), CustomAnimations.parse("5216, 13149"));
	}

	@Test
	public void emptyOrNullTextGivesEmptySet()
	{
		assertTrue(CustomAnimations.parse("").isEmpty());
		assertTrue(CustomAnimations.parse(null).isEmpty());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void parsedSetIsImmutable()
	{
		CustomAnimations.parse("5216").add(1);
	}

	@Test
	public void forgivesSpacesEmptyEntriesDuplicatesAndJunk()
	{
		assertEquals(ImmutableSet.of(5216, 13149), CustomAnimations.parse(" 5216 ,,13149, 5216, abc, -5 ,"));
	}

	@Test
	public void ignoresEntriesThatAreNotWholeNonNegativeNumbers()
	{
		assertTrue(CustomAnimations.parse("52l6").isEmpty());
		assertTrue(CustomAnimations.parse("1.5").isEmpty());
		assertTrue(CustomAnimations.parse("-1").isEmpty());
		assertTrue(CustomAnimations.parse("99999999999").isEmpty());
	}

	@Test
	public void acceptsZeroAndLeadingPlus()
	{
		assertEquals(ImmutableSet.of(0), CustomAnimations.parse("0"));
		assertEquals(ImmutableSet.of(5216), CustomAnimations.parse("+5216"));
	}

	@Test
	public void onlyCommasSeparateEntries()
	{
		assertTrue(CustomAnimations.parse("5216 13149").isEmpty());
	}

	@Test
	public void separatorsOrSpacesAloneGiveEmptySet()
	{
		assertTrue(CustomAnimations.parse(",").isEmpty());
		assertTrue(CustomAnimations.parse("  ").isEmpty());
	}

	@Test
	public void parsesLongLists()
	{
		StringBuilder text = new StringBuilder();
		for (int id = 1; id <= 300; id++)
		{
			text.append(id).append(", ");
		}
		assertEquals(300, CustomAnimations.parse(text.toString()).size());
	}

	@Test
	public void helmAnimationsCountAsSailingEvenIfListed()
	{
		int helm = AnimationCatalog.HELM_ANIMATIONS.iterator().next();
		assertEquals("Sailing", CustomAnimations.countedAs(helm, ImmutableSet.of()));
		assertEquals("Sailing", CustomAnimations.countedAs(helm, ImmutableSet.of(helm)));
	}

	@Test
	public void builtInAnimationsKeepTheirNameEvenIfListed()
	{
		int axe = AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE;
		assertEquals("Woodcutting", CustomAnimations.countedAs(axe, ImmutableSet.of()));
		assertEquals("Woodcutting", CustomAnimations.countedAs(axe, ImmutableSet.of(axe)));
		assertEquals("Animation", CustomAnimations.countedAs(AnimationID.MILKIT, ImmutableSet.of()));
	}

	@Test
	public void listedAnimationsCountAsCustom()
	{
		assertEquals("Not counted", CustomAnimations.countedAs(5216, ImmutableSet.of()));
		assertEquals("Animation (custom)", CustomAnimations.countedAs(5216, ImmutableSet.of(5216)));
	}

	@Test
	public void idsFollowsTheText()
	{
		CustomAnimations custom = new CustomAnimations();
		assertEquals(ImmutableSet.of(5216), custom.ids("5216"));
		assertEquals(ImmutableSet.of(5216), custom.ids("5216"));
		assertEquals(ImmutableSet.of(13149), custom.ids("13149"));
	}

	@Test
	public void removedIdStopsCounting()
	{
		CustomAnimations custom = new CustomAnimations();
		assertTrue(custom.ids("5216").contains(5216));
		assertFalse(custom.ids("").contains(5216));
		Set<Integer> ids = custom.ids("13149");
		assertTrue(ids.contains(13149));
		assertFalse(ids.contains(5216));
	}

	@Test
	public void unchangedTextIsNotReparsed()
	{
		CustomAnimations custom = new CustomAnimations();
		Set<Integer> first = custom.ids(new String("5216"));
		assertSame(first, custom.ids(new String("5216")));
	}

	@Test
	public void nullTextIsTreatedAsEmpty()
	{
		CustomAnimations custom = new CustomAnimations();
		custom.ids("5216");
		assertTrue(custom.ids(null).isEmpty());
	}
}
