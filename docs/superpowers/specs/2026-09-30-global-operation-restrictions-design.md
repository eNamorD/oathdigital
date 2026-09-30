# Global Operation Restrictions

**Status:** designed 2026-09-30. It is the prerequisite of
[Catalog batch 3](2026-09-29-catalog-batch-3-design.md), which starts after it.

**Builds on** [Powers batch 1](2026-09-20-powers-design.md) (contributions,
windows and the restriction look-ahead) and the
[Power log lines design](2026-09-26-power-log-lines-design.md) (notes for
hidden options).

## Goal

A rule that forbids an operation must hold wherever that operation runs, not
only where a procedure remembered to ask for it. Today a restriction reaches
the pipeline only when a `BuildOps` node passes it, so a persistent rule such
as Lost Tongue's "cannot take" would need every power that takes to opt in.

This phase makes operation restrictions global. It moves the Locked rule onto
the new seam, adds the Grand Scepter's "cannot be removed from play", and
replaces the restriction look-ahead with a search that hides every option
that cannot lead to a legal outcome.

## Today

- `OperationRestriction` (`model/OperationReason.scala`) has one
  implementation, `DiscardRestrictions`. It is a per-call argument of
  `OperationPipeline.run`, supplied only by `BuildOps.restrictions`, which
  five nodes set: Dazzle, Proving Grounds, Horned Mask, `PlanDiscard` and
  `CardPlayProcedure`. `CardPlay.legalChoices` also calls it by hand.
- `DiscardRestrictions` holds four rules: a faceup locked denizen, an intact
  edifice, a modifier selected for the running action, and the Hall of
  Ministers. It refuses Discards only. Its coverage test scans for
  `Discard.(Denizen|Vision|RuinedEdifice)`, so the relic discards of Broken
  Forge and Magic Carpet carry no restriction.
- `CardPlay` and Horned Mask refuse to discard a facedown locked adviser,
  while `DiscardRestrictions` allows it. Twin Brother filters locked
  advisers out of its swap by itself.
- A composite that sits directly in a tree (Take Wealth's `Take`, Challenge
  custody) is split by the walker into its `Move` leaves before it is
  recorded. Only a `BuildOps` batch reaches the pipeline whole.
- The restriction look-ahead (`WalkerPowerGather.probe`) re-checks
  tree-level `Restriction`s for each hypothetical answer. It never consults
  operation restrictions, so an option whose operation would be refused stays
  offered.
- The Grand Scepter is protected only by Fae Merchant's own filter. It is
  never dealt in all-Exile games.
- Nothing in all-Exile legitimately moves or flips a locked card: Negotiation
  transfers only favor and relics, and the Chronicle is not built on
  operations.
- Replay applies recorded operations without re-checking restrictions. That
  stays: a recorded operation was legal when it ran.

Nine power files restrict through the other contribution kinds. Their window
`Restriction`s are action-level guards that ignore the operation (Vow of
Peace, Take Wealth, Fortress, Travel sites, and the Vision-play guards of
Secret Police, Sacred Ground and Vow of Obedience). Their `OptionRestriction`s
name a choice (Circlet of Command, Forgotten Vault, Fortress, Narrow Pass).
Both stay as they are.

## Rules

- **Refusal kind.** Every global restriction refuses as `Impossible`. An
  optional operation it refuses is skipped; a required one rejects the batch,
  as today.
- **Locked.** A card that is locked now refuses any `Move`, `Flip` or `Swap`
  of itself. A discard, a `Take` and a `Give` are `Move`s. `Bury` ignores
  locked. A card is locked when it is faceup and prints the lock icon: a
  `LockedAdviserOnly` denizen as a faceup adviser, or an intact edifice.
  Facedown cards have no restrictions.
- **Active modifier.** A card selected as a modifier for the running action
  cannot be discarded while the action runs.
- **Hall of Ministers.** Unchanged in meaning: no Discard from a site whose
  ruler is the acting player's enemy and rules a site with the intact Hall.
