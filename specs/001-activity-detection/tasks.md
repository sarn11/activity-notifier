---

description: "Task list for Activity Detection & State Signals"
---

# Tasks: Activity Detection & State Signals

**Input**: Design documents from `specs/001-activity-detection/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Included. The plan (R13) and quickstart require JUnit coverage of the pure logic: the trackers, the formatter and the catalog. Constitution VII requires JUnit for client-independent logic. In-game behaviour is validated only by the user.

**Organization**: Tasks are grouped by user story (US1–US8 from spec.md), so each story can be implemented and validated on its own.

## Progress & handoff (updated 2026-10-12)

**Start here when resuming in a new session.**

### Done

- **Every task through T049 is built.** `./gradlew build` is green, with 63 JUnit tests: ActivityTrackerTest 28, AnimationCatalogTest 5, XpGainTrackerTest 6, ResourceAlertTrackerTest 7, SignalFormatterTest 10, LogoutWarningTrackerTest 7.
- Phases 1–5 (MVP, US1–US3) are committed in `143fb74`. Phases 6–11 (US4–US8 + polish) are **uncommitted** on branch `001-activity-detection`. Commit only when the user asks.
- Built files:
  - `ActivityNotifierConfig` (every key from contracts/config.md; no `[WIP]` labels remain)
  - `signals/` (`SignalType`, `Signal`, `SignalFormatter`, `SignalDispatcher`)
  - `detection/` (`Detector`, `Activity`, `ActivityTracker`, `AnimationCatalog`, `WeaponSpeed`, `XpGainTracker`)
  - `resources/` (`ResourceType`, `ResourceAlertTracker`)
  - `warnings/` (`LogoutWarningTracker`)
  - `ui/ActivityNotifierPanel` + `panel_icon.png`
  - `ActivityNotifierPlugin`
  - `README.md` (full feature docs, Watchdog recipe, config reference, changelog)
- `log.debug` lines cover observations (starts and changes only, not every tick), tracker state transitions, resource state transitions, weapon-speed changes and logout warnings.

### Validated in-game by the user

- MVP session: interaction detector (combat), animation detector (mining, woodcutting, herblore), and Watchdog **Game Message** alerts reacting to the chat signals.
- **Phases 6–10 (2026-10-12):** the user tested every quickstart scenario except #18 (Sailing). Sailing is shipped labelled **beta** (`Detector.isBeta()`, plus `(beta)` in the config names and the panel row). Its keys and signal name are unchanged.
- **Side-panel sync finding:** panel edits apply immediately, but RuneLite's settings page only shows them after it is reopened. That's a core limitation: `ConfigPanel` rebuilds only on open, reset or profile switch. It is documented in the README and as a panel hint, and recorded under FR-028. Settings-screen → panel sync works live.

### Deviations from the original plan (recorded in the docs)

1. **Loading screens suspend the tracker instead of resetting it.** Timers are shifted on resume. See spec.md edge cases and data-model.md.
2. **No Guice `@Provides Clock`.** The plugin uses a private `Clock.systemUTC()`, and the tracker takes `Instant`s. See research R4 and T011.
3. **The pause hotkey shows a plain, non-signal chat confirmation** via `SignalDispatcher.info()`. See contracts/signals.md, "Non-signal messages".
4. **`AnimationCatalog` was generated from core Idle Notifier's switch** (283 IDs, grouped by its skill comments) and attributed in the file header. Extra choices:
   - A few "Misc" entries got specific names (Prayer, Herblore, Thieving).
   - The 16 `HUMAN_SAILING_*HELM*` IDs are kept in a separate `HELM_ANIMATIONS` set.
   - `CONFLICTS` collects any ID mapped to two activities; a test asserts it is empty.
5. **Combat ignores hitsplats on the local player** (T029): `isMine()` is also true for damage taken.
6. **Tie-break for the Inactive name**: SPECIFIC beats GENERAL when both expire at the same moment (T027, FR-010a).
7. **Resource alerts**: the real-level guard applies to prayer as well as HP (as core does), and cleared recovered signals use fixed details (T037).
8. **Six-hour timer restarts on hop/reconnect** (as core does), not only on login (T044). **Logout warnings ignore the pause hotkey.**

### Things to watch in in-game testing

- **Oxygen:** `FOSSIL_UNDERWATER_OXYGEN` outside the water. If it reads 0 there, the tracker silently starts ALERTED, and entering the water sends one `Oxygen recovered`. That's harmless, but worth seeing.
- **Movement on a ship:** what happens to `getWorldLocation()` while standing (not steering) on a moving boat.
- **Six-hour on hop:** whether hopping really restarts the game's timer.

### Notes

- **Get API signatures from the real source**, using `.claude/skills/runelite-api-lookup/scripts/Get-RuneLiteSource.ps1` (RuneLite 1.13.1 = `latest.release` at the time of writing). Don't rely on memory.
- **Watchdog reference:** `adamk33n3r/runelite-watchdog` @ `5bdceff` (research R1–R3).
- **Archive:** once the user confirms in-game and the PR is ready, move this spec folder per the user's spec workflow.
## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: The user story the task belongs to (US1–US8)

## Path Conventions

- Production code: `src/main/java/com/activitynotifier/…`
- Tests: `src/test/java/com/activitynotifier/…`
- Resources: `src/main/resources/com/activitynotifier/…`

## Rules for every task (from the constitution and AGENTS.md)

- Read `Client` only on the client thread (`@Subscribe` handlers). The hotkey (AWT) and panel (EDT) code hops via `clientThread.invoke`. No `Thread.sleep`.
- Use `net.runelite.api.gameval.*` constants only, with no magic IDs. Use `log.debug` for per-event logs.
- Config keys must match [contracts/config.md](./contracts/config.md) exactly. Signal keywords must match [contracts/signals.md](./contracts/signals.md) exactly.
- Whatever `startUp` registers, `shutDown` removes.
- Settings that aren't built yet are labelled in `ActivityNotifierConfig.java`: a `[WIP] ` name prefix and a `Not implemented yet. ` description prefix, or ` (not implemented yet)` on a fully unbuilt section. When a phase makes a setting work, it removes that setting's labels. Only `name`/`description` change, never `keyName`. Phase 11 (T048) checks that no labels remain.
- Run `./gradlew build` at every checkpoint.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Clear out the template leftovers and confirm a green baseline build.

- [X] T001 Remove the template `greeting` config item from `src/main/java/com/activitynotifier/ActivityNotifierConfig.java` and the `onGameStateChanged` greeting chat message from `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`, leaving an empty plugin that still compiles
- [X] T002 Run `./gradlew build` to confirm the baseline compiles for Java 11 and the existing test passes (`src/test/java/com/activitynotifier/ActivityNotifierPluginTest.java` is a launcher only)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The config contract, signal pipeline, detector model and plugin skeleton that every story uses.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 Write the full config interface in `src/main/java/com/activitynotifier/ActivityNotifierConfig.java`:
  - Group `activitynotifier`.
  - Every section and key from `contracts/config.md`, with the exact `keyName`s, types (`Keybind`, `Notification`, `int` with `@Range`/`@Units`, `String`, `boolean`) and defaults: General, Signals, General detectors, Specific activities, Extra delays, Resource alerts, Logout warnings.
  - Give each item a `position` and `section`.
  - Define `String GROUP = "activitynotifier"` on the interface and use it in `@ConfigGroup(GROUP)`.
- [X] T004 [P] Create `SignalType` in `src/main/java/com/activitynotifier/signals/SignalType.java`:
  - Values: INACTIVE, ACTIVE, STILL_INACTIVE, RESOURCE_ALERT, RESOURCE_RECOVERED, IDLE_LOGOUT, SIX_HOUR.
  - Each value holds its keyword (for resource types, the keyword comes from `ResourceType`; see T035) and the config key of its detail template, per `contracts/signals.md`.
- [X] T005 [P] Create the immutable `Signal` value in `src/main/java/com/activitynotifier/signals/Signal.java`. Fields: type, keyword, activity name, resource name, value, threshold, duration, and an optional fixed detail (for cleared signals), per data-model.md.
- [X] T006 Implement the pure `SignalFormatter` in `src/main/java/com/activitynotifier/signals/SignalFormatter.java`:
  - Format: `"Activity Notifier: " + keyword + " - " + rendered detail`.
  - An empty rendered detail produces the head only.
  - Substitute the tokens `{activity}`, `{duration}`, `{resource}`, `{value}` and `{threshold}`; leave unknown tokens as-is.
  - Format durations as `45s`, `1m 23s` or `1h 2m`.
  - Depends on T004 and T005.
- [X] T007 [P] Write `SignalFormatterTest` in `src/test/java/com/activitynotifier/signals/SignalFormatterTest.java`:
  - Every head (including all resource alert/recovered keywords, hard-coded from `contracts/signals.md` until `ResourceType` exists in T035) contains none of `[ ] { } , ! \`.
  - No head is a case-insensitive substring of another head.
  - Token substitution, empty template, unknown tokens, and duration formatting.
- [X] T008 Implement `SignalDispatcher` (`@Singleton`) in `src/main/java/com/activitynotifier/signals/SignalDispatcher.java`:
  - Format via `SignalFormatter` with the template read from config.
  - If `config.chatSignals()`, queue a `ChatMessageType.GAMEMESSAGE` via `ChatMessageManager.queue(QueuedMessage.builder()...runeLiteFormattedMessage(...))`, with plain text: no colour tags and the default game-message colour (contracts/signals.md).
  - Then call `notifier.notify(config.notificationSignals(), text)`. The `Notifier` skips it when that notification is disabled.
  - `log.debug` each signal.
- [X] T009 [P] Create the `Detector` enum in `src/main/java/com/activitynotifier/detection/Detector.java`:
  - GENERAL values: ANIMATION, INTERACTION, MOVEMENT, XP_GAIN.
  - SPECIFIC values: COOKING, CRAFTING, FISHING, FIREMAKING, FLETCHING, HERBLORE, MINING, WOODCUTTING, SMITHING, SAILING, COMBAT, MAGIC_SKILLING.
  - Each value has `group`, `displayName`, `enabledKey`, `delayKey`, `defaultEnabled` and `defaultDelayMs`, per data-model.md and contracts/config.md.
  - Add a helper that reads `isEnabled(ConfigManager)` and `extraDelayMs(ConfigManager)` via `configManager.getConfiguration(ActivityNotifierConfig.GROUP, key, Boolean.class/Integer.class)`, falling back to the defaults.
- [X] T010 [P] Create the immutable `Activity` value (name + detector) in `src/main/java/com/activitynotifier/detection/Activity.java`
- [X] T011 Build the plugin skeleton in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Inject `Client`, `ClientThread`, `ConfigManager`, `SignalDispatcher` and the config.
  - Use a private `Clock.systemUTC()` field. *(Deviation: not a Guice `@Provides Clock`, to avoid a duplicate binding with RuneLite's injector; the tracker takes `Instant`s, so tests are unaffected.)*
  - Add a `ready` flag: set on the first `GameTick` after `GameState.LOGGED_IN`, cleared on any other `GameStateChanged` (FR-008). *(Refinement: `LOADING` suspends the tracker instead of resetting it; see the spec's edge cases.)*
  - Add an `onGameTick` evaluation hook (empty for now).
  - `startUp`/`shutDown` touch no client state.
  - Update the `@PluginDescriptor` description and tags.

**Checkpoint**: `./gradlew build` passes (including SignalFormatterTest). User stories can now begin.

---

## Phase 3: User Story 1 - Know when I stop doing something (Priority: P1) 🎯 MVP

**Goal**: Emit exactly one `Activity Notifier: Inactive - <activity>` when every enabled detector has gone quiet for longer than its effective delay.

**Independent Test**: With default settings, chop a tree until it falls. You get exactly one Inactive signal about 2 s later, and none while chopping (quickstart #1).

### Tests for User Story 1

- [X] T012 [P] [US1] Write `ActivityTrackerTest` for inactivity in `src/test/java/com/activitynotifier/detection/ActivityTrackerTest.java`, using a mutable test `Clock`. Cases:
  - NEUTRAL→ACTIVE on the first observation is silent.
  - ACTIVE→INACTIVE emits once after base + extra delay.
  - A pause shorter than the delay emits nothing.
  - No repeat while still inactive.
  - Disabled detectors are ignored.
  - With multiple detectors, the player stays active while any is live.
  - `reset()` (logout/hop) returns to NEUTRAL silently.
  - Reminders: none when disabled; exactly one per interval when enabled.
  - Turning a detector off never produces an Inactive. Turn off the only live detector while another enabled detector was never seen: the tracker stays ACTIVE. Turn off all detectors while ACTIVE: silent → NEUTRAL (data-model I1 rows).

### Implementation for User Story 1

- [X] T013 [US1] Implement `ActivityTracker` in `src/main/java/com/activitynotifier/detection/ActivityTracker.java`, per the data-model.md state machine:
  - `observe(Detector, Activity, Instant)`.
  - `evaluate(Instant)` returns `Optional<Signal>` for INACTIVE and STILL_INACTIVE.
  - `reset()`.
  - The effective delay comes from a nested functional interface `ActivityTracker.DelayProvider` (base + extra; the combat weapon term is added in US4). The enabled check comes from a nested `ActivityTracker.EnabledProvider`.
  - Keep `lastSeen` and `lastActivity` per detector. Remove a detector's entries when it is turned off. ACTIVE→INACTIVE only when no detector is live **and** at least one enabled detector has a `lastSeen` entry; the signal names that detector's activity. When no detectors are enabled at all, go to NEUTRAL (silently if ACTIVE).
  - The reminder settings come from a provider.
  - No RuneLite `Client` dependency.
- [X] T014 [P] [US1] Create `AnimationCatalog` in `src/main/java/com/activitynotifier/detection/AnimationCatalog.java`:
  - A static `Map<Integer, String>` from `net.runelite.api.gameval.AnimationID` to an activity name (Woodcutting, Mining, Fishing, Cooking, Fletching, Crafting, Smithing, Herblore, Firemaking, Magic, Sailing, Construction, etc.).
  - Port all 283 cases from core Idle Notifier's `checkAnimationIdle` switch (RuneLite 1.13.1, `~/.cache/runelite-sources/1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/idlenotifier/IdleNotifierPlugin.java`), grouped by its skill comments.
  - Put the 16 `AnimationID.HUMAN_SAILING_*HELM*` constants in a **separate** `HELM_ANIMATIONS` set that is NOT part of the general catalog map. Move any helm IDs found in core's list into this set too (analysis decision A1).
  - Credit RuneLite's Idle Notifier (BSD-2) in the file header.
- [X] T015 [P] [US1] Write `AnimationCatalogTest` in `src/test/java/com/activitynotifier/detection/AnimationCatalogTest.java`: the catalog is non-empty, every name is non-empty, it contains the woodcutting and mining animations, and no ID is mapped twice with different names.
- [X] T016 [US1] Wire the animation detector in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - `onAnimationChanged` for the local player only.
  - On each `GameTick`, re-observe while `localPlayer.getAnimation()` is in `AnimationCatalog`. Activity name = catalog name (FR-010a).
  - Feed `ActivityTracker` only when `ready` and `Detector.ANIMATION` is enabled.
- [X] T017 [US1] Wire the interaction detector in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - `onInteractingChanged` where the source is the local player.
  - Re-observe on `GameTick` while `getInteracting() != null`.
  - Activity name = `Combat` when the target is an NPC whose composition has an `Attack` action, otherwise `Interaction` (FR-010a).
- [X] T018 [US1] In `onGameTick` in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`, call `activityTracker.evaluate(clock.instant())` and send any signal through `SignalDispatcher`. On `GameStateChanged` other than LOGGED_IN, call `activityTracker.reset()`.

