package oathdigital.frontend

import munit.FunSuite
import oathdigital.presentation._

class ServerModeUiSuite extends FunSuite {
  test("Take Wealth actions use the active-player labels and commands") {
    val actions = ServerModeUi.takeWealthActions(
      projection(Set("takeFavor", "takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(
      actions.map(_.label),
      Vector("Take Wealth: 1 favor", "Take Wealth: 1 secret")
    )
    assertEquals(
      actions.map(_.command),
      Vector(
        GameCommand.TakeWealth("red-exile", "favor"),
        GameCommand.TakeWealth("red-exile", "secret")
      )
    )
  }

  test("unavailable Take Wealth actions are absent") {
    assertEquals(
      ServerModeUi.takeWealthActions(
        projection(Set("endWake")),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("blocked Take Wealth resource is absent while the legal one remains") {
    val actions = ServerModeUi.takeWealthActions(
      projection(Set("takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(actions.map(_.label), Vector("Take Wealth: 1 secret"))
  }

  test("already-used Take Wealth actions are absent from a later phase") {
    assertEquals(
      ServerModeUi.takeWealthActions(
        projection(Set("takeFavor", "takeSecret"), phase = "act-action-selection"),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("inactive Wake viewer waits and receives no gameplay controls") {
    val value = projection(
      Set("takeFavor", "takeSecret", "endWake"),
      activeParticipantId = "red-exile"
    )
    val presentation = ServerModeUi.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForPlayerId, Some("red-exile"))
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
    assertEquals(
      ServerModeUi.takeWealthActions(value, "blue-exile"),
      Vector.empty
    )
  }

  test("inactive setup viewer waits without pawn or private adviser controls") {
    val value = projection(
      Set("placePawn", "chooseAdviser"),
      phase = "awaiting-adviser",
      activeParticipantId = "red-exile",
      ready = false,
      privateAdviserChoices = Vector(
        AdviserChoice("adviser-1", "Adviser One")
      )
    )

    val presentation = ServerModeUi.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
  }

  test("active viewer retains Wake and setup gameplay controls") {
    val wake = projection(Set("takeFavor", "endWake"))
    assert(ServerModeUi.viewerPresentation(
      wake,
      "red-exile"
    ).showGameplayControls)
    assertEquals(
      ServerModeUi.takeWealthActions(wake, "red-exile").map(_.label),
      Vector("Take Wealth: 1 favor")
    )

    val setup = projection(
      Set("chooseAdviser"),
      phase = "awaiting-adviser",
      ready = false,
      privateAdviserChoices = Vector(
        AdviserChoice("adviser-1", "Adviser One")
      )
    )
    assert(ServerModeUi.viewerPresentation(
      setup,
      "red-exile"
    ).showGameplayControls)
  }

  test("active Wake sites are read-only while legal setup sites are buttons") {
    val wake = projection(Set("takeFavor", "endWake"))
    assertEquals(ServerModeUi.siteCardsActionable(
      wake,
      ServerModeUi.viewerPresentation(wake, "red-exile")
    ), false)

    val setup = projection(
      Set("placePawn"),
      phase = "awaiting-pawn",
      ready = false
    )
    assertEquals(ServerModeUi.siteCardsActionable(
      setup,
      ServerModeUi.viewerPresentation(setup, "red-exile")
    ), true)
    assertEquals(ServerModeUi.siteCardsActionable(
      setup,
      ServerModeUi.viewerPresentation(setup, "red-exile"),
      controlsAvailable = false
    ), false)
    assertEquals(ServerModeUi.siteCardsActionable(
      setup,
      ServerModeUi.viewerPresentation(setup, "blue-exile")
    ), false)
  }

  test("Travel selection makes only projected L2 site cards actionable") {
    val value = projection(Set.empty, phase = "act-action-selection").copy(
      actionSelectionOpen = true,
      legalTravelDestinations = Vector(
        LegalTravelDestination("site:legal", 2)))
    val viewer = ServerModeUi.viewerPresentation(value, "red-exile")
    assert(ServerModeUi.siteCardsActionable(
      value, viewer, travelSelectionOpen = true, siteId = "site:legal"))
    assert(!ServerModeUi.siteCardsActionable(
      value, viewer, travelSelectionOpen = true, siteId = "site:blocked"))
    assertEquals(ServerModeUi.travelCost(value, "site:legal"), Some(2))
    assertEquals(ServerModeUi.travelCost(value, "site:blocked"), None)
  }

  test("populated site details render properties, stable IDs, and hidden relics") {
    val site = GameSite(
      "site:woods",
      "Woods",
      looseFavor = 2,
      looseSecrets = 1,
      denizenCapacity = 3,
      relicCapacity = 2,
      denizens = Vector(
        GameSiteCard("denizen:fox", "Fox"),
        GameSiteCard("denizen:owl", "Owl")
      ),
      relics = GameSiteRelics(2)
    )
    val details = SiteCardPresentation.from(site)

    assertEquals(details.metrics, Vector(
      SiteMetric("Favor", 2),
      SiteMetric("Secrets", 1),
      SiteMetric("Denizen slots", 3),
      SiteMetric("Relic slots", 2)
    ))
    assertEquals(site.denizens.map(_.label), Vector("Fox", "Owl"))
    assertEquals(site.denizens.map(_.denizenId),
      Vector("denizen:fox", "denizen:owl"))
    assertEquals(details.relicSummary, "2 facedown relics")
  }

  test("empty site details have image-independent empty states") {
    val details = SiteCardPresentation.from(GameSite(
      "site:empty",
      "Empty",
      0,
      0,
      0,
      0,
      Vector.empty,
      GameSiteRelics(0)
    ))

    assertEquals(details.metrics.map(_.value), Vector(0, 0, 0, 0))
    assertEquals(details.denizenEmpty, "None")
    assertEquals(details.relicSummary, "None")
  }

  test("site and denizen visuals deterministically fall back without assets") {
    val details = SiteCardPresentation.from(GameSite(
      "woods",
      "Woods",
      0,
      0,
      1,
      0,
      Vector(GameSiteCard("fox", "Fox")),
      GameSiteRelics(0)
    ))

    assertEquals(details.siteVisual.instruction,
      VisualInstruction.Placeholder(
        "W", "Woods", AccessibleLabel("Woods")
      ))
    assertEquals(details.denizenVisuals.head._2.instruction,
      VisualInstruction.Placeholder("F", "Fox", AccessibleLabel("Fox")))
  }

  test("failed and stale assets share the same accessible fallback") {
    val expected = ImageRef("sites/woods.webp")
    val site = SiteView(
      ViewId("site:woods"),
      AccessibleLabel("Woods"),
      Some(expected),
      FallbackVisual("W", "Woods")
    )
    val fallback = VisualInstruction.Placeholder(
      "W", "Woods", AccessibleLabel("Woods")
    )

    assertEquals(VisualRenderPlan.from(
      site, ImageLoadResult.Failed(expected)).instruction, fallback)
    assertEquals(VisualRenderPlan.from(
      site,
      ImageLoadResult.Loaded(ImageRef("sites/mine.webp"))
    ).instruction, fallback)
  }

  test("an image load error deterministically selects the labelled fallback") {
    val expected = ImageRef("sites/woods.webp")
    val site = SiteView(
      ViewId("site:woods"),
      AccessibleLabel("Woods"),
      Some(expected),
      FallbackVisual("W", "Woods")
    )
    val plan = VisualRenderPlan.from(
      site,
      ImageLoadResult.Loaded(expected)
    )

    assertEquals(plan.instruction,
      VisualInstruction.Image(expected, AccessibleLabel("Woods")))
    assertEquals(plan.afterFailure, VisualInstruction.Placeholder(
      "W", "Woods", AccessibleLabel("Woods")
    ))
  }

  private def projection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true,
      privateAdviserChoices: Vector[AdviserChoice] = Vector.empty
  ): GameProjection =
    GameProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activeParticipantId = Some(activeParticipantId),
      players = Vector(
        GamePlayer(
          "red-exile",
          "Red Exile",
          "exile",
          PlayerColorToken.Red
        ),
        GamePlayer(
          "blue-exile",
          "Blue Exile",
          "exile",
          PlayerColorToken.Blue
        )
      ),
      world = Vector.empty,
      pawnLocations = Vector.empty,
      legalControls = legalControls,
      ready = ready,
      completed = false,
      privateAdviserChoices = privateAdviserChoices
    )
}
