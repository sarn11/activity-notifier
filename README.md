# Activity Notifier

Tells you when you stop doing something, and again when you start, in a format the
[Watchdog](https://runelite.net/plugin-hub/show/watchdog) plugin can react to.

- Sends **"Inactive"** when nothing you're doing has been seen for a short delay (2 s by default, plus any extra delay per detector).
- Sends **"Active"** as soon as you resume. It comes only after an "Inactive", so the two always arrive in pairs, and a Watchdog overlay shown for "Inactive" can be removed automatically.
- Moving the mouse or pressing keys never counts as resuming. Only an enabled detector seeing activity does.

## Detectors

Every detector can be switched on or off on its own.

| Detector | Default | Counts as activity |
|---|---|---|
| Animation | on | Skilling and action animations (woodcutting, mining, fishing, cooking, fletching, …) |
| Interaction | on | Interacting with an NPC, such as a combat target or a fishing spot |
| XP gain *(not implemented yet)* | on | Gaining XP. Has a 3 s extra delay by default, because drops can be sparse |
| Movement *(not implemented yet)* | off | Walking and running. Has a 2 s grace delay after you stop |

Specific activities (Woodcutting, Fishing, Combat, …), resource alerts, the side panel and logout warnings are still in development.

The signal names the activity when it can, for example `Woodcutting`, `Combat` or `Mining`.

## Pause hotkey

Set **Pause hotkey** in the plugin settings to pause and resume detection. Pausing while you're
inactive sends `Activity Notifier: Active - Detection paused`, so Watchdog overlays clear.
Turning the plugin off does the same, with the detail `Plugin stopped`.

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

- **The head never changes.** You can edit the detail text under *Signals* in the plugin settings, and patterns that match the head keep working.
- **One head never matches another signal.** For example, `Activity Notifier: Active` does not match an Inactive signal.

### Channels

Signals can be delivered two ways. Turn on either or both in *Signals*.

| Channel | Default | Watchdog alert to use |
|---|---|---|
| Chat message | on | **Game Message** alert, with message type *Game Message* (or *Any*) |
| RuneLite notification | off | **Notification Fired** alert |

- **Use one alert kind per signal.** If both channels are on and you create both alert kinds, Watchdog fires twice.
- **For the notification channel without popups,** keep the notification enabled but turn off its tray popup and sound in its options. Watchdog still receives it.

### Recipe: show an overlay while inactive, remove it when active

1. In Watchdog, create a **Game Message** alert:
   - Message type: *Game Message*
   - Message: `Activity Notifier: Inactive` (glob, regex off)
   - Debounce: `0`
   - Add an **Overlay** action: tick **Sticky**, set **ID** to `activity-notifier-inactive`, and write any text, e.g. `You are inactive!`
2. Create a second **Game Message** alert:
   - Message type: *Game Message*
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

## Changelog

Signal heads are a public format. Any change to them will be listed here as a breaking change.

- Initial release.
