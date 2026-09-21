package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** When a `Repeat` stops. Its guard reads only state and answers, so a pass that
  * records nothing and asks nothing cannot change what the guard reads, and the
  * loop ends instead of repeating for ever.
  */
class RepeatPassSuite extends munit.FunSuite {
  private val actor = initialReady.game.current.turn.activePlayer
  private val low: ReadyGame = initialReady.updateCurrent(current =>
    current.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(supply = SupplyTrack(1)))
      else p)))

  private def supply(state: ReadyGame): Int = state.game.current.players
    .find(_.player == actor).get.board.supply.supply

  private def run(tree: Operation) = ProcedureWalker.advance(low, tree, None,
    WalkerPowers.empty)

  test("a pass that records nothing ends the loop, and the walk goes on") {
    val Right(WalkerOutcome.Finished(after, events)) = run(Sequence(Vector[Operation](
      Repeat((_, _) => true, Sequence(Vector.empty)),
      GainSupply(actor, 1)))): @unchecked
    assertEquals(supply(after), 2)
    assertEquals(events.size, 1)
  }

  test("a pass that records something repeats while its guard holds") {
    val Right(WalkerOutcome.Finished(after, events)) = run(Repeat(
      (state, _) => supply(state) < 4, GainSupply(actor, 1))): @unchecked
    assertEquals(supply(after), 4)
    assertEquals(events.size, 3)
  }

  test("a pass that parks on a decision is not an empty pass") {
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val outcome = run(Repeat((_, _) => true, question))
    assert(outcome.exists(_.isInstanceOf[WalkerOutcome.Parked]))
  }
}
