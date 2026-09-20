package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PayCostSettlementSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val active = current.turn.activePlayer
  private val payer = current.players.map(_.player).find(_ != active).get
  private val siteId = current.map.inPlay.head
  private val denizen = current.commonCards.worldDeck.collectFirst {
    case id: DenizenId => id }.get
  private val suit = catalog.suitOf(denizen).get

  private def arranged(cardTokens: Tokens = Tokens.empty): ReadyGame =
    base.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck =
        c.commonCards.worldDeck.filterNot(_ == denizen)),
      players = c.players.map(p => if (p.player != payer) p else
        p.copy(board = p.board.copy(favor = 3, faceUpSecrets = 3,
          faceDownSecrets = 0))),
      map = c.map.copy(sites = c.map.sites.updated(siteId,
        c.map.sites(siteId).copy(denizens = Vector(DenizenState(denizen,
          Orientation.FaceUp, cardTokens)))))))

  private def run(state: ReadyGame, operation: PayCost) =
    OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive)(Right(_))
  private def board(state: ReadyGame, player: PlayerId) =
    state.game.current.players.find(_.player == player).get.board
  private def tokensOn(state: ReadyGame): Tokens =
    state.game.current.map.sites(siteId).denizens.collectFirst {
      case d: DenizenState if d.id == denizen => d.tokens }.get
  private def offTurnPay(cost: Cost, bank: Option[Suit] = Some(suit),
      intoOccupied: Boolean = false) = PayCost(payer,
    Location.OnCard(denizen), cost, intoOccupied, bank)

  test("an off-turn favor payment goes straight to the matching bank") {
    val start = arranged()
    val done = run(start, offTurnPay(Cost(favor = 2))).toOption.get
    assertEquals(tokensOn(done.state), Tokens.empty)
    assertEquals(board(done.state, payer).favor, 1)
    assertEquals(done.state.banks.favor.getOrElse(suit, 0),
      start.banks.favor.getOrElse(suit, 0) + 2)
  }

  test("an off-turn secret payment flips facedown instead of resting on the card") {
    val done = run(arranged(), offTurnPay(Cost(secret = 2))).toOption.get
    assertEquals(tokensOn(done.state), Tokens.empty)
    val after = board(done.state, payer)
    assertEquals(after.faceUpSecrets -> after.faceDownSecrets, 1 -> 2)
  }

  test("burnt portions are unchanged off-turn") {
    val done = run(arranged(), offTurnPay(Cost(secretBurnt = 1))).toOption.get
    val after = board(done.state, payer)
    assertEquals(after.faceUpSecrets + after.faceDownSecrets, 2)
  }

  test("an off-turn payment onto an occupied card is allowed, since nothing rests") {
    val done = run(arranged(Tokens(1, 1)), offTurnPay(Cost(favor = 1))).toOption.get
    assertEquals(tokensOn(done.state), Tokens(1, 1))
  }

  test("an off-turn placed favor with no matching bank is rejected") {
    assert(run(arranged(), offTurnPay(Cost(favor = 1), bank = None)).isLeft)
    assert(run(arranged(), offTurnPay(Cost(secret = 1), bank = None)).isRight)
  }

  test("the recorded operation is the requested one, and replay reaches the same state") {
    val start = arranged()
    val requested = offTurnPay(Cost(favor = 1, secret = 1))
    val first = run(start, requested).toOption.get
    assertEquals(first.executed, Vector[CoreOperation](requested))
    val replayed = OperationPipeline.run(start, first.executed,
      OperationPolicy.Permissive)(Right(_)).toOption.get
    assertEquals(replayed.state, first.state)
  }

  test("the active player's payment still rests on the card") {
    val start = arranged().updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != active) p else
        p.copy(board = p.board.copy(favor = 3, faceUpSecrets = 3)))))
    val done = run(start, PayCost(active, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), matchingBank = Some(suit))).toOption.get
    assertEquals(tokensOn(done.state), Tokens(1, 1))
  }
}
