package com.activitynotifier.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
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
	public void idsFollowsTheText()
	{
		CustomAnimations custom = new CustomAnimations();
		assertEquals(ImmutableSet.of(5216), custom.ids("5216"));
		assertEquals(ImmutableSet.of(5216), custom.ids("5216"));
		assertEquals(ImmutableSet.of(13149), custom.ids("13149"));
	}
}
