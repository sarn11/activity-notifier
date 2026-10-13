---

description: "Task list for the Damage Taken Notifier"
---

# Tasks: Damage Taken Notifier

**Input**: Design documents from `specs/002-damage-notifier/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/signals.md, contracts/config.md,
quickstart.md

**Tests**: Included. The plan and Constitution VII call for JUnit coverage of logic that doesn't need the
live client (`HitKind`, `DamageAlertTracker`, `SignalFormatter`). In each phase, write the tests first and
check that they fail before implementing.

**Organization**: Tasks are grouped by user story, so each story can be implemented and tested on its own.

**Post-analysis (2026-10-13)**:
- Damage over time is **poison and venom only** (U1).
- **Phase 6 (US4) runs before Phase 5 (US3)** (D1), so the README documents existing settings.
- The other remediations (A1, I3, I4, U2, C1, T1) are folded into the tasks below.

**Implementation status (2026-10-13)**: T001–T025 are done.
- `./gradlew clean build` passes with 86 tests: 21 new damage tests and 2 new formatter tests.
- Phases ran in the order 1 → 2 → 3 → 4 → 6 → 5 → 7.
- Deviations:
  - The per-phase in-game checkpoints were folded into a single full quickstart pass at T026.
  - `damageSettings()` clamps the cooldown with `Math.max(0, …)` as a guard. `@Range` already prevents negative values in the settings page.
  - T018 needed no new rows, because T004's `damageTakenTokens` already covers all four contract examples.
- T026 (2026-10-13): you confirmed checks 1–7 of the hand-off list in-game, including the `{hitpoints}` check (quickstart #10), which closes research R3 / A1. Check 8's logout/hop-after-hit case isn't reachable, because the game blocks logout and hopping for about 10 s after combat (quickstart.md updated).
- T027 (2026-10-13): archived to `specs/done/002-damage-notifier`. The code comments and the relative links in the contracts point at the new path.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: The user story the task belongs to (US1–US4 from spec.md)

## Conventions for every task

- Paths are relative to the repo root.
- Production code is in `src/main/java/com/activitynotifier/`, and tests are in
  `src/test/java/com/activitynotifier/`.
- Match the surrounding code: tabs, Allman braces, Lombok where the neighbouring classes use it, and JUnit
  4 (`org.junit.Test`, static `org.junit.Assert.*`).
- Hitsplat types come from `net.runelite.api.HitsplatID`. `gameval` has no hitsplat class (research R2),
  so never write raw numbers.
- Only `log.debug` (AGENTS.md). Client-thread code only, with no new threads or executors.
- Run `./gradlew build` at every checkpoint. It must pass before the next phase starts.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a green baseline before changing anything.

- [X] T001 Run `./gradlew build` on branch `002-damage-notifier` and confirm the build and all existing tests pass (baseline for `build.gradle`; no file changes)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The Damage taken signal type and its text rendering. Every story sends this signal.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Add `DAMAGE_TAKEN("Damage taken")` as the last constant of the enum in `src/main/java/com/activitynotifier/signals/SignalType.java`. The keyword is part of the public contract (contracts/signals.md). Extend the class Javadoc to cite `specs/002-damage-notifier/contracts/signals.md` as well as the 001 contract (I3). The existing head-rule tests in `SignalFormatterTest` (`headsContainNoGlobSpecialCharacters`, `noHeadIsSubstringOfAnother`) pick it up automatically
- [X] T003 [P] In `src/main/java/com/activitynotifier/signals/Signal.java`, add two `@Nullable String` fields, `damage` and `hitpoints`, with Javadoc in the style of the existing fields. Add a factory, `public static Signal damageTaken(int damage, int minimum, int hitpoints)`, that builds `type(SignalType.DAMAGE_TAKEN).keyword(SignalType.DAMAGE_TAKEN.getKeyword())`, with `damage`, `threshold` (the minimum) and `hitpoints` each set via `String.valueOf`
- [X] T004 Write failing tests in `src/test/java/com/activitynotifier/signals/SignalFormatterTest.java` (depends on T002, T003). They cover the four example rows of contracts/signals.md "Template tokens", all using `Signal.damageTaken(...)`:
  - `{damage}` gives `Activity Notifier: Damage taken - 12`.
  - `{damage} damage, {hitpoints} HP left` with (12, 1, 38) gives `... - 12 damage, 38 HP left`.
  - `{damage} (minimum {threshold})` with (20, 15, 50) gives `... - 20 (minimum 15)`.
  - An empty template gives `Activity Notifier: Damage taken`.
  - `{activity}` in a damage template is left as-is.
- [X] T005 In `src/main/java/com/activitynotifier/signals/SignalFormatter.java` `render(...)`, add `out = replace(out, "{damage}", signal.getDamage());` and `out = replace(out, "{hitpoints}", signal.getHitpoints());` after the existing token replacements. This makes T004 pass
- [X] T006 In `src/main/java/com/activitynotifier/ActivityNotifierConfig.java` Signals region, add the `templateDamageTaken` String item after `templateSixHour`: `position = 11`, `section = signalsSection`, name "Damage taken text", description "Detail for damage taken signals. Tokens: {damage}, {threshold}, {hitpoints}", default `"{damage}"` (contracts/config.md)
- [X] T007 In `src/main/java/com/activitynotifier/signals/SignalDispatcher.java` `templateFor(...)`, add `case DAMAGE_TAKEN: return config.templateDamageTaken();` (depends on T002, T006)

**Checkpoint**: `./gradlew build` passes, and the head-rule tests pass with the new keyword. Nothing sends the signal yet.

---

## Phase 3: User Story 1 - Get notified when I take a hit (Priority: P1) 🎯 MVP

**Goal**: With the alert on, every hit on the player (minimum 1 by default) sends one
`Activity Notifier: Damage taken - N`. Hits of 0, healing and damage to others send nothing.

**Independent Test**: quickstart.md #1–#4 and #12.
- Turn the alert on and get hit: one signal per hit, showing the amount.
- A blue 0 hit, and damage dealt to or by others, send nothing.
- Nothing is sent while paused.

### Tests for User Story 1

- [X] T008 [P] [US1] Write failing tests in a new file, `src/test/java/com/activitynotifier/damage/HitKindTest.java`, for `HitKind.of(int)`, all using `HitsplatID` constants:
  - `DAMAGE_ME`, `DAMAGE_MAX_ME` and `DAMAGE_OTHER` give `DIRECT`.
  - `POISON` and `VENOM` give `DAMAGE_OVER_TIME`.
  - `DISEASE`, `BURN`, `BLEED`, `BLOCK_ME`, `BLOCK_OTHER`, `HEAL`, `DISEASE_BLOCKED`, `CYAN_UP`, `CYAN_DOWN`, `PRAYER_DRAIN`, `SANITY_DRAIN`, `SANITY_RESTORE`, `CORRUPTION`, `DOOM`, `DAMAGE_ME_ORANGE`, `DAMAGE_ME_CYAN`, `DAMAGE_ME_POISE` and an unused id (e.g. `-1`) give `NONE` (research R2).
- [X] T009 [P] [US1] Write failing tests in a new file, `src/test/java/com/activitynotifier/damage/DamageAlertTrackerTest.java`, using `Instant.EPOCH` plus offsets for `now`, and `new DamageSettings(1, true, Duration.ZERO)` unless noted:
  - One `record(DAMAGE_ME, 7)` then `flush(settings, 92, now)` gives one signal, with type `DAMAGE_TAKEN` and formatted text `Activity Notifier: Damage taken - 7` (via `SignalFormatter.format(s, "{damage}")`).
  - `record(BLOCK_ME, 0)`, `record(HEAL, 5)` and `record(DAMAGE_ME, 0)` give no signals.
  - Two direct hits recorded before one flush give two signals, in record order.
  - A second `flush` with nothing recorded returns an empty list (`pending` is cleared).
  - `discardPending()` after a record makes the next flush empty.
  - `{hitpoints}` renders the `hitpoints` argument passed to `flush`.

### Implementation for User Story 1

- [X] T010 [P] [US1] Create `src/main/java/com/activitynotifier/damage/HitKind.java`, an enum with `DIRECT`, `DAMAGE_OVER_TIME` and `NONE`. Add `public static HitKind of(int hitsplatType)`, a `switch` over `net.runelite.api.HitsplatID` constants exactly as in data-model.md, with `default: return NONE`. Add a class Javadoc citing research R2. This makes T008 pass
- [X] T011 [P] [US1] Create `src/main/java/com/activitynotifier/damage/DamageSettings.java`. It is a Lombok `@Value` with `int minimum`, `boolean countDamageOverTime` and `java.time.Duration cooldown`, and Javadoc saying it is a per-flush snapshot of config (data-model.md)
- [X] T012 [US1] Create `src/main/java/com/activitynotifier/damage/DamageAlertTracker.java` (depends on T010, T011, T003). It is a pure class with no `Client` or config access:
  - A private `List<Hit> pending`, where `Hit` is a private static final value class holding `HitKind kind` and `int amount`, and a nullable `Instant lastSent`.
  - `record(int hitsplatType, int amount)` ignores the hit when `HitKind.of` gives `NONE` or `amount <= 0`, and appends it otherwise.
  - `List<Signal> flush(DamageSettings s, int hitpoints, Instant now)` applies, in record order, only the **minimum** check for now (`amount < s.getMinimum()` means skip). It sends `Signal.damageTaken(amount, s.getMinimum(), hitpoints)`, sets `lastSent = now` for each signal sent, clears `pending`, and returns the list.
  - `discardPending()` clears `pending` only.
  - Leave clear `// FR-005` and `// FR-008` placeholders where US4 inserts its two checks.

  This makes T009 pass.
