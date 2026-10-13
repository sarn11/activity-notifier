# Implementation Plan: Damage Taken Notifier

**Branch**: `002-damage-notifier` | **Date**: 2026-10-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-damage-notifier/spec.md`

## Summary

Add an opt-in **Damage taken** signal, `Activity Notifier: Damage taken - {damage}`. It fires when a
single hit on the local player is at least a configurable minimum. Damage over time (poison and venom
only) counts by default and can be excluded. An optional real-time cooldown limits how
often it repeats.

Technical approach:
- The existing `HitsplatApplied` handler gains a branch for hitsplats on the local player. A pure
  classifier (`HitKind`) sorts each hitsplat type into direct, damage over time, or none.
- A pure tracker (`DamageAlertTracker`) records qualifying hits. On the next `GameTick` it applies the
  minimum, the damage-over-time setting and the cooldown, and returns `Signal`s. These go through the
  existing `SignalDispatcher`.
- Sending on the tick ensures `{hitpoints}` shows the value *after* the hit (research R3).
- Settings get their own config section, and the side panel gets one row (toggle and minimum).

## Technical Context

**Language/Version**: Java 11 (`options.release.set(11)` in `build.gradle`)

**Primary Dependencies**:
- RuneLite client `latest.release` (1.13.1 at plan time), `compileOnly`.
- Lombok.
- New uses from the client: `HitsplatApplied`, `Hitsplat`, `HitsplatID`, `Skill.HITPOINTS` via
  `client.getBoostedSkillLevel`. `HitsplatApplied` is already subscribed to; the others are new.

**Storage**: RuneLite config only (group `activitynotifier`), with 5 new keys. The cooldown timestamp is
in memory only.

**Testing**:
- JUnit 4 for `HitKind`, `DamageAlertTracker` and the new `SignalFormatter` tokens.
- In-game validation by the user ([quickstart.md](./quickstart.md)).

**Target Platform**: RuneLite desktop client (Plugin Hub plugin)

**Project Type**: Single-module RuneLite plugin

**Performance Goals**:
- O(1) work per hitsplat on the local player. Hitsplats on other actors are rejected by one reference
  comparison (already the case).
- O(hits this tick) work per game tick, which is usually 0–2.

**Constraints**:
- Client thread only. No new threads, I/O or network.
- The signal head must pass the existing glob-safety and substring rules.

**Scale/Scope**:
- 3 new production classes (`HitKind`, `DamageSettings`, `DamageAlertTracker`) and edits to 6 (`SignalType`, `Signal`,
  `SignalFormatter`, `SignalDispatcher`, `ActivityNotifierConfig`, `ActivityNotifierPlugin`), plus the
  panel and the README.
- 2 new test classes, and 1 extended.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Plugin Hub Compliance | The signal reports only a hit that already landed on the local player, with its amount and the player's own hitpoints. There is no attacker, NPC, attack-style or projectile information, no prediction, and no per-boss or per-location behaviour (FR-014, research R7). It is equivalent to the on-screen hitsplat and to the existing Low hitpoints alert and core Idle Notifier. No data about other players, menus, input or chat changes. No reflection, JNI, processes or serialization. | PASS |
| II. Client Thread Safety | Recording happens in `@Subscribe onHitsplatApplied` and flushing in `@Subscribe onGameTick`, both on the client thread. The side panel touches only `ConfigManager` on the EDT, as before. No sleeps or executors. | PASS |
| III. Performance Budget | Event-driven, with no scene scans and no overlay. `log.debug` only. | PASS |
| IV. Sanctioned APIs | Hitsplat types use `net.runelite.api.HitsplatID` constants. `gameval` has no hitsplat class (research R2), so this is the sanctioned constant source and no magic numbers are used. `Skill.HITPOINTS` is used for hitpoints. No HTTP, JSON or file I/O. | PASS |
| V. Config Stability & Consent | Only new keys are added, and every existing key and signal keyword is untouched (FR-016). The new keys are frozen in [contracts/config.md](./contracts/config.md). The alert is off by default. No third-party server. | PASS |
| VI. Clean Lifecycle | No new registrations. `startUp` creates a fresh tracker. Pending hits are discarded on logout, hop, connection loss and pause. Nothing to clean up in `shutDown`, because the signal is a one-off with no cleared pair (FR-012). Works when enabled mid-session (only later hits count). | PASS |
| VII. Human-Verified Testing | [quickstart.md](./quickstart.md) lists 15 in-game scenarios plus edge cases. The pure logic gets JUnit coverage. | PASS |
| Packaging | No new dependencies or resources. Java 11. BSD-2 kept. | PASS |

**Post-design re-check (after Phase 1)**: PASS.
- The design artifacts add no threads, I/O, dependencies or game-facing behaviour beyond the above.
- The one judgment call is Principle I (boss rules). It is recorded in research R7 and enforced by FR-014:
  any future request for per-source or per-boss damage filtering would need a fresh compliance review.

## Project Structure

### Documentation (this feature)

```text
specs/002-damage-notifier/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R7
├── data-model.md        # Phase 1: HitKind, Hit, DamageAlertTracker, Signal additions
├── quickstart.md        # Phase 1: build checks + in-game validation scenarios
├── contracts/
│   ├── signals.md       # Damage taken keyword, tokens, Watchdog recipe (extends 001 contract)
│   └── config.md        # New permanent config keys and panel row (extends 001 contract)
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/com/activitynotifier/
├── ActivityNotifierPlugin.java       # EDIT: hitsplat-on-local-player branch, flush on GameTick, discard on logout/pause, new tracker in startUp
├── ActivityNotifierConfig.java       # EDIT: "Damage taken" section + 4 keys; templateDamageTaken in Signals
├── damage/                           # NEW package
│   ├── HitKind.java                  # NEW: pure HitsplatID → DIRECT / DAMAGE_OVER_TIME / NONE
│   ├── DamageSettings.java           # NEW: per-flush settings snapshot (minimum, DoT, cooldown)
│   └── DamageAlertTracker.java       # NEW: pure record/flush/discard, minimum + DoT + cooldown
├── signals/
│   ├── SignalType.java               # EDIT: DAMAGE_TAKEN("Damage taken")
│   ├── Signal.java                   # EDIT: damage + hitpoints fields, damageTaken() factory
│   ├── SignalFormatter.java          # EDIT: {damage}, {hitpoints} tokens
│   └── SignalDispatcher.java         # EDIT: templateFor(DAMAGE_TAKEN)
└── ui/
    └── ActivityNotifierPanel.java    # EDIT: Damage taken row (toggle + minimum)

