package oathdigital.frontend

import org.scalajs.dom

class CampaignResultPanelSuite extends munit.FunSuite {
  private def projection(result: Option[CampaignResultState]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, lastCampaign = result)

  private def draw(result: Option[CampaignResultState]): dom.Element = {
    val panel = dom.document.createElement("div")
    CampaignResultPanel.render(projection(result), panel)
    panel
  }

  private val conquest = CampaignResultState("red", "conquest", None,
    Vector("site:a"), Vector.empty, 4, Vector("one-sword", "two-swords-skull"),
    3, 1, 1, Vector("one-shield", "doubler"), 4, victorious = true)

  test("nothing is drawn before a Campaign has been fought") {
    assertEquals(draw(None).children.length, 0)
  }

  test("the result names the fight, both dice sets, both totals and the victor") {
    val panel = draw(Some(conquest))
    val text = panel.textContent
    assert(text.contains("Last Campaign"), text)
    assert(text.contains("Conquest"), text)
    assert(text.contains("Bandits"), text)
    assert(text.contains("one sword, two swords and a skull"), text)
    assert(text.contains("Attack 3 + 1 sacrificed = 4"), text)
    assert(text.contains("one shield, doubler"), text)
    assert(text.contains("Defense 4"), text)
    assert(text.contains("Victory"), text)
  }

  test("a defeat and a Raid against a player read as such") {
    val raid = conquest.copy(kind = "raid", defenderPlayerId = Some("blue"),
      targetSiteIds = Vector.empty,
      raidTargets = Vector("pawn:blue", "relic:blue:r1"), victorious = false)
    val text = draw(Some(raid)).textContent
    assert(text.contains("Raid"), text)
    assert(text.contains("blue"), text)
    assert(text.contains("Defeat"), text)
  }
}
