# Feature Specification: Activity Detection & State Signals

**Feature Branch**: `001-activity-detection`

**Created**: 2026-10-12

**Status**: In implementation. The MVP (US1–US3) is built and partly validated in-game; see the progress section of [tasks.md](./tasks.md).

**Input**: User description: "A new plugin that detects most/all activity the player is doing, similar to
Skilling Notifications and Idle Notifier but better. Highly compatible with the Watchdog plugin so Watchdog
can easily react to this plugin's notifications. Customizable, with per-activity extra detection delays like
Skilling Notifications. Must signal both when the player goes inactive AND when they resume activity, so a
Watchdog effect started on 'inactive' (e.g. an overlay) can be removed on 'active'. Discuss which features
to keep and drop from the existing plugins rather than copying them."

## Background & Reference Plugins

Research of the three reference plugins (sources reviewed 2026-10-12) shaped this spec:

- **Watchdog** (adamk33n3r/runelite-watchdog @ 5bdceff) can react to another plugin through two
  alert types: **Game Message** alerts (chat box messages of the "Game Message" category, matched by
  glob or regex, case-insensitive for glob) and **Notification Fired** alerts (any RuneLite
  notification, matched the same way). Its "Plugin Message" receiver is an unimplemented stub, so
  inter-plugin messages are **not** a usable channel today. Watchdog supports **sticky overlays with
  an ID** and a **Dismiss Overlay** action that clears an overlay by ID — exactly the mechanism
  needed to show an "inactive" overlay and remove it on "resumed". Watchdog suppresses all of its
  alerts inside a fixed list of boss/minigame areas, and supports per-alert debounce.
- **Skilling Notifications** (erwinvc/runelite-plugins @ 692d485) detects inactivity per selected
  skill, with per-skill extra delays and a movement grace delay. It only shows an on-screen tint
  overlay (plus an optional system beep); it emits **no** chat message or RuneLite notification, so
  Watchdog cannot detect it at all.
- **Idle Notifier** (RuneLite core) detects animation, interaction/combat, and movement idleness with
  a single global delay, plus idle-logout, six-hour logout, and resource thresholds (hitpoints,
  prayer, run energy, oxygen, special attack). It emits RuneLite notifications, which Watchdog can
  match, but it never signals that the player has become active again.

### Feature decisions vs. reference plugins

| Feature | Source | Decision |
|---|---|---|
| Per-activity selection (choose which activities are watched) | Skilling Notifications | **Keep** |
| Per-activity extra detection delay | Skilling Notifications | **Keep** (core requirement) |
| Movement grace delay / "moving doesn't count as idle" | Skilling Notifications | **Keep**, as a configurable option |
| XP-gain–based activity ("receiving XP of at least N counts as active") | Skilling Notifications | **Keep** as a generic catch-all detector |
| Combat delay that accounts for weapon attack speed | Skilling Notifications | **Keep** |
| General animation / interaction / movement idle detection | Idle Notifier | **Keep** |
| Idle-logout warning and six-hour logout warning | Idle Notifier | **Keep** (optional, off by default) |
| Signal when activity **resumes** | neither | **New** (core requirement) |
| Watchdog-friendly signals (chat message and/or RuneLite notification) with stable, documented text | neither (Idle Notifier partly) | **New** (core requirement) |
| Built-in screen tint / flash overlay | Skilling Notifications | **Deferred** to a later spec; Watchdog and RuneLite notifications provide visible/audible feedback in v1 |
| Resource thresholds (hitpoints, prayer, run energy, special attack, oxygen) | Idle Notifier | **Keep**, and add a matching "recovered" signal so Watchdog effects can be cleared |
| Maniacal-monkeys–specific detection logic | Skilling Notifications | **Drop** (niche; generic XP detection covers it) |
| Side panel for quick access to settings | Skilling Notifications | **Keep**, with an option to hide the panel button |
| Hidden "enabled" flag + toggle hotkey separate from plugin on/off | Skilling Notifications | **Replace** with a single pause/resume hotkey (optional) |
| System beep | Skilling Notifications | **Drop** (use RuneLite notification sound settings instead) |

