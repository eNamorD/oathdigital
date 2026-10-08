package oathdigital.application

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.search.{Disciples, NewsFromAfar}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Outcomes, PowerStubs, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What the parked source decision shows each pile as: its cost under the
  * selected modifiers, or why it cannot be searched (search source design,
  * rulings 3 and 4). p1 stands at Ancient City, in the Cradle. */
class SearchSourceRowsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val home = CatalogNames.site("Ancient City")

  /** The source rows p1 is shown, by pile id ("world", "cradle"), once a
    * Search with `modifiers` starts at `ready` under `powers`. */
  private def rows(ready: ReadyGame, modifiers: Vector[PowerId] = Vector.empty,
      powers: WalkerPowers = WalkerPowerCatalog.default(catalog))
      (using munit.Location): Map[String, DecisionOptionProjection] =
    val rules = new OathRules(catalog, walkerPowerCatalog = powers)
    val projector = new WalkerDecisionProjector(catalog, presentation, powers)
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1, modifiers))
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

  test("an empty world deck says it is empty"):
    // The whole world deck goes to the Provinces discard, which p1 cannot
    // search; one card in the Cradle discard lets the Search start.
    val stocked = Table.start.discarded(Region.Cradle, "Wrestlers")
      .supply(p1, 5)
    val deck = stocked.unchecked.game.current.commonCards.worldDeck
    val shown = rows(stocked.discarded(Region.Provinces, deck*).ready)
    assertEquals(shown("world").state, "unavailable")
    assertEquals(shown("world").reason, Some("The world deck is empty"))
    assertEquals(shown("cradle").state, "available")

  test("a pile refused for no reason the pricing knows says it cannot be " +
      "searched now"):
    // A stub fails every Search of a discard pile once the pile is picked,
    // whatever Supply and favor p1 holds, so neither a shortfall nor an
    // empty pile explains the refusal.
    val closed = PowerStubs.TransformStub(PowerId("test.discard-closed"),
      PowerWindow.SearchBeforeDraw, (ctx, children) =>
        if ctx.answered.exists(_.answer == DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.DiscardPile(Region.Cradle)))
        then Vector(BuildOps((_, _) => Left(OathViolation.InvalidEventOrder(
          "test: the Cradle discard pile is closed"))))
        else children)
    val shown = rows(stocked(supply = 5).ready, powers = WalkerPowers(
      Vector(closed)))
    assertEquals(shown("cradle").state, "unavailable")
    assertEquals(shown("cradle").reason, Some("Cannot search there now"))
    assertEquals(shown("world").badge, Some("2 Supply"))

  test("a single-card draw reads its cost once, though each way to place " +
      "the card is previewed"):
    // A Vision on top stops the draw at one card, so the walk goes straight
    // to placing it, faceup or discarded, each costing the same 2 Supply.
    val ready = Table.start.worldDeckTop(VisionRules.Faith).supply(p1, 5).ready
    assertEquals(rows(ready)("world").badge, Some("2 Supply"))
