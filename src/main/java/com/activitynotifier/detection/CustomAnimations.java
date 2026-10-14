package com.activitynotifier.detection;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The player's own animation IDs from the "Custom animation IDs" setting (see
 * specs/003-custom-animation-ids/contracts/config.md). Listed animations count for the Animation detector,
 * after ship-helm and built-in animations.
 * <p>
 * An instance caches the parsed set until the setting's text changes. Client thread only.
 */
public final class CustomAnimations
{
	private String lastText = "";
	private Set<Integer> lastIds = ImmutableSet.of();

	/**
	 * Parses comma-separated animation IDs. Spaces around entries are ignored, empty entries are skipped, and
	 * any entry that isn't a whole number of 0 or more is ignored without affecting the others. Never throws.
	 */
	public static Set<Integer> parse(@Nullable String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return ImmutableSet.of();
		}

		ImmutableSet.Builder<Integer> ids = ImmutableSet.builder();
		for (String entry : text.split(","))
		{
			String trimmed = entry.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			try
			{
				int id = Integer.parseInt(trimmed);
				// -1 means no animation, so negatives never count
				if (id >= 0)
				{
					ids.add(id);
				}
			}
			catch (NumberFormatException e)
			{
				// a typo; skip it and keep the rest of the list
			}
		}
		return ids.build();
	}

	/**
	 * The IDs in the setting's current text, re-parsed only when the text has changed since the last call.
	 */
	public Set<Integer> ids(@Nullable String text)
	{
		String current = text == null ? "" : text;
		if (!current.equals(lastText))
		{
			lastText = current;
			lastIds = parse(current);
		}
		return lastIds;
	}
}
