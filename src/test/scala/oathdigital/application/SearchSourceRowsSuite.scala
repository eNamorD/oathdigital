package oathdigital.application

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.search.{Disciples, NewsFromAfar}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What the parked source decision shows each pile as: its cost under the
  * selected modifiers, or why it cannot be searched (search source design,
  * rulings 3 and 4). p1 stands at Ancient City, in the Cradle. */
class SearchSourceRowsSuite extends munit.FunSuite:
  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))
  private val home = CatalogNames.site("Ancient City")

  /** The source rows p1 is shown, by pile id ("world", "cradle"), once a
    * Search with `modifiers` starts at `ready`. */
  private def rows(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty)
      (using munit.Location): Map[String, DecisionOptionProjection] =
    val started = accepted(SearchFixture.rules.startWalker(Ready(ready),
      ActionRef.Search, p1, modifiers))
    present(projector.project(ScopedProjectionContext(
      Outcomes.ready(started.state), Some(p1))), "the source decision")
      .query.toVector.flatMap(_.offeredOptions).map(row => row.id -> row).toMap

  /** Both piles hold cards, and p1 has `supply` Supply. */
  private def stocked(supply: Int): Table = Table.start
    .worldDeckTop("Wrestlers", "Errand Boy", "Mercenaries")
    .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant",
      "Second Chance")
    .supply(p1, supply)

  test("each pile is badged with the Supply its Search spends, under its " +
      "own name"):
    val shown = rows(stocked(supply = 5).visionsDrawn(1).ready)
    assertEquals(shown("world").badge, Some("3 Supply"))
    assertEquals(shown("cradle").badge, Some("2 Supply"))
    assertEquals(shown("world").label, "World deck")
    assertEquals(shown("cradle").label, "Cradle discard pile")

  test("News from Afar makes both piles read 0 Supply, and its own favor is " +
      "not in the badge"):
    val ready = stocked(supply = 5)
      .adviser(p1, CatalogNames.denizen("News from Afar")).favor(p1, 2).ready
    val shown = rows(ready, Vector(NewsFromAfar.id))
    assertEquals(shown("world").badge, Some("0 Supply"))
    assertEquals(shown("cradle").badge, Some("0 Supply"))

  test("Disciples lowers a world Search costing 3 to 2"):
    val ready = stocked(supply = 5).adviser(p1, CatalogNames.denizen("Disciples"))
      .darkestSecret(Some(p1), 1).visionsDrawn(1).ready
    assertEquals(rows(ready, Vector(Disciples.id))("world").badge,
      Some("2 Supply"))
    assertEquals(rows(ready)("world").badge, Some("3 Supply"))

  test("Forced Labor's toll joins the badge of both piles"):
    val peaks = CatalogNames.site("Broken Peaks")
    val ready = stocked(supply = 5)
      .denizen(CatalogNames.denizen("Forced Labor"), at = peaks)
      .warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1).ready
    val shown = rows(ready)
    assertEquals(shown("world").badge, Some("2 Supply · 1 favor"))
    assertEquals(shown("cradle").badge, Some("2 Supply · 1 favor"))

  test("a pile the actor cannot afford says how much it needs"):
    // One Vision drawn puts the world deck at 3 Supply.
    val shown = rows(stocked(supply = 2).visionsDrawn(1).ready)
    assertEquals(shown("world").state, "unavailable")
    assertEquals(shown("world").reason, Some("Needs 3 Supply; you have 2"))
    assertEquals(shown("cradle").state, "available")

  test("an empty pile says it is empty rather than what it would cost"):
    // The Cradle discard is empty on the quiet table.
    val ready = Table.start.worldDeckTop("Wrestlers").supply(p1, 5).ready
    val shown = rows(ready)
    assertEquals(shown("cradle").state, "unavailable")
    assertEquals(shown("cradle").reason,
      Some("The Cradle discard pile is empty"))
