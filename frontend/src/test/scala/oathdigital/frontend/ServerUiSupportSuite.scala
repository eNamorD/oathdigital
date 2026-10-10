package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.twoSeatProjection
import oathdigital.frontend.TestSites.*
import oathdigital.model.PlayerColor

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

  test("round tracker geometry is eight circular ring wedges"):
    // The tracker is drawn on a 200 by 200 viewBox, so its center is
    // (100, 100); a round label sits 68 from it, inside the 30 to 170 band.
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
    // The segments start at round 1, so index 3 is round 4. The marker the
    // tracker draws on a limiter round sits farther out than its label.
    val roundFour = segments(3)
    val markerRadius = Math.hypot(roundFour.markerX - 100.0,
      roundFour.markerY - 100.0)
    val labelRadius = Math.hypot(roundFour.labelX - 100.0,
      roundFour.labelY - 100.0)
    assert(markerRadius > labelRadius)

  test("winner banner class resolves the winner's stable color token"):
    assertEquals(ServerUiSupport.winnerColorClass(
      twoSeatProjection(), "red-exile"), "player-red")
    assertEquals(ServerUiSupport.winnerColorClass(
      twoSeatProjection(), "missing"), "player-neutral")

  test("peeked site relics replace opaque slots only for the scoped viewer"):
    val known = CardDetails("R1", "relic", "Ancient Crown", rulesText = Some("Rule"))
    val site = quietSite("site", "Site").withDenizenCapacity(0)
      .withRelicCapacity(2)
    val owner = SiteCardPresentation.from(site.withRelics(2, known))
    val other = SiteCardPresentation.from(site.withRelics(2))
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
