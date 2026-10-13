# Research: Damage Taken Notifier

All API facts were checked against RuneLite **1.13.1** (the latest release, which `build.gradle` resolves
through `latest.release`) in the local source tree, not from memory.

## R1. Detecting damage to the local player

**Decision**: Subscribe to `HitsplatApplied` and keep only events where
`event.getActor() == client.getLocalPlayer()`. Classify the hit with `Hitsplat.getHitsplatType()` and read
the damage from `Hitsplat.getAmount()`.

**Rationale**:
- `HitsplatApplied` (`net.runelite.api.events`) fires for every hitsplat the client processes, *even when
  it isn't rendered* (for example when the actor already shows 4 splats). That makes it complete, which
  is what SC-001/SC-002 need.
- Core Idle Notifier uses exactly this filter to detect the player being attacked
  (`IdleNotifierPlugin.onHitsplatApplied`: actor is the local player and `hitsplat.isMine()`).
- The plugin already subscribes to `HitsplatApplied` for the combat detector, which currently ignores
  hitsplats on the local player. The new branch handles exactly the case that one returns early on.
- No scene scan or per-frame work (Constitution III).

**Alternatives considered**:
- *A `StatChanged` drop in boosted Hitpoints*: mixes damage with stat drains, and loses individual hits
  when two land in one tick. That breaks FR-004 ("each hit on its own"). It also can't tell poison from a
  direct hit (FR-005). Rejected.
- *Reading `Actor.getHealthRatio()`*: proportional, and only for the health bar. Rejected.

## R2. Which hitsplat types count

`net.runelite.api.HitsplatID` is the constants class for hitsplat types. It is not deprecated, and
`net.runelite.api.gameval` has **no** hitsplat class, so using `HitsplatID` follows Constitution IV (no
magic numbers where a constant exists).

**Decision**: a pure classifier, `HitKind.of(int hitsplatType)`, with three results:

| Kind | `HitsplatID` constants | Counted |
|---|---|---|
| `DIRECT` | `DAMAGE_ME`, `DAMAGE_MAX_ME`, `DAMAGE_OTHER` | always (when amount ≥ minimum) |
| `DAMAGE_OVER_TIME` | `POISON`, `VENOM` | only when "Count damage over time" is on (FR-005) |
| `NONE` | everything else, e.g. `BLOCK_ME`/`BLOCK_OTHER` (0 hits), `HEAL`, `DISEASE`, `DISEASE_BLOCKED`, `BURN`, `BLEED`, `CYAN_UP`/`CYAN_DOWN`, `PRAYER_DRAIN`, `SANITY_DRAIN`/`SANITY_RESTORE`, `CORRUPTION`, `DOOM`, and the coloured `*_CYAN`/`*_ORANGE`/`*_YELLOW`/`*_WHITE`/`*_POISE` variants | never (FR-006) |

**Update (analysis U1, user decision 2026-10-13)**:
- Damage over time is limited to **poison and venom**.
- `DISEASE`, `BURN` and `BLEED` were first listed here but never checked as hitpoint damage. In OSRS,
  disease may drain other stats, and burn and bleed are tied to specific bosses. So they are `NONE`, which
  fails safe (quiet).

**Rationale**:
- Hitsplats on your own character use the `*_ME` style. `DAMAGE_OTHER` is included defensively: the actor
  filter already guarantees the hit is on the local player, so if the game ever tints a hit on you as
  "other", it is still damage to you.
- The coloured variants are NPC shield, armour, charge and poise bars (core Zalcano counts `*_ORANGE` as
  shield damage). They don't take the player's hitpoints.
- `PRAYER_DRAIN`, `SANITY_*`, `CORRUPTION` and `DOOM` are not hitpoint damage.
- An amount of 0 never counts, whatever the type. This is a second guard for FR-006, since the minimum is
  at least 1.

**Alternatives considered**: `Hitsplat.isMine()` alone. It includes `BLOCK_ME` and the coloured variants,
and excludes poison and venom, so it doesn't match FR-005/FR-006. Rejected.

**Risk**: new hitsplat types added by future game updates are `NONE` until we add them. That fails safe:
the plugin stays quiet rather than spamming.

## R3. When the signal is sent, and reading `{hitpoints}`

**Decision**: `onHitsplatApplied` only *records* qualifying hits in the tracker. The next `GameTick` sends
them, reading `client.getBoostedSkillLevel(Skill.HITPOINTS)` for `{hitpoints}` at that moment.