## Clarifications

### Session 2026-10-12

- Q: After an "inactive" signal, what should count as becoming active again and send the "active" signal? → A: Only an enabled detector (general or specific) observing activity; raw mouse/keyboard input never counts.
- Q: What should the default base delay be before an "inactive" signal (before per-activity extra delays)? → A: 2 seconds (user-configurable).
- Q: Which activity name should signals contain when a general detector observed the activity? → A: The most specific recognizable name (skill/activity, e.g. "Woodcutting", "Agility", "Combat"), falling back to the detector name ("Animation", "Interaction", "Movement").
- Q: Should settings be shared across all OSRS accounts or saved per account? → A: Shared (standard RuneLite behavior); users who want per-account setups use RuneLite's built-in configuration profiles.
- Q (analysis): What happens when detectors are turned off while the player is Active? → A: Never emit Inactive because of a settings change; if no detectors remain enabled, return silently to neutral (emitting an active/cleared signal if an Inactive is outstanding).
- Q (analysis): Does steering a ship count as movement or as an activity? → A: Ship-helm animations count only toward the Sailing activity detector — not the general animation detector and not the movement detector.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Know when I stop doing something (Priority: P1)

A player is woodcutting, fishing, fighting, or doing any other selected activity and tabs away. When the
activity stops (tree fell, inventory full, monster died) and stays stopped for longer than the configured
delay for that activity, the plugin emits a single "inactive" signal naming the activity.

**Why this priority**: This is the core value shared with both reference plugins; nothing else works
without reliable inactivity detection.

**Independent Test**: Enable the plugin with the Woodcutting detector enabled, chop a tree until it falls, wait, and
confirm exactly one inactive signal appears after the configured delay and none appears while chopping.

**Acceptance Scenarios**:

1. **Given** the player is actively woodcutting and the Woodcutting detector is enabled, **When** the player stops
   for longer than the base delay plus the Woodcutting extra delay, **Then** exactly one inactive
   signal is emitted naming "Woodcutting".
2. **Given** the player is actively woodcutting, **When** there is a natural pause shorter than the
   configured delay (e.g. between log gains), **Then** no inactive signal is emitted.
3. **Given** an inactive signal has already been emitted, **When** the player remains inactive,
   **Then** no further inactive signals are emitted (unless the optional reminder in FR-011 is enabled).
4. **Given** an activity has no enabled detector, **When** the player stops doing it, **Then** no signal is
   emitted for it.

---

### User Story 2 - Know when I start again, so Watchdog can clear its alert (Priority: P1)

After an inactive signal, an enabled detector observes activity again. The plugin emits a single "active"
(resumed) signal so a Watchdog alert can dismiss the overlay it showed for "inactive".

**Why this priority**: This is the user's main motivation and the gap no reference plugin fills. Without
it, Watchdog overlays started on "inactive" can never be cleared automatically.

**Independent Test**: With a Watchdog sticky overlay on the inactive signal and a Dismiss Overlay on the
active signal, go inactive, see the overlay, resume chopping, and confirm the overlay disappears.

**Acceptance Scenarios**:

1. **Given** an inactive signal was emitted, **When** an enabled detector observes activity, **Then**
   exactly one active signal is emitted within one second, naming the resumed activity.
2. **Given** no inactive signal has been emitted since the last active signal, **When** the player
   continues or switches activity, **Then** no active signal is emitted (active signals only follow
   inactive signals, so they always come in pairs).
3. **Given** the plugin is turned off or paused while an inactive signal is outstanding, **When** it
   stops, **Then** it emits an active/cleared signal so dependent Watchdog effects are cleared.

---

### User Story 3 - Easy Watchdog setup (Priority: P1)

