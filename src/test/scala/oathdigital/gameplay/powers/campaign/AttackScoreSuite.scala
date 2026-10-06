package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model.AttackDieFace
import oathdigital.model.AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}

/** The attack scorer's steps (catalog batch 4 rulings, "The attack score"):
  * face values, Lancers' doubling, the skull cap, War Tortoise, the total. */
class AttackScoreSuite extends munit.FunSuite:
  private def check(faces: Vector[AttackDieFace], force: Int,
      rules: AttackRules)(score: Int, lost: Int)(using munit.Location): Unit =
    assertEquals(AttackScore.of(faces, force, rules), (score, lost))

  test("with no plan chosen it scores as the printed rules do"):
    val rolls = Vector(Vector(OneSword, HollowSword, HollowSword, HollowSword),
      Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), Vector(TwoSwordsSkull),
      Vector.empty)
    for faces <- rolls; force <- 0 to 3 do
      assertEquals(AttackScore.of(faces, force, AttackRules()),
        CampaignBattle.attackResult(faces, force, ignoreSkulls = false))

  test("a hollow sword is worth half, a whole sword with Fae Battalion, and " +
      "nothing with Rusting Ray"):
    val faces = Vector.fill(3)(HollowSword)
    check(faces, 3, AttackRules())(1, 0)
    check(faces, 3, AttackRules(hollow = HollowWorth.Whole))(3, 0)
    check(faces, 3, AttackRules(hollow = HollowWorth.Zero))(0, 0)

  test("Lancers doubles every face, skulls included, before the skull cap"):
    // Two hollow swords make one sword, two swords, and two payable skulls
    // of two swords each.
    check(Vector(HollowSword, OneSword, TwoSwordsSkull), 5,
      AttackRules(doubled = true))(1 + 2 + 4, 2)

  test("the rulings' example: Lancers and Fearsome General with a force of 3"):
    // Doubled: four skulls and two swords. Three skulls are payable and score
    // 12, the fourth scores 0, the swords add 2.
    check(Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), 3,
      AttackRules(doubled = true, skullSwords = 4))(14, 3)

  test("an unpayable skull scores nothing, with Fearsome General too"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull)
    check(faces, 1, AttackRules())(2, 1)
    check(faces, 1, AttackRules(skullSwords = 4))(4, 1)

  test("an ignored skull scores its swords and kills nothing, four with " +
      "Fearsome General"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull)
    check(faces, 1, AttackRules(skullsIgnored = true))(4, 0)
    check(faces, 1, AttackRules(skullsIgnored = true, skullSwords = 4))(8, 0)

  test("War Tortoise scores every skull face as nothing, Fearsome General's " +
      "included, and payable skulls still kill"):
    check(Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), 1,
      AttackRules(skullFacesScoreNothing = true, skullSwords = 4))(1, 1)
    check(Vector(TwoSwordsSkull, OneSword), 2,
      AttackRules(skullFacesScoreNothing = true, skullsIgnored = true))(1, 0)
