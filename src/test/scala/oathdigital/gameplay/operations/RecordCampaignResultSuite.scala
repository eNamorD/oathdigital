package oathdigital.gameplay.operations

import oathdigital.model._

class RecordCampaignResultSuite extends munit.FunSuite {
  private val conquest = CampaignResult(PlayerId("red"), CampaignKind.Conquest,
    CampaignDefender.Bandits, Vector(SiteId("site:a")), Vector.empty, force = 3,
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield), defenseScore = 4,
    victorious = false)

  private def record(ready: ReadyGame, result: CampaignResult) =
    new OperationExecutor().executeAll(ready, Vector(RecordCampaignResult(result)))
      .toOption.get.game.current.lastCampaignResult

  test("recording a result writes it into state") {
    assertEquals(TestGameFixtures.ready.game.current.lastCampaignResult, None)
    assertEquals(record(TestGameFixtures.ready, conquest), Some(conquest))
  }

  test("the next Campaign's result replaces the last one") {
    val later = conquest.copy(victorious = true, sacrificed = 0)
    val once = TestGameFixtures.ready.updateCurrent(
      _.copy(lastCampaignResult = Some(conquest)))
    assertEquals(record(once, later), Some(later))
  }

  test("the total an attacker brought is the score plus the sacrifice") {
    assertEquals(conquest.attackTotal, 3)
  }
}
