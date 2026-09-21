package oathdigital.gameplay

import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model._

class CampaignBattleSuite extends munit.FunSuite {
  import AttackDieFace._

  test("a skull costs a warband and its swords only when the warband can be paid") {
    assertEquals(CampaignBattle.attackResult(Vector(OneSword, OneSword), 2,
      ignoreSkulls = false), (2, 0))
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull, OneSword), 2,
      ignoreSkulls = false), (3, 1))
    // Two skulls but one warband: only one skull can be paid, so only its
    // swords count; the other adds nothing.
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull,
      TwoSwordsSkull), 1, ignoreSkulls = false), (2, 1))
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull,
      TwoSwordsSkull), 0, ignoreSkulls = false), (0, 0))
  }

  test("Outriders ignores every skull and keeps every sword") {
    assertEquals(CampaignBattle.attackResult(Vector(TwoSwordsSkull,
      TwoSwordsSkull), 1, ignoreSkulls = true), (4, 0))
  }

  test("hollow swords score one per pair") {
    assertEquals(CampaignBattle.attackResult(Vector(HollowSword, HollowSword,
      HollowSword), 3, ignoreSkulls = false), (1, 0))
  }

  test("the sacrifice heading names the faces, the total and the survivors") {
    assertEquals(CampaignBattle.sacrificeHeading(Vector(TwoSwordsSkull, OneSword),
      score = 3, skulls = 1, max = 1),
      "Attack roll: two swords and a skull, one sword. Attack 3 with 1 skull " +
        "loss. Sacrifice up to 1 warband for one attack each.")
    assertEquals(CampaignBattle.sacrificeHeading(Vector(OneSword, OneSword),
      score = 2, skulls = 0, max = 2),
      "Attack roll: one sword, one sword. Attack 2 with 0 skull losses. " +
        "Sacrifice up to 2 warbands for one attack each.")
  }
}