- [X] T013 [US1] In `src/main/java/com/activitynotifier/ActivityNotifierConfig.java`, add the "Damage taken" `@ConfigSection` (`damageSection = "damage"`, name "Damage taken", description "Alerts when you take damage", `position = 55`, `closedByDefault = true`) between the Resource alerts and Logout warnings sections. Add a `// region Damage taken` with two items:
  - `alertDamage`: boolean, default `false`, position 0, name "Damage taken", description "Send a signal when you take damage of at least the minimum".
  - `damageThreshold`: int, `@Range(min = 1)`, default `1`, position 1, name "Minimum damage", description "Smallest single hit that sends a signal. Hits are never added together".

  Keys are exactly as in contracts/config.md. Also extend the interface's class Javadoc to cite `specs/002-damage-notifier/contracts/config.md` next to the 001 contract (I3).
- [X] T014 [US1] Wire the tracker into `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java` (depends on T012, T013):
  - Add a `private DamageAlertTracker damageTracker;` field, and in `startUp()` set `damageTracker = new DamageAlertTracker();`.
  - Restructure `onHitsplatApplied`. If `event.getActor() == client.getLocalPlayer()`, then when `config.alertDamage() && ready && !paused`, call `damageTracker.record(event.getHitsplat().getHitsplatType(), event.getHitsplat().getAmount())`, and in all cases `return`. Otherwise keep the existing `isMine()` → `observe(Detector.COMBAT, …)` path unchanged.
  - Add a private `flushDamage(Instant now)`. If `!config.alertDamage()`, call `damageTracker.discardPending()` and return (I4). Otherwise:
    `damageTracker.flush(damageSettings(), client.getBoostedSkillLevel(Skill.HITPOINTS), now).forEach(dispatcher::dispatch);`
    If quickstart #10 later shows hitpoints from before the hit, apply the research R3 fallback (A1).
    Call it in `onGameTick` after the `if (paused) return;` guard, next to the resource sampling (research R3: on the tick, so hitpoints are read after the hit).
  - Add a private `damageSettings()` returning `new DamageSettings(config.damageThreshold(), true, Duration.ZERO)`. US4 replaces the two constants.
  - In `onGameStateChanged`'s `default` branch (logout, hop, connection lost), call `damageTracker.discardPending()`. Leave the `LOADING` branch alone (research R5).
  - In `togglePause()`, call `damageTracker.discardPending()` when pausing.
  - Add only a `log.debug` if anything is logged.

