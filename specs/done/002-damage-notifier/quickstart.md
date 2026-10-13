# Quickstart: Validating the Damage Taken Notifier

## 1. Build checks (agent runs these after every phase)

```powershell
./gradlew build
```

Expected: the build passes, including the new `HitKindTest` and `DamageAlertTrackerTest`, the extended
`SignalFormatterTest`, and all existing tests.

## 2. In-game validation (the user runs this; Constitution VII)

1. Start the dev client with `./gradlew run`, and log in following
   [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
2. Make sure **Signals → Chat messages** is on. To test the notification channel, set
   **RuneLite notification** to on as well.
3. Open **Damage taken** settings. The alert is off by default.

Use a safe, low-level spot where something will hit you, for example chickens or cows (low damage),
goblins or Al Kharid warriors (aggressive at low levels), or a poisonous monster for the poison
scenarios. Behaviour and text are defined in [contracts/signals.md](./contracts/signals.md), and settings
in [contracts/config.md](./contracts/config.md).

| # | Setup | Action | Expected |
|---|---|---|---|
| 1 | Alert **off** (default) | Get hit | No Damage taken signal (SC-008) |
| 2 | Alert on, minimum 1, cooldown 0 | Get hit for N > 0 | `Activity Notifier: Damage taken - N` once per hit, within about one tick (SC-001) |
| 3 | Same | Get a 0 (blue) hit | Nothing (FR-006) |
| 4 | Same | Attack a monster and watch others fight nearby | No signals for damage you deal or damage others take (SC-003) |
| 5 | Minimum set above what the monster can hit | Get hit repeatedly | Nothing. Then lower the minimum to 1 without restarting: the next hit sends (FR-002) |
| 6 | Minimum = a value the monster sometimes hits | Get hit | Only hits ≥ the minimum send. A hit equal to the minimum sends (inclusive) |
| 7 | Cooldown 10 s, minimum 1 | Keep fighting | Signals at most once every 10 s (SC-006). Hits in between send nothing |
| 8 | Damage over time on (default) | Get poisoned | Each poison tick sends a signal |
| 9 | Damage over time **off** | Get poisoned, then hit | Poison ticks send nothing. Direct hits still send (SC-007) |
| 10 | Template `{damage} damage, {hitpoints} HP left` | Get hit | Text shows the hit and your hitpoints *after* it, matching the orb |
| 11 | Empty template | Get hit | `Activity Notifier: Damage taken` only |
| 12 | Pause hotkey pressed | Get hit | Nothing. Unpause, get hit: a signal is sent (FR-013) |
| 13 | Low hitpoints alert also on | Get hit below its threshold | Both signals fire, independently |
| 14 | Side panel | Toggle Damage taken and change its minimum in the panel | The settings page shows the same values, and behaviour follows |
| 15 | Watchdog recipe from the README | Get hit | The Watchdog alert fires. Existing Inactive/Active Watchdog alerts do not match (SC-005) |

### Edge cases worth a quick look

- Turn the plugin on (or the alert on) mid-fight: only hits after that point send.
- ~~World-hop or log out right after being hit~~: **not reachable in-game.** The game blocks logout and
  world-hopping for about 10 s after combat, so no hit can still be waiting to send at logout. The
  discard-on-logout code path remains as a guard, and `DamageAlertTrackerTest.discardPendingDropsHits`
  covers it.
- Nightmare Zone with absorption, if available: hits still send, and `{hitpoints}` shows the orb value
  (absorption not included).
- Cooldown of 10 s, then hop worlds and get hit within 10 s of the last signal: no signal, because the
  cooldown is real time and not reset by hopping.
