# Game Log, Slice 1 (Headlines and Action Lines) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dead Log pane with a live, player-scoped game log: round, turn and victory headlines; a start line (with modifiers and a Supply cost span) for every action that can take modifiers; one action line per action; served per seat by the trusted and development routes and appended by the table.

**Architecture:** `EventReplayEngine.scan` exposes the state before and after every journal event; `GameApplicationService.history` returns the scanned journal. A pure formatter in `oathdigital.application.gamelog` walks it, steps each recorded operation batch one operation at a time through the walker's own replay application, and posts each line at the event where its facts are complete, reading ahead only to the end of the event's own segment. `GameProjector.logPage` maps entries to shared wire types; both seat routes serve a page after a journal sequence. The client fetches the log whenever a projection's `nextSequence` passes the log's, and a new `GameLogPane` appends entries and sticks to the bottom.

**Tech Stack:** Scala 3.9.0, Scala.js, Akka HTTP 10.5.3, ujson with hand-written `exact` codecs, munit (jsdom for the frontend). Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-25-game-log-design.md` (amended and approved at `e050d4c4`). Read its Decisions, Posting, Headlines, Start lines, Action lines, Visibility, Wire contract, Routes, Client and Testing sections before starting. Slice 2 (knowledge, detail lines, setup lines, negotiation settlement, the rest of Campaign, goldens) and Slice 3 (overlay, divider, New chip, sticky headline) are separate plans.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`. `gameplay` must not import `application` or `protocol`.
- Never touch the live database `var/oathdigital`. The smoke read in Task 8 works from a copy in scratch space only.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Voice: past-tense verbs, subject omitted for the actor's own action. The formatter never emits an operation's or event's class name.
- Card backs are public: a card the viewer may not identify reads "a Denizen", "a Vision" or "a Relic", and is always a `text` span. No wire field ever carries a hidden card's id.
- Entry order equals `(sequence, ordinal)` order, and nothing already posted ever changes: the client only appends.
- Record the baseline test counts from the first full run and use them as expected counts later. Do not invent counts.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Facts this plan relies on (verified against the code on 2026-09-26)

- **Recording shape.** A composite operation placed directly in a procedure tree (for example Muster's `PayCost`, `Gain.Warbands`, Take Wealth's `Take`) is decomposed by the walker to its primitive `Move` leaves, each recorded in its own `WalkerStepRecorded`. Only a `BuildOps` node records a batch, and it records the composite values it returned unchanged: Search's `Draw`, Forge's `PayCost`s plus `Play`, Card Play's planned operations, Negotiation's settlement `Peek`s and `Give`s, Recover's relic `Move`, Finish Rest's `GainSupply` and `BeginTurn`. `SpendSupply` is always alone in its step.
- **Travel:** `SpendSupply`, then `Move(Piece.Pawn(actor), Site(from) -> Site(to))`. Never parks.
- **Search:** `SpendSupply`; `Draw(actor, cards, source, Hand(actor))` (plus `AdvanceVisionsDrawn`) in one step; the `search.cards` Partition (sections `keep`/`discard`) only when two or more cards were drawn; then Card Play placement.
- **Play Facedown Adviser:** first node is the `cardplay.place.{kind}.{id}` ChooseOne (Buttons `discard`, `site`, `adviser-faceup`, `adviser-facedown`), an optional `cardplay.replace.*` ChooseOne, then one `BuildOps` batch applying the placement.
- **Muster / Trade:** the `muster.source` / `trade.source` ChooseOne (Denizen or Edifice refs) parks first; then `Move(Favor(1) | Secrets(1), PlayArea(actor) -> OnCard(source))` (plus a burn `Move(Favor(1), PlayArea(actor) -> SharedBank)` for trade-for-secrets), `SpendSupply(actor, 1)`, then the gain: `Move(Warbands(force, n), WarbandBank -> PlayArea(actor))`, `Move(Favor(n), FavorBank -> PlayArea(actor))` or `Move(Secrets(n), SharedBank -> PlayArea(actor))` (absent when trade-for-secrets gains nothing).
- **Take Wealth:** `Move(Favor(1) | Secrets(1), Site(site) -> PlayArea(actor))`, then `RecordPowerUse`. Never parks.
- **Recover:** `ModifyDicePool`, then per pass `SpendSupply(actor, 1)`, an automatic `RollPayload`, and the `recover.choice` ChooseOne (Buttons `continue`/`stop`) only after a failed roll; on success the `recover.relic` ChooseOne and `Move(Card(relic), Site(site) -> PlayArea(actor), Some(FaceDown))`. A failed Recover ends on the `stop` answer with no relic move.
- **Forge:** `SpendSupply(actor, 1)`; `forge.assignment` Partition only on a mixed-cost site; then one batch of `PayCost(actor, OnCard(denizen), Cost(favor = 1) | Cost(secret = 1))`s plus `Play(relic, Deck(Relic) top, PlayArea(actor), FaceDown)`.
- **Challenge:** `SpendSupply`; `challenge.banner`; `challenge.amount`; ribbon steps; `Move(Favor(n) | Secrets(n), PlayArea(actor) -> OnBanner(banner))`; `Move(Banner(banner), SharedBank | PlayArea(holder) -> PlayArea(actor))`.
- **Place Banner Resource:** `place-banner-resource.banner`, `place-banner-resource.amount`, `Move(Favor(n) | Secrets(n), PlayArea(actor) -> OnBanner(banner))`. No Supply.
- **Campaign:** `SpendSupply(actor, 2)`; `campaign.kind` asked only when both kinds are legal; `campaign.defender` only for a Raid with two or more co-located enemy pawns; targets; the always-asked `campaign.force`; pools, plans, rolls; a step recording `RecordCampaignResult(result)`; outcome. `CampaignSetup.kindOf` and `defenderOf` (public, in `gameplay.actions.campaign`) give the kind and defender from the answers so far plus live state, exactly as the procedure reads them.
- **Negotiation:** `negotiation.negotiators` ChooseMany only with two or more eligible players (`NegotiationDeal.eligible(state, actor)` otherwise); every proposal, acceptance and `DeclineDeal` answers the one `negotiation.deal` decision; an agreed deal records one settlement batch of `Peek`s and `Give`s (no step when it settles nothing).
- **Use Power:** payment `Move`s of favor or secrets from the actor's area onto the source card or to the shared bank (only when the power has a cost), the power's own subtree, then `RecordPowerUse(PowerUseRef(timing, source, id))` for Wake and Rest powers only. A parked run's `WalkerParked.startArgs` holds the single source ref.
- **Oathkeeper:** optional `oathkeeper.recipient` ChooseOne, then `SetOathkeeper(holder)`.
- **Finish Rest:** cleanup batch; `GainSupply(resting, n)` (or `SpendSupply`, or nothing); `BeginTurn(next, Wake)` or `BeginTurn(first, RoundEnd)` for the last player, after which `gameplay.round-ended` moves that player into Wake. Setup's last step is `BeginTurn(first, Wake)` from `Phase.Setup`.
- **Arrange steps** (`testkit/Step.Arrange`) are journaled as a bare `WalkerStepRecorded` with no closing event, so the formatter treats them as part of the next run. Scripts place every Arrange immediately before a command whose run posts nothing about the arranged pieces (End Wake, or a Take Wealth whose only line is its `Take`).

---

## File Structure

**Server, new:**

- `src/main/scala/oathdigital/application/gamelog/LogEntry.scala` — `LogEntry`, `LogKind`, `LogSpan`, and the internal `Posted`.
- `src/main/scala/oathdigital/application/gamelog/LogJournal.scala` — the scanned journal with per-operation states, segment closings, and the `Run` and `OpStep` records.
- `src/main/scala/oathdigital/application/gamelog/LogWords.scala` — names and the visibility rule (`CardWord`, card lists counted by kind, joining).
- `src/main/scala/oathdigital/application/gamelog/StartLines.scala` — start lines, their anchors, modifiers and cost; "Continued Recover".
- `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` — one action line per `ProcedureRef`.
- `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` — the exhaustive `OathEvent` match, headlines, run tracking.
- `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala` — entries to wire, page after a sequence.
- `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala` and `LogPageCodec.scala` — wire types and codec.

**Server, modified:** `engine/Engine.scala`, `application/GameApplicationService.scala`, `gameplay/walker/WalkerReplay.scala`, `gameplay/walker/ProcedureWalker.scala`, `application/GamePresentationProjector.scala`, `application/PreviewModifierDescriptions.scala`, `model/CoreOperations.scala` (doc only), `application/GameProjection.scala`, `server/TrustedGameGateway.scala`, `server/TrustedSeatRoutes.scala`, `server/GameRoutes.scala`.

**Frontend, new:** `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`.

**Frontend, modified:** `GameClient.scala`, `TableSession.scala`, `TableScreen.scala`, `GameTableShell.scala`, `frontend/styles.css`.

**Tests, new:** `src/test/scala/oathdigital/engine/EventReplayEngineSuite.scala`, `src/test/scala/oathdigital/application/GameHistorySuite.scala`, `src/test/scala/oathdigital/application/gamelog/{LogScripts,GameLogHeadlineSuite,GameLogStartLineSuite,GameLogActionLineSuite,GameLogExchangeSuite,GameLogPropertiesSuite,GameLogRouteSuite}.scala`, `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`.

**Tests, modified:** `frontend/src/test/scala/oathdigital/frontend/{TableSessionSuite,ServerModeUiSuite,HttpGameClientSuite}.scala`.

---

### Task 1: Scan the journal

