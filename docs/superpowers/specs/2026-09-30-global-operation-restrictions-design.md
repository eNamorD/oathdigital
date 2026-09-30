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
  Passing it between players (`Take`, `Give`) is allowed. The Grand Scepter is
  never facedown, so pruning on it reveals nothing.
- **Take is the keyword.** A restriction on `Take` refuses the `Take`
  operation only. A `Give` is not a `Take`, so a Negotiation transfer is
  allowed.

## Design

### The restriction set

`OperationRestrictions.forCatalog(catalog).active(powerRestrictions,
selectedModifiers)` returns the restrictions that hold for a command. It merges
three sources:

- **Rule restrictions** that always apply: the active-modifier rule, bound to
  the modifiers selected for the running action.
- **Printed restrictions.** A single catalog function turns a card's printed
  properties into restrictions: `LockedCards(cardIds)` from the lock icon, the
  Hall of Ministers from its intact face, and `GrandScepter(relicId)` from
  `RelicRole.GrandScepter`. `LockedCards` is one restriction holding every
  lock-icon card, so an operation is flattened once however many cards are
  locked; a refusal still names the card. The Hall is printed rather than a
  power because
  `CardPlay.legalChoices` runs inside a tree builder, which holds only the
  catalog.
- **Power restrictions.** A power registers them through a new member,
  `ContributingPower.operationRestrictions`. It is not a window-keyed
  contribution, since it hooks no window: it holds wherever the power is
  offered. Lost Tongue is its first user, in Catalog batch 3.

That function is the seam the card-classes phase replaces. When cards become
Scala classes, Locked and Grand Scepter become traits a card mixes in, and
the restrictions themselves do not change.

`OperationPipeline.run` takes the restriction set as a required argument with
no default, so a new caller cannot forget it. Its callers are:

- `ProcedureWalker.recordBatch`, for every walker step;
- `MinorActions.evolveOperations` and `StateBasedEvaluation.evolve`;
- `OathRulesWalker.requirePayable`, the modifier payment dry run.

`CardPlay.legalChoices` asks the catalog's rule and printed restrictions,
with the modifiers recorded in the state, instead of `DiscardRestrictions`.
Until slice 2, that is the only option filter that consults the set.

### Composites are checked whole

The walker checks the restriction set against a composite before
`walkComposite` splits it into child steps. A restriction therefore sees
`Take`, `Give`, `Swap` or `Discard.Denizen`, never only the `Move`s they
contain. A composite refused as optional is skipped whole.

A container (`Sequence`, `Repeat`, `Branch` and the card-played windows) only
holds other operations, so it is never screened: its children are screened when
the walker reaches them, and refusing the container would skip its unrelated
children.

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
- **Any rejection prunes.** The search runs the real pipeline, so a path
  fails when any required operation is rejected, for whatever reason, not
  only when a restriction refuses it.
- **Answers tried.** A choose-many keeps an option when some accepted
  selection holding it survives; selections are tried smallest first, and the
  search stops at the first survivor. A choose-amount is tried value by value
  and narrowed when the survivors form one range. Partition and Distribute are
  not enumerated: a search that reaches one counts the path as legal, and the
  answer-time check stays. A decision owned by another player is answered by
  its owner in the same simulation.
- **Loops.** A search that reaches a decision it already stands at, in the
  same state, stops there and counts the path as legal. The first visit tries
  the other answers.
- **Atomic batches.** A `BuildOps` marked `required` runs whole or rejects,
  so a refused operation inside it fails the path instead of being skipped.
- **Empty choices.** A choose-one `Decide` marked `passWhenEmpty` is passed
  when the search leaves it no option, as an empty optional choose-many is,
  instead of failing the path. The walk passes it without asking, and a
  search passes it too. With one option left it still parks: the player sees
  the one option and confirms it. Fae Merchant is the first user: it offers
  every relic held, the search hides the Grand Scepter, and with only the
  scepter held and an empty relic deck it asks nothing and puts nothing back.
- **Phase powers.** A phase power's use is offered only when the same dry run
  a start runs accepts it.
- **Memo.** A verdict asked from the live walk is kept on its `WalkerPowers`
  instance (`SearchMemo`), keyed by the tree's identity, the position, the
  state, the answers so far and the answer. One command reads a park several
  times: the walk, the answer check and each projection or preview. A verdict
  asked from inside a search is not kept, since it also depends on the
  decisions that search already stands at.
- **No depth cap.** A cap would count the paths it cut off as valid, which is
  an incorrect validation. If the search is too slow, the architecture is
  re-evaluated instead (see the budget below).
- **One look-ahead.** The search replaces `WalkerPowerGather.probe`. A path
  also fails when an answer adds a tree-level `Restriction` violation that the
  answers so far do not already produce.
- **Notes.** The notes that tree-level `Restriction`s write for the options
  they hide stay. An operation restriction's note is deferred until a card
  needs one: Lost Tongue's refused `Take` writes no line (Catalog batch 3).

The search reuses the walker's simulation (`WalkerSimulation`), which runs the
real pipeline on a copy of the state, so it cannot disagree with execution.

