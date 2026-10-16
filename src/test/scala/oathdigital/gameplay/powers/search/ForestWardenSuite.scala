package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{PlacementFixture, PowerAccess}
import oathdigital.gameplay.actions.{CardPlayRules, RuleNotes}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Forest Warden (card 218), a selected Search modifier: "If you play a
  * [suit-beast] card to a site, you may bury a card at the site first." */
class ForestWardenSuite extends munit.FunSuite:
  import SearchFixture.{keep, placeAt, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val warden = CatalogNames.denizen("Forest Warden")
  private val modifiers = Vector(ForestWarden.id)
  private val beast = CatalogNames.denizen("Errand Boy")
  private val order = SearchFixture.anyPlainDenizensOf(Suit.Order).head
  private val filler = CatalogNames.denizen("Code of Honor")
  private val horn = CatalogNames.relic("Cracked Horn")
  private val home = CatalogNames.site("Ancient City")
  /** The Beast Homeland: its rule gives a discard as well. */
  private val woods = CatalogNames.site("Deep Woods")

  /** p1 in Act at `at` holding Forest Warden faceup, with 5 Supply and
    * `top` on the world deck. */
  private def wardenHeldWithDeckTop(top: DenizenId, at: SiteId = home): Table =
    Table.start.pawn(p1, at).worldDeckTop(top).supply(p1, 5)
      .adviser(p1, warden)

  private def toPlacement(ready: ReadyGame, card: DenizenId = beast,
      selected: Vector[PowerId] = modifiers): OathTransition = accepted(for
    started <- start(ready, selected)
    kept <- keep(started, card)
  yield kept.copy(events = started.events ++ kept.events))

  private def parkedOptions(at: OathTransition): Vector[DecisionOption] =
    PlacementPicks.parkedOptions(at.state, walkerPowers)

  private def wardenSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(ForestWarden.id, Vector(CardPlayRules.buryFirst), events)

  test("selected, a Beast card's Replace is Bury and replace, and the bury " +
      "sends the card to the bottom of the world deck with the line"):
    val ready = wardenHeldWithDeckTop(beast).denizen(filler, at = home)
      .tokens(filler, favor = 1).ready
    val asked = toPlacement(ready)
    assert(parkedOptions(asked).exists(o =>
      PlacementPicks.buries(o).contains(filler)), parkedOptions(asked).toString)
    val done = accepted(placeReplacing(asked, beast, PlayedAs.AtSite, filler))
    val end = SearchFixture.after(done)
    assertEquals(end.game.current.commonCards.worldDeck.lastOption,
      Some(filler))
    assertEquals(Look(end).cardsAt(home), Vector[CardId](beast))
    // Code of Honor is Order: its favor returns to the Order bank.
    assertEquals(end.banks.favor.getOrElse(Suit.Order, 0),
      ready.banks.favor.getOrElse(Suit.Order, 0) + 1)
    assertEquals(wardenSaid(done.events), Vector(NoteText.Said("bury-first",
      s"May bury a card at ${home.value} first.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("a card of another suit gets no bury"):
    val asked = toPlacement(wardenHeldWithDeckTop(order)
      .denizen(filler, at = home).ready, order)
    assert(!parkedOptions(asked).exists(o => PlacementPicks.buries(o)
      .nonEmpty), parkedOptions(asked).toString)

  test("bury ignores locks: an intact edifice and a card Ancient Bloodline " +
      "locks may be buried, though a discard may not take them"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val ready = wardenHeldWithDeckTop(beast)
      .edifice(hall, EdificeSide.Intact, at = home)
      .denizen(filler, at = home).warbandsAt(home, p2, 1)
      .adviser(p2, CatalogNames.denizen("Ancient Bloodline"))
      .adviser(p1, CatalogNames.denizen("Crop Rotation")).ready
    assert(PowerAccess.ruledSites(ready, p2).contains(home))
    // Crop Rotation's discard alone may take neither card.
    val discarding = parkedOptions(toPlacement(ready,
      selected = Vector(CropRotation.id)))
    Vector[CardId](hall, filler).foreach(card =>
      assert(discarding.exists(o => PlacementPicks.replaces(o).contains(card)
        && !o.available), discarding.toString))
    val both = parkedOptions(toPlacement(ready,
      selected = Vector(ForestWarden.id, CropRotation.id)))
    val buried = both.filter(_.available).flatMap(PlacementPicks.buries)
    assertEquals(buried.toSet, Set[CardId](hall, filler))

  test("at a site with room, a relic there is Bury and play"):
    val ready = wardenHeldWithDeckTop(beast).relicAt(horn, at = home).ready
    val asked = toPlacement(ready)
    val done = accepted(placeAt(asked, beast,
      DecisionOptionRef.SiteRelicSlot(home, 0)))
    val end = SearchFixture.after(done)
    assertEquals(end.game.current.commonCards.relicDeck.lastOption,
      Some(horn))
    assertEquals(Look(end).cardsAt(home), Vector[CardId](beast))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("with the Homeland's discard too, Replace asks: Discard discards " +
      "with the Homeland's line"):
    val ready = wardenHeldWithDeckTop(beast, at = woods)
      .denizen(filler, at = woods).ready
    val asked = toPlacement(ready)
    val placed = accepted(placeReplacing(asked, beast, PlayedAs.AtSite,
      filler))
    val done = accepted(rules.resolveWalker(placed.state, p1,
      CardPlayProcedure.replacedDecisionId(beast),
      DecisionAnswer.ChooseOneAnswer(CardPlayProcedure.discardReplaced)))
    val end = SearchFixture.after(done)
    assert(Look(end).discardAfter(woods).contains(filler))
    val events = asked.events ++ placed.events ++ done.events
    assertEquals(ActPowerHarness.replayed(rules, ready, events), end)
    assertWireRoundTrips(events)
    assertEquals(wardenSaid(placed.events ++ done.events), Vector.empty)
    assertEquals(NoteText.said(RuleNotes.homelandDiscard,
      Vector(CardPlayRules.discardFirst), placed.events ++ done.events),
      Vector(NoteText.Said("discard-first",
        s"May discard a card at ${woods.value} first.", covers = false)))

  test("a facedown relic at another site is offered by its slot, never by " +
      "its name"):
    val ready = wardenHeldWithDeckTop(beast)
      .adviser(p1, CatalogNames.denizen("New Growth"))
      .relicAt(horn, at = woods).ready
    val asked = toPlacement(ready, selected = Vector(ForestWarden.id,
      NewGrowth.id))
    val query = PlacementFixture.projectedPlacement(asked, p1)
    val row = query.offeredOptions.find(o => o.kind == "site-relic-slot" &&
      o.id == s"${woods.value}:0")
    assert(row.exists(r => r.buries && r.card.exists(_.hidden) &&
      !r.label.contains("Horn")), query.toString)
