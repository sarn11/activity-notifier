# Implementation Plan: Custom Animation IDs

**Branch**: `003-custom-animation-ids` | **Date**: 2026-10-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-custom-animation-ids/spec.md`

## Summary

Add a **Custom animation IDs** setting: a comma-separated list of animation IDs that count as activity
for the general Animation detector, named `Animation` in signals. Add a read-only **Last animation** row
to the side panel, so players can find IDs without developer tools.

Technical approach:
- A pure `CustomAnimations` class in the `detection` package parses the config text forgivingly and
  caches the parsed set until the text changes (research R1, R2). It also works out the panel's status
  text (`countedAs`).
- `observeAnimation` checks custom IDs only after the ship-helm and built-in checks, so built-in
  behaviour can't change (R3).
- `onAnimationChanged` sends each new local-player animation ID to the panel on the EDT. The panel
  works out the status from config itself, so editing the list updates it straight away (R4, R5).

## Technical Context

**Language/Version**: Java 11 (`options.release.set(11)` in `build.gradle`)

**Primary Dependencies**:
- RuneLite client `latest.release` (`compileOnly`) and Lombok.
- No new API use. `AnimationChanged`, `Actor.getAnimation`, `ConfigManager` and Swing are already
  used.

**Storage**: RuneLite config only (group `activitynotifier`), with 1 new key (`customAnimationIds`).
The parsed set and the last animation are in memory only.

**Testing**:
- JUnit 4 for `CustomAnimations` (parsing, caching, `countedAs`).
- In-game validation by the user ([quickstart.md](./quickstart.md)).

**Target Platform**: RuneLite desktop client (Plugin Hub plugin)

**Project Type**: Single-module RuneLite plugin

**Performance Goals**:
- O(1) set lookup per observed animation, at most twice per tick.
- Re-parsing happens only when the config text changes.
- At most one EDT update per *new* animation.

**Constraints**:
- Client-thread state only for the plugin's fields. The panel touches config and Swing on the EDT only.
- No new threads, I/O or network.

**Scale/Scope**:
- 1 new production class (`CustomAnimations`). Edits to 3: `ActivityNotifierConfig`,
  `ActivityNotifierPlugin`, `ActivityNotifierPanel`. Plus the README.
- 1 new test class.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Plugin Hub Compliance | The setting is an optional extension of built-in detection, empty by default. The plugin's function doesn't depend on it, so the "entire function depends on player-provided IDs" rule doesn't apply. Only the local player's animations are matched or displayed, so it can't watch bosses, NPCs or other players, and no boss or PvP rule is touched (FR-007, FR-008, research R7). No menus, input or chat changes. No reflection, JNI, processes or serialization. | PASS |
| II. Client Thread Safety | Plugin state (`customAnimations`, `lastAnimationShown`) is touched only in `@Subscribe` handlers on the client thread. The panel gets the ID through `SwingUtilities.invokeLater` and reads config on the EDT, as `refresh()` already does. No sleeps or executors. | PASS |
| III. Performance Budget | Event-driven, with no scene scans and no overlay work. The parse is cached (FR-011). No logging on the hot path. | PASS |
| IV. Sanctioned APIs | No new IDs are hard-coded. Built-in IDs stay on `gameval.AnimationID`. Custom IDs are player data by design, not magic numbers in the code. No HTTP, JSON or file I/O. | PASS |
| V. Config Stability & Consent | One new permanent key, frozen in [contracts/config.md](./contracts/config.md). Existing keys are untouched. Only display positions in General detectors shift, which needs no migration. Empty by default. No third-party server. | PASS |
| VI. Clean Lifecycle | No new registrations or listeners. `startUp` creates fresh state. The panel is rebuilt on each `startUp`, as now. Works when enabled mid-session. | PASS |
| VII. Human-Verified Testing | [quickstart.md](./quickstart.md) has 14 in-game scenarios plus edge cases. The pure logic gets JUnit coverage. | PASS |
| Packaging | No new dependencies or resources. Java 11. BSD-2 kept. | PASS |

**Post-design re-check (after Phase 1)**: PASS.
- The design adds no threads, I/O, dependencies or game-facing behaviour beyond the above.
- The judgment call is Principle I's player-provided-IDs rule. It is recorded in research R7 and
  enforced by FR-008. Matching *other actors'* animations would need a fresh review.

## Project Structure

### Documentation (this feature)

```text
specs/003-custom-animation-ids/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R7
├── data-model.md        # Phase 1: CustomAnimations, plugin and panel state, flow
├── quickstart.md        # Phase 1: build checks + in-game validation scenarios
├── contracts/
│   └── config.md        # New permanent key, value format, panel row (extends 001 and 002)
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/com/activitynotifier/
├── ActivityNotifierPlugin.java       # EDIT: customAnimations + lastAnimationShown fields; startUp init;
│                                     #       onAnimationChanged sends new IDs to the panel; observeAnimation custom branch
├── ActivityNotifierConfig.java       # EDIT: customAnimationIds at General detectors position 1; later items +1
├── detection/
│   └── CustomAnimations.java         # NEW: pure parse / countedAs, plus a cached ids(text)
└── ui/
    └── ActivityNotifierPanel.java    # EDIT: Last animation row (status label + read-only ID field), setLastAnimation, refresh

src/test/java/com/activitynotifier/
└── detection/CustomAnimationsTest.java  # NEW

README.md                             # EDIT: setting, how to find an ID with the panel, config reference, changelog
```

**Structure Decision**:
- `CustomAnimations` sits next to `AnimationCatalog` in `detection`, the package that owns animation
  meaning. It has no `Client` dependency, so it is unit-testable.
- `ActivityNotifierPlugin` stays the only event subscriber, and the panel stays client-free.

## Implementation Phases (input for /speckit-tasks)

Run `./gradlew build` after every phase.

1. **Parsing core**:
   - Add `CustomAnimations` with `parse`, `ids` (the cache) and `countedAs`.
   - Tests:
     - Parsing: valid lists, spaces, empty entries, duplicates, `abc`, `52l6`, `1.5`, `-5`, `-1`,
       overflow, `null` and blank.
     - Caching: the same text returns the same set, and new text re-parses.
     - `countedAs`: a helm ID gives `Sailing`, a built-in ID gives its name, a built-in ID that is also
       listed still gives its name, a listed ID gives `Animation (custom)`, and anything else gives
       `Not counted`.
2. **Custom IDs count as activity (US1, US2, US3)**:
   - Add the `customAnimationIds` config item and shift the later positions.
   - Plugin: create `CustomAnimations` in `startUp`, and add the custom branch at the end of
     `observeAnimation`.
   - The user can now validate quickstart #4 (status column aside), #6–#11 and #13.
3. **Last animation in the side panel (US4)**:
   - Add the panel row, `setLastAnimation` and the status recompute in `refresh()`.
   - Plugin: `lastAnimationShown`, and the change check in `onAnimationChanged` with `invokeLater`.
   - Quickstart #1–#5, #12 and #14.
4. **Docs and polish**:
   - README: a feature section under General detectors (what it does, an example, finding an ID with the
     panel). Add the key to the Configuration reference, describe the new row in the Side panel section,
     and add a changelog entry.
   - Check the class Javadoc of `ActivityNotifierConfig` cites the 003 contract.
   - Do a full quickstart pass with the user. Archive the spec to `specs/done/` once the PR is ready.

## Complexity Tracking

No constitution violations. Nothing to justify.
