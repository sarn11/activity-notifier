# Contract: Configuration Keys (additions)

This extends
[specs/done/001-activity-detection/contracts/config.md](../../done/001-activity-detection/contracts/config.md).
The group is still `activitynotifier`. Every existing key is unchanged (FR-016). The keys below are new
and **permanent** (Constitution V).

## Section: Damage taken (new)

`@ConfigSection` name "Damage taken", description "Alerts when you take damage", position **55**,
`closedByDefault = true`.

| Key | Type | Default | Annotations | Settings name / description |
|---|---|---|---|---|
| `alertDamage` | boolean | `false` | — | "Damage taken" / "Send a signal when you take damage of at least the minimum" (FR-001) |
| `damageThreshold` | int | `1` | `@Range(min = 1)` | "Minimum damage" / "Smallest single hit that sends a signal. Hits are never added together" (FR-002, FR-004) |
| `damageOverTime` | boolean | `true` | — | "Count damage over time" / "Count poison and venom damage" (FR-005) |
| `damageCooldown` | int | `0` | `@Range(min = 0)`, `@Units(Units.SECONDS)` | "Cooldown" / "Minimum time between damage signals. 0 sends one for every qualifying hit" (FR-008) |

Positions in the section: 0, 1, 2, 3, in the order above.

## Section: Signals (addition)

| Key | Type | Default | Position | Settings name / description |
|---|---|---|---|---|
| `templateDamageTaken` | String | `{damage}` | 11 (after `templateSixHour`) | "Damage taken text" / "Detail for damage taken signals. Tokens: {damage}, {threshold}, {hitpoints}" |

The name and description match the existing template items' style (for example "Resource alert text" /
"Detail for resource alerts. Tokens: …").

## Side panel (addition)

- The panel's Resource alerts block gets one more row: a **Damage taken** checkbox bound to
  `alertDamage`, and a spinner bound to `damageThreshold` (min 1), in the existing "Threshold" column.
- Both read and write through `ConfigManager`, like every other panel row. There is no separate store.

## Behaviour contract

- Changes apply to the next hit, with no restart (FR-002). Settings are read when pending hits are
  flushed on the game tick.
- No key touches a third-party server, so none needs the IP-address `warning`.