A player who uses Watchdog wants to react to this plugin without writing complex patterns. Signals use
stable, documented text with a fixed recognizable prefix and distinct wording for inactive vs. active, so
one simple Watchdog pattern per transition (e.g. `*[Activity Notifier]*inactive*`) is enough, and
capture groups can extract the activity name.

**Why this priority**: Watchdog compatibility is explicitly called "very important".

**Independent Test**: Follow the documented Watchdog recipe from scratch and confirm both alerts fire
correctly on the first try.

**Acceptance Scenarios**:

1. **Given** default settings, **When** an inactive or active signal is emitted, **Then** it reaches
   Watchdog through the channel(s) selected in FR-013 and matches the documented pattern.
2. **Given** the inactive and active texts, **When** a pattern matching one is written as documented,
   **Then** it never matches the other.
3. **Given** the player customized the signal text, **When** a signal is emitted, **Then** the
   recognizable prefix is still present so documented patterns keep working.

---

### User Story 4 - Tune detection per activity (Priority: P2)

A player finds that some activities have long natural pauses (e.g. fishing spots moving, slow cooking)
and sets a larger extra delay for just those activities, plus a movement grace delay so walking between
spots does not count as inactive.

**Why this priority**: Prevents false positives, which is what makes the plugin "better" than Idle
Notifier's single global delay.

**Independent Test**: Set a 3-second extra delay for Fishing only; verify fishing waits the extra 3
seconds while woodcutting does not.

**Acceptance Scenarios**:

1. **Given** Fishing has an extra delay of 3000 ms, **When** fishing stops, **Then** the inactive
   signal fires no sooner than base delay + 3000 ms.
2. **Given** the movement detector is enabled, **When** the player walks between fishing spots,
   **Then** no inactive signal fires until the base delay plus the movement grace delay have elapsed after stopping.
3. **Given** the Combat detector is enabled, **When** the player uses a slow weapon, **Then** the combat delay
   accounts for the weapon's attack interval so normal attack gaps are not reported as inactivity.

---

### User Story 5 - Broad activity coverage (Priority: P2)

Beyond specific skills, the player wants general detection for "anything I'm doing" — any
skilling animation, interacting with NPCs/objects, combat, and gaining XP — so activities that are not
individually listed still count.

**Why this priority**: The request is to detect "most/all activity"; per-skill lists alone always miss
some content.

**Independent Test**: Do an activity that is not individually listed but gives XP (e.g. an agility
course) with the generic XP detector on, and confirm correct inactive/active pairs.

**Acceptance Scenarios**:

1. **Given** the generic XP detector is enabled with threshold N, **When** the player keeps gaining at
   least N XP per drop, **Then** they are treated as active.
2. **Given** general animation detection is enabled, **When** the player performs any skilling
   animation, **Then** they are treated as active.

---

### User Story 6 - Resource threshold alerts (Priority: P2)

The player wants to know when hitpoints, prayer, run energy, or oxygen drop below a threshold, or when
special attack energy is restored above one — and wants a matching "recovered" signal so a Watchdog
overlay shown for the low-resource alert can be dismissed automatically.

**Why this priority**: The user considers these alerts useful and important; they reuse the same
signal and Watchdog pairing model as activity detection.

**Independent Test**: Set a hitpoints threshold of 50, take damage below it, confirm one low signal;
eat above it, confirm one recovered signal.

**Acceptance Scenarios**:

1. **Given** the hitpoints alert is enabled with threshold T, **When** hitpoints fall from above T to
   T or below, **Then** exactly one low-hitpoints signal is emitted.
2. **Given** a low-hitpoints signal is outstanding, **When** hitpoints rise above T, **Then** exactly
   one recovered signal is emitted.
3. **Given** hitpoints fluctuate while staying below T, **When** each change occurs, **Then** no
   additional signals are emitted.
4. **Given** each resource alert is independently toggleable, **When** only the prayer alert is
   enabled, **Then** no other resource signals are emitted.

---

### User Story 7 - Quick settings side panel (Priority: P3)

