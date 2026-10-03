package oathdigital.application

import oathdigital.testkit.Dice

class WalkerDiceAdapterSuite extends munit.FunSuite:
  test("a port's default shuffle is a permutation of the pile's positions"):
    val order = CampaignDicePort.walkerDice(Dice.steady).shuffle(6)
    assertEquals(order.map(_.sorted), Right((0 until 6).toVector))

  test("the adapter takes the port's shuffle, so a test port can fix it"):
    assertEquals(
      CampaignDicePort.walkerDice(Dice.reversingShuffle).shuffle(3),
      Right(Vector(2, 1, 0)))
