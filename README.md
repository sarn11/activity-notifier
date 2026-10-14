# Activity Notifier

Tells you when you stop doing something, and again when you start, as signals the
[Watchdog](https://runelite.net/plugin-hub/show/watchdog) plugin can react to with overlays, sounds,
screen flashes or text to speech.

- **Inactive** is sent when none of your enabled detectors has seen activity for a short delay.
- **Active** is sent as soon as you resume. It only ever follows an Inactive, so a Watchdog effect started by Inactive can be removed automatically.
- Moving the mouse or pressing keys never counts as activity. Only what your character does counts.

Optional extras: "Still inactive" reminders, resource alerts, a damage taken alert and logout
warnings. A side panel holds the settings you change most, and a hotkey pauses detection.

## What counts as activity

Every detector can be turned on or off and given its own extra delay.

**General detectors**

- **Animation**: skilling and action animations (woodcutting, mining, fishing, cooking, fletching, …)
- **Interaction**: interacting with an NPC, such as a combat target or a fishing spot
- **XP gain**: gaining XP
- **Movement**: walking and running

**Specific activities**: Cooking, Crafting, Fishing, Firemaking, Fletching, Herblore, Mining,
Woodcutting, Smithing, Combat, Magic skilling (such as Lunar spells) and Sailing (beta). Watch one
on its own to give it a longer delay, for example for fishing. Combat allows for your weapon's attack
speed.

**Custom animation IDs**: if something you do isn't recognised, such as searching trails while
hunting herbiboar, add its animation IDs to **Custom animation IDs**, separated by commas. To find an
ID, open the side panel and do the action. **Last animation** shows its ID and whether it counts.

## Alerts

- **Resource alerts**: low hitpoints, low prayer, low run energy, run energy restored, low oxygen and special attack restored. Each sends one alert when it crosses your threshold, and one "recovered" signal when it crosses back.
- **Damage taken**: sent when a single hit on you reaches your minimum damage. A cooldown and a poison/venom filter are available.
- **Logout warnings**: sent shortly before the idle logout and the six-hour logout.

## Watchdog integration

### Signals

Every signal is `Activity Notifier: <head> - <detail>`. Match on the head: it never changes, and no
head matches another signal (`Active` won't match `Inactive`). The detail text can be edited under
*Signals* in the settings.

```text
Activity Notifier: Inactive - Woodcutting
Activity Notifier: Active - Woodcutting (inactive for 1m 23s)
Activity Notifier: Still inactive - Woodcutting (for 5m 0s)
Activity Notifier: Low hitpoints - 9 (threshold 10)
Activity Notifier: Hitpoints recovered - 34
Activity Notifier: Damage taken - 12
Activity Notifier: Idle logout soon - About to log out from idling
Activity Notifier: Six hour logout soon - About to reach the six hour limit
```

Resource alert heads, in alert / recovered pairs:

- Low hitpoints / Hitpoints recovered
- Low prayer / Prayer recovered
- Low run energy / Run energy recovered
- Run energy restored / Run energy used
- Low oxygen / Oxygen recovered
- Special attack restored / Special attack used

### Channels

Turn on either or both under *Signals*:

- **Chat message**: use a Watchdog **Game Message** alert with message type *Spam*. These are filtered game messages: set the chat box's *Game* tab to *Filtered* to hide them. Watchdog still sees them.
- **RuneLite notification**: use a **Notification Fired** alert.

Create only one alert kind per signal. If you create both, Watchdog fires twice.

### Recipe: an overlay while you're inactive

1. Create a **Game Message** alert: type *Spam*, message `Activity Notifier: Inactive`, debounce `0`.
   Add a sticky **Overlay** action with ID `activity-notifier-inactive`.
2. Create a second one with message `Activity Notifier: Active`, debounce `0`, and a
   **Dismiss Overlay** action with the same ID.

The overlay appears when you stop and disappears when you start again. For damage, match
`Activity Notifier: Damage taken` and add any action.

### Capturing values

Turn **regex** on in the alert. `$1` is then usable in its actions:

- Activity on Inactive: `^Activity Notifier: Inactive - (.+)$`
- Activity on Active: `^Activity Notifier: Active - (.+?) \(`
- Damage: `^Activity Notifier: Damage taken - (\d+)`

The activity is the specific activity if one matched (`Woodcutting`). Otherwise it is what a general
detector recognised (`Agility` from XP gain, `Combat` from attacking an NPC), or the detector's name
(`Animation`, `Interaction`, `Movement`).

### Notes

- Keep Watchdog's debounce at `0`, or a quick inactive → active → inactive can be missed.
- Logging out or hopping while inactive sends no "Active", so a sticky overlay stays until your next resume or Watchdog's clear hotkey.
- Pausing or turning off the plugin while inactive sends `Active - Detection paused` or `Active - Plugin stopped`, so overlays clear.
- Watchdog ignores alerts in its own disabled areas (some bosses, raids, the Inferno).
- `[ ] { } , !` are special in Watchdog glob patterns.

## Changelog

Signal heads are a public format. Any change to one will be listed here as a **breaking change**.

- Initial release: activity signals, general and specific detectors, resource alerts, logout warnings, side panel and pause hotkey.
- Added the **Damage taken** signal. New head, no existing head changed.
- Added **Custom animation IDs**, and **Last animation** in the side panel. No head changes.
