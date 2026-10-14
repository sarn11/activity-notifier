# Contract: Configuration and Side Panel (additions)

This extends
[specs/done/001-activity-detection/contracts/config.md](../../001-activity-detection/contracts/config.md)
and [specs/done/002-damage-notifier/contracts/config.md](../../002-damage-notifier/contracts/config.md).
The group is still `activitynotifier`. Every existing key is unchanged (FR-010). The key below is new
and **permanent** (Constitution V).

## Section: General detectors (addition)

| Key | Type | Default | Position | Settings name / description |
|---|---|---|---|---|
| `customAnimationIds` | String | `""` | 1 (directly after `detectAnimation`) | "Custom animation IDs" / "Comma-separated animation IDs that also count as animation activity, e.g. 5216, 13149. The side panel shows your last animation's ID" |

The existing items in the section move down one position. Their keys don't change:

| Key | Old position | New position |
|---|---|---|
| `detectInteraction` | 1 | 2 |
| `detectMovement` | 2 | 3 |
| `detectXpGain` | 3 | 4 |
| `xpThreshold` | 4 | 5 |

## Value format

- Entries are separated by commas. Spaces around entries are ignored, and empty entries are skipped.
- An entry counts only if it is a whole number of 0 or more that fits in a Java `int`. Anything else is
  silently ignored, and the other entries still apply.
- Duplicates are harmless.
- Example: `5216, 13149`.

## Behaviour contract

- A listed animation counts for the Animation detector only, with the activity name `Animation`. It
  never counts for a specific activity (FR-003).
- Built-in and ship-helm animations keep their meaning whatever the list contains (FR-004).
- Changes apply to the next animation, with no restart (FR-005). This includes a profile switch.
- No signal heads or templates change. The `{activity}` token can now also show `Animation` because of a
  custom ID, and `Animation` was already a possible value.
- The key doesn't touch a third-party server, so it needs no IP-address `warning`.

## Side panel (addition)

Two rows at the bottom of the panel's **General detectors** section. *(Implementation deviation: the
status was moved to its own row, because it didn't fit next to the field at side-panel width.)*

| Element | Content |
|---|---|
| Row 1, left | The label `Last animation` |
| Row 1, right | The ID as a number, or `-` before the first animation. It is not editable, but can be selected and copied, and is the same width as the spinners |
| Row 2 | A small grey status line: `None yet` before the first animation, then `Counts as <name>` or `Not counted`. `<name>` is a built-in activity name (e.g. `Woodcutting`, `Animation`), `Sailing` (ship-helm), or `Animation` for a listed one. *(Changed after in-game testing: `Animation (custom)` was cut off at panel width.)* The status line spans the panel's full width |
| Tooltip (all three) | "The last animation you played, and whether it counts as activity. Copy the ID into Custom animation IDs to make it count" |

- Only the local player's animations update it. `-1` (no animation) never replaces the shown ID.
- It updates whether detection is paused or not, and whatever detectors are on (FR-013).
- The status is recomputed when settings change or the profile switches, so editing the list updates it
  straight away.
- The row doesn't read or write any config key except reading `customAnimationIds` for the status.