**Files:**
- Modify: `src/main/scala/oathdigital/engine/Engine.scala` (the `EventReplayEngine` class at the end)
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala:15-25` (result types) and `:259-284` (`reconstruct`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala:30-40` (`executeRecorded`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala:112-115` (beside `applyRecorded`)
- Create: `src/test/scala/oathdigital/engine/EventReplayEngineSuite.scala`
- Create: `src/test/scala/oathdigital/application/GameHistorySuite.scala`

**Interfaces:**
- Produces: `final case class ReplayStep[S, E](event: RecordedEvent[E], before: S, after: S)` in `oathdigital.engine`; `EventReplayEngine.scan(events: Iterable[RecordedEvent[E]]): Either[EventReplayFailure[V], Vector[ReplayStep[S, E]]]`.
- Produces: `final case class GameHistory(steps: Vector[ReplayStep[OathState, OathEvent]], nextSequence: Long)` in `oathdigital.application`; `GameApplicationService.history(gameId: String): Either[GameApplicationError, Option[GameHistory]]`.
- Produces: `ProcedureWalker.applyRecordedOperation(state: ReadyGame, operation: CoreOperation): Either[OathViolation, ReadyGame]`.

- [ ] **Step 1: Write the failing engine test**

Create `src/test/scala/oathdigital/engine/EventReplayEngineSuite.scala`:

```scala
package oathdigital.engine

class EventReplayEngineSuite extends munit.FunSuite:
  /** Adds each event to a running total; a negative event is corrupt. */
  private object Totals extends EventEvolution[Int, Int, String]:
    val initialState = 0
    def evolve(state: Int, event: Int): Either[String, Int] =
      if event < 0 then Left(s"negative $event") else Right(state + event)

  private val engine = new EventReplayEngine(Totals)
  private def recorded(events: Int*) = events.toVector.zipWithIndex.map {
    case (event, index) => RecordedEvent(index.toLong, event) }

  test("scan pairs every event with the state before and after it"):
    assertEquals(engine.scan(recorded(2, 3, 5)), Right(Vector(
      ReplayStep(RecordedEvent(0L, 2), 0, 2),
      ReplayStep(RecordedEvent(1L, 3), 2, 5),
      ReplayStep(RecordedEvent(2L, 5), 5, 10))))

  test("replay is the last step's after, or the initial state when empty"):
    assertEquals(engine.replay(recorded(2, 3, 5)), Right(10))
    assertEquals(engine.replay(recorded()), Right(0))
    assertEquals(engine.scan(recorded()), Right(Vector.empty))

  test("scan and replay report the same corrupt event"):
    val corrupt = recorded(2, -1, 5)
    assertEquals(engine.scan(corrupt), Left(EventReplayFailure(1L, "negative -1")))
    assertEquals(engine.replay(corrupt), Left(EventReplayFailure(1L, "negative -1")))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.engine.EventReplayEngineSuite"`
Expected: compilation failure, `Not found: ReplayStep` and `value scan is not a member`.

- [ ] **Step 3: Implement `scan` and make `replay` delegate**

In `Engine.scala`, add after `final case class EventReplayFailure[V](...)`:

```scala
/** One journal event with the state it was applied to and the state it made. */
final case class ReplayStep[S, E](event: RecordedEvent[E], before: S, after: S)
```

Replace the body of `EventReplayEngine` with:

```scala
final class EventReplayEngine[S, E, V](
    evolution: EventEvolution[S, E, V]
):
  /** The whole fold, one step per event, so a reader that needs the state
    * around every event (the game log) and a reader that needs only the end
    * (`replay`) share one fold and cannot drift.
    */
  def scan(
      events: Iterable[RecordedEvent[E]]
  ): Either[EventReplayFailure[V], Vector[ReplayStep[S, E]]] =
    events.foldLeft[Either[EventReplayFailure[V], (S, Vector[ReplayStep[S, E]])]](
      Right(evolution.initialState -> Vector.empty)
    ):
      case (Right((state, steps)), record) =>
        evolution
          .evolve(state, record.event)
          .left
          .map(violation => EventReplayFailure(record.index, violation))
          .map(next => next -> (steps :+ ReplayStep(record, state, next)))
      case (failure @ Left(_), _) => failure
    .map(_._2)

  def replay(
      events: Iterable[RecordedEvent[E]]
  ): Either[EventReplayFailure[V], S] =
    scan(events).map(_.lastOption.fold(evolution.initialState)(_.after))
```

If the compiler rejects `failure @ Left(_)` as the wrong `Left` type, write `case (Left(failure), _) => Left(failure)`.

- [ ] **Step 4: Run the engine test**

Run: `./sbtw "testOnly oathdigital.engine.EventReplayEngineSuite"`
Expected: 3 tests pass.

- [ ] **Step 5: Write the failing history test**

Create `src/test/scala/oathdigital/application/GameHistorySuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, chronicle, orders}
import oathdigital.model.OathState

class GameHistorySuite extends munit.FunSuite:
  test("history is the scanned journal: one step per event, ending on the loaded state"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = service.handle("history", 0L,
      GameCommand.Begin(chronicle, orders)).toOption.get
    val history = service.history("history").toOption.flatten.get
    val loaded = service.load("history").toOption.flatten.get
    assertEquals(history.nextSequence, begun.nextSequence)
    assertEquals(history.steps.size.toLong, begun.nextSequence)
    assertEquals(history.steps.map(_.event.index),
      (0L until begun.nextSequence).toVector)
    assertEquals(history.steps.head.before, OathState.NoGame)
    assertEquals(history.steps.last.after, loaded.state)
    history.steps.sliding(2).foreach { pair =>
      assertEquals(pair(1).before, pair(0).after) }

  test("history of a missing game is None"):
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    assertEquals(service.history("missing"), Right(None))
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.GameHistorySuite"`
Expected: compilation failure, `value history is not a member of GameApplicationService`.

- [ ] **Step 7: Implement `history`**

In `GameApplicationService.scala`, change the engine import to `import oathdigital.engine.{EventReplayEngine, RecordedEvent, ReplayStep}` and add after `LoadedGame`:

```scala
/** The whole journal as the log reads it: every event with the state
  * before and after it, and the sequence the next event takes.
  */
final case class GameHistory(
    steps: Vector[ReplayStep[OathState, OathEvent]],
    nextSequence: Long
)
```

Add after `load`:

```scala
  /** The scanned journal: the same decode and fold `load` runs, keeping
    * every step. The game log formats from this; nothing else needs it.
    */
  def history(
      gameId: String
  ): Either[GameApplicationError, Option[GameHistory]] =
    repository.load(gameId).left.map(storageError).flatMap:
      case None => Right(None)
      case Some(stream) =>
        for
          records <- decodeRecords(gameId, stream)
          steps <- replay.scan(records).left
            .map(failure => ReplayFailure(failure.index, failure.violation))
        yield Some(GameHistory(steps, stream.nextSequence))
```

Split `reconstruct` so both share the decoding:

```scala
  private def reconstruct(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, OathState] =
    decodeRecords(gameId, stream).flatMap(records =>
      replay.replay(records).left
        .map(failure => ReplayFailure(failure.index, failure.violation)))

  private def decodeRecords(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, Vector[RecordedEvent[OathEvent]]] =
    for
      _ <-
        if stream.gameId == gameId then Right(())
        else Left(StreamIdentityMismatch(gameId, stream.gameId))
      envelopes <- eventCodec
        .decodeStream(stream.records.mkString("[", ",", "]"))
        .left
        .map(CodecFailure.apply)
      _ <- envelopes.zipWithIndex.collectFirst {
        case (envelope, index) if envelope.sequence != index.toLong =>
          CodecFailure(EventCodecFailure("invalid-sequence",
            s"$$[$index].sequence",
            s"expected $index but found ${envelope.sequence}"))
        case (envelope, _) if envelope.gameId != gameId =>
          StreamIdentityMismatch(gameId, envelope.gameId)
      }.toLeft(())
    yield envelopes.map(envelope =>
      RecordedEvent(envelope.sequence, envelope.event))
```

`decodeStream` returns a collection of envelopes; if it is not already a `Vector`, add `.toVector` to the `yield`.

- [ ] **Step 8: Expose one-operation replay application**

In `WalkerReplay.scala`, replace `executeRecorded` with:

```scala
  /** Applies one recorded operation the way the pipeline ran it: a
    * `PayCost` whose payer is not the active player settles at once, so the
    * recorded (requested) operation expands to the same moves it did when it
    * ran.
    */
  def applyOperation(ready: ReadyGame, operation: CoreOperation)
      : Either[OathViolation, ReadyGame] =
    PayCostSettlement.prepare(ready, operation).flatMap(prepared =>
      new OperationExecutor().execute(ready, prepared).left.map(_.toViolation))

  private def executeRecorded(ready: ReadyGame, ops: Vector[CoreOperation])
      : Either[OathViolation, ReadyGame] =
    ops.foldLeft[Either[OathViolation, ReadyGame]](Right(ready)):
      (result, operation) => result.flatMap(applyOperation(_, operation))
```

In `ProcedureWalker.scala`, add below `applyRecorded`:

```scala
  /** Applies ONE operation of a recorded batch, exactly as replay applies
    * each in turn. The game log steps through a batch with it, so a line
    * about one operation is judged against the state just before and just
    * after that operation rather than the whole batch.
    */
  def applyRecordedOperation(state: ReadyGame, operation: CoreOperation)
      : Either[OathViolation, ReadyGame] =
    WalkerReplay.applyOperation(state, operation)
```

Add `CoreOperation` to `ProcedureWalker.scala`'s model import if it is not already imported.

- [ ] **Step 9: Run the new suites and the full server suite**

Run: `./sbtw "testOnly oathdigital.application.GameHistorySuite oathdigital.engine.EventReplayEngineSuite"`
Expected: 5 tests pass.
Run: `./sbtw "test"`
Expected: every test passes. Record the count as the server baseline.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/engine/Engine.scala \
  src/main/scala/oathdigital/application/GameApplicationService.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerReplay.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/test/scala/oathdigital/engine/EventReplayEngineSuite.scala \
  src/test/scala/oathdigital/application/GameHistorySuite.scala
git commit -m "feat(engine): scan the journal with the state around every event"
```

---

### Task 2: Names and card visibility on the presentation projector

**Files:**
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala` (`setupPlayers`, `readyPlayers`, new members after `identifiesCard`, new companion object at the end)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:403-408` (`orientationOf` delegates)
- Modify: `src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala` (`printedOn` visibility)
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:521-527` (the `BeginTurn` doc comment)
- Create: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (the first script only; later tasks add more)
- Create: `src/test/scala/oathdigital/application/PresentationLabelsSuite.scala`

**Interfaces:**
- Produces on `GamePresentationProjector`: `playerLabel(id: PlayerId): String`; `cardLabel(ready: ReadyGame, id: CardId): String`; `identifiesAt(ready: ReadyGame, viewer: Option[PlayerId], id: CardId): Boolean`.
- Produces: `private[application] object GamePresentationProjector { def orientationOf(state: Option[CardState]): Option[Orientation] }`.
- Produces: `PreviewModifierDescriptions.printedOn(ready: ReadyGame, actor: PlayerId, handlerId: String): Option[CardId]` at `private[application]`.
- Produces (test): `oathdigital.application.gamelog.Script` and `LogScripts.woken`.

- [ ] **Step 1: Write the first script and a failing test**

Create `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Situation, SituationDriver}

/** A journal built by real play through `GameApplicationService` on every
  * run (spec, "Test journals"). No stored game is a fixture: a script a new
  * card changes fails at the command that no longer applies.
  */
final case class Script(name: String, service: GameApplicationService,
    actor: PlayerId):
  def history(using munit.Location): GameHistory =
    service.history(name) match
      case Right(Some(history)) => history
      case other => munit.Assertions.fail(s"$name has no history: $other")
  def players(using munit.Location): Vector[PlayerId] =
    history.steps.last.after match
      case OathState.Ready(ready) => ready.game.current.players.map(_.player)
      case other => munit.Assertions.fail(s"$name is not ready: $other")

object LogScripts:
  /** Every attack die a sword, every defense die two shields, so a script
    * reaches the same board every run. */
  val steadyDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.TwoShields)

  /** A fresh service and its journaled driver, the stream named `name`, with
    * pawns spread one per site so no two players share a site by accident. */
  def journaled(name: String, dice: CampaignDicePort = steadyDice,
      spread: Vector[SiteId] = FirstGameSetupFixture.sites)
      : (GameApplicationService, InMemoryEventStreamRepository, SituationDriver) =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = dice)
    (service, repository, Situation.journaled(service, catalog, repository,
      name).withAnswers(Situation.pawnsAt(spread)))

  def active(situation: Situation)(using munit.Location): PlayerId =
    situation.ready.game.current.turn.activePlayer

  def pawn(situation: Situation, player: PlayerId)(using munit.Location)
      : SiteId =
    situation.ready.game.current.players.find(_.player == player)
      .flatMap(_.pawnSite).get

  /** Setup to the first player's Wake. */
  def woken(using munit.Location): Script =
    val (service, _, driver) = journaled("woken")
    val situation = Situation.wake(driver)
    Script("woken", service, active(situation))
```

Create `src/test/scala/oathdigital/application/PresentationLabelsSuite.scala`:

```scala
package oathdigital.application

import oathdigital.application.gamelog.LogScripts
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class PresentationLabelsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)

  private def woken: ReadyGame = LogScripts.woken.history.steps.last.after match
    case OathState.Ready(ready) => ready
    case other => fail(s"expected a ready game, got $other")

  test("a player's label is the one the Players strip shows"):
    val ready = woken
    val player = ready.game.current.players.head.player
    assertEquals(presentation.playerLabel(player),
      presentation.readyPlayers(ready).head.displayName)

  test("a site denizen is identified to everyone; a facedown adviser to its owner only"):
    val ready = woken
    val current = ready.game.current
    val denizen = current.map.inPlay.flatMap(current.map.sites(_).denizens)
      .collectFirst { case DenizenState(id, _, _) => id }.get
    Vector(None, Some(current.players.head.player)).foreach { viewer =>
      assert(presentation.identifiesAt(ready, viewer, denizen)) }
    val owner = current.players.find(_.advisers.exists(
      presentation.adviserOrientation(_) == Orientation.FaceDown)).get
    val adviser = owner.advisers.find(
      presentation.adviserOrientation(_) == Orientation.FaceDown).get.id
    assert(presentation.identifiesAt(ready, Some(owner.player), adviser))
    val other = current.players.find(_.player != owner.player).get.player
    assert(!presentation.identifiesAt(ready, Some(other), adviser))
    assert(!presentation.identifiesAt(ready, None, adviser))

  test("a card in the world deck is identified to nobody"):
    val ready = woken
    val top = ready.game.current.commonCards.worldDeck.head
    ready.game.current.players.map(p => Some(p.player)).foreach { viewer =>
      assert(!presentation.identifiesAt(ready, viewer, top)) }

  test("a card's label is its printed name"):
    val ready = woken
    val denizen = catalog.denizens.head
    assertEquals(presentation.cardLabel(ready, DenizenId(denizen.id.value)),
      denizen.name)
```

If setup dealt every adviser faceup, the second test's `find` fails: then take the owner from `Situation.wake` answered with the setup adviser partition's default, which keeps one card per player facedown (read `SetupProcedure` to confirm the orientation before changing the test).

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.PresentationLabelsSuite"`
Expected: compilation failure naming `playerLabel`, `identifiesAt` and `cardLabel`.

- [ ] **Step 3: Implement the labels and `identifiesAt`**

In `GamePresentationProjector.scala`, add after `edificeLabel`:

```scala
  /** A player's display name: the one label the Players strip and the game
    * log both use, so the two never disagree. */
  def playerLabel(id: PlayerId): String = safeLabel(id.value)
```

Change `setupPlayers` and `readyPlayers` to call `playerLabel(participant.playerId)` and `playerLabel(player.player)` in place of `safeLabel(....value)`.

Add after `identifiesCard`:

```scala
  /** [[identifiesCard]] for the card wherever it lies in `ready`: its own
    * orientation and container, looked up rather than passed. False for a
    * card that is nowhere. The game log asks this on the state before and
    * after each operation (spec, "Visibility").
    */
  def identifiesAt(ready: ReadyGame, viewer: Option[PlayerId], id: CardId)
      : Boolean =
    CardIndex.from(ready.game).toOption.flatMap(_.get(id)).exists(located =>
      identifiesCard(ready, viewer, id,
        GamePresentationProjector.orientationOf(located.state),
        located.location.container))

  /** A card's printed name; an edifice's is the side it shows in `ready`. */
  def cardLabel(ready: ReadyGame, id: CardId): String = id match
    case edifice: EdificeId =>
      val side = CardIndex.from(ready.game).toOption.flatMap(_.stateOf(edifice))
        .collect { case EdificeState(_, side, _) => side }
        .getOrElse(EdificeSide.Intact)
      edificeLabel(edifice, side)
    case other => cardDetails(other, None, hidden = false).name
```

Add at the end of the file:

```scala
private[application] object GamePresentationProjector:
  /** A card's orientation from its container state; `None` for a state
    * that has none (a deck, a discard, a hand). */
  def orientationOf(state: Option[CardState]): Option[Orientation] =
    state match
      case Some(DenizenState(_, orientation, _)) => Some(orientation)
      case Some(VisionState(_, orientation)) => Some(orientation)
      case Some(RelicState(_, orientation, _)) => Some(orientation)
      case _ => None
```

In `WalkerDecisionProjector.scala`, replace the body of the private `orientationOf` with `GamePresentationProjector.orientationOf(state)` (keep the private method so its three call sites are untouched).

- [ ] **Step 4: Open `printedOn` to the application package**

In `PreviewModifierDescriptions.scala`, change `private def printedOn(` to `private[application] def printedOn(`, and add one sentence to its doc comment: "The game log names modifiers and used powers with it too, and judges visibility per viewer itself."

- [ ] **Step 5: Correct the `BeginTurn` doc**

In `CoreOperations.scala`, replace the sentence "Finish Rest is the only procedure that declares it." with:

```scala
  * Two procedures declare it: Finish Rest hands the turn to the next player,
  * and Setup's last step hands the first turn to the first player.
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.PresentationLabelsSuite oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/GamePresentationProjector.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala \
  src/main/scala/oathdigital/model/CoreOperations.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/PresentationLabelsSuite.scala
git commit -m "feat(application): label players and judge card visibility where a card lies"
```

---

### Task 3: The formatter skeleton and headlines

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/LogEntry.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/LogJournal.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/LogWords.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (add `round`, `formatter`, `texts`)
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogHeadlineSuite.scala`

**Interfaces:**
- Consumes: `GameHistory`, `ReplayStep`, `ProcedureWalker.applyRecordedOperation` (Task 1); `playerLabel`, `cardLabel`, `identifiesAt`, `printedOn` (Task 2).
- Produces: `LogEntry(sequence: Long, ordinal: Int, kind: LogKind, depth: Int, spans: Vector[LogSpan])`; `enum LogKind(val key: String)` with `Round, Turn, Action, Decision, Roll, Delta, Trigger, Victory`; `sealed trait LogSpan { def text: String }` with `Text(value)`, `Player(id, name)`, `Card(id, name)`, `Site(id, name)`, `Amount(value, unit)`, `Cost(value, unit)`.
- Produces: `private[application] final class GameLogFormatter(catalog: ExecutableCatalog, presentation: GamePresentationProjector)` with `format(steps: Vector[ReplayStep[OathState, OathEvent]], viewer: Option[PlayerId]): Vector[LogEntry]`.
- Produces (internal to `gamelog`): `Posted`, `OpStep`, `Run`, `LogJournal`, `CardWord`, `LogWords`, as written below. Later tasks call exactly these names.

- [ ] **Step 1: Add the round script and the text helpers**

Append to `object LogScripts` in `LogScripts.scala`:

```scala
  /** Setup; the first player travels and rests; every other player rests;
    * Round 2 begins. */
  def round(using munit.Location): Script =
    val (service, _, driver) = journaled("round")
    val woken = Situation.wake(driver)
    val first = active(woken)
    val acting = woken.after(GameCommand.EndWake(first))
    val destination = acting.ready.game.current.map.inPlay
      .find(_ != pawn(acting, first)).get
    val travelled = acting.after(GameCommand.StartWalker(ActionRef.Travel,
      StartPayload(first, Vector.empty,
        Vector(DecisionOptionRef.Site(destination)))))
    val seats = woken.ready.game.current.players.size
    (1 to seats).foldLeft(travelled) { (situation, turn) =>
      val player = active(situation)
      val awake = if turn == 1 then situation
        else situation.after(GameCommand.EndWake(player))
      awake.after(GameCommand.BeginRest(player),
        GameCommand.FinishRest(player))
    }
    Script("round", service, first)

  val presentation = new GamePresentationProjector(catalog)
  val formatter = new GameLogFormatter(catalog, presentation)

  def format(script: Script, viewer: Option[PlayerId])(using munit.Location)
      : Vector[LogEntry] =
    formatter.format(script.history.steps, viewer)

  /** An entry as a client that ignores span kinds shows it; the cost span
    * set apart by a space, as the pane sets it apart by a margin. */
  def text(entry: LogEntry): String = entry.spans.map {
    case cost: LogSpan.Cost => " " + cost.text
    case span => span.text
  }.mkString

  def texts(entries: Vector[LogEntry]): Vector[String] = entries.map(text)

  def name(player: PlayerId): String = presentation.playerLabel(player)
```

- [ ] **Step 2: Write the failing headline tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogHeadlineSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogHeadlineSuite extends munit.FunSuite:
  private def headlines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 0))

  test("setup opens the log and hands the first turn to Round 1"):
    val script = woken
    val entries = format(script, Some(script.actor))
    assertEquals(headlines(entries),
      Vector("Setup", "Round 1", s"${name(script.actor)}'s turn"))
    assertEquals(entries.map(_.kind).take(3),
      Vector(LogKind.Round, LogKind.Round, LogKind.Turn))
    assertEquals(entries.head.sequence, 0L)

  test("a whole round posts one turn headline per seat and opens Round 2"):
    val script = round
    val all = headlines(format(script, Some(script.actor)))
    assertEquals(all.take(2), Vector("Setup", "Round 1"))
    assertEquals(all.count(_.endsWith("'s turn")), script.players.size + 1)
    assertEquals(all.takeRight(2),
      Vector("Round 2", s"${name(script.actor)}'s turn"))

  test("the turn headline names the player with a player span"):
    val script = woken
    val turn = format(script, None).find(_.kind == LogKind.Turn).get
    assertEquals(turn.spans, Vector(
      LogSpan.Player(script.actor.value, name(script.actor)),
      LogSpan.Text("'s turn")))

  test("entries are keyed by journal sequence, in key order, with ordinals from 0"):
    val entries = format(round, None)
    val keys = entries.map(entry => entry.sequence -> entry.ordinal)
    assertEquals(keys, keys.sorted)
    entries.groupBy(_.sequence).values.foreach { same =>
      assertEquals(same.map(_.ordinal), same.indices.toVector) }
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogHeadlineSuite"`
Expected: compilation failure, `Not found: GameLogFormatter`.

- [ ] **Step 4: Create the entry model**

Create `src/main/scala/oathdigital/application/gamelog/LogEntry.scala`:

```scala
package oathdigital.application.gamelog

/** One line of the game log (spec, "Output"). `(sequence, ordinal)` is its
  * stable identity: `sequence` is the journal index of the event that posted
  * it, `ordinal` its position among the entries that event posted. Depth 0
  * is a headline, depth 1 a line.
  */
final case class LogEntry(sequence: Long, ordinal: Int, kind: LogKind,
    depth: Int, spans: Vector[LogSpan])

/** `key` is the kind's wire spelling. */
enum LogKind(val key: String):
  case Round extends LogKind("round")
  case Turn extends LogKind("turn")
  case Action extends LogKind("action")
  case Decision extends LogKind("decision")
  case Roll extends LogKind("roll")
  case Delta extends LogKind("delta")
  case Trigger extends LogKind("trigger")
  case Victory extends LogKind("victory")

/** One piece of an entry's text: plain words or a typed reference. `text` is
  * what a reader that ignores the kind shows. A card a viewer may not
  * identify is never a `Card`: it is `Text` naming its back.
  */
sealed trait LogSpan extends Product with Serializable:
  def text: String
object LogSpan:
  final case class Text(value: String) extends LogSpan:
    def text: String = value
  final case class Player(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Card(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Site(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Amount(value: Int, unit: String) extends LogSpan:
    def text: String = s"$value $unit"
  /** What an action spent, drawn apart from the sentence (spec, "Supply"). */
  final case class Cost(value: Int, unit: String) extends LogSpan:
    def text: String = s"−$value $unit"

/** An entry before it has a key: what one event posts, in order. */
private[gamelog] final case class Posted(kind: LogKind, depth: Int,
    spans: Vector[LogSpan])
private[gamelog] object Posted:
  def headline(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 0, spans)
  def line(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 1, spans)
```

- [ ] **Step 5: Create the journal view**

Create `src/main/scala/oathdigital/application/gamelog/LogJournal.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.engine.ReplayStep
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerCompleted, WalkerParked, WalkerStepRecorded}
import oathdigital.model._

/** One operation of a recorded batch with the state just before and just
  * after it (spec, "Input"). */
private[gamelog] final case class OpStep(operation: CoreOperation,
    before: ReadyGame, after: ReadyGame)

/** A walker run in progress: its procedure, whose action it is, the index of
  * its first event, and -- once posted -- the segment end of its start line.
  */
private[gamelog] final case class Run(procedure: ProcedureRef, actor: PlayerId,
    first: Int, started: Option[Int])

/** The scanned journal a formatting reads. Indices are positions in `steps`,
  * which equal journal sequences because the journal starts at 0.
  *
  * A segment is what one command appends, ending at a `walker.parked` or
  * `walker.completed`; a line may read ahead to the end of its own segment
  * and no further (spec, "Posting").
  */
private[gamelog] final class LogJournal(
    steps: Vector[ReplayStep[OathState, OathEvent]]):
  private val closings: Vector[Option[Int]] =
    steps.indices.foldRight(List.empty[Option[Int]]) { (index, later) =>
      val next = later.headOption.flatten
      (steps(index).event.event match
        case _: WalkerParked | _: WalkerCompleted => Some(index)
        case _ => next) :: later
    }.toVector

  private val batches: Vector[Vector[OpStep]] = steps.map(LogJournal.opSteps)

  def size: Int = steps.size
  def event(index: Int): OathEvent = steps(index).event.event
  def sequence(index: Int): Long = steps(index).event.index
  def readyBefore(index: Int): Option[ReadyGame] =
    LogJournal.ready(steps(index).before)
  def readyAfter(index: Int): Option[ReadyGame] =
    LogJournal.ready(steps(index).after)
  def ops(index: Int): Vector[OpStep] = batches(index)

  /** The last event of `index`'s segment: its closing event, or `index`
    * itself when no closing event follows in this prefix. */
  def segmentEnd(index: Int): Int = closings(index).getOrElse(index)

  /** The procedure `index`'s segment belongs to, named only by the segment's
    * closing event: a step carries none. */
  def procedureAt(index: Int): Option[ProcedureRef] =
    closings(index).map(event).collect {
      case parked: WalkerParked => parked.procedure
      case completed: WalkerCompleted => completed.procedure
    }

  /** Every operation of `run` from its first event through `at`. */
  def runOps(run: Run, at: Int): Vector[(Int, OpStep)] =
    (run.first to at).toVector.flatMap(index => ops(index).map(index -> _))

  /** Every answer `run` recorded from its first event through `at`. */
  def answers(run: Run, at: Int): Vector[Answered] =
    (run.first to at).toVector.map(event).collect {
      case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _, _) =>
        Answered(id, answer, by)
    }

  /** Supply `player` lost to `SpendSupply` in event `index`, from the states
    * around each spend: a spend is clamped when applied. */
  def supplySpent(index: Int, player: PlayerId): Int =
    ops(index).collect {
      case OpStep(SpendSupply(spender, _, _), before, after)
          if spender == player =>
        LogJournal.supply(before, player) - LogJournal.supply(after, player)
    }.sum

private[gamelog] object LogJournal:
  def ready(state: OathState): Option[ReadyGame] = state match
    case OathState.Ready(ready) => Some(ready)
    case _ => None

  def supply(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.supply.supply)

  def pawnSite(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)

  /** A step's batch applied one operation at a time from the state before
    * the step. Replay already applied it whole, so a failure here cannot
    * happen on a journal that loaded; if it did, the rest of the batch keeps
    * the last good state rather than losing its lines. */
  private def opSteps(step: ReplayStep[OathState, OathEvent]): Vector[OpStep] =
    (step.before, step.event.event) match
      case (OathState.Ready(start), recorded: WalkerStepRecorded) =>
        recorded.ops.foldLeft((start, Vector.empty[OpStep])) {
          case ((state, done), operation) =>
            val next = ProcedureWalker.applyRecordedOperation(state, operation)
              .getOrElse(state)
            (next, done :+ OpStep(operation, state, next))
        }._2
      case _ => Vector.empty
```

- [ ] **Step 6: Create the names and the visibility rule**

Create `src/main/scala/oathdigital/application/gamelog/LogWords.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{GamePresentationProjector,
  PreviewModifierDescriptions}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** A card as one viewer may read it: by name, or by its back's kind. */
private[gamelog] enum CardWord:
  case Named(span: LogSpan.Card)
  case Back(kind: String)

/** Every name the log writes, from the same presentation projector the board
  * uses, and the one visibility rule (spec, "Names" and "Visibility").
  */
private[gamelog] final class LogWords(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val descriptions = new PreviewModifierDescriptions(catalog,
    presentation)

  def player(id: PlayerId): LogSpan =
    LogSpan.Player(id.value, presentation.playerLabel(id))
  def site(id: SiteId): LogSpan =
    LogSpan.Site(id.value, presentation.siteLabel(id))
  def banner(banner: Banner): LogSpan =
    LogSpan.Text(BannerRules.displayName(banner))
  /** A Vision a victory names: public by then. */
  def vision(id: VisionId): LogSpan =
    LogSpan.Card(id.value, presentation.cardDetails(id, None,
      hidden = false).name)

  /** Named when the viewer identifies the card where it lies before the
    * operation, or where it lies after it; otherwise its back. */
  def card(id: CardId, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): CardWord =
    if presentation.identifiesAt(before, viewer, id) ||
        presentation.identifiesAt(after, viewer, id) then
      CardWord.Named(LogSpan.Card(id.value, presentation.cardLabel(after, id)))
    else CardWord.Back(LogWords.backOf(id))

  /** Cards as one phrase: named cards in order, then backs counted by kind,
    * "Tinker, 2 Denizens and a Vision". */
  def cards(words: Vector[CardWord]): Vector[LogSpan] =
    val named = words.collect { case CardWord.Named(span) =>
      Vector[LogSpan](span) }
    val backs = words.collect { case CardWord.Back(kind) => kind }
    val counted = backs.distinct.map { kind =>
      val count = backs.count(_ == kind)
      Vector[LogSpan](LogSpan.Text(
        if count == 1 then s"a $kind" else s"$count ${LogWords.plural(kind)}"))
    }
    LogWords.join(named ++ counted)

  def one(word: CardWord): Vector[LogSpan] = cards(Vector(word))

  /** A power named by the card it is printed on, as the modifier picker
    * names it; the card follows the visibility rule on `ready`. */
  def power(ready: ReadyGame, actor: PlayerId, id: PowerId,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    descriptions.printedOn(ready, actor, id.value).fold(
      Vector[LogSpan](LogSpan.Text(presentation.safeLabel(id.value))))(card =>
      one(this.card(card, ready, ready, viewer)))

private[gamelog] object LogWords:
  def backOf(id: CardId): String = id match
    case _: VisionId => "Vision"
    case _: RelicId => "Relic"
    case _: EdificeId => "Edifice"
    case _: LegacyId => "Legacy"
    case _ => "Denizen"

  def plural(kind: String): String =
    if kind == "Legacy" then "Legacies" else s"${kind}s"

  /** "A", "A and B", "A, B and C". */
  def join(items: Vector[Vector[LogSpan]]): Vector[LogSpan] = items match
    case Vector() => Vector.empty
    case Vector(only) => only
    case _ =>
      val leading = items.init.zipWithIndex.flatMap { case (item, index) =>
        if index == 0 then item else LogSpan.Text(", ") +: item }
      leading ++ (LogSpan.Text(" and ") +: items.last)
```

- [ ] **Step 7: Create the formatter with headlines only**

Create `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.ReplayStep
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.model._
import LogSpan.Text

/** The game log: a pure function of the scanned journal prefix and the
  * viewer (spec, "The formatter").
  *
  * Every event is matched by name. A new `OathEvent` case fails compilation
  * here until it has a decision; `WalkerEvent` is open, so a walker event
  * this build does not know is silent, the same fallback `OathRules.evolve`
  * takes.
  */
private[application] final class GameLogFormatter(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val words = new LogWords(catalog, presentation)

  def format(steps: Vector[ReplayStep[OathState, OathEvent]],
      viewer: Option[PlayerId]): Vector[LogEntry] =
    val journal = new LogJournal(steps)
    (0 until journal.size).foldLeft(
        (Option.empty[Run], Vector.empty[LogEntry])) {
      case ((run, entries), at) =>
        val (posted, next) = eventLines(journal, run, at, viewer)
        (next, entries ++ posted.zipWithIndex.map { case (entry, ordinal) =>
          LogEntry(journal.sequence(at), ordinal, entry.kind, entry.depth,
            entry.spans) })
    }._2

  private def eventLines(journal: LogJournal, run: Option[Run], at: Int,
      viewer: Option[PlayerId]): (Vector[Posted], Option[Run]) =
    journal.event(at) match
      case OathEvent.GameStarted(_, _) =>
        (Vector(Posted.headline(LogKind.Round, Vector(Text("Setup")))), run)
      case OathEvent.RoundEnded(_, Some(next)) =>
        (roundHeadline(next) +: journal.readyAfter(at).toVector.map(ready =>
          turnHeadline(ready.game.current.turn.activePlayer)), run)
      // After the eighth round no round begins; the victory headline follows.
      case OathEvent.RoundEnded(_, None) => (Vector.empty, run)
      case OathEvent.UsurperVictory(player) =>
        (Vector(victory(player, Vector(Text(" won as the Usurper")))), run)
      case OathEvent.VisionVictory(player, vision) =>
        (Vector(victory(player, Vector(Text(" won with "),
          words.vision(vision))))), run)
      case OathEvent.WarExhaustionResolved(winner, kind, vision, _) =>
        (Vector(victory(winner, exhaustion(kind, vision))), run)
      // A diagnostic, not play.
      case _: OathEvent.IgnoredRulesRecorded => (Vector.empty, run)
      // Detail lines: the second slice.
      case _: OathEvent.SiteRelicsPeeked | _: OathEvent.OwnedRelicRevealed |
          _: OathEvent.WarbandsMoved | _: OathEvent.BanditsRefilled |
          _: OathEvent.UsurperFlipped => (Vector.empty, run)
      case _: WalkerStepRecorded | _: WalkerParked | _: WalkerCompleted =>
        walker(journal, run, at, viewer)
      case _: WalkerEvent => (Vector.empty, run)

  /** Lines for one event of a walker run. A run begins at the first walker
    * event after the previous run completed, and its procedure is read from
    * the closing event of that first segment. */
  private def walker(journal: LogJournal, current: Option[Run], at: Int,
      viewer: Option[PlayerId]): (Vector[Posted], Option[Run]) =
    current.orElse(for
      procedure <- journal.procedureAt(at)
      ready <- journal.readyBefore(at)
    yield Run(procedure, ready.game.current.turn.activePlayer, at, None)) match
      case None => (Vector.empty, None)
      case Some(run) =>
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => Some(run)
        (turnHeadlines(journal, at), next)

  /** A turn begins at `BeginTurn(player, Wake)`. Setup's closing one also
    * opens Round 1; a `BeginTurn` into the round's end posts nothing, since
    * `gameplay.round-ended` posts the next round and its first turn. */
  private def turnHeadlines(journal: LogJournal, at: Int): Vector[Posted] =
    journal.ops(at).flatMap {
      case OpStep(BeginTurn(player, Phase.Wake), before, _) =>
        if before.game.current.turn.phase == Phase.Setup then
          Vector(roundHeadline(1), turnHeadline(player))
        else Vector(turnHeadline(player))
      case _ => Vector.empty
    }

  private def roundHeadline(round: Int): Posted =
    Posted.headline(LogKind.Round, Vector(Text(s"Round $round")))

  private def turnHeadline(player: PlayerId): Posted =
    Posted.headline(LogKind.Turn, Vector(words.player(player), Text("'s turn")))

  private def victory(player: PlayerId, rest: Vector[LogSpan]): Posted =
    Posted.headline(LogKind.Victory, words.player(player) +: rest)

  private def exhaustion(kind: VictoryKind, vision: Option[VisionId])
      : Vector[LogSpan] = kind match
    case VictoryKind.Usurper => Vector(Text(" won as the Usurper"))
    case VictoryKind.Visionary => vision.fold(
      Vector[LogSpan](Text(" won as a Visionary")))(id =>
        Vector(Text(" won with "), words.vision(id)))
    case VictoryKind.Oathkeeper => Vector(Text(" won as the Oathkeeper"))
    case VictoryKind.RandomSelection => Vector(Text(" won by random selection"))
```

- [ ] **Step 8: Run the headline tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogHeadlineSuite"`
Expected: 4 tests pass. If "a whole round" fails because Begin Rest parks on a Rest choice, answer it in the script with `.withAnswers` naming that decision, and say so in the task report.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogHeadlineSuite.scala
git commit -m "feat(log): format round, turn and victory headlines from the scanned journal"
```

---

### Task 4: Start lines, and the first action lines

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/StartLines.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (`walker`, constructor fields)
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (add `oathkeeper`)
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`

**Interfaces:**
- Consumes: `LogJournal`, `Run`, `OpStep`, `LogWords`, `CardWord`, `Posted` (Task 3).
- Produces: `StartLines(words: LogWords, resolutions: Map[PowerId, PowerResolution])` with `start(journal, run, at, viewer): Option[Posted]` and `continued(journal, run, at): Option[Posted]`.
- Produces: `ActionLines(words: LogWords)` with `lines(journal: LogJournal, run: Run, at: Int, viewer: Option[PlayerId]): Vector[Posted]` and the helpers `action`, `plural`, `resource`, `source`, named below. Tasks 5 to 7 add cases to its match.

- [ ] **Step 1: Add the Oathkeeper script**

Append to `object LogScripts`. `ParkedServiceFixture` lives in `oathdigital.application` in the test tree, so the file's existing `import oathdigital.application._` already covers it:

```scala
  /** The service suite's Oathkeeper tie: an arranged board, the active
    * player's Travel, and the holder's choice of the next Oathkeeper. */
  def oathkeeper(using munit.Location): Script =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = steadyDice)
    val (parked, active, _, _) = ParkedServiceFixture.oathkeeperTiePark(
      service, repository, "oathkeeper")
    Situation(parked.state, Vector.empty, parked.nextSequence,
      Situation.journaled(service, catalog, repository, "oathkeeper")).after()
    Script("oathkeeper", service, active)
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogStartLineSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 1))

  private def supply(script: Script, player: PlayerId, index: Int): Int =
    script.history.steps(index).after match
      case OathState.Ready(ready) => ready.game.current.players
        .find(_.player == player).get.board.supply.supply
      case _ => fail("expected a ready game")

  test("Travel opens with its start line and cost, then says where it went"):
    val script = round
    val entries = format(script, Some(script.actor))
    val start = entries.find(entry => text(entry).startsWith("Started Travel")).get
    val travelled = entries.find(entry =>
      text(entry).startsWith("Travelled to ")).get
    assert(start.sequence < travelled.sequence)
    val cost = start.spans.collect { case cost: LogSpan.Cost => cost }
    assertEquals(cost.size, 1)
    val spentAt = start.sequence.toInt
    assertEquals(cost.head.value,
      supply(script, script.actor, spentAt - 1) -
        supply(script, script.actor, spentAt))
    assertEquals(cost.head.unit, "Supply")
    val destination = travelled.spans.collect { case site: LogSpan.Site => site }
    assertEquals(destination.size, 1)
    assertEquals(start.kind, LogKind.Action)

  test("the first player's Finish Rest reports the supply it restored"):
    val script = round
    val restored = lines(format(script, None))
      .filter(_.startsWith("Increased supply from "))
    assert(restored.nonEmpty, lines(format(script, None)))
    restored.foreach { line =>
      val numbers = "\\d+".r.findAllIn(line).map(_.toInt).toVector
      assertEquals(numbers.size, 2, line)
      assert(numbers(0) < numbers(1), line) }

  test("End Wake and Begin Rest post nothing"):
    val all = texts(format(round, None))
    assert(!all.exists(line => line.contains("Wake") || line.contains("Rest")),
      all)

  test("an Oathkeeper change names the new holder"):
    val script = oathkeeper
    val passed = format(script, None).filter(entry =>
      text(entry).startsWith("Oathkeeper passed to "))
    assertEquals(passed.size, 1)
    assertEquals(passed.head.kind, LogKind.Trigger)
    assert(passed.head.spans.exists(_.isInstanceOf[LogSpan.Player]),
      passed.head.spans)

  test("a start line without modifiers has no 'with' clause"):
    val start = texts(format(round, None)).find(_.startsWith("Started Travel")).get
    assert(!start.contains(" with "), start)
```

The "End Wake and Begin Rest post nothing" test relies on no player or site name containing "Wake" or "Rest" with a capital; the fixture's players are `p1` to `p3`. If a catalog site name does, compare against the lines each of those runs posted instead.

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogStartLineSuite"`
Expected: FAIL: no "Started Travel" entry (`None.get`).

- [ ] **Step 4: Create the start lines**

Create `src/main/scala/oathdigital/application/gamelog/StartLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.model._
import LogSpan.Text

/** The line that opens every action that can take modifiers (spec, "Start
  * lines"): its opening words, the modifiers the player chose, and the Supply
  * the action has spent by the end of the start line's segment.
  *
  * `resolutions` is each walker power's resolution: a power that resolves
  * automatically is not a choice, so it is not named.
  */
private[gamelog] final class StartLines(words: LogWords,
    resolutions: Map[PowerId, PowerResolution]):

  def start(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] =
    if run.started.nonEmpty then None
    else opening(journal, run, at).map(spans => Posted.line(LogKind.Action,
      spans ++ modifiers(journal, run, at, viewer) ++ cost(journal, run, at)))

  /** Recover spends Supply again each time the player continues rolling. */
  def continued(journal: LogJournal, run: Run, at: Int): Option[Posted] =
    run.procedure match
      case ActionRef.Recover
          if run.started.exists(_ != journal.segmentEnd(at)) =>
        val spent = journal.supplySpent(at, run.actor)
        Option.when(spent > 0)(Posted.line(LogKind.Action,
          Vector(Text("Continued Recover"), LogSpan.Cost(spent, "Supply"))))
      case _ => None

  /** The opening words, but only at the start line's anchor: the run's
    * first event, except Muster and Trade (their cost step, after the source
    * decision) and Campaign (the first event by which kind and defender are
    * both settled). */
  private def opening(journal: LogJournal, run: Run, at: Int)
      : Option[Vector[LogSpan]] =
    val completing = journal.event(at).isInstanceOf[WalkerCompleted]
    run.procedure match
      case ActionRef.Campaign => campaign(journal, run, at)
      case ActionRef.Muster | ActionRef.Trade =>
        title(run.procedure).filter(_ =>
          paysCost(journal, run, at) || completing).map(t => Vector(Text(t)))
      case other =>
        title(other).filter(_ => at == run.first).map(t => Vector(Text(t)))

  private def title(procedure: ProcedureRef): Option[String] =
    procedure match
      case ActionRef.Search => Some("Started Search")
      case ActionRef.PlayFacedownAdviser => Some("Playing Facedown Adviser")
      case ActionRef.Recover => Some("Started Recover")
      case ActionRef.Forge => Some("Started Forge")
      case ActionRef.Travel => Some("Started Travel")
      case ActionRef.Muster => Some("Started Muster")
      case ActionRef.Trade => Some("Started Trade")
      case ActionRef.Challenge => Some("Started Challenge")
      // Built from its kind and defender by `campaign`.
      case ActionRef.Campaign => None
      // No modifier window, so no start line.
      case ActionRef.TakeWealth | ActionRef.PlaceBannerResource |
          ActionRef.Negotiation | _: ActionRef.UsePower => None
      case _: PhaseTransitionRef | _: TriggeredProcedureRef => None

  private def paysCost(journal: LogJournal, run: Run, at: Int): Boolean =
    journal.ops(at).exists {
      case OpStep(SpendSupply(player, _, _), _, _) => player == run.actor
      case OpStep(Move(Piece.Favor(_) | Piece.Secrets(_),
          PositionedLocation(Location.PlayArea(player), _),
          PositionedLocation(Location.OnCard(_), _), _), _, _) =>
        player == run.actor
      case _ => false
    }

  /** Kind and defender from the answers so far and the state after `at`,
    * read exactly as the procedure reads them; failing that, from the
    * recorded result. */
  private def campaign(journal: LogJournal, run: Run, at: Int)
      : Option[Vector[LogSpan]] =
    val pending = PendingTree(Vector.empty, journal.answers(run, at))
    journal.readyAfter(at).flatMap(ready => for
      kind <- CampaignSetup.kindOf(ready, run.actor, pending)
      defender <- CampaignSetup.defenderOf(ready, run.actor, pending, kind)
    yield campaignWords(kind, defender)).orElse(journal.ops(at).collectFirst {
      case OpStep(RecordCampaignResult(result), _, _) =>
        campaignWords(result.kind, result.defender)
    })

  private def campaignWords(kind: CampaignKind, defender: CampaignDefender)
      : Vector[LogSpan] =
    val named = kind match
      case CampaignKind.Raid => "Raid"
      case CampaignKind.Conquest => "Conquest"
    defender match
      case CampaignDefender.Bandits =>
        Vector(Text(s"Started Campaign: $named against the bandits"))
      case CampaignDefender.Player(player) =>
        Vector(Text(s"Started Campaign: $named against "), words.player(player))

  /** The player's selection as a parked run records it, or, for a run that
    * never parked, the powers its steps record as contributing; automatic
    * powers are left out either way. */
  private def modifiers(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    val through = (run.first to journal.segmentEnd(at)).toVector
    val chosen = through.reverseIterator.map(journal.event).collectFirst {
      case parked: WalkerParked => parked.modifiers
    }.getOrElse(through.map(journal.event).flatMap {
      case step: WalkerStepRecorded => step.contributions
      case _ => Vector.empty
    }.distinct)
    val shown = chosen.filterNot(id =>
      resolutions.get(id).contains(PowerResolution.Automatic))
    journal.readyBefore(run.first).filter(_ => shown.nonEmpty)
      .fold(Vector.empty[LogSpan])(ready => Text(" with ") +: LogWords.join(
        shown.map(id => words.power(ready, run.actor, id, viewer))))

  private def cost(journal: LogJournal, run: Run, at: Int): Vector[LogSpan] =
    val spent = (run.first to journal.segmentEnd(at))
      .map(journal.supplySpent(_, run.actor)).sum
    if spent > 0 then Vector(LogSpan.Cost(spent, "Supply")) else Vector.empty
```

`PendingTree(Vector.empty, ...)` is safe: `PendingTree` has no invariant on `at`, and `CampaignSetup.kindOf`/`defenderOf` read only `answered`. Verify that by reading `CampaignAnswers` before relying on it; if it reads `at`, pass `Vector("0")`.

- [ ] **Step 5: Create the action lines with Travel, Finish Rest and Oathkeeper**

Create `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** The action line each procedure posts, at the event its facts complete
  * (spec, "Action lines"). A rule reads the event being formatted and the
  * run's events before it; a name alone may come from later in the same
  * segment. The subject is omitted: it is the actor's own action.
  */
private[gamelog] final class ActionLines(words: LogWords):
  import ActionLines._

  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val actor = run.actor
    val ops = journal.ops(at)
    run.procedure match
      case ActionRef.Travel => ops.collect {
        case OpStep(Move(Piece.Pawn(mover), _,
            PositionedLocation(Location.Site(to), _), _), _, _)
            if mover == actor =>
          action(Vector(Text("Travelled to "), words.site(to)))
      }
      case TriggeredProcedureRef.Oathkeeper => ops.collect {
        case OpStep(SetOathkeeper(holder), _, _) =>
          Posted.line(LogKind.Trigger, Vector(Text("Oathkeeper passed to "),
            holder.fold[LogSpan](Text("the bank"))(words.player)))
      }
      case PhaseTransitionRef.FinishRest => ops.collect {
        case OpStep(GainSupply(player, _), before, after) =>
          Posted.line(LogKind.Delta, Vector(Text(
            s"Increased supply from ${LogJournal.supply(before, player)} " +
              s"to ${LogJournal.supply(after, player)}")))
      }
      // The phase changes show in the lines around them.
      case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
        Vector.empty
      // Setup lines: the second slice.
      case TriggeredProcedureRef.Setup => Vector.empty
      // Filled by Tasks 5 to 7; Task 7 deletes this case, making the match
      // exhaustive over `ProcedureRef`.
      case _ => Vector.empty

private[gamelog] object ActionLines:
  def action(spans: Vector[LogSpan]): Posted = Posted.line(LogKind.Action, spans)

  def plural(count: Int, one: String, many: String): String =
    if count == 1 then one else many

  /** "3 favor", "1 secret", "2 secrets". */
  def resource(piece: Piece): String = piece match
    case Piece.Favor(count) => s"$count favor"
    case Piece.Secrets(count) =>
      s"$count ${plural(count, "secret", "secrets")}"
    case _ => ""
```

`viewer` is unused until Task 5; an unused parameter does not trip `-Wunused`. Do not add a `completing` local or a `WalkerCompleted` import yet: Task 5 adds both with their first reader, since an unused local or import fails the build.

- [ ] **Step 6: Wire them into the formatter**

In `GameLogFormatter.scala`, add imports `oathdigital.gameplay.powers.WalkerPowerCatalog` and add fields after `words`:

```scala
  private val starts = new StartLines(words, WalkerPowerCatalog.default(catalog)
    .powers.map(power => power.id -> power.resolution).toMap)
  private val actions = new ActionLines(words)
```

Replace the `case Some(run) =>` branch of `walker` with:

```scala
      case Some(run) =>
        val start = starts.start(journal, run, at, viewer)
        val begun = if start.isEmpty then run
          else run.copy(started = Some(journal.segmentEnd(at)))
        val posted = start.toVector ++
          starts.continued(journal, begun, at).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          turnHeadlines(journal, at)
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => Some(begun)
        (posted, next)
```

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogStartLineSuite oathdigital.application.gamelog.GameLogHeadlineSuite"`
Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala
git commit -m "feat(log): post start lines with modifiers and cost, and the first action lines"
```

---

### Task 5: Search, Play Facedown Adviser, Muster, Trade and Take Wealth

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (add `search`, `facedownAdviser`, `muster`, `trade`, `takeWealth`)
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`

**Interfaces:**
- Consumes: `ActionLines.action`, `plural`, `resource` (Task 4); `LogJournal.runOps`, `answers`, `readyBefore`, `readyAfter`, `pawnSite` (Task 3).
- Produces: `ActionLines.source(journal, run, at, decisionId, before, after, viewer): Vector[LogSpan]` and `ActionLines.worldCard(ref: DecisionOptionRef): Option[CardId]`, reused by Task 6.

- [ ] **Step 1: Add the scripts**

Append to `object LogScripts`, and widen the testkit import to `import oathdigital.testkit.{Situation, SituationDriver, Step}`:

```scala
  private def acting(name: String)(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository, Situation) =
    val (service, repository, driver) = journaled(name)
    val woken = Situation.wake(driver)
    (service, repository, woken.after(GameCommand.EndWake(active(woken))))

  private def start(situation: Situation, action: StartableRef,
      args: DecisionOptionRef*)(using munit.Location): Situation =
    situation.after(GameCommand.StartWalker(action,
      StartPayload(active(situation), Vector.empty, args.toVector)))

  /** Search of the world deck, every decision answered by default. */
  def search(using munit.Location): Script =
    val (service, _, act) = acting("search")
    start(act, ActionRef.Search, DecisionOptionRef.Button("search:world"))
    Script("search", service, active(act))

  /** The setup adviser played from its facedown slot. */
  def facedownAdviser(using munit.Location): Script =
    val (service, _, act) = acting("facedown-adviser")
    val actor = active(act)
    val held = act.ready.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) =>
          DecisionOptionRef.Denizen(id)
        case VisionState(id, Orientation.FaceDown) =>
          DecisionOptionRef.Vision(id)
      }.get
    start(act, ActionRef.PlayFacedownAdviser, held)
    Script("facedown-adviser", service, actor)

  /** The actor stands on a site with a denizen, travelling there first if
    * the pawn's own site has none. */
  private def besideDenizen(act: Situation)(using munit.Location): Situation =
    val actor = active(act)
    def hasDenizen(site: SiteId) = act.ready.game.current.map.sites(site)
      .denizens.exists(_.isInstanceOf[DenizenState])
    if hasDenizen(pawn(act, actor)) then act
    else start(act, ActionRef.Travel, DecisionOptionRef.Site(
      act.ready.game.current.map.inPlay.find(hasDenizen).get))

  def muster(using munit.Location): Script =
    val (service, _, act) = acting("muster")
    start(besideDenizen(act), ActionRef.Muster)
    Script("muster", service, active(act))

  /** Trade for secrets: it costs favor, which every player starts with. */
  def trade(using munit.Location): Script =
    val (service, _, act) = acting("trade")
    start(besideDenizen(act), ActionRef.Trade, DecisionOptionRef.Button("secret"))
    Script("trade", service, active(act))

  /** One favor arranged onto the actor's site, then taken in Wake. */
  def takeWealth(using munit.Location): Script =
    val (service, _, driver) = journaled("take-wealth")
    val woken = Situation.wake(driver)
    val actor = active(woken)
    val arranged = woken.after(Step.Arrange(Vector(Move(Piece.Favor(1),
      PositionedLocation(Location.FavorBank(Suit.all.head)),
      PositionedLocation(Location.Site(pawn(woken, actor)))))))
    start(arranged, ActionRef.TakeWealth, DecisionOptionRef.Button("favor"))
    Script("take-wealth", service, actor)
```

`Suit.all` exists (`FirstGameSetupFixture` uses it). If the favor bank the head suit names is empty on this board, pick the first suit with favor from `woken.ready.banks`.

- [ ] **Step 2: Write the failing tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogActionLineSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId]): Vector[String] =
    texts(format(script, viewer).filter(_.depth == 1))

  private def other(script: Script): PlayerId =
    script.players.find(_ != script.actor).get

  private val backs = Set("a Denizen", "a Vision", "a Relic")

  test("Search: the searcher reads the cards; everyone else reads their backs"):
    val script = search
    val mine = lines(script, Some(script.actor))
    val theirs = lines(script, Some(other(script)))
    assert(mine.exists(_.startsWith("Started Search")), mine)
    val drew = mine.find(_.startsWith("Drew ")).get
    assert(drew.contains(" from the World Deck and kept "), drew)
    val seen = theirs.find(_.startsWith("Drew ")).get
    assert(backs.exists(back => seen.contains(back.stripPrefix("a "))), seen)
    val entries = format(script, Some(other(script)))
    val drawn = entries.find(entry => text(entry).startsWith("Drew ")).get
    assert(!drawn.spans.exists(_.isInstanceOf[LogSpan.Card]), drawn.spans)

  test("Play Facedown Adviser: a start line, then where the card went"):
    val script = facedownAdviser
    val mine = lines(script, Some(script.actor))
    assert(mine.contains("Playing Facedown Adviser"), mine)
    assert(mine.exists(line => line.startsWith("Played ") ||
      line.startsWith("Discarded ")), mine)

  test("Muster: the start line waits for the cost, then names the card"):
    val script = muster
    val mine = lines(script, None)
    val start = mine.indexWhere(_.startsWith("Started Muster"))
    val mustered = mine.indexWhere(_.startsWith("Mustered "))
    assert(start >= 0 && mustered > start, mine)
    assert(mine(start).endsWith("−1 Supply"), mine(start))
    assert("^Mustered \\d+ warbands? with .+$".r.matches(mine(mustered)),
      mine(mustered))

  test("Trade: the start line waits for the cost, then says what it bought"):
    val script = trade
    val mine = lines(script, None)
    assert(mine.exists(_.startsWith("Started Trade")), mine)
    assert(mine.exists(line => "^Traded with .+ for (no|\\d+) secrets?$".r
      .matches(line)), mine)

  test("Take Wealth: one line naming the site, and no start line"):
    val script = takeWealth
    val mine = lines(script, None)
    assert(mine.exists(line => line.startsWith("Took 1 favor from ")), mine)
    assert(!mine.exists(_.startsWith("Started")), mine)
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: FAIL on every test's action-line assertion (the start lines for Search and Play Facedown Adviser already pass their part).

