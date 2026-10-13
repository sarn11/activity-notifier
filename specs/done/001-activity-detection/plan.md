# Implementation Plan: Activity Detection & State Signals

**Branch**: `001-activity-detection` | **Date**: 2026-10-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-activity-detection/spec.md`

## Summary

Activity Notifier tracks whether the local player is **Active** or **Inactive**. It uses individually
toggleable general detectors (animation, interaction, movement, XP gain) and specific-activity
detectors, each with its own extra delay on top of a 2 s base delay.

It emits a strictly paired **Inactive** → **Active** signal sequence, plus optional resource
threshold alerts with matching **recovered** signals, and optional logout warnings.

Signals use a fixed, glob-safe text head (`Activity Notifier: <Keyword>`). Watchdog receives them through
two independent channels: a chat game message (on by default) and a RuneLite notification (off by
default). That makes a "sticky overlay on inactive, dismiss on active" Watchdog recipe trivial.

A side panel mirrors the most-used settings through `ConfigManager`, and can be hidden.

The core logic consists of pure, clock-injected state machines that are unit-tested without the
client. Thin RuneLite event handlers feed them observations.

## Technical Context

**Language/Version**: Java 11 (`options.release.set(11)` in `build.gradle`)

**Primary Dependencies**:
- RuneLite client `latest.release` (1.13.1 at plan time), `compileOnly`.
- Lombok 1.18.30.
- Used from the client: `Client`, `ClientThread`, `EventBus` events, `ChatMessageManager`, `Notifier`, `ConfigManager`, `ItemManager`, `KeyManager`/`HotkeyListener`, `ClientToolbar`/`PluginPanel`.

**Storage**: RuneLite config only (group `activitynotifier`). No files and no per-account data.

**Testing**:
- JUnit 4.12 (already in `build.gradle`) for the pure logic.
- In-game validation by the user via `./gradlew run`.

**Target Platform**: RuneLite desktop client (Plugin Hub plugin)

**Project Type**: Single-module RuneLite plugin (desktop client extension)

**Performance Goals**:
- No per-frame work; evaluation runs once per game tick (600 ms).
- O(number of detectors) work per tick (≤ 16 detectors + 6 resources).

**Constraints**:
- Client-thread-only game reads; the panel stays on the EDT.
- No blocking I/O, no reflection, no network.
- Signal heads must be glob-safe for Watchdog.

**Scale/Scope**:
- Settings: 16 detectors, 6 resource alerts, 2 logout warnings and 7 signal templates (about 60 config keys).
- Code: about 12 production classes and 4 test classes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Plugin Hub Compliance | Only the local player's own state is reported. No boss-mechanic cues, attack counters, prayer advice or data about other players. Low HP/prayer alerts are generic, as in core Idle Notifier. No menu entries, input injection or chat-input changes. Chat signals are client-side game messages. No reflection, JNI, processes or serialization. | PASS |
| II. Client Thread Safety | All `Client` reads happen in `@Subscribe` handlers (client thread). The hotkey listener (AWT) hops via `clientThread.invoke`. The panel uses only `ConfigManager` and `SwingUtilities`. No `Thread.sleep` and no executors. | PASS |
| III. Performance Budget | No scene scans. Detection is event-driven, and tick evaluation is O(detectors). No overlay is added in v1. `log.debug` only on hot paths. | PASS |
| IV. Sanctioned APIs | `gameval.AnimationID`, `VarbitID`, `VarPlayerID` and `InventoryID` are used, with no magic numbers. There is no HTTP, JSON, file I/O or URL opening, so OkHttp, Gson, Filepath and LinkBrowser are not needed. | PASS |
| V. Config Stability & Consent | The group `activitynotifier` is specific. Keys are frozen in [contracts/config.md](./contracts/config.md). The template `greeting` key is removed (never released). No third-party server features, so no warning is needed. | PASS |
| VI. Clean Lifecycle | `startUp` adds the nav button (if shown) and the hotkey listener. `shutDown` removes both, emits cleared signals, and resets the trackers. Enabling mid-session starts NEUTRAL. | PASS |
| VII. Human-Verified Testing | [quickstart.md](./quickstart.md) defines the in-game scenarios for the user. Pure logic gets JUnit coverage. | PASS |
| Packaging | Java 11, no direct Gson/Guice/OkHttp dependencies, no `META-INF/services`, BSD-2 license kept. Animation lists derived from core Idle Notifier (BSD-2) are attributed in the file header. | PASS |

**Post-design re-check (after Phase 1)**: PASS. The design artifacts add no new dependencies, threads,
I/O or game-facing features beyond those checked above.

## Project Structure

### Documentation (this feature)

```text
specs/001-activity-detection/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14
├── data-model.md        # Phase 1: detectors, state machines, signals
├── quickstart.md        # Phase 1: build checks + in-game validation scenarios
├── contracts/
│   ├── signals.md       # Signal text contract (Watchdog interface) + README recipe
│   └── config.md        # Permanent config keys and defaults
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/com/activitynotifier/
├── ActivityNotifierPlugin.java       # Lifecycle + @Subscribe glue: events → observations/samples, tick evaluation
├── ActivityNotifierConfig.java       # All config items (contracts/config.md)
├── detection/
│   ├── Detector.java                 # Enum: group, display name, config keys, defaults
│   ├── Activity.java                 # Value: name + detector
│   ├── ActivityTracker.java          # Pure state machine (Clock-injected) → Signal events
│   ├── AnimationCatalog.java         # gameval AnimationID → activity name (derived from core Idle Notifier)
│   └── WeaponSpeed.java              # Cached weapon attack interval from ItemManager
├── resources/
│   ├── ResourceType.java             # Enum: direction, unit, config keys, keywords
│   └── ResourceAlertTracker.java     # Pure hysteresis state machine per resource
├── warnings/
│   └── LogoutWarningTracker.java     # Idle-logout + six-hour warnings
├── signals/
│   ├── SignalType.java               # Keywords (contract), template keys
│   ├── Signal.java                   # Value object
│   ├── SignalFormatter.java          # Pure: head + template rendering + duration format
│   └── SignalDispatcher.java         # Chat (ChatMessageManager) + Notifier channels
└── ui/
    └── ActivityNotifierPanel.java    # PluginPanel synced through ConfigManager

