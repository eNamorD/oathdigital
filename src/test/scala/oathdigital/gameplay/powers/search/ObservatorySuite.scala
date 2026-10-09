package oathdigital.gameplay.powers.search

import oathdigital.gameplay.SearchPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Observatory (card 64): while the actor's pawn is at its site, every
  * region's discard pile is offered, each at 2 Supply (catalog batch 6,
  * slice 3). */
class ObservatorySuite extends munit.FunSuite:
  import SearchFixture.rules

  private val observatory = CatalogNames.denizen("Observatory")
  private val modifiers = Vector(Observatory.id)
  /** Three plain denizens in each region's discard pile; none is played. */
  private val piles: Map[Region, Vector[DenizenId]] = Map(
    Region.Cradle -> Vector("Threatening Roar", "Fae Merchant",
      "Second Chance"),
    Region.Provinces -> Vector("Wrestlers", "Mercenaries", "Homesteaders"),
    Region.Hinterland -> Vector("Defame", "Scryer", "Blackmail"))
    .view.mapValues(_.map(CatalogNames.denizen(_))).toMap
  /** The world deck, then each region's pile in region order. */
  private val everySource: Vector[DecisionOptionRef] =
    (SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_)))
      .map(SearchSource.ref)

  /** p1 at Ancient City (Cradle), which holds Observatory, with 5 Supply
    * and three cards in each region's discard pile. */
  private def besideObservatory: Table = Region.all.foldLeft(Table.start
      .pawn(p1, "Ancient City").denizen(observatory, at = "Ancient City")
      .supply(p1, 5))((table, region) => table.discarded(region,
    piles(region)*))

  test("picked, every region's discard pile is offered at 2 Supply"):
    val shown = ShownOptions.sourcesAfterPicking(besideObservatory.ready,
      modifiers)
    assertEquals(shown.map(option => (option.kind, option.id, option.state,
      option.badge)), everySource.map(ref => (ref.kind, ref.wireId,
      "available", Some("2 Supply"))))

  test("a Search of another region's pile draws its top three for 2 " +
      "Supply"):
    val searched = accepted(SearchPlay.searched(rules,
      Ready(besideObservatory.ready), p1,
      SearchSource.RegionalDiscard(Region.Provinces), modifiers))
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands(p1),
      piles(Region.Provinces).reverse)
    assertEquals(Look(after).supply(p1), 5 - 2)

  test("it is offered only while p1's pawn is at its site"):
    val offered = (ready: ReadyGame) =>
      accepted(rules.offerableWalkerPowers(ready, p1, ActionRef.Search))
        .map(_.id)
    assert(offered(besideObservatory.ready).contains(Observatory.id))
    assert(!offered(besideObservatory.pawn(p1, "Broken Peaks").ready)
      .contains(Observatory.id))
