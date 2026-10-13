# Activity Notifier

Tells you when you stop doing something, and again when you start, in a format the
[Watchdog](https://runelite.net/plugin-hub/show/watchdog) plugin can react to.

- Sends **"Inactive"** when nothing you're doing has been seen for a short delay (2 s by default, plus any extra delay per detector).
- Sends **"Active"** as soon as you resume. It comes only after an "Inactive", so the two always arrive in pairs, and a Watchdog overlay shown for "Inactive" can be removed automatically.
- Moving the mouse or pressing keys never counts as resuming. Only an enabled detector seeing activity does.
- Optional extras: "Still inactive" reminders, resource alerts (hitpoints, prayer, run energy, oxygen, special attack), and warnings before the idle and six-hour logouts.
- A side panel gives one-click access to the settings you change most.

## Detectors

Every detector can be switched on or off on its own. You count as active while **any** enabled
detector has seen activity within its delay. Each detector's delay is the **base delay** (2 s) plus
its own **extra delay**, which you can set under *Extra delays* or in the side panel.

### General detectors

| Detector | Default | Extra delay | Counts as activity |
|---|---|---|---|
| Animation | on | 0 ms | Skilling and action animations (woodcutting, mining, fishing, cooking, fletching, …) |
| Interaction | on | 0 ms | Interacting with an NPC, such as a combat target or a fishing spot |
| XP gain | on | 3000 ms | Gaining XP (at least *XP gain threshold*, 1 by default, in one drop). The extra delay covers sparse drops |
| Movement | off | 2000 ms | Walking and running. The extra delay is a grace period after you stop |

### Specific activities

Cooking, Crafting, Fishing, Firemaking, Fletching, Herblore, Mining, Woodcutting, Smithing, Sailing,
Combat and Magic skilling (e.g. Lunar crafting spells) can each be watched on their own. They are all
off by default. Turn one on to give that activity its own extra delay, e.g. a longer one for fishing.

- **Combat** adds your weapon's attack interval to its delay automatically, so the gap between slow attacks isn't reported as inactivity. It counts your own hits on a target and attacking an NPC. Being hit doesn't count.
- **Sailing (beta)** includes steering a ship. Steering counts only towards Sailing, never towards the Animation or Movement detectors. It hasn't been fully tested in-game yet, so please report anything odd.

### Activity names

Signals name the activity as specifically as they can: a specific activity's name (`Woodcutting`,
`Magic skilling`), then the skill or action a general detector recognised (`Agility` from XP gain,
`Combat` from interacting with an attackable NPC), and otherwise the detector's name
(`Animation`, `Interaction`, `Movement`).

## Resource alerts

Each alert can be turned on separately, and all are off by default. An alert fires **once** when the
value crosses its threshold. A matching "recovered" signal fires **once** when it crosses back, so a
Watchdog effect started by the alert can be dismissed.

| Alert | Default threshold | Alert head | Recovered head |
|---|---|---|---|
| Low hitpoints | 10 | `Activity Notifier: Low hitpoints` | `Activity Notifier: Hitpoints recovered` |
| Low prayer | 10 | `Activity Notifier: Low prayer` | `Activity Notifier: Prayer recovered` |
| Low run energy | 10 % | `Activity Notifier: Low run energy` | `Activity Notifier: Run energy recovered` |
| Run energy restored | 90 % | `Activity Notifier: Run energy restored` | `Activity Notifier: Run energy used` |
| Low oxygen | 20 % | `Activity Notifier: Low oxygen` | `Activity Notifier: Oxygen recovered` |
| Special attack restored | 100 % | `Activity Notifier: Special attack restored` | `Activity Notifier: Special attack used` |

- "Low" alerts fire at or below the threshold. "Restored" alerts fire at or above it, and their recovered signal fires when the value drops back below it.
- **Special attack:** at the default threshold of 100 %, every special attack you use sends one `Special attack used`. If that's too noisy, lower the threshold, or simply don't create a Watchdog alert for `Special attack used`.
- Hitpoints include Nightmare Zone absorption points. Hitpoints and prayer alerts are skipped while your real level is at or below the threshold, as in RuneLite's Idle Notifier.
- Nothing fires for values you already have when you log in. Turning an alert off while it's triggered, pausing, or stopping the plugin sends the recovered signal with the detail `Alert off`, `Detection paused` or `Plugin stopped`.

## Logout warnings

Both are off by default.

- **Idle logout warning** sends `Activity Notifier: Idle logout soon` about 20 s before the game logs you out for not touching the mouse or keyboard. It warns again only after you've used the mouse or keyboard.
- **Six hour logout warning** sends `Activity Notifier: Six hour logout soon` 5 h 40 min after you log in or hop worlds.

The pause hotkey doesn't silence these warnings.

## Side panel

The Activity Notifier button in the sidebar opens a panel with:

- the base delay
- the chat and notification channel toggles
- every detector, each with its extra delay
- every resource alert, each with its threshold

Changes in either place apply immediately, and both follow RuneLite profile switches. Changes made in
the plugin settings show up in the panel right away. RuneLite's settings page only reads values when
it's opened, though. If it was already open when you changed something in the panel, close and reopen
the plugin's settings to see the new values there. To hide the button, turn off **Show side panel**
in the settings.

