package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class PayCostSuite extends munit.FunSuite:
  private val actor = p1
  private val denizen = CatalogNames.denizen("Errand Boy")

  /** p1 holds 3 favor and 3 faceup secrets; Errand Boy stands at Ancient
    * City carrying `tokens`. */
  private def errandBoyCarrying(tokens: Tokens): ReadyGame =
    Table.start.favor(actor, 3).secrets(actor, faceUp = 3)
      .denizen(denizen, at = "Ancient City")
      .tokens(denizen, tokens.favor, tokens.secrets).ready

  private def pay(state: ReadyGame, operation: PayCost) =
    OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive, Vector.empty)(Right(_))

  test("a placed cost is accepted onto an empty card"):
    val state = accepted(pay(errandBoyCarrying(Tokens.empty), PayCost(actor,
      Location.OnCard(denizen), Cost(favor = 1, secret = 1)))).state
    assertEquals(Look(state).tokensOn(denizen), Tokens(1, 1))

  test("a placed cost onto an occupied card is rejected"):
    assertEquals(pay(errandBoyCarrying(Tokens(1, 0)), PayCost(actor,
      Location.OnCard(denizen), Cost(secret = 1))).left.toOption, Some(OathViolation.CoreOperationRejected("card-occupied",
        s"${denizen.value} already holds favor or secrets, so a cost cannot be placed on it")))
    assertEquals(pay(errandBoyCarrying(Tokens(0, 1)), PayCost(actor,
      Location.OnCard(denizen), Cost(favor = 1))).left.toOption, Some(OathViolation.CoreOperationRejected("card-occupied",
        s"${denizen.value} already holds favor or secrets, so a cost cannot be placed on it")))

  test("intoOccupied places onto an occupied card"):
    val paid = accepted(pay(errandBoyCarrying(Tokens(1, 0)), PayCost(actor,
      Location.OnCard(denizen), Cost(secret = 1), intoOccupied = true)))
    val state = paid.state
    assertEquals(Look(state).tokensOn(denizen), Tokens(1, 1))

  test("a burnt-only cost ignores what the card holds"):
    val state = accepted(pay(errandBoyCarrying(Tokens(1, 1)), PayCost(actor,
      Location.OnCard(denizen), Cost(secretBurnt = 1)))).state
    assertEquals(Look(state).tokensOn(denizen), Tokens(1, 1))

  test("Costs.affordable applies the same rule"):
    val at = Location.OnCard(denizen)
    val empty = errandBoyCarrying(Tokens.empty)
    val occupied = errandBoyCarrying(Tokens(0, 1))
    assert(Costs.affordable(empty, actor, at, Cost(secret = 1)))
    assert(!Costs.affordable(occupied, actor, at, Cost(secret = 1)))
    assert(Costs.affordable(occupied, actor, at, Cost(secret = 1),
      intoOccupied = true))
