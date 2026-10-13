# Contract: Signal Text (Watchdog interface)

This contract is the plugin's **public interface**. Watchdog alerts written by users depend on it, so
changing a keyword is a breaking change. It must be called out in the README changelog and in the
release notes of the Plugin Hub update (Constitution V spirit: "config is a contract", applied to signal
text).

## Format

```
Activity Notifier: <Keyword> - <detail>
```

- `Activity Notifier: <Keyword>` is the **head**. It is fixed and cannot be customised (FR-014).
- `<detail>` is rendered from a user-editable template (FR-015). Default templates are below.
- Delivered on two channels:
  - **Chat** as a `GAMEMESSAGE`. In Watchdog, use a **Game Message** alert with type "Game Message" or "Any".
  - **Notification** via RuneLite's notifier. In Watchdog, use a **Notification Fired** alert.
- Chat text uses RuneLite's default game-message colour, with no colour tags and no colour setting in v1.
  The notification text is identical to the chat text.

## Head rules (unit-tested)

1. The head contains none of `[ ] { } , ! \`, because Watchdog glob mode treats them specially (research R2).
2. No head is a case-insensitive **substring** of another head. Watchdog globs match anywhere, so this
   is what guarantees that one pattern never matches another signal type (FR-014).

## Signal types

| Type | Keyword (head = `Activity Notifier: ` + keyword) | Default detail template | Example |
|---|---|---|---|
| INACTIVE | `Inactive` | `{activity}` | `Activity Notifier: Inactive - Woodcutting` |
| ACTIVE | `Active` | `{activity} (inactive for {duration})` | `Activity Notifier: Active - Woodcutting (inactive for 1m 23s)` |
| ACTIVE (cleared) | `Active` | fixed: `Detection paused` (hotkey) / `Plugin stopped` (shutdown) / `Detection off` (no detectors left enabled) | `Activity Notifier: Active - Detection paused` |
| STILL_INACTIVE (reminder) | `Still inactive` | `{activity} (for {duration})` | `Activity Notifier: Still inactive - Woodcutting (for 5m 0s)` |
| RESOURCE_ALERT | per resource, see below | `{value} (threshold {threshold})` | `Activity Notifier: Low hitpoints - 9 (threshold 10)` |
| RESOURCE_RECOVERED | per resource, see below | `{value}` | `Activity Notifier: Hitpoints recovered - 34` |
| IDLE_LOGOUT | `Idle logout soon` | `About to log out from idling` | `Activity Notifier: Idle logout soon - About to log out from idling` |
| SIX_HOUR | `Six hour logout soon` | `About to reach the six hour limit` | |

### Resource keywords

| Resource | Alert keyword | Recovered keyword |
|---|---|---|
| Hitpoints | `Low hitpoints` | `Hitpoints recovered` |
| Prayer | `Low prayer` | `Prayer recovered` |
| Run energy (low) | `Low run energy` | `Run energy recovered` |
| Run energy (high) | `Run energy restored` | `Run energy used` |
| Oxygen | `Low oxygen` | `Oxygen recovered` |
| Special attack | `Special attack restored` | `Special attack used` |

For "restored" alerts (high direction), the recovered signal fires when the value drops back below the
threshold. With the special attack default of 100%, that means every special attack you use sends one
`Special attack used`. The README must say this, and suggest a lower threshold or not using the
recovered signal if that is too noisy.

Turning a resource alert off while it is triggered, pausing, or stopping the plugin sends the recovered
signal once, so Watchdog effects are always cleared (data-model.md).

Collision check for rule 2:
- `Activity Notifier: Active` is not a substring of `Activity Notifier: Inactive`, because the character after `: ` differs.
- No keyword above is contained in another keyword's head.

## Non-signal messages

Pausing or resuming detection with the hotkey shows a plain chat confirmation, `Activity detection paused.`
or `Activity detection resumed.`. These are not signals. They do not start with `Activity Notifier: `, so
signal patterns never match them, and they are not sent as notifications.

## Template tokens

| Token | Meaning | Available in |
|---|---|---|
| `{activity}` | Activity name (FR-010a) | INACTIVE, ACTIVE, STILL_INACTIVE |
| `{duration}` | Inactive duration, formatted `45s`, `1m 23s`, or `1h 2m` | ACTIVE, STILL_INACTIVE |
| `{resource}` | Resource display name | RESOURCE_* |
| `{value}` | Current value (with `%` for percentage resources) | RESOURCE_* |
| `{threshold}` | Configured threshold | RESOURCE_ALERT |

- Unknown tokens are left as-is.
- An empty template renders the head only, with no ` - ` separator.

## Watchdog recipe (goes in the README, FR-016)

| Goal | Watchdog alert | Pattern (glob, regex off) | Action |
|---|---|---|---|
| Show an overlay when inactive | Game Message alert, type Game Message | `Activity Notifier: Inactive` | Overlay: **sticky**, ID `activity-notifier-inactive` |
| Clear it when active again | Game Message alert, type Game Message | `Activity Notifier: Active` | Dismiss Overlay, ID `activity-notifier-inactive` |

- If you use the notification channel instead, use **Notification Fired** alerts with the same patterns. Don't use both alert kinds for the same signal, or the actions fire twice.
- Keep the alert's debounce at 0, so that a quick inactive → active → inactive sequence isn't swallowed.
- To capture the activity name, turn regex on and use `^Activity Notifier: Inactive - (.+)$`. In Watchdog actions, `$1` is the activity.
- Watchdog ignores all alerts in its banned areas (bosses, raids, Inferno, etc.). That's Watchdog behaviour, not ours.
