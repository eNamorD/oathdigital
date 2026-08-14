package oathdigital.frontend

import munit.FunSuite
import oathdigital.presentation._

class ServerModeUiSuite extends FunSuite {
  test("selection actions map only authorized single target shapes to commands") {
    def action(kind: String) = BoardTargetAction(kind, "Choose", 1, 1,
      false, Vector.empty)
    assertEquals(ServerModeUi.commandForSelection(action("travel"),
      Vector(BoardTargetRef.Site("site:b")), "red"),
      Some(GameCommand.Travel("red", "site:b")))
    assertEquals(ServerModeUi.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red", 4),
      Some(GameCommand.CampaignConquest("red", "site:b", 4)))
    assertEquals(ServerModeUi.commandForSelection(action("muster"), Vector(
      BoardTargetRef.SiteCard("site", "edifice", "E26")), "red"),
      Some(GameCommand.Muster("red", EconomyTarget("edifice", "E26"))))
    assertEquals(ServerModeUi.commandForSelection(action("trade-favor"), Vector(
      BoardTargetRef.SiteCard("site", "denizen", "D1")), "red"),
      Some(GameCommand.Trade("red", EconomyTarget("denizen", "D1"), "favor")))
    assertEquals(ServerModeUi.commandForSelection(action("trade-secret"), Vector(
      BoardTargetRef.SiteCard("site", "denizen", "D1")), "red"),
      Some(GameCommand.Trade("red", EconomyTarget("denizen", "D1"), "secret")))
    assertEquals(ServerModeUi.commandForSelection(action("travel"), Vector(
      BoardTargetRef.PlayerRelic("red", "R1")), "red"), None)
    assertEquals(ServerModeUi.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:a"), BoardTargetRef.Site("site:b")),
      "red", 4), None)
    assertEquals(ServerModeUi.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red", 0), None)
  }

  test("selection copy exposes details and non-color cardinality instructions") {
    val single = BoardTargetAction("travel", "Travel", 1, 1, false,
      Vector.empty)
    val multi = single.copy(actionKind = "campaign", maximum = 3)
    assert(ServerModeUi.cardinalityInstruction(single).contains("immediately"))
    assertEquals(ServerModeUi.cardinalityInstruction(multi),
      "Choose 1 to 3 targets, then confirm.")
    assertEquals(ServerModeUi.candidateButtonLabel(BoardTargetCandidate(
      BoardTargetRef.Site("b"), "Site B", Vector("2 Supply"))),
      "Site B · 2 Supply")
    assertEquals(ServerModeUi.candidateButtonLabel(BoardTargetCandidate(
      BoardTargetRef.Site("b"), "Site B",
      Vector("2 Supply", "Commit all 4 board warbands"))),
      "Site B · 2 Supply · Commit all 4 board warbands")
    assertEquals(ServerModeUi.actionLabel("trade-secret"), "Trade for secrets")
  }

  test("bounded Campaign uses the generic single-site action label") {
    assertEquals(ServerModeUi.actionLabel("campaign-conquest"), "Campaign")
  }

  test("site forces retain accessible labels counts and stable color classes") {
    val cases = Vector(
      SiteForces("exile", 2, "player", Some("red-exile"),
        "Red Warbands", "red") -> ("Red Warbands x2", "force-red"),
      SiteForces("exile", 1, "player", Some("blue-exile"),
        "Blue Warbands", "blue") -> ("Blue Warbands x1", "force-blue"),
      SiteForces("imperial", 1, "empire", None,
        "Imperial Warbands", "empire") -> ("Imperial Warbands x1", "force-empire"),
      SiteForces("bandit", 3, "bandit", None,
        "Bandit Warbands", "bandit") -> ("Bandit Warbands x3", "force-bandit")
    )
    cases.foreach { case (forces, (label, cssClass)) =>
      assertEquals(ServerModeUi.forceText(forces), label)
      assertEquals(ServerModeUi.forceCssClass(forces), cssClass)
    }
    assertEquals(GameSite("empty", "Empty", 0, 0, 0, 0, Vector.empty,
      GameSiteRelics(0)).forces, None)
  }

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

  test("inactive Act viewer sees no action-selection controls") {
    val value = projection(Set("beginRest"), phase = "act-action-selection")
      .copy(actionSelectionOpen = true,
        legalSearchSources = Vector(LegalSearchSource("world", None, 2)),
        legalTravelDestinations = Vector(LegalTravelDestination("site:1", 2)),
        legalMusters = Vector(LegalMuster(EconomyTarget("denizen", "d1"),
          "Muster target", "order", 1, 2)),
        legalTrades = Vector(LegalTrade(EconomyTarget("denizen", "d1"),
          "Trade target", "order", "favor", 1, 2)))
    val inactive = ServerModeUi.viewerPresentation(value, "blue-exile")
    val active = ServerModeUi.viewerPresentation(value, "red-exile")

    assertEquals(inactive.waitingForPlayerId, Some("red-exile"))
    assert(!ServerModeUi.showActActionControls(value, inactive))
    assert(ServerModeUi.showActActionControls(value, active))
  }

  test("inactive setup viewer waits without pawn or private adviser controls") {
    val value = projection(
      Set("placePawn", "chooseAdviser"),
      phase = "awaiting-adviser",
      activeParticipantId = "red-exile",
      ready = false
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
      ready = false
    )
    assert(ServerModeUi.viewerPresentation(
      setup,
      "red-exile"
    ).showGameplayControls)
  }

  test("board target classes distinguish candidate selected and read-only state") {
    assertEquals(ServerModeUi.siteTargetClasses(false, false),
      "site site-readonly")
    assertEquals(ServerModeUi.siteTargetClasses(true, false),
      "site board-target")
    assertEquals(ServerModeUi.siteTargetClasses(true, true),
      "site board-target board-target-selected")
    assert(ServerModeUi.cardTargetClasses(true, true)
      .contains("board-target-selected"))
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
      SiteMetric("Favor", "2"),
      SiteMetric("Secrets", "1"),
      SiteMetric("Defense", "0")
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

    assertEquals(details.metrics.map(_.value), Vector("0", "0", "0"))
    assertEquals(details.denizenEmpty, "None")
    assertEquals(details.relicSummary, "None")
  }

  test("site metric uses Forge cost instead of Recover difficulty") {
    val forged = SiteCardPresentation.from(GameSite("forge", "Forge", 0, 0,
      3, 0, Vector.empty, GameSiteRelics(0), recoverDifficulty = Some(4),
      forgeCost = Some(ForgeCost(2, 1))))
    assert(forged.metrics.contains(SiteMetric("Forge cost", "2 favor · 1 secrets")))
    assert(!forged.metrics.exists(_.label == "Recover difficulty"))

    val recover = SiteCardPresentation.from(GameSite("recover", "Recover", 0, 0,
      2, 0, Vector.empty, GameSiteRelics(0), recoverDifficulty = Some(3)))
    assert(recover.metrics.contains(SiteMetric("Recover difficulty", "3")))
    assert(!recover.metrics.exists(_.label == "Forge cost"))
  }

  test("pile symbols and shape classes distinguish public tops and empty piles") {
    assertEquals(ServerModeUi.pileSymbol(2, Some("denizen")), "D")
    assertEquals(ServerModeUi.pileSymbol(1, Some("vision")), "V")
    assertEquals(ServerModeUi.pileSymbol(0, None), "")
    assertEquals(ServerModeUi.pileCardClasses(2), "pile-card pile-back")
    assertEquals(ServerModeUi.pileCardClasses(0), "pile-card pile-empty")
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
      ready: Boolean = true
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
      completed = false
    )
}