- [ ] **Step 4: Implement the five lines**

In `ActionLines.scala`, add imports:

```scala
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}
```

Add `val completing = journal.event(at).isInstanceOf[WalkerCompleted]` to `lines`, after `val ops = …`. Then add these cases to the match in `lines`, above `case _ => Vector.empty`:

```scala
      case ActionRef.Search => search(journal, run, at, viewer)
      case ActionRef.PlayFacedownAdviser =>
        playedAdviser(journal, run, at, viewer)
      case ActionRef.Muster => ops.collect {
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.WarbandBank(_), _),
            PositionedLocation(Location.PlayArea(taker), _), _), before, after)
            if taker == actor =>
          action(Vector(Text(
            s"Mustered $count ${plural(count, "warband", "warbands")} with ")) ++
            source(journal, run, at, MusterProcedure.decisionId, before, after,
              viewer))
      }
      case ActionRef.Trade =>
        val gained = ops.collect {
          case OpStep(Move(piece @ Piece.Favor(_),
              PositionedLocation(Location.FavorBank(_), _),
              PositionedLocation(Location.PlayArea(taker), _), _), before, after)
              if taker == actor =>
            traded(journal, run, at, resource(piece), before, after, viewer)
          case OpStep(Move(piece @ Piece.Secrets(_),
              PositionedLocation(Location.SharedBank, _),
              PositionedLocation(Location.PlayArea(taker), _), _), before, after)
              if taker == actor =>
            traded(journal, run, at, resource(piece), before, after, viewer)
        }
        // Trading favor for secrets with no matching adviser gains nothing:
        // the line is posted at completion instead.
        val gainedEarlier = (run.first until at).exists(index =>
          journal.ops(index).exists(isTradeGain(actor)))
        if gained.nonEmpty || !completing || gainedEarlier then gained
        else journal.readyBefore(at).toVector.map(ready =>
          traded(journal, run, at, "no secrets", ready, ready, viewer))
      case ActionRef.TakeWealth => ops.collect {
        case OpStep(Move(piece, PositionedLocation(Location.Site(site), _),
            PositionedLocation(Location.PlayArea(taker), _), _), _, _)
            if taker == actor && resource(piece).nonEmpty =>
          action(Vector(Text(s"Took ${resource(piece)} from "),
            words.site(site)))
      }
```

