<!--
Sync Impact Report
- Version change: (template, unversioned) → 1.0.0
- Modified principles: all placeholders replaced (initial ratification)
  - [PRINCIPLE_1_NAME] → I. Plugin Hub Compliance (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] → II. Client Thread Safety & Concurrency
  - [PRINCIPLE_3_NAME] → III. Performance Budget
  - [PRINCIPLE_4_NAME] → IV. Sanctioned RuneLite APIs Only
  - [PRINCIPLE_5_NAME] → V. Config Stability & User Consent
- Added principles: VI. Clean Lifecycle & Hygiene, VII. Human-Verified In-Game Testing
- Added sections: Technology & Packaging Constraints, Development Workflow & Quality Gates
- Removed sections: none
- Source: derived from AGENTS.md (RuneLite Plugin Development guidelines + Plugin Rules & Restrictions)
- Templates: plan/spec/tasks templates read the constitution at runtime; no template edits made
- Deferred TODOs: none
-->

# Activity Notifier Constitution

## Core Principles

### I. Plugin Hub Compliance (NON-NEGOTIABLE)

Activity Notifier ships on the RuneLite Plugin Hub and MUST comply with Jagex's Third-Party Client
Guidelines and RuneLite's Rejected or Rolled-Back Features list. A feature that violates any rule
below MUST NOT be specified, planned, or implemented.

- **Language**: Java 11 compatible code only. No reflection, JNI/JNA, `Unsafe` or LWJGL
  native memory access, external processes (`Process`/`ProcessBuilder`), dynamic code
  loading or classloading, runtime code generation, or Java (de)serialization.
- **Boss & combat** (bosses, raids sub-bosses, slayer bosses, demi-bosses, wave minigames):
  no next-attack prediction, projectile target/landing indicators, prayer-switch indicators,
  attack counters, automatic stand/don't-stand indicators, extra visual/audio cues for boss
  mechanics (unless a manually triggered external helper), advance hazard warnings, flinch
  helpers, prayer recommendations, NPC focus identification, or content simulation.
- **PvP**: no removing/deprioritising attack or cast options, freeze-duration indicators,
  clan opponent identification, loot previews, opponent's-opponent identification, target
  scouting, player group summaries, level-based attackability indicators, or spell targeting
  simplification.
- **Menus**: no new menu entries that send actions to the server; no conditional menu entry
  removal based on NPC type, friend status, etc.
- **Interface**: no unhiding hidden components; no moving/resizing click zones for 3D
  components, combat options, inventory, equipment, spellbook, or prayer book; no removing the
  inventory background or making it click-through; no detached-camera world interaction.
- **Input**: no injected mouse/keyboard events, no autotyping into the chatbox, no modifying
  outgoing chat messages.
- **Data & privacy**: no exposing player information over HTTP, no crowdsourcing data about
  other players, no credential storage.
- **Content**: no adult or overtly sexual content; no features whose entire function depends
  on player-provided IDs.

Rationale: a single violation gets the plugin rejected from the Hub or puts users' accounts at
risk. Notifications MUST only inform the player about state; they MUST NOT act on the player's
behalf.

### II. Client Thread Safety & Concurrency

- `Thread.sleep()` MUST NOT be used.
- `startUp()` and `shutDown()` MUST NOT block; executors are stopped with `shutdownNow()`,
  never `awaitTermination()`.
- Blocking network or disk I/O MUST NOT run on the client thread. Network work uses the OkHttp
  thread pool via `enqueue()`; callbacks that touch `client` MUST hop back with
  `clientThread.invoke()`.
- Scheduled tasks (`ScheduledFuture`) MUST be explicitly cancelled on shutdown in addition to
  shutting down their executor.
- Batched async work uses `CompletableFuture.allOf()`, not `CountDownLatch`.
- Any `Process.waitFor()` MUST pass a timeout (and Principle I forbids processes in Hub
  builds regardless).

Rationale: the client thread drives the game; stalling it freezes the client for the user.

### III. Performance Budget

- The plugin MUST NOT scan the entire scene every tick or frame. It MUST track what it needs
  from spawn/despawn and change events and maintain its own collections.
- Overlay `render` paths run every frame and MUST do minimal work: precompute on game ticks
  or events, render from cached state.
- Hot-path logging MUST use `log.debug()`; `log.info()` is reserved for one-time or
  infrequent events (production runs at INFO).

Rationale: Activity Notifier runs passively all session long; its cost must be negligible.

### IV. Sanctioned RuneLite APIs Only

- IDs MUST come from `net.runelite.api.gameval` constants (`ItemID`, `InterfaceID`,
  `VarbitID`, `AnimationID`, etc.). Magic numbers are forbidden where a gameval exists.
- Widgets MUST be looked up by gameval component ID (e.g.
  `client.getWidget(InterfaceID.X.Y)`), never by manually combining group and child IDs.
