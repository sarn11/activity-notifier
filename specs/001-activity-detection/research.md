# Research: Activity Detection & State Signals

**Feature**: [spec.md](./spec.md) | **Date**: 2026-10-12

Sources reviewed:
- RuneLite **1.13.1** (current `latest.release`) source, via the runelite-api-lookup skill.
- Watchdog @ `5bdceff` (the commit pinned in plugin-hub).
- Skilling Notifications @ `692d485` (the commit pinned in plugin-hub).

Paths below are relative to those source trees.

---

## R1. How signals reach Watchdog

**Decision**: There are two independent channels, matching FR-013:
1. **Chat channel** (on by default). The plugin queues a `ChatMessageType.GAMEMESSAGE` line via `ChatMessageManager.queue(QueuedMessage)`.
2. **Notification channel** (off by default). The plugin calls `Notifier.notify(Notification, String)` with a `Notification`-typed config item that defaults to `Notification.OFF`.

**Rationale**:
- **Chat channel**:
  - Watchdog's `EventHandler.onChatMessage` matches `ChatAlert`s whose `GameMessageType` is `ANY` or `GAME_MESSAGE`. `GAME_MESSAGE` covers `GAMEMESSAGE`, `CONSOLE` and `ENGINE` (`GameMessageType.java`).
  - It strips formatting tags before matching (`Text.removeFormattingTags`), so coloured text is safe.
  - The only messages it skips are those whose sender name equals `"Watchdog"`.
- **Notification channel**:
  - `Notifier.notify(Notification, String)` (`Notifier.java:157`) returns early if the notification is disabled. Otherwise it posts `NotificationFired` **before** the "send when focused" check (`Notifier.java:173`). That means Watchdog's `NotificationFiredAlert` sees it even while the client is focused.
  - Users can turn off the tray, sound and flash per notification while keeping it enabled.
  - We use RuneLite's `Notification` config type, as the constitution's config guidance and core Idle Notifier do.