`gainedEarlier` keeps the completion event from posting a second Trade line after a gain. Add to `object ActionLines`:

```scala
  def isTradeGain(actor: PlayerId)(step: OpStep): Boolean = step match
    case OpStep(Move(Piece.Favor(_), PositionedLocation(Location.FavorBank(_), _),
        PositionedLocation(Location.PlayArea(taker), _), _), _, _) => taker == actor
    case OpStep(Move(Piece.Secrets(_), PositionedLocation(Location.SharedBank, _),
        PositionedLocation(Location.PlayArea(taker), _), _), _, _) => taker == actor
    case _ => false
```

Add these private members to `class ActionLines`:

```scala
  /** The card an economy action's source decision chose. */
  private def source(journal: LogJournal, run: Run, at: Int,
      decisionId: String, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    journal.answers(run, at).collectFirst {
      case Answered(`decisionId`, ChooseOneAnswer(DecisionOptionRef.Denizen(id)), _) =>
        id: CardId
      case Answered(`decisionId`, ChooseOneAnswer(DecisionOptionRef.Edifice(id)), _) =>
        id: CardId
    }.fold(Vector.empty[LogSpan])(card =>
      words.one(words.card(card, before, after, viewer)))

  private def traded(journal: LogJournal, run: Run, at: Int, bought: String,
      before: ReadyGame, after: ReadyGame, viewer: Option[PlayerId]): Posted =
    action(Vector(Text("Traded with ")) ++ source(journal, run, at,
      TradeProcedure.decisionId, before, after, viewer) ++
      Vector(Text(s" for $bought")))

  /** "Drew {cards} from the {source} and kept {cards}", at the keep/discard
    * answer, or at completion when only one card was drawn. Each card is
    * judged where the draw took it from and put it. */
  private def search(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.runOps(run, at).collectFirst {
      case (_, OpStep(Draw(_, cards, from, _), before, after)) =>
        (cards.map(card => card -> words.card(card, before, after, viewer)), from)
    }.toVector.flatMap { case (drawn, from) =>
      val kept: Option[Vector[CardId]] = journal.event(at) match
        case WalkerStepRecorded(_, ChoicePayload(SearchProcedure.cardDecisionId,
            PartitionAnswer(placements), _), _, _) =>
          Some(placements.collect {
            case DecisionPlacement(ref, SearchProcedure.keepKey) => ref
          }.flatMap(worldCard))
        case _: WalkerCompleted if drawn.size == 1 => Some(drawn.map(_._1))
        case _ => None
      kept.toVector.map(ids => action(Vector(Text("Drew ")) ++
        words.cards(drawn.map(_._2)) ++
        Vector(Text(s" from ${sourceName(from)} and kept ")) ++
        words.cards(ids.flatMap(id => drawn.find(_._1 == id).map(_._2)))))
    }

  /** Where the played card went, at the first batch after the placement
    * answer. */
  private def playedAdviser(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, _, _) =>
        val placed = (run.first until at).reverse.map(index =>
          index -> journal.event(index)).collectFirst {
          case (index, WalkerStepRecorded(_, ChoicePayload(id,
              ChooseOneAnswer(DecisionOptionRef.Button(key)), _), _, _))
              if id.startsWith(PlacePrefix) =>
            (index, id.stripPrefix(PlacePrefix), key)
        }.filter { case (index, _, _) =>
          !(index + 1 until at).exists(between => isDelta(journal.event(between)))
        }
        (for
          (_, subject, key) <- placed
          card <- subjectCard(subject)
          before <- journal.readyBefore(at)
          after <- journal.readyAfter(at)
        yield played(card, key, before, after, run.actor, viewer)).toVector
      case _ => Vector.empty

  private def played(card: CardId, key: String, before: ReadyGame,
      after: ReadyGame, actor: PlayerId, viewer: Option[PlayerId]): Posted =
    val named = words.one(words.card(card, before, after, viewer))
    key match
      case "discard" => action(Vector(Text("Discarded ")) ++ named)
      case "site" => action(Vector(Text("Played ")) ++ named ++
        LogJournal.pawnSite(before, actor).toVector.flatMap(site =>
          Vector(Text(" to "), words.site(site))))
      case _ => action(Vector(Text("Played ")) ++ named ++
        Vector(Text(" as an adviser")))
```

