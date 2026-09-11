package oathdigital.gameplay

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ProcedureWalker,
  WalkerCompleted, WalkerOutcome, WalkerParked, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model.DecisionAnswer.{RecoverChoice, RecoverChoiceAnswer,
  RecoverRelicAnswer}
import oathdigital.model._

/** Task 8 step 3: the replay-drift check.
  *
  * Spec J1 makes the *recorded* operations in the walker event stream the
  * replay authority: production replay (`OathRules.evolve` ->
  * `ProcedureWalker.applyRecorded`) applies those recorded ops and never
  * re-derives or re-walks the action tree. This suite is the dev/test-only
  * counterpart that earns that trust for the slice's Recover corpus: for each
  * scripted walk it independently reconstructs state PURELY by replaying the
  * recorded events through the production `ProcedureWalker.applyRecorded`
  * path (the same function `OathRules.evolve` dispatches every walker event
  * to — never a re-run of a command), rebuilds a FRESH tree from that
  * replayed state via `RecoverProcedure.build`/`rebuild` (exactly what
  * `OathRules.buildWalker` does on every live command — the tree is never
  * cached across commands in production either), and re-invokes the same
  * walker entry point with the same resume input (the same roll faces or the
  * same decision answer the live walk used). It then asserts the operations
  * that independent re-derivation produces equal the operations the live walk
  * actually recorded.
  *
  * This is a real drift assertion, not a restatement of the two existing
  * suites:
  *  - `RecoverProcedureSuite` pins one live walk's tree/ops/state against
  *    hand-written expected values; it never reconstructs state by replay nor
  *    re-derives a second, independent set of ops to diff against the first.
  *  - `GameApplicationServiceSuite` ("walker Recover persists every park and
  *    replays to legacy-equivalent state", :69-174) proves *final replayed
  *    state* equals *final live state* after a full app-service replay, and
  *    separately pins the recorded ops as literals. It never compares
  *    recorded ops against an ops derivation obtained by re-walking a
  *    freshly-rebuilt tree over replay-reconstructed state — the exact
  *    scenario where the walker's derivation could, in principle, depend on
  *    information a pure event replay does not reconstruct (the concrete risk
  *    the SDD ledger's carry-in contract #10 and Task 6 review flagged:
  *    "applyRecorded... never derives a tree, so walker delta events have a
  *    weaker replay tamper posture"). If the walker's tree-building or
  *    walking ever became sensitive to such information, THIS is the
  *    assertion that would fail — nothing else in the suite makes it.
  *
  * Per Branch/BuildOps design (spec + walker doc), the comparison is over
  * RECORDED operations (`WalkerStepRecorded.ops`, flattened across the whole
  * walk), never over the declared tree: `Branch` composites are resolved at
  * walk time and are statically childless (flattening the declared tree would
  * see empty `Branch`es, not the operations the walker actually selects and
  * runs), and an empty-batch `BuildOps` records no step at all.
  *
  * Dev/test-only: this file is never imported by production code, and the
  * only production code it reuses is `ProcedureWalker.applyRecorded` itself
  * (already the sole replay mechanism) — it never wires a second, walker-
  * rerunning replay path into the application.
  *
  * Corpus: the three unpowered scripted walks below cover every scenario
  * shape `RecoverProcedureSuite` covers EXCEPT its two rejection-only
  * scenarios (insufficient supply on Continue, and resolving the relic
  * decision with a relic that isn't facedown at the site). Both of those end
  * in `Left(violation)` with no `WalkerOutcome` and no journal at all, so
  * there is nothing to fold into a "live" track or replay through
  * `applyRecorded` — a drift comparison needs two independently
  * reconstructed op sequences to diff, and a rejected command produces
  * neither.
  *
  * A fourth walk (Task 10 step 3) adds Catacombs: the first drift case where
  * a POWER changed the tree. `CatacombsContributionSuite` already pins that
  * one live walk's recorded ops/state against hand-written expectations, the
  * same relationship `RecoverProcedureSuite` has to the unpowered corpus
  * above; this suite's job is not to re-pin those values but to prove that
  * independently re-deriving them from replayed state -- with the fold
  * re-run by a freshly rebuilt tree -- reproduces exactly what the live walk
  * recorded. If `CatacombsContribution`'s `Transform` (or the gather/fold
  * machinery it runs through) ever depended on anything beyond the pure
  * `(PowerCtx, ops)` it is handed -- e.g. mutable state, iteration order that
  * isn't reconstructed by a pure replay -- the second fold would insert its
  * placement-and-cost step at a different node, with different operations,
  * or not at all, and this comparison (unlike the unpowered ones) would be
  * the first thing in the suite to catch it.
  */
