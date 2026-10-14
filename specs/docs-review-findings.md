# Docs Review Findings

Findings from a `/code-review` run on 2026-10-14, to be addressed in a later session.

## Context

- **What was reviewed:** the uncommitted working-tree diff on `master`. It adds Javadoc to 11 files under
  `src/main/java/com/activitynotifier/`, plus two new untracked files, `docs/ARCHITECTURE.md` and `docs/COMPONENTS.md`.
  No compiled code changed.
- **Line numbers** refer to that working tree and may shift once the docs are committed or edited.
- **Not yet verified.** The review removed duplicates but did not check each finding against the code.
  Check each one before acting on it.
- **No AGENTS.md rule violations** were found.
- **Keep commits separate.** AGENTS.md says not to mix documentation changes with behaviour changes. Fix the docs
  and Javadoc in one commit and the behaviour issues (section 1) in their own commits.

## 1. Possible behaviour bugs

These describe code behaviour, not just wrong comments. The new docs currently present both as intended.

- [ ] **1.1 Hopping worlds while Inactive drops the clearing signal.** `ActivityNotifierPlugin.java:280`
  - Say the player goes Inactive and Watchdog starts a sound or flash. On `HOPPING` or `CONNECTION_LOST`, the
    `default` branch of `onGameStateChanged` calls `activityTracker.reset()`, which sends no Active signal.
  - If the player then stands still, the state goes NEUTRAL and never moves to INACTIVE or ACTIVE, so nothing ever
    clears the alert and the Watchdog effect stays on. Resource alerts that are active at the time have the same
    problem.
  - The `LOADING` branch exists so an outstanding Inactive is never dropped, and the hop path breaks that same rule.
  - Diagram 5 in `docs/ARCHITECTURE.md` also leaves out the INACTIVE → NEUTRAL edge for `reset()`.
  - To check: in the dev client, go Inactive with a Watchdog effect configured, hop worlds, stand still, and see
    whether the effect clears.

