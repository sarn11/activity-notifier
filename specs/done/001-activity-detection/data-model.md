# Data Model: Activity Detection & State Signals

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

All state lives in memory. Only the user's **settings** persist, and they persist through RuneLite's
config system (group `activitynotifier`; see [contracts/config.md](./contracts/config.md)). Nothing is
written to disk by the plugin, and nothing is stored per account (FR-022).

---

## Detector (enum)

A source of activity that can be watched individually (FR-002).

| Field | Type | Notes |
|---|---|---|
| `group` | `GENERAL` \| `SPECIFIC` | |
| `displayName` | String | Shown in the panel and used as the fallback activity name (FR-010a) |
| `enabledKey` | String | Config key for the on/off toggle (permanent) |
| `delayKey` | String | Config key for the extra delay in ms (permanent) |
| `defaultEnabled` | boolean | GENERAL: Animation, Interaction and XP gain are on; Movement is off. SPECIFIC: all off (FR-002a) |
| `defaultDelayMs` | int | See research R12 |

Values:
- **GENERAL**: `ANIMATION`, `INTERACTION`, `MOVEMENT`, `XP_GAIN`
- **SPECIFIC**: `COOKING`, `CRAFTING`, `FISHING`, `FIREMAKING`, `FLETCHING`, `HERBLORE`, `MINING`,
  `WOODCUTTING`, `SMITHING`, `SAILING`, `COMBAT`, `MAGIC_SKILLING`

**Validation**: delays are ≥ 0 (`@Range(min = 0)`, `@Units(MILLISECONDS)`).

## Activity (value)

The resolved name of what the player is doing (FR-010a). Examples: `Woodcutting`, `Agility`, `Combat`,
or the fallback `Animation`.

| Field | Type | Notes |
|---|---|---|
| `name` | String | The most specific name available |
| `detector` | Detector | The detector that observed it |

### How the name is chosen

1. If a SPECIFIC detector matched, use its `displayName`.
2. Otherwise use the name recognized by the general detector:
   - **Animation**: the `AnimationCatalog` entry for the animation.
   - **XP gain**: the skill's name.
   - **Interaction**: `Combat` if the target is attackable.
3. Otherwise use the general detector's `displayName`.

## Observation (event → tracker input)

| Field | Type |
|---|---|
| `detector` | Detector |
| `activity` | Activity |
| `at` | Instant (from the injected `Clock`) |

An observation is produced by the event handlers in research R5. It is dropped if its detector is
disabled or detection is paused.

## ActivityTracker (state machine)

Per-detector state:

```
lastSeen: Map<Detector, Instant>
lastActivity: Map<Detector, Activity>  // per-detector last observed activity
state: NEUTRAL | ACTIVE | INACTIVE
inactiveSince: Instant?         // set on ACTIVE→INACTIVE
lastReminderAt: Instant?
```

The **effective delay** for detector `d` is:

```
base + extra(d) + (d == COMBAT ? weaponIntervalTicks × 600 : 0)
```

`base` defaults to 2000 ms.

A detector is **live** at time `now` when it is enabled, has been seen, and
`now − lastSeen[d] < effectiveDelay(d)`.

### Transitions

