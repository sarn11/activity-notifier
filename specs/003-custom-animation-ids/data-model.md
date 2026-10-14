# Data Model: Custom Animation IDs

There is no stored data beyond one config string. Everything else is derived, in memory, and rebuilt
from that string.

## Custom animation list (config)

| Field | Type | Default | Notes |
|---|---|---|---|
| `customAnimationIds` | String | `""` | Comma-separated, hand-typed. Permanent key ([contracts/config.md](./contracts/config.md)) |

## CustomAnimations (new, `detection` package, pure)

Turns the config text into a set of IDs, and tells callers how an animation counts.

| Member | Kind | Description |
|---|---|---|
| `parse(String text)` | static | Returns an immutable `Set<Integer>`. The rules are in research R2: split on `,`, trim, skip empty entries, keep whole numbers `>= 0`, ignore everything else. `null` gives an empty set. |
| `countedAs(int animation, Set<Integer> custom)` | static | Returns the panel status text for an animation. In order: a ship-helm animation gives `Sailing`, a built-in one gives its catalog activity name (e.g. `Woodcutting`, `Animation`), a listed one gives `Animation (custom)`, and anything else gives `Not counted`. |
| `ids(String text)` | instance | Returns the parsed set, re-parsing only when `text` differs from the last call (research R1). Client thread only. |
| `lastText`, `lastIds` | instance fields | The cache. Starts as `""` and an empty set. |

**Invariants**:
- `parse` never throws, whatever the input.
- Nothing in the result is negative.
- `countedAs` agrees with `observeAnimation`: an ID counts for the Animation detector exactly when
  `countedAs` returns something other than `Not counted` or `Sailing`.

## Plugin state (additions to `ActivityNotifierPlugin`)

| Field | Type | Thread | Lifecycle |
|---|---|---|---|
| `customAnimations` | `CustomAnimations` | client | Created in `startUp()`. It needs no clearing, because it holds only data derived from config. |
| `lastAnimationShown` | int | client | Set to `-1` in `startUp()`. It is updated in `onAnimationChanged` for the local player, when the animation is not `-1` and has changed. It isn't reset on logout or hop: the panel keeps showing the last ID (FR-012). |

## Panel state (additions to `ActivityNotifierPanel`)

| Field | Type | Notes |
|---|---|---|
| `lastAnimation` | int | `-1` until the first `setLastAnimation`. EDT only. |
| `lastAnimationStatus` | JLabel | The status line below the ID: `None yet`, `Counts as <name>` or `Not counted`. It is recomputed in `setLastAnimation` and `refresh()`. |
| `lastAnimationId` | JTextField | Not editable, but selectable. Shows the ID, or `-` before the first animation. |

New method: `setLastAnimation(int id)`, called on the EDT.

## Flow

```text
AnimationChanged (local player, client thread)
  ├─ id != -1 && id != lastAnimationShown → lastAnimationShown = id → invokeLater(panel.setLastAnimation(id))
  └─ observeAnimation(id)                       (also called every GameTick, unchanged)
       ├─ helm?              → observe(SAILING)                                   (unchanged)
       ├─ catalog name?      → observe(specific), observe(ANIMATION, name)        (unchanged)
       └─ customAnimations.ids(config.customAnimationIds()).contains(id)?
                             → observe(ANIMATION, "Animation")                    (new)

ConfigChanged / ProfileChanged (EDT) → panel.refresh() → status = countedAs(lastAnimation, parse(config text))
```
