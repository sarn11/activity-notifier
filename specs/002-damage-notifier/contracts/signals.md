# Contract: Damage Taken Signal (addition to the signal contract)

This extends the signal contract in
[specs/done/001-activity-detection/contracts/signals.md](../../done/001-activity-detection/contracts/signals.md).
Format, channels, head rules and template rules there all apply unchanged. Only the additions are listed
here.

## New signal type

| Type | Keyword (head = `Activity Notifier: ` + keyword) | Default detail template | Example |
|---|---|---|---|
| DAMAGE_TAKEN | `Damage taken` | `{damage}` | `Activity Notifier: Damage taken - 12` |

- The signal is a one-off event. It has **no** recovered or cleared counterpart, and pausing or stopping
  the plugin sends nothing (FR-012).
- It is sent on the same channels as every signal: a chat `SPAM` message and/or a RuneLite notification,
  following the existing channel settings (FR-011).

## Head rules check

1. `Activity Notifier: Damage taken` contains none of `[ ] { } , ! \`.
2. It is not a substring of any existing head, and no existing head is a substring of it. "Damage" appears
   in no other keyword, and no other keyword appears inside "Damage taken".

Both rules are already enforced for every `SignalType` keyword by `SignalFormatterTest`
(`headsContainNoGlobSpecialCharacters`, `noHeadIsSubstringOfAnother`), so adding the enum value is
covered automatically.

## Template tokens (additions)

| Token | Meaning | Available in |
|---|---|---|
| `{damage}` | Damage of the hit that triggered the signal, as a plain number | DAMAGE_TAKEN |
| `{threshold}` | The configured minimum damage, as a plain number | RESOURCE_ALERT, **DAMAGE_TAKEN** |
| `{hitpoints}` | The player's current hitpoints when the signal is sent (after the hit, without Nightmare Zone absorption) | DAMAGE_TAKEN |

- As before, tokens that don't apply to a signal type (for example `{activity}` in a Damage taken
  template) are left as-is, and an empty template sends the head only.

Examples:

| Template | Hit | Hitpoints after | Text |
|---|---|---|---|
| `{damage}` (default) | 12 | 38 | `Activity Notifier: Damage taken - 12` |
| `{damage} damage, {hitpoints} HP left` | 12 | 38 | `Activity Notifier: Damage taken - 12 damage, 38 HP left` |
| `{damage} (minimum {threshold})` | 20 | 50 | `Activity Notifier: Damage taken - 20 (minimum 15)` |
| *(empty)* | 7 | 92 | `Activity Notifier: Damage taken` |

## Watchdog recipe (README addition, FR-015)

| Goal | Watchdog alert | Pattern (glob, regex off) | Action |
|---|---|---|---|
| Flash or play a sound when hit | Game Message alert, type Spam | `Activity Notifier: Damage taken` | Any: flash, sound, TTS, popup |

- With the notification channel, use a **Notification Fired** alert with the same pattern instead, not
  both.
- To capture the damage amount, turn regex on and use `^Activity Notifier: Damage taken - (\d+)`. This
  only works while the template starts with `{damage}`. In Watchdog actions, `$1` is the damage.
- Use the plugin's own cooldown to limit repeats, rather than Watchdog's debounce. That way both channels
  and all Watchdog alerts see the same signals.
