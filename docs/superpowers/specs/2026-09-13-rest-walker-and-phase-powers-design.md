# Rest on the Walker and Generic Phase Powers Design

> Status: implemented on feat/rest-walker; see docs/superpowers/plans/2026-09-13-rest-walker-and-phase-powers.md.

## Goal

Move the Rest phase onto the procedure walker, give ACTION, WAKE and REST
powers one generic activation path, and close with an engine-level invariant
that nothing but a walker resume runs while a walker is pending.

- Rest becomes two phase-transition procedures, `BeginRest` and `FinishRest`,
  with REST powers used between them as actions.
- League Treaty becomes a walker power folded into `FinishRest`, answered by
  its off-turn ruler through a new `Distribute` decision shape.
- Silver Tongue is the first phase power: a REST power plus a registered,
  not-yet-active two-adviser restriction.
- The legacy Rest power seam (`RestPowerIntegration`, `RestPowerHandler`,
  `PendingProcedure.RestPower*`) and the Rest events are deleted.

## Background

Verified in code on 2026-09-13 against `feat/engine-redesign` at `50080a7`:

- **Legacy Rest.** `OathRules.handle(RestCommand)` wraps Begin in
  `withFallback(MajorActionKind.Rest)`. Begin (`Rest.validateBegin`) emits
  `RestStarted`, then `RestPowerIntegration.begin` queues League Treaty
  decisions as `AwaitingRestPowerDecision`. Finish emits a computed
  `RestCompleted` whose `applyCompletion` returns card favor to printed suit
  banks, card secrets to the resting player, flips the facedown stash, refreshes
  Supply and sets `TurnState(next, Wake | RoundEnd, Set.empty)`. The last player
  in turn order then runs `finishRound` (`RoundEnded`, and
  `WarExhaustionResolved` at round 8). Finish then calls `enterWake`.
- **Cleanup is global and total.** `RestCleanupPlan.derive` returns all favor on
  every in-play denizen and edifice to its printed suit bank. Relics keep favor
  in place. Any redistribution of card favor before cleanup is therefore only a
  change of which bank the favor reaches.
- **Walker completion.** `walkerTransition` applies `WalkerCompleted`, runs
  `completeAction` iff the reference is an `ActionRef`, and reads the
  continuation from `continuationIn(phase)`, which rejects every phase but Act
  and Wake (`2026-09-12-walker-ownership-and-phases-design.md`, Phases and Open
  items: "Rest completion continuation").
- **Walker resume re-folds.** A windowed node's `Transform`s are re-applied
  against the current state on every resume (`ProcedureWalker.walkFolded`). A
  transform must produce the same vector while its walker is parked.
- **Architecture seam.** `BackendArchitectureSuite` forbids any power name in
  `gameplay/walker` or `gameplay/operations`. `WalkerProcedureRegistry` lives in
  `gameplay/walker`, so a procedure's continuation mapping cannot name a power.
