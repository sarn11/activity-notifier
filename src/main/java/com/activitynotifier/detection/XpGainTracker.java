package com.activitynotifier.detection;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.Skill;

/**
 * Turns XP updates into XP-gain observations (FR-007, research R5). Has no client dependency.
 * Until {@link #seed} is called, updates are ignored, so the burst of StatChanged events at login
 * doesn't count as activity. Client thread only.
 */
public class XpGainTracker
{
	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private boolean seeded;

	/**
	 * Caches the current XP of every skill.
	 */
	public void seed(Map<Skill, Integer> current)
	{
		xp.clear();
		xp.putAll(current);
		seeded = true;
	}

	/**
	 * Records a skill's XP. Returns the skill when it gained at least {@code threshold} XP since the last update.
	 */
	public Optional<Skill> onXp(Skill skill, int value, int threshold)
	{
		if (!seeded)
		{
			return Optional.empty();
		}

		Integer previous = xp.put(skill, value);
		if (previous == null)
		{
			return Optional.empty();
		}

		int gained = value - previous;
		return gained > 0 && gained >= threshold ? Optional.of(skill) : Optional.empty();
	}

	/**
	 * Forgets the cache (logout, hop); updates are ignored until the next {@link #seed}.
	 */
	public void reset()
	{
		xp.clear();
		seeded = false;
	}

	public boolean isSeeded()
	{
		return seeded;
	}
}
