# Walker Continuation Removal

> Status: design approved in conversation on 2026-09-24, from the same
> architecture review that produced
> [the operation family consolidation](2026-09-24-operation-family-consolidation-design.md),
> which lists this work under "Left alone, deliberately". This is a
> behavior-preserving change on the production side and a deliberate
> strengthening on the test side; the two are separated below. The sibling
> deletions (`OperationShadow`, `ViewerPresentation.procedureStatus`,
> `PeekedRelicPresentation`) and the Act-action control merge are not part of
> this design. `DeltaMeaning` is retained; see
> [the delta meaning retention decision](2026-09-24-delta-meaning-retention-decision.md).
>
> Revised 2026-09-24 while writing
> [the implementation plan](../plans/2026-09-24-walker-continuation-removal.md),
> after measuring the change rather than sampling it. Three claims were wrong
> and are corrected below: the family has seventeen cases, not eighteen; the
> registry key-set invariant already exists and does not need writing; and the
> test surface is 218 sites across 53 files, not 104 across 43, because a
> majority reach the continuation through a fixture helper without naming the
> type.
>
> Landed 2026-09-25 by
> [the implementation plan](../plans/2026-09-24-walker-continuation-removal.md),
> commits `f5c1508c..da150149` (thirteen commits; see **Delivery** for the
> actual shape, which is not the eight this document originally estimated).

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**form** and **surface**.

## Purpose

`OathContinue` (`model/GameProcedureProtocol.scala:5-51`) is a seventeen-case
family describing what a game is waiting for — fifteen `Awaiting*`/selection
cases and `GameFinished`. It is carried on `OathTransition.continue` (`:53-57`),
threaded through `GameplayTransition` (`OathLifecycle.scala:40-51`), and
surfaced on `GameAccepted.continue` and `PreparedGameBootstrap.continue`
(`application/GameApplicationService.scala:19,31`).

Nothing reads it. There is no `case OathContinue.` anywhere in `src/main`,
`frontend/src/main` or `shared/src/main`; the only four production reads of the
field are `case _ => current.continue` in `OathRules.scala:184` and
`phases/rest/TurnBoundary.scala:23`, which discriminate nothing, and the two
copies into a DTO at `GameApplicationService.scala:106,306`, which no route,
codec or projection then reads. It is not on the wire and not in the journal.

The fact it describes has two owners already. `CoreOperations.scala:574-582`
defines `Decide(decisionId, owner, query, …, coOwners)` with `def owners`, and
`ProcedureWalker.awaitedPlayer` (`:240-243`) computes the awaited player on
demand — its own documentation says the value is "never stored" because a power
that changes an owner changes the answer on the next command, and that
"authorization, projection and continuation all read it". `OathContinue` is the
third copy, and the only one with no reader.

The cost is real and recurring. A new procedure needs a case in
`GameProcedureProtocol`, a `continuationFor` lambda in the registry, and a
prompt that no client consumes. Eighteen such lambdas exist — seventeen in
`entries` plus `usePowerEntry`'s own
(`WalkerProcedureRegistry.scala:155-457`), four of them `(_, _, _) => None`.

## Ownership and interface

`PendingProjector` and `WalkerDecisionProjector` already own what a client is
told about a parked decision, per viewer and with redaction. They keep that
ownership unchanged; this design adds nothing to them, and adds nothing to
production at all.

The following cease to exist:

- `OathContinue`, all seventeen cases, including `ActActionSelection` and
  `GameFinished`. Both are construct-only as well
  (`OathRulesWalker.scala:499`, `actions/MinorActions.scala:178`,
  `OathRules.scala:182-183`, `TurnBoundary.scala:22`), and both facts are
  already projected as the phase strings `act-action-selection` and
  `game-over`; the winner is on `ready.game.current.result.get.winner`.
- The `continue` field on `OathTransition`, on `GameplayTransition`, on
  `GameAccepted` and on `PreparedGameBootstrap`. `OathTransition` becomes
  state and events.
- `Entry.continuationFor` (`WalkerProcedureRegistry.scala:125`) and its
  eighteen literals; `WalkerProcedureRegistry.continuationFor`;
  `OathRulesWalker.parkedContinue` and `continuationIn` (`:497`, `:533`).

`Entry` keeps its remaining fields. Narrowing `Entry` further, and removing the
test-only `registrations` parameter from its accessors, is a separate change.

### The gates inside `parkedContinue` and `continuationIn`

`parkedContinue` carries three rejections, and they are not equivalent.

