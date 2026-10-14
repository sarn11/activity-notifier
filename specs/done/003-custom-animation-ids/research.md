# Research: Custom Animation IDs

Decisions made while planning. Each one records what was chosen, why, and the alternatives.

## R1. When the list is parsed

- **Decision**: parse lazily, on the client thread. `CustomAnimations` keeps the last raw string and its
  parsed set. Each lookup reads `config.customAnimationIds()` and re-parses only when the string has
  changed (`String.equals`).
- **Rationale**:
  - `ConfigChanged` is not enough on its own. A RuneLite profile switch posts only `ProfileChanged`, with
    no per-key `ConfigChanged` (`ConfigManager.switchProfile`, RuneLite 1.13.x). A cache fed by config
    events would need two triggers.
  - Config events arrive on the EDT, but lookups run on the client thread. A lazy cache stays on one
    thread and needs no `volatile` or locking.
  - This matches the existing design: trackers read settings "through lambdas every time they evaluate"
    (`ActivityNotifierPlugin.onConfigChanged` Javadoc), and the RuneLite config proxy caches values, so
    the per-tick read is a map lookup.
  - Parsing happens only when the text changes, which meets FR-011.
- **Alternatives**:
  - Parse in `onConfigChanged` and `onProfileChanged` into a `volatile` immutable set. This works, but
    needs two triggers and a cross-thread hand-off, with nothing gained.
  - Parse on every lookup. This is simplest, but re-splits the string up to twice per tick, against
    FR-011.

## R2. Parsing rules

- **Decision**:
  - Split on `,`. Trim each entry and skip empty ones.
  - Accept an entry only if `Integer.parseInt` succeeds and the value is `>= 0`.
  - Collect the results into an immutable set, so duplicates disappear.
  - `null` or blank text gives an empty set.
- **Rationale**:
  - This covers every case in User Story 3. `parseInt` rejects `abc`, `52l6`, `1.5`, `+` forms with
    spaces inside, and anything larger than `Integer.MAX_VALUE`.
  - Rejecting negatives excludes `-1` (no animation), so standing still can never count.
  - `parseInt` accepts a leading `+` (`+5216`). That is harmless, so it is allowed.
  - There is no upper bound below `Integer.MAX_VALUE`. Unused IDs are harmless (spec edge case), and a
    hard-coded maximum would go stale as Jagex adds animations.
- **Alternatives**:
  - Also split on spaces and semicolons. Rejected: the user asked for comma-separated input, and one
    rule is easier to explain. The setting's description says "comma-separated".
  - Report invalid entries to the player. Rejected: config items can't show validation errors, and
    SC-004 says invalid entries must never need the player's attention.

## R3. Where custom IDs fit in `observeAnimation`

- **Decision**: check custom IDs last.
  1. Ship-helm animations count as Sailing only (unchanged).
  2. Built-in animations count as before (unchanged).
  3. Otherwise, if the ID is in the custom set, call
     `observe(Detector.ANIMATION, Detector.ANIMATION.getDisplayName())`.
- **Rationale**:
  - Checking last makes FR-004 hold by construction: listing a built-in or ship-helm ID changes
    nothing.
  - `Detector.ANIMATION`'s display name is `Animation`, the name FR-003 asks for. It is the same text as
    the catalog's `MISC` fallback, but it doesn't depend on that package-private constant.
  - `observe()` already applies the Animation toggle, its delays and the pause check (FR-002), so
    custom IDs need no special handling there.
- **Alternatives**: merging custom IDs into `AnimationCatalog`'s map. Rejected: the catalog is an
  immutable, class-level map checked by `AnimationCatalogTest`, and per-user data doesn't belong in it.

## R4. Showing the last animation (User Story 4)

- **Decision**:
  - In `onAnimationChanged`, for the local player only, the plugin records the animation if it is not
    `-1` and differs from the last one shown. It then sends the ID to the panel with
    `SwingUtilities.invokeLater`.
  - The panel works out the status text itself on the EDT, from the ID and
    `config.customAnimationIds()`, using the same pure classifier as the plugin (`CustomAnimations.countedAs`).
  - `refresh()` recomputes the status, so editing the list, or switching profile, updates the status
    straight away (both already trigger `refresh()`).
- **Rationale**:
  - `AnimationChanged` fires whenever a new animation starts, including long ones, which fire once. The
    display only needs those moments, so the per-tick re-read isn't needed.
  - Recording in the event handler, before `observe()`, means the display works while paused and with
    every detector off (FR-013).
  - Sending only on change keeps EDT traffic to one message per new animation.
  - Working out the status on the EDT keeps the panel's "reads config, never touches the client" rule.
    The classifier uses only static data and the config string.
- **Alternatives**:
  - Show the *current* animation every tick. Rejected: it reads `-1` most of the time, so short
    animations such as a search would flash by before the player could read them.
  - Print IDs to chat. Rejected by the user in favour of the side panel.
  - An "Add" button that appends the ID to the list. Deferred: useful, but beyond what was asked. The
    copyable field covers the need.

## R5. Panel layout

- **Decision**:
  - Add one row at the bottom of the panel's **General detectors** section.
  - Left: a label `Last animation: <status>`, with a tooltip explaining what it is for.
  - Right: a non-editable `JTextField` holding the ID, sized like the spinners, so it can be selected
    and copied (FR-012).
  - Before the first animation: the field shows `-` and the status reads `none yet`.
- **Rationale**: the row sits next to the Animation toggle it relates to, and fits the panel's existing
  two-column row layout (`addRow`). A text field, not a label, is what makes the ID copyable.
- **Alternatives**: a separate "Animation lookup" section. Rejected: one row doesn't need a section.

## R6. Config item

- **Decision**:
  - Key `customAnimationIds`, type `String`, default `""`.
  - Position 1 in **General detectors**, directly under the Animation toggle. Later items in the section
    move down one position. Positions are display order only, not keys, so no migration is needed.
  - Name and description are in [contracts/config.md](./contracts/config.md).
- **Rationale**: this follows the spec's placement assumption. A `String` item gives a free-text field
  in RuneLite's settings page.
- **Alternatives**: a new "Custom animations" section. Rejected: the setting belongs with the Animation
  detector.

## R7. Plugin Hub compliance

- **Decision**: compliant. This gets its own record because Principle I lists "no features whose entire
  function depends on player-provided IDs".
- **Rationale**:
  - The plugin's function is complete without the setting. It is empty by default, and every built-in
    detector works unchanged (FR-008). The rule targets plugins that do nothing until the player
    supplies IDs, which this is not.
  - Only the local player's animations are matched, as for built-in ones. Listing a boss's or other
    player's animation ID matches nothing, because only `client.getLocalPlayer()` animations are read.
    So the setting can't become an NPC or boss attack indicator, and no boss or PvP rule is touched.
  - The last-animation display shows only the player's own animation, which is already visible on
    screen. There is no information about other players.
  - Core RuneLite's Idle Notifier uses the same kind of built-in local-player animation list.
- **Alternatives**: none needed. Any future request to match *other actors'* animations would need a
  fresh compliance review.
