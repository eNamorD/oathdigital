package oathdigital.application

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.protocol.projection.{CardDetailsProjection, DecisionOptionProjection, DecisionQueryProjection}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.present

/** How a card slot option reads in the Action panel. Plan 1 shows the slots
  * as labelled rows; plan 2's board summary reuses the same words.
  */
class SlotOptionRowsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val projector = new WalkerDecisionProjector(catalog, presentation,
    WalkerPowers(Vector.empty))
  /** Cradle, room for three, no Homeland. */
  private val home = CatalogNames.site("Ancient City")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val roar = CatalogNames.denizen("Threatening Roar")

  /** Wrestlers lies at Ancient City; p1 holds Threatening Roar facedown and
    * Conquest as their revealed Vision. */
  private lazy val board: ReadyGame = Table.start.denizen(wrestlers, at = home)
    .adviser(p1, roar, facedown = true)
    .revealedVision(p1, VisionRules.Conquest).ready

  private def rowOf(option: DecisionOption, viewer: PlayerId = p1)(
      using munit.Location): DecisionOptionProjection =
    present(projector.optionProjection(board, Some(viewer),
      CardIndex.from(board.game).toOption, option), "a projected row")

  private def site: String = presentation.siteLabel(home)

  test("an empty site slot names the site"):
    val row = rowOf(DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals((row.kind, row.label, row.replaces),
      ("site-slot", s"Empty slot at $site", None))

  test("a Replace site slot names the card it discards and carries it"):
    val row = rowOf(DecisionOption.Replacing(
      DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 0)), wrestlers))
    assertEquals(row.label, s"Replace Wrestlers at $site")
    assertEquals(row.replaces.map(_.cardId), Some(wrestlers.value))

  test("a site slot past the site's capacity is not presentable"):
    assertEquals(projector.optionProjection(board, Some(p1), None,
      DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 3))), None)

  test("an adviser play names its slot and its facing"):
    assertEquals(rowOf(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 1, faceup = true))).label,
      "Adviser slot 2, faceup")
    assertEquals(rowOf(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 1, faceup = false))).label,
      "Adviser slot 2, facedown")

  test("a Replace adviser play names a facedown adviser to its owner only"):
    val option = DecisionOption.Replacing(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)), roar)
    assertEquals(rowOf(option).label, "Replace Threatening Roar, facedown")
    val seenByOther = rowOf(option, viewer = p2)
    assert(seenByOther.replaces.exists(_.hidden))
    assert(!seenByOther.label.contains("Threatening Roar"))

  test("the revealed Vision spot, and Replace naming the Vision there"):
    val spot = DecisionOption.RevealedVision(
      DecisionOptionRef.RevealedVision(p1))
    assertEquals(rowOf(spot).label, "Revealed Vision")
    assertEquals(rowOf(DecisionOption.Replacing(spot, VisionRules.Conquest))
      .label, s"Replace ${presentation.cardLabel(board, VisionRules.Conquest)}" +
        " as revealed Vision")

  test("the discard pile names its region"):
    assertEquals(rowOf(DecisionOption.DiscardPile(
      DecisionOptionRef.DiscardPile(Region.Provinces))).label,
      "Discard to the Provinces discard")

  test("an adviser play carries its facing as the word its toggle shows"):
    assertEquals(Vector(true, false).map(faceup => rowOf(
      DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(p1, 1,
        faceup))).variant), Vector(Some("Faceup"), Some("Facedown")))
    assertEquals(rowOf(DecisionOption.SiteSlot(
      DecisionOptionRef.SiteSlot(home, 1))).variant, None)

  test("a card play's Replace options read as the board's summary, and " +
      "every other option keeps its label"):
    val played = CardDetailsProjection("denizen:spirit", "denizen",
      "Wayward Spirit")
    val site = rowOf(DecisionOption.Replacing(DecisionOption.SiteSlot(
      DecisionOptionRef.SiteSlot(home, 0)), wrestlers))
    val adviser = rowOf(DecisionOption.Replacing(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)), roar))
    val empty = rowOf(DecisionOption.SiteSlot(
      DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(projector.replacing(DecisionQueryProjection.ChooseOne(
      Vector(site, adviser, empty)), played),
      DecisionQueryProjection.ChooseOne(Vector(
        site.copy(label = "Discard Wrestlers; play Wayward Spirit here"),
        adviser.copy(label =
          "Discard Threatening Roar; play Wayward Spirit here, facedown"),
        empty)))

  test("a Replace option whose card the viewer may not identify keeps " +
      "its label"):
    val played = CardDetailsProjection("denizen:spirit", "denizen",
      "Wayward Spirit")
    val unseen = rowOf(DecisionOption.Replacing(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)), roar),
      viewer = p2)
    val query = DecisionQueryProjection.ChooseOne(Vector(unseen))
    assertEquals(projector.replacing(query, played), query)

  test("a Replace option naming an edifice reads the face's name, not the " +
      "card's id"):
    val forge = CatalogNames.edifice("Broken Forge")
    val withEdifice = Table.start.edifice(forge, EdificeSide.Ruined, at = home)
      .ready
    val row = present(projector.optionProjection(withEdifice, Some(p1),
      CardIndex.from(withEdifice.game).toOption, DecisionOption.Replacing(
        DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 0)), forge)),
      "a projected row")
    assertEquals(row.label, s"Replace Broken Forge at $site")
    assertEquals(row.replaces.map(card => (card.name, card.cardKind)),
      Some(("Broken Forge", "edifice")))
