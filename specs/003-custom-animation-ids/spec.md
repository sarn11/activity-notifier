# Feature Specification: Custom Animation IDs

**Feature Branch**: `003-custom-animation-ids`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Lets add in the settings the option to add animation IDs, separated by a comma, so the user can just add the additional IDs they want, instead of having to update the plugin every time someone wants new functionality for animations."

## Context

The **Animation** detector counts the player as active only while they play an animation from a list
built into the plugin. Animations that aren't on that list are ignored. Any activity whose animations
the plugin doesn't know about can't keep the player active through the Animation detector. Herbiboar
tracking is an example: searching trail spots, attacking the tunnel and harvesting. At the moment the
only fix is a new plugin release with more IDs added.

This feature adds a setting where players type their own animation IDs, separated by commas. Those
animations then count as activity, just like the built-in ones. A player can cover a new or uncommon
activity themselves, as soon as they know its animation IDs (for example from RuneLite's developer
tools or the OSRS Wiki).

## Clarifications

### Session 2026-10-14

- Q: How is activity from a listed animation named in signals, and can it count toward a specific
  activity? → A: Plain IDs only. Signals name it `Animation` (for example
  `Activity Notifier: Inactive - Animation`), and it counts only for the general Animation detector.
- Q: Should the herbiboar animation IDs also be added to the built-in list in this feature? → A: No.
  The custom setting covers herbiboar. The built-in list is unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Count my own animations as activity (Priority: P1)

A player doing herbiboar runs gets "Inactive" signals while they are busy searching trail spots,
because the plugin doesn't know those animations. They find the animation IDs, type them into the new
setting, for example `5216, 13149`, and from then on those animations keep them active.

**Why this priority**: This is the whole feature. It fixes the reported herbiboar gap, and any future gap
like it, without a plugin update.

**Independent Test**: With only the Animation detector on, do an activity whose animation isn't built
in and confirm that the player counts as inactive. Add that animation's ID to the setting and do the
activity again. Confirm the player now counts as active while it plays, and becomes inactive after the
usual delay once it stops.

**Acceptance Scenarios**:

1. **Given** the setting is empty (the default), **When** the player plays an animation that isn't
   built in, **Then** it does not count as activity, exactly as before this feature.
2. **Given** the setting contains `5216`, **When** the player plays animation 5216, **Then** it counts
   as activity for the Animation detector.
3. **Given** the setting contains `5216, 13149`, **When** the player plays either animation, **Then**
   each counts as activity.
4. **Given** the player was inactive, **When** they play an animation from the setting, **Then** an
   Active signal is sent, as it would be for a built-in animation.
5. **Given** the Animation detector is turned off, **When** the player plays an animation from the
   setting, **Then** it does not count as activity.
6. **Given** another player nearby plays an animation from the setting, **When** the local player is
   idle, **Then** it does not count as activity.

---

### User Story 2 - Change the list without restarting (Priority: P1)

A player is testing which IDs they need. They add an ID, try the activity, then remove an ID they no
longer want. Each change takes effect straight away, without turning the plugin off and on again.

**Why this priority**: Finding the right IDs is trial and error, so it needs a fast loop. A restart
after every edit would make the setting painful to use.

**Independent Test**: While logged in, add an ID and play that animation: it counts. Remove the ID and
play it again: it no longer counts. Do not restart the plugin at any point.

**Acceptance Scenarios**:

1. **Given** the plugin is running, **When** the player adds an ID to the setting, **Then** the next
   time that animation plays it counts as activity.
2. **Given** the setting contains an ID, **When** the player removes it, **Then** that animation no
   longer counts as activity from then on.

---

### User Story 3 - Forgiving input (Priority: P2)

A player types the list by hand and makes small mistakes: spaces after commas, a trailing comma, the
same ID twice, or a typo such as `52l6`. The setting accepts what it can understand and ignores the
rest, so one typo doesn't break the whole list.

**Why this priority**: Story 1 works with perfectly formatted input. This makes the setting robust for
real hand-typed lists.

**Independent Test**: Enter ` 5216 ,,13149, 5216, abc, -5 ,` and confirm that animations 5216 and
13149 both count as activity, and that nothing else changes or breaks.

**Acceptance Scenarios**:

1. **Given** the setting is `5216 , 13149`, **When** either animation plays, **Then** it counts (spaces
   are ignored).
2. **Given** the setting contains empty entries (`5216,,13149,`), **When** either animation plays,
   **Then** it counts (empty entries are skipped).
3. **Given** the setting contains an entry that isn't a whole number of 0 or more (`abc`, `52l6`,
   `-5`, `1.5`), **When** the plugin reads the setting, **Then** that entry is ignored and every valid
   entry still works.
4. **Given** the same ID appears twice, **When** that animation plays, **Then** it counts once, as
   normal.

---

### Edge Cases