**Checkpoint**: `./gradlew build` passes, and US1 is fully functional. Ask the user to run quickstart #1–#4 and #12 in-game.

---

## Phase 4: User Story 2 - Only notify me for hits that matter (Priority: P1)

**Goal**: The minimum damage is inclusive, hits are never summed, changes apply live, and the toggle and
minimum can also be set from the side panel.

**Independent Test**: quickstart.md #5, #6 and #14.
- A minimum above what the monster can hit sends nothing, and lowering it live makes the next hit send.
- A hit equal to the minimum sends.
- The panel row edits the same settings.

### Tests for User Story 2

- [X] T015 [US2] Add tests to `src/test/java/com/activitynotifier/damage/DamageAlertTrackerTest.java`:
  - With minimum 15: a hit of 14 gives nothing, and a hit of 15 gives one signal (inclusive).
  - Two hits of 8 in one flush with minimum 15 give nothing (FR-004: never summed).
  - The same tracker flushed with minimum 15, then with minimum 5, applies each flush's own minimum (live change).
  - `{threshold}` renders the minimum.

  These are *confirmation* tests, not test-first ones (T1): they should pass against the T012 implementation. If any fail, fix `DamageAlertTracker`.

### Implementation for User Story 2

- [X] T016 [US2] In `src/main/java/com/activitynotifier/ui/ActivityNotifierPanel.java`, add a Damage taken row at the end of the Resource alerts block (contracts/config.md "Side panel"):
  - Fields `private final JCheckBox damageBox = new JCheckBox("Damage taken");` and `private final JSpinner damageMinimum = spinner(1, MAX_POINTS, 1);`.
  - In `resourceRows()`, after the loop, set tooltips ("Send a signal when you take damage of at least the minimum" / "Minimum damage of a single hit"), wire `damageBox` → `set("alertDamage", …)` and `damageMinimum` → `set("damageThreshold", …)`, and `addRow(rows, row++, damageBox, damageMinimum)`.
  - In `refresh()`, set `damageBox.setSelected(config.alertDamage())` and `damageMinimum.setValue(Math.max(1, config.damageThreshold()))`.

