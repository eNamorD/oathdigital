# The Parked Roll: Retired at the Edges, Kept in the Engine

> Status: decided and landed 2026-10-23, from the architecture scan of the
> edges (application, server, persistence, protocol, frontend, testkit),
> which raised this as candidate A and rated it strong. The same pass
> closed two leftovers that earlier records had named, and widened the
> frontend gate the scan found narrower than its document (candidate H).

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**form** and **roll feedback**.

## The evidence

A `Roll` node has two modes. `RollMode.Automatic` draws its faces from the
walker's dice source and keeps walking; `RollMode.Parked` stops the walk
until a command brings the faces. The undo design made every roll automatic
so a batch never parks mid-roll, and the code agrees:

- The only production code that builds a `Roll` to walk is `RollStep` in
  `gameplay/actions/dice`, and it builds `RollMode.Automatic`. `Parked` is
  the model default (`model/CoreOperations.scala`); the operation codec
  decodes a `Roll` with that default, but a decoded operation rides a
  replayed payload and is never walked. In production only
  `ProcedureWalker`'s park check names `RollMode.Parked`; test trees reach
  it through the default.
- `RecoverProcedure` states that its roll is automatic, so that a player is
  never asked to confirm a roll they have already paid for.
- The path that answered a parked roll — the `rollWalker` intent, the
  `RollWalker` command, its mapper arm, authorization builder and service
  arm, the `DefenseDicePort` it rolled through, the projector's roll branch,
  the `recover-walker-roll` phase label, the wire's `kind = "roll"` with
  its `pool` and `count`, and the client's `RecoverStep.Roll` with its
  "Roll the dice" button — was kept alive by fifteen test files and nothing
  else. Eleven test trees reach a parked roll through the model default.
- The journal carries no command. A roll is recorded as a
  `WalkerStepRecorded` with a `RollPayload`, whose `automatic` flag says
  whether the walk rolled it or a command did. No journal written by a
  production tree holds `automatic = false`.

## What was deleted

The application, wire and client side of the parked roll:

- `GameIntent.RollWalker`, its codec tag `rollWalker`, `GameCommand
  .RollWalker`, `AuthorizedPlayer.rollWalker`, the `GameIntentMapper` arm
  and the `GameApplicationService` arm.
- `DefenseDicePort`, and its threading through the service constructor,
  `Table.service`, `Situation.rules` and `SituationDriver.Rules`.
- `WalkerDecisionProjector`'s roll branch and `PendingProjector`'s
  `-walker-roll` phase: a park on a `Roll` now projects nothing, and its
  phase reads `-walker-waiting`.
- `WalkerDecisionProjection.kind`, `pool` and `count`: every walker decision
  on the wire is a `Decide` answered with `ResolveWalker`, so the
  discriminator carried one value.
- `ParkedDecision.RecoverStep.Roll`, the client's roll arm in
  `WalkerPanelSupport.renderRecoverPanel`, and the `kind == "decide"` guards
  in the route.
- The tests that exercised only that path, and the roll assertions of tests
  that asserted more.

The testkit driver, which answered a parked roll with the command, now fails
the test with the park's procedure, pool and awaited player: a tree that
parks on a roll is the test's own.

Two leftovers named by earlier records went with it. The 2026-09-24
walker-continuation removal named `ViewerPresentation.procedureStatus`,
defaulted to `None` with one reader that could never see a value; it is
gone. `PendingCardDecisionProjection`, which no longer exists, left the
architecture script's duplicate-DTO list.

## What was kept, and why

- **Roll feedback.** `WalkerRollOutcomeProjection` and the projector's
  `rollOutcome` show the faces rolled so far, their score and the target
  beside Recover's choice and Campaign's sacrifice. That is live and
  untouched.
- **`RollMode.Parked` and the engine's roll park.** The model keeps the
  mode and its default, and `ProcedureWalker` keeps `parkedRoll`, `roll`
  and the `RollResume` path, as does `OathRulesWalker.rollWalkerPrepared`.
  Nine suites — `ProcedureWalkerRollSuite`, `ProcedureWalkerWindowSuite`,
  `WalkerRollsSuite`, `LazyPruningSuite`, `HiddenOutcomesSuite`,
  `OathRulesWalkerPowerSuite`, `ParkedDecisionAssertionsSuite`,
  `WalkerOperationWireSuite` and `WalkerDecisionProjectorSuite` — build a
  parked `Roll` to test the walker's
  resume, window folding, lazy pruning and answerability rules, and
  `ProcedureWalkerRollSuite` answers it through `ProcedureWalker.roll`
  directly. Deleting the mode would rewrite those by hand for no production
  gain, and the engine's own scan owns that call. Whether the default flips
  to `Automatic` or the enum goes is left to it.
- **`WalkerProcedureRegistry.rollDecisionId` and `RecoverProcedure
  .rollDecisionId`.** `OathRulesWalker.checkAnswerable` still refuses a
  roll park under a procedure that declares no roll decision id, and
  Recover's `rollFeedback` names its pool by it. Both are engine-side and
  stay.
- **`WalkerStepRecorded(RollPayload(_, _, automatic = false))`.** The codec
  and `WalkerReplay` arms that read a parked roll's record stay, since they
  are the engine's. No production journal holds one; a journal that did
  would still decode, and would still replay through the engine's resume.
- **`SearchDrawPort`.** The scan suggested folding it away if the service's
  per-action arm was being edited. The Search arm was not: the roll arm
  beside it went, and another branch is editing `GameApplicationService`
  concurrently, so the Search arm stays as it is.
- **`TableSession.currentGameId`, `currentPlayerId` and
  `displayedProjection`.** The scan listed them as read only by
  `TableSessionSuite`. They are the `SessionControls` the development
  toolbar (`DevelopmentRenderer.controls`) reads, so they are live and stay.

## Consequence

A parked decision's form is now always one of the forms a `Decide` asks;
CONTEXT.md's **form** entry dropped its "or none, when it asks nothing (a
roll)" clause the same day. A procedure that wants a player-triggered roll
would reintroduce the command, the port, the projection kind and the client
step together; none is planned in `docs/ROADMAP.md`.

The engine half is deliberately left in an asymmetric state, and it is
worth saying plainly: `OathRulesWalker.rollWalkerPrepared` now has test
callers only, and `checkAnswerable` still accepts a `Roll` park for a
procedure that declares a `rollDecisionId`, although the projector projects
nothing for it and no intent can answer it. If a parked `Roll` were ever
walked in production the game would stop with no way forward. It cannot be
today, because nothing in production builds one; the guard is that fact,
not a check. The half stays because nine engine suites build a parked `Roll`
to test resume, window folding, lazy pruning and answerability, and
`ProcedureWalkerRollSuite` answers it through `ProcedureWalker.roll`
directly; retiring the mode is a hand rewrite of those suites for no
production gain. Whoever adds a player-triggered roll, or decides to flip
the model default to `Automatic`, takes this paragraph with them.