Add to `object ActionLines`:

```scala
  /** `CardPlayProcedure`'s placement decision id prefix; the card follows as
    * `{kind}.{id}`, the same spelling `WalkerDecisionProjector.subjectCards`
    * parses. */
  val PlacePrefix = "cardplay.place."

  def subjectCard(subject: String): Option[CardId] =
    subject.split("\\.", 2).toVector match
      case Vector("denizen", value) => Some(DenizenId(value))
      case Vector("vision", value) => Some(VisionId(value))
      case _ => None

  def worldCard(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Vision(id) => Some(id)
    case _ => None

  def isDelta(event: OathEvent): Boolean = event match
    case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, _, _) => true
    case _ => false

  def sourceName(location: Location): String = location match
    case Location.Deck(CardDeck.World) => "the World Deck"
    case Location.RegionalDiscard(region) => s"the ${region.key.capitalize} discard"
    case _ => "the deck"
```

`decisionId` is a method parameter, so the backticked stable-identifier pattern `` `decisionId` `` is legal. `SearchProcedure.cardDecisionId` and `SearchProcedure.keepKey` are qualified stable identifiers and need no backticks.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogActionLineSuite oathdigital.application.gamelog.GameLogStartLineSuite"`
Expected: all pass. If a script's start command is refused (for example Muster finds no source), read the refusal, fix the script's board, and record what changed in the task report.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala
git commit -m "feat(log): action lines for Search, facedown play, Muster, Trade and Take Wealth"
```

---

### Task 6: Recover, Forge, Campaign, Challenge and Place Banner Resource

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (add `recoverFailed`, `recoverSucceeded`, `forge`, `banners`)
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`

**Interfaces:**
- Consumes: Task 5's helpers; `StartLines.continued` (Task 4); `ParkedServiceFixture.recoverChronicle`, `recoverSites`, `failingDice`; `ForgeWalkerFixture.forgeReadyGame`, `blankCampaignDice`.

- [ ] **Step 1: Add the scripts**

Append to `object LogScripts` (add `import oathdigital.gameplay.actions.recover.RecoverProcedure` and `import oathdigital.model.DecisionAnswer.ChooseOneAnswer`):

```scala
  private def recovering(name: String, dice: CampaignDicePort)
      (using munit.Location): (GameApplicationService, Situation) =
    val (service, _, driver) = journaled(name, dice,
      ParkedServiceFixture.recoverSites)
    val woken = Situation.wake(driver,
      ParkedServiceFixture.recoverChronicle)
    (service, woken.after(GameCommand.EndWake(active(woken))))

  /** Dice that fail every Recover roll: continue once, then stop. */
  def recoverFailed(using munit.Location): Script =
    val (service, act) = recovering("recover-failed",
      ParkedServiceFixture.failingDice)
    val actor = active(act)
    def answer(key: String) = GameCommand.ResolveWalker(actor, TreeDecision(
      RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button(key))))
    act.parkedAfter(GameCommand.StartWalker(ActionRef.Recover,
        StartPayload(actor)))
      .parkedAfter(answer("continue"))
      .after(answer("stop"))
    Script("recover-failed", service, actor)

  /** Steady dice succeed at once; the relic decision is answered by default. */
  def recoverSucceeded(using munit.Location): Script =
    val (service, act) = recovering("recover-succeeded", steadyDice)
    start(act, ActionRef.Recover)
    Script("recover-succeeded", service, active(act))

  /** The Forge fixture's journal (a Conquest, Searches, rounds) and then the
    * Forge itself, which never parks at a single-resource site. */
  def forge(using munit.Location): Script =
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository,
      campaignDicePort = ForgeWalkerFixture.blankCampaignDice)
    val (ready, actor, _) = ForgeWalkerFixture.forgeReadyGame(service, "forge")
    service.handle("forge", ready.nextSequence, GameCommand.StartWalker(
      ActionRef.Forge, StartPayload(actor))).fold(
      error => munit.Assertions.fail(s"Forge refused: $error"), identity)
    Script("forge", service, actor)

  /** A Challenge for a banner from the bank, then resources placed on it. */
  def banners(using munit.Location): Script =
    val (service, _, act) = acting("banners")
    start(start(act, ActionRef.Challenge), ActionRef.PlaceBannerResource)
    Script("banners", service, active(act))
```

`Situation.wake(driver, chronicle)` takes the chronicle as its second parameter and keeps the fixture's orders.

- [ ] **Step 2: Write the failing tests**

Append to `GameLogActionLineSuite`:

```scala
  test("a failed Recover: start line, one continued spend, then the failure"):
    val script = recoverFailed
    val mine = lines(script, None)
    assert(mine.exists(line => line.startsWith("Started Recover") &&
      line.endsWith("−1 Supply")), mine)
    assertEquals(mine.count(_ == "Continued Recover −1 Supply"), 1, mine)
    assert(mine.last.startsWith("Failed to recover at "), mine)

  test("a successful Recover names the relic to the recoverer only"):
    val script = recoverSucceeded
    val mine = format(script, Some(script.actor))
      .find(entry => text(entry).startsWith("Recovered ")).get
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    val theirs = format(script, Some(other(script)))
      .find(entry => text(entry).startsWith("Recovered ")).get
    assert(!theirs.spans.exists(_.isInstanceOf[LogSpan.Card]) ||
      theirs.spans == mine.spans, theirs.spans)

  test("Forge: the payment, then the relic, named to the forger"):
    val script = forge
    val mine = lines(script, Some(script.actor))
    val start = mine.lastIndexWhere(_.startsWith("Started Forge"))
    assert(start >= 0, mine)
    assert(mine.drop(start).exists(_.startsWith("Placed ")), mine)
    val forged = format(script, Some(script.actor))
      .filter(entry => text(entry).startsWith("Forged ")).last
    assert(forged.spans.exists(_.isInstanceOf[LogSpan.Card]), forged.spans)
    assert(lines(script, Some(other(script))).contains("Forged a Relic"))

  test("Campaign: a start line naming kind and defender, and the winner"):
    val all = lines(forge, None)
    val start = all.indexWhere(_.startsWith("Started Campaign: "))
    val wins = all.indexWhere(line => line.endsWith(" wins!") ||
      line == "The bandits win!")
    assert(start >= 0 && wins > start, all)
    assert("^Started Campaign: (Raid|Conquest) against .+$".r
      .findPrefixOf(all(start)).nonEmpty, all(start))

  test("Challenge takes the banner from the bank; Place Banner Resource names the amount"):
    val all = lines(banners, None)
    assert(all.exists(_.startsWith("Started Challenge")), all)
    assert(all.exists(line => "^Took .+ from the bank with \\d+ (favor|secrets?)$"
      .r.matches(line)), all)
    assert(all.exists(line => "^Placed \\d+ (favor|secrets?) on .+$".r
      .matches(line)), all)
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: the five new tests fail on their action lines; Task 5's tests still pass.

- [ ] **Step 4: Implement the lines**

Add imports to `ActionLines.scala`: `import oathdigital.gameplay.actions.recover.RecoverProcedure`.

Add these cases above `case _ => Vector.empty`:

```scala
      case ActionRef.Recover =>
        val recovered = ops.collect {
          case OpStep(Move(Piece.Card(relic: RelicId),
              PositionedLocation(Location.Site(site), _),
              PositionedLocation(Location.PlayArea(taker), _), _), before, after)
              if taker == actor =>
            action(Vector(Text("Recovered ")) ++
              words.one(words.card(relic, before, after, viewer)) ++
              Vector(Text(" at "), words.site(site)))
        }
        // A Recover that succeeds with no relic to take posts nothing.
        val failed = if completing && stopped(journal, run, at) then
            journal.readyBefore(run.first).flatMap(LogJournal.pawnSite(_, actor))
              .toVector.map(site =>
                action(Vector(Text("Failed to recover at "), words.site(site))))
          else Vector.empty
        recovered ++ failed
      case ActionRef.Forge =>
        val paid = ops.collect {
          case OpStep(PayCost(_, Location.OnCard(card), cost, _, _, _),
              before, after) => (card, cost, before, after)
        }
        val favor = paid.collect { case (card, cost, before, after)
          if cost.favor > 0 => words.card(card, before, after, viewer) }
        val secret = paid.collect { case (card, cost, before, after)
          if cost.secret > 0 => words.card(card, before, after, viewer) }
        val halves = Vector(
          Option.when(favor.nonEmpty)(Text("favor on ") +: words.cards(favor)),
          Option.when(secret.nonEmpty)(Text("secrets on ") +: words.cards(secret))
        ).flatten
        val payment = if halves.isEmpty then Vector.empty
          else Vector(action(Text("Placed ") +: LogWords.join(halves)))
        val forged = ops.collect {
          case OpStep(Play(relic: RelicId,
              PositionedLocation(Location.Deck(CardDeck.Relic), _), _, _, _),
              before, after) =>
            action(Vector(Text("Forged ")) ++
              words.one(words.card(relic, before, after, viewer)))
        }
        payment ++ forged
      case ActionRef.Campaign => ops.collect {
        case OpStep(RecordCampaignResult(result), _, _) =>
          action(
            if result.attackerWins then
              Vector(words.player(result.attacker), Text(" wins!"))
            else result.defender match
              case CampaignDefender.Player(player) =>
                Vector(words.player(player), Text(" wins!"))
              case CampaignDefender.Bandits => Vector(Text("The bandits win!")))
      }
      case ActionRef.Challenge => ops.collect {
        case OpStep(Move(Piece.Banner(banner), PositionedLocation(from, _),
            PositionedLocation(Location.PlayArea(taker), _), _), _, _)
            if taker == actor =>
          val holder = from match
            case Location.PlayArea(player) => words.player(player)
            case _ => Text("the bank")
          action(Vector(Text("Took "), words.banner(banner), Text(" from "),
            holder) ++ paidOnto(journal, run, at, banner).toVector.map(paid =>
              Text(s" with $paid")))
      }
      case ActionRef.PlaceBannerResource => ops.collect {
        case OpStep(Move(piece, PositionedLocation(Location.PlayArea(giver), _),
            PositionedLocation(Location.OnBanner(banner), _), _), _, _)
            if giver == actor && resource(piece).nonEmpty =>
          action(Vector(Text(s"Placed ${resource(piece)} on "),
            words.banner(banner)))
      }
```

Add to `class ActionLines`:

```scala
  /** The last continue-or-stop answer was "stop". */
  private def stopped(journal: LogJournal, run: Run, at: Int): Boolean =
    journal.answers(run, at).reverse.collectFirst {
      case Answered(RecoverProcedure.choiceDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Button(key)), _) => key == "stop"
    }.contains(true)

  /** What the challenger put on the banner, earlier in the run. */
  private def paidOnto(journal: LogJournal, run: Run, at: Int, banner: Banner)
      : Option[String] =
    journal.runOps(run, at).collectFirst {
      case (_, OpStep(Move(piece, PositionedLocation(Location.PlayArea(giver), _),
          PositionedLocation(Location.OnBanner(onto), _), _), _, _))
          if giver == run.actor && onto == banner && resource(piece).nonEmpty =>
        resource(piece)
    }
```

`completing` is the local Task 5 added to `lines`.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala
git commit -m "feat(log): action lines for Recover, Forge, Campaign, Challenge and banner resources"
```

---

### Task 7: Negotiation and Use Power; the exhaustive match

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (add `negotiationDeclined`, `negotiationAgreed`, `usePower`, `all`)
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogExchangeSuite.scala`

**Interfaces:**
- Consumes: `NegotiationDeal.eligible`, `negotiatorsDecisionId`, `dealDecisionId`; `ParkedServiceFixture.silverTonguePark`; `LogWords.power`.
- Produces: `LogScripts.all(using munit.Location): Vector[Script]`, used by Task 8.

- [ ] **Step 1: Add the scripts**

Append to `object LogScripts` (add imports `oathdigital.gameplay.actions.negotiation.NegotiationDeal` and `oathdigital.model.DecisionAnswer.{AcceptDeal, DeclineDeal, ProposeTerms}`):

```scala
  /** The first two pawns share a site, so the first player can negotiate
    * with exactly one other: the negotiators decision is not asked. */
  private def negotiating(name: String)(using munit.Location)
      : (GameApplicationService, Situation, PlayerId, PlayerId) =
    val sites = FirstGameSetupFixture.sites
    val (service, _, driver) = journaled(name,
      spread = Vector(sites(0), sites(0)) ++ sites.drop(1))
    val woken = Situation.wake(driver)
    val actor = active(woken)
    val act = woken.after(GameCommand.EndWake(actor))
    val partner = act.ready.game.current.players.find(player =>
      player.player != actor && player.pawnSite == Some(pawn(act, actor)))
      .get.player
    (service, act.parkedAfter(GameCommand.StartWalker(ActionRef.Negotiation,
      StartPayload(actor))), actor, partner)

  private def deal(by: PlayerId, answer: DecisionAnswer): GameCommand =
    GameCommand.ResolveWalker(by, TreeDecision(NegotiationDeal.dealDecisionId,
      answer))

  def negotiationDeclined(using munit.Location): Script =
    val (service, parked, actor, partner) = negotiating("negotiation-declined")
    parked.after(deal(partner, DeclineDeal))
    Script("negotiation-declined", service, actor)

  def negotiationAgreed(using munit.Location): Script =
    val (service, parked, actor, partner) = negotiating("negotiation-agreed")
    parked
      .parkedAfter(deal(actor, ProposeTerms(NegotiationTerms(Vector(
        NegotiationTransfer(partner, 1, Vector.empty))))))
      .parkedAfter(deal(partner, AcceptDeal))
      .after(deal(actor, AcceptDeal))
    Script("negotiation-agreed", service, actor)

  /** Silver Tongue used in Rest, its bank choice answered by default. */
  def usePower(using munit.Location): Script =
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = steadyDice)
    val (parked, actor, _) = ParkedServiceFixture.silverTonguePark(service,
      repository, "use-power")
    Situation(parked.state, Vector.empty, parked.nextSequence,
      Situation.journaled(service, catalog, repository, "use-power")).after()
    Script("use-power", service, actor)

  /** Every script, for the properties that hold over all of them. */
  def all(using munit.Location): Vector[Script] = Vector(woken, round,
    oathkeeper, search, facedownAdviser, muster, trade, takeWealth,
    recoverFailed, recoverSucceeded, forge, banners, negotiationDeclined,
    negotiationAgreed, usePower)
```

`partner` is found by site, not by seat, so it resolves whoever placed the second pawn. If Setup refuses a second pawn on an occupied site, arrange the partner's pawn instead: an `Step.Arrange(Vector(Move(Piece.Pawn(partner), …)))` placed immediately before `GameCommand.EndWake(actor)`, whose run posts nothing.

`TrustedSeat` (used in Task 10) is `oathdigital.application.TrustedSeat`.

- [ ] **Step 2: Write the failing tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogExchangeSuite.scala`:

```scala
package oathdigital.application.gamelog

import LogScripts._

class GameLogExchangeSuite extends munit.FunSuite:
  private def lines(script: Script): Vector[String] =
    texts(format(script, None).filter(_.depth == 1))

  test("a declined negotiation names who ended it, and nothing else"):
    val script = negotiationDeclined
    val all = lines(script)
    assertEquals(all.count(_.startsWith("Negotiation ended by ")), 1, all)
    assert(!all.exists(_.startsWith("Negotiated with ")), all)
    assert(!all.exists(_.startsWith("Started")), all)

  test("an agreed negotiation names the other negotiators once"):
    val script = negotiationAgreed
    val all = lines(script)
    val negotiated = all.filter(_.startsWith("Negotiated with "))
    assertEquals(negotiated.size, 1, all)
    val partner = script.players.filterNot(_ == script.actor).map(name)
    assert(partner.exists(negotiated.head.contains), negotiated.head)

  test("a used power is named by its source card, once"):
    val all = lines(usePower)
    assertEquals(all.count(_.startsWith("Used ")), 1, all)
    assert(all.exists(_ == "Used Silver Tongue") ||
      all.exists(_.startsWith("Used a ")), all)
```

