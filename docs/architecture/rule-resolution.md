# Power resolution

Every card power that runs is a typed Scala object. Catalog `rulesText` is
documentation and is never executed. Gameplay code must not read `rulesText`
or printed power `.text`; `scripts/check-architecture.py` enforces this.

Design history: [powers design](../superpowers/specs/2026-09-20-powers-design.md),
[global operation restrictions](../superpowers/specs/2026-09-30-global-operation-restrictions-design.md),
[operation rewrite](../superpowers/specs/2026-10-09-operation-rewrite-design.md),
[consent system](../superpowers/specs/2026-10-08-consent-system-design.md)
and [automatic power consent](../superpowers/specs/2026-10-15-automatic-power-consent-design.md).

## Sources and access

`RuleSourceIndex.enumerate` lists the factual sources in a `ReadyGame`: site
cards and edifice faces, advisers, relics, banners, Foundations, legacies and
game rules. Each `IndexedRuleSource` carries a stable `RuleSourceRef` and the
power ids printed on it. `CatalogHandlerInventory.handlerIds` is the full
power id vocabulary of a catalog.

`PowerAccess` is the rulebook's access rule. A player may use a power on a
card at their pawn's site, at a site they rule, or in their play area. A
modifier may make the actor act as if their pawn stood at another site for
the rest of the action. Every contribution's `applicable` and every phase
power reads this one rule.

## Kinds of power

There are three kinds:

- A `ContributingPower` (`gameplay/powerresolver/ContributingPower.scala`)
  hooks power windows inside a walked tree. `WalkerPowerCatalog.default`
  lists the production ones.
- A `PhasePower` (`gameplay/powerresolver/PhasePower.scala`) is a WAKE,
  ACTION or REST power a player uses as an action from the phase menu. Its
  `build` returns the tree the walker runs. The engine checks access,
  once-per-turn use and the cost. `PhasePowerCatalog.default` lists them.
- A `PrintedRule` (`gameplay/operations/PrintedRule.scala`) is a printed
  power enforced as an operation restriction, with no walker or phase power:
  the Hall of Ministers, Ancient Bloodline and the Grand Scepter.
  `OperationRestrictions.printedRules` lists them.

Power code lives under `gameplay/powers/`, grouped by action or timing
(`search/`, `campaign/`, `cardplay/`, `rest/`, and so on), never one package
per card.

## Windows and contributions

A tree node may carry a `PowerWindow`. When the walker reaches the node,
`WalkerPowerGather.applyWindow` gathers the window's contributions with
`ContributionCollector.gather`:

1. `PowerWindowIndex` finds the powers whose `contributions` name the window.
2. Each power's `applicable` is asked with its `PowerCtx`.
3. A power whose use a power-use restriction refuses keeps only its offers
   and rerolls, which its host shows as withheld.
4. Powers that another power `ignores` are dropped.
5. The rest are ordered by their earliest `FoldPhase`, then by
   `ContributingPower.sortKey`: priority, the source's stable key, then the
   power id.

