package com.activitynotifier.damage;

import com.activitynotifier.signals.Signal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Records hits on the local player and turns them into damage taken signals on the next game tick, so
 * the hitpoints in the signal already include the hit (specs/002-damage-notifier/research.md R3).
 * Each hit is judged on its own; hits are never added together (FR-004).
 */
public class DamageAlertTracker
{
	@Value
	private static class Hit
	{
		HitKind kind;
		int amount;
	}

	private final List<Hit> pending = new ArrayList<>();

	/**
	 * When the last damage taken signal was sent. Kept across logout and pause, so the cooldown is real
	 * time; only a new tracker (plugin restart) clears it.
	 */
	@Nullable
	private Instant lastSent;

	public void record(int hitsplatType, int amount)
	{
		HitKind kind = HitKind.of(hitsplatType);
		if (kind == HitKind.NONE || amount <= 0)
		{
			return;
		}
		pending.add(new Hit(kind, amount));
	}

	public List<Signal> flush(DamageSettings settings, int hitpoints, Instant now)
	{
		List<Signal> signals = new ArrayList<>();
		for (Hit hit : pending)
		{
			if (hit.getKind() == HitKind.DAMAGE_OVER_TIME && !settings.isCountDamageOverTime())
			{
				continue;
			}

			if (hit.getAmount() < settings.getMinimum())
			{
				continue;
			}

			// hits during the cooldown are dropped, not queued (FR-008)
			if (lastSent != null && now.isBefore(lastSent.plus(settings.getCooldown())))
			{
				continue;
			}

			signals.add(Signal.damageTaken(hit.getAmount(), settings.getMinimum(), hitpoints));
			lastSent = now;
		}
		pending.clear();
		return signals;
	}

	/**
	 * Drops hits not yet sent, e.g. on logout or pause. Does not reset the cooldown.
	 */
	public void discardPending()
	{
		pending.clear();
	}
}