- **Decision vocabulary.** `DecisionQuery` has `ChooseOne` and `Partition`;
  `DecisionOptionRef` has Button, Player, Site, Denizen, Relic, Vision and Deck.
  No shape expresses amounts (the walker spec's "Answer kinds" open item).
- **Powers today.** The catalog prints 98 ACTION, 10 WAKE and 5 REST powers.
  All REST powers are `ReviewedPower`s with an automatic `RestStart` handler
  that only yields ignored-rule diagnostics. `TurnState.usedPowers` holds
  `PowerUseRef(timing, source, powerId)`; `PowerSourceRef` has only `Site`.
- **Adviser limit.** No walker procedure adds advisers yet; legacy
  `CardPlay.validateAdviserReplacement` hardcodes a limit of 3.
- **Headroom.** `ActionDecisionRenderer.scala` is at 734 of 800 lines;
  `OperationValidator.scala` 688; `ProcedureWalker.scala` 674;
  `GameEventJsonSupport.scala` 658; `WalkerOperationCodec.scala` 623.

## Scope

In scope:

- `BeginRest` and `FinishRest` phase transitions, the Rest continuation, the
  turn boundary and the auto-skip after `BeginRest`.
- `BeginTurn` primitive; `PowerSourceRef.Card`.
- League Treaty as a walker `ContributingPower`.
- `DecisionOptionRef.FavorBank`, `DecisionQuery.Distribute`,
  `DecisionAnswer.DistributeAnswer` and the frontend Distribute panel with
  Shift+click fill and drain.
- Generic phase powers: `PhasePower`, `PhasePowerCatalog`,
  `ActionRef.UsePower`, the `usePower` intent and `phasePowers` projection,
  wired and tested in Act, Wake and Rest.
- Silver Tongue's REST power and two-adviser restriction.
- Deleting the legacy Rest power seam, Rest events and Rest power projections.
- The pending-walker invariant.

Out of scope:

- Power costs and the empty-card rule. The first costed phase power adds them.
- The other REST powers (Naysayers, Insomnia, Vow of Obedience, Vow of
  Poverty) and every WAKE and ACTION power in the catalog.
- `DecisionOptionRef.Edifice`. The bank-based Distribute does not need it; the
  first procedure that offers an edifice adds it.
- Undo.
- Search's migration, which activates Silver Tongue's restriction.
- Removing unused `PowerWindow` values (`RestStart`, `RestReturnSecrets`,
  `RestEnd`).

## Rest procedure

### `BeginRest`

`beginRest` is legal in Act, as today.

1. Start gate: the checks of legacy `Rest.validateBegin` (`validateAct` plus the
   audited handler-inventory fingerprint).
2. `PowerRuntime.ignored(..., MajorActionKind.Rest)` diagnostics for accessible,
   unmigrated handlers are emitted as `IgnoredRulesRecorded(actor, Rest, ...)`
   before the walker's first event, exactly as `withFallback` does today.
3. `PhaseTransitionRef.BeginRest` starts with tree
   `Sequence(Vector(EnterPhase(Phase.Rest)))`.
4. It completes in Rest. `continuationIn(Phase.Rest)` now returns
   `OathContinue.AwaitingRestAction(actor)`.

**Auto-skip.** Only after `BeginRest` completes: if the active player has no
usable REST power (see Phase powers), the engine starts `FinishRest` in the
same command. That `FinishRest` may itself park on an off-turn League Treaty
decision. After a REST power has been used, the player always ends Rest
deliberately with `finishRest`, even when no usable power remains. This keeps
a used power's turn open for a future undo.

### Rest phase

Legal controls in Rest: one `usePower` per usable REST power, and
`finishRest`. `LegalActionProjector` drops the legacy `Phase.Rest =>
Vector("finishRest")` special case in favour of this.

### `FinishRest`

`finishRest` starts `PhaseTransitionRef.FinishRest`. Start gate: phase Rest,
requester is the active player, no walker or legacy pending state.

```scala
Sequence(Vector(
  BuildOps(cleanup, window = Some(PowerWindow.RestReturnFavor)),
  BuildOps(supplyRefresh),
  BuildOps(beginNextTurn)))
```

- **`cleanup`** derives `RestCleanupPlan` at walk time and yields the same core
  operations as legacy `applyCompletion`: `CoreMove` of card favor to its
  printed suit bank, `CoreMove` of card secrets to the resting player's play
  area, and `FlipSecrets` of the resting player's facedown stash. A derivation
  failure is `UnsupportedRestState`, as today.
- **`supplyRefresh`** computes the refreshed Supply with the existing
  `ExileSupply.refresh` band calculation and yields
  `AdjustSupply(player, refreshed - current)`, or nothing when the delta is 0.
- **`beginNextTurn`** yields `BeginTurn(next, phase)`. `next` is the next
  player in turn order (wrapping to the first player); `phase` is
  `Phase.RoundEnd` when the resting player is last in turn order, otherwise
  `Phase.Wake`.

### `BeginTurn`

A new `PrimitiveOperation`:

```scala
final case class BeginTurn(player: PlayerId, phase: Phase) extends PrimitiveOperation
```

It sets `turn = TurnState(player, phase, Set.empty)`. Validation requires a
seated player and `phase` in `{Wake, RoundEnd}`. It gains a
`WalkerOperationCodec` branch and a replay mutation.

### Turn boundary

`runsTurnBoundary(procedure)` sits beside `runsActionBoundary` and is true only
for `PhaseTransitionRef.FinishRest`. After `WalkerCompleted(FinishRest)`:

1. If the phase is `RoundEnd`, run the existing `finishRound`, moved from
   `Rest.scala` into `gameplay/phases/rest/TurnBoundary.scala`, which
   `walkerTransition` calls.
2. Unless the game has ended, run the existing `enterWake`.
3. The continuation is `AwaitingWakeAction(turn.activePlayer)`, or
   `GameFinished(winner)`.

`continuationIn` is not consulted for `FinishRest`; the turn boundary owns its
continuation.

### Rest decisions

Every `Decide` parked inside `FinishRest` maps, through
`WalkerProcedureRegistry.continuationFor`, to a new
`OathContinue.AwaitingRestDecision(owner, decision)`. It is generic so the
registry names no power.

## Phase powers

### Contract

`gameplay/powerresolver/PhasePower.scala`:

```scala
trait PhasePower {
  def id: PowerId
  def timing: PowerTiming
  /** Power-specific preconditions beyond access and once-per-turn. */
  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Boolean
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation]
}

final case class PhasePowers(powers: Vector[PhasePower])
```

`build` must be a pure function of state, like every walker tree source: the
tree is rebuilt on every resume.

### Catalog and injection

`gameplay/powers/PhasePowerCatalog.default(catalog): PhasePowers` sits beside
`WalkerPowerCatalog`. A power whose card is absent from the catalog is omitted.

- `OathRules` takes `phasePowerCatalog: PhasePowers` as a constructor
  parameter, defaulting like `walkerPowerCatalog`.
- `WalkerProcedureRegistry.Entry.build` and `rebuild` receive the
  `PhasePowers` so the `UsePower` entry resolves its power without the walker
  package naming one.
- `LegalActionProjector` and `WalkerDecisionProjector` build or receive the
  same default.
- Tests inject synthetic powers through the constructor.

### Sources and usability

A power's sources are derived by the engine, never listed by the power: the
cards the player can access (pawn site, ruled sites, play area) whose catalog
power id equals `power.id`, found through the access rule the reviewed catalog
already uses. Each source is a `DecisionOptionRef` (`Denizen` or `Relic` today).

A power is **usable** from a source iff:

- the requester is the active player;
- `power.timing` matches the phase (`Act`, `Wake`, `Rest`);
- the source is accessible;
- `PowerUseRef(timing, PowerSourceRef.Card(sourceCard), power.id)` is not in
  `turn.usedPowers`;
- `power.usable(...)` holds.

One function computes this and is shared by the `UsePower` start gate, the
legal-action projection, the `phasePowers` projection and the Rest auto-skip.

### `ActionRef.UsePower`

```scala
final case class UsePower(power: PowerId) extends ActionRef
```

- Start argument: the source `DecisionOptionRef`.
- Start gate: the usability function above. Violations are
  `PowerAlreadyUsed`, `WrongPlayer`, or `InvalidEventOrder` naming the failed
  condition.
- Tree:
  `Sequence(Vector(power.build(...), RecordPowerUse(PowerUseRef(timing, PowerSourceRef.Card(card), power.id))))`.
  The use is recorded last, as Take Wealth does.
- As an `ActionRef` it runs the action boundary. Its continuation is
  `continuationIn(phase)`: `ActActionSelection`, `AwaitingWakeAction` or
  `AwaitingRestAction`.
- Every parked `Decide` inside it maps to a new
  `OathContinue.AwaitingPowerDecision(owner, decision)`.
- `ProcedureRef` codec key: `use-power:<power id>`.

### `PowerSourceRef.Card`

```scala
final case class Card(id: CardId) extends PowerSourceRef
```

Added with its `WalkerOperationCodec` branch.

### Projection

- `phasePowers: Vector[PhasePowerProjection]` with
  `PhasePowerProjection(powerId, source: DecisionOptionProjection, name, rulesText)`,
  listing usable powers for the viewer in the current phase.
- Legal actions: `usePower:<powerId>:<sourceWireId>` per usable power.

### Architecture

`BackendArchitectureSuite`'s power-name scan also collects `PhasePower`
implementations, so neither `gameplay/walker` nor `gameplay/operations` names
one.

## Silver Tongue

`gameplay/powers/rest/SilverTongue.scala`: one object implementing
`PhasePower` and `ContributingPower`, id `denizen.silver-tongue` (card 92).

### REST power

- `timing = PowerTiming.Rest`.
- Matching suits: the suits of face-up denizens and edifices at the player's
  pawn site.
- `usable`: at least one matching suit's favor bank holds favor.
- `build`: a `Branch` over the matching banks that hold favor.
  - One bank: `BuildOps` moving one favor from that bank to the player.
  - Several: `Decide(decisionId, owner = player, ChooseOne(FavorBank refs, heading))`,
    then that `BuildOps` reading the answer.
  - No decline option: activating the power is the consent.

### Two-adviser restriction

- `contributions`:
  `Map(PowerWindow.SearchPlayFacedownAdviser -> Vector(Restriction(...)))`.
- The restriction rejects a tree that would leave Silver Tongue's holder with
  more than two advisers.
- Registered in `WalkerPowerCatalog.default`. It is inert until Search is a
  walker procedure, and is not tested by this spec.

### Legacy diagnostic

`RestPowers.SilverTongue`'s `ReviewedPower` handler moves from `RestStart` to
`SearchModifierSelection`. Legacy Search records Silver Tongue's limit as an
ignored rule; Rest no longer reports it.

## Distribute decisions

### Vocabulary

In `model/Decisions.scala`:

```scala
final case class FavorBank(suit: Suit) extends DecisionOptionRef  // kind "favor-bank", wireId suit.key

final case class DistributeSlot(ref: DecisionOptionRef, minimum: Int,
    maximum: Int, suggested: Option[Int])
final case class Distribute(slots: Vector[DistributeSlot], total: Int,
    heading: String, confirmLabel: String) extends DecisionQuery

final case class DistributeAmount(ref: DecisionOptionRef, amount: Int)
final case class DistributeAnswer(amounts: Vector[DistributeAmount])
    extends DecisionAnswer
```

### `DecisionQueries.wellFormed`

Rejects a `Distribute` with:

- duplicate slot refs;
- any slot with `minimum < 0` or `minimum > maximum`;
- `total` below the sum of minimums or above the sum of maximums;
- fewer than two slots;
- sum of minimums or sum of maximums equal to `total` (forced);
- `suggested` present on some slots but not all, or a suggested vector that is
  not itself an accepted answer.

### `DecisionQueries.accepts`

Accepts a `DistributeAnswer` iff every slot ref appears exactly once and no
other ref appears, each amount lies within its slot's minimum and maximum, and
the amounts sum to `total`.

### Wire

- `DecisionAnswerCodec` tag `distribute`:
  `{"kind":"distribute","amounts":[{"option":<ref>,"amount":n}]}`.
- The query projection carries each slot's option, `minimum`, `maximum` and
  optional `suggested`, plus `total`, `heading` and `confirmLabel`.
- Shared `CommandNestedCodecs` gains the matching answer codec.

## League Treaty

`gameplay/powers/rest/LeagueTreatyPower.scala` is rewritten as an automatic
`ContributingPower` (card 237, id `denizen.league-treaty`):

```scala
Map(PowerWindow.RestReturnFavor -> Vector(Transform(...)))
```

### Applicability

The transform returns the children unchanged unless League Treaty's site is
ruled and the denizens and edifices in that site's region hold favor. The
ruler is the `Decide` owner and may be off-turn; `ctx.activePlayer` is the
resting player.

### Inserted nodes

In front of the cleanup leaf:

1. `Decide(destinationId, owner = ruler, ChooseOne(FavorBank(s) for every suit, plus Button("decline"), heading))`.
2. `Branch`: nothing if the answer is decline or the distribution is forced
   (the destination is the only source suit); otherwise
   `Decide(distributionId, owner = ruler, Distribute(...))` with:
   - one slot per source suit other than the destination:
     `minimum = 0`, `maximum = that suit's card favor in the region`,
     `suggested = maximum`;
   - one destination slot: `maximum = total`,
     `minimum = the destination suit's card favor in the region (0 if none)`,
     `suggested = minimum`;
   - `total` = all card favor in the region.
3. `BuildOps`: nothing on decline. Otherwise, for each source suit other than
   the destination, move `maximum - answered amount` favor from that suit's
   cards to the destination bank, taking cards in site order, then card id. On
   the forced branch no favor moves: it would reach the same bank anyway.

The cleanup leaf then returns whatever favor remains on cards to printed suit
banks.

### Purity

Both `Decide`s precede every state-changing node the transform inserts, so the
transform sees the same state and returns the same vector while either is
parked.

### Decision ids

Minted from round, resting player, site and card, as the legacy power does.

### Worked example

Region cards hold 2 Arcane, 2 Discord and 2 Hearth favor; the ruler picks
Nomad. Slots: Arcane `0..2` (suggested 2), Discord `0..2` (2), Hearth `0..2`
(2), Nomad `0..6` (0); total 6. Answer Arcane 0, Discord 1, Hearth 2, Nomad 3
moves 2 Arcane and 1 Discord favor to the Nomad bank; cleanup returns 1 Discord
and 2 Hearth.

## Protocol and projection

- Keep the `beginRest` and `finishRest` intents.
- Add `usePower {powerId, source}` through `CommandIntents`, the command codec
  and decoders, `GameIntentMapper` (`GameCommand.UsePower(powerId, source)`)
  and `Authorization` (active player).
- Delete the `resolveRestPower` and `declineRestPower` intents and commands.
- Delete `RestPowerProjection`, `LeagueTreatyProjection`, and the
  `rest-power-decision` and `rest-power-waiting` pending phases from
  `PendingProcedureProjector`.
- The off-turn League Treaty wait uses the generic walker waiting projection
  Oathkeeper already uses.

## Frontend

- **Phase power buttons.** Act, Wake and Rest panels render one button per
  `phasePowers` entry, submitting `usePower`.
- **Finish Rest.** Rendered only when `finishRest` is a legal action.
- **Distribute panel.** A new renderer file (not `ActionDecisionRenderer`, for
  headroom) backed by a pure `DistributeDecisionState` beside
  `PartitionDecisionState`:
  - `increment(ref)` and `decrement(ref)` step by one, clamped to the slot's
    bounds and to the remaining amount;
  - `fill(ref)` raises a slot by `min(remaining, maximum - current)`;
    `drain(ref)` lowers it to its minimum;
  - `remaining = total - sum`; Confirm is enabled iff `remaining == 0`;
  - the draft starts at `suggested`, or at the minimums when absent.
  - Each row shows the slot label, a −/+ stepper and the maximum. Shift+click
    on + calls `fill`, on − calls `drain`; both buttons carry the tooltip
    "Shift+click: all".
- Delete `RestPowerDecisionRenderer` and the Rest power branches in
  `ActionDecisionRenderer` and `ServerUiSupport`.

## Deleted

- `RestPowerIntegration.scala`, `RestPowerHandler.scala` and the legacy body of
  `LeagueTreatyPower.scala`.
- `PendingProcedure.RestPower*`, `OathContinue.AwaitingRestPowerDecision`.
- `RestCommand.Begin`/`Finish`/`ResolvePower`/`DeclinePower` handling in
  `OathRules.handle` and `Rest.scala`'s `validateRest`, `expected`,
  `applyCompletion`. `Rest.scala` is removed: the start gate moves to
  `gameplay/phases/rest/BeginRestProcedure.scala`, the cleanup and Supply
  builders to `gameplay/phases/rest/FinishRestProcedure.scala`, following
  `EndWakeProcedure`. `RestCleanup.scala` stays.
- `RestStarted`, `RestCompleted` and the League Treaty events, with their
  `GameEventWire` and `LifecycleEventCodec` type strings. No compatibility
  decoding: streams containing them no longer load.
- `RestPowers.SilverTongue`'s `RestStart` handler (moved, see Silver Tongue).

## Replay

- Rest state changes replay through walker step events: `EnterPhase`, the
  cleanup and Supply operations, and `BeginTurn` each apply through
  `WalkerReplay.applyRecorded`.
- `IgnoredRulesRecorded`, `RoundEnded`, `WarExhaustionResolved` and the
  `enterWake` events replay through their existing `evolve` cases.
- Parked League Treaty and Silver Tongue decisions reload through the durable
  `walkerProcedure`, `walkerPending` and `walkerStartArgs`, as Oathkeeper's do.

## Failure handling

- Every start gate failure rejects the command with no events.
- A `Distribute` or `ChooseOne` answer that `accepts` refuses is rejected by the
  walker with the existing typed violation; the walker stays parked.
- A non-owner answering a parked Rest or power decision is refused by the
  walker's owner check.
- Cleanup and Supply derivation failures are `UnsupportedRestState` and reject
  the whole command.
- A restriction violation from any contributing power rejects at command entry,
  as today.

## Delivery order

1. **Decision vocabulary.** `FavorBank` ref, `Distribute` query and answer,
   `wellFormed`/`accepts`, codecs, walker decision projection,
   `DistributeDecisionState` and the Distribute panel.
2. **Primitives.** `BeginTurn` and `PowerSourceRef.Card`, with codecs and
   replay.
3. **Rest on the walker.** `BeginRest`, `FinishRest`, `continuationIn(Rest)`,
   the turn boundary, auto-skip after `BeginRest`, the Rest diagnostics, legal
   actions, League Treaty as a walker power, and deleting the legacy Rest seam,
   events and projections. League Treaty moves in the same stage because the
   legacy hook depends on `RestStarted`.
4. **Phase powers.** `PhasePower`, `PhasePowerCatalog` injection, `UsePower`,
   usability, projections, the `usePower` intent, frontend buttons and the
   synthetic WAKE and ACTION powers.
5. **Silver Tongue.** REST power, restriction registration and the diagnostic
   move.
6. **Pending-walker invariant.**

The implementation plan may split these stages into smaller tasks.

## Testing

### Rest

`RestSuite` rewritten against the walker:

- Rest globally cleans every in-play denizen and relic (existing pin).
- Supply refresh bands.
- A non-last player's Rest reaches the next player's Wake.
- The last player's Rest emits `RoundEnded` and reaches Wake.
- Round 8 Rest resolves War Exhaustion.
- Unmigrated REST powers still record `IgnoredRulesRecorded` at `beginRest`.
- Auto-skip: with no usable REST power, one `beginRest` command reaches the
  next player's Wake.
- With a usable REST power, `beginRest` parks at `AwaitingRestAction`, and
  `finishRest` completes Rest.
- After a REST power is used, the continuation is `AwaitingRestAction` even when
  no usable power remains; no auto-skip.

### League Treaty

- Unruled site or no regional card favor: no decision.
- Ruled site: the off-turn ruler gets `AwaitingRestDecision`; any other player
  answering is refused.
- Decline moves nothing.
- Forced single-suit destination skips the Distribute.
- The worked example produces the stated bank totals.
- Reload while parked on the Distribute resumes to the same decision.

### Silver Tongue

- One matching bank with favor resolves without a decision.
- Several give a `ChooseOne` over exactly those banks.
- No matching favor: not usable, not projected.
- Once per turn.
- The action boundary runs after use.

### Synthetic phase powers

Test-scope WAKE and ACTION `PhasePower`s injected through `OathRules`:

- projected and legal only in their phase;
- the action boundary runs after each;
- once per turn per source;
- `BeginTurn` clears the use.

### Units

- `Distribute` `wellFormed` and `accepts`, one case per rejection.
- Codec round trips: `BeginTurn`, `PowerSourceRef.Card`, `FavorBank`,
  `Distribute` query projection, `DistributeAnswer`, `UsePower` reference and
  the `usePower` intent.
- `DistributeDecisionState`: increment and decrement clamping, `fill` limited
  by remaining, `fill` limited by maximum, `drain` to minimum, `fill` with
  nothing remaining, Confirm enablement, starting draft.

### Pending-walker invariant

For each park context below, submit one instance of every `GameCommand`
constructor. Only `ResolveWalker` and `RollWalker` may be accepted. Where an
existing gate lets another command through, this task adds the gate.

- Recover roll park in Act.
- Off-turn Oathkeeper recipient.
- Off-turn League Treaty in Rest.
- Silver Tongue `ChooseOne` in Rest.

It also asserts that the legacy follow-up commands (Search, Campaign,
Challenge) are refused over a parked walker.

## Open items

- **Undo.** The deliberate `finishRest` after a used REST power keeps room for
  it; undo itself is not designed.
- **Power costs.** Added by the first costed phase power, with the empty-card
  rule.
- **Edifice option refs.** Added by the first procedure that offers an edifice.
- **Silver Tongue's restriction.** Tested when Search moves onto the walker.
- **Remaining phase powers.** Four REST, ten WAKE and 98 ACTION powers migrate
  onto `PhasePower` individually.

## Corrections to other specs

Made at implementation, each pointing here:

- `2026-09-12-walker-ownership-and-phases-design.md`: Phases ("Nothing in this
  design can complete in Rest"), Completion and the action boundary ("Legacy
  Rest commands ... do not run it"), and Open items "Answer kinds" (amounts
  now have `Distribute`) and "Rest completion continuation" (settled).