src/test/java/com/activitynotifier/
├── damage/HitKindTest.java           # NEW
├── damage/DamageAlertTrackerTest.java # NEW
└── signals/SignalFormatterTest.java  # EDIT: damage tokens; head rules cover the new keyword automatically

README.md                             # EDIT: feature, settings, keyword/tokens, Watchdog recipe (FR-015)
```

**Structure Decision**:
- The new logic goes in its own `damage` sub-package, following the existing split by concern
  (`detection`, `resources`, `warnings`).
- Both new classes have no `Client` dependency, so they are unit-testable.
- `ActivityNotifierPlugin` stays the only event subscriber.

## Implementation Phases (input for /speckit-tasks)

Run `./gradlew build` after every phase.

1. **Signal plumbing**:
   - Add `SignalType.DAMAGE_TAKEN`, the `Signal` fields and factory, the formatter tokens, the
     dispatcher template, and the `templateDamageTaken` config key.
   - Tests: formatter tokens. The head rules are covered automatically.
2. **Detection core (US1, US2)**:
   - Add `HitKind` and `DamageAlertTracker`, with the minimum only (cooldown 0, DoT always on in this
     phase's tests).
   - Add the Damage taken config section with `alertDamage` and `damageThreshold`.
   - Plugin glue: record on `HitsplatApplied`, flush on `GameTick`, discard on logout and pause, and
     create a fresh tracker in `startUp`.
   - Tests: classifier and tracker minimum, zero-hit and ordering.
   - The user can now validate quickstart #1–#6 and #12.
3. **Quieting options (US4)**:
   - Add `damageOverTime` and `damageCooldown`, the tracker's damage-over-time filter and cooldown, and
     the settings snapshot.
   - Tests: damage-over-time exclusion, cooldown boundaries, changing the cooldown live, and
     `discardPending` keeping `lastSent`.
   - Quickstart #7–#9.
4. **Customisation and docs (US3)**:
   - The template defaults check, the side panel row, and the README section (feature, settings,
     keyword, tokens, Watchdog recipe from contracts/signals.md).
   - Quickstart #10, #11, #13–#15.
5. **Polish**:
   - Add `damage` and `hit` to the plugin descriptor `tags`.
   - Do a full quickstart pass with the user, then archive the spec to `specs/done/` once the PR is
     ready.

**Order change (analysis D1)**: implementation runs phase 3 (US4) before the README phase (US3), so the
README documents settings that already exist. tasks.md keeps the story labels but orders the phases
this way.

**Post-analysis changes (2026-10-13)**:
- U1: damage over time is poison and venom only.
- A1: fallback recorded in research R3.
- I1: `DamageSettings` added to the structure above.
- I3: Javadoc cites the 002 contracts.
- I4: alert-off discards pending hits.
- U2, C1, T1: task wording in tasks.md.

## Complexity Tracking

No constitution violations. Nothing to justify.