| From | Trigger | To | Emits |
|---|---|---|---|
| NEUTRAL | An observation arrives | ACTIVE | nothing (FR-008: no signal for pre-existing state) |
| ACTIVE | An observation arrives | ACTIVE | nothing |
| ACTIVE | Tick evaluation finds no live detectors, **and** at least one enabled detector has a `lastSeen` entry (it was seen and has since expired) | INACTIVE | **Inactive**(the activity of the most recently expired enabled detector; on a tie, the SPECIFIC detector's, per FR-010a) |
| INACTIVE | An observation arrives (evaluated immediately) | ACTIVE | **Active**(activity, now − inactiveSince) |
| INACTIVE | Tick, reminders enabled, and `now − (lastReminderAt ?: inactiveSince) ≥ interval` | INACTIVE | **Still inactive**(lastActivity, duration) |
| ACTIVE / INACTIVE | Logout, hop or connection lost (`GameStateChanged` other than LOGGED_IN / LOADING) | NEUTRAL | nothing; state is discarded (spec edge case) |
| any | `LOADING` | (unchanged, suspended) | nothing. Evaluation stops; on the first tick after loading, every timestamp is shifted forward by the suspended time. |
| INACTIVE | Plugin shutdown, or paused via hotkey | NEUTRAL | **Active**(cleared) (FR-012) |
| ACTIVE | Plugin shutdown or paused | NEUTRAL | nothing |
| any | A detector is turned off in config | (unchanged) | nothing. The detector's `lastSeen` entry is removed, so it no longer counts toward liveness. |
| ACTIVE | Tick evaluation finds **no detectors enabled at all** | NEUTRAL | nothing |
| INACTIVE | Tick evaluation finds **no detectors enabled at all** | NEUTRAL | **Active**(cleared, `Detection off`) |

### Invariants

- Signals alternate strictly: Inactive → Active → Inactive … with exactly one each (FR-009, FR-010, SC-004). Reminders occur only between an Inactive and its Active.
- Mouse and keyboard input is never an observation (clarification Q1).
- A settings change never produces an **Inactive** signal. Turning a detector off deletes its
  `lastSeen` entry, so ACTIVE→INACTIVE can only come from an enabled detector that was seen and has
  since expired. If the player was only seen by detectors that are now off, the tracker stays ACTIVE
  until a remaining enabled detector is seen and expires, or until no detectors remain enabled
  (→ NEUTRAL).
- `lastActivity` is kept per detector (`lastActivity: Map<Detector, Activity>`) so the Inactive signal
  names the activity of an enabled detector, never one that was just turned off.
- Evaluation is gated by `ready`. It becomes true on the first `GameTick` after `LOGGED_IN` and false on any other `GameState` (FR-008).

## ResourceType (enum) and ResourceAlertTracker

| ResourceType | Direction | Unit | Default threshold | Keys (enabled / threshold) |
|---|---|---|---|---|
| `HITPOINTS` | LOW | points | 10 | `alertHitpoints` / `hitpointsThreshold` |
| `PRAYER` | LOW | points | 10 | `alertPrayer` / `prayerThreshold` |
| `RUN_ENERGY_LOW` | LOW | % | 10 | `alertLowEnergy` / `lowEnergyThreshold` |
| `RUN_ENERGY_HIGH` | HIGH | % | 90 | `alertHighEnergy` / `highEnergyThreshold` |
| `OXYGEN` | LOW | % | 20 | `alertOxygen` / `oxygenThreshold` |
| `SPECIAL_ATTACK` | HIGH | % | 100 | `alertSpecial` / `specialThreshold` |

All are disabled by default (FR-026).

Each tracker has a **state** of `UNKNOWN`, `NORMAL` or `ALERTED`.

| From | Condition | To | Emits |
|---|---|---|---|
| UNKNOWN | First sample after `ready` | NORMAL or ALERTED (silently) | nothing (prevents an alert on login) |
| NORMAL | LOW: value ≤ threshold. HIGH: value ≥ threshold | ALERTED | **Resource alert** |
| ALERTED | LOW: value > threshold. HIGH: value < threshold | NORMAL | **Resource recovered** |
| any | Logout or hop | UNKNOWN | nothing |
| ALERTED | The alert is turned off in config (or stops applying: real HP/prayer level ≤ threshold), the plugin shuts down, or detection is paused | UNKNOWN | **Resource recovered** (cleared; fixed detail `Alert off` / `Plugin stopped` / `Detection paused`) |
| NORMAL / UNKNOWN | The alert is turned off in config, the plugin shuts down, or detection is paused | UNKNOWN | nothing |

Clearing on turn-off, shutdown or pause mirrors FR-012, so a Watchdog effect started by the alert is
always dismissed. Logout and hop discard state without emitting, matching the activity edge case.

HIGH-direction alerts mean "restored". Their "recovered" signal fires when the value drops back below
the threshold, so a Watchdog effect can be cleared once the resource is spent.

## LogoutWarning trackers

| Tracker | Armed when | Fires when | Re-arms |
|---|---|---|---|
| Idle logout | enabled and `ready` | idle ticks ≥ `client.getIdleTimeout()` − 20 s | after any input (idle ticks drop) |
| Six-hour | enabled, and a session start (login, hop or reconnect, as core does) was recorded | `now − loginAt ≥ 340 min` | on the next session start |

Neither tracker has a recovered signal (spec FR-017 and FR-018 define only one warning each).

## Signal (value)

| Field | Type | Notes |
|---|---|---|
| `type` | SignalType | See [contracts/signals.md](./contracts/signals.md) |
| `activity` | Activity? | For activity signals |
| `resource` | ResourceType? | For resource signals |
| `value` / `threshold` | int? | For resource signals |
| `duration` | Duration? | Active and Still inactive |
| `text` | String | `"Activity Notifier: " + keyword(type) + " - " + render(detailTemplate)` |

Delivered by `SignalDispatcher` to each enabled channel (FR-013). Order: chat first, then notification.