The player spends a lot of time adjusting which detectors are enabled and their delays, so a side
panel gives one-click access to those settings without opening the full RuneLite config. Players who
don't want it can hide the panel's button.

**Why this priority**: Quality-of-life; the plugin is fully usable from the standard config screen.

**Independent Test**: Toggle Woodcutting and change its extra delay from the panel; confirm the
standard config screen reflects the change and vice versa. Turn on "hide side panel" and confirm the
button disappears.

**Acceptance Scenarios**:

1. **Given** the side panel is shown, **When** the player changes a setting in it, **Then** the same
   setting in the standard config screen updates, and detection uses the new value immediately.
2. **Given** the "show side panel" option is turned off, **When** the setting changes, **Then** the
   panel button is removed without restarting the plugin, and reappears when turned back on.

---

### User Story 8 - Logout warnings (Priority: P3)

Optionally, the player is warned shortly before the game would log them out for idleness, and before the
six-hour session limit, using the same Watchdog-friendly signals.

**Why this priority**: Useful and cheap, but not core activity tracking.

**Independent Test**: Stay idle until the warning window and confirm one warning signal is emitted.

**Acceptance Scenarios**:

1. **Given** idle-logout warning is enabled, **When** the player approaches the idle-logout limit,
   **Then** one warning signal is emitted before the logout happens.

---

### Edge Cases

- **Login and world hop**: state resets; the plugin waits until the player is in the world before
  evaluating inactivity.
- **Loading screens** (teleports, region changes): state is kept and timers are paused for the duration
  of the loading screen, so no inactive signal fires during or immediately after loading, and an
  outstanding inactive signal is never silently dropped. *(Implementation refinement: originally
  "state resets"; resetting would have stranded Watchdog overlays on every teleport.)*
- **Plugin enabled mid-session**: starts in a neutral state and does not emit an inactive signal for
  activity that happened before it was enabled.
- **Logout while inactive**: the outstanding inactive state is discarded; on next login the plugin
  starts fresh. (Watchdog sticky overlays may persist across logout; the documented recipe covers
  clearing them.)
- **Switching between activities with enabled detectors** (e.g. woodcutting → firemaking) without a gap longer than the
  delay: no signals.
- **Returning to the keyboard without resuming** (e.g. moving the mouse, opening the bank while the
  interaction detector is off): the player stays Inactive and no active signal is sent until an
  enabled detector observes activity.
- **Multiple enabled detectors at once**: the player is active if *any* enabled detector is live;
  inactive only when all are inactive.
- **Rapid flapping** (inactive → active → inactive within seconds): every transition is signaled once;
  no duplicate signals for a single transition.
- **Watchdog banned areas** (bosses, raids, Inferno, etc.): Watchdog ignores signals there. This plugin
  still emits them; no special handling is required.
- **Chat message channel while logged out**: signals that cannot be shown in chat are not queued and
  replayed later.
- **Plugin Hub compliance**: signals report only the local player's own state. The plugin MUST NOT
  provide boss-mechanic cues, attack counters, or any information about other players (Constitution I).

## Requirements *(mandatory)*

### Functional Requirements

**Detection**

- **FR-001**: The plugin MUST track an activity state for the local player: **Active** or **Inactive**.
- **FR-002**: Detection MUST combine two groups of detectors, and **every detector MUST be
  individually toggleable** (no detector forces another on or off):
  - **General detectors**: animation (any skilling/action animation), interaction (interacting with
    NPCs/objects, including combat targets), movement (walking and running), and XP gain.
  - **Specific activities**: at least Cooking, Crafting, Fishing, Firemaking, Fletching, Herblore,
    Mining, Woodcutting, Smithing, Sailing, Combat, and magic skilling spells (e.g. Lunar crafting
    spells), each with its own extra delay. Steering a ship (helm animations) is observed **only** by
    the Sailing detector.
