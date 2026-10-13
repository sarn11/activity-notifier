# Data Model: Damage Taken Notifier

Everything here lives in memory on the client thread, except the settings, which are RuneLite config
([contracts/config.md](./contracts/config.md)).

## HitKind (enum, pure)

The classification of one hitsplat type (research R2).

| Value | Meaning |
|---|---|
| `DIRECT` | A hit that takes hitpoints: `DAMAGE_ME`, `DAMAGE_MAX_ME`, `DAMAGE_OTHER` |
| `DAMAGE_OVER_TIME` | `POISON`, `VENOM` |
| `NONE` | Everything else, including 0 hits, healing, disease, burn, bleed, drains and NPC shield/armour variants |

- `static HitKind of(int hitsplatType)`: total, so any unknown type returns `NONE`.

## Hit (value, pure)

A recorded hit on the local player, waiting for the next game tick.

| Field | Type | Notes |
|---|---|---|
| `kind` | `HitKind` | Never `NONE`: those are dropped on arrival |
| `amount` | int | > 0. Hits of 0 are dropped on arrival |

## DamageSettings (value, read once per flush)

A snapshot of the config, so the tracker never touches `ConfigManager`.

| Field | Source key | Validation |
|---|---|---|
| `minimum` | `damageThreshold` | ≥ 1 (`@Range(min = 1)`) |
| `countDamageOverTime` | `damageOverTime` | — |
| `cooldown` | `damageCooldown` (seconds) | ≥ 0. `Duration.ZERO` means no cooldown |

The on/off toggle (`alertDamage`) is checked by the plugin before recording, not by the tracker.

## DamageAlertTracker (pure, `Clock`-free: callers pass `Instant now`)

### State

| Field | Type | Initial | Notes |
|---|---|---|---|
| `pending` | `List<Hit>` | empty | Hits since the last flush, in arrival order |
| `lastSent` | `Instant` (nullable) | `null` | When the last Damage taken signal was sent. It is not reset by `discardPending()` or logout (spec edge case), and is cleared only by a new tracker (plugin restart) |

### Operations

- `record(int hitsplatType, int amount)`: classify with `HitKind.of`. If the result is `NONE` or
  `amount <= 0`, ignore the hit. Otherwise append it to `pending`.
- `List<Signal> flush(DamageSettings s, int hitpoints, Instant now)`: for each pending hit, in order:
  1. If its kind is `DAMAGE_OVER_TIME` and `!s.countDamageOverTime`, skip it (FR-005).
  2. If `amount < s.minimum`, skip it (FR-003, inclusive minimum; FR-004, never summed).
  3. If `lastSent != null` and `now` is before `lastSent + s.cooldown`, skip it. The hit is dropped, not
     re-queued (FR-008).
  4. Otherwise add `Signal.damageTaken(amount, s.minimum, hitpoints)` and set `lastSent = now`.

  Finally, clear `pending` and return the signals (possibly empty).
  - With a cooldown of 0, step 3 never skips, so two qualifying hits in one tick make two signals.
  - With a cooldown above 0, only the first qualifying hit in the tick is sent, because `lastSent` is now
    `now`.
- `discardPending()`: clear `pending`, leaving `lastSent` untouched. Used on logout, hop, connection loss
  and pause.

### Flow (plugin glue, client thread)

```text
HitsplatApplied ── actor == local player? ── alertDamage on? ── ready && !paused? ── tracker.record(type, amount)
GameTick (after ready/pause checks) ─────────────────────────── tracker.flush(settings, boostedHp, now) → dispatcher.dispatch(each)
GameStateChanged (logout/hop/conn lost) ─────────────────────── tracker.discardPending()
togglePause → paused ────────────────────────────────────────── tracker.discardPending()
startUp ─────────────────────────────────────────────────────── tracker = new DamageAlertTracker()
```

- Turning `alertDamage` off stops new hits being recorded. The plugin also checks it before flushing:
  when it is off, the plugin calls `discardPending()` instead of `flush`, so a hit recorded just before
  the toggle is never sent (analysis I4).

## Signal (existing value, extended)

| New field | Type | Used by token |
|---|---|---|
| `damage` | `String` (nullable) | `{damage}` |
| `hitpoints` | `String` (nullable) | `{hitpoints}` |

- `{threshold}` reuses the existing `threshold` field, holding the minimum damage as a plain number.
- New factory: `Signal.damageTaken(int damage, int minimum, int hitpoints)`, with type `DAMAGE_TAKEN` and
  keyword `Damage taken`.

## SignalType (existing enum, extended)

- `DAMAGE_TAKEN("Damage taken")`. The keyword is fixed, and its contract is in
  [contracts/signals.md](./contracts/signals.md).
