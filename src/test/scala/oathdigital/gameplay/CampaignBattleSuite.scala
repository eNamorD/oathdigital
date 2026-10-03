package oathdigital.gameplay

import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model._

class CampaignBattleSuite extends munit.FunSuite:
  import AttackDieFace._

  /** The attack after the skull cap scores `score` and loses `skullsLost`
    * warbands: a TwoSwordsSkull die is 2 swords and a skull, each skull costs
    * one of the `force` warbands, and an unpaid skull's swords do not count.
    */
  private def assertAttack(faces: Vector[AttackDieFace], force: Int,
      ignoreSkulls: Boolean = false)(score: Int, skullsLost: Int)(
      using munit.Location): Unit =
    assertEquals(CampaignBattle.attackResult(faces, force, ignoreSkulls),
      (score, skullsLost))

  test("a skull costs a warband and its swords only when the warband can " +
      "be paid"):
    // Two single swords and no skull.
    assertAttack(Vector(OneSword, OneSword), force = 2)(
      score = 2, skullsLost = 0)
    // 2 + 1 swords; the one skull is paid by a warband.
    assertAttack(Vector(TwoSwordsSkull, OneSword), force = 2)(
      score = 2 + 1, skullsLost = 1)
    // Two skulls but one warband: only one skull can be paid, so only its
    // swords count; the other adds nothing.
    assertAttack(Vector(TwoSwordsSkull, TwoSwordsSkull), force = 1)(
      score = 2, skullsLost = 1)
    // No warband pays either skull, so neither die's swords count.
    assertAttack(Vector(TwoSwordsSkull, TwoSwordsSkull), force = 0)(
      score = 0, skullsLost = 0)

  test("Outriders ignores every skull and keeps every sword"):
    assertAttack(Vector(TwoSwordsSkull, TwoSwordsSkull), force = 1,
      ignoreSkulls = true)(score = 2 + 2, skullsLost = 0)

  test("hollow swords score one per pair"):
    // Three halves make one pair; the odd half scores nothing.
    assertAttack(Vector(HollowSword, HollowSword, HollowSword), force = 3)(
      score = 1, skullsLost = 0)

  test("the sacrifice heading is the prompt alone -- the dice are glyphs"):
    assertEquals(CampaignBattle.sacrificeHeading(1),
      "Sacrifice up to 1 warband for one attack each")
    assertEquals(CampaignBattle.sacrificeHeading(2),
      "Sacrifice up to 2 warbands for one attack each")
