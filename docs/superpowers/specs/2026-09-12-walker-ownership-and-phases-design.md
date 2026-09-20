# Walker Ownership, Phases and Triggered Procedures Design

> Status: approved; implemented. Landed by
> `docs/superpowers/plans/2026-09-12-walker-ownership-and-phases.md`, Tasks
> 1-8, commits `25a8a69..4c98292` (per-task commits: `25a8a69`, `f2c1ab3`,
> `02e1357..877d7e4`, `266f2fb`, `4321aae..90f4e6a`, `dae427d`,
> `7ccf57a`, `d5e7e4b..4c98292`). See
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`'s Migration
> status for what settled.

## Goal

Remove the walker constraints that block the next migration batch, without
porting any legacy action except the one slice that proves the changes:

- a parked decision may be owned by a player other than the active player;
- a walker procedure may park, resume and complete in any phase;
- the engine may start a procedure itself, not only in response to a
  `StartWalker` command;
- the action boundary runs after every action, in every phase, and is decided
  by what kind of procedure completed rather than by the phase it started in.

`OathkeeperRecipient` is ported as the proving slice. It is the smallest
inventory case that needs all four, and porting it turns every title change
into one walker procedure.

## Background

Batch 1 closed with nine `PendingProcedure` cases left
(`2026-09-05-procedure-walker-design.md`, Migration status). Reading them
against the walker surfaced these conflicts, all verified in code on
2026-09-12:

- **Ownership.** `walkerResumeContext` requires the requester to equal the
  stored `pending.actor` and that actor to equal `turn.activePlayer`, and
  `ProcedureWalker` requires `decide.owner == ctx.actor`. Legacy cases need
  other owners: Campaign's player defender chooses plans, Negotiation
  participants accept, Rest power hooks carry a resolver-chosen
  `decisionOwner`, and the Oathkeeper holder chooses a recipient.
- **Phase.** Resume rejects any phase but Act; completion rejects any phase
  but Act and Wake, and ran the action boundary only after a procedure that
  started in Act.
- **Engine-started decisions.** The Oathkeeper recipient decision is started
  by `completeAction` with no player command.
- **Wrong rule.** The Oathkeeper check (and bandit refill) must run at the end
  of every action, including Wake and Rest actions. Today it runs only after
  Act actions.
- **Redundant state.** `PendingTree.actor` always equals `turn.activePlayer`:
  a fresh walk takes it from the turn (`ProcedureWalker.scala:129`), replay
  checks every walker event's actor against the turn
  (`WalkerReplay.validateActor`), and no operation writes the active player.
- **Two title writers.** `OathkeeperChanged` and `OathkeeperRecipientChosen`
  each write `current.title` directly, and each resets the side to Oathkeeper.
- **Unreachable guards.** `completeAction` checks `hasResult` twice and maps
  `UsurperVictory` to `GameFinished`, but a result is only ever set by Wake-start
  evaluation or after round 8, and `OathLifecycle` rejects every command with
  `GameEnded` once one exists.
- **Headroom.** `serialization/WalkerEventCodec.scala` is at 785 of 800 lines.

## Scope

In scope: off-turn decision ownership; parking, resuming and completing in any
phase; engine-started (triggered) procedures; the action boundary after every
action; splitting `WalkerEventCodec.scala`; porting Oathkeeper end to end,
including its frontend.

Out of scope, each recorded under Open items: Negotiation's any-order
multi-owner acceptance; new answer kinds (amounts, ordering, per-site
allocations); per-action cleanup (minted decision ids, Campaign's
`bandit-validation` context, Campaign's plan vocabulary); power selection by an
off-turn owner; an owner who never answers; turn-scoped power activation. The
Rest completion continuation was settled by
`2026-09-13-rest-walker-and-phase-powers-design.md`.

## Procedure references

Three families replace the single `ActionRef`. Scala 2.13 has no union types,
so they form a sealed hierarchy in `model`:

```scala
sealed trait ProcedureRef { def key: String }
sealed trait StartableRef extends ProcedureRef
sealed trait ActionRef extends StartableRef              // Recover, Forge, Travel, TakeWealth
sealed trait PhaseTransitionRef extends StartableRef     // EndWake
sealed trait TriggeredProcedureRef extends ProcedureRef  // Oathkeeper
```

- **Actions** are what a player spends as an action, in any phase. The action
  boundary runs after one completes. A future card granting an action during
  Rest is an `ActionRef`.
- **Phase transitions** are player-commanded changes of phase. They are
  procedures because a phase change is a state write, and they are not
  actions, so no boundary runs after one. End Wake moves here from
  `ActionRef`.
- **Triggered procedures** are started by the engine. No command can name one
  and no boundary runs after one.

`key` is unique across all three families, and `ProcedureRef.all` enumerates
every reference. On the wire and in the journal a reference is encoded as a
family tag plus its key (`"action"`, `"phase-transition"`, `"triggered"`), so
a decoder rejects a triggered reference wherever a startable one is expected,
and rejects an unknown family.

`StartWalker` takes a `StartableRef`, so starting a triggered procedure from a
client cannot be expressed. `GameIntent.EndWake` and `GameCommand.EndWake`
keep routing to `StartWalker`, now naming `PhaseTransitionRef.EndWake`.

### Registry

`WalkerActionRegistry` becomes `WalkerProcedureRegistry`: one `Entry` type in
one map keyed by `ProcedureRef`. A family that does not use a field leaves it
empty, which batch 1 already made possible for `rollDecisionId` and
`modifierWindow`:

```scala
final case class Entry(
    fallbackKind: Option[MajorActionKind],   // was required; None for triggered
    rollDecisionId: Option[String],
    modifierWindow: Option[PowerWindow],
    continuationFor: (String, PlayerId, DecisionId) => Option[OathContinue],
    build: (ExecutableCatalog, ReadyGame, PlayerId,
      Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
    rebuild: (ExecutableCatalog, ReadyGame, PlayerId,
      Vector[DecisionOptionRef]) => Either[OathViolation, Operation])
```

`continuationFor`'s player argument is now the player being awaited (see
Ownership), and the `PlayerId` passed to `build`/`rebuild` is the active
player. The registry suite asserts the map covers `ProcedureRef.all`.

## Durable state and events

```scala
final case class CurrentGameState(
  ...,
  walkerPending: Option[PendingTree],
  walkerProcedure: Option[ProcedureRef],        // was walkerAction: Option[ActionRef]
  walkerModifiers: Vector[PowerId],
  walkerStartArgs: Vector[DecisionOptionRef],
  ...)

final case class PendingTree(
  at: Vector[String],
  answered: Vector[Answered])                   // no player: see below

final case class Answered(
  decisionId: String,
  answer: DecisionAnswer,
  by: PlayerId)                                 // who submitted it
```

**The active player is not stored.** A walker procedure always belongs to the
active player's turn, and nothing a walk can do changes the active player. So
the walk, the rebuild, the projection and every contribution read
`turn.activePlayer`, and the rule "a procedure belongs to the active player"
holds by construction instead of by comparing two stored copies.
`walkerResumeContext`'s `pending.actor == activePlayer` check and replay's
`validateActor` are deleted with the field.

**Who answered is stored.** With off-turn owners, the submitter of an answer
is the one player identity that varies. `Answered.by` is set on the command
path from the authorized requester, which equals the parked decision's owner,
and is journalled so log lines can say who chose what from the payload alone.
Replay does not re-derive the owner, as it re-runs no other gate.

Events:

- `WalkerParked(procedure, at, answered, modifiers, startArgs)`, no actor;
- `WalkerStepRecorded` loses `actor`;
- `WalkerCompleted(procedure)`, no actor.

`walkerModifiers` and `walkerStartArgs` keep their lifetimes. A triggered
procedure parks with both empty.

## Ownership

**Starting.** `startWalker` checks `requester == turn.activePlayer` once, for
every startable procedure, before building. Each procedure's `build` keeps its
phase gates (`validateAct`, `validateReady`); the player check becomes the
walker's, because it is now a rule about every procedure rather than something
each action must remember. `startTriggered` has no requester.

**Resuming.** `walkerResumeContext` keeps: a procedure and a parked position
exist, and no legacy `PendingProcedure` is present. It deletes the stored-actor
check and the `Phase.Act` gate (see Phases). It rebuilds the tree against
`turn.activePlayer` and runs restrictions, as today. Authorization then depends
on the parked node:

- **Parked `Decide`:** `ProcedureWalker.resolve` receives the requester, and
  checks `decide.owner == requester` against the rebuilt, power-transformed
  node, failing with `WrongPlayer(owner, requester)`. The recorded answer is
  `Answered(decisionId, answer, by = requester)`.
- **Parked `Roll`:** the requester must be `turn.activePlayer`. A `Roll` has no
  owner node; rolling for another player waits for a real case.

The owner is recomputed on every command and never stored. A power that
transforms a `Decide`'s owner therefore changes who may answer, who sees the
decision, and which continuation is issued together, which is the declarative
decisions rule that the transformed tree is the single source.

`Decide.owner` remains a concrete `PlayerId`, computed when the tree is built
or when a `Branch` selects the node. No owner query type is reintroduced.

**Continuations.** `parkedContinue` finds the awaited player from the same
parked node it already inspects (the `Decide`'s owner, or the active player for
a `Roll`) and passes it to `continuationFor`, so a prompt such as
`AwaitingOathkeeperRecipient(owner, decision)` names who must answer.

**Application gating** is unchanged: while a walker is pending, only walker
resume commands are accepted, from any authenticated player, and the engine
rejects every requester but the awaited one.

## Projection

When a walker is pending, `WalkerDecisionProjector` rebuilds the tree for every
viewer, spectators included, and locates the parked node and the awaited
player.

- **The awaited player** receives today's full owner-private projection. Card
  disclosure is evaluated with that player as viewer. Recover's roll feedback
  reads `turn.activePlayer` instead of `pending.actor`.
- **Every other viewer**, including an active player who is not the owner,
  receives a public `walkerWaiting: Option[WalkerWaitingProjection]` carrying
  the awaited player and the query's `heading` (`None` for a `Roll` park).
  This generic field is what later ports use instead of per-case `*Waiting`
  flags.
- **A rebuild or lookup failure** omits both projections, keeping the
  declarative decisions rule that a partially described decision is never
  exposed.

`LegalActionProjector` offers resume controls only to the awaited player.

`DecisionOption.Player` gains projection behaviour: `WalkerDecisionProjector`
resolves the player's presentation through `GamePresentationProjector`, and a
player id missing from state omits the whole decision.

## Phases

The resume path's `Phase.Act` gate is deleted and not replaced. While a walker
is pending, only resume commands are accepted, so nothing else can change the
phase; the procedure's own `build` passed its phase gates at start. A recorded
start phase would always match, and would wrongly reject a tree that changes
the phase and then parks.

`continuationIn` gives Act `ActActionSelection`, Wake `AwaitingWakeAction` and
Rest `AwaitingRestAction`, and keeps its typed rejection for every other phase.
Rest gained its continuation with Begin Rest
(`2026-09-13-rest-walker-and-phase-powers-design.md`). Finish Rest never
consults it: its turn boundary owns the continuation.

## Completion and the action boundary

When a walk finishes, `walkerTransition` applies `WalkerCompleted` and then
makes two independent decisions:

- **Continuation** is read off the phase the walk finished in, unchanged: Act
  gives `ActActionSelection`, Wake gives `AwaitingWakeAction`, Rest gives
  `AwaitingRestAction`, any other phase is a typed rejection.
- **Boundary** runs iff the completed reference is an `ActionRef`. This
  replaces batch 1's "runs after a procedure that started in Act", which the
  phase can no longer answer: Take Wealth (an action) and End Wake (a phase
  transition) both start in Wake. The rule is carried by the reference type,
  not by a registry flag and not by a special case.

The boundary is `completeAction`, with its steps unchanged and in order:
record the `ActionBoundary` ignored-rule diagnostics, refill bandits, then the
Oathkeeper step. It runs after every action in every phase:

- walker actions, through the rule above;
- legacy Act handles, which already call it and continue to;
- **Take Wealth, which now runs it.** This is a deliberate behaviour change:
  bandits refill and Oathkeeper is checked after a take during Wake.

Begin Rest and Finish Rest are phase transitions and do not run it. Finish Rest
runs the turn boundary instead. A REST power used through `UsePower` is an
action and does run it
(`2026-09-13-rest-walker-and-phase-powers-design.md`).

`completeAction`'s two `hasResult` checks and `appendEvaluation`'s
`UsurperVictory -> GameFinished` and `OathkeeperRecipientChoiceStarted` cases
are deleted. The guarantee they appeared to provide is `OathLifecycle`'s
`GameEnded` gate, which a test pins.

## Triggered procedures

The boundary's Oathkeeper step calls `OathkeeperRules.outcome(current)`. On
`NoChange` it returns the transition unchanged and journals nothing. Otherwise
it calls `startTriggered(transition, TriggeredProcedureRef.Oathkeeper)`.

`startTriggered` lives in `OathRulesWalker` beside `startWalker`, is
`private[gameplay]`, and has no client route. It:

1. requires that no walker and no legacy `PendingProcedure` is pending, and
   otherwise fails with `InvalidEventOrder`. Procedures are strictly
   sequential: a walk that just completed has already cleared its state.
   Legacy handles are *required* to reach the boundary with nothing pending,
   and the plan verifies it per call site rather than assuming it. Some
   `OathRules` call sites guard on `pending.isEmpty` (Conspiracy, Negotiation
   accept, Campaign sacrifice); the rest (Economy, Search complete, Challenge,
   minor actions, Vision reveal, Negotiation decline, Campaign place and raid
   relocation) rely on their own evolve having cleared it. Legacy `afterAction`
   overwrote `pending` unconditionally, so a violation there was already a
   silent bug; the guard turns it into a typed rejection. Nothing nests; a
   procedure that needs sub-steps changes its own operations mid-walk through
   `Branch` and `BuildOps`.
2. builds the tree through the registry with empty start selections, checks
   restrictions, and runs `ProcedureWalker.advance` with automatic powers only.
   It validates no modifiers and records no fallback diagnostics, which the
   boundary has already recorded.
3. hands the outcome to `walkerTransition`. A park yields the owner's
   continuation. A finish yields the phase's continuation, and runs no
   boundary because the reference is triggered.
4. appends the procedure's events to the incoming transition, so the action
   (legacy or walker) and the triggered procedure journal as one command.

The recursion is at most one level deep by construction: an action's
completion runs the boundary, which may start the Oathkeeper procedure, whose
completion runs no boundary.

A consequence while the migration is incomplete: a legacy Act handle whose
boundary finds an Oathkeeper tie ends its command with a `WalkerParked`, so a
legacy transition and walker events share one command until those actions
port.

This resolves walker decision 6's open question. State-based evaluation stays
engine-internal and decides *whether* anything happens; a triggered procedure
*performs* the change, because a change that may park a player decision needs
the walker's declaration, journal and replay.

## Oathkeeper

### Outcome

`gameplay/oathkeeper/OathkeeperRules.scala` holds the only definition of the
title outcome, as a pure function over `CurrentGameState`:

```scala
sealed trait OathkeeperOutcome
object OathkeeperOutcome {
  case object NoChange extends OathkeeperOutcome
  final case class Transfer(holder: Option[PlayerId]) extends OathkeeperOutcome
  final case class Choose(holder: PlayerId, candidates: Vector[PlayerId])
      extends OathkeeperOutcome
}
```

`qualifyingPlayers` moves in with it. The rows are today's `afterAction` match,
in the same order:

| Holder | Leaders | Outcome |
|---|---|---|
| holder is a leader | any | `NoChange` |
| holder is not a leader | two or more | `Choose(holder, leaders in seat order)` |
| holder or none | exactly one | `Transfer(Some(leader))` |
| holder is not a leader | none | `Transfer(None)` |
| none | none, or two or more | `NoChange` |

The side is not consulted: a Usurper who loses the lead loses the title like
any holder.

### Procedure

`gameplay/oathkeeper/OathkeeperProcedure.scala` declares the tree. `build` and
`rebuild` are the same function, because only resume commands are accepted
while it is parked, so the outcome cannot change underneath it. It rejects a
non-empty start selection, and declares no window: like End Wake, nothing may
transform it until a real power needs to.

```
NoChange          -> Left(InvalidEventOrder("no Oathkeeper change to perform"))
Transfer(holder)  -> Sequence(SetOathkeeper(holder))
Choose(holder, c) -> Sequence(
                       Decide("oathkeeper.recipient", owner = holder,
                         ChooseOne(c.map(DecisionOption.Player(_)),
                           heading = Some("Choose the Oathkeeper"))),
                       BuildOps(chosen player -> SetOathkeeper(Some(chosen))))
```

`Transfer` finishes inside the command that started it. `Choose` parks for the
holder, who may be any player. A single leader is a forced choice, so it omits
the `Decide` and applies the transfer itself, which is the declarative decisions
rule for forced queries. `Choose` always has at least two candidates, so its
query is never forced. An answer naming a player outside the rebuilt candidates
is rejected by the generic validator.

The registry entry for `TriggeredProcedureRef.Oathkeeper` has no
`fallbackKind`, `rollDecisionId` or `modifierWindow`, and its `continuationFor`
maps `"oathkeeper.recipient"` to `AwaitingOathkeeperRecipient(owner, decision)`.
The decision id is a constant, replacing the id legacy code minted from the
round and players.

### `SetOathkeeper`

A new generic primitive operation beside `EnterPhase` and `RecordPowerUse`:

```scala
final case class SetOathkeeper(holder: Option[PlayerId]) extends PrimitiveOperation
```

It sets `current.title` to `OathkeeperState(holder, TitleSide.Oathkeeper)`, so
the holder and the side always change together, as both legacy writers did.
`None` returns the title to the shared bank. It rejects a set whose holder
equals the current holder, whatever the side, with
`OperationError.OathkeeperUnchanged`: the outcome never produces one, and the
rejection gives replay the same no-op check `EnterPhase` gave batch 1.

It is the only writer of the title holder. Every title change is journalled as
this operation inside an Oathkeeper procedure step. Reading the title is
unchanged (`current.title.holder`, `.side`); no accessors are added.

### Deleted

- `PendingProcedure.OathkeeperRecipient`;
- `StateBasedEvaluation.afterAction`, `chooseRecipient`, and the evolve cases
  for `OathkeeperChanged`, `OathkeeperRecipientChoiceStarted` and
  `OathkeeperRecipientChosen`;
- those three events, their codec branches and wire types;
- `GameCommand.ChooseOathkeeperRecipient`, its intent, decoder, authorization
  helper, and the `OathRules` route;
- `OathkeeperRecipientProjection` and its `PendingProcedureProjector` and
  `LegalActionProjector` branches;
- the frontend block in `ActionDecisionRenderer` and its `package.scala` alias.

### Frontend

- `WalkerPanelSupport` renders `DecisionOption.Player` options as player
  buttons submitting a generic `ChooseOneAnswer`.
- `ServerModeUi` renders `walkerWaiting` as a "Waiting for {player}" banner,
  with the heading when present.

## Replay

A triggered procedure's `WalkerParked`, `WalkerStepRecorded` and
`WalkerCompleted` replay exactly like any walker events: recorded operations
are applied and no gate re-runs, including `OathkeeperRules.outcome`. Legacy
replay re-checked `afterAction` for every Oathkeeper event; that check moves to
`WalkerReplayDriftSuite`, which gains an Oathkeeper tie case. This is the
journal-trust rule of walker decision 5, applied without exception.

## Renames

`actor` becomes `activePlayer` in `PowerCtx`, `WalkCtx`, the registry's
`build`/`rebuild` parameters and every procedure's parameters. Stored copies
(`PendingTree`, the walker events) are deleted rather than renamed.
`Decide.owner` keeps its name. Legacy `PendingProcedure` cases keep `actor`,
since they are being deleted.

`PowerCtx.activePlayer` is the player whose procedure is running, which is
what every existing contribution reads it as (the traveller, the recoverer, the
taker). A contribution hooked on an off-turn `Decide` reads the owner from
`ctx.operation`. Only the active player selects powers, so no separate
activator field is needed.

## Failure handling

- Non-active requester starting a procedure: `WrongPlayer(activePlayer, requester)`.
- Requester other than a parked `Decide`'s owner: `WrongPlayer(owner, requester)`.
- Requester other than the active player on a `Roll` park: `WrongPlayer`.
- Triggered start while anything is pending: `InvalidEventOrder`.
- Triggered reference in a `StartWalker` payload, or unknown family tag:
  decode failure.
- Completion in a phase with no continuation: `InvalidEventOrder` (unchanged).
- Oathkeeper `build` against `NoChange`: `InvalidEventOrder`.
- `SetOathkeeper` to the current holder: `OperationError.OathkeeperUnchanged`.
- Projection rebuild failure, or a `Player` option naming a missing player:
  omit the decision and the waiting projection.

## Delivery order

Each commit keeps the full gate green.

1. **Codec headroom.** Move the operation encoders and decoders out of
   `WalkerEventCodec.scala` into `WalkerOperationCodec.scala`, as a pure move
   proven by no test file changing.
2. **Rename.** `actor` to `activePlayer` in `PowerCtx`, `WalkCtx`, registry and
   procedures. Mechanical, no behaviour change.
3. **Unstored active player.** `PendingTree(at, answered)`; events lose
   `actor`; `Answered.by`; `startWalker` checks the requester; `resolve`
   checks the requester against the owner. Behaviour is unchanged because
   every owner still equals the active player.
4. **Procedure families.** `ProcedureRef` hierarchy, `WalkerProcedureRegistry`,
   `walkerProcedure`, wire family tags, End Wake as a `PhaseTransitionRef`, the
   boundary decided by reference type.
5. **Off-turn ownership and any phase.** Delete the resume phase gate;
   authorize and continue by the awaited player; `walkerWaiting`; owner-only
   resume controls. Proven with synthetic registrations.
6. **Boundary after every action.** Take Wealth runs `completeAction`; delete
   the unreachable guards; `startTriggered` and its guard, proven with a
   synthetic triggered procedure.
7. **Oathkeeper.** `OathkeeperRules`, `SetOathkeeper`, `OathkeeperProcedure`,
   the registry entry, `Player` option projection, frontend, and every legacy
   deletion.
8. **Close-out.** Record what the work settled against its plan.

## Testing

**Walker.** A parked `Decide` accepts its owner and rejects anyone else,
including the active player, with `WrongPlayer`; `Answered.by` is journalled; a
`Roll` park requires the active player; a synthetic action with an off-turn
`Decide` parks and resumes in Wake; a synthetic completion in Rest is still a
typed rejection; a synthetic power that changes a `Decide`'s owner changes
authorization and projection together.

**References.** The registry covers `ProcedureRef.all`; decoding a
`StartWalker` naming a triggered reference fails; an unknown family tag fails.

**Boundary.** An `ActionRef` completion in Act and in Wake runs diagnostics,
refill and the Oathkeeper step; End Wake runs none; a triggered completion does
not refill again (exactly one `BanditsRefilled` per action); `startTriggered`
rejects while a walker or legacy procedure is pending; a command after the game
ends is rejected with `GameEnded`.

**Oathkeeper.** Every outcome row; `Transfer` finishes without parking;
`Choose` parks for the holder; a stale candidate is rejected; `SetOathkeeper`
resets a Usurper side and rejects an unchanged holder, for both `Some` and
`None`; the check runs after Take Wealth, and after a legacy Act action within
the same command; a reloaded game matches the live one; `WalkerReplayDriftSuite`
covers a tie.

**Projection.** The owner receives the full decision; the active player and a
spectator receive `walkerWaiting`; `Player` options carry presentation; a
missing player omits the decision.

**Codecs.** Round trips for each family tag, `Answered.by`, and
`SetOathkeeper(Some(_))` and `SetOathkeeper(None)`.

**Mutation proofs.** Each must fail at least one test: dropping the owner check;
running the boundary after a triggered completion; projecting the awaited player
as the active player; swapping `outcome`'s first two rows.

**Gates.** `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`,
`python3 scripts/check-architecture.py`, `git diff --check`.
`BackendArchitectureSuite` stays green without being weakened.

## Settled deviations

Two implementation details differ from earlier wording in this spec; both are
intentional and neither changes behaviour today:

- **`OathkeeperRules.outcome` takes `ReadyGame`, not `CurrentGameState`.** The
  Oathkeeper section above describes it as "a pure function over
  `CurrentGameState`"; the implementation instead takes the whole `ReadyGame`,
  since `qualifyingPlayers` needs `ready.game.campaign.oathkeeperGoal`
  alongside `current`.
- **`WalkerDecisionProjector.rollOutcome` receives the awaited player, not
  `turn.activePlayer`.** The Projection section above describes Recover's roll
  feedback as reading `turn.activePlayer`; the implementation instead passes
  the player `ProcedureWalker.awaitedPlayer` resolves for the parked position.
  The two are equal for every Roll registered today (a Roll park is always the
  active player's), so this is a shape change, not a behaviour change.

## Open items

- **Concurrent multi-owner decisions.** Closed by
  `2026-09-19-negotiation-walker-design.md`: a `Decide` may carry co-owners, any
  of whom may answer it, and the park reports every open decision and every
  awaited player (`ProcedureWalker.openDecisions`, `awaitedPlayers`). A
  simultaneous step, where each of several players answers their own decision
  and the step joins when all have, is designed for but not built; the
  Negotiation design records its semantics for the Lineage setup step that will
  need it.
- **Answer kinds.** Amounts (Campaign dice and sacrifice, Challenge), ordering
  (Search discards) and per-site allocations (Campaign placement) have no
  `DecisionQuery` shape. The first port that needs one widens the vocabulary.
  Amounts now have `DecisionQuery.Distribute`
  (`2026-09-13-rest-walker-and-phase-powers-design.md`); ordering and per-site
  allocations remain open.
- **Power selection by an off-turn owner.** Only the active player selects
  powers, at `StartWalker`. Whether Campaign's defender plans are powers the
  defender selects or options on the defender's `Decide` is settled at
  Campaign's port.
- **Unanswered off-turn decisions.** A parked decision blocks every command
  until its owner answers, as League Treaty and Oathkeeper recipients do.
  Timeouts, forfeits and administrative resolution are not designed.
- **Rest completion continuation.** Settled: Begin Rest completes to
  `AwaitingRestAction`, and Finish Rest's turn boundary to `AwaitingWakeAction`
  or `GameFinished` (`2026-09-13-rest-walker-and-phase-powers-design.md`).
- **Turn-scoped power activation.** Unchanged from the walker spec.
- **Per-action cleanup.** Minted decision ids (Conspiracy, Negotiation), the
  `bandit-validation` context in `CampaignRules`, Campaign's plan vocabulary,
  and `Campaign.scala` at 800 lines are each settled at that action's port.

## Corrections to other specs

Made in place on 2026-09-12, each pointing here and marked as not yet
implemented:

- `2026-09-05-procedure-walker-design.md`: locked decisions 6, 12 and 13; the
  State sketch; Walker step 7 and Phase gates; the Operation ADT's registry name
  and operation list; the `PowerCtx` signature and example; Journal / replay;
  Migration status; Out of scope.
- `2026-09-10-declarative-walker-decisions-design.md`: Resolution semantics
  step 2, Ownership, Failure handling and Non-goals.

`docs/superpowers/plans/2026-09-09-walker-batch-1-forge-travel-wake.md` is a
historical record and is not edited.