**Performance budget.** Before slice 2 starts, record the full `sbt test` wall
time and add a benchmark of a Campaign park on a full board. After
slice 2, `sbt test` may grow by at most 15% and the park must answer in under
50 ms. If either fails, stop and bring alternative designs to the product
owner. Lowering a depth cap is not one of them.
The baseline, measured on 2026-09-30 before slice 2: `sbt test` took 9 s (two
warm runs took 9 s and 8 s), and the `SearchBudget` benchmark measured 12 ms for
the Campaign start and 2 ms for reading its park.
After slice 2, without the memo, `sbt test` took 11 s (summed per-test time
+35%, mostly the Campaign suites), so the budget failed. A profile put the cost
in the existing pipeline run once per searched answer (`ContributionCollector.gather`
and `CardIndex.from`), not in the two caches named under "Verify at plan time".
The memo brought it to 9 to 10 s, with summed per-test time +9.4% (73.07 s to
79.91 s), the Campaign start at 13 ms and reading its park at 0 ms.

### What retires

- `DiscardRestrictions`, its coverage suite, and `BuildOps.restrictions`.
- `CardPlay`'s locked-adviser check, in slice 1: the pipeline refuses the
  discard instead.
- The option filters of Horned Mask and Twin Brother, in slice 2. Until the
  search hides refused options, deleting them would offer a locked card and
  then skip it silently, so slice 1 points them at the shared, faceup-aware
  `OperationRestrictions.isLocked`. Both retired in slice 2, with
  `isLocked`.
- Fae Merchant's Grand Scepter filter, in slice 3, with its own "ask only
  when more than one relic is eligible" rule. It now asks whenever it holds a
  relic, so a lone relic is confirmed with one click, and its choice is passed
  when empty.
- `WalkerPowerGather.probe`.

### Take for Challenge and Conspiracy

Challenge custody and Conspiracy's banner transfer move the banner by a raw
`Move`. Both become `Take`, as a Campaign's spoils and Take Wealth already
are, so a restriction on taking sees them. Conspiracy's relic transfer stays a
`Give`.

Challenge custody sits directly in the tree, so the walker checks the `Take`
whole and then records its `Move` leaf. The Game Log line, which reads that
`Move`, is unchanged. The custody is a required `Take`, because the payment
buys the banner: a refused custody fails the path, and the search hides that
banner. Conspiracy's transfer runs in a `BuildOps`, so it is recorded as a
`Take`, and its Game Log line is a note, which reads no operation. It stays
optional, like the relic `Give` beside it. Whether a refused Conspiracy
target is hidden is decided with Lost Tongue, the first restriction on `Take`.

## Slicing

| Slice | Content |
|---|---|
| 1. The seam | The restriction set and the power member. The pipeline's required argument and its four callers, plus `CardPlay.legalChoices`. Composites checked before they are split. `LockedCards`, the active-modifier rule and the Hall of Ministers. The facedown fix. `DiscardRestrictions`, its coverage suite, `BuildOps.restrictions` and `CardPlay`'s locked check retire. |
| 2. Lazy pruning | The baseline measurement and the benchmark first. The depth-first search at parks and action start, stopping at hidden information, with its per-instance memo. Notes for pruned options are deferred until a card needs one. `probe` and the Horned Mask and Twin Brother filters retire. |
| 3. Grand Scepter and Take | `GrandScepter` per relic. Choices passed when empty, and Fae Merchant's filter retires. Challenge custody and Conspiracy's banner transfer become `Take`. |

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
  - The benchmark and the `sbt test` time meet the budget. The benchmark is a
    program run by hand, never part of `sbt test`: a timing check is not
    deterministic.
- **Slice 3.**
  - On a hand-built state, the Grand Scepter refuses a discard, a return to
    the relic deck and a `Bury`, and allows a `Take` and a `Give`.
  - A choose-one marked `passWhenEmpty` with no option left is passed, by the
    walk and by a search. With one option left it parks, offering that
    option.
  - Fae Merchant does not offer the Grand Scepter without its own filter.
    Holding the scepter and one other relic, it offers that relic alone.
    Holding one relic, it offers it for the player to confirm. Holding only
    the scepter, with an empty relic deck, it asks nothing and puts nothing
    back.
  - A restriction on `Take` sees Challenge custody. Conspiracy records a
    `Take` for the banner. Both Game Log lines are unchanged.
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
- **Search inputs.** Resolved in the slice 2 plan: see **Answers tried** under
  Lazy pruning.
- **Challenge and Conspiracy log lines.** That turning the `Move` into a
  `Take` keeps their Game Log lines.
- **The set's cost inside the search.** `WalkerPowers.operationRestrictions` is
  a lazy value per instance, and `powers.copy(probing = false)` (the probe and
  the look-ahead) builds a fresh instance that recomputes it. The search must
  carry the computed vector through such copies, or it rebuilds the set at
  every step. `CardPlay.legalChoices` also builds `forCatalog(catalog).active`
  on every call, which scans the catalog's denizens and edifices, and
  `LockedCards.showing` scans every site per check; cache the catalog-level set
  and index the locked cards in play if the measured budget needs it. Slice 2
  did not need either: the profile showed neither, and the memo under Lazy
  pruning met the budget.

## Out of scope

- **Lost Tongue** registers its `Take` restriction in Catalog batch 3,
  slice 1b.
- **The Vision-play guards** stay window `Restriction`s.
- **Cards as Scala classes** is its own phase, after Catalog batch 3.