src/main/resources/com/activitynotifier/
└── panel_icon.png                    # Small (≤ 32×32) genuine PNG for the nav button

src/test/java/com/activitynotifier/
├── ActivityNotifierPluginTest.java   # Existing dev-client launcher (unchanged)
├── detection/ActivityTrackerTest.java
├── detection/AnimationCatalogTest.java
├── resources/ResourceAlertTrackerTest.java
└── signals/SignalFormatterTest.java

README.md                             # Features + Watchdog recipe (FR-016)
```

**Structure Decision**:
- Single RuneLite plugin module, using the existing `com.activitynotifier` package and Gradle layout from the template.
- Logic is split into sub-packages by concern. The trackers and formatter have no RuneLite `Client` dependency, so they are unit-testable (R13).
- `ActivityNotifierPlugin` is the only class that subscribes to game events.

## Implementation Phases (input for /speckit-tasks)

Run `./gradlew build` after every phase.

1. **Foundation**:
   - Write the config interface (all keys).
   - Write `SignalType`, `SignalFormatter` and `SignalDispatcher`.
   - Remove the template `greeting`.
   - Tests: formatter and head rules.
2. **Core activity state (US1, US2)**:
   - Write `Detector`, `Activity` and `ActivityTracker`.
   - Plugin glue for the animation and interaction detectors.
   - Ready-gating on `GameState`, plus cleared signals on shutdown.
   - The inactive reminder (FR-011) and the pause hotkey (FR-021) are included here, because both are activity-state behaviour.
   - Tests: tracker.
3. **Watchdog recipe and README (US3)**: the README section from contracts/signals.md. The user validates quickstart #4 and #5.
4. **Tuning and coverage (US4, US5)**:
   - The movement detector, XP-gain detector and specific activities.
   - `AnimationCatalog` (full list) and `WeaponSpeed`.
   - Tests: catalog.
5. **Resource alerts (US6)**: `ResourceType`, `ResourceAlertTracker` and the glue. Tests: tracker.
6. **Side panel (US7)**: the panel, the show/hide toggle, and profile refresh.
7. **Logout warnings (US8)**: the reminder and pause hotkey moved to phase 2 during `/speckit-tasks`.
8. **Polish**:
   - The README feature list.
   - The plugin descriptor `description` and `tags`.
   - An icon PNG check.
   - A full quickstart pass by the user.

## Complexity Tracking

No constitution violations. Nothing to justify.