- HTTP MUST use the injected `OkHttpClient`; `HttpURLConnection`, `java.net.http.HttpClient`,
  and Apache HttpClient are forbidden. OkHttp calls MUST NOT execute on the client thread.
- JSON MUST use the injected `Gson` (derive with `.newBuilder()` if needed); never construct a
  fresh `Gson`.
- URLs are opened with `LinkBrowser`, not `java.awt.Desktop`.
- All file I/O MUST go through the `Filepath` utility (requiring `internalName` on the
  `@PluginDescriptor`); file pickers use `Filepath.Chooser`, not `JFileChooser`.

Rationale: these APIs are what Plugin Hub reviewers expect and what stays stable across
RuneLite updates.

### V. Config Stability & User Consent

- The config group name MUST be specific to this plugin (never a generic word or `example`).
- Config keys and the config group MUST NOT be renamed without a migration; renames silently
  reset users' settings.
- Any `@ConfigItem` toggling a feature that contacts a third-party server (e.g. webhooks,
  push services) MUST be disabled by default and MUST set
  `warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`.
- Unused config classes, fields, and items MUST be removed.

Rationale: user settings are long-lived; outbound traffic requires informed opt-in.

### VI. Clean Lifecycle & Hygiene

- Everything registered in `startUp()` (overlays, event subscriptions, listeners, infoboxes,
  timers, executors) MUST be unregistered or cancelled in `shutDown()`. The plugin MUST
  behave correctly when enabled mid-session and when toggled repeatedly.
- No template leftovers: no `com.example`, `ExamplePlugin`, `ExampleConfig`, or `example`
  config group anywhere (package, classes, `build.gradle` group, `settings.gradle`,
  `runelite-plugin.properties`).
- Unused imports, fields, and classes MUST be removed.

Rationale: leaked registrations cause duplicate notifications and memory growth after
re-enabling the plugin.

### VII. Human-Verified In-Game Testing

- Agents MUST NOT use screen capture or computer-use tools to interact with RuneScape; game
  input automation violates Jagex's guidelines and risks a ban.
- A feature is not done when it compiles or when the JVM starts. It is done only after the
  user confirms it in-game via `./gradlew run`, logged in per the RuneLite "Using Jagex
  Accounts" wiki instructions.
- Every task hand-off MUST tell the user what to test: the golden path and the relevant edge
  cases (e.g. enabling mid-session, world hop, logout/login, config toggles).
- Logic that does not need the live client SHOULD be covered by JUnit tests.

Rationale: only the user can observe real plugin behavior in the game.

## Technology & Packaging Constraints

- `build.gradle` MUST target Java 11 and keep the structure of the RuneLite example-plugin
  template.
- Transitive dependencies of `runelite-client` (Gson, Guice, OkHttp, etc.) MUST NOT be added
  directly to `build.gradle`.
- The repo MUST NOT contain a `META-INF/services/net.runelite.client.plugins.Plugin` file.
- Build artifacts (`.class` files, `out/`, `.tmp` directories) MUST NOT be committed.
- Icon PNGs MUST be genuine PNGs (not renamed JPEG/ICO) and sized/optimized for memory, since
  Java holds images at `width × height × 4` bytes.
- Legacy `.runelite` data folders, if any, are migrated via the `legacyDataDirectory`
  `@PluginDescriptor` field.
- The project retains a permissive license (BSD-2).

## Development Workflow & Quality Gates

- After each implementation phase, `./gradlew build` (including unit tests) MUST pass before
  the next phase begins.
- Code reformatting MUST NOT be mixed with feature changes in the same commit.
- Each plan MUST include a Constitution Check against Principles I–VII. Any Plugin Hub
  compliance concern (Principle I) blocks the plan until resolved; it cannot be waived as
  justified complexity.
- Pull requests to `runelite/plugin-hub` MUST end their description with the footer
  `Generated-by: Claude Code` when prepared with Claude Code.

## Governance

This constitution supersedes ad-hoc practice for all Spec Kit artifacts (spec, plan, tasks)
in this repository. `AGENTS.md` remains the runtime guidance file for coding agents and is the
source this constitution is derived from; the two MUST stay consistent. When RuneLite or
Jagex rules change, update `AGENTS.md` and amend this constitution in the same change.

- **Amendments**: proposed via `/speckit-constitution`, recorded in the Sync Impact Report,
  and committed with a message stating the new version.
- **Versioning**: semantic versioning. MAJOR for removing or redefining a principle; MINOR for
  adding a principle/section or materially expanding guidance; PATCH for clarifications.
- **Compliance review**: `/speckit-plan` and `/speckit-analyze` MUST check artifacts against
  this constitution; code review checks implementation against it before merge.

**Version**: 1.0.0 | **Ratified**: 2026-10-12 | **Last Amended**: 2026-10-12
