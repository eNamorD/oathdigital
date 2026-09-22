package oathdigital.gameplay.setup

import oathdigital.gameplay.OathRules
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready

class GameStartToWakeSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val rules = new OathRules(catalog)
  private val chronicle = FirstGameSetupFixture.chronicle
  private val orders = FirstGameSetupFixture.orders

  test("beginGame parks on the first player's pawn-placement decision") {
    val transition = rules.beginGame(OathState.NoGame, chronicle, orders)
      .toOption.get
    assertEquals(transition.continue,
      OathContinue.AwaitingSetupPawn(orders.firstPlayer,
        DecisionId(SetupProcedure.pawnDecisionId(orders.firstPlayer))))
  }

  test("driving every player's two decisions ends in Wake with the recorded seating") {
    var state: OathState = OathState.NoGame
    var transition = rules.beginGame(state, chronicle, orders).toOption.get
    state = transition.state

    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.foreach { playerId =>
      transition = rules.resolveWalker(state, playerId,
        SetupProcedure.pawnDecisionId(playerId),
        ChooseOneAnswer(DecisionOptionRef.Site(
          FirstGameSetupFixture.sites(order.indexOf(playerId))))).toOption.get
      state = transition.state
      val Ready(placedReady) = state: @unchecked
      val adviser = placedReady.game.current.temporaryHands(playerId)
        .collectFirst { case id: DenizenId => id }.get
      transition = rules.resolveWalker(state, playerId,
        SetupProcedure.adviserDecisionId(playerId),
        ChooseOneAnswer(DecisionOptionRef.Denizen(adviser))).toOption.get
      state = transition.state
    }

    assertEquals(transition.continue,
      OathContinue.AwaitingWakeAction(orders.firstPlayer))
    val Ready(ready) = state: @unchecked
    assertEquals(ready.game.current.turn.phase, Phase.Wake)
    assertEquals(ready.game.current.players.count(_.pawnSite.nonEmpty),
      orders.participants.size)
    assertEquals(ready.game.current.players.count(_.advisers.size == 1),
      orders.participants.size)
  }

  test("replaying the recorded events reproduces the same Ready state with no RNG") {
    val transition = rules.beginGame(OathState.NoGame, chronicle, orders)
      .toOption.get
    val replayed = transition.events.foldLeft[Either[OathViolation, OathState]](
      Right(OathState.NoGame)) {
      case (Right(state), event) => rules.evolve(state, event)
      case (left, _) => left
    }
    assertEquals(replayed, Right(transition.state))
  }
}
