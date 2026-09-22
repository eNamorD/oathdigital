package oathdigital.gameplay.setup

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

class SetupProcedureSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val ready = FirstGameSetupFixture.freshReady

  /** Walks `tree` from scratch, answering every park with the first option
    * a `Decide` offers, until the tree finishes. Mirrors the resolve loop
    * `RecoverProcedureSuite`/`WalkerReplayDriftSuite` already use to drive a
    * multi-park tree to completion inside a test. */
  private def driveToCompletion(ready: ReadyGame, tree: Operation): ReadyGame = {
    var outcome = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .toOption.get
    while (outcome.isInstanceOf[WalkerOutcome.Parked]) {
      val WalkerOutcome.Parked(pending, _) = outcome: @unchecked
      val decide = ProcedureWalker.parkedDecide(ready, tree, pending,
        WalkerPowers.empty).get
      val answer = Answered(decide.decisionId,
        DecisionAnswer.ChooseOneAnswer(
          decide.query.asInstanceOf[DecisionQuery.ChooseOne].options.head.ref),
        decide.owner)
      outcome = ProcedureWalker.resolve(ready, tree, pending, answer,
        WalkerPowers.empty).toOption.get
    }
    val WalkerOutcome.Finished(finished, _) = outcome: @unchecked
    finished
  }

  test("each player places a pawn, then chooses an adviser, in turn order") {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    val first = ready.game.current.turn.activePlayer

    val parked1 = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .toOption.get
    val pending1 = parked1 match {
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"expected a park, got $other")
    }
    val decide1 = ProcedureWalker.parkedDecide(ready, tree, pending1,
      WalkerPowers.empty).get
    assertEquals(decide1.decisionId, SetupProcedure.pawnDecisionId(first))
    assertEquals(decide1.owner, first)
  }

  test("the whole procedure finishes in Wake of round 1 with three pawns and three advisers") {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    val finished = driveToCompletion(ready, tree)
    assertEquals(finished.game.current.turn.phase, Phase.Wake)
    assertEquals(finished.game.current.turn.activePlayer,
      ready.game.current.turn.activePlayer)
    assertEquals(finished.game.current.players.count(_.pawnSite.nonEmpty), 3)
    assertEquals(finished.game.current.players.count(_.advisers.size == 1), 3)
    assert(finished.game.current.temporaryHands.values.forall(_.isEmpty))
  }
}
