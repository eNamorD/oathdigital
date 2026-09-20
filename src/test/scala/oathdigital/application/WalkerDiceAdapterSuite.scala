package oathdigital.application

import oathdigital.model._

class WalkerDiceAdapterSuite extends munit.FunSuite {
  private val port = new CampaignDicePort {
    def rollAttack(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.TwoShields)
  }

  test("the adapter routes by die kind and passes the count through") {
    val dice = CampaignDicePort.walkerDice(port)
    assertEquals(dice.roll(DiceKind.Attack, 2),
      Right(Vector(AttackDieFace.OneSword, AttackDieFace.OneSword)))
    assertEquals(dice.roll(DiceKind.Defense, 3),
      Right(Vector.fill(3)(DefenseDieFace.TwoShields)))
    assertEquals(dice.roll(DiceKind.Attack, 0), Right(Vector.empty))
  }
}