- **FR-002a**: By default, the general animation, interaction, and XP-gain detectors are on; movement
  and all specific activities are off. Specific activities can be used alone (with all general
  detectors off) for precise, Skilling-Notifications–style tracking.
- **FR-003**: The player MUST be considered Active while any enabled detector has observed activity within its
  effective delay, and Inactive only when every enabled detector has exceeded its effective delay. Turning
  detectors off MUST NEVER produce an inactive signal: if no detectors remain enabled, the state returns
  silently to neutral, and if an inactive signal is outstanding, an active/cleared signal is emitted (FR-012).
- **FR-004**: The effective delay for an activity MUST be a user-configurable base delay (default
  2000 ms) plus a user-configurable per-activity extra delay (milliseconds, minimum 0).
- **FR-005**: Combat's effective delay MUST additionally account for the equipped weapon's attack
  interval.
- **FR-006**: The movement detector (walking and running) MUST have a configurable movement
  grace delay; when the movement detector is off, movement neither keeps the player Active nor
  resumes activity.
- **FR-007**: The generic XP detector MUST treat XP gains at or above a user-configured threshold as
  activity.
- **FR-008**: The plugin MUST NOT evaluate inactivity while logged out, during loading, or before the
  player is in the world after login or hop.

**Signals**

- **FR-009**: On each Active → Inactive transition, the plugin MUST emit exactly one **inactive
  signal** naming the most recently active activity.
- **FR-010**: On each Inactive → Active transition, the plugin MUST emit exactly one **active
  signal** naming the activity that resumed and how long the player was inactive. Only an enabled
  detector (general or specific) observing activity triggers this transition; mouse movement, clicks,
  or key presses on their own MUST NOT count as resuming.
- **FR-010a**: The activity name in a signal MUST be the most specific name recognizable: the specific
  activity if one matched; otherwise, for a general detector, the recognized skill or activity (e.g. a
  known woodcutting animation → "Woodcutting", an Agility XP gain → "Agility", attacking an NPC →
  "Combat"); otherwise the detector's name ("Animation", "Interaction", "Movement").
- **FR-011**: Users MAY enable an optional repeating reminder while still inactive, with a configurable
  interval; it is off by default.
- **FR-012**: When the plugin is disabled or paused while Inactive, it MUST emit an active/cleared
  signal so dependent effects can be removed.
- **FR-013**: Signals MUST be deliverable through two independently toggleable channels: a **chat box
  game message** (on by default) and a **RuneLite notification** (off by default). Either, both, or
  neither may be enabled.
- **FR-014**: Every signal's text MUST begin with a fixed, recognizable plugin prefix and use distinct,
  documented wording per signal type (inactive, active, reminder, logout warning, each resource
  low/high alert, and resource recovered) so a single simple
  pattern matches each type and never matches another.
- **FR-015**: Users MUST be able to customize the text of each signal type, with templates that can
  include the activity name and inactive duration; the fixed prefix is always kept.
- **FR-016**: The README MUST include a Watchdog recipe: the exact patterns and alert/action setup
  for "show sticky overlay on inactive, dismiss it on active".

**Logout warnings (optional)**

- **FR-017**: Users MAY enable an idle-logout warning, emitted once before the game's idle-logout limit.
- **FR-018**: Users MAY enable a six-hour session warning, emitted once before the six-hour limit.
- **FR-019**: Both logout warnings MUST be off by default and use the same signal channels.

**Built-in feedback**

- **FR-020**: v1 MUST NOT include a built-in visual alert (screen tint/flash). Visible and audible
  feedback comes from Watchdog and RuneLite notifications. A built-in alert is deferred to a later spec.

**Controls**

- **FR-021**: Users MAY bind an optional hotkey that pauses/resumes detection; pausing follows FR-012.
- **FR-022**: All settings MUST persist across sessions and MUST NOT be reset by plugin updates.
  Settings are shared across all OSRS accounts; the plugin MUST NOT add its own per-account settings.
  Per-account setups are handled by RuneLite's configuration profiles, which the plugin MUST respect
  (settings and the side panel update when the user switches profile).