- **Grand Scepter.** It refuses any operation that moves it out of play: a
  discard to the set-aside relics, a return to the relic deck, or a `Bury`.
  Passing it between players (`Take`, `Give`) is allowed.
- **Take is the keyword.** A restriction on `Take` refuses the `Take`
  operation only. A `Give` is not a `Take`, so a Negotiation transfer is
  allowed.

## Design

### The restriction set

`OperationRestrictions.active(ready, catalog, selectedModifiers)` returns the
restrictions that hold in a state. It merges two sources:

- **Rule restrictions** that always apply: the active-modifier rule.
- **Card restrictions**, gathered from the cards in play. Powers contribute
  them through a new contribution kind, `OperationRestrictionContribution`,
  beside `Restriction` and `OptionRestriction`, and the Hall of Ministers
  becomes one. A single catalog function turns a card's printed properties
  into restrictions: `Locked(cardId)` from the lock icon and
  `GrandScepter(relicId)` from `RelicRole.GrandScepter`. Each restriction
  names its card, so a refusal is attributable.

That function is the seam the card-classes phase replaces. When cards become
Scala classes, Locked and Grand Scepter become traits a card mixes in, and
the restrictions themselves do not change.

`OperationPipeline.run` takes the restriction set as a required argument with
no default, so a new caller cannot forget it. Its callers are:

- `ProcedureWalker.recordBatch`, for every walker step;
- `MinorActions.evolveOperations` and `StateBasedEvaluation.evolve`;
- `OathRulesWalker.requirePayable`, the modifier payment dry run.

`CardPlay.legalChoices` asks the same set instead of `DiscardRestrictions`.

### Composites are checked whole

The walker checks the restriction set against a composite before
`walkComposite` splits it into child steps. A restriction therefore sees
`Take`, `Give`, `Swap` or `Discard.Denizen`, never only the `Move`s they
contain. A composite refused as optional is skipped whole.

### Lazy pruning

A validator that walked the whole procedure tree at the start could not
exist: `BuildOps`, `Branch` and `Repeat` are resolved at walk time from the
current state, choices such as `ChooseMany` multiply, and rolls and draws are
hidden. The search below gives the same result for every branch a player can
reach, computed only when the player reaches it.

- **Where it runs.** Before a `Decide` is parked, and at the start of an
  action, where choosing the action counts as the first decision.
- **What survives.** An option survives when some path from it reaches the
  end of the action with every required operation accepted by the
  restriction set and no tree-level `Restriction` violated. The search is
  depth-first and stops at the first path that succeeds.
- **Later decisions.** When the search reaches another decision, including
  one owned by another player, it prunes that decision's options the same
  way. If every option is pruned and the decision is required, the path
  fails.
- **Hidden information.** The search stops at any `Roll`, `Draw` or `Shuffle`,
  and at any operation that shows the chooser a card they cannot see (a
  `Peek`, a `Reveal`, a facedown card flipped faceup). A path that reaches one
  counts as valid. Pruning past that point would leak the outcome. A refusal
  there happens at execution, as today.
- **No depth cap.** A cap would count the paths it cut off as valid, which is
  an incorrect validation. If the search is too slow, the architecture is
  re-evaluated instead (see the budget below).
- **One look-ahead.** The search replaces `WalkerPowerGather.probe`. It checks
  tree-level `Restriction`s and operation restrictions in one pass.
- **Notes.** An operation restriction may carry a `note`, like `Restriction`,
  that the Game Log writes when it hides an option. Locked and the Grand
  Scepter are silent.

The search reuses the walker's simulation (`WalkerSimulation`), which runs the
real pipeline on a copy of the state, so it cannot disagree with execution.

**Performance budget.** Before slice 2 starts, record the full `sbt test` wall
time and add a benchmark suite with a Campaign park on a full board. After
slice 2, `sbt test` may grow by at most 15% and the park must answer in under
50 ms. If either fails, stop and bring alternative designs to the product
owner. Lowering a depth cap is not one of them.

### What retires

- `DiscardRestrictions`, its coverage suite, and `BuildOps.restrictions`.
- The locked filters of `CardPlay`, Horned Mask and Twin Brother, and Fae
  Merchant's Grand Scepter filter.