- **ID already built in**: listing an animation that is already in the built-in list changes nothing.
  It keeps its built-in meaning, including the specific activity it belongs to (for example a
  woodcutting animation still counts as Woodcutting).
- **Ship-helm animations**: listing a ship-steering animation does not make it count for the Animation
  detector. Steering a ship counts only as Sailing, as it does now.
- **No animation (-1)**: `-1` is not a valid entry and is ignored, so standing still never counts as
  activity.
- **Numbers too large**: an entry too large to be an animation ID is ignored like any other invalid
  entry.
- **IDs that don't exist in the game**: accepted but harmless, since the player never plays them.
- **Very long lists**: a list of a few hundred IDs works, with no noticeable effect on game
  performance.
- **Other separators**: only commas separate entries. A list typed with semicolons or spaces only, such
  as `5216 13149`, is treated as one entry, and that entry is invalid and ignored. The setting's
  description must say "comma-separated".
- **Plugin enabled mid-session, logout, world hop**: the list applies whenever the plugin is running.
  Nothing is stored outside the setting itself.
- **Detection paused** with the pause hotkey: listed animations count no more than built-in ones while
  paused.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Players MUST be able to enter a comma-separated list of animation IDs in the plugin
  settings, as a free-text setting next to the Animation detector toggle. The default MUST be empty,
  so existing users see no change after updating.
- **FR-002**: While the Animation detector is on, an animation played by the player's own character
  whose ID is in the list MUST count as activity for the Animation detector, in the same way as a
  built-in animation. It MUST respect the Animation detector's extra delay, the base delay and
  pausing.
- **FR-003**: Activity from a listed animation MUST be named `Animation` in signals (for example
  `Activity Notifier: Active - Animation (inactive for 12s)`). It MUST count only for the general
  Animation detector, never for a specific activity such as Woodcutting. The input holds plain IDs
  only, with no labels.
- **FR-004**: Built-in animations MUST keep their current meaning. Listing a built-in animation MUST
  NOT change which activity it counts as. Listing a ship-helm animation MUST NOT make it count for
  anything but Sailing.
- **FR-005**: Changes to the list MUST take effect without restarting the plugin or logging out.
- **FR-006**: Parsing MUST be forgiving. Spaces around entries are ignored, empty entries are skipped,
  duplicates count once, and any entry that isn't a whole number from 0 up to the largest valid ID is
  ignored without affecting the valid entries.
- **FR-007**: Animations played by anyone other than the player's own character MUST NOT count, as for
  built-in animations.
- **FR-008**: The setting MUST only widen what counts as the player's *own* activity. It MUST NOT be
  usable to watch NPCs, bosses or other players, and nothing else in the plugin may depend on it. The
  plugin's built-in detection is complete without it. This keeps it within the Plugin Hub rule against
  features whose entire function depends on player-provided IDs, and within the boss and PvP rules
  (Constitution, Principle I).
- **FR-009**: The setting's description MUST tell the player to separate IDs with commas and where to
  find animation IDs. The README MUST document the setting, with an example and how to find an
  animation ID in RuneLite's developer tools.
- **FR-010**: Existing settings and signals MUST be unchanged. No existing setting is renamed or reset,
  and no existing signal's text changes.
- **FR-011**: The list MUST NOT noticeably slow the game, however often the player animates and
  however long the list is (up to a few hundred IDs).

### Key Entities

- **Custom animation list**: the player's text setting, default empty, holding comma-separated
  animation IDs.
- **Parsed custom animation set**: the valid IDs taken from that text. It is rebuilt whenever the
  setting changes and is never saved anywhere else.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A player who knows an animation ID can make that animation count as activity in under one
  minute, without a plugin update or restart.
- **SC-002**: After adding the herbiboar tracking animation IDs, a player doing herbiboar runs with
  only the Animation detector on gets no Inactive signals while they keep searching trail spots within
  the configured delay.
- **SC-003**: With the setting empty, the plugin behaves identically to the previous version. Existing
  tests pass unchanged, and no new signals appear for existing users.
- **SC-004**: Any mix of valid and invalid entries never stops the valid entries from working, and
  never produces an error the player has to deal with.
- **SC-005**: Built-in animations behave the same whatever the list contains.

## Assumptions

- **Placement**: the setting sits in the **General detectors** section, directly under the Animation
  toggle, because it only affects that detector.
- **Side panel**: the side panel does not show or edit the list. It is a settings-page-only option,
  like the extra delays.
- **No per-ID delays**: listed animations use the Animation detector's delays. Per-ID delays are out of
  scope.
- **No removing built-in animations**: the setting only adds animations. A way to stop a built-in
  animation from counting is out of scope.
- **Where IDs come from**: players find IDs themselves, for example with RuneLite's developer tools,
  which show the player's current animation, or from the OSRS Wiki. The plugin doesn't look IDs up or
  check that they exist.
- **Built-in list unchanged**: no animations are added to the built-in list in this feature,
  including herbiboar's. Herbiboar players use the custom setting.
