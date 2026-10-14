---

description: "Task list for Custom Animation IDs"
---

# Tasks: Custom Animation IDs

**Input**: Design documents from `specs/003-custom-animation-ids/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/config.md, quickstart.md

**Tests**: Included. The plan and Constitution VII call for JUnit coverage of `CustomAnimations`, which
doesn't need the live client. In each phase, write the tests first and check that they fail before
implementing.

**Organization**: Tasks are grouped by user story, so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: The user story the task belongs to (US1Ã¢â‚¬â€œUS4 from spec.md)

## Conventions for every task

- Paths are relative to the repo root.
- Production code is in `src/main/java/com/activitynotifier/`, and tests are in
  `src/test/java/com/activitynotifier/`.
- Match the surrounding code: tabs, Allman braces, Javadoc on non-trivial methods (as in
  `AnimationCatalog`), and JUnit 4 (`org.junit.Test`, static `org.junit.Assert.*`).
- Built-in IDs in tests come from `net.runelite.api.gameval.AnimationID`. Raw numbers are fine only for
  IDs that stand for *player-typed* input (e.g. `"5216, 13149"`).
- Only `log.debug` (AGENTS.md). Plugin state is client-thread only, and panel state is EDT only.
- Run `./gradlew build` at every checkpoint. It must pass before the next phase starts.
- Don't mix reformatting with feature changes (AGENTS.md).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: A green baseline and a clean working tree before changing anything.

- [X] T001 Ask the user how to handle the uncommitted Javadoc work and `docs/` that came along from `master`: commit it on its own, or stash it. It touches `ActivityNotifierPlugin.java`, `AnimationCatalog.java` and `ActivityNotifierPanel.java`, which this feature edits, and AGENTS.md forbids mixing it into feature commits. Don't edit those files until this is settled. No file changes by the agent without the user's answer.
- [X] T002 Run `./gradlew build` on branch `003-custom-animation-ids` and confirm the build and all existing tests pass. This is the baseline; no file changes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The pure parser and cache that every story uses (data-model.md, research R1 and R2).

**Ã¢Å¡Â Ã¯Â¸Â CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 Create `src/test/java/com/activitynotifier/detection/CustomAnimationsTest.java` with failing tests:
  - `parse("5216, 13149")` returns exactly {5216, 13149}.
  - `parse("")` and `parse(null)` return an empty set.
  - The returned set is immutable: `add` throws `UnsupportedOperationException`.
  - `ids(text)` returns an equal set for the same text, and a new result after the text changes.
- [X] T004 Create `src/main/java/com/activitynotifier/detection/CustomAnimations.java`, a `public final` class:
  - `public static Set<Integer> parse(@Nullable String text)`, following research R2: split on `,`, trim, skip empty entries, keep `Integer.parseInt` results `>= 0`, ignore `NumberFormatException`, and return `ImmutableSet`.
  - Instance cache fields `lastText` (`""`) and `lastIds` (empty), plus `public Set<Integer> ids(@Nullable String text)`, which re-parses only when the text differs (null is treated as `""`).
  - Add a class Javadoc citing `specs/003-custom-animation-ids/contracts/config.md` and stating that the cache is client-thread only.
  - Make T003 pass.
- [X] T005 Checkpoint: `./gradlew build` passes.

**Checkpoint**: The parser is ready. User story work can begin.

---

## Phase 3: User Story 1 - Count my own animations as activity (Priority: P1) Ã°Å¸Å½Â¯ MVP

**Goal**: An animation ID listed in the setting counts as activity for the Animation detector, named
`Animation` (FR-001 to FR-004, FR-007, FR-008, FR-010).

**Independent Test**: With only the Animation detector on, an unknown animation goes Inactive. List its
ID, and the same animation keeps you active and resumes with `Active - Animation (Ã¢â‚¬Â¦)` (quickstart #4,
#6).

- [X] T006 [US1] In `src/main/java/com/activitynotifier/ActivityNotifierConfig.java`:
  - Add the `customAnimationIds` item to the General detectors region, exactly as in `contracts/config.md`: `String`, default `""`, position 1, with that name and description.
  - Move `detectInteraction`, `detectMovement`, `detectXpGain` and `xpThreshold` to positions 2, 3, 4 and 5. Their keys don't change.
  - Add `specs/003-custom-animation-ids/contracts/config.md` to the interface Javadoc's list of contracts.
- [X] T007 [US1] In `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Add a `private CustomAnimations customAnimations` field with a one-line Javadoc ("client thread only"), and create it in `startUp()` next to the other trackers.
  - In `observeAnimation`, replace the early `return` for an unknown name with this: if `customAnimations.ids(config.customAnimationIds()).contains(animation)`, call `observe(Detector.ANIMATION, Detector.ANIMATION.getDisplayName())`; otherwise return. Ship-helm and built-in handling stay first and unchanged (research R3, FR-004).
  - Update the method's Javadoc list: unknown animations count as `Animation` if listed in Custom animation IDs, and are otherwise ignored.
