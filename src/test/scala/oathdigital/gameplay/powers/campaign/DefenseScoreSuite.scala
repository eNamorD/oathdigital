package oathdigital.gameplay.powers.campaign

import oathdigital.model.DefenseDieFace
import oathdigital.model.DefenseDieFace.{Blank, Doubler, OneShield, TwoShields}

/** The defense scorer: face filters, then doublers on what is left, then the
  * defender's force (catalog batch 4 rulings, "The defense score"). */
class DefenseScoreSuite extends munit.FunSuite:
  test("with no plan chosen it scores the dice and adds the force"):
    for faces <- Vector(Vector(OneShield, TwoShields, Doubler), Vector(Blank),
        Vector.empty[DefenseDieFace]) do
      assertEquals(DefenseScore.of(faces, 2, DefenseRules()),
        DefenseDieFace.score(faces) + 2)

  test("single shields score nothing, as SingleShields scores them"):
    val faces = Vector(OneShield, TwoShields, Doubler)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      singleShieldsIgnored = true)), 4)
    assertEquals(SingleShields.score(faces), 4)

  test("War Tortoise with Rain Boots and a doubler: the filters run before " +
      "the doubler"):
    val faces = Vector(OneShield, TwoShields, TwoShields, Doubler)
    assertEquals(DefenseScore.dice(faces, DefenseRules()), 10)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      doubleShieldsIgnored = true)), 2)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      singleShieldsIgnored = true)), 8)
    assertEquals(DefenseScore.dice(faces, DefenseRules(
      singleShieldsIgnored = true, doubleShieldsIgnored = true)), 0)

  test("Garrison Armory adds the force twice, undoubled"):
    assertEquals(DefenseScore.of(Vector(TwoShields, Doubler), 2,
      DefenseRules(forceTwice = true)), 4 + 2 + 2)
