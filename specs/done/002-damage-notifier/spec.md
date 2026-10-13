# Feature Specification: Damage Taken Notifier

**Feature Branch**: `002-damage-notifier`

**Created**: 2026-10-13

**Status**: Implemented and validated in-game (2026-10-13)

**Input**: User description: "Simply a notifier when you take damage. We have an HP threshold, but thats not quite the same, I want a notifier that notifies the player when they have taken damage, customizable to the amount of damage needed to trigger the notification"

## Context

Activity Notifier already has a **Low hitpoints** resource alert. It fires when the player's hitpoints
fall to or below a set level. That is a statement about the player's *state*. It does not say when the
player is being *hurt*: a player at 90/99 who takes a 15 hit gets no alert, and a player already below
the threshold gets no further alert however many hits land.

This feature adds a **Damage taken** signal. It fires when the player takes damage, and the player sets
how much damage it takes to fire. It is delivered the same way as every other Activity Notifier signal,
through the chat message and RuneLite notification channels, so the Watchdog plugin can react to it.

## Clarifications

### Session 2026-10-13

- Q: How is damage counted against the minimum? → A: Each hit on its own. Hits are never added
  together, even when several land in the same game tick.
- Q: Does damage over time (poison, venom, disease, burn) count? → A: Yes, with a setting to exclude
  it. That setting is on (counted) by default.
- Q: How are repeated signals limited in sustained combat? → A: A player-set cooldown in seconds,
  default 0 (off).
- Q (analysis U1): which damage-over-time types count? → A: **Poison and venom only.** Disease, burn,
  bleed and every other splat type never count.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get notified when I take a hit (Priority: P1)

A player is AFK skilling or training somewhere they could be attacked, such as a wilderness resource
area, a spot with aggressive monsters, or a long AFK combat session. They turn on the Damage taken alert
and leave the minimum damage at its default. When something damages them, they get a notification, so
they can look back at the game window before the situation gets worse.

**Why this priority**: This is the whole feature. Without it, nothing else has value.

**Independent Test**: Turn the alert on with a minimum of 1, then take damage from any monster. Confirm
that exactly one Damage taken signal appears in chat (and as a notification, if that channel is on) with
the damage amount. Then take a 0 hit (a blocked attack) and confirm that nothing fires.

**Acceptance Scenarios**:

1. **Given** the Damage taken alert is on with a minimum of 1, **When** a monster hits the player for 7,
   **Then** one signal is sent: `Activity Notifier: Damage taken - 7`.
2. **Given** the alert is on, **When** an attack on the player misses or is fully blocked (a 0 hit),
   **Then** no signal is sent.
3. **Given** the alert is off (the default), **When** the player takes any damage, **Then** no signal is
   sent.
4. **Given** the alert is on, **When** a different player or an NPC near the player takes damage,
   **Then** no signal is sent.

---

### User Story 2 - Only notify me for hits that matter (Priority: P1)

A player who takes frequent small hits, for example while training on low-level monsters or while
standing in a spot with weak aggressive NPCs, only cares about big hits. They set the minimum damage to
15. Small hits stay quiet, and a hit of 15 or more notifies them.

**Why this priority**: The user asked for the threshold explicitly. Without it the alert is too noisy to
leave on in most places, so it ships together with Story 1.

**Independent Test**: Set the minimum to a value above the damage a weak monster can deal and let it hit
the player: nothing fires. Lower the minimum to 1 without restarting: the next hit fires.

**Acceptance Scenarios**:

1. **Given** the minimum is 15, **When** the player takes a 14 hit, **Then** no signal is sent.
2. **Given** the minimum is 15, **When** the player takes a 15 hit, **Then** a signal is sent (the
   minimum is inclusive).
3. **Given** the minimum is 15, **When** the player changes it to 5 in settings, **Then** the next hit of
   5 or more sends a signal, with no plugin restart.
4. **Given** the minimum is 15, **When** the player takes two hits of 8 in the same game tick, **Then**
   no signal is sent, because hits are not added together (FR-004).

---

### User Story 3 - Customise and automate on the damage signal (Priority: P2)

A player uses the Watchdog plugin to flash the screen or play a sound on Activity Notifier signals. They
add a Watchdog alert that matches `Activity Notifier: Damage taken`, and they edit the signal's detail
text, for example to include their remaining hitpoints.

**Why this priority**: Signals are already the plugin's public interface, so the new signal must follow
the same contract. This only adds a template and a documented keyword on top of Story 1.

**Independent Test**: Edit the Damage taken template to `{damage} damage, {hitpoints} HP left`, take a
hit, and confirm the chat text matches. Set up the Watchdog recipe from the README and confirm it fires.

**Acceptance Scenarios**:

1. **Given** the default template, **When** the player takes a 12 hit, **Then** the text is
   `Activity Notifier: Damage taken - 12`.