**Checkpoint**: `./gradlew build` passes. US1 is testable in-game (quickstart #1). Hand off to the user to validate.

---

## Phase 4: User Story 2 - Know when I start again (Priority: P1)

**Goal**: Emit exactly one `Activity Notifier: Active - <activity> (inactive for <duration>)` right after an Inactive signal when an enabled detector observes activity. Clear an outstanding Inactive on pause or shutdown.

**Independent Test**: Go inactive (US1), then resume chopping. One Active signal arrives within 1 s. Mouse movement alone never triggers it (quickstart #2, #3).

### Tests for User Story 2

- [X] T019 [P] [US2] Extend `src/test/java/com/activitynotifier/detection/ActivityTrackerTest.java` with:
  - INACTIVE→ACTIVE on an observation returns exactly one ACTIVE signal, with the correct duration and resumed activity name.
  - Signals strictly alternate over a flapping sequence (SC-004).
  - No ACTIVE without a preceding INACTIVE.
  - `clear(reason)` while INACTIVE returns one ACTIVE with the fixed detail; while ACTIVE or NEUTRAL it returns nothing.
  - Turning off all detectors while INACTIVE returns one ACTIVE with detail `Detection off`.

### Implementation for User Story 2

- [X] T020 [US2] Extend `ActivityTracker` in `src/main/java/com/activitynotifier/detection/ActivityTracker.java`:
  - `observe(...)` returns `Optional<Signal>` (ACTIVE on INACTIVE→ACTIVE, evaluated immediately).
  - Add `clear(String fixedDetail)` per the data-model transitions (FR-010, FR-012).
- [X] T021 [US2] In `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`, dispatch the result of every `observe(...)` immediately. In `shutDown()`, run `clientThread.invoke(() -> dispatch(activityTracker.clear("Plugin stopped")))` and then reset the state.
- [X] T022 [US2] Add the pause hotkey (FR-021) in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Register a `HotkeyListener` bound to `config.pauseHotkey()` with `KeyManager` in `startUp`, and unregister it in `shutDown`.
  - On press, call `clientThread.invoke` to toggle an in-memory `paused` flag.
  - On pause, dispatch `clear("Detection paused")` and the resource clears (added in US6). While paused, drop observations.
  - Resuming starts NEUTRAL.

**Checkpoint**: `./gradlew build` passes. US1 and US2 together give paired signals (quickstart #2, #3, #13, #14).

---

## Phase 5: User Story 3 - Easy Watchdog setup (Priority: P1)

**Goal**: A user can wire a Watchdog overlay to inactive/active in under 5 minutes, with no regex.

**Independent Test**: Follow the README recipe from scratch. The sticky overlay appears on Inactive and is dismissed on Active, on both channels (quickstart #4, #5).

### Implementation for User Story 3

- [X] T023 [US3] Write a "Watchdog integration" section in `README.md`, taken from the recipe in `contracts/signals.md`:
  - The signal format and keyword table.
  - The two-alert overlay/dismiss setup with ID `activity-notifier-inactive`.
  - Chat vs Notification Fired alert types, and why not to use both for the same signal.
  - Debounce 0.
  - The optional regex capture (`^Activity Notifier: Inactive - (.+)$`, where `$1` is the activity).
  - A note about Watchdog's banned areas.
- [X] T024 [US3] Check `src/main/java/com/activitynotifier/signals/SignalDispatcher.java` against `contracts/signals.md`:
  - Chat messages use `ChatMessageType.GAMEMESSAGE` with an empty sender name. *(Later changed to `ChatMessageType.SPAM`, at the user's request after in-game testing, so the Game chat filter "Filtered" hides signals. Watchdog alerts use message type Spam or Any. The pause confirmation stays `GAMEMESSAGE`.)*
  - The text has no colour tags (default game-message colour).
  - The notification text is identical to the chat text.
  - Fix any mismatch.

**Checkpoint**: `./gradlew build` passes. The **MVP is complete** (US1–US3). Hand off quickstart #1–#5 to the user.

---

## Phase 6: User Story 4 - Tune detection per activity (Priority: P2)

**Goal**: Specific-activity detectors with their own extra delays, combat delay that accounts for weapon speed, and a movement detector with a grace delay.

**Independent Test**: Fishing with a 3000 ms extra delay waits about 5 s; Woodcutting still waits about 2 s. A slow weapon doesn't trigger Inactive between attacks. Walking with the movement detector on stays active (quickstart #6, #7, #9).

### Tests for User Story 4

- [X] T025 [P] [US4] Extend `src/test/java/com/activitynotifier/detection/ActivityTrackerTest.java` with:
  - The effective delay uses the per-detector extra delay.
  - COMBAT adds `weaponIntervalTicks × 600`.
  - A specific detector and a general detector observed together keep the player active until both expire.
  - With MOVEMENT disabled, movement observations are ignored.

### Implementation for User Story 4

- [X] T026 [P] [US4] Create `WeaponSpeed` in `src/main/java/com/activitynotifier/detection/WeaponSpeed.java`:
  - Cache the equipped weapon's attack interval (ticks) via `ItemManager.getItemStats(id).getEquipment().getAspeed()`. Default to 4 when unknown.
  - Refresh it from `onItemContainerChanged` for `net.runelite.api.gameval.InventoryID.WORN` (weapon slot `EquipmentInventorySlot.WEAPON`).
- [X] T027 [US4] Extend the `DelayProvider` used by `ActivityTracker` in `src/main/java/com/activitynotifier/detection/ActivityTracker.java` to add `WeaponSpeed` ticks × 600 ms for `Detector.COMBAT` (FR-005)
  - *Done as:* a pure static `ActivityTracker.effectiveDelayMs(detector, base, extra, weaponTicks)` (uses `Constants.GAME_TICK_LENGTH`). The plugin's `DelayProvider` calls it, so the weapon term is unit-tested.
  - *Refinement:* when detectors expire at the same instant, the Inactive names the later one in enum order, so a SPECIFIC name (e.g. `Magic skilling`) wins over a general one (`Magic`), per FR-010a.
- [X] T028 [US4] Map catalog activities to specific detectors in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - When an animation's catalog name matches a SPECIFIC detector (Cooking, Crafting, Fishing, Firemaking, Fletching, Herblore, Mining, Woodcutting, Smithing, Sailing, Magic→MAGIC_SKILLING) and that detector is enabled, also observe the SPECIFIC detector.
  - An animation in `AnimationCatalog.HELM_ANIMATIONS` observes **only** `Detector.SAILING` (activity `Sailing`) when it is enabled, never ANIMATION or MOVEMENT.
  - Add a lookup for this to `src/main/java/com/activitynotifier/detection/Detector.java`.
- [X] T029 [US4] Wire the combat detector in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - `onHitsplatApplied` where `hitsplat.isMine()`.
  - Interaction with an attackable NPC (from T017).
  - Both observe `Detector.COMBAT` with activity `Combat` when it is enabled, and refresh `WeaponSpeed` from `onItemContainerChanged`.
  - *Refinement:* hitsplats applied **to the local player** are ignored, because `isMine()` is also true for damage taken and being hit while AFK is not activity. `WeaponSpeed` is also refreshed on the first ready tick, for when the plugin starts mid-session.
- [X] T030 [US4] Wire the movement detector in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - In `onGameTick`, compare the local player's `WorldPoint` with the previous tick's. Any change observes `Detector.MOVEMENT` with activity `Movement` (FR-006). Ship-helm animations are not movement (A1).
  - Clear the previous point on reset.
  - *Refinement:* the previous point is also cleared on `LOADING` and on pause/resume, so a region change across a loading screen isn't counted as walking.

**Checkpoint**: `./gradlew build` passes. Quickstart #6, #7 and #9 are ready for the user.

---

## Phase 7: User Story 5 - Broad activity coverage (Priority: P2)

**Goal**: An XP-gain catch-all detector, named after the skill gaining XP.

**Independent Test**: An agility course with only the XP detector on gives correct Inactive/Active pairs named `Agility` (quickstart #8).

### Tests for User Story 5

- [X] T031 [P] [US5] Write `XpGainTrackerTest` in `src/test/java/com/activitynotifier/detection/XpGainTrackerTest.java`:
  - The first sample per skill after `seed()` is silent.
  - A delta ≥ threshold returns the skill.
  - A delta < threshold returns empty.
  - `reset()` requires re-seeding.

### Implementation for User Story 5

- [X] T032 [US5] Create the pure `XpGainTracker` in `src/main/java/com/activitynotifier/detection/XpGainTracker.java`. It caches XP per `Skill` and has `seed(Map<Skill,Integer>)`, `onXp(Skill, int xp, int threshold)` returning `Optional<Skill>`, and `reset()`.
- [X] T033 [US5] Wire the XP detector in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Seed from `client.getSkillExperience(skill)` on the first `GameTick` after LOGGED_IN.
  - `onStatChanged` calls `XpGainTracker.onXp(..., config.xpThreshold())`, which observes `Detector.XP_GAIN` with the activity `skill.getName()` (FR-007, FR-010a).
  - Reset on logout or hop.

**Checkpoint**: `./gradlew build` passes. Quickstart #8 is ready for the user.

---

## Phase 8: User Story 6 - Resource threshold alerts (Priority: P2)

**Goal**: Independently toggleable alerts for low hitpoints, low prayer, low run energy, restored run energy, low oxygen and restored special attack. Each emits one alert per crossing and one recovered signal when the value crosses back.

**Independent Test**: With a hitpoints threshold of 50, dropping below it gives one `Low hitpoints`, and healing above it gives one `Hitpoints recovered` (quickstart #10).

### Tests for User Story 6

- [X] T034 [P] [US6] Write `ResourceAlertTrackerTest` in `src/test/java/com/activitynotifier/resources/ResourceAlertTrackerTest.java`:
  - The first sample is silent (UNKNOWN → NORMAL or ALERTED).
  - A LOW crossing gives one alert; fluctuating below gives none; crossing back gives one recovered.
  - HIGH direction works the same way mirrored.
  - `clear()` while ALERTED gives one recovered, and `clear()` while NORMAL or UNKNOWN gives nothing; `reset()` is silent. Turning an alert off while ALERTED is modelled as `clear()`.

### Implementation for User Story 6

- [X] T035 [P] [US6] Create the `ResourceType` enum in `src/main/java/com/activitynotifier/resources/ResourceType.java`:
  - Values: HITPOINTS, PRAYER, RUN_ENERGY_LOW, RUN_ENERGY_HIGH, OXYGEN, SPECIAL_ATTACK.
  - Fields: direction, display name, unit (points or %), enabled key, threshold key, default threshold, alert keyword and recovered keyword, exactly as in `contracts/signals.md` and `contracts/config.md`.
- [X] T036 [US6] Implement the pure `ResourceAlertTracker` in `src/main/java/com/activitynotifier/resources/ResourceAlertTracker.java`. It is a per-resource hysteresis state machine (UNKNOWN, NORMAL, ALERTED) with `sample(int value, int threshold)`, `clear()` and `reset()`, each returning `Optional<Signal>`, per data-model.md (FR-023–FR-025).
- [X] T037 [US6] Wire the resource alerts in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`. Sample each enabled resource on `GameTick` (and on `StatChanged` for HP and prayer) when `ready`, reading values as described in research R8:
  - HP = boosted HP + `VarbitID.NZONE_ABSORB_POTION_EFFECTS`, skipped when `getRealSkillLevel(HITPOINTS) ≤ threshold`.
  - Prayer = boosted level.
  - Energy = `client.getEnergy() / 100`.
  - Oxygen = `getVarbitValue(VarbitID.FOSSIL_UNDERWATER_OXYGEN) / 10`.
  - Special attack = `getVarpValue(VarPlayerID.SA_ENERGY) / 10`.
  - Dispatch the results. Reset on logout or hop. Call `clear()` on shutdown or pause (extending T021 and T022). A resource toggled off while ALERTED gets a `clear()`.
  - *Refinements:* the core guard (real level ≤ threshold → skip) is applied to **prayer** as well as hitpoints, as core Idle Notifier does. A skipped resource is treated like a turned-off one: `clear("Alert off")`, so an outstanding alert is still dismissed. Cleared recovered signals use a fixed detail (`Alert off` / `Detection paused` / `Plugin stopped`), like cleared Active signals.
- [X] T038 [US6] Check `SignalType` and `SignalFormatter` in `src/main/java/com/activitynotifier/signals/` for resource keywords: the head uses the `ResourceType` alert/recovered keyword, and the detail uses `templateResourceAlert`/`templateResourceRecovered`. Confirm `SignalFormatterTest` rule 2 covers all resource keywords.

**Checkpoint**: `./gradlew build` passes. Quickstart #10 is ready for the user.

---

## Phase 9: User Story 7 - Quick settings side panel (Priority: P3)

**Goal**: A side panel with one-click detector toggles, delay spinners, channel toggles and resource controls, kept in sync with the config screen, that can be hidden live.

**Independent Test**: Toggling Woodcutting in the panel shows up in the config screen and vice versa. "Show side panel" off removes the button immediately. A profile switch refreshes the values (quickstart #11, #12).

### Implementation for User Story 7

- [X] T039 [P] [US7] Add the nav icon `src/main/resources/com/activitynotifier/panel_icon.png`. It must be a real PNG, at most 32×32 and small in bytes (AGENTS.md resources rules).
- [X] T040 [US7] Implement `ActivityNotifierPanel extends PluginPanel` in `src/main/java/com/activitynotifier/ui/ActivityNotifierPanel.java`:
  - Sections: Signals (chat toggle, notification toggle), General detectors, Specific activities, Resource alerts.
  - The notification toggle writes `config.notificationSignals().withEnabled(b)` through `ConfigManager.setConfiguration`, so the user's tray, sound and focus options are kept (analysis U1).
  - Rows are generated from the `Detector` and `ResourceType` enums: a checkbox plus a delay or threshold spinner.
  - Writes go through `ConfigManager.setConfiguration(GROUP, key, value)`.
  - `refresh()` re-reads every value.
  - All code is EDT-only and there are no `Client` calls (FR-027, FR-028).
  - Style with `ColorScheme`/`FontManager`.
- [X] T041 [US7] Wire the panel in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Build the `NavigationButton` with the icon (via `ImageUtil.loadImageResource`).
  - Add it in `startUp` if `config.showSidePanel()`.
  - On `ConfigChanged` for group `activitynotifier`, add or remove it when the key is `showSidePanel` (FR-029), and otherwise `SwingUtilities.invokeLater(panel::refresh)`.
  - On `ProfileChanged`, refresh the panel (FR-022).
  - Remove the button in `shutDown`.
  - *Done as:* the icon is a 16×16, 328-byte PNG (an orange pulse line). Resource rows are labelled with the alert keyword (e.g. Run energy restored). Spinners cover 0–600000 ms in steps of 100 for delays, and 0–100 % or 0–9999 points for thresholds.
  - *Addition (user request, after in-game testing):* a **General** section at the top with a **Base delay** spinner (key `baseDelay`, 0–600000 ms in steps of 100).

**Checkpoint**: `./gradlew build` passes. Quickstart #11 and #12 are ready for the user.

---

## Phase 10: User Story 8 - Logout warnings (Priority: P3)

**Goal**: Optional single warnings before the idle logout and before the six-hour session limit.

**Independent Test**: Stay AFK with the idle-logout warning on. One `Idle logout soon` arrives about 20 s before the logout (quickstart #15).

### Tests for User Story 8

- [X] T042 [P] [US8] Write `LogoutWarningTrackerTest` in `src/test/java/com/activitynotifier/warnings/LogoutWarningTrackerTest.java`:
  - Idle: fires once when idle ticks ≥ timeout − buffer, and re-arms after idle ticks drop.
  - Six-hour: fires once at 340 min after login, with no fire before then.
  - `reset()` disarms both.

### Implementation for User Story 8

- [X] T043 [US8] Implement the pure `LogoutWarningTracker` in `src/main/java/com/activitynotifier/warnings/LogoutWarningTracker.java`:
  - `checkIdle(int idleTicks, int idleTimeoutTicks)` uses a 20 s buffer (`20_000 / Constants.CLIENT_TICK_LENGTH` ticks).
  - `checkSession(Instant now)` uses a 340 min threshold.
  - Also has `onLogin(Instant)` and `reset()`.
  - Each check returns `Optional<Signal>` (FR-017, FR-018).
- [X] T044 [US8] Wire the logout warnings in `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Record the login time on LOGGED_IN only when the previous state was LOGGING_IN or LOGIN_SCREEN, not HOPPING.
  - On `GameTick`, when enabled, check idle using `min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks())` and `client.getIdleTimeout()`, and check the session time.
  - Dispatch the results (FR-019: off by default).
  - *Deviation:* the six-hour timer starts on LOGGED_IN after LOGGING_IN, **HOPPING** or CONNECTION_LOST (and is disarmed on LOGIN_SCREEN). This matches core Idle Notifier, which treats each new world session as restarting the game's timer; research R9 had said hops don't count. Needs in-game confirmation.
  - *Refinement:* warnings are checked before the pause gate, so the pause hotkey doesn't silence them (they matter most while AFK). Idle warnings need idle ticks to be seen below the warning point first, so turning the option on while already idle doesn't fire at once. Core's extra in-combat condition is not ported.

**Checkpoint**: `./gradlew build` passes. Quickstart #15 is ready for the user.

---

## Phase 11: Polish & Cross-Cutting Concerns

- [X] T045 [P] Finish `README.md`: a feature overview, each detector and default, resource alerts (including that the special-attack "restored" alert sends `Special attack used` on every spec use at the 100% default), side panel, pause hotkey, a changelog section (signal keyword changes are breaking), and a configuration reference that links to the Watchdog section
- [X] T046 [P] Update `runelite-plugin.properties`: the description should mention activity, idle and Watchdog; set tags such as `notification,notifier,activity,idle,afk,watchdog,skilling`. Keep `plugins=com.activitynotifier.ActivityNotifierPlugin`.
- [X] T047 Review the lifecycle and threading of `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java` against Constitution II/III/VI:
  - Everything added in `startUp` (nav button, hotkey listener) is removed in `shutDown`.
  - No `Client` access from the EDT or AWT.
  - No per-frame work, and only `log.debug` in handlers.
  - Unused imports and fields are removed.
- [X] T048 Check every ID in `src/main/java/com/activitynotifier/` is a gameval constant, with no magic numbers (Constitution IV), and that no config `keyName` differs from `contracts/config.md`
- [X] T049 Run `./gradlew build` and confirm all tests pass
- [X] T050 Hand off to the user. Offer to run `./gradlew run`, point to the Jagex account login guide, and walk through every scenario in `specs/001-activity-detection/quickstart.md`. The feature is not complete until the user confirms it in-game (Constitution VII).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none
- **Foundational (Phase 2)**: depends on Setup. **Blocks all stories.**
- **US1 (Phase 3)**: depends on Foundational
- **US2 (Phase 4)**: depends on US1. It extends `ActivityTracker` and needs Inactive to exist before Active can be emitted.
- **US3 (Phase 5)**: depends on US2. The recipe needs both signals.
- **US4 (Phase 6)** and **US5 (Phase 7)**: depend on US1. They can follow US3 in either order.
- **US6 (Phase 8)**: depends on Foundational only, plus T021/T022 for the clear-on-pause/shutdown hooks. It is independent of US4 and US5.
- **US7 (Phase 9)**: depends on Foundational (config, `Detector`) and on `ResourceType` (T035) for the resource rows.
- **US8 (Phase 10)**: depends on Foundational only.
- **Polish (Phase 11)**: depends on every story included.

### Within Each Story

- Write the tests first. They fail until the pure class exists.
- Then the pure classes (trackers, enums), then the plugin wiring in `ActivityNotifierPlugin.java`.
- `ActivityNotifierPlugin.java` and `ActivityTracker.java` are touched by several stories. Tasks on those files are **never** [P] relative to each other.

### Parallel Opportunities

- Foundational: T004, T005, T009 and T010 can run in parallel, and so can T007 once T006 exists.
- US1: T012, T014 and T015 run in parallel with T013.
- US6: T034 and T035 run in parallel. T036 can be built while US4/US5 wiring proceeds, because it's a different file.
- US7: T039 runs in parallel with T040.
- US8: T042 and T043 can be built any time after Foundational.
- Polish: T045 and T046 can run in parallel.

## Parallel Example: User Story 1

```text
Task: "T012 Write ActivityTrackerTest in src/test/java/com/activitynotifier/detection/ActivityTrackerTest.java"
Task: "T014 Create AnimationCatalog in src/main/java/com/activitynotifier/detection/AnimationCatalog.java"
Task: "T015 Write AnimationCatalogTest in src/test/java/com/activitynotifier/detection/AnimationCatalogTest.java"
```

## Parallel Example: User Story 6

```text
Task: "T034 Write ResourceAlertTrackerTest in src/test/java/com/activitynotifier/resources/ResourceAlertTrackerTest.java"
Task: "T035 Create ResourceType in src/main/java/com/activitynotifier/resources/ResourceType.java"
```

---

## Implementation Strategy

### MVP First (US1 → US2 → US3)

1. Phase 1 Setup, then Phase 2 Foundational. Build green.
2. US1, then build, then the user validates quickstart #1.
3. US2, then build, then the user validates quickstart #2, #3, #13 and #14.
4. US3 (the README recipe), then the user validates the Watchdog overlay/dismiss loop (quickstart #4, #5). **The MVP is shippable.**

### Incremental Delivery

5. US4 + US5 (detection quality), then US6 (resource alerts), then US7 (side panel), then US8 (logout warnings), then Polish.
6. Run `./gradlew build` after every phase (Constitution workflow gate). Keep `spec.md` and this file current: tick tasks and note deviations.

---

## Notes

- [P] means a different file with no incomplete dependencies.
- Commit after each phase. Don't mix reformatting with feature changes (AGENTS.md).
- Agents must never interact with the game. Only the user validates in-game.