Check the card name `Silver Tongue` against `catalog.denizens` for id `92`; use the catalog name if it differs.

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogExchangeSuite"`
Expected: all three fail on their lines.

- [ ] **Step 4: Implement the lines and make the match exhaustive**

Add imports to `ActionLines.scala`: `oathdigital.gameplay.actions.negotiation.NegotiationDeal`, `oathdigital.gameplay.walker.WalkerParked`, and extend the `DecisionAnswer` import to `{ChooseManyAnswer, ChooseOneAnswer, DeclineDeal, PartitionAnswer}`.

Replace `case _ => Vector.empty` with:

```scala
      case ActionRef.Negotiation => negotiation(journal, run, at)
      case ActionRef.UsePower(power) =>
        usedPower(journal, run, at, power, completing, viewer)
```

The match is now exhaustive over `ProcedureRef`; the compiler must accept it with no fallback. Add to `class ActionLines`:

```scala
  /** "Negotiation ended by …" at the decline; otherwise "Negotiated with …"
    * at the settlement batch, or at completion when the deal settled
    * nothing. */
  private def negotiation(journal: LogJournal, run: Run, at: Int)
      : Vector[Posted] =
    def negotiated = Vector(action(Text("Negotiated with ") +: LogWords.join(
      negotiators(journal, run, at).map(player => Vector(words.player(player))))))
    val declined = journal.answers(run, at).exists {
      case Answered(NegotiationDeal.dealDecisionId, DeclineDeal, _) => true
      case _ => false
    }
    val settledEarlier = (run.first until at).exists(index =>
      isDelta(journal.event(index)))
    journal.event(at) match
      case WalkerStepRecorded(_, ChoicePayload(NegotiationDeal.dealDecisionId,
          DeclineDeal, by), _, _) =>
        Vector(action(Vector(Text("Negotiation ended by "), words.player(by))))
      case event if isDelta(event) && !settledEarlier => negotiated
      case _: WalkerCompleted if !declined && !settledEarlier => negotiated
      case _ => Vector.empty

  /** The negotiators decision's answer, or the one eligible player when it
    * was not asked. */
  private def negotiators(journal: LogJournal, run: Run, at: Int)
      : Vector[PlayerId] =
    journal.answers(run, at).collectFirst {
      case Answered(NegotiationDeal.negotiatorsDecisionId,
          ChooseManyAnswer(refs), _) =>
        refs.collect { case DecisionOptionRef.Player(id) => id }
    }.getOrElse(journal.readyBefore(run.first).fold(Vector.empty[PlayerId])(
      NegotiationDeal.eligible(_, run.actor)))

  /** "Used {card}" at the first step recording one of the power's own
    * effects: a step that is not only the payment onto its card or to the
    * bank, and not only the use record. At completion if it recorded none. */
  private def usedPower(journal: LogJournal, run: Run, at: Int, power: PowerId,
      completing: Boolean, viewer: Option[PlayerId]): Vector[Posted] =
    val postedEarlier = (run.first until at).exists(effect(journal, run, _))
    if postedEarlier || !(effect(journal, run, at) || completing) then
      Vector.empty
    else Vector(action(Text("Used ") +: powerSource(journal, run, at, power,
      viewer)))

  private def effect(journal: LogJournal, run: Run, index: Int): Boolean =
    journal.event(index) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, ops, _) =>
        !ops.forall {
          case RecordPowerUse(_) => true
          case Move(Piece.Favor(_) | Piece.Secrets(_),
              PositionedLocation(Location.PlayArea(payer), _),
              PositionedLocation(Location.OnCard(_) | Location.SharedBank, _),
              _) => payer == run.actor
          case _ => false
        }
      case _ => false

  /** The source the player named when starting (kept on every park), else
    * the use record's source, else the card the power is printed on. */
  private def powerSource(journal: LogJournal, run: Run, at: Int,
      power: PowerId, viewer: Option[PlayerId]): Vector[LogSpan] =
    val through = (run.first to journal.segmentEnd(at)).toVector
    val ready = journal.readyBefore(run.first)
    val started = through.map(journal.event).collectFirst {
      case parked: WalkerParked => parked.startArgs
    }.flatMap(_.headOption).flatMap(ref => ready.flatMap(state =>
      named(ref, state, viewer)))
    val used = through.flatMap(journal.ops).collectFirst {
      case OpStep(RecordPowerUse(PowerUseRef(_, source, _)), _, _) => source
    }.flatMap(source => ready.flatMap(state => source match
      case PowerSourceRef.Card(id) =>
        Some(words.one(words.card(id, state, state, viewer)))
      case PowerSourceRef.Banner(banner) => Some(Vector(words.banner(banner)))
      case PowerSourceRef.Site(site) => Some(Vector(words.site(site)))))
    started.orElse(used).orElse(ready.map(state =>
      words.power(state, run.actor, power, viewer)))
      .getOrElse(Vector(Text(power.value)))

  private def named(ref: DecisionOptionRef, ready: ReadyGame,
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] = ref match
    case DecisionOptionRef.Denizen(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Relic(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Vision(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Edifice(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Banner(banner) => Some(Vector(words.banner(banner)))
    case _ => None
```

`NegotiationDeal.dealDecisionId` in a pattern is a qualified stable identifier. `NegotiationDeal.eligible` is public (`NegotiationDeal.scala:33`).

- [ ] **Step 5: Run the formatter suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, and `ActionLines.scala` compiles with no wildcard case.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogExchangeSuite.scala
git commit -m "feat(log): lines for Negotiation and used powers; every procedure has a decision"
```

---

### Task 8: Properties over every script, and the six-player smoke read

**Files:**
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`
- Temporary, never committed: `src/test/scala/oathdigital/application/gamelog/SixPlayerSmokeRead.scala`

**Interfaces:**
- Consumes: `LogScripts.all`, `LogScripts.formatter`, `LogScripts.presentation`.

- [ ] **Step 1: Write the property tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked}
import oathdigital.model._
import LogScripts._

/** What holds for every script (spec, "Testing"). The scripts are built once
  * per run of this suite. */
class GameLogPropertiesSuite extends munit.FunSuite:
  private lazy val scripts = LogScripts.all

  private def viewers(script: Script): Vector[Option[PlayerId]] =
    None +: script.players.map(Some(_))

  test("prefix stability: every segment boundary formats to the same entries"):
    scripts.foreach { script =>
      val steps = script.history.steps
      val boundaries = steps.indices
        .filter(index => closes(steps(index).event.event))
        .map(_ + 1) :+ steps.size
      viewers(script).foreach { viewer =>
        val whole = formatter.format(steps, viewer)
        boundaries.foreach { end =>
          assertEquals(formatter.format(steps.take(end), viewer),
            whole.takeWhile(_.sequence < end),
            s"${script.name} for $viewer at $end")
        }
      }
    }

  test("every viewer receives the same entries with the same keys"):
    scripts.foreach { script =>
      val shapes = viewers(script).map(viewer => formatter
        .format(script.history.steps, viewer)
        .map(entry => (entry.sequence, entry.ordinal, entry.kind, entry.depth)))
      shapes.tail.foreach(shape => assertEquals(shape, shapes.head,
        script.name))
    }

  test("no card span names a card its viewer cannot identify"):
    scripts.foreach { script =>
      val steps = script.history.steps
      script.players.foreach { player =>
        formatter.format(steps, Some(player)).foreach { entry =>
          val step = steps(entry.sequence.toInt)
          entry.spans.collect { case card: LogSpan.Card => card }
            .foreach { card =>
              val id = cardId(card.id, step)
              val known = Vector(step.before, step.after).exists {
                case OathState.Ready(ready) => id.exists(value =>
                  presentation.identifiesAt(ready, Some(player), value))
                case _ => false
              }
              assert(known, s"${script.name}: $player may not read " +
                s"${card.name} at ${entry.sequence}")
            }
        }
      }
    }

  test("scan ends on the state load reconstructs"):
    scripts.foreach { script =>
      assertEquals(script.history.steps.last.after,
        script.service.load(script.name).toOption.flatten.get.state,
        script.name)
    }

  test("every ProcedureRef key the formatter handles appears in some script"):
    val keys = scripts.flatMap(_.history.steps.map(_.event.event).collect {
      case completed: WalkerCompleted => completed.procedure.key
    }).toSet
    val expected = Set("travel", "search", "play-facedown-adviser", "muster",
      "trade", "take-wealth", "recover", "forge", "challenge",
      "place-banner-resource", "campaign", "negotiation", "end-wake",
      "begin-rest", "finish-rest", "oathkeeper", "setup")
    assertEquals(expected -- keys, Set.empty[String])
    assert(keys.exists(_.startsWith("use-power:")), keys)

  /** A segment ends at a park or a completion: one command's append. */
  private def closes(event: OathEvent): Boolean = event match
    case _: WalkerParked | _: WalkerCompleted => true
    case _ => false

  /** The card a span's id names, found among the cards either side of its
    * step. */
  private def cardId(value: String,
      step: oathdigital.engine.ReplayStep[OathState, OathEvent])
      : Option[CardId] =
    Vector(step.before, step.after).collectFirst {
      case OathState.Ready(ready) => CardIndex.from(ready.game).toOption
        .flatMap(_.ids.find(_.value == value))
    }.flatten
```

This leak check judges a card on its event's states, not each operation's. A batch whose card is identifiable only between two of its operations would fail it spuriously; if that happens, check the operation states before loosening the test, and say which script and entry.

- [ ] **Step 2: Run the properties**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogPropertiesSuite"`
Expected: all pass. A prefix-stability failure means a line read past its segment: fix the rule, not the test.

- [ ] **Step 3: Commit**

```bash
git add src/test/scala/oathdigital/application/gamelog/GameLogPropertiesSuite.scala
git commit -m "test(log): prefix stability, viewer agreement and leak checks over every script"
```

- [ ] **Step 4: The six-player smoke read (read only, never committed)**

The completed six-player game `manual-1790205747051-112090` (1053 events) was exported, one envelope per line, to
`/private/tmp/claude-501/-Users-roman-projects-oathdigital/0c9af783-65ae-419e-882c-bcff64ce79a7/scratchpad/sixplayer_all.txt`.
If that file is missing, skip this step and say so in the report; never read `var/oathdigital` directly.

Create `src/test/scala/oathdigital/application/gamelog/SixPlayerSmokeRead.scala`:

```scala
package oathdigital.application.gamelog

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PlayerId

/** Not a test: formats the exported six-player game once for a person to
  * read. Never committed. */
class SixPlayerSmokeRead extends munit.FunSuite:
  test("format the six-player game"):
    val gameId = "manual-1790205747051-112090"
    val input = sys.env("OATH_SMOKE_JOURNAL")
    val output = sys.env("OATH_SMOKE_OUT")
    val repository = new InMemoryEventStreamRepository
    repository.seed(gameId, Files.readAllLines(Paths.get(input)).asScala
      .toVector.filter(_.trim.nonEmpty))
    val service = new GameApplicationService(catalog, repository)
    service.history(gameId) match
      case Right(Some(history)) =>
        val viewer = LogScripts.presentation
        val formatter = new GameLogFormatter(catalog, viewer)
        val first = Some(PlayerId("Red"))
        val body = Vector(first, None).flatMap { seat =>
          s"== viewer $seat ==" +: formatter.format(history.steps, seat)
            .map(entry => s"${"  " * entry.depth}${LogScripts.text(entry)}")
        }
        Files.writeString(Paths.get(output), body.mkString("\n"))
      case other => fail(s"the exported game does not load: $other")
```

Run:

```bash
OATH_SMOKE_JOURNAL=/private/tmp/claude-501/-Users-roman-projects-oathdigital/0c9af783-65ae-419e-882c-bcff64ce79a7/scratchpad/sixplayer_all.txt OATH_SMOKE_OUT=/private/tmp/claude-501/-Users-roman-projects-oathdigital/0c9af783-65ae-419e-882c-bcff64ce79a7/scratchpad/sixplayer_log.txt ./sbtw "testOnly oathdigital.application.gamelog.SixPlayerSmokeRead"
```

If the game no longer replays (a card implemented since it was played re-checks `IgnoredRulesRecorded`), report the failure and move on: that is exactly why no stored game is a fixture. Otherwise read the output, and report anything that reads wrong (a thin line, a class name, a missing turn, a named hidden card for the non-owner). Player ids in that game are colours; if `Red` is not seated, use the first seat the output shows.

Delete the file afterwards and confirm with `git status --short` that nothing under `src/test` is left untracked.

---

### Task 9: Wire types and codec

**Files:**
- Create: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`
- Create: `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala`
- Create: `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`

**Interfaces:**
- Produces: `LogSpanWire(kind: String, text: String, id: Option[String] = None, value: Option[Int] = None, unit: Option[String] = None)`, `LogEntryWire(sequence: Long, ordinal: Int, kind: String, depth: Int, spans: Vector[LogSpanWire])`, `LogPageWire(gameId: String, after: Long, nextSequence: Long, entries: Vector[LogEntryWire])` in `oathdigital.protocol.projection`.
- Produces: `object LogPageCodec { def encode(value: LogPageWire): String; def decode(json: String): Either[ProtocolDecodeFailure, LogPageWire] }`.

- [ ] **Step 1: Write the failing codec test**

Create `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`:

```scala
package oathdigital.protocol

import oathdigital.protocol.projection._