**Resource threshold alerts**

- **FR-023**: The plugin MUST offer independently toggleable alerts for: low hitpoints, low prayer, low
  run energy, high (restored) run energy, low oxygen, and restored special attack energy, each with its
  own user-configurable threshold.
- **FR-024**: Each resource alert MUST emit exactly one signal when the value crosses its threshold, and
  exactly one **recovered** signal when it crosses back, so Watchdog effects can be dismissed.
- **FR-025**: Resource alerts MUST NOT re-fire while the value stays on the same side of the threshold.
- **FR-026**: Resource alerts MUST be off by default and use the channels in FR-013.

**Side panel**

- **FR-027**: The plugin MUST provide a side panel for quick access to the most frequently changed
  settings: detector toggles (general and specific), per-activity extra delays, signal channel toggles,
  and resource alert toggles and thresholds.
- **FR-028**: Settings changed in the side panel and in the standard config screen MUST stay in sync
  and apply immediately.
- **FR-029**: Users MUST be able to hide the side panel's button via a config option (shown by
  default); the change applies without restarting the plugin.

### Key Entities

- **Detector**: A source of activity the plugin can watch — either a general detector (animation,
  interaction, movement, XP gain) or a specific activity (e.g. Woodcutting, Combat). Attributes:
  display name, group (general/specific), on/off, extra delay.
- **Resource Alert**: A watched resource (hitpoints, prayer, run energy, special attack, oxygen).
  Attributes: on/off, threshold, direction (low or restored), whether an alert is outstanding.
- **Activity State**: The player's current state (Active/Inactive), the last active activity, when it
  was last observed, and whether an inactive signal is outstanding.
- **Signal**: A message emitted on a state transition. Attributes: type (inactive, active, reminder,
  idle-logout warning, six-hour warning, resource alert, resource recovered), text (prefix + template), activity name, inactive duration,
  delivery channel(s).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: During 30 minutes of continuous activity seen by an enabled detector with default settings, zero inactive
  signals are emitted.
- **SC-002**: When the player stops an activity seen by an enabled detector, the inactive signal arrives no earlier than the
  effective delay and no later than the effective delay + 1 second.
- **SC-003**: When the player resumes after an inactive signal, the active signal arrives within
  1 second.
- **SC-004**: 100% of inactive signals are followed by exactly one active/cleared signal before the next
  inactive signal (while logged in and the plugin enabled).
- **SC-005**: A user following the README recipe can set up the Watchdog "overlay on inactive, dismiss
  on active" pair in under 5 minutes, without writing regex.
- **SC-006**: The plugin causes no noticeable frame-rate drop with every detector and resource alert
  enabled.
- **SC-007**: 100% of resource alert signals are followed by exactly one recovered signal when the
  value crosses back over its threshold (while logged in and the alert enabled).
- **SC-008**: Any detector can be turned on or off from the side panel in at most 2 clicks.

## Assumptions

- Users have the Watchdog plugin installed when they want Watchdog integration; the plugin is still
  useful alone through RuneLite notifications.
- Inter-plugin messages are out of scope because Watchdog does not currently receive them; if Watchdog
  adds support later, a third channel can be added.
- Watchdog matches game messages of the "Game Message" category, so chat signals use that category.
- RuneLite only broadcasts a notification when that notification is enabled in the plugin's settings, so
  the notification channel requires the user to keep it enabled (they can still turn off the tray popup
  and sound in RuneLite's per-notification settings).
- Resource alerts report only the local player's own stats and are generic (not tied to any boss
  mechanic), consistent with RuneLite's own Idle Notifier, so they comply with Constitution I.
- A built-in screen tint/flash alert is out of scope for v1 (deferred by user decision).
- The default base delay is 2 s (clarified). Default per-activity extra delays are 0 except where
  natural pauses longer than 2 s are common; those defaults are set during planning.
- Signals carry only the local player's own activity; nothing is sent to external servers.