- [X] T008 [US1] Checkpoint: `./gradlew build` passes, including `AnimationCatalogTest`, unchanged (SC-003). Before Phase 7 the user can validate quickstart #4, #6 and #9Ã¢â‚¬â€œ#11 (ignore the panel column until US4).

**Checkpoint**: Custom IDs count as activity. This is the MVP.

---

## Phase 4: User Story 2 - Change the list without restarting (Priority: P1)

**Goal**: Edits, including a profile switch, apply to the next animation with no restart (FR-005).

**Independent Test**: Add an ID, and it counts. Remove it, and it doesn't. No restart (quickstart #7,
#13).

- [X] T009 [US2] Extend `src/test/java/com/activitynotifier/detection/CustomAnimationsTest.java`:
  - `ids("5216")` contains 5216. Then `ids("")` does not contain it, and `ids("13149")` contains 13149 but not 5216.
  - Calling `ids` twice with equal but distinct `String` instances (e.g. `new String("5216")`) returns the same `Set` instance, which proves the cache.
  - Fix `CustomAnimations.ids` if any test fails.
- [X] T010 [US2] Check that no other code caches the parsed set. `ActivityNotifierPlugin` must call `config.customAnimationIds()` on every lookup (research R1), so a `ProfileChanged`, which posts no per-key `ConfigChanged`, is still picked up. No file change is expected. If one is needed, record it in Notes.
- [X] T011 [US2] Checkpoint: `./gradlew build` passes.

---

## Phase 5: User Story 3 - Forgiving input (Priority: P2)

**Goal**: Hand-typed mistakes never break the valid entries (FR-006, SC-004).

**Independent Test**: The setting ` 5216 ,,13149, 5216, abc, -5 ,` makes both 5216 and 13149 count
(quickstart #8).

- [X] T012 [US3] Extend `src/test/java/com/activitynotifier/detection/CustomAnimationsTest.java` with parse cases:
  - ` 5216 ,,13149, 5216, abc, -5 ,` gives {5216, 13149}.
  - `52l6`, `1.5`, `-1` and `99999999999` (overflow) each give an empty set.
  - `0` gives {0}, and `+5216` gives {5216} (research R2).
  - `5216 13149` (no comma) gives an empty set, because it is a single invalid entry.
  - `,` and `  ` give an empty set.
  - A 300-entry generated list parses to 300 IDs.
- [X] T013 [US3] Fix `src/main/java/com/activitynotifier/detection/CustomAnimations.java` if any T012 case fails. `parse` must never throw.
- [X] T014 [US3] Checkpoint: `./gradlew build` passes.

---

## Phase 6: User Story 4 - Find out an animation's ID (Priority: P2)

**Goal**: The side panel shows the last animation's ID, copyable, and how it counts (FR-012, FR-013,
research R4 and R5).

