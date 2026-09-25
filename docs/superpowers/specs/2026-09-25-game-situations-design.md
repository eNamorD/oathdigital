# Game Situations

Status: design approved in conversation on 2026-09-25. This is a behaviour-preserving test-architecture slice; it changes no production code except one contained extraction in the test tree's dependency, `ParkedDecisionAssertions`, which is itself a test module.

## Purpose

There is no seam for "a game at a known point of play". Every test that needs one replays Setup by hand: `FirstGameSetupFixture.execute`, `ParkedServiceFixture.setUp`, `ForgeWalkerFixture.forgeReadyGame` and `GameStartToWakeSuite` each carry their own copy of the Setup walk, with the turn order `p2, p3, p1` hard-coded four times and the pawn and adviser answers assembled by hand in eleven files. About thirty call sites replay all of Setup through `GameApplicationService.handle`. When one Setup decision changed its query shape (`1a6a596`), one production file and eight test files moved; when the shared fixture changed (`c8057fb`), sixty-four files moved.

The one generic piece that exists, `SetupWalkDriver.driveToCompletion`, answers any park with a default answer, but only at the tree level and only for four suites.

This design gives the concept a module. `CONTEXT.md` names it: a **Situation** is a game reached by real play from a **first-game input**, every command issued and every parked decision answered along the way, in order.

## Ownership and interface

A new test-only package, `oathdigital.testkit`, owns `Situation`. It serves gameplay, application and server suites, which share one `src/test` tree.

`Situation` is one immutable value:

- `ready: ReadyGame`, `events: Vector[OathEvent]`, `nextSequence: Long`, and the adapter handle that reached it, so a test can keep driving from where the situation left off.

Two ways to reach one:

- `Situation.after(steps, answers)` issues each step and answers every parked decision it causes, including those after the last step. The result is not parked.
- `Situation.parkedAfter(steps, answers)` does the same for every step but the last, and leaves the final park in place. The result is parked, and `ParkedDecisionAssertions` reads it.

Three named situations, thin over the driver: `wake` (Setup complete, `after(Begin)`), `act(actor)` (that player's Wake ended) and `rest(actor)` (that player's Act ended, stopped at the Rest action). Anything else is described by its steps. The existing park recipes (`leagueTreatyPark`, `recoverChoicePark`, `oathkeeperTiePark`, `silverTonguePark`, `forgeReadyGame`, `parkedForge`) stay in the fixtures that own them, re-expressed over the driver; they are not named situations.

`situation.seedInto(repository)` writes the situation's events into an `InMemoryEventStreamRepository` through `GameEventWire.encodeEvent`, the way `AuthenticatedGameRoutesSuite` and `TrustedSeatRoutesSuite` do by hand today.

A step is one of:

- a `GameCommand`, the vocabulary the codebase already has; or
- `Arrange(ops: Vector[CoreOperation])`, which places pieces by recording one arranging `WalkerStepRecorded` and applying it through the replay path. It is `ParkedServiceFixture.seed` given a name and a second adapter.

Answers are a partial function from the parked `Decide` (decision id, owner, query) to a `DecisionAnswer`. The default answers `ChooseOne` with its first option, `Partition` by keeping the first option in the first section that requires one and discarding the rest (today's `SetupWalkDriver.defaultAnswer`), `ChooseMany` with its first `min` options, and `ChooseAmount` with `min`. A test overrides by decision id, using the ids the procedures already export (`SetupProcedure.pawnDecisionId(player)`, `RecoverProcedure.choiceDecisionId`), so no new string enters the tree. A Roll park is rolled by the driver with the adapter's dice; the test never names a pool key.

Failure is loud: a rejected step, or a park neither the default nor the override can answer, fails the test with the step index, the command, the decision id, the form and the awaited player.

## Two adapters, one seam

The driver's interface is the same for both adapters. What varies is how a step becomes a transition.

- **Rules adapter.** Takes an `OathRules` and a `DefenseDicePort`. Interprets `GameCommand` by calling the rules directly, the same dispatch `GameApplicationService.applyCommand` performs (`beginGame`, `startWalker`, `resolveWalker`, `rollWalkerPrepared`, `handle(MinorActionCommand)`); Search needs no draw port at this level because the port is only checked for equality against `SearchRules.draw`. Interprets `Arrange` through `WalkerReplay.applyRecorded`. Returns state and events; nothing is journaled.
- **Journaled adapter.** Takes a `GameApplicationService`, a repository and a game id. Interprets `GameCommand` through `service.handle`, and `Arrange` by appending the arranging record to the repository and reloading. Returns the accepted position.

`Situation.defaultRules(catalog, campaignDice)` builds an `OathRules` the way the service does (`WalkerPowerCatalog.default`, `PhasePowerCatalog.default`, `CampaignDicePort.walkerDice`), so the two adapters agree given the same input. The module's own suite pins that agreement. Dependencies are accepted, never created: catalog, rules or service, dice, repository and game id are parameters, with a `default` companion for the common case.

`FirstGameSetupFixture` keeps its role as the first-game input: catalog, participants, Chronicle and orders. `initialReady` and `execute()` stay as delegates over `Situation.wake` driven by `new OathRules(catalog)`, exactly the rules they use today, so the 39 files reading `initialReady` and the 29 calling `execute()` see the same state and events and do not move. Deleting the delegates is a follow-up once callers ask for a situation directly.

Both adapters, and `ParkedDecisionAssertions`, need "the parked node of this state". That rebuild — `WalkerProcedureRegistry.rebuild`, then `ProcedureWalker.parkedDecide`, `parkedRoll` and `awaitedPlayer` — is extracted from `ParkedDecisionAssertions.parkedDecision` into one function in the testkit, `ParkedNode.of(state, catalog, walkerPowers, phasePowers)`, returning the `Decide` or the `Roll` pool and count with the awaited player. `ParkedDecisionAssertions` consumes it; its own suite is the bracket.

## Preservation boundary

No production behaviour changes: no rule, operation order, walker event, wire shape or protocol DTO. Migrated tests keep their exact assertions, including event-count assertions such as `nextSequence == 21L`; strengthening them is a separate change. `FirstGameSetupFixture.initialReady` is byte-identical before and after. Route suites keep driving Setup over HTTP where the route is what they test; none of them needs a situation to reach a later point of play.

Out of scope, recorded so they are not re-derived: a general arrangement vocabulary (`pawnAt`, `warbandsAt`, `cardAtSite`) for the 116 files that hand-edit `ReadyGame` — its shape should be decided from all 116, not from the four builders this slice needs; the park recipes as named situations; the on-disk `ServerRuntime` test in `TrustedSeatRoutesSuite`, which cannot take an in-memory repository.

## Verification

`SituationSuite` pins the module: `wake` under `new OathRules(catalog)` equals today's `initialReady`; the rules adapter and the journaled adapter reach an equal `ready` for the same steps under equivalent rules; an unanswerable park fails with the decision id in the message; `parkedAfter` leaves the last park and `ParkedDecisionAssertions` reads it.

Each migration slice is bracketed by the suites it touches and by the full JVM suite, because the fixture's blast radius is the whole gameplay tree. The complete gate is `./sbtw test`, `./sbtw frontend/test`, `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`. After the last slice, `grep` confirms no test file outside the testkit spells the Setup turn order or assembles a `PartitionAnswer` from `SetupProcedure.adviserKeepKey`.
