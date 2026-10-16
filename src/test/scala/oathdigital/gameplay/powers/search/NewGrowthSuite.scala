package oathdigital.gameplay.powers.search

import oathdigital.gameplay.FacedownAdviserPlay
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** New Growth (card 188), a selected Search modifier: "You may play
  * [suit-beast] and [suit-hearth] cards to any site (that has space)." */
class NewGrowthSuite extends munit.FunSuite:
  import SearchFixture.{keep, placeAt, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val newGrowth = CatalogNames.denizen("New Growth")
  private val crop = CatalogNames.denizen("Crop Rotation")
  private val beast = CatalogNames.denizen("Errand Boy")
  private val hearth = SearchFixture.anyPlainDenizensOf(Suit.Hearth).head
  private val order = SearchFixture.anyPlainDenizensOf(Suit.Order).head
  private val filler = CatalogNames.denizen("Code of Honor")
  private val home = CatalogNames.site("Ancient City")
  /** Room for one. */
  private val peaks = CatalogNames.site("Broken Peaks")
  private val woods = CatalogNames.site("Deep Woods")

  /** p1 in Act at Ancient City holding New Growth faceup, with 5 Supply and
    * `top` on the world deck. Broken Peaks is full. */
  private def growing(top: DenizenId): Table = Table.start.pawn(p1, home)
    .worldDeckTop(top).supply(p1, 5).adviser(p1, newGrowth)
    .denizen(filler, at = peaks)

  private def toPlacement(ready: ReadyGame, card: DenizenId,
      selected: Vector[PowerId] = Vector(NewGrowth.id)): OathTransition =
    accepted(for
      started <- start(ready, selected)
      kept <- keep(started, card)
    yield kept.copy(events = started.events ++ kept.events))

  private def siteSlotRefs(at: OathTransition): Vector[DecisionOptionRef] =
    PlacementPicks.siteSlotOptions(PlacementPicks.parkedOptions(at.state,
      walkerPowers)).map(_.ref)

  test("selected, a Beast card may go to every site with room, after the " +
      "pawn site in map order; a full site has no option"):
    val ready = growing(beast).ready
    val expected = DecisionOptionRef.SiteSlot(home, 0) +:
      ready.game.current.map.inPlay.filterNot(Set(home, peaks))
        .map(DecisionOptionRef.SiteSlot(_, 0))
    assertEquals(siteSlotRefs(toPlacement(ready, beast)), expected)

  test("a Hearth card may go too, and a card of another suit only to the " +
      "pawn site"):
    assert(siteSlotRefs(toPlacement(growing(hearth).ready, hearth))
      .contains(DecisionOptionRef.SiteSlot(woods, 0)))
    assertEquals(siteSlotRefs(toPlacement(growing(order).ready, order)),
      Vector(DecisionOptionRef.SiteSlot(home, 0)))

  test("with a discard permission, a full site offers its Replace"):
    val ready = growing(beast).adviser(p1, crop).ready
    val options = PlacementPicks.parkedOptions(toPlacement(ready, beast,
      Vector(NewGrowth.id, CropRotation.id)).state, walkerPowers)
    assert(options.exists(option =>
      option.ref == DecisionOptionRef.SiteSlot(peaks, 0) &&
        PlacementPicks.replaces(option).contains(filler)), options.toString)

  test("played at another site, the card stays there, and replays"):
    val ready = growing(beast).ready
    val asked = toPlacement(ready, beast)
    val done = accepted(placeAt(asked, beast,
      DecisionOptionRef.SiteSlot(woods, 0)))
    val end = SearchFixture.after(done)
    assertEquals(Look(end).cardsAt(woods), Vector[CardId](beast))
    assertEquals(Look(end).cardsAt(home), Vector.empty[CardId])
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("it applies to a facedown adviser played to another site"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, newGrowth)
      .adviser(p1, beast, facedown = true).ready
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      Ready(ready), p1, beast, Vector(NewGrowth.id)))
    assert(siteSlotRefs(started).contains(DecisionOptionRef.SiteSlot(woods,
      0)))
