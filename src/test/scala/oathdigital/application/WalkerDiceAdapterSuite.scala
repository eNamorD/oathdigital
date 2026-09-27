package oathdigital.application

import oathdigital.model._

class WalkerDiceAdapterSuite extends munit.FunSuite:
  private val port = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] = Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] = Vector.fill(count)(DefenseDieFace.TwoShields)

  test("the adapter routes by die kind and passes the count through"):
    val dice = CampaignDicePort.walkerDice(port)
    assertEquals(dice.roll(DiceKind.Attack, 2),
      Right(Vector(AttackDieFace.OneSword, AttackDieFace.OneSword)))
    assertEquals(dice.roll(DiceKind.Defense, 3),
      Right(Vector.fill(3)(DefenseDieFace.TwoShields)))
    assertEquals(dice.roll(DiceKind.Attack, 0), Right(Vector.empty))

  test("a port's default shuffle is a permutation of the pile's positions"):
    val order = CampaignDicePort.walkerDice(port).shuffle(6)
    assertEquals(order.map(_.sorted), Right((0 until 6).toVector))

  test("the adapter takes the port's shuffle, so a test port can fix it"):
    val fixed = new CampaignDicePort:
      def rollAttack(count: Int): Vector[AttackDieFace] = Vector.empty
      def rollDefense(count: Int): Vector[DefenseDieFace] = Vector.empty
      override def shuffle(count: Int): Vector[Int] =
        (0 until count).reverse.toVector
    assertEquals(CampaignDicePort.walkerDice(fixed).shuffle(3),
      Right(Vector(2, 1, 0)))