- `WalkerPowerGather.probe`.

### Take for Challenge and Conspiracy

Challenge custody and Conspiracy's banner transfer move the banner by a raw
`Move`. Both become `Take`, as a Campaign's spoils and Take Wealth already
are, so a restriction on taking sees them. Conspiracy's relic transfer stays a
`Give`.

## Slicing

| Slice | Content |
|---|---|
| 1. The seam | The restriction set and its contribution kind. The pipeline's required argument and its four callers, plus `CardPlay.legalChoices`. Composites checked before they are split. `Locked` per card, the active-modifier rule and the Hall of Ministers. The facedown fix. `DiscardRestrictions`, its coverage suite, `BuildOps.restrictions` and the locked filters retire. |
| 2. Lazy pruning | The baseline measurement and benchmark suite first. The depth-first search at parks and action start, stopping at hidden information. Notes for pruned options. `probe` retires. |
| 3. Grand Scepter and Take | `GrandScepter` per relic, and Fae Merchant's filter retires. Challenge custody and Conspiracy's banner transfer become `Take`. |

Slice 1 alone closes the relic-discard gap. Slice 2 is the riskiest, so it
lands on a seam that is already in place.

## Testing

- **Slice 1.**
  - Locked refuses a `Move`, `Flip` and `Swap` of a faceup locked adviser and
    of an intact edifice. It allows the same operations on a facedown locked
    adviser, and allows `Bury`.
  - A composite in a static tree is checked before it is split: a
    restriction on `Take` sees Take Wealth's `Take`.
  - Dazzle, Proving Grounds, Horned Mask, `PlanDiscard` and card play keep
    their current suites green with the per-call restrictions removed.
  - Broken Forge and Magic Carpet now honour the Hall of Ministers and the
    active-modifier rule.
  - Horned Mask and card play may discard a facedown locked adviser.
  - `MinorActions` and `StateBasedEvaluation` receive the set.
- **Slice 2.**
  - An option whose required operation is refused is hidden. An option whose
    refused operation is optional stays.
  - A later required decision with every option pruned hides the earlier
    option.
  - An action with no legal path is not offered.
  - The search stops at a roll: an option whose refusal lies after a roll
    stays offered.
  - The existing look-ahead and hidden-option note suites stay green.
  - The benchmark suite and the `sbt test` time meet the budget.
- **Slice 3.**
  - On a hand-built state, the Grand Scepter refuses a discard, a return to
    the relic deck and a `Bury`, and allows a `Take` and a `Give`.
  - Fae Merchant does not offer it without its own filter.
  - Challenge and Conspiracy record a `Take` for the banner, and their Game
    Log lines are unchanged.
- `BackendArchitectureSuite` still applies: no power names in walker sources,
  and powers do not import `gameplay.walker`.

## Verify at plan time

- **The split point.** Where `walkComposite` splits a composite, and that
  checking the composite there covers every static composite (Take Wealth,
  Challenge custody, Conspiracy).
- **Selected modifiers outside an action.** What `active` receives from
  `MinorActions` and `StateBasedEvaluation`, where no action runs.
- **The procedure at action start.** `PowerCtx.procedure` is `None` at an
  action's start command (walker follow-ups). No global restriction may read
  it until that is fixed.
- **A hidden Grand Scepter.** A facedown relic's identity must not decide
  whether an option is pruned. Confirm the search treats an operation on a
  card the chooser cannot see as accepted.
- **Search inputs.** How the search enumerates `ChooseMany` and
  `ChooseAmount` answers, and whether a decision owned by another player can
  reuse the same simulation.
- **Challenge and Conspiracy log lines.** That turning the `Move` into a
  `Take` keeps their Game Log lines.

## Out of scope

- **Lost Tongue** registers its `Take` restriction in Catalog batch 3,
  slice 1b.
- **The Vision-play guards** stay window `Restriction`s.
- **Cards as Scala classes** is its own phase, after Catalog batch 3.
