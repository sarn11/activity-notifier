# Quickstart: Validating Custom Animation IDs

## 1. Build checks (agent runs these after every phase)

```powershell
./gradlew build
```

Expected: the build passes, including the new `CustomAnimationsTest` and all existing tests, with
`AnimationCatalogTest` unchanged (SC-003).

## 2. In-game validation (the user runs this; Constitution VII)

1. Start the dev client with `./gradlew run`, and log in following
   [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
2. Make sure **Signals → Chat messages** is on.
3. In **General detectors**, leave **Animation** on and turn **Interaction**, **Movement** and
   **XP gain** off, so only animations can keep you active. Set the base delay low (e.g. 2000 ms) so
   Inactive signals arrive quickly.
4. Open the Activity Notifier side panel.

Settings and panel behaviour are defined in [contracts/config.md](./contracts/config.md).

Herbiboar (Fossil Island) is the motivating case. Any animation the plugin doesn't know works the same
way, for example an emote.

| # | Setup | Action | Expected |
|---|---|---|---|
| 1 | Fresh start, setting empty | Open the side panel | Last animation row shows `-` and `none yet` |
| 2 | Same | Chop a tree | Panel shows the woodcutting ID and `Woodcutting`, within about a tick (FR-012) |
| 3 | Same | Stop chopping and stand still | Panel keeps the woodcutting ID. It doesn't go blank or show -1 |
| 4 | Setting empty | Search a herbiboar trail spot (or do an emote) | Panel shows its ID and `Not counted`. After the delay, `Inactive` is sent as before (SC-003) |
| 5 | Select the ID in the panel | Copy it, paste it into **Custom animation IDs** | The panel status changes to `Animation (custom)` straight away, without animating again |
| 6 | ID from #5 listed | Repeat the action | It counts as activity. If you were Inactive, you get `Active - Animation (…)`. While you repeat it within the delay, no Inactive is sent (SC-002) |
| 7 | Same | Remove the ID from the setting, without restarting | The action no longer counts. Inactive follows after the delay (FR-005) |
| 8 | Setting ` 5216 ,,abc, -5, <your ID> ,` | Repeat the action | It still counts. The junk entries change nothing, and there's no error (FR-006, SC-004) |
| 9 | List a woodcutting ID too | Chop a tree | The panel still says `Woodcutting`, and resuming sends `Active - Woodcutting (…)`, not `Animation` (FR-004) |
| 10 | ID listed, **Animation** detector off | Repeat the action | It doesn't count. The panel still shows the ID (FR-002, FR-013) |
| 11 | ID listed, pause hotkey on | Repeat the action | It doesn't count. The panel still updates. Unpause, and it counts again |
| 12 | ID listed | Watch another player do the same action nearby | The panel doesn't change, and no activity is counted (FR-007) |
| 13 | ID listed | Switch RuneLite profile to one without it, then back | Behaviour and the panel status follow the active profile |
| 14 | Turn the plugin off and on mid-session | Repeat the action | The panel starts at `-` again. The listed ID still counts |

## 3. Edge cases worth a glance

- Log out and back in, or hop worlds: the listed ID still counts, and the panel keeps its last ID.
- A long list (paste a few hundred numbers): no visible stutter while playing.