- [ ] **1.2 ACTIVE can stay set with no live detector.** `detection/ActivityTracker.java:62`
  - Example: only Woodcutting has been observed, then the user turns Woodcutting off. `evaluate()` removes it from
    `lastSeen`, `evaluateActive()` returns empty because `lastSeen` is empty, and the state stays ACTIVE until some
    other enabled detector fires.
  - This goes against the `State.ACTIVE` Javadoc ("at least one enabled detector has seen activity within its
    delay"). It is minor today, but would break any future "active via X" UI.
  - Decide whether to fix the behaviour or reword the Javadoc.

## 2. Docs and Javadoc that contradict the code

- [ ] **2.1 Tracker contract is wrong.** Plugin class Javadoc (`ActivityNotifierPlugin.java:62`),
  `docs/ARCHITECTURE.md:5` and `docs/COMPONENTS.md:22`.
  - All three say no tracker touches the client and every tracker returns `Optional<Signal>`.
  - In fact `DamageAlertTracker.flush` returns `List<Signal>` (damage can produce several signals per tick),
    `XpGainTracker.onXp` returns `Optional<Skill>`, and `WeaponSpeed` returns nothing and uses the client's
    `ItemManager`.
  - COMPONENTS.md diagram 2 draws `WS --> ItemManager` right under "None of these classes touch the client".

- [ ] **2.2 Threading claim is wrong.** `ActivityNotifierPlugin.java:64`
  - The Javadoc says tracker state is only touched on the client thread, and off-thread code hops on via
    `ClientThread#invoke`.
  - `startUp()` runs on the plugin-manager thread without `clientThread.invoke`. It builds `activityTracker`,
    `resourceTrackers` and `damageTracker`, calls `weaponSpeed.reset()`, `xpGainTracker.reset()` and
    `logoutWarnings.reset()`, and writes `ready`, `paused` and `lastLocation`.
  - Either reword the Javadoc to describe what actually happens (setup in `startUp()` before subscription, then
    client thread only), or change the code so it matches the Javadoc.

- [ ] **2.3 `flushDamage` states an open assumption as fact.** `ActivityNotifierPlugin.java:579`
  - The Javadoc says the flush runs a tick after the hit, so the hitpoints value already includes it.
  - `specs/done/002-damage-notifier/research.md` lists this as an unverified assumption (analysis A1). The flush can
    run on the same server cycle as the hit, before the HP stat update arrives, so `{hitpoints}` may show the
    pre-hit value.
  - Reword the Javadoc to mark it as an assumption, or verify it in-game first.

- [ ] **2.4 `onGameStateChanged` Javadoc is wrong in two places.** `ActivityNotifierPlugin.java:276`
  - "LOGGED_IN does nothing here" is wrong: `trackSession()`, called at the top of the same handler, starts the
    six-hour session timer on LOGGED_IN.
  - "Other states discard all tracker state" is also wrong. `HOPPING` and `CONNECTION_LOST` do not reset
    `logoutWarnings` (only `LOGIN_SCREEN` does), `WeaponSpeed` is kept, and `DamageAlertTracker.lastSent` survives
    on purpose.
  - Diagram 9 in `docs/ARCHITECTURE.md` repeats the second claim ("reset all trackers silently").

- [ ] **2.5 `inactiveSince` describes the wrong moment.** `detection/ActivityTracker.java:93`
  - The Javadoc says "When the player became inactive". It is actually set at the `evaluate()` tick that *detected*
    inactivity, which is the last activity plus the detector's delay.
  - Example: last activity at t=0 with a 3000 ms delay gives `inactiveSince` of about 3 s, so the "Active ...
    inactive for" and "Still inactive" durations are short by the delay.
  - Fix the comment. Separately decide whether the durations should include the delay; if so that is a behaviour
    change for its own commit.

- [ ] **2.6 `Detector.displayName` is not always the signal name.** `detection/Detector.java:60`
  - The Javadoc says it is "used as the activity name in signals".
  - `observeAnimation` passes the catalog name for ANIMATION (for example "Prayer" or "Farming"), and
    `observeInteraction` passes "Combat" for INTERACTION when the target is attackable. A Watchdog pattern such as
    "Inactive - Animation" built from the docs would never match.

- [ ] **2.7 Diagram errors in ARCHITECTURE.md.** `docs/ARCHITECTURE.md:145`
  - Diagram 3 sends "HitsplatApplied my hit on someone else" into a "COMBAT + INTERACTION" node, but
    `onHitsplatApplied` only observes `Detector.COMBAT`.
  - Diagram 5 puts "all detectors off" on `clear(detail)`, but that path is `evaluate()` calling `reset()`.

## 3. Structure

- [ ] **3.1 The same architecture summary appears in three places.** `docs/COMPONENTS.md:9`,
  `docs/ARCHITECTURE.md` and the `ActivityNotifierPlugin` class Javadoc.
  - The three copies share the same `Optional<Signal>` error (2.1), while ARCHITECTURE.md's own class diagram shows
    `List<Signal>`. Every future change needs three edits.
  - The review suggests keeping the summary only in `docs/ARCHITECTURE.md` and having the Javadoc link to it.

## 4. Changes from 003-custom-animation-ids (added 2026-10-14)

The Javadoc work above was stashed (`stash@{0}`) while 003 was implemented. When you restore it,
expect conflicts in `ActivityNotifierPlugin.java`, `ActivityNotifierPanel.java` and
`ActivityNotifierConfig.java`. Then bring the docs up to date with 003:

- [ ] **4.1 `docs/ARCHITECTURE.md` animation flow (around lines 136–139, 159).** `observeAnimation`
  now has a third branch: an ID the catalog doesn't know counts for ANIMATION if it's in
  `CustomAnimations.ids(config.customAnimationIds())`. Helm and built-in IDs are checked first.
  `onAnimationChanged` also sends each new local-player animation to the panel (`showLastAnimation`).
- [ ] **4.2 `docs/ARCHITECTURE.md` helpers row (line 20) and `docs/COMPONENTS.md` (line 29).** Add
  `detection/CustomAnimations.java`: it parses the setting, caches the result until the text changes,
  and provides `countedAs` for the panel.
- [ ] **4.3 Javadoc on `observeAnimation` and `onAnimationChanged`.** The stashed versions predate 003.
  Mention custom IDs and the panel update when you merge them.

## Suggested order

1. Verify each finding against the code and drop any that are wrong.
2. Commit the docs and Javadoc fixes (sections 2 and 3) together with the new `docs/` files.
3. Handle 1.1 and 1.2 as separate behaviour changes, each with its own commit and in-game test.
4. Delete this file once everything is addressed.
