# Contract: Configuration Keys

- Config group: **`activitynotifier`**. It is already specific, and is kept from the renamed template.
- The `keyName`s below are **permanent** (Constitution V). Renaming one requires a migration.
- The template's `greeting` key is removed. It was never released, so it needs no migration.

`ms` means `@Units(MILLISECONDS)`. All ints have `@Range(min = 0)` unless noted otherwise.

## Section: General

| Key | Type | Default | Notes |
|---|---|---|---|
| `showSidePanel` | boolean | `true` | FR-029; applies live |
| `pauseHotkey` | Keybind | `NOT_SET` | FR-021 |
| `baseDelay` | int ms | `2000` | FR-004 |

## Section: Signals

| Key | Type | Default | Notes |
|---|---|---|---|
| `chatSignals` | boolean | `true` | FR-013 chat channel |
| `notificationSignals` | Notification | `Notification.OFF` | FR-013 notification channel; users can disable tray/sound per notification while keeping it on |
| `reminderEnabled` | boolean | `false` | FR-011 |
| `reminderInterval` | int seconds | `60` | `@Range(min = 10)` |
| `templateInactive` | String | `{activity}` | FR-015 |
| `templateActive` | String | `{activity} (inactive for {duration})` | |
| `templateReminder` | String | `{activity} (for {duration})` | |
| `templateResourceAlert` | String | `{value} (threshold {threshold})` | |
| `templateResourceRecovered` | String | `{value}` | |
| `templateIdleLogout` | String | `About to log out from idling` | |
| `templateSixHour` | String | `About to reach the six hour limit` | |

## Section: General detectors

| Key | Type | Default |
|---|---|---|
| `detectAnimation` | boolean | `true` |
| `detectInteraction` | boolean | `true` |
| `detectMovement` | boolean | `false` |
| `detectXpGain` | boolean | `true` |
| `xpThreshold` | int | `1` (`@Range(min = 1)`) |

## Section: Specific activities

Every key is a boolean that defaults to `false`:

`detectCooking`, `detectCrafting`, `detectFishing`, `detectFiremaking`, `detectFletching`,
`detectHerblore`, `detectMining`, `detectWoodcutting`, `detectSmithing`, `detectSailing`,
`detectCombat`, `detectMagicSkilling`.

## Section: Extra delays

Every key is an int in ms. The default is `0` except where noted:

| Key | Default |
|---|---|
| `delayAnimation` | 0 |
| `delayInteraction` | 0 |
| `delayMovement` | **2000** (movement grace delay, FR-006) |
| `delayXpGain` | **3000** |

The specific activities follow the same pattern, all defaulting to 0: `delayCooking`, `delayCrafting`, `delayFishing`, `delayFiremaking`, `delayFletching`, `delayHerblore`, `delayMining`, `delayWoodcutting`, `delaySmithing`, `delaySailing`, `delayCombat` (the weapon interval is added on top automatically) and `delayMagicSkilling`.

## Section: Resource alerts

| Toggle key (boolean, default `false`) | Threshold key | Default threshold |
|---|---|---|
| `alertHitpoints` | `hitpointsThreshold` | 10 |
| `alertPrayer` | `prayerThreshold` | 10 |
| `alertLowEnergy` | `lowEnergyThreshold` (%) | 10 |
| `alertHighEnergy` | `highEnergyThreshold` (%) | 90 |
| `alertOxygen` | `oxygenThreshold` (%) | 20 |
| `alertSpecial` | `specialThreshold` (%) | 100 |

Percentage thresholds are `@Range(min = 0, max = 100)`.

## Section: Logout warnings

| Key | Type | Default |
|---|---|---|
| `warnIdleLogout` | boolean | `false` |
| `warnSixHour` | boolean | `false` |

## Behaviour contract

- Any change applies on the next game tick, without a restart (FR-028).
- The side panel reads and writes these same keys through `ConfigManager`. There is no separate store.
- When the active RuneLite profile changes (`ProfileChanged`), the panel refreshes and detection
  re-reads its settings (FR-022).