**Checkpoint**: `./gradlew build` passes. Ask the user to run quickstart #5, #6 and #14.

---

## Phase 5: User Story 3 - Customise and automate on the damage signal (Priority: P2)

**Goal**: The detail template is editable with `{damage}`, `{threshold}` and `{hitpoints}`, and the
README documents the keyword and a Watchdog recipe.

**Independent Test**: quickstart.md #10, #11 and #15.
- A custom template renders correctly, and an empty one sends the head only.
- The README Watchdog recipe fires on a hit and doesn't match other signals.

### Implementation for User Story 3

- [X] T017 [US3] Update `README.md` (FR-015). Read the existing README first and match its tone. The exact sections to touch (U2):
  - **Intro feature list** (top of file): mention damage alerts if the intro lists features.
  - **New `## Damage taken alert` section**, after `## Logout warnings`, covering:
    - the toggle and minimum damage (single hits, never summed, inclusive);
    - "Count damage over time" (poison and venom only; on by default);
    - the cooldown (seconds, 0 = every hit, real time, hits during it are dropped);
    - `{hitpoints}` = hitpoints after the hit, without Nightmare Zone absorption;
    - no cleared/recovered pair, and nothing is sent while paused.
  - **`## Side panel`**: mention the Damage taken row (toggle + minimum).
  - **`### Signal format` table**: add a `Damage taken` row (`Activity Notifier: Damage taken`, example `... - 12`).
  - **Watchdog recipe**: add a short subsection after `### Capturing the activity name`, using the recipe and regex bullets from contracts/signals.md.
  - **`## Configuration reference` table**:
    - Add a "Damage taken" row: Damage taken (off), Minimum damage (1), Count damage over time (on), Cooldown (0 s).
    - Add `{damage}` and `{hitpoints}` to the Signals row's token list.
  - **`## Changelog`**: add an entry for the new `Damage taken` signal. It is an addition, not a breaking change.