**Alternatives considered**:
- **`PluginMessage` on the EventBus**: rejected. `EventHandler.onPluginMessage` in Watchdog has no `@Subscribe` and an empty body, so Watchdog never receives these. It can be revisited if Watchdog adds a receiver.
- **Posting `NotificationFired` directly on the EventBus** (as Watchdog's own `notify()` does): rejected. It bypasses the user's RuneLite notification settings and isn't standard practice for hub plugins. `Notifier` gives the same Watchdog visibility.
- **Other ChatMessageTypes** (`CONSOLE`, `ENGINE`): rejected. `GAMEMESSAGE` is the canonical type for plugin feedback and falls under both Watchdog's `ANY` and `GAME_MESSAGE` filters.

## R2. Signal text format that is safe for Watchdog globs

**Decision**:
- Every signal starts with the fixed head `Activity Notifier: <Keyword>`, where `<Keyword>` is fixed per signal type: `Inactive`, `Active`, `Still inactive`, `Low hitpoints`, `Hitpoints recovered`, and so on.
- A user-customisable **detail** template follows the head.
- The head, and the default details, MUST NOT contain the characters `[ ] { } , ! \`.
- The full table is in [contracts/signals.md](./contracts/signals.md).

**Rationale**:
- `Util.createRegexFromGlob` in Watchdog turns `[` and `]` into a regex character class, `{a,b}` into an alternation group, and a leading `!` inside a class into negation. The original idea of a `[Activity Notifier]` prefix would silently break user globs.
- `.`, `(`, `)`, `+`, `|`, `^` and `$` are escaped by the converter, so they are safe.
- Matching uses `Matcher.find()` (unanchored), and glob matching is case-insensitive. So `Activity Notifier: Inactive` matches as a substring with no `*` needed. It does not match `Activity Notifier: Active`, and vice versa.
- `Still inactive` is used for reminders so that the `: Inactive` substring stays unique to the transition signal.
- Keeping the keyword fixed and only the detail customisable preserves FR-014 (no cross-matching), even when users edit templates (FR-015).

**Alternatives considered**:
- **Fully customisable text**: rejected. It breaks the documented patterns (SC-005).
- **A bracketed prefix**: rejected for the glob reason above.

## R3. Capture groups in Watchdog

**Decision**:
- The README recipe uses **glob** patterns for matching, so no regex is needed (SC-005).
- An optional **regex** variant extracts the activity name. For inactive: `^Activity Notifier: Inactive - (.+)$`. For active: `^Activity Notifier: Active - (.+?) \(`. Both are listed in [contracts/signals.md](./contracts/signals.md).

**Rationale**: In glob mode, `*` becomes `.*` without a group, and only `{a,b}` creates groups. Extracting the activity name therefore requires Watchdog's regex mode. The default detail templates put the activity name first, right after ` - `, which keeps the regex simple.

## R4. Evaluation cadence and clock

**Decision**:
- Activity *observations* are recorded inside event handlers as soon as they happen.
- **Inactive** transitions are evaluated on `GameTick`, every 600 ms.
- **Active** transitions are evaluated immediately when an observation arrives.
- `ActivityTracker` takes every time as an `Instant` argument, so unit tests control time directly. The plugin reads a private `Clock.systemUTC()`. *(Implementation note: the plugin no longer provides `Clock` through Guice, because a duplicate binding with RuneLite's injector would fail at startup.)*

**Rationale**:
- SC-002 allows the effective delay + 1 s. Tick granularity (≤ 600 ms) satisfies this without per-frame work (Constitution III).
- Resuming immediately on observation satisfies SC-003 (< 1 s).
- An injected clock makes the state machine pure and testable without a client.

**Alternatives considered**:
- **`ClientTick` evaluation**, as Skilling Notifications does every client tick: rejected. It costs about 30× more for no benefit at the spec's precision.
- **Timing in overlay `render()`**, as Skilling Notifications does its beep: rejected (Constitution III).

## R5. General detectors: which events

| Detector | Source events (1.13.1) | Notes |
|---|---|---|
| Animation | `AnimationChanged` (local player). An animation counts as active while `getAnimation() != -1` and is in the catalog (R6). | Core Idle Notifier uses the same event. Re-observe on `GameTick` while the animation persists, because long animations don't re-fire. |
| Interaction | `InteractingChanged` (local player is the source). The interaction stays active while `getInteracting() != null`. | Fishing spots are NPCs, so fishing also shows up here. Combat targets are reported as "Combat" (FR-010a). |
| Movement | `GameTick`: compare the local player's `WorldPoint` to the previous tick (walking and running only). | The movement grace delay (FR-006) is that detector's extra delay. Ship-helm animations do **not** count here (analysis decision A1). |
| XP gain | `StatChanged`: delta against a cached XP table. Counts if delta ≥ threshold. | The XP cache is seeded on `LOGGED_IN` so the burst of login `StatChanged` events isn't counted (the same issue Skilling Notifications works around with `xpCacheInitialized`). The name is the skill's display name (FR-010a). |

`client.getLocalPlayer()` and the other client reads happen only inside these handlers, which run on the client thread (Constitution II).

## R6. Animation catalogue (animation → activity name)

**Decision**:
- Build `AnimationCatalog`, a static `Map<Integer, Activity>` populated from **`net.runelite.api.gameval.AnimationID`** constants.
- Seed it from the 283 constants in core Idle Notifier's `checkAnimationIdle` switch (1.13.1), which are already grouped by skill.
- Add anything Skilling Notifications covers that core doesn't, such as Lunar spells, translated to gameval names.
- Keep the 16 ship-helm animations (`AnimationID.HUMAN_SAILING_*HELM*`) in a **separate** set, `HELM_ANIMATIONS`. They are observed only by the Sailing specific detector, never by the general animation detector or the movement detector (analysis decision A1). Any helm IDs that appear in core's list are moved into this set.

**Rationale**:
- Core's list is maintained by the RuneLite team and already uses gameval constants (Constitution IV). Skilling Notifications' `Constants.java` partly uses raw IDs.
- Both are BSD-2 licensed, so we can reuse them with attribution in the file header.

**Alternatives considered**:
- **Treat any `animation != -1` as activity**: rejected. Emotes, eating and teleports would count as activity.

## R7. Combat detector and weapon speed

**Decision**: The Combat specific activity is observed in three ways:
1. On `HitsplatApplied` where `hitsplat.isMine()`.
2. On `InteractingChanged` to an attackable NPC.
3. While the interaction persists.

Its effective delay is the base delay + the combat extra delay + **weapon attack interval × 600 ms**. The weapon interval comes from `ItemManager.getItemStats(weaponId).getEquipment().getAspeed()` (`ItemManager.java:394`, `ItemEquipmentStats.java:56`), defaulting to 4. It is cached on `ItemContainerChanged` for the equipment container, using the gameval `InventoryID`.

**Rationale**: This is Skilling Notifications' approach (`Utils.getAttackSpeed`), but without querying the item on every delay lookup.

## R8. Resource alerts

| Resource | Read (1.13.1) | Direction | Default threshold |
|---|---|---|---|
| Hitpoints | `client.getBoostedSkillLevel(Skill.HITPOINTS)` + `client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS)`; skipped when the real HP level ≤ threshold (core behaviour) | low | 10 |
| Prayer | `client.getBoostedSkillLevel(Skill.PRAYER)` | low | 10 |
| Run energy (low) | `client.getEnergy() / 100` (%) | low | 10 % |
| Run energy (restored) | same | high | 90 % |
| Oxygen | `client.getVarbitValue(VarbitID.FOSSIL_UNDERWATER_OXYGEN) / 10` (%) (the varbit is in tenths of a percent, as core uses it) | low | 20 % |
| Special attack | `client.getVarpValue(VarPlayerID.SA_ENERGY) / 10` (%) | high | 100 % |

- Values are evaluated on `GameTick`, as core does, plus `StatChanged` for hitpoints and prayer so alerts are prompt.
- A hysteresis state machine (see data-model) guarantees one alert and one recovery per crossing (FR-024, FR-025).
- Core's guards are kept:
  - HP alerts include Nightmare Zone absorption and are skipped when the real level is at or below the threshold.
  - Special-attack "restored" fires only when the value crosses upward.
  - No alerts are evaluated before login stats arrive.

## R9. Logout warnings

- **Idle logout**: same as core. Warn when the client's idle ticks reach `client.getIdleTimeout() − 20 s`. Idle ticks are `min(getMouseIdleTicks(), getKeyboardIdleTicks())`. The warning re-arms after any input.
- **Six-hour**: record `Instant` at `LOGGED_IN` (from a login, not a hop) and warn at 5 h 40 min (core's `SIX_HOUR_LOGOUT_WARNING_AFTER_DURATION = 340 min`). Reset on `LOGIN_SCREEN`.

## R10. Side panel and config sync

**Decision**:
- `ActivityNotifierPanel extends PluginPanel`. Its controls are generated from the `Detector` and `ResourceType` enums: a checkbox plus a delay or threshold spinner per row, so toggling any detector takes one click (SC-008).
- It writes through `ConfigManager.setConfiguration(GROUP, key, value)`.
- It refreshes from the config proxy on `ConfigChanged` (filtered to our group) and on `ProfileChanged` (`net.runelite.client.events.ProfileChanged`). Profile switching covers FR-022's requirement to respect profiles.
- All panel work happens on the EDT (`SwingUtilities.invokeLater`). The panel never touches `Client`.
- The nav button is added or removed according to the `showSidePanel` config item, in `startUp` and on `ConfigChanged` (FR-029).

**Rationale**: Writing through `ConfigManager` makes the standard config screen and the panel share one source of truth (FR-028).

**Alternatives considered**:
- **Skilling Notifications' panel**: it registers the key listener twice in `startUp`, and its hidden `enabled` flag duplicates the plugin's own on/off. Both are anti-patterns we drop.

## R11. Pause hotkey

**Decision**:
- A `Keybind` config item (default `Keybind.NOT_SET`) with a `HotkeyListener` registered and unregistered via `KeyManager`.
- The paused state lives in memory (not a setting) and resets to running on plugin start.
- The listener runs on the AWT thread, so it hops to `clientThread.invoke` before touching state or emitting the cleared signal (FR-012).

## R12. Default per-activity extra delays

The base delay is 2000 ms (clarification). Extra delays:

| Detector | Default |
|---|---|
| Movement (grace) | 2000 ms |
| XP gain | 3000 ms (XP drops are sparse, e.g. Agility) |
| Combat | 0 ms (weapon interval is added automatically) |
| All others | 0 ms |

XP gain threshold: 1 XP. Reminder interval (when enabled): 60 s.

## R13. Testing approach

Pure-Java units get JUnit 4 tests with an injected `Clock`, and need no client:
- `ActivityTracker`: transitions, pairing, flapping.
- `ResourceAlertTracker`: hysteresis.
- `SignalFormatter`: templates, and a check that the fixed head contains no forbidden glob characters.
- `AnimationCatalog`: no duplicate IDs and every entry has a name.

In-game behaviour is verified by the user via `./gradlew run` (Constitution VII); see [quickstart.md](./quickstart.md).

## R14. Plugin Hub compliance review

- Signals and alerts describe **only the local player's own state**. There are no boss-specific cues, attack counters, prayer suggestions or data about other players (Constitution I).
- Low-HP and low-prayer alerts are generic and shipped by core Idle Notifier, so they are acceptable.
- No third-party servers and no file I/O.
- Chat messages are client-side only and never sent to the server. No menu entries are added.