2. **Given** the template `{damage} damage, {hitpoints} HP left`, **When** the player at 50 hitpoints
   takes a 12 hit, **Then** the text is `Activity Notifier: Damage taken - 12 damage, 38 HP left`.
3. **Given** an empty template, **When** a signal fires, **Then** only the head
   `Activity Notifier: Damage taken` is sent.
4. **Given** a Watchdog pattern for any existing signal (for example `Activity Notifier: Inactive`),
   **When** a Damage taken signal fires, **Then** that pattern does not match it.

---

### User Story 4 - Keep it quiet in long fights and while poisoned (Priority: P2)

A player who leaves the alert on during a long combat session doesn't want a notification for every
hit. They set a cooldown, so they get at most one signal every 10 seconds. A different player finds
poison ticks noisy and turns off counting damage over time, so only direct hits notify them.

**Why this priority**: Stories 1 and 2 work without it. It makes the alert bearable to leave on in
places where the player is hit often.

**Independent Test**: Set the cooldown to 10 seconds and let a monster hit you repeatedly. Confirm that
signals arrive no more than once every 10 seconds. Then set the cooldown to 0, turn off damage over time,
and get poisoned. Confirm that poison ticks send nothing but a direct hit still does.

**Acceptance Scenarios**:

1. **Given** a cooldown of 10 seconds, **When** the player takes a qualifying hit, **Then** a signal is
   sent, and qualifying hits in the next 10 seconds send nothing.
2. **Given** a cooldown of 10 seconds, **When** a qualifying hit lands 10 or more seconds after the last
   signal, **Then** a signal is sent for that hit.
3. **Given** a cooldown of 0 (the default), **When** the player takes two qualifying hits in the same
   game tick, **Then** two signals are sent, one per hit.
4. **Given** damage over time is counted (the default) and the minimum is 1, **When** a poison tick of 4
   lands on the player, **Then** a signal is sent.
5. **Given** damage over time is not counted, **When** a poison or venom tick lands on the player,
   **Then** no signal is sent, and a direct hit still sends one.

---

### Edge Cases

- **Damage over time** (poison and venom): counted by default. A setting excludes it (FR-005). A
  damage-over-time tick is compared with the minimum like any other hit. Disease, burn, bleed and other
  special splats never count (FR-006).
- **Rapid repeated hits** in sustained combat: limited only by the cooldown (FR-008). Hits during the
  cooldown are dropped. They are not queued, added up, or reported when the cooldown ends.
- **Several hits in the same game tick** (for example a multi-hit special attack, or two monsters
  hitting at once): each hit is judged on its own (FR-004). With no cooldown, each qualifying hit sends
  its own signal.
- **Cooldown across logout or world hop**: the cooldown is real time. Logging out does not reset it, and
  it doesn't need to survive a plugin restart.
- **Cooldown changed while running**: the new value applies to the next qualifying hit, measured from
  the last signal sent.
- **Healing splats** and other splats that do not reduce hitpoints never count as damage.
- **Damage the player deals to others**, and damage to other players, NPCs or the player's boat while
  Sailing, never counts. Only damage to the player's own character counts.
- **Nightmare Zone absorption**: a hit that absorption soaks up still shows as damage on the player, and
  it counts. The player is being hit, which is what the alert is about.
- **Damage while the player dies**: the killing hit sends a signal like any other hit if it meets the
  minimum. No special death signal is in scope.
- **Logging out, world hopping, or loading screens**: no signals are sent while the player is not logged
  in. Nothing carries over (for example, a partial total) from before the hop.
- **Plugin turned on mid-fight, or alert turned on mid-fight**: only damage taken after it is on counts.
- **Detection paused with the pause hotkey**: no Damage taken signals are sent while paused, matching
  how other alerts behave.
- **Both channels off** (no chat messages, notification off): the alert does nothing visible, the same as
  every other signal.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Players MUST be able to turn the Damage taken alert on and off in the plugin settings. It
  MUST be **off by default**, so existing users see no change after updating.
- **FR-002**: Players MUST be able to set a **minimum damage**, a whole number of 1 or more, with a
  default of **1** (any damage). Changes MUST apply right away, with no plugin restart.
- **FR-003**: When the alert is on, the plugin MUST send a Damage taken signal when the damage taken by
  the player's own character reaches the minimum. The minimum is inclusive.
- **FR-004**: Each hit MUST be compared with the minimum on its own. Hits MUST NOT be added together,
  whether they land in the same game tick or close together.
- **FR-005**: Damage-over-time hits (**poison and venom only**) MUST count by default. Players MUST be
  able to exclude them with a setting. When they are excluded, those hits never send a signal, and direct
  hits are unaffected.
- **FR-006**: Only direct damage hits and (per FR-005) poison and venom count. Hits of 0 (misses and
  fully blocked attacks), healing, disease, burn, bleed, and any other splat type MUST NOT count as
  damage.