The first is the registration gate: `continuationFor` returning `None` becomes
`InvalidEventOrder("no client continuation is registered for walker decision
…")` at `OathRulesWalker.scala:533-570`. That is a registration mistake reported
as an event-order violation at command time, and it goes with the type. Nothing
replaces it, because the invariant it approximates is already asserted:
`WalkerProcedureRegistrySuite.scala:50-51` — "the production entries register
every procedure reference" — asserts `WalkerProcedureRegistry.entries.keySet ==
ProcedureRef.all.toSet`, and `Entry` is a case class, so a registered procedure
cannot omit a field. `ActionRef.UsePower` is parameterized and therefore
correctly absent from `ProcedureRef.all` (`model/ProcedureRef.scala:97-124`);
it is resolved by `lookup` (`WalkerProcedureRegistry.scala:461-467`) and
admitted by `isRegistered` (`:547-548`). A procedure that is reachable but
unregistered already fails the test gate rather than a running game.

The other two are kept, verbatim, because they guard a fact that outlives the
type:

- `WalkerProcedureRegistry.rollDecisionId(procedure)` returning a `Left` for a
  Roll park under a procedure declaring no roll decision id. `rollDecisionId`
  survives — `WalkerDecisionProjector.scala:125` reads it — and the park is
  unanswerable without it. `OathRulesWalkerPowerSuite.scala:507-530` pins that
  the rejection happens at command time, appending no `WalkerParked`, and says
  why: a client must never be handed a park whose decision id no tree declares.
  Losing it would move that rejection to projection time, after the park is
  durable.
- `InvalidEventOrder("parked walker position is neither a Roll nor a Decide")`.

Both survive as a small `checkAnswerable` in place of `parkedContinue`, keeping
the same codes and detail strings.

`continuationIn`, the other half of what this design deletes, carries a
rejection of its own, and it is lost too — the registration gate above is not
the only one. A walker procedure completing in a phase with no walker
continuation registered failed with `InvalidEventOrder("a walker procedure
completed in the <phase> phase, which has no walker continuation")`, pinned
by a test in `OathRulesWalkerPowerSuite`. It is unreachable in production.
`turnBoundary` (`OathRules.scala:124-133`) is the only code that puts the turn
in `Phase.RoundEnd`; it runs only after `FinishRest`
(`runsTurnBoundary`, `OathRulesWalker.scala:461-462`) and proceeds straight to
`enterWake` inside the same command, so `RoundEnd` is transient within one
command's fold and no procedure other than `FinishRest` can ever complete
there. The deleted test could only observe the rejection by setting the phase
directly and injecting a flat tree — a state no command produces. Like the
registration gate, this guard protected the correctness of a continuation;
with none left there is no wrong answer left to guard against. Its test goes
with it, which is a real loss, not a conversion: the suite total drops from
1701 to 1700.

### The test surface

218 continuation sites span 53 test files. 136 name `OathContinue` directly;
the other 82 reach it through a fixture helper — `PlanDriver.awaits`,
`CampaignPlanWindowSuite.awaits`, `TargetsFixture.awaits`,
`MovementFixture.parkedAt`/`backToActing`, `BannerFixture.backToActing` — or
read `transition.continue.toString` as an assertion's clue string. Eight suites
in the campaign family assert only through `PlanDriver.awaits` and never
mention the type. Seven shared fixtures carry the helpers
(`application/ParkedServiceFixture.scala`,
`application/ForgeWalkerFixture.scala`,
`gameplay/powers/campaign/PlanDriver.scala`,
`gameplay/powers/TargetsFixture.scala`,
`gameplay/powers/action/MovementFixture.scala`,
`gameplay/powers/banner/BannerFixture.scala`,
`gameplay/CampaignPlanWindowSuite.scala`), so their suites move with them.

A shared assertion module, `ParkedDecisionAssertions`, replaces them. It reads
only values that already exist:

```scala
final case class ParkedDecisionFacts(procedure: ProcedureRef, decision: String,
    awaiting: PlayerId, coOwners: Set[PlayerId])

final class ParkedDecisionAssertions(catalog: ExecutableCatalog,
    walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    phasePowerCatalog: PhasePowers = PhasePowers.empty):

  def parkedDecision(state: OathState): Option[ParkedDecisionFacts]

  def assertParked(state: OathState, procedure: ProcedureRef,
      decision: String, awaiting: PlayerId)(using munit.Location): Unit

  def assertNotParked(state: OathState)(using munit.Location): Unit

  def assertResumed(state: OathState, phase: Phase, active: PlayerId)(
      using munit.Location): Unit
```

It is a class, not an object, because the facts are only derivable against the
catalog and the power catalogs the suite's own `OathRules` was built with; each
suite constructs one beside its rules. It takes an `OathState`, so the
application suites can pass `GameAccepted.state` as readily as the gameplay
suites pass `OathTransition.state`.

Its inputs are `walkerProcedure`, `walkerPending`, `walkerModifiers` and
`walkerStartArgs` (`model/GameState.scala:139-155`),
`ProcedureWalker.parkedDecide` (`:229`), `parkedRoll`, and `awaitedPlayer`
(`:240`) and `awaitedPlayers` (`:249`) for the owner and co-owners. It rebuilds
the tree through `WalkerProcedureRegistry.rebuild` (`:439`), which is what
`OathRules.declaredWalkerTree` (`OathRules.scala:232-237`) does; the five sites
that hand-roll that rebuild today (`CampaignProcedureSuite.scala:33-38`,
`PlanDriver.scala:38`, `CampaignPlanWindowSuite.scala:92`,
`TargetsFixture.scala:73`, `MovementFixture.scala:82`) call it instead.

A Roll park has no `Decide` and therefore no decision id of its own, so
`parkedDecision` resolves one the way `parkedContinue` does: `parkedRoll`
first, and then `WalkerProcedureRegistry.rollDecisionId(procedure)`. Mirroring
production here is deliberate — a helper that reported a Roll park as "not
parked" would silently weaken every Recover assertion.

`awaiting` is required at every call site. The invariant `CONTEXT.md` states —
one parked decision, awaiting one player — has its exclusion half tested well
(`application/PendingWalkerInvariantSuite.scala:51-66` refuses every command
from every player over a park) and its identity half not tested at all. A
default would preserve that gap.

`assertResumed` exists because three cases carry a fact `assertNotParked` does
not: `ActActionSelection`, `AwaitingWakeAction` and `AwaitingRestAction` each
assert a phase and an active player as well as the absence of a park. Asserting
"nothing is parked, and the turn is at phase P with active player A" keeps that
fact and is a game invariant in its own right.

The 136 direct sites divide three ways:

- **28 are deleted.** Nineteen assert a fact an adjacent line already asserts;
  `application/GameApplicationServiceSuite.scala:165-168` is one of them, but
  not for the reason first thought: its neighbours name only `walkerProcedure`
  and `walkerPending` — that some Recover walk is pending, not which decision
  or whose. What actually covers the deleted claim is the unchanged
  `ResolveWalker(actor, TreeDecision(relicDecisionId, …)).toOption.get`
  further down the same test, which throws if either the decision id or the
  owner is wrong. Nine restate the implementation: four sites in
  `WalkerProcedureRegistrySuite.scala` (`:113`, `:133`, `:143`, `:151`) assert
  the registry's own lambdas and die with them;
  `BackendArchitectureSuite.scala:20-21` passes one as a constructor argument
  to a test about event folding; `RestWalkerSuite.scala:32` names a player
  read off `turn.activePlayer` on the previous line.

  Three sites first classified as restatements turned out to assert something
  their neighbour did not, and are converted instead of deleted:
  `GameApplicationServiceSuite.scala:1074` (the neighbour names the phase, not
  that the active player is still `active` after a completed Wake action — a
  real claim, "a completed Wake action does not hand the turn over," that
  nothing else in the test makes), `TakeWealthProcedureSuite.scala:150`
  (same shape, the phase without the player), and
  `gameplay/powers/economy/KnightsErrantSuite.scala:103-105` (the kept
  neighbour re-proves the decision id and is silent on the awaited player).
  In each case the neighbour carried only part of the deleted claim, usually
  the phase or the decision id, never the awaited player. The classification
  that produced 31 was made from adjacent-line greps rather than claim by
  claim: a neighbour asserting something similar is not the same test as a
  neighbour asserting everything the deleted line asserted.
- **95 become `assertParked`, `assertNotParked` or `assertResumed`.**
- **13 need a written substitute.** Nine are not assertions: they destructure
  the continuation to obtain a decision id for the next command
  (`PendingWalkerInvariantSuite.scala:96-99`, `PlanDriver.scala:85-90`,
  `ForgeWalkerFixture.scala:103,115`,
  `gameplay/powers/economy/KnightsErrantSuite.scala:61-67`,
  `NegotiationProcedureSuite.scala:223`,
  `GameApplicationServiceSuite.scala:77`); they read
  `parkedDecision(state).get.decision`. Eight assert an owner who is not the
  acting player, which is the rule under test —
  `gameplay/powers/rest/LeagueTreatySuite.scala:41,79` (an off-turn player owns
  a decision parked inside Begin Rest),
  `oathkeeper/OathkeeperProcedureSuite.scala:60,116`,
  `OathRulesWalkerPowerSuite.scala:543` (a power moved ownership away from the
  actor), `ParkedServiceFixture.scala:142,218`,
  `gameplay/setup/GameStartToWakeSuite.scala:18`. Four sites fall in both
  groups.

The 82 indirect sites convert with the helper that carries them, and nine of
them are those helper definitions. Two are `GameFinished` checks
(`RestWalkerSuite.scala:79`, `RestSuite.scala:345`) that assert only the case
and not the winner; they become an assertion on
`ready.game.current.result`, which is where the winner actually lives.

## Scope and preservation

Production behavior is preserved exactly for every command a client can send:
no operation order, no projected control, no walker event, no wire shape, no
protocol DTO, no frontend edit. Two rejections do disappear along with the
type — the registration gate and `continuationIn`'s own `RoundEnd` gate, both
under "The gates inside `parkedContinue` and `continuationIn`" below — but
both are proven unreachable from any state a command can produce, so no
legal or illegal input sees a different result; "preserved exactly" is about
outcomes, not about which dead code still compiles. The change is in
`model`, `gameplay` and `application` only; `frontend` and `shared` hold no
reference to `OathContinue` and are untouched.

Test behavior is deliberately strengthened in one place. Twelve sites assert
the negative — eleven as
`!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision]`
(`gameplay/powers/wake/HornedMaskSuite.scala:138,148,157,163,172` and similar
in `LeagueTreatySuite`, `SilverTongueSuite`, `SleightOfHandSuite`,
`AlchemistSuite`, `IvoryEyeSuite`, `CrystalVialSuite`), and one as
`SearchProcedureSuite`'s `!…isInstanceOf[AwaitingSearchDecision]`. These pass
when the walk parked on a different continuation family, so they are weaker
than the claim they are making. `assertNotParked` asserts that nothing is
parked at all, which is strictly stronger. All twelve were converted and all
twelve passed under the stronger assertion: the strengthening found no
defect. That is a result worth recording, not a non-event — the claim that
these negatives were weaker than they looked turned out to be true of their
wording and false of the code they were checking.

Out of scope: narrowing `Entry`, removing the test-only `registrations`
parameter, `LegalActionProjector`'s parallel per-procedure switchboard, and any
rule defect found while rewriting.

## Verification

Bracket the change with the existing suites. Before starting, run the full
Scala and frontend suites, `python3 scripts/check-architecture.py` and the
Markdown link check, and record the baseline.

- `WalkerProcedureRegistrySuite` keeps its key-set invariant unchanged and
  loses the four `continuationFor` assertions.
- `PendingWalkerInvariantSuite` and `PendingWalkerRulesSuite` keep their
  exclusion assertions unchanged; their two extractions move to
  `parkedDecision`.
- `OathRulesWalkerPowerSuite`'s Roll-park rejection test keeps its exact
  `Left`, which is what proves `checkAnswerable` kept the gate.
- `WalkerDecisionProjectorSuite` and the application projection suites are
  untouched, and are the evidence that the client-facing prompt is unaffected.
- Every suite that loses a continuation assertion keeps at least one assertion
  about the same park, through `assertParked`/`assertNotParked`/
  `assertResumed` or an existing neighbouring line. No suite ends with fewer
  facts pinned than it started with, except the 28 deletions, each of which is
  justified by a named duplicate or restatement above, and the one
  `RoundEnd`-completion rejection test, which guarded a fact that no longer
  exists.
- Confirm by grep that `OathContinue` appears nowhere in `src`, `frontend` or
  `shared` at the end.

## Delivery

Eight tasks: the assertion module; then the test conversion in four suite
families, each of which compiles and passes on its own; then the registry
suite and the architecture suite; then the deletion of the type and its
carriers; then the documentation and the full gate. This document estimated
eight commits, one per task; thirteen landed. Two precede Task 1 — the design
and plan, and a pre-flight fix to the plan — and were never counted against a
task. Four tasks (1, 4, 5 and 7) needed a fix-round commit beyond their own,
each restoring a claim a reviewer found the task's own commit had dropped;
those four fix rounds are the rest of the gap between eight and thirteen.