## Pause hotkey

Set **Pause hotkey** in the plugin settings to pause and resume detection. Pausing while you're
inactive sends `Activity Notifier: Active - Detection paused`, so Watchdog overlays clear.
Turning the plugin off does the same, with the detail `Plugin stopped`. Resuming starts fresh:
nothing is sent for what happened while paused.

## Watchdog integration

### Signal format

Every signal starts with a fixed head that you can match on:

```
Activity Notifier: <Keyword> - <detail>
```

| Signal | Head to match | Example |
|---|---|---|
| Inactive | `Activity Notifier: Inactive` | `Activity Notifier: Inactive - Woodcutting` |
| Active again | `Activity Notifier: Active` | `Activity Notifier: Active - Woodcutting (inactive for 1m 23s)` |
| Reminder (optional) | `Activity Notifier: Still inactive` | `Activity Notifier: Still inactive - Woodcutting (for 5m 0s)` |
| Resource alert | see [Resource alerts](#resource-alerts) | `Activity Notifier: Low hitpoints - 9 (threshold 10)` |
| Resource recovered | see [Resource alerts](#resource-alerts) | `Activity Notifier: Hitpoints recovered - 34` |
| Idle logout warning | `Activity Notifier: Idle logout soon` | `Activity Notifier: Idle logout soon - About to log out from idling` |
| Six hour warning | `Activity Notifier: Six hour logout soon` | `Activity Notifier: Six hour logout soon - About to reach the six hour limit` |

- **The head never changes.** You can edit the detail text under *Signals* in the plugin settings, and patterns that match the head keep working.
- **One head never matches another signal.** For example, `Activity Notifier: Active` does not match an Inactive signal.

### Channels

Signals can be delivered two ways. Turn on either or both in *Signals*.

| Channel | Default | Watchdog alert to use |
|---|---|---|
| Chat message | on | **Game Message** alert, with message type *Spam* (or *Any*) |
| RuneLite notification | off | **Notification Fired** alert |

- **Chat signals are filtered game messages.** To keep them out of your chat box, set the chat box's *Game* tab to *Filtered*. Watchdog still receives them. To see them, set it to *On*.
- **Use one alert kind per signal.** If both channels are on and you create both alert kinds, Watchdog fires twice.
- **For the notification channel without popups,** keep the notification enabled but turn off its tray popup and sound in its options. Watchdog still receives it.

### Recipe: show an overlay while inactive, remove it when active

1. In Watchdog, create a **Game Message** alert:
   - Message type: *Spam*
   - Message: `Activity Notifier: Inactive` (glob, regex off)
   - Debounce: `0`
   - Add an **Overlay** action: tick **Sticky**, set **ID** to `activity-notifier-inactive`, and write any text, e.g. `You are inactive!`
2. Create a second **Game Message** alert:
   - Message type: *Spam*
   - Message: `Activity Notifier: Active`
   - Debounce: `0`
   - Add a **Dismiss Overlay** action with ID `activity-notifier-inactive`.

That's it. When you stop, the overlay appears; when you start again, it disappears.

Using the notification channel instead? Make both alerts **Notification Fired** alerts with the same messages.

### Capturing the activity name

Turn **regex** on in the alert and use:

- Inactive: `^Activity Notifier: Inactive - (.+)$`
- Active: `^Activity Notifier: Active - (.+?) \(`

In Watchdog actions, `$1` is the activity name, e.g. an overlay that says `Stopped $1!`.

### Notes

- Keep the debounce at `0`. Otherwise a quick inactive → active → inactive sequence can be swallowed.
- Watchdog ignores all alerts inside its disabled areas (some bosses, raids, the Inferno, etc.). That's Watchdog's own behaviour.
- When you log out or hop worlds while inactive, Activity Notifier discards its state without sending "Active". A sticky Watchdog overlay may stay up until you clear it with Watchdog's clear hotkey, or until your next Inactive → Active cycle. Loading screens (teleports, entering new areas) don't discard anything.
- Don't put `[ ] { } , !` in your glob patterns unless you mean them. Watchdog treats them as special characters.

## Configuration reference

| Section | Settings |
|---|---|
| General | Show side panel, Pause hotkey, Base delay (2000 ms) |
| Signals | Chat messages (on), RuneLite notification (off), Inactive reminders (off) and their interval (60 s), and the detail text of every signal. Tokens: `{activity}`, `{duration}`, `{resource}`, `{value}`, `{threshold}`. An empty text sends the head only |
| General detectors | Animation, Interaction, Movement, XP gain, XP gain threshold |
| Specific activities | One toggle per activity (all off) |
| Extra delays | One extra delay per detector, in milliseconds |
| Resource alerts | One toggle and one threshold per alert (all off) |
| Logout warnings | Idle logout warning, Six hour logout warning (both off) |

Settings are shared across your accounts. Use RuneLite profiles for per-account setups. For the
signal text and how to react to it, see [Watchdog integration](#watchdog-integration).

## Changelog

Signal heads are a public format. Any change to them will be listed here as a **breaking change**,
because it breaks the Watchdog alerts that match them.

- Initial release: Inactive / Active / Still inactive signals, general and specific detectors, resource alerts, logout warnings, side panel, pause hotkey.