- [X] T018 [US3] Check that `SignalFormatterTest` covers every contracts/signals.md example row (added in T004), and add any missing row in `src/test/java/com/activitynotifier/signals/SignalFormatterTest.java`

**Checkpoint**: `./gradlew build` passes. Ask the user to run quickstart #10, #11 and #15.

---

## Phase 6: User Story 4 - Keep it quiet in long fights and while poisoned (Priority: P2)

**Goal**: An optional cooldown limits repeats, and damage over time can be excluded.

**Independent Test**: quickstart.md #7–#9.
- With a 10 s cooldown, signals arrive at most every 10 s.
- With damage over time off, poison ticks send nothing but direct hits still do.

### Tests for User Story 4

- [X] T019 [US4] Add failing tests to `src/test/java/com/activitynotifier/damage/DamageAlertTrackerTest.java`.
  - Damage over time:
    - `record(POISON, 4)` with `countDamageOverTime = true` sends.
    - With `false`, `POISON`/`VENOM` send nothing, while a `DAMAGE_ME` in the same flush still sends.
  - Cooldown of 10 s:
    - A hit at t=0 sends.
    - Hits at t=5 s and t=9.999 s don't.
    - A hit at exactly t=10 s sends (the boundary is inclusive, `now >= lastSent + cooldown`).
    - Two qualifying hits in one flush with a cooldown give one signal, the first.
    - Hits during the cooldown are not re-sent by the next flush (dropped, not queued).
  - Cooldown of 0: two hits in one flush give two signals.
  - Live change: a signal at t=0 with a 30 s cooldown, then the cooldown changed to 5 s and a hit at t=6 s, sends (measured from the last signal).
  - `discardPending()` does not reset the cooldown: a signal at t=0, then `discardPending()`, then a hit at t=5 s with a 10 s cooldown gives nothing.

### Implementation for User Story 4

- [X] T020 [US4] In `src/main/java/com/activitynotifier/damage/DamageAlertTracker.java` `flush(...)`, replace the two placeholders. The check order per hit must follow data-model.md:
  1. **FR-005**: skip a hit whose `kind == HitKind.DAMAGE_OVER_TIME && !s.isCountDamageOverTime()`.
  2. Minimum check (already there).
  3. **FR-008**: skip a hit when `lastSent != null && now.isBefore(lastSent.plus(s.getCooldown()))`.

  This makes T019 pass.
- [X] T021 [US4] In `src/main/java/com/activitynotifier/ActivityNotifierConfig.java` Damage taken region, add two items (contracts/config.md):
  - `damageOverTime`: boolean, default `true`, position 2, name "Count damage over time", description "Count poison and venom damage".
  - `damageCooldown`: int, `@Range(min = 0)`, `@Units(Units.SECONDS)`, default `0`, position 3, name "Cooldown", description "Minimum time between damage signals. 0 sends one for every qualifying hit".
- [X] T022 [US4] In `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java` `damageSettings()`, replace the T014 constants with `config.damageOverTime()` and `Duration.ofSeconds(config.damageCooldown())` (depends on T021)

**Checkpoint**: `./gradlew build` passes. Ask the user to run quickstart #7–#9.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Descriptor, hygiene, spec bookkeeping and the in-game sign-off.

- [X] T023 [P] Add `"damage"` and `"hit"` to `tags` in the `@PluginDescriptor` of `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`
- [X] T024 [P] Hygiene pass over every file touched in this feature, checking these points:
  - No unused imports or fields.
  - No `log.info` on the hitsplat or tick paths.
  - No raw hitsplat numbers.
  - No existing config key or signal keyword changed (FR-016). Confirm with `git diff master -- src/main/java/com/activitynotifier/ActivityNotifierConfig.java src/main/java/com/activitynotifier/signals/SignalType.java`.
  - FR-012: `shutDown()` and `togglePause()` send no Damage taken signal (C1).
  - FR-014: the damage path never reads the attacker, `getInteracting()`, NPC IDs or location (C1).