/** One command in a scripted Recover walk (top-level so pattern matches on it
  * carry no per-instance outer reference).
  */
private sealed trait Resume
private case object StartWalk extends Resume
private final case class RollResume(faces: Vector[DieFace]) extends Resume
private final case class AnswerResume(answer: Answered) extends Resume

class WalkerReplayDriftSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer {
  private val setup = new FirstGameSetupRules(catalog)

  /** The power source BOTH tracks walk with. It is a named value rather than
    * a default on the walker's entry points precisely so this suite cannot
    * drift into checking an unpowered walk while production walks with
    * powers: when a power registers on this slice (Task 5), it is added here
    * and both the live track and the re-derivation see it.
    */
  private val walkerPowers: WalkerPowers = WalkerPowers.empty

  /** Task 10 step 3: the same production selection `OathRules.startWalker`
    * performs (`WalkerPowers.selected` over the real `WalkerPowerCatalog`),
    * pinned to Catacombs being player-selected for this walk. Both tracks in
    * `assertNoDrift` receive this SAME value (never re-selected per track),
    * matching the invariant the suite-level doc states: "the powered walk
    * must gather the same contributions on both tracks, or you are testing
    * your test harness rather than the engine."
    */
  private val catacombsPowers: WalkerPowers = {
    val selected = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector(CatacombsContribution.id))
    assert(selected.powers.nonEmpty,
      "fixture catalog must declare the Catacombs card for this drift case")
    selected
  }

  private def recoverable: (ReadyGame, PlayerId, SiteId, RelicState) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val candidates = base.game.current.map.inPlay.filter(siteId =>
      RecoverRules.difficulty(catalog, siteId).exists(d => d > 0 && d <= 4))
    assert(candidates.nonEmpty,
      "fixture needs an in-play Recover site with difficulty <= 4")
    val siteId = candidates.minBy(site => RecoverRules.difficulty(catalog, site).get)
    val relic = RelicState(base.game.current.commonCards.relicDeck.head,
      Orientation.FaceDown, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(relics = Vector(relic))
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        turn = base.game.current.turn.copy(phase = Phase.Act),
        commonCards = base.game.current.commonCards.copy(
          relicDeck = base.game.current.commonCards.relicDeck.tail),
        players = base.game.current.players.map(p =>
          if (p.player == active.player) moved else p),
        map = base.game.current.map.copy(sites =
          base.game.current.map.sites.updated(siteId, site)))))
    (ready, active.player, siteId, relic)
  }

  private val lowRoll: Vector[DieFace] =
    Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)
  private val highRoll: Vector[DieFace] =
    Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)

  private def runResume(resume: Resume, state: ReadyGame, tree: Operation,
      pending: Option[PendingTree], powers: WalkerPowers): WalkerOutcome = {
    val result = resume match {
      case StartWalk => ProcedureWalker.advance(state, tree, None, powers)
      case RollResume(faces) => ProcedureWalker.roll(state, tree,
        pending.getOrElse(fail("roll() resume requires a pending park")), faces,
        powers)
      case AnswerResume(answer) => ProcedureWalker.resolve(state, tree,
        pending.getOrElse(fail("resolve() resume requires a pending park")),
        answer, powers)
    }
    result.fold(violation => fail(s"walker step $resume failed: $violation"),
      identity)
  }

  private def opsOf(outcome: WalkerOutcome): Vector[CoreOperation] = {
    val events = outcome match {
      case WalkerOutcome.Parked(_, evs) => evs
      case WalkerOutcome.Finished(_, evs) => evs
    }
    events.collect { case s: WalkerStepRecorded => s.ops }.flatten
  }

  /** Folds one command's recorded steps into the "live" state track via the
    * shared `WalkerRecordedOpsReducer` (also used by `RecoverProcedureSuite`
    * to fold a live walk's own steps forward). This is scaffolding, not a
    * second replay mechanism: the drift assertion's independence comes from
    * comparing this track against `replayCommand` below, which reconstructs
    * state purely through the production `ProcedureWalker.applyRecorded`
    * dispatch. Sharing the reducer only removes a duplicate reimplementation
    * of "fold recorded ops back into ReadyGame state" between two suites
    * that both needed it for unrelated reasons — it does not make the two
    * tracks the same mechanism.
    */
  private def foldLive(state: ReadyGame,
      events: Vector[OathEvent]): ReadyGame =
    foldRecordedOps(state, events, "live-track replay of recorded ops failed")

  /** Reconstructs the "replay" state track purely through the production
    * replay function: the command's `WalkerStepRecorded` steps, followed by
    * the durable `WalkerParked`/`WalkerCompleted` fact `OathRules` appends
    * after every walker command, are each applied through
    * `ProcedureWalker.applyRecorded` — the identical dispatch
    * `OathRules.evolve` uses for these event types in production. The walker
    * itself is never invoked here.
    */
  private def replayCommand(state: OathState, actor: PlayerId,
      outcome: WalkerOutcome): OathState = {
    val stepEvents = (outcome match {
      case WalkerOutcome.Parked(_, evs) => evs
      case WalkerOutcome.Finished(_, evs) => evs
    }).map(_.asInstanceOf[WalkerEvent])
    val fact: WalkerEvent = outcome match {
      case WalkerOutcome.Parked(pending, _) =>
        WalkerParked(actor, ActionRef.Recover, pending.at, pending.answered,
          Vector.empty)
      case WalkerOutcome.Finished(_, _) =>
        WalkerCompleted(actor, ActionRef.Recover)
    }
    (stepEvents :+ fact).foldLeft(state) { (current, event) =>
      ProcedureWalker.applyRecorded(current, event) match {
        case Right(updated) => updated
        case Left(violation) =>
          fail(s"replay-track applyRecorded of $event failed: $violation")
      }
    }
  }

  /** Drives `script` to completion. At every command it independently
    * rebuilds the tree from, and re-walks, the replay-reconstructed state
    * (never the live state, never a tree cached from an earlier command) and
    * asserts the ops that produces equal the ops the live walk actually
    * recorded for that same command. Returns the live walk's final outcome
    * for the caller's own state assertions.
    */
  private def assertNoDrift(ready: ReadyGame, actor: PlayerId,
      script: Vector[Resume], powers: WalkerPowers): WalkerOutcome = {
    def go(remaining: Vector[Resume], liveState: ReadyGame,
        livePending: Option[PendingTree], replayState: OathState,
        starting: Boolean): WalkerOutcome = {
      val resume = remaining.head

      val liveTree =
        if (starting) RecoverProcedure.build(catalog, liveState, actor)
          .toOption.get
        else RecoverProcedure.rebuild(catalog, liveState, actor).toOption.get
      val liveOutcome = runResume(resume, liveState, liveTree, livePending,
        powers)

      val Ready(replayReady) = replayState: @unchecked
      val replayTree =
        if (starting) RecoverProcedure.build(catalog, replayReady, actor)
          .toOption.get
        else RecoverProcedure.rebuild(catalog, replayReady, actor).toOption.get
      val replayPending = replayReady.game.current.walkerPending
      val replayOutcome = runResume(resume, replayReady, replayTree,
        replayPending, powers)

      assertEquals(opsOf(replayOutcome), opsOf(liveOutcome),
        "walker-derived ops (fresh tree over replay-reconstructed state) " +
          s"drifted from the recorded journal at command $resume")

      val nextReplayState = replayCommand(replayState, actor, liveOutcome)

      (liveOutcome, remaining.tail) match {
        case (WalkerOutcome.Finished(_, _), _) => liveOutcome
        case (WalkerOutcome.Parked(pending, events), rest) if rest.nonEmpty =>
          go(rest, foldLive(liveState, events), Some(pending),
            nextReplayState, starting = false)
        case (parked, _) => parked
      }
    }

    go(script, ready, None, OathState.Ready(ready), starting = true)
  }

  test("drift check: single-roll success (advance -> roll -> resolve)") {
    val (ready, actor, siteId, relic) = recoverable
    val finished = assertNoDrift(ready, actor,
      Vector(StartWalk, RollResume(highRoll),
        AnswerResume(Answered(RecoverProcedure.relicDecisionId,
          RecoverRelicAnswer(relic.id)))), walkerPowers)
    finished match {
      case WalkerOutcome.Finished(treeless, _) =>
        assertEquals(treeless.game.current.map.sites(siteId).relics,
          Vector.empty)
        assertEquals(treeless.game.current.players.find(_.player == actor)
          .get.relics.map(_.id), Vector(relic.id))
      case other => fail(s"expected a Finished outcome, got $other")
    }
  }

  test("drift check: multi-roll success (fail parks Continue/Stop, Continue " +
      "rolls again to a cumulative success)") {
    val (ready, actor, _, relic) = recoverable
    val finished = assertNoDrift(ready, actor,
      Vector(StartWalk, RollResume(lowRoll),
        AnswerResume(Answered(RecoverProcedure.choiceDecisionId,
          RecoverChoiceAnswer(
            RecoverChoice.Continue))),
        RollResume(highRoll),
        AnswerResume(Answered(RecoverProcedure.relicDecisionId,
          RecoverRelicAnswer(relic.id)))), walkerPowers)
    finished match {
      case WalkerOutcome.Finished(treeless, _) =>
        assertEquals(treeless.game.current.players.find(_.player == actor)
          .get.relics.map(_.id), Vector(relic.id))
      case other => fail(s"expected a Finished outcome, got $other")
    }
  }

  test("drift check: stop after a failed roll ends the walk with no relic") {
    val (ready, actor, siteId, relic) = recoverable
    val finished = assertNoDrift(ready, actor,
      Vector(StartWalk, RollResume(lowRoll),
        AnswerResume(Answered(RecoverProcedure.choiceDecisionId,
          RecoverChoiceAnswer(
            RecoverChoice.Stop)))), walkerPowers)
    finished match {
      case WalkerOutcome.Finished(treeless, _) =>
        assertEquals(treeless.game.current.players.find(_.player == actor)
          .get.relics, Vector.empty)
        assertEquals(treeless.game.current.map.sites(siteId).relics,
          Vector(relic))
      case other => fail(s"expected a Finished outcome, got $other")
    }
  }

  test("drift check: Catacombs-modified Recover (advance -> roll -> resolve) " +
      "-- the first corpus entry where a power changed the tree") {
    val fixture = CatacombsContributionSuite.reliclessSite(setup)
    val finished = assertNoDrift(fixture.ready, fixture.actor,
      Vector(StartWalk, RollResume(highRoll),
        AnswerResume(Answered(RecoverProcedure.relicDecisionId,
          RecoverRelicAnswer(fixture.topRelic)))), catacombsPowers)
    finished match {
      case WalkerOutcome.Finished(treeless, _) =>
        assertEquals(treeless.game.current.map.sites(fixture.site).relics,
          Vector.empty)
        assertEquals(treeless.game.current.players.find(
          _.player == fixture.actor).get.relics.map(_.id),
          Vector(fixture.topRelic))
      case other => fail(s"expected a Finished outcome, got $other")
    }
  }
}
