package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.frontend.ServerModeFixtures.projection

import munit.FunSuite

/** The pure helpers behind the server UI: labels, classes, geometry and the
  * presentation of sites, piles and targets.
  */
class ServerUiSupportSuite extends FunSuite:

  test("canonical path decodes only a single game segment"):
    assertEquals(ServerUiSupport.canonicalGameId("/games/my%20game"), Some("my game"))
    assertEquals(ServerUiSupport.canonicalGameId("/"), None)
    assertEquals(ServerUiSupport.canonicalGameId("/games/"), None)
    assertEquals(ServerUiSupport.canonicalGameId("/games/a/api"), None)
    assertEquals(ServerUiSupport.canonicalGameId("/games/%broken"), None)

  test("secret summaries lead with available over total and explain unavailable tokens"):
    assertEquals(ServerUiSupport.secretSummaryLabel(1, 1, 0, 0),
      "1 available of 1 owned; 0 facedown and 0 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(0, 1, 0, 1),
      "0 available of 1 owned; 0 facedown and 1 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(0, 1, 1, 0),
      "0 available of 1 owned; 1 facedown and 0 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(1, 2, 0, 1),
      "1 available of 2 owned; 0 facedown and 1 committed")

  test("banner and Challenge action labels are presentable"):
    assertEquals(ServerUiSupport.actionLabel("challenge"), "Challenge")
    assertEquals(ServerUiSupport.actionLabel("peoples-favor"), "People's Favor")

  test("selection copy exposes details and non-color cardinality instructions"):
    val single = BoardTargetAction("travel", "Travel", 1, 1, false,
      Vector(BoardTargetCandidate(BoardTargetRef.Site("a"), "A", Vector.empty)))
    val confirmed = single.copy(explicitConfirm = true)
    assert(ServerUiSupport.cardinalityInstruction(single).contains("immediately"))
    assertEquals(ServerUiSupport.cardinalityInstruction(confirmed),
      "Choose one target, then confirm.")
    assertEquals(ServerUiSupport.cardinalityInstruction(single.copy(
      actionKind = "travel", minimum = 0, maximum = 0)),
      "No target is available; confirm to play this action.")

  test("site forces retain accessible labels counts and stable color classes"):
    val cases = Vector(
      SiteForces.Exile(2, "red-exile", PlayerColor.Red,
        "Red Warbands") -> ("Red Warbands x2", "force-red"),
      SiteForces.Exile(1, "blue-exile", PlayerColor.Blue,
        "Blue Warbands") -> ("Blue Warbands x1", "force-blue"),
      SiteForces.Imperial(1, "Imperial Warbands") -> ("Imperial Warbands x1", "force-empire"),
      SiteForces.Bandit(3, "Bandit Warbands") -> ("Bandit Warbands x3", "force-bandit")
    )
    cases.foreach { case (forces, (label, cssClass)) =>
      assertEquals(ServerUiSupport.forceText(forces), label)
      assertEquals(ServerUiSupport.forceCssClass(forces), cssClass)
    }

  test("board target classes distinguish candidate selected and read-only state"):
    assertEquals(ServerUiSupport.siteTargetClasses(false, false),
      "site site-readonly")
    assertEquals(ServerUiSupport.siteTargetClasses(true, false),
      "site board-target")
    assertEquals(ServerUiSupport.siteTargetClasses(true, true),
      "site board-target board-target-selected")

  test("round tracker geometry is eight circular ring wedges"):
    val segments = (1 to 8).map(WorldBoardRenderer.roundSegment)
    assertEquals(segments.map(_.path).distinct.size, 8)
    segments.foreach { segment =>
      assert(segment.path.startsWith("M "))
      assertEquals(" A ".r.findAllIn(segment.path).length, 2)
      assert(segment.path.contains(" L "))
      assert(segment.path.endsWith(" Z"))
      assert(segment.labelX >= 30.0 && segment.labelX <= 170.0)
      assert(segment.labelY >= 30.0 && segment.labelY <= 170.0)
    }
    val limiter = segments(3)
    val markerRadius = Math.hypot(limiter.markerX - 100.0, limiter.markerY - 100.0)
    val labelRadius = Math.hypot(limiter.labelX - 100.0, limiter.labelY - 100.0)
    assert(markerRadius > labelRadius)

  test("winner banner class resolves the winner's stable color token"):
    assertEquals(ServerUiSupport.winnerColorClass(
      projection(Set.empty), "red-exile"), "player-red")
    assertEquals(ServerUiSupport.winnerColorClass(
      projection(Set.empty), "missing"), "player-neutral")

  test("visible target detail badges contain details without duplicating names"):
    val candidate = BoardTargetCandidate(BoardTargetRef.Site("site:a"),
      "Ancient City", Vector("2 Supply", "+1 warband"))
    val badge = ServerUiSupport.candidateDetailText(candidate)
    assertEquals(badge, Some("2 Supply · +1 warband"))
    assert(!badge.get.contains(candidate.label))
    assertEquals(ServerUiSupport.candidateDetailText(candidate.copy(details = Vector.empty)), None)

  test("peeked site relics replace opaque slots only for the scoped viewer"):
    val known = CardDetails("R1", "relic", "Ancient Crown", rulesText = Some("Rule"))
    val owner = SiteCardPresentation.from(GameSite("site", "Site", 0, 0, 0, 2,
      Vector.empty, GameSiteRelics(2, Vector(known))))
    val other = SiteCardPresentation.from(GameSite("site", "Site", 0, 0, 0, 2,
      Vector.empty, GameSiteRelics(2)))
    assertEquals(owner.unknownRelicCount, 1)
    assertEquals(other.unknownRelicCount, 2)
    assertEquals(owner.peekedRelics.map(_.card.name), Vector("Ancient Crown"))
    assertEquals(other.peekedRelics, Vector.empty)

  test("pile symbols and shape classes distinguish public tops and empty piles"):
    assertEquals(ServerUiSupport.pileSymbol(2, Some("denizen")), "D")
    assertEquals(ServerUiSupport.pileSymbol(1, Some("vision")), "V")
    assertEquals(ServerUiSupport.pileSymbol(1, Some("relic")), "R")
    assertEquals(ServerUiSupport.pileSymbol(3, None), "")
    assertEquals(ServerUiSupport.pileSymbol(0, Some("denizen")), "")
    assertEquals(ServerUiSupport.pileCardClasses(2), "pile-card pile-back")
    assertEquals(ServerUiSupport.pileCardClasses(0), "pile-card pile-empty")

  test("action kinds sort into major and minor, and trade variants share a family"):
    assertEquals(ServerUiSupport.actionCategory("travel"), "major")
    assertEquals(ServerUiSupport.actionCategory("challenge"), "major")
    // Using a power's "Action:" is itself a minor action, so an unrecognised
    // kind joins them rather than opening a section of its own.
    assertEquals(ServerUiSupport.actionCategory("unrecognized-power"), "minor")
    assertEquals(Vector("trade-favor", "trade-secret").map(
      ServerUiSupport.actionFamily), Vector("trade", "trade"))