class LogPageCodecSuite extends munit.FunSuite:
  private val page = LogPageWire("game", 4L, 9L, Vector(
    LogEntryWire(4L, 0, "turn", 0, Vector(
      LogSpanWire("player", "Red", id = Some("red")),
      LogSpanWire("text", "'s turn"))),
    LogEntryWire(6L, 1, "action", 1, Vector(
      LogSpanWire("text", "Started Travel"),
      LogSpanWire("cost", "−2 Supply", value = Some(2),
        unit = Some("Supply"))))))

  test("a page round-trips"):
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(page)), Right(page))

  test("a text span carries no id, value or unit on the wire"):
    val json = ujson.read(LogPageCodec.encode(page))
    val text = json("entries")(0)("spans")(1).obj
    assertEquals(text.keySet.toSet, Set("kind", "text"))

  test("an unexpected field is rejected at its path"):
    val json = LogPageCodec.encode(page).replace("\"ordinal\":0",
      "\"ordinal\":0,\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].extra"))

  test("malformed JSON is a decode failure, not an exception"):
    assert(LogPageCodec.decode("{").isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite"`
Expected: compilation failure, `Not found: LogPageWire`.

- [ ] **Step 3: Create the wire types**

Create `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`:

```scala
package oathdigital.protocol.projection

/** One span of a log entry on the wire. `kind` is `text`, `player`, `card`,
  * `site`, `amount` or `cost`; `text` is always present, so a client that
  * ignores kinds still shows a sentence. A card shown by its back is a
  * `text` span: no field carries a hidden card's id. */
final case class LogSpanWire(kind: String, text: String,
    id: Option[String] = None, value: Option[Int] = None,
    unit: Option[String] = None)

/** One log entry; `(sequence, ordinal)` is its stable key. */
final case class LogEntryWire(sequence: Long, ordinal: Int, kind: String,
    depth: Int, spans: Vector[LogSpanWire])

/** Every entry with `sequence >= after`, formatted for one seat, and the
  * cursor to ask with next time. */
final case class LogPageWire(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntryWire])
```

- [ ] **Step 4: Create the codec**

Create `shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala`:

```scala
package oathdigital.protocol.projection

import scala.util.control.NonFatal

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure.MalformedJson
import ProjectionCodecSupport._

/** The log page's wire form. Kinds are not checked against a list: a client
  * shows an unknown span kind as its text, so a newer server's span does
  * not break an older page. */
object LogPageCodec:
  private val PageFields = Set("gameId", "after", "nextSequence", "entries")
  private val EntryFields = Set("sequence", "ordinal", "kind", "depth", "spans")
  private val SpanFields = Set("kind", "text", "id", "value", "unit")

  def encode(value: LogPageWire): String = ujson.write(ujson.Obj(
    "gameId" -> value.gameId,
    "after" -> ujson.Num(value.after.toDouble),
    "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "entries" -> encoded(value.entries)(encodeEntry)))

  def decode(json: String): Either[ProtocolDecodeFailure, LogPageWire] =
    try decodePage(ujson.read(json), "$")
    catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  private def encodeEntry(entry: LogEntryWire): ujson.Value = ujson.Obj(
    "sequence" -> ujson.Num(entry.sequence.toDouble),
    "ordinal" -> entry.ordinal, "kind" -> entry.kind, "depth" -> entry.depth,
    "spans" -> encoded(entry.spans)(encodeSpan))

  private def encodeSpan(span: LogSpanWire): ujson.Value = ujson.Obj.from(
    Vector[(String, ujson.Value)]("kind" -> ujson.Str(span.kind),
      "text" -> ujson.Str(span.text)) ++
      span.id.map(id => "id" -> (ujson.Str(id): ujson.Value)) ++
      span.value.map(amount => "value" -> (ujson.Num(amount): ujson.Value)) ++
      span.unit.map(unit => "unit" -> (ujson.Str(unit): ujson.Value)))

  private def decodePage(raw: ujson.Value, path: String): Result[LogPageWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, PageFields, path)
      gameId <- string(value, "gameId", path)
      after <- long(value, "after", path)
      next <- long(value, "nextSequence", path)
      rawEntries <- array(value, "entries", path)
      entries <- traverse(rawEntries, s"$path.entries")(decodeEntry)
    yield LogPageWire(gameId, after, next, entries)

  private def decodeEntry(raw: ujson.Value, path: String): Result[LogEntryWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, EntryFields, path)
      sequence <- long(value, "sequence", path)
      ordinal <- int(value, "ordinal", path)
      kind <- string(value, "kind", path)
      depth <- int(value, "depth", path)
      rawSpans <- array(value, "spans", path)
      spans <- traverse(rawSpans, s"$path.spans")(decodeSpan)
    yield LogEntryWire(sequence, ordinal, kind, depth, spans)

  private def decodeSpan(raw: ujson.Value, path: String): Result[LogSpanWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, SpanFields, path)
      kind <- string(value, "kind", path)
      text <- string(value, "text", path)
      id <- optionalAbsent(value, "id", path)(string)
      amount <- optionalAbsent(value, "value", path)(int)
      unit <- optionalAbsent(value, "unit", path)(string)
    yield LogSpanWire(kind, text, id, amount, unit)
```

If overload resolution picks the wrong `string` or `int` for `optionalAbsent`, pass `(raw, at) => string(raw, at)` explicitly.

- [ ] **Step 4b: Run on both platforms**

Run: `./sbtw "testOnly oathdigital.protocol.LogPageCodecSuite" "frontend/testOnly oathdigital.protocol.LogPageCodecSuite"`
Expected: 4 tests pass on each.

- [ ] **Step 5: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/LogPageCodec.scala \
  shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala
git commit -m "feat(protocol): the game log page wire and its codec"
```

---

### Task 10: The log page on both seat routes

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala`
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala` (a field and a method on `GameProjector`)
- Modify: `src/main/scala/oathdigital/server/TrustedGameGateway.scala`
- Modify: `src/main/scala/oathdigital/server/TrustedSeatRoutes.scala` (the `pathPrefix("api")` block)
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala` (`GameServerGateway` and the `parameter("playerId")` block)
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogRouteSuite.scala`

**Interfaces:**
- Consumes: `GameLogFormatter` (Task 3), `LogPageWire` and `LogPageCodec` (Task 9), `GameApplicationService.history` (Task 1).
- Produces: `GameProjector.logPage(gameId: String, history: GameHistory, after: Long, viewer: PlayerId): Option[LogPageWire]` (`None` when `after > nextSequence`).
- Produces: `TrustedGameGateway.log(gameId: String, seat: TrustedSeat, after: Long): Either[TrustedSeatFailure, LogPageWire]`; `GameServerGateway.log(gameId: String, player: PlayerId, after: Long): Either[GameApplicationError, Option[LogPageWire]]`.
- Routes: `GET /games/{gameId}/api/log/{after}` (cookie) and `GET /api/dev/first-games/{gameId}/log/{after}?playerId=...`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/application/gamelog/GameLogRouteSuite.scala`:

```scala
package oathdigital.application.gamelog

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PlayerId
import oathdigital.protocol.projection.LogPageCodec
import oathdigital.server.{DevelopmentRoutes, GameServerGateway, TrustedGameGateway,
  TrustedSeatFailure}

class GameLogRouteSuite extends munit.FunSuite:
  test("the projector pages the log after a sequence, and refuses past the end"):
    val script = LogScripts.round
    val history = script.history
    val projector = new GameProjector(catalog)
    val whole = projector.logPage("round", history, 0L, script.actor).get
    assertEquals(whole.nextSequence, history.nextSequence)
    assert(whole.entries.nonEmpty)
    val after = whole.entries(whole.entries.size / 2).sequence
    val tail = projector.logPage("round", history, after, script.actor).get
    assertEquals(tail.entries, whole.entries.filter(_.sequence >= after))
    assertEquals(tail.after, after)
    assertEquals(projector.logPage("round", history, history.nextSequence,
      script.actor).map(_.entries), Some(Vector.empty))
    assertEquals(projector.logPage("round", history, history.nextSequence + 1,
      script.actor), None)

  test("the trusted gateway pages for the seat and refuses another game"):
    val script = LogScripts.woken
    val gateway = new TrustedGameGateway(script.service,
      new GameProjector(catalog))
    val seat = TrustedSeat(script.name, script.actor.value)
    assert(gateway.log(script.name, seat, 0L).isRight)
    assertEquals(gateway.log("other", seat, 0L),
      Left(TrustedSeatFailure.Forbidden))
    assertEquals(gateway.log(script.name, seat, 10_000L),
      Left(TrustedSeatFailure.InvalidIntent))

  test("the development route binds playerId, checks the cursor, and leaves raw events alone"):
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "game-log-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val script = LogScripts.woken
    val gateway = new GameServerGateway(script.service,
      new GameProjector(catalog))
    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(
      DevelopmentRoutes.route(gateway, blocking)), 10.seconds)
    val base = s"http://127.0.0.1:${binding.localAddress.getPort}"
    val client = HttpClient.newHttpClient()
    def get(path: String) = client.send(HttpRequest.newBuilder(
      URI.create(base + path)).GET().build(), JavaResponse.BodyHandlers.ofString())
    try
      val page = get(s"/api/dev/first-games/woken/log/0?playerId=${script.actor.value}")
      assertEquals(page.statusCode(), 200, page.body())
      val decoded = LogPageCodec.decode(page.body()).toOption.get
      assertEquals(decoded.gameId, "woken")
      assertEquals(decoded.entries.head.spans.head.text, "Setup")
      assertEquals(get(s"/api/dev/first-games/woken/log/-1?playerId=p1")
        .statusCode(), 400)
      assertEquals(get(s"/api/dev/first-games/woken/log/99999?playerId=p1")
        .statusCode(), 400)
      assertEquals(get(s"/api/dev/first-games/woken/log/x?playerId=p1")
        .statusCode(), 400)
      val events = get("/api/dev/first-games/woken/events?limit=5")
      assertEquals(events.statusCode(), 200)
      assert(events.body().contains("\"warning\""))
    finally
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
```

Also add one test to `src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala`, beside "creation returns ordered public links", using its `withServer`, `create` and `send` helpers:

```scala
  test("the log route pages for the seat's cookie and checks the cursor"):
    withServer() { (base, _) =>
      val client = HttpClient.newHttpClient()
      val created = create(client, base, "log-game")
      val code = URI.create(created.seats(1).url).getPath.stripPrefix("/s/")
      val cookie = Some(s"oath_seat=$code")
      val page = send(client, base, "/games/log-game/api/log/0", cookie = cookie)
      assertEquals(page.statusCode(), 200, page.body())
      assertEquals(page.headers().firstValue("Cache-Control").orElse(""), "no-store")
      val decoded = oathdigital.protocol.projection.LogPageCodec
        .decode(page.body()).toOption.get
      assertEquals(decoded.after, 0L)
      assertEquals(send(client, base, "/games/log-game/api/log/0").statusCode(), 403)
      assertEquals(send(client, base, "/games/log-game/api/log/-1",
        cookie = cookie).statusCode(), 400)
      assertEquals(send(client, base,
        s"/games/log-game/api/log/${decoded.nextSequence + 1}",
        cookie = cookie).statusCode(), 400)
      assertEquals(send(client, base, "/games/log-game/api/log/0?x=1",
        cookie = cookie).statusCode(), 400)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogRouteSuite oathdigital.server.TrustedSeatRoutesSuite"`
Expected: compilation failure, `value logPage is not a member of GameProjector`.

- [ ] **Step 3: Create the log projector**

Create `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{GameHistory, GamePresentationProjector}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.PlayerId
import oathdigital.protocol.projection.{LogEntryWire, LogPageWire, LogSpanWire}

/** The log page one seat reads: the whole prefix formatted for that seat,
  * then every entry at or after `after` (spec, "Routes" and "Cost"). */
private[application] final class GameLogProjector(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val formatter = new GameLogFormatter(catalog, presentation)

  def page(gameId: String, history: GameHistory, after: Long,
      viewer: PlayerId): Option[LogPageWire] =
    Option.when(after >= 0 && after <= history.nextSequence)(LogPageWire(
      gameId, after, history.nextSequence,
      formatter.format(history.steps, Some(viewer))
        .filter(_.sequence >= after).map(GameLogProjector.wire)))

private[application] object GameLogProjector:
  def wire(entry: LogEntry): LogEntryWire = LogEntryWire(entry.sequence,
    entry.ordinal, entry.kind.key, entry.depth, entry.spans.map(span))

  def span(value: LogSpan): LogSpanWire = value match
    case LogSpan.Text(text) => LogSpanWire("text", text)
    case LogSpan.Player(id, name) => LogSpanWire("player", name, id = Some(id))
    case LogSpan.Card(id, name) => LogSpanWire("card", name, id = Some(id))
    case LogSpan.Site(id, name) => LogSpanWire("site", name, id = Some(id))
    case amount @ LogSpan.Amount(count, unit) =>
      LogSpanWire("amount", amount.text, value = Some(count), unit = Some(unit))
    case cost @ LogSpan.Cost(count, unit) =>
      LogSpanWire("cost", cost.text, value = Some(count), unit = Some(unit))
```

In `GameProjection.scala`, add to `GameProjector` after the `pendingProjector` field:

```scala
  private val logs = new gamelog.GameLogProjector(catalog, presentation)

  /** The log page after `after` for `viewer`; `None` when `after` is past
    * the journal's end. */
  def logPage(gameId: String, history: GameHistory, after: Long,
      viewer: PlayerId): Option[LogPageWire] =
    logs.page(gameId, history, after, viewer)
```

`LogPageWire` comes in through the existing `import oathdigital.protocol.projection._`.

- [ ] **Step 4: Add the gateway methods**

In `TrustedGameGateway.scala`, add after `load`:

```scala
  /** The seat's log page. A cursor past the journal's end is an invalid
    * request, which the route reports as malformed. */
  def log(gameId: String, seat: TrustedSeat, after: Long)
      : Either[TrustedSeatFailure, LogPageWire] = for
    player <- actor(gameId, seat)
    loaded <- service.history(gameId).left.map(Application.apply)
    history <- loaded.toRight(Application(GameApplicationError.StreamNotFound(gameId)))
    page <- projector.logPage(gameId, history, after, player).toRight(InvalidIntent)
  yield page
```

and extend its projection import to `import oathdigital.protocol.projection.{GameProjection, LogPageWire}`.

In `GameRoutes.scala`, add to `GameServerGateway` after `load`:

```scala
  /** `None` when `after` is past the journal's end. */
  def log(gameId: String, requestingPlayer: PlayerId, after: Long)
      : Either[GameApplicationError, Option[LogPageWire]] =
    service.history(gameId).flatMap:
      case Some(history) =>
        Right(projector.logPage(gameId, history, after, requestingPlayer))
      case None => Left(GameApplicationError.StreamNotFound(gameId))
```

Import `oathdigital.protocol.projection.{LogPageCodec, LogPageWire}` in `GameRoutes.scala`.

- [ ] **Step 5: Add the routes**

In `TrustedSeatRoutes.scala`, inside `pathPrefix("api"):`, insert between the `path("commands") { … }` block and `~ path("preview"):`:

```scala
            } ~ path("log" / Segment) { raw =>
              get { async {
                raw.toLongOption.filter(_ >= 0).fold(malformed)(after =>
                  authenticate(request, gameId).flatMap(gateway.log(gameId, _, after))
                    .fold(publicError, page => json(StatusCodes.OK,
                      oathdigital.protocol.projection.LogPageCodec.encode(page))))
              } }
```

keeping the closing brace structure of the chain intact (each sibling is joined with `~`).

In `GameRoutes.scala`, inside `parameter("playerId") { … case Right((validGameId, validPlayerId)) =>`, add a sibling before `path("preview")`:

```scala
              path("log" / Segment) { raw =>
                get:
                  raw.toLongOption.filter(_ >= 0) match
                    case None => complete(jsonResponse(StatusCodes.BadRequest,
                      "malformed-request", "$.after: expected a journal sequence"))
                    case Some(after) => completeLog(gateway.log(validGameId,
                      PlayerId(validPlayerId), after))
              } ~
```

and add to `GameRoutes`:

```scala
  private def completeLog(
      operation: => Either[GameApplicationError, Option[LogPageWire]]
  ): Route = onComplete(Future(operation)(using blockingExecutionContext)):
    case Success(Right(Some(page))) => complete(HttpResponse(StatusCodes.OK,
      entity = HttpEntity(ContentTypes.`application/json`,
        LogPageCodec.encode(page))))
    case Success(Right(None)) => complete(jsonResponse(StatusCodes.BadRequest,
      "malformed-request", "$.after: past the end of the journal"))
    case Success(Left(error)) =>
      val (status, code, message, _) = publicError(error)
      complete(jsonResponse(status, code, message))
    case Failure(error) =>
      logger.error("Unhandled game-log route failure", error)
      complete(jsonResponse(StatusCodes.InternalServerError, "internal-error",
        "the server could not complete the request"))
```

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogRouteSuite oathdigital.server.TrustedSeatRoutesSuite oathdigital.server.GameRoutesSuite"`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala \
  src/main/scala/oathdigital/application/GameProjection.scala \
  src/main/scala/oathdigital/server/TrustedGameGateway.scala \
  src/main/scala/oathdigital/server/TrustedSeatRoutes.scala \
  src/main/scala/oathdigital/server/GameRoutes.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogRouteSuite.scala \
  src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala
git commit -m "feat(server): serve the game log page on the trusted and development routes"
```

---

### Task 11: The client asks for the log

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameClient.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala` (`FakeClient` only)
- Modify: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala` (a `quietLog` wrapper on every `Main.start`)

**Interfaces:**
- Produces: `GameClient.loadLog(gameId: String, selectedPlayerId: String, after: Long): Future[Either[GameClientFailure, LogPageWire]]`; `GameJson.logResponse(response: TransportResponse): Either[GameClientFailure, LogPageWire]`.
- Produces (test): `FakeClient.logs: Vector[(String, String, Long)]` and `FakeClient.answerLog(result)`.

- [ ] **Step 1: Write the failing client tests**

Append to `HttpGameClientSuite` (before the `StubTransport` class):

```scala
  private val logPage = """{"gameId":"g","after":3,"nextSequence":5,"entries":[{"sequence":3,"ordinal":0,"kind":"turn","depth":0,"spans":[{"kind":"player","text":"Red","id":"red"},{"kind":"text","text":"'s turn"}]}]}"""

  test("the trusted client reads the log after a cursor on the seat's path"):
    val transport = new StubTransport(Vector(Right(TransportResponse(200, logPage))))
    new TrustedHttpGameClient(transport).loadLog("my game", "ignored", 3L).map { result =>
      assertEquals(transport.requests.map(r => r._1 -> r._2).toVector,
        Vector("GET" -> "/games/my%20game/api/log/3"))
      assertEquals(result.map(_.entries.map(_.sequence)), Right(Vector(3L)))
    }

  test("the development client reads the log for the selected seat"):
    val transport = new StubTransport(Vector(Right(TransportResponse(200, logPage))))
    new HttpGameClient(transport).loadLog("g", "red exile", 0L).map { result =>
      assertEquals(transport.requests.map(_._2).toVector,
        Vector("/api/dev/first-games/g/log/0?playerId=red%20exile"))
      assert(result.isRight)
    }

  test("a log failure is a client failure, never a thrown decode"):
    val transport = new StubTransport(Vector(
      Right(TransportResponse(400, """{"error":"malformed-request","message":"bad"}""")),
      Right(TransportResponse(200, "{"))))
    val client = new TrustedHttpGameClient(transport)
    client.loadLog("g", "", 9L).flatMap { refused =>
      assertEquals(refused, Left(GameClientFailure.HttpFailure(400,
        "malformed-request", "bad")))
      client.loadLog("g", "", 0L)
    }.map(garbled => assert(garbled.isLeft))
```

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.HttpGameClientSuite"`
Expected: compilation failure, `value loadLog is not a member`.

- [ ] **Step 3: Implement the client method**

In `GameClient.scala`, extend the projection import to `import oathdigital.protocol.projection.{GameProjection, GameProjectionCodec, LogPageCodec, LogPageWire}`. Add to `trait GameClient`:

```scala
  /** Every log entry at or after `after`, formatted for this seat. */
  def loadLog(gameId: String, selectedPlayerId: String, after: Long)
      : Future[Either[GameClientFailure, LogPageWire]]
```

Add to `HttpGameClient`:

```scala
  override def loadLog(gameId: String, selectedPlayerId: String, after: Long) =
    transport.request("GET", s"/api/dev/first-games/${encode(gameId)}/log/$after" +
      "?playerId=" + encode(selectedPlayerId), None)
      .map(_.flatMap(GameJson.logResponse))(
        using scala.scalajs.concurrent.JSExecutionContext.queue)
```

Add to `TrustedHttpGameClient`:

```scala
  override def loadLog(gameId: String, selectedPlayerId: String, after: Long) =
    transport.request("GET", api(gameId) + s"/log/$after", None)
      .map(_.flatMap(GameJson.logResponse))(
        using scala.scalajs.concurrent.JSExecutionContext.queue)
```

Add to `object GameJson`:

```scala
  def logResponse(response: TransportResponse)
      : Either[GameClientFailure, LogPageWire] =
    if response.status >= 200 && response.status < 300 then
      LogPageCodec.decode(response.body).left.map(error =>
        GameClientFailure.DecodeFailure(error.path, error.message))
    else Left(responseFailure(response))
```

- [ ] **Step 4: Teach the fakes the new method**

In `TableSessionSuite.scala`, add to `FakeClient` (and add `LogPageWire` to the imports: `import oathdigital.protocol.projection.LogPageWire`):

```scala
    var logs = Vector.empty[(String, String, Long)]
    private var pendingLogs = Vector.empty[Promise[Answer[LogPageWire]]]
    def loadLog(gameId: String, selectedPlayerId: String, after: Long) =
      logs :+= ((gameId, selectedPlayerId, after))
      val promise = Promise[Answer[LogPageWire]]()
      pendingLogs :+= promise
      promise.future
    def answerLog(result: Answer[LogPageWire]): Unit =
      val promise = pendingLogs.head
      pendingLogs = pendingLogs.tail
      promise.success(result)
```

In `ServerModeUiSuite.scala`, add near the top of the class:

```scala
  /** Answers every log request with an empty page and passes every other
    * request to `inner`, so a test that counts, lists or queues requests sees
    * only the requests it is about. */
  private def quietLog(inner: JsonTransport): JsonTransport = new JsonTransport:
    def request(method: String, url: String, body: Option[String])
        : Future[Either[GameClientFailure, TransportResponse]] =
      if url.contains("/log/") then Future.successful(Right(TransportResponse(200,
        """{"gameId":"quiet","after":0,"nextSequence":0,"entries":[]}""")))
      else inner.request(method, url, body)
```

Then change every one of the 16 `Main.start(…, transport)` calls so its last argument is `quietLog(transport)` (or `quietLog(hostTransport(...))` where the transport is built inline). Check with `grep -n "Main.start(" frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala` that none is left unwrapped.

- [ ] **Step 5: Run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: every test passes. Record the count as the frontend baseline plus 3.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/GameClient.scala \
  frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala
git commit -m "feat(frontend): read the game log page on both clients"
```

---

### Task 12: The session keeps the log

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala` (`TableView` only)
- Modify: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`

**Interfaces:**
- Consumes: `GameClient.loadLog` (Task 11).
- Produces: `TableView.viewedLog: Vector[LogEntryWire]`.

- [ ] **Step 1: Write the failing session tests**

Append to `TableSessionSuite` (add `import oathdigital.protocol.projection.{LogEntryWire, LogPageWire, LogSpanWire}`):

```scala
  private def page(after: Long, next: Long): LogPageWire =
    LogPageWire("g", after, next, (after until next).toVector.map(sequence =>
      LogEntryWire(sequence, 0, "action", 1,
        Vector(LogSpanWire("text", s"line $sequence")))))

  test("a displayed position fetches the log from the start and appends it"):
    displayed(3).flatMap { fixture =>
      assertEquals(fixture.client.logs, Vector(("g", "red", 0L)))
      fixture.client.answerLog(Right(page(0, 3)))
      settle().map { _ =>
        assertEquals(fixture.session.viewedLog.map(_.sequence),
          Vector(0L, 1L, 2L))
      }
    }

  test("the log is asked for again only when the position advances"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Right(page(0, 3)))
      settle().flatMap { _ =>
        fixture.clock.fire()
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.flatMap { _ =>
        assertEquals(fixture.client.logs.size, 1)
        fixture.clock.fire()
        fixture.client.answerLoad(Right(snapshot(5)))
        settle()
      }.map { _ =>
        assertEquals(fixture.client.logs.last, ("g", "red", 3L))
      }
    }

  test("a failed log request leaves the log and is retried at the next advance"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Left(GameClientFailure.NetworkFailure("down")))
      settle().flatMap { _ =>
        assertEquals(fixture.session.viewedLog, Vector.empty)
        assertEquals(fixture.session.shownFailure, None)
        fixture.clock.fire()
        fixture.client.answerLoad(Right(snapshot(4)))
        settle()
      }.map { _ =>
        assertEquals(fixture.client.logs, Vector(("g", "red", 0L),
          ("g", "red", 0L)))
      }
    }

  test("a session change resets the log and ignores the old answer"):
    displayed(3).flatMap { fixture =>
      fixture.session.loadSession("h", "blue")
      fixture.client.answerLog(Right(page(0, 3)))
      settle().map { _ =>
        assertEquals(fixture.session.viewedLog, Vector.empty)
      }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite"`
Expected: compilation failure, `value viewedLog is not a member`.

- [ ] **Step 3: Keep the log in the session**

In `TableScreen.scala`, add to `trait TableView` after `viewedRawEvents`:

```scala
  /** The game log as fetched for this seat, oldest first. */
  def viewedLog: Vector[oathdigital.protocol.projection.LogEntryWire]
```

In `TableSession.scala`, add `import oathdigital.protocol.projection.LogEntryWire` and these fields after `rawHistorySequence`:

```scala
  private var log = Vector.empty[LogEntryWire]
  // The cursor the next request asks with: the log's `nextSequence`.
  private var logSequence = 0L
  // Bumped on every reset, so an answer to a request from before it lands
  // on nothing.
  private var logEpoch = 0
  private var logLoading = false

  private def resetLog(): Unit =
    log = Vector.empty
    logSequence = 0L
    logEpoch += 1
    logLoading = false

  /** Asks for the entries after the log's cursor when `target`, a displayed
    * position, is past it. One request at a time; a failure changes nothing
    * and the next advancing position asks again (spec, "Fetching"). */
  private def fetchLog(target: Long): Unit =
    if !logLoading && target > logSequence then
      logLoading = true
      val epoch = logEpoch
      client.loadLog(gameId, selectedPlayer, logSequence).foreach { result =>
        if epoch == logEpoch then
          logLoading = false
          result.foreach { page =>
            val advanced = page.nextSequence > logSequence
            val last = log.lastOption.map(entry => entry.sequence -> entry.ordinal)
            log ++= page.entries.filter(entry => last.forall(key =>
              Ordering[(Long, Int)].gt(entry.sequence -> entry.ordinal, key)))
            logSequence = math.max(logSequence, page.nextSequence)
            redraw()
            if advanced then projection.foreach(current =>
              fetchLog(current.nextSequence))
          }
      }
```

In `store`, in the `ProjectionRoute.Display` branch, add `fetchLog(displayed.nextSequence)` right after `polling.resume(coordinator.capture)`.

Call `resetLog()`:
- in the trusted `case _ =>` branch of `store` (the seat changed), after `projection = None`;
- in the `ProjectionRoute.ReloadForActivePlayer` branch, after `drafts = SessionDrafts.empty`;
- in `accept`, inside `if TableSession.needsSeatLink(trusted, error) then`, after `projection = None`;
- in `loadExisting`, after `rawHistorySequence = None`.

Add to the TableView section: `def viewedLog: Vector[LogEntryWire] = log`.

- [ ] **Step 4: Run the tests**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite oathdigital.frontend.ServerModeUiSuite"`
Expected: all pass, including the existing redraw-count assertions (a pending log request draws nothing).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TableSession.scala \
  frontend/src/main/scala/oathdigital/frontend/TableScreen.scala \
  frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala
git commit -m "feat(frontend): the table session fetches and keeps the game log"
```

---

### Task 13: The Log pane

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:31` (placeholder) and a new `showLog`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala` (`render`, last line)
- Modify: `frontend/styles.css:845-847`
- Create: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`

**Interfaces:**
- Consumes: `TableView.viewedLog` (Task 12), `PlayerColorCss.of`.
- Produces: `GameLogPane(content: dom.html.Element)` with `show(sessionKey: String, entries: Vector[LogEntryWire], colors: Map[String, String]): Unit`; `GameTableShell.showLog(sessionKey, entries, colors)`.

- [ ] **Step 1: Write the failing pane tests**

Create `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}

class GameLogPaneSuite extends munit.FunSuite:
  private def entry(sequence: Long, kind: String, depth: Int,
      spans: LogSpanWire*) = LogEntryWire(sequence, 0, kind, depth, spans.toVector)

  private val setup = entry(0, "round", 0, LogSpanWire("text", "Setup"))
  private val turn = entry(3, "turn", 0,
    LogSpanWire("player", "Red", id = Some("red")), LogSpanWire("text", "'s turn"))
  private val travel = entry(5, "action", 1, LogSpanWire("text", "Started Travel"),
    LogSpanWire("cost", "−2 Supply", value = Some(2), unit = Some("Supply")))

  /** A content box jsdom gives a size: `height` tall, `scrolled` from the top,
    * holding `total` pixels. */
  private def box(total: Int, height: Int, scrolled: Double): dom.html.Element =
    val content = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    def define(name: String, value: js.Any, writable: Boolean) =
      js.Object.defineProperty(content, name, js.Dynamic.literal(value = value,
        writable = writable, configurable = true)
        .asInstanceOf[js.PropertyDescriptor])
    define("scrollHeight", total, writable = false)
    define("clientHeight", height, writable = false)
    define("scrollTop", scrolled, writable = true)
    content

  private def items(content: dom.Element): Vector[dom.Element] =
    content.querySelectorAll("li").toVector.map(_.asInstanceOf[dom.Element])

  test("an empty log shows the Setup headline"):
    val content = box(0, 0, 0)
    new GameLogPane(content).show("g|red", Vector.empty, Map.empty)
    assertEquals(items(content).map(_.textContent), Vector("Setup"))

  test("headlines and lines carry their depth and kind; a player takes the seat colour"):
    val content = box(0, 0, 0)
    new GameLogPane(content).show("g|red", Vector(setup, turn, travel),
      Map("red" -> "player-red"))
    val shown = items(content)
    assertEquals(shown.map(_.className), Vector(
      "log-entry log-headline log-round", "log-entry log-headline log-turn",
      "log-entry log-line log-action"))
    assertEquals(shown(1).querySelector(".log-player").className,
      "log-player player-ref player-red")
    assertEquals(shown(2).querySelector(".log-cost").textContent,
      "−2 Supply")
    assertEquals(shown(2).getAttribute("title"), "5.0")
    val list = content.querySelector("ol")
    assertEquals(list.getAttribute("role"), "log")
    assertEquals(list.getAttribute("aria-live"), "polite")

  test("new entries append; a reader at the end stays at the end"):
    val content = box(500, 100, 400)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    val first = items(content).head
    content.scrollTop = 400
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    assert(items(content).head eq first)
    assertEquals(items(content).size, 3)
    assertEquals(content.scrollTop, 500.0)

  test("a reader scrolled up is not moved by new entries"):
    val content = box(500, 100, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    content.scrollTop = 40
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    assertEquals(content.scrollTop, 40.0)

  test("a different session redraws the whole list"):
    val content = box(0, 0, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    pane.show("g|blue", Vector(setup), Map.empty)
    assertEquals(items(content).map(_.textContent), Vector("Setup"))
```

- [ ] **Step 2: Run to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite"`
Expected: compilation failure, `Not found: GameLogPane`.

- [ ] **Step 3: Create the pane**

Create `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}
import ServerUiSupport._

/** The Log pane's list (spec, "Pane"). Entries only ever arrive at the end,
  * so a render appends what is new rather than replacing the list, and the
  * view stays at the end when the reader was already there.
  */
private[frontend] final class GameLogPane(content: dom.html.Element):
  private val list = element("ol", "game-log")
  list.setAttribute("role", "log")
  list.setAttribute("aria-live", "polite")
  content.appendChild(list)
  private var key = Option.empty[String]
  private var shown = Vector.empty[LogEntryWire]
  list.appendChild(GameLogPane.placeholder)

  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String]): Unit =
    val appending = key.contains(sessionKey) && shown.nonEmpty &&
      entries.size >= shown.size && entries(shown.size - 1) == shown.last
    if !(appending && entries.size == shown.size) then
      val following = !appending || GameLogPane.atEnd(content)
      val fresh = if appending then entries.drop(shown.size) else
        while list.firstChild != null do list.removeChild(list.firstChild)
        entries
      if fresh.isEmpty then list.appendChild(GameLogPane.placeholder)
      else fresh.foreach(entry => list.appendChild(GameLogPane.item(entry, colors)))
      if following then content.scrollTop = content.scrollHeight.toDouble
    key = Some(sessionKey)
    shown = entries

private[frontend] object GameLogPane:
  /** Within a few pixels of the end counts as at the end. */
  private val Slack = 4.0

  def atEnd(content: dom.html.Element): Boolean =
    content.scrollHeight - content.scrollTop - content.clientHeight <= Slack

  /** Before the first entry arrives the log is the game's first headline. */
  def placeholder: dom.Element =
    text("li", "log-entry log-headline log-round", "Setup")

  def item(entry: LogEntryWire, colors: Map[String, String]): dom.Element =
    val depth = if entry.depth == 0 then "log-headline" else "log-line"
    val node = element("li", s"log-entry $depth log-${entry.kind}")
    node.setAttribute("title", s"${entry.sequence}.${entry.ordinal}")
    entry.spans.foreach(span => node.appendChild(spanNode(span, colors)))
    node

  private def spanNode(span: LogSpanWire, colors: Map[String, String])
      : dom.Node = span.kind match
    case "player" => text("span", "log-player player-ref " +
      span.id.flatMap(colors.get).getOrElse(PlayerColorCss.neutral), span.text)
    case "card" | "site" | "amount" | "cost" =>
      text("span", s"log-${span.kind}", span.text)
    case _ => dom.document.createTextNode(span.text)
```

- [ ] **Step 4: Wire it into the shell and the screen**

In `GameTableShell.scala`, replace line 31 (`log.content.appendChild(text("p", "log-placeholder", …))`) with:

```scala
  private val logPane = new GameLogPane(log.content)
```

and add after `update`:

```scala
  /** The log is not rebuilt with the other panes: it only grows. */
  def showLog(sessionKey: String,
      entries: Vector[oathdigital.protocol.projection.LogEntryWire],
      colors: Map[String, String]): Unit =
    logPane.show(sessionKey, entries, colors)
```

In `TableScreen.scala`, add after the final `shell.update(…)` line of `render`:

```scala
    shell.showLog(s"${view.viewedGameId}|${view.viewedPlayerId}",
      view.viewedLog, projection.fold(Map.empty[String, String])(value =>
        value.players.map(player =>
          player.playerId -> PlayerColorCss.of(player.color)).toMap))
```

- [ ] **Step 5: Style it**

In `frontend/styles.css`, replace lines 845-847 (`.log-placeholder …` and the `.pane-log .pane-content` stripe) with:

```css
/* The game log: 11px Ink at the pane floor, lines one gutter in and dimmed,
   round and victory headlines in the replay green, a spent cost set apart
   by a lighter treatment rather than a separator. */
.game-log { list-style: none; margin: 0; padding: 0; font-size: 11px;
  line-height: 1.45; color: #f5ecd7; }
.game-log .log-headline { font-weight: 800; margin-top: 6px; }
.game-log .log-headline:first-child { margin-top: 0; }
.game-log .log-round, .game-log .log-victory { color: #9ed59e; }
.game-log .log-line { padding-left: 12px; color: #a99f8c; }
.game-log .log-card, .game-log .log-site { color: #f5ecd7; }
.game-log .log-cost { margin-left: 0.5em; opacity: 0.72; font-weight: 400; }
```

- [ ] **Step 6: Run the frontend suite and link**

Run: `./sbtw "frontend/test" "frontend/fastLinkJS"`
Expected: every test passes; the link succeeds.

- [ ] **Step 7: Verify in the browser**

Start the development server with the Browser pane (`preview_start` by name from `.claude/launch.json`; if no entry exists, add one for the development server the README names). Create a game, play one Travel, and check: the Log pane shows "Setup", "Round 1", the turn headline in the seat colour, "Started Travel" with its lighter cost, and "Travelled to …"; no console errors; the placeholder text is gone. Take a screenshot for the task report.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala \
  frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala \
  frontend/src/main/scala/oathdigital/frontend/TableScreen.scala \
  frontend/styles.css \
  frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala
git commit -m "feat(frontend): the Log pane appends the game log and sticks to the bottom"
```

---

### Task 14: Final gates

**Files:**
- Modify: `docs/ROADMAP.md` (mark the first slice of "Player-facing action history" delivered, in the style the roadmap already uses for delivered slices)

- [ ] **Step 1: Run every gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: all pass; counts are the baselines plus this plan's new tests.
Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 2: Check coverage**

Run: `./sbtw clean coverage test coverageReport frontend/test`
Expected: statement coverage at or above the floor of 86.8. If it drops below, add tests for the uncovered formatter branches rather than lowering the floor.

- [ ] **Step 3: Update the roadmap and commit**

Read `docs/ROADMAP.md`'s "Player-facing action history" entry, note Slice 1 as delivered with the date, and leave Slices 2 and 3 open.

```bash
git add docs/ROADMAP.md
git commit -m "docs: mark the game log's first slice delivered"
```

---

## Self-review notes

- **Spec coverage (Slice 1):** `scan` (Task 1); formatter with the `OathEvent` and `ProcedureRef` matches and segment look-ahead (Tasks 3, 4, 7); round, turn and victory headlines (Task 3); start lines with modifiers and cost, "Continued Recover" (Task 4); one action line per row of the Action lines table, including Campaign's start line and "{winner} wins!" and Negotiation's first line (Tasks 4 to 7); `playerLabel` and the `BeginTurn` doc fix (Task 2); the visibility rule with card backs (Tasks 2, 3); wire types and codec (Task 9); both routes (Task 10); the pane, sticking to the bottom, and the fetch rule (Tasks 11 to 13); prefix-stability and leak tests (Task 8); the six-player smoke read (Task 8).
- **Deliberately not here:** victory headlines have no script (no scripted game reaches a victory cheaply); their wording is a direct mapping in `GameLogFormatter` and Slice 2's goldens pin it. Setup lines, detail lines, knowledge, negotiation settlement lines, the rest of Campaign and the campaign panel's removal are Slice 2; the overlay, divider, New chip and sticky headline are Slice 3.
- **Plan-level decisions the spec left open:** Campaign's start line is anchored at the first event where `CampaignSetup.kindOf` and `defenderOf` both answer on the state after it with the run's answers so far, falling back to the recorded result. A Trade for secrets that gains nothing posts "Traded with {card} for no secrets" at completion. A used power's "power's own effect" is any recorded batch that is not only payment onto a card or to the bank and not only the use record.
