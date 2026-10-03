package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class PayCostSettlementSuite extends munit.FunSuite:
  private val active = p1
  private val payer = p2
  private val denizen = CatalogNames.denizen("Errand Boy")
  /** Errand Boy is a Beast. */
  private val suit = Suit.Beast

  /** p2, off turn, holds 3 favor and 3 faceup secrets; Errand Boy stands at
    * Ancient City carrying `cardTokens`. */
  private def offTurnPayer(cardTokens: Tokens = Tokens.empty): Table =
    Table.start.favor(payer, 3).secrets(payer, faceUp = 3)
      .denizen(denizen, at = "Ancient City")
      .tokens(denizen, cardTokens.favor, cardTokens.secrets)

  private def run(state: ReadyGame, operation: PayCost) =
    OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive, Vector.empty)(Right(_))
  private def offTurnPay(cost: Cost, bank: Option[Suit] = Some(suit),
      intoOccupied: Boolean = false) = PayCost(payer,
    Location.OnCard(denizen), cost, intoOccupied, bank)

  test("an off-turn favor payment goes straight to the matching bank"):
    val start = offTurnPayer().ready
    val done = accepted(run(start, offTurnPay(Cost(favor = 2))))
    assertEquals(Look(done.state).tokensOn(denizen), Tokens.empty)
    // 3 favor, 2 paid.
    assertEquals(Look(done.state).favor(payer), 1)
    assertEquals(done.state.banks.favor.getOrElse(suit, 0),
      start.banks.favor.getOrElse(suit, 0) + 2)

  test("an off-turn secret payment flips facedown instead of resting on the card"):
    val done = accepted(run(offTurnPayer().ready, offTurnPay(Cost(secret = 2))))
    assertEquals(Look(done.state).tokensOn(denizen), Tokens.empty)
    val after = Look(done.state).player(payer).board
    // 3 faceup secrets, 2 of them turned facedown.
    assertEquals(after.faceUpSecrets -> after.faceDownSecrets, 1 -> 2)

  test("burnt portions are unchanged off-turn"):
    val done =
      accepted(run(offTurnPayer().ready, offTurnPay(Cost(secretBurnt = 1))))
    val after = Look(done.state).player(payer).board
    // 3 secrets, 1 burnt.
    assertEquals(after.faceUpSecrets + after.faceDownSecrets, 2)

  test("an off-turn payment onto an occupied card is allowed, since nothing rests"):
    val done = accepted(run(offTurnPayer(Tokens(1, 1)).ready,
      offTurnPay(Cost(favor = 1))))
    assertEquals(Look(done.state).tokensOn(denizen), Tokens(1, 1))

  test("an off-turn placed favor with no matching bank is rejected"):
    assertEquals(run(offTurnPayer().ready, offTurnPay(Cost(favor = 1),
      bank = None)).left.toOption,
      Some(OathViolation.CoreOperationRejected("no-matching-bank",
        "favor paid outside the payer's turn needs the card's suit bank")))
    assert(run(offTurnPayer().ready,
      offTurnPay(Cost(secret = 1), bank = None)).isRight)

  test("the recorded operation is the requested one, and replay reaches the same state"):
    val start = offTurnPayer().ready
    val requested = offTurnPay(Cost(favor = 1, secret = 1))
    val first = accepted(run(start, requested))
    assertEquals(first.executed, Vector[CoreOperation](requested))
    val replayed = accepted(OperationPipeline.run(start, first.executed,
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    assertEquals(replayed.state, first.state)

  test("the active player's payment still rests on the card"):
    val start = offTurnPayer().favor(active, 3).secrets(active, faceUp = 3)
      .ready
    val done = accepted(run(start, PayCost(active, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), matchingBank = Some(suit))))
    assertEquals(Look(done.state).tokensOn(denizen), Tokens(1, 1))
