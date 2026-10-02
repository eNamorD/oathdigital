package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table, TestCards}
import oathdigital.testkit.Table.p1

/** The Homeland site power (CR p. 31): "When playing a card of its Homeland
  * suit to this site, you may discard a card from the site first (even one of
  * matching suit)." The site prints its Homeland suit, and the discard is
  * offered whether or not the site is full.
  */
class HomelandRuleSuite extends munit.FunSuite:
  /** The Beast Homeland, with room for three cards. */
  private val deepWoods = "Deep Woods"
  // Plain, unrestricted cards: two Beast, the rest of other suits.
  private val (errandBoy, wolves) = ("Errand Boy", "Wolves")
  private val (rainBoots, ancientBinding, wrestlers, battleHonors) =
    ("Rain Boots", "Ancient Binding", "Wrestlers", "Battle Honors")

  private def siteChoice(ready: ReadyGame, card: String) =
    CardPlay.legalChoices(catalog, ready, p1, CatalogNames.denizen(card),
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])

  /** p1 stands at Deep Woods, which p1 rules and which holds `cards`, with
    * `card` in hand to play. */
  private def atDeepWoods(card: String, cards: String*): ReadyGame =
    cards.foldLeft(Table.start
      .pawn(p1, at = deepWoods).warbandsAt(deepWoods, p1, 1)
      .hand(p1, card))((table, held) => table.denizen(held, at = deepWoods))
      .ready

  test("the Homeland suit is the one the site prints"):
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:deep-woods")),
      Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:ancient-city")), None)
    // A variant Ancient City printing an Order Homeland keeps its handlers.
    val city = catalog.site(SiteId("site:ancient-city")).get
    val variant = catalog.copy(sites = catalog.sites.map(site =>
      if site.id == city.id then
        TestCards.siteLike(city)(homeland = Some(Suit.Order))
      else site))
    assertEquals(CardPlay.homelandSuit(variant, city.id), Some(Suit.Order))

  test("a matching Homeland with room offers an optional discard"):
    val choice = siteChoice(atDeepWoods(errandBoy, rainBoots), errandBoy).get
    assertEquals(choice.replacements,
      Vector[CardId](CatalogNames.denizen(rainBoots)))
    assert(choice.replacementOptional)

  test("a card of another suit is offered no discard at a Homeland with room"):
    val choice = siteChoice(atDeepWoods(rainBoots, ancientBinding), rainBoots).get
    assertEquals(choice.replacements, Vector.empty)
    assert(!choice.replacementOptional)

  test("a full matching Homeland requires a discard, even of a matching card"):
    val fillers = Vector(wolves, rainBoots, ancientBinding)
    val choice = siteChoice(atDeepWoods(errandBoy, fillers*), errandBoy).get
    assertEquals(choice.replacements.toSet,
      fillers.map(CatalogNames.denizen(_): CardId).toSet)
    assert(!choice.replacementOptional)

  test("a full Homeland refuses a card of another suit"):
    val ready = atDeepWoods(rainBoots, ancientBinding, wrestlers, battleHonors)
    assertEquals(siteChoice(ready, rainBoots), None)

  test("an edifice of the card's suit does not make a site a Homeland"):
    // Ancient City is no Homeland; the Order Hall of Ministers and two cards
    // fill it, so an Order card has nowhere to go without a discard.
    val home = Table.homeOf(p1)
    assertEquals(CardPlay.homelandSuit(catalog, home), None)
    val ready = Table.start
      .warbandsAt(home, p1, 1)
      .hand(p1, wrestlers)
      .denizen(rainBoots, at = home).denizen(ancientBinding, at = home)
      .edifice("Hall of Ministers", EdificeSide.Intact, at = home)
      .ready
    assertEquals(siteChoice(ready, wrestlers), None)
