# Quickstart: Validating Activity Detection & State Signals

This guide shows how to check, end to end, that the feature works. Signal formats are in
[contracts/signals.md](./contracts/signals.md), settings are in [contracts/config.md](./contracts/config.md),
and the state rules are in [data-model.md](./data-model.md).

## Prerequisites

- JDK 11 or newer, and this repository on branch `001-activity-detection`.
- A Jagex account that can log in to the dev client. Follow
  https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts.
- (For the Watchdog scenarios) Watchdog installed from the Plugin Hub in the dev client.

## Automated checks (after every phase)

```powershell
./gradlew build      # compiles for Java 11 and runs the JUnit tests
```

These must pass:

- **ActivityTracker**: the transition table (NEUTRAL→ACTIVE is silent, ACTIVE→INACTIVE emits once, INACTIVE→ACTIVE emits once with a duration), flapping, pause/clear, logout reset, and reminders.
- **ResourceAlertTracker**: one alert per crossing, one recovery when it crosses back, no alert on the first sample, and HIGH direction.
- **SignalFormatter**: head rules 1 and 2, tokens, empty template, and duration formatting.
- **AnimationCatalog**: no duplicate IDs, and every entry has a non-empty activity name.

## In-game validation (the user does this; agents must not drive the game)

Launch with `./gradlew run` and log in.

| # | Scenario | Steps | Expected |
|---|---|---|---|
| 1 | Inactive signal (US1) | Default settings. Chop a tree until it falls, then stand still. | One `Activity Notifier: Inactive - Woodcutting` about 2 s after the last chop. Nothing while chopping. |
| 2 | Active signal (US2) | After #1, click another tree. | One `Activity Notifier: Active - Woodcutting (inactive for …)` within 1 s. |
| 3 | No mouse resume (Q1) | Go inactive, then wiggle the mouse and open the inventory. | No Active signal. |
| 4 | Watchdog recipe (US3) | Set up the two alerts from contracts/signals.md, then repeat #1 and #2. | The sticky overlay appears on inactive and disappears on active. |
| 5 | Notification channel | Turn off chat signals, turn on the notification (tray off). Use Notification Fired alerts. Repeat #4. | Same as #4, with nothing in chat. |
| 6 | Per-activity delay (US4) | Turn on Fishing with a 3000 ms extra delay and fish until the spot moves. | Inactive arrives about 5 s after fishing stops. Woodcutting still takes about 2 s. |
| 7 | Movement grace | Turn on the movement detector. Walk between spots. | No Inactive until about 2 s after you stop walking (+2 s base). |
| 8 | XP catch-all (US5) | Do an agility course with only the XP detector on. | Correct Inactive/Active pairs, named `Agility`. |
| 9 | Combat weapon speed | Fight with a slow weapon (e.g. a 6-tick one). | No Inactive between attacks. |
| 10 | Resource alerts (US6) | Turn on hitpoints with threshold 50 (needs > 50 HP). Take damage below 50, then eat above it. | One `Low hitpoints`, then one `Hitpoints recovered`. |
| 11 | Side panel (US7) | Toggle Woodcutting in the panel, then check the config screen. Turn off "Show side panel". | Both views stay in sync. The panel button disappears immediately and comes back when you turn it on. |
| 12 | Profile switch | Switch RuneLite profile. | The panel shows that profile's values. |
| 13 | Pause hotkey | Bind a key. Go inactive, then press it. | `Activity Notifier: Active - Detection paused`. No further signals until you press it again. |
| 14 | Lifecycle | Turn the plugin on while already logged in and mid-activity. Hop worlds while inactive. Turn the plugin off while inactive. | No signal on enable. No signal on hop (state resets). An `Active - Plugin stopped` signal on disable. |
| 15 | Logout warnings (US8) | Turn on the idle logout warning and stay AFK. | One `Idle logout soon` about 20 s before the logout. |
| 16 | Performance (SC-006) | Turn on every detector and alert. Play normally for a few minutes. | No visible FPS drop. |
| 17 | Detector turn-off (analysis I1) | While chopping (active), turn off the Animation and Interaction detectors in the side panel. Then go inactive and turn every detector off. | No `Inactive` is caused by the turn-off. With every detector off while inactive, you get one `Active - Detection off`. |
| 18 | Sailing (analysis A1) | With only the movement and animation detectors on, steer a ship. Then turn on the Sailing detector and steer again. | Steering alone doesn't keep you active with only movement and animation on. With Sailing on, the activity is named `Sailing`. |

The feature is done only when the user confirms these scenarios in-game (Constitution VII).
