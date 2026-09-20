package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PayCostSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val actor = current.turn.activePlayer
  private val siteId = current.map.inPlay.head
  private val denizen = current.commonCards.worldDeck.collectFirst {
    case id: DenizenId => id }.get

  private def withCard(tokens: Tokens): ReadyGame =
    base.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck =
        c.commonCards.worldDeck.filterNot(_ == denizen)),
      players = c.players.map(p => if (p.player != actor) p else
        p.copy(board = p.board.copy(favor = 3, faceUpSecrets = 3))),
      map = c.map.copy(sites = c.map.sites.updated(siteId,
        c.map.sites(siteId).copy(denizens = Vector(DenizenState(denizen,
          Orientation.FaceUp, tokens)))))))

  private def pay(state: ReadyGame, operation: PayCost) =
    OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive)(Right(_))

  private def tokensOn(state: ReadyGame): Tokens =
    state.game.current.map.sites(siteId).denizens.collectFirst {
      case d: DenizenState if d.id == denizen => d.tokens }.get

  test("a placed cost is accepted onto an empty card") {
    val state = pay(withCard(Tokens.empty), PayCost(actor,
      Location.OnCard(denizen), Cost(favor = 1, secret = 1))).toOption.get.state
    assertEquals(tokensOn(state), Tokens(1, 1))
  }

  test("a placed cost onto an occupied card is rejected") {
    assert(pay(withCard(Tokens(1, 0)), PayCost(actor,
      Location.OnCard(denizen), Cost(secret = 1))).isLeft)
    assert(pay(withCard(Tokens(0, 1)), PayCost(actor,
      Location.OnCard(denizen), Cost(favor = 1))).isLeft)
  }

  test("intoOccupied places onto an occupied card") {
    val state = pay(withCard(Tokens(1, 0)), PayCost(actor,
      Location.OnCard(denizen), Cost(secret = 1), intoOccupied = true))
      .toOption.get.state
    assertEquals(tokensOn(state), Tokens(1, 1))
  }

  test("a burnt-only cost ignores what the card holds") {
    val state = pay(withCard(Tokens(1, 1)), PayCost(actor,
      Location.OnCard(denizen), Cost(secretBurnt = 1))).toOption.get.state
    assertEquals(tokensOn(state), Tokens(1, 1))
  }

  test("Costs.affordable applies the same rule") {
    val at = Location.OnCard(denizen)
    assert(Costs.affordable(withCard(Tokens.empty), actor, at, Cost(secret = 1)))
    assert(!Costs.affordable(withCard(Tokens(0, 1)), actor, at, Cost(secret = 1)))
    assert(Costs.affordable(withCard(Tokens(0, 1)), actor, at, Cost(secret = 1),
      intoOccupied = true))
  }

  test("Costs.onCard names the card's suit bank") {
    val cost = Costs.onCard(actor, denizen, Cost(favor = 1), catalog)
    assertEquals(cost, PayCost(actor, Location.OnCard(denizen),
      Cost(favor = 1), matchingBank = catalog.suitOf(denizen)))
  }
}