`PowerCtx` is what a contribution reads: `state`, `actor` (the player whose
procedure is running, which can differ from a parked decision's owner),
`source`, `window`, `nodePath`, the hooked `operation`, the `procedure` and
the decisions `answered` so far.

A contribution is one of:

- `Transform` rewrites the hooked node's children, never the whole tree.
  Transforms fold in `FoldPhase.Normal`, then `FoldPhase.Late`.
  `OperationRewrite` finds and replaces operations by what they are.
- `Restriction` checks the whole action tree and returns a violation to
  refuse the action.
- `OptionRestriction` forbids single options of the `Decide` it hooks.
- `Offer` and `OfferReroll` offer a battle plan or a reroll to an
  `OfferHost` or `RerollHost` node.

A power may also declare `operationRestrictions`, which hook no window and
hold on every walker step, and `powerUseRestrictions`, which judge other
powers' uses. `ExecutedTrigger`, `onArrival` and `takesBurn` let a power react
to executed operations, arriving advisers and burns. `Deferred` lets a power
hold an operation and resolve it later in the same action.

Contribution functions must be pure functions of the state they are handed.
The walker rebuilds the tree and refolds every window on every command, and a
parked position is a path of child indexes. So a `Transform` that adds nodes
must add the same number whatever the state is. Put a state-dependent part in
a `Branch`, which is one node however many children it selects, as Book
Binders does.

## Modifiers

A power with `PowerResolution.PlayerSelected` is a modifier. Each action that
takes modifiers starts with a `ModifierChoice` loop at its modifier-selection
window, such as `SearchModifierSelection`. The power declares
`selectionWindows`, `selectionPayments` (paid at the pick), `offerReason`
(unavailable or warned, given the picks so far) and `selectionQuestions`.
Picked modifiers join the walk's `WalkerPowers`.

## Restriction look-ahead

The walker never offers an option from which the action cannot end legally.
Before a `Decide` parks, it walks a copy of the state from each answer
(`WalkerSearch.narrow`, called by `ProcedureWalker`). An option survives when
some path from it reaches the end of the action with every required operation
accepted and no `Restriction` violated. A required decision with nothing left
refuses the action, so its menu option shows as unavailable. The search stops
at any operation that would reveal hidden information
(`HiddenOutcomes.hides`: rolls, draws, shuffles, peeks and reveals) and
counts that path as legal. `SearchMemo` caches verdicts per `WalkerPowers`
instance.
`WalkerPowers.quiet` turns the search off inside a search.

`OptionRestriction`s apply in the window fold itself
(`WalkerPowerGather.restrictOptions`). The walk, the answer check, the
projector and `WalkerSimulation` therefore all see the same options. A hidden
option shows the reason its restriction's `note` gives;
`WalkerPowerGather.lookAheadNoted` finds that reason for an option the
look-ahead hid.

## Consent waivers

A restriction on enemies of a power's ruler may carry a `Waiver`. Its
`granter` is the ruler, or nobody when bandits or the Empire rule. The ruler
may consent to lift the restriction for one decision and option, keyed by
`ConsentKey` and remembered for the turn. `ConsentedRestriction` reads the
same ledger for operation restrictions, and a `WaivableRule` is a printed
rule that can be waived this way. An automatic power use that its rulers
may refuse waits at a `PowerUseStep` (`walker/AutomaticUses.scala`).

`ConsentPowers` lists every waivable power for the consent grid, the
preference check and the Game Log. Consent commands go to the application's
`ConsentDesk`, not to the rules. Their events evolve through
`gameplay/consent/ConsentEvolution.scala`.

## Unimplemented powers

A power the code does not implement is ignored and recorded. It never fails
or blocks a command.

`ReviewedPowerCatalog` holds the reviewed inventories (`ActionPowers`,
`CampaignPowers`, `RestPowers` and the others in `gameplay/powers/`). They
name each reviewed power with the window where it would act and whether it
is implemented. `PowerImplementationStatus` counts a power as implemented
when `WalkerPowerCatalog`, `PhasePowerCatalog` or
`OperationRestrictions.printedRules` covers it. The UI's unimplemented-card marker reads the same status.

`PowerRuntime.ignored` runs at fixed points: an action's start (the
registry entry's `fallbackKind`), a faceup card play (`WhenPlayed`), Wake's
start and the action boundary. An applicable automatic power that is not
implemented becomes an `IgnoredRuleDiagnostic`. `OathRules`,
`OathRulesWalker` and `OathDriver` append the diagnostics in one
`IgnoredRulesRecorded` event. An
unimplemented player-selected power is simply not offered. Replay recomputes
the diagnostics and rejects an event that does not match.

A power id that the catalog inventory does not list is a catalog error
(`UnsupportedRuleCatalog`), not an unimplemented power.

## Replay

`WalkerPowers` is built for live commands only. Replay applies the recorded
operations and never gathers powers or folds windows again. Each
`WalkerStepRecorded` carries the `contributions` order its node used.

## Adding a power

1. Verify the catalog power id and its printed source.
2. Confirm `RuleSourceIndex` exposes the state the power reads.
3. Declare it as a named object in the family package under
   `gameplay/powers/`, as a `ContributingPower`, a `PhasePower`, or a
   `PrintedRule`, and add it to its catalog.
4. Read access through `PowerAccess`. Express effects as core operations;
   never edit owned state directly.
5. Remove its reviewed stub, or let `PowerImplementationStatus` cover it, so
   no stale diagnostic is recorded.
6. Test ordering, inactive and facedown sources, the look-ahead, and replay.