**Rationale**:
- Hitsplat and stat updates arrive in the same server tick, in no guaranteed order. Reading hitpoints
  inside `onHitsplatApplied` could show the value *before* the hit. By the game tick, both have been
  applied.
- The delay is at most one tick (0.6 s), which is within SC-001.
- Signals within one tick keep hit order, so FR-004 and the cooldown (FR-008) are applied in the order the
  hits happened.
- Boosted hitpoints without Nightmare Zone absorption match the hitpoints orb (spec assumption). This
  differs on purpose from the Low hitpoints alert, which adds absorption.

**Alternatives considered**: sending immediately and computing `hp - amount`. That double-counts when the
stat has already updated. Rejected.

**Unverified assumption (analysis A1)**: the claim that the stat update has landed by the next `GameTick`
is not proven by source. RuneLite's client injects the packet handling, so the source can't show the
order. Quickstart #10 checks it in-game.

**Fallback**: if `{hitpoints}` shows the value from before the hit, keep hits recorded during tick N and
send them on tick N+1, by adding a one-tick age to the pending hits. Record the outcome here.

**Outcome (2026-10-13)**: verified in-game by the user (quickstart #10 passed). `{hitpoints}` shows the
value after the hit, so the fallback isn't needed.

## R4. Cooldown clock

**Decision**: real time from an injected `Clock`, which the plugin already holds (`Clock.systemUTC()`).
The tracker stores the `Instant` of the last *sent* Damage taken signal. A hit is suppressed when
`now < lastSent + cooldown`. A cooldown of 0 never suppresses.

**Rationale**:
- The spec defines the cooldown in seconds and says it is not reset by logout (edge cases), so game ticks
  are the wrong unit: there are no ticks while logged out.
- The `Clock` makes the tracker unit-testable without the client, the same way `ActivityTracker` is.
- Reading the cooldown setting on every flush makes a changed value apply to the next hit, measured from
  the last signal (edge case).
- `lastSent` is held only in memory, so a plugin restart clears it (data-model).

## R5. Pause, login state and lifecycle

**Decision**:
- Hits are ignored while `paused` or not `ready` (the plugin's existing flags) (FR-013).
- Pending hits are discarded on logout, hop or connection loss (the existing `default` branch of
  `onGameStateChanged`) and when pausing.
- Pending hits are kept across `LOADING`, matching how the activity tracker treats loading screens.
- `startUp` creates a fresh tracker. `shutDown` needs no clearing signal, because Damage taken is a
  one-off event with no "recovered" pair (FR-012).

**Rationale**: this reuses the existing ready/pause gating, so the behaviour matches the other alerts
with no new lifecycle state (Constitution VI).

## R6. Settings placement

**Decision**: a new config section, **Damage taken** (position 55, closed by default), between Resource
alerts (50) and Logout warnings (60). The template goes in the **Signals** section after the six-hour
template.

**Rationale**:
- The Resource alerts section's description promises "a matching recovered signal", and this alert has
  none.
- A separate section also gives room for the cooldown and damage-over-time options without crowding six
  resource rows.
- The spec explicitly allowed planning to choose a separate section.

**Side panel**: one row is added to the panel's Resource alerts block, with a **Damage taken** checkbox
and a minimum-damage spinner in the existing "Threshold" column. It works through `ConfigManager`, like
the other rows.
- Cooldown and damage-over-time stay in the settings page only, matching how the panel already covers
  just the most-used settings.
- The spec's "No side panel change" assumption was about the activity *status* display, which stays
  unchanged.

## R7. Plugin Hub compliance (boss and PvP rules)

**Decision**: compliant, given these design constraints, which are enforced by FR-014:
- The signal fires *after* a hit lands, using only the hit's amount and the player's own hitpoints.
- There is no source, NPC, attack-style or projectile information, no per-boss or per-NPC behaviour, and
  no prediction.
- Every hit type is handled the same way in every location.

**Rationale**:
- The rejected-features rules target predictive or mechanic-specific cues (next attack, projectile
  landing, prayer switching, hazards).
- A generic "you were hit for N" duplicates information already on screen as a hitsplat, in the same way
  as the existing Low hitpoints alert and core Idle Notifier's low-hitpoints notification.
- It reports only the local player's own state, so the PvP and data-privacy rules are untouched.
- Watchdog separately suppresses all its alerts in its banned areas.