- [X] T025 Run `./gradlew build`, then update `specs/002-damage-notifier/spec.md` (Status) and this `tasks.md` with completion state and any deviations found during implementation
- [X] T026 Offer to launch `./gradlew run`. Point the user at the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) login steps, and walk them through the full `specs/002-damage-notifier/quickstart.md` table and its edge cases. Wait for their in-game confirmation (Constitution VII). The task is not done until they confirm
- [X] T027 After user sign-off and once the PR is ready to merge, archive with `git mv specs/002-damage-notifier specs/done/002-damage-notifier`. Then:
  - Update any code comments that cite `specs/002-damage-notifier/...` paths.
  - Rewrite `.specify/feature.json` (no BOM) to `specs/done/002-damage-notifier`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup. Blocks every story, because all of them send `DAMAGE_TAKEN`.
- **US1 (Phase 3)**: after Foundational. It is the MVP and creates `HitKind`, `DamageSettings`, `DamageAlertTracker`, the config section and the plugin glue.
- **US2 (Phase 4)**: after US1, because it tests the tracker's minimum and adds a panel row for keys US1 created.
- **US3 (Phase 5)**: after Foundational for the template work. The README text also describes the US2 and US4 settings, so do it after US1. If US4 isn't done yet, T017 can describe its settings ahead of time or be finished in Polish.
- **US4 (Phase 6)**: after US1 (it fills the tracker placeholders and `damageSettings()`). Independent of US2 and US3.
- **Polish (Phase 7)**: after all stories.

### Within each phase

- Tests are written first and fail, then the implementation makes them pass.
- `HitKind` and `DamageSettings` come before `DamageAlertTracker`, which comes before the plugin glue.
- Config keys come before the plugin and panel code that reads them.

### Same-file sequencing (no [P] between these)

- `ActivityNotifierConfig.java`: T006 → T013 → T021
- `ActivityNotifierPlugin.java`: T014 → T022 → T023
- `DamageAlertTrackerTest.java`: T009 → T015 → T019
- `DamageAlertTracker.java`: T012 → T020
- `SignalFormatterTest.java`: T004 → T018

## Parallel Opportunities

- **Phase 2**: T002 (`SignalType`) and T003 (`Signal`) can run together. T006 (config) can also run alongside them. T004 waits for T002 and T003.
- **Phase 3**: T008 and T009 (two new test files) can run together, then T010 and T011 (two new classes) together. T012 → T013 → T014 run in sequence.
- **Across stories, once US1 is done**:
  - US2's T016 (panel) and US4's T019–T020 (tracker) touch different files, so they can run in parallel.
  - T015 and T019 both edit `DamageAlertTrackerTest.java`, so run them one after the other.
- **Phase 7**: T023 and T024 can run together.

### Parallel Example: User Story 1

```text
Task: "T008 HitKindTest in src/test/java/com/activitynotifier/damage/HitKindTest.java"
Task: "T009 DamageAlertTrackerTest in src/test/java/com/activitynotifier/damage/DamageAlertTrackerTest.java"
# then
Task: "T010 HitKind in src/main/java/com/activitynotifier/damage/HitKind.java"
Task: "T011 DamageSettings in src/main/java/com/activitynotifier/damage/DamageSettings.java"
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 (baseline) → Phase 2 (signal plumbing) → Phase 3 (US1).
2. **Stop and validate**: the user runs quickstart #1–#4 and #12 in-game. With the default minimum of 1,
   that is already the feature the user asked for, minus the extras.

### Incremental delivery

1. US1: get notified on any hit (MVP).
2. US2: the minimum is verified inclusive and never summed, plus the panel row.
3. US4: cooldown and damage-over-time exclusion. Do this before US3 if the README should land in one
   pass.
4. US3: README and Watchdog recipe.
5. Polish, then full quickstart sign-off by the user, then PR, then archive the spec.

## Notes

- `[P]` = different files with no dependency on an incomplete task.
- Commit after each phase. Never mix reformatting with feature changes (constitution).
- A clean `./gradlew build` or JVM start is not a passing test. Only the user's in-game confirmation
  closes the feature.