**Independent Test**: Chop a tree, and the panel shows its ID and `Woodcutting`. Do an unknown action,
and it shows `Not counted`. Paste the ID into the setting, and the status becomes `Animation (custom)`
straight away (quickstart #1Ã¢â‚¬â€œ#5, #12, #14).

- [ ] T015 [US4] Extend `src/test/java/com/activitynotifier/detection/CustomAnimationsTest.java` with `countedAs` tests:
  - A helm ID (first element of `AnimationCatalog.HELM_ANIMATIONS`) gives `Sailing`, even if it is listed.
  - `AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE` gives `Woodcutting`, listed or not.
  - `AnimationID.MILKIT` gives `Animation`, which is the built-in MISC.
  - An unlisted unknown ID (e.g. 5216 with an empty set) gives `Not counted`.
  - The same ID listed gives `Animation (custom)`.
- [ ] T016 [US4] Add `public static String countedAs(int animation, Set<Integer> custom)` to `src/main/java/com/activitynotifier/detection/CustomAnimations.java`, in the order from data-model.md: helm, then `AnimationCatalog.activityFor`, then custom, then `Not counted`. Use constants for `Sailing`, `Animation (custom)` and `Not counted`; take `Sailing` from `Detector.SAILING.getDisplayName()` if it matches. Make T015 pass.
- [ ] T017 [US4] In `src/main/java/com/activitynotifier/ui/ActivityNotifierPanel.java`:
  - Add the fields `lastAnimation` (int, `-1`), `lastAnimationStatus` (`JLabel`) and `lastAnimationId` (`JTextField`). The text field is not editable, selectable, `SPINNER_WIDTH` wide and right-aligned.
  - In `detectorRows(Detector.Group.GENERAL)` only, add one last row with `addRow(rows, row++, lastAnimationStatus, lastAnimationId)`, with the tooltip from `contracts/config.md`.
  - Add `public void setLastAnimation(int id)` (EDT), which stores the ID and calls a private `showLastAnimation()`.
  - `showLastAnimation()` sets the field to the ID, or `-`, and the label to `Last animation: ` plus `none yet` or `CustomAnimations.countedAs(lastAnimation, CustomAnimations.parse(config.customAnimationIds()))`.
  - Call `showLastAnimation()` from `refresh()`, so list edits and profile switches update the status.
  - Update the class Javadoc if needed. It must still say "never touches the client".
- [ ] T018 [US4] In `src/main/java/com/activitynotifier/ActivityNotifierPlugin.java`:
  - Add a `private int lastAnimationShown` field ("client thread only"), set to `-1` in `startUp()`.
  - In `onAnimationChanged`, after the local-player check and before `observeAnimation`: read the animation once. If it is not `-1` and differs from `lastAnimationShown`, store it and hand it to the panel with `SwingUtilities.invokeLater`. Capture `panel` in a local and null-check it, following `refreshPanel()`.
  - Don't reset `lastAnimationShown` on logout or hop (data-model.md).
  - Update the handler's Javadoc to mention the panel update (FR-013: this happens regardless of pause and detector toggles).
- [ ] T019 [US4] Checkpoint: `./gradlew build` passes.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T020 [P] Update `README.md`:
  - In the General detectors section, add a "Custom animation IDs" subsection: what it does, that listed animations count as `Animation`, the example `5216, 13149`, and how to find an ID with the side panel's Last animation row. Mention herbiboar as the motivating example.
  - In the Side panel section, describe the Last animation row.
  - In the Configuration reference, add "Custom animation IDs" to the General detectors row.
  - In the Changelog, add an entry for this feature.
- [ ] T021 [P] Check `docs/ARCHITECTURE.md` and `docs/COMPONENTS.md`, if they are committed by then, for animation-detection descriptions that now need a line about custom IDs. Update only if they are tracked. Otherwise note it in `specs/docs-review-findings.md`.
- [ ] T022 Final `./gradlew clean build`. Record the test count in Notes.
- [ ] T023 Hand off to the user for the full in-game pass of `quickstart.md`, following AGENTS.md "Testing": offer `./gradlew run`, link the Jagex Accounts wiki, list what to test, and wait for confirmation. Record the results in Notes, and update quickstart.md if any scenario turns out to be unreachable.
- [ ] T024 Once the user confirms:
  - Mark the spec Implemented and record any deviations in Notes.
  - Update the PR #3 description (summary and testing, like PR #2) and mark the PR ready.
  - Archive the spec: `git mv specs/003-custom-animation-ids specs/done/003-custom-animation-ids`, fix the code comments and contract links that cite the old path, and update `.specify/feature.json` (no BOM).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 must be answered before any edit to the plugin or panel.
- **Foundational (Phase 2)**: depends on Setup. Blocks every story.
- **US1 (Phase 3)**: depends on Phase 2. This is the MVP.
- **US2 (Phase 4)**: depends on US1, which provides the config read on each lookup that US2 verifies.
- **US3 (Phase 5)**: depends on Phase 2 only. It is parser-only, so it can run before or after US1.
- **US4 (Phase 6)**: depends on Phase 2 and on T006 (the config getter used by the panel).
- **Polish (Phase 7)**: depends on all stories.

### Within Each Story

- Tests are written first and fail, then the implementation, then the checkpoint build.
- T012, T015 and T009 all edit `CustomAnimationsTest.java`, so they run one after another, never in
  parallel.

### Parallel Opportunities

- T020 and T021 in Polish touch different files.
- US3 (Phase 5) can be developed alongside US1 (Phase 3), because they touch different files:
  `CustomAnimations*` for US3, and `ActivityNotifierConfig` and `ActivityNotifierPlugin` for US1.

## Parallel Example: User Story 3 alongside User Story 1

```text
Developer A: T006 Ã¢â€ â€™ T007 Ã¢â€ â€™ T008   (config + plugin glue)
Developer B: T012 Ã¢â€ â€™ T013 Ã¢â€ â€™ T014   (parser edge cases)
```

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: settle the working tree, and get a green baseline.
2. Phase 2: build the parser.
3. Phase 3: config and plugin glue. **Stop here** and let the user try a herbiboar run with listed IDs.

### Incremental Delivery

1. Add US2 and US3, which mostly add tests that harden behaviour already in place.
2. Add US4: the panel row, which makes the setting usable without developer tools.
3. Polish: README, the final build, the in-game pass, and the archive.

## Notes

- Commits: one commit per phase or logical group, with spec-file updates in the same commit as the code
  they describe. Never include the pre-existing Javadoc work (T001).
- Mark tasks `[X]` as they complete, and record deviations here (CLAUDE.md "Keep the spec current").
