package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class PowerOperationsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Errand Boy")
  private val cardSite = CatalogNames.site("Ancient City")

  /** p1 stands at Ancient City with 3 favor and 3 faceup secrets, where
    * Errand Boy, with nothing on it, shows facedown when `facedown` is true,
    * else faceup. */
  private def payerAtCard(facedown: Boolean = false): ReadyGame =
    Table.start.pawn(p1, at = cardSite).favor(p1, 3).secrets(p1, faceUp = 3)
      .denizen(card, at = cardSite, facedown = facedown).ready

  test("Costs rejects malformed unaffordable and misplaced costs"):
    val ready = payerAtCard()
    val placedAt = Location.OnCard(card)
    intercept[IllegalArgumentException](Cost(favor = -1))
    // p1 holds only 3 secrets.
    assert(Costs.plan(ready, p1, placedAt, Cost(secretBurnt = 4)).isLeft)
    assert(Costs.plan(ready, p1,
      Location.OnCard(DenizenId("missing")),
      Cost(secret = 1)).isLeft)

  test("Costs accepts a burnt-only cost on a facedown card"):
    val facedown = payerAtCard(facedown = true)
    assert(Costs.plan(facedown, p1, Location.OnCard(card),
      Cost(secretBurnt = 1)).isRight)

  test("PayCost executes placed and burnt portions atomically"):
    val ready = payerAtCard()
    val placedAt = Location.OnCard(card)
    val cost = Cost(favor = 1, secret = 1, favorBurnt = 1, secretBurnt = 1)
    val payCost = accepted(Costs.plan(ready, p1, placedAt, cost))
    val run = accepted(OperationPipeline.run(ready, Vector(payCost),
      OperationPolicy.exact(Vector(payCost),
        "test payment operation is not permitted"), Vector.empty)(Right(_)))
    val after = run.state
    val before = Look(ready).player(p1).board
    val player = Look(after).player(p1)
    assertEquals(player.board.favor, before.favor - 2)
    assertEquals(player.board.faceUpSecrets, before.faceUpSecrets - 2)
    assertEquals(Look(after).tokensOn(card), Tokens(favor = 1, secrets = 1))

  test("a free PayCost is an inert no-op"):
    val ready = payerAtCard()
    val payCost = accepted(Costs.plan(ready, p1, Location.OnCard(
      DenizenId("irrelevant")), Cost.free))
    assertEquals(Operation.flatten(payCost), Vector.empty)
    val run = accepted(OperationPipeline.run(ready, Vector(payCost),
      OperationPolicy.exact(Vector(payCost),
        "test payment operation is not permitted"), Vector.empty)(Right(_)))
    val after = run.state
    assertEquals(after.game, ready.game)
    assertEquals(after.banks, ready.banks)