- **FR-007**: Damage to anyone or anything other than the player's own character MUST NOT count.
- **FR-008**: Players MUST be able to set a **cooldown** in whole seconds, 0 or more, default **0**
  (off). After a signal is sent, qualifying hits MUST NOT send another signal until the cooldown has
  passed. Hits during the cooldown are dropped, not queued or added up. With a cooldown of 0, every
  qualifying hit sends its own signal.
- **FR-009**: The signal MUST follow the existing signal contract: the head
  `Activity Notifier: Damage taken`, which is fixed, followed by ` - ` and a player-editable detail
  template. The head MUST NOT contain any character that Watchdog glob patterns treat specially, and no
  existing head may be a substring of it, nor it of any existing head.
- **FR-010**: The default detail template MUST be `{damage}`. The template MUST support these tokens:
  `{damage}` (the damage that triggered the signal), `{threshold}` (the configured minimum), and
  `{hitpoints}` (the player's hitpoints after the damage). Unknown tokens are left as-is, and an empty
  template sends the head only, as for existing signals.
- **FR-011**: The signal MUST be delivered through the existing channels (chat message and RuneLite
  notification) and MUST respect the existing channel settings. It MUST NOT add a channel of its own.
- **FR-012**: The signal is a one-off event. There MUST NOT be a matching "recovered" or "cleared"
  signal.
- **FR-013**: No Damage taken signals MUST be sent while detection is paused with the pause hotkey, or
  while the player is not logged in.
- **FR-014**: The alert MUST only *report* damage that has already happened. It MUST NOT predict
  incoming damage, tell the player which attack, mechanic or source hit them, or behave differently by
  boss, NPC type or player. This keeps it within the Plugin Hub's boss and PvP rules (Constitution,
  Principle I).
- **FR-015**: The README MUST document the new alert, its settings, its keyword and template tokens, and
  a Watchdog recipe for it. The signal contract MUST list the new keyword.
- **FR-016**: Existing settings and signals MUST be unchanged. No existing setting is renamed or reset,
  and no existing signal's text changes.

### Key Entities

- **Damage taken alert settings**: whether the alert is on (default off), the minimum damage (default
  1), whether damage over time counts (default yes), the cooldown in seconds (default 0), and the detail
  template (default `{damage}`).
- **Cooldown state**: when the last Damage taken signal was sent. It is held for the session only and
  never saved.
- **Damage taken signal**: a one-off message with the fixed head `Activity Notifier: Damage taken` and a
  detail rendered from the template using the damage amount, the minimum and the remaining hitpoints.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the alert on, a minimum of 1 and no cooldown, every hit of 1 or more on the player
  produces a signal within one game tick (0.6 seconds) of the hit appearing on screen.
- **SC-002**: With a minimum of N, no hit below N produces a signal, and every qualifying hit of N or
  more does, in a test session of at least 20 hits on each side of the minimum.
- **SC-003**: Zero signals come from 0 hits, healing, or damage to anything other than the player, in a
  test session with other players and NPCs taking damage nearby.
- **SC-004**: A player can turn the alert on, set a minimum, and receive their first Damage taken signal
  in under one minute, without reading the README.
- **SC-005**: A Watchdog alert built from the README recipe matches 100% of Damage taken signals and 0%
  of other Activity Notifier signals.
- **SC-006**: With a cooldown of C seconds, no two Damage taken signals are ever less than C seconds
  apart during sustained combat.
- **SC-007**: With damage over time excluded, a poisoned or venomed player receives zero signals from
  poison or venom ticks.
- **SC-008**: Existing users who update see no new signals and no change to their settings until they
  turn the alert on.

## Assumptions

- **Placement in settings**: *(decided in planning, research R6)* the alert gets its own **Damage
  taken** settings section, because Resource alerts promises a matching recovered signal and this alert
  has none. Its template sits with the other templates in the **Signals** section.
- **Default minimum of 1**: players who turn the alert on most likely want to know about any damage.
  Those who don't can raise it.
- **Remaining hitpoints**: `{hitpoints}` shows current hitpoints, not counting Nightmare Zone
  absorption, so it matches the hitpoints orb.
- **Not tied to activity state**: the alert fires whether the player counts as active or inactive. The
  existing Low hitpoints alert is unchanged and independent. Both can fire for the same hit.
- **Side panel**: the panel's activity status does not show damage. *(Planning, research R6:)* the
  panel gets one row with the Damage taken toggle and minimum, like the resource alert rows. Cooldown and
  damage over time stay in the settings page only.
- **Plugin Hub compliance**: a generic, reactive "you were hit" signal is the same kind of feature as the
  existing Low hitpoints alert and RuneLite's own idle notifier. It reveals nothing the hit itself
  doesn't already show on screen. The plan's Constitution Check must confirm this, given FR-014.
- **Out of scope**: damage dealt by the player, damage to the player's boat while Sailing, per-source or
  per-NPC filtering, and sound or overlay effects of the plugin's own (Watchdog handles those).
