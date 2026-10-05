package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, CardPlayRules,
  DiscardPermission, RuleNotes, VisionRules}
import oathdigital.gameplay.actions.cardplay.{CardSlot, CardSlots,
  MoreDiscards}
import oathdigital.gameplay.powers.search.CropRotation
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table, TestCards}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** `CardSlots`: where a card may be played, as the options of its placement
  * decision (card play slots design, "Slot builder"). p1 stands at Ancient
  * City (Cradle, room for three, no Homeland) on the quiet table, with no
  * adviser, unless a test says otherwise; a discard there goes to the
  * Provinces.
  */
class CardSlotsSuite extends munit.FunSuite:
  import PlacementFixture.testPermission

  private val home = CatalogNames.site("Ancient City")
  /** The Beast Homeland, room for three. */
  private val woods = CatalogNames.site("Deep Woods")
  /** The plain Beast card p1 plays unless a test says otherwise. */
  private val card = CatalogNames.denizen("Errand Boy")
  // Plain cards of other suits.
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val binding = CatalogNames.denizen("Ancient Binding")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** Locked and adviser-only. */
  private val sealingWard = CatalogNames.denizen("Sealing Ward")
  /** Site-only. */
  private val curfew = CatalogNames.denizen("Curfew")
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val pile = DecisionOption.DiscardPile(
    DecisionOptionRef.DiscardPile(Region.Provinces))
  private val play = DecisionOption.Button(CardSlots.play, "Play")
  private val spot = DecisionOption.RevealedVision(
    DecisionOptionRef.RevealedVision(p1))
  /** A second plain site discard permission, besides `testPermission`. */
  private val second = DiscardPermission(PowerId("test.second-discard"),
    Note(PowerId("test.second-discard"), _ => None))
  private val permitted = CardPlayRules.default.permitSiteDiscard(testPermission)

  private def slotsOf(ready: ReadyGame, played: WorldCardId = card,
      origin: CardPlay.Origin = CardPlay.Origin.TemporaryHand,
      rules: CardPlayRules = CardPlayRules.default): Vector[CardSlot] =
    CardSlots.slots(catalog, ready, p1, played, origin, rules)

  private def options(slots: Vector[CardSlot]): Vector[DecisionOption] =
    slots.map(_.option)

  private def siteSlot(index: Int, at: SiteId = home): DecisionOption =
    DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(at, index))

  private def adviserPlay(index: Int, faceup: Boolean): DecisionOption =
    DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(p1, index, faceup))

  private def replacing(option: DecisionOption, replaced: CardId)
      : DecisionOption = DecisionOption.Replacing(option, replaced)

  private def refused(option: DecisionOption, reason: String): DecisionOption =
    DecisionOption.Unavailable(option, OptionReason.Written(reason))

  private def locked(option: DecisionOption): DecisionOption =
    refused(option, "Locked: cannot be discarded")

  /** The site slot options of `slots`, in order. */
  private def atSite(slots: Vector[CardSlot]): Vector[DecisionOption] =
    options(slots).filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])

  /** The adviser play options of `slots` with that facing, in order. */
  private def advisers(slots: Vector[CardSlot], faceup: Boolean)
      : Vector[DecisionOption] =
    options(slots).filter(_.ref match
      case DecisionOptionRef.AdviserPlay(_, _, up) => up == faceup
      case _ => false)

  /** p1 holds Errand Boy in hand at Ancient City, which holds `cards`. */
  private def atHome(cards: DenizenId*): Table =
    cards.foldLeft(Table.start.hand(p1, card))((table, held) =>
      table.denizen(held, at = home))

  /** p1 holds Errand Boy in hand and `held` as facedown advisers. */
  private def holding(held: DenizenId*): Table =
    held.foldLeft(Table.start.hand(p1, card))(_.adviser(p1, _, facedown = true))

  /** p1 rules Deep Woods, stands there with `played` in hand, and Deep Woods
    * holds `cards`. */
  private def atWoods(played: DenizenId, cards: DenizenId*): ReadyGame =
    cards.foldLeft(Table.start.pawn(p1, at = woods).warbandsAt(woods, p1, 1)
      .hand(p1, played))((table, held) => table.denizen(held, at = woods)).ready

  test("an empty table offers the discard pile, the first empty site slot " +
      "and the first adviser slot each way, in that order"):
    assertEquals(options(slotsOf(atHome().ready)), Vector(pile, siteSlot(0),
      adviserPlay(0, faceup = true), adviserPlay(0, faceup = false)))

  test("a site with room offers only its first empty slot when nothing " +
      "permits a discard"):
    assertEquals(atSite(slotsOf(atHome(rainBoots, binding).ready)),
      Vector(siteSlot(2)))

  test("a permission marks each site card Replace, after the empty slot"):
    val slots = slotsOf(atHome(rainBoots, binding).ready, rules = permitted)
    assertEquals(atSite(slots), Vector(siteSlot(2),
      replacing(siteSlot(0), rainBoots), replacing(siteSlot(1), binding)))
    assertEquals(slots.find(_.option == replacing(siteSlot(0), rainBoots))
      .map(_.placement), Some(SearchPlacement.Site(Some(rainBoots))))

  test("a full site is offered only as Replace slots, and not at all " +
      "without a permission"):
    val full = atHome(rainBoots, binding, wrestlers).ready
    assertEquals(atSite(slotsOf(full)), Vector.empty)
    assertEquals(atSite(slotsOf(full, rules = permitted)), Vector(
      replacing(siteSlot(0), rainBoots), replacing(siteSlot(1), binding),
      replacing(siteSlot(2), wrestlers)))

  test("the Homeland suit is the one the site prints"):
    assertEquals(CardPlay.homelandSuit(catalog, woods), Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, home), None)
    // A variant Ancient City printing an Order Homeland keeps its handlers.
    val city = present(catalog.site(home), "Ancient City")
    val variant = catalog.copy(sites = catalog.sites.map(site =>
      if site.id == city.id then
        TestCards.siteLike(city)(homeland = Some(Suit.Order))
      else site))
    assertEquals(CardPlay.homelandSuit(variant, city.id), Some(Suit.Order))

  test("playing a card of the Homeland's suit there may replace any card at " +
      "that site, whatever its suit"):
    // Errand Boy is Beast and Deep Woods the Beast Homeland. Rain Boots is
    // Nomad and Wolves Beast: the replaced card's suit plays no part.
    val wolves = CatalogNames.denizen("Wolves")
    val beast = atWoods(card, rainBoots, wolves)
    assertEquals(atSite(slotsOf(beast)), Vector(siteSlot(2, woods),
      replacing(siteSlot(0, woods), rainBoots),
      replacing(siteSlot(1, woods), wolves)))
    assertEquals(CardSlots.permissions(catalog, beast, p1, card,
      CardPlayRules.default).map(_.source), Vector(RuleNotes.homelandDiscard))

  test("playing a card of another suit at a Homeland may replace nothing"):
    // Rain Boots is Nomad. The Beast Wolves at the site lends it no suit.
    val wolves = CatalogNames.denizen("Wolves")
    assertEquals(atSite(slotsOf(atWoods(rainBoots, wolves), rainBoots)),
      Vector(siteSlot(1, woods)))

  test("an intact edifice and a faceup locked adviser are shown Locked; a " +
      "facedown locked adviser is not"):
    val site = atHome(rainBoots).edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).ready
    assertEquals(atSite(slotsOf(site, rules = permitted)), Vector(siteSlot(2),
      replacing(siteSlot(0), rainBoots), locked(replacing(siteSlot(1), hall))))
    def full(lockedFacedown: Boolean) = holding(rainBoots, binding)
      .adviser(p1, sealingWard, facedown = lockedFacedown).ready
    assertEquals(advisers(slotsOf(full(lockedFacedown = false)), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding),
        locked(replacing(adviserPlay(2, faceup = true), sealingWard))))
    assert(advisers(slotsOf(full(lockedFacedown = true)), faceup = true)
      .forall(_.available))

  test("a card in use as a modifier shows why it stays"):
    val crop = CatalogNames.denizen("Crop Rotation")
    val ready = atHome(crop).update(_.updateCurrent(
      _.copy(walkerModifiers = Vector(CropRotation.id)))).ready
    assertEquals(atSite(slotsOf(ready, rules = permitted)), Vector(siteSlot(1),
      refused(replacing(siteSlot(0), crop), "In use as a modifier this action")))

  test("the Hall of Ministers protects its ruler's site from an enemy, and " +
      "says so"):
    // Threatening Roar is Beast, so Deep Woods' Homeland permits a discard.
    val roar = CatalogNames.denizen("Threatening Roar")
    val fillers = Vector("Fae Merchant", "Second Chance")
      .map(CatalogNames.denizen(_))
    def ruledBy(ruler: PlayerId) = fillers.foldLeft(Table.start
      .pawn(p1, at = woods).hand(p1, roar))(_.denizen(_, at = woods))
      .edifice(hall, EdificeSide.Intact, at = woods)
      .warbandsAt(woods, ruler, 1).ready
    val hallProtects = "Protected by the Hall of Ministers"
    assertEquals(atSite(slotsOf(ruledBy(p2), roar)), Vector(
      refused(replacing(siteSlot(0, woods), fillers(0)), hallProtects),
      refused(replacing(siteSlot(1, woods), fillers(1)), hallProtects),
      locked(replacing(siteSlot(2, woods), hall))))
    assert(atSite(slotsOf(ruledBy(p1), roar)).take(2).forall(_.available))

  test("a full adviser area is offered only as Replace, each adviser, each " +
      "facing"):
    val ready = holding(rainBoots, binding, wrestlers).ready
    Vector(true, false).foreach { faceup =>
      assertEquals(advisers(slotsOf(ready), faceup), Vector(
        replacing(adviserPlay(0, faceup), rainBoots),
        replacing(adviserPlay(1, faceup), binding),
        replacing(adviserPlay(2, faceup), wrestlers)), s"faceup = $faceup")
    }

  test("a lowered limit offers fewer slots, and nothing at or past it"):
    val limited = CardPlayRules.default.limitAdvisers(2)
    val two = holding(rainBoots, binding)
    assertEquals(advisers(slotsOf(two.ready), faceup = true),
      Vector(adviserPlay(2, faceup = true)))
    assertEquals(advisers(slotsOf(two.ready, rules = limited), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding)))
    val three = two.adviser(p1, wrestlers, facedown = true).ready
    Vector(true, false).foreach(faceup =>
      assertEquals(advisers(slotsOf(three, rules = limited), faceup),
        Vector.empty, s"faceup = $faceup"))

  test("the played card's own limit lowers only its faceup plays"):
    val rules = CardPlayRules.default.limitPlayedCard(2)
    val two = holding(rainBoots, binding).ready
    assertEquals(advisers(slotsOf(two, rules = rules), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding)))
    assertEquals(advisers(slotsOf(two, rules = rules), faceup = false),
      Vector(adviserPlay(2, faceup = false)))

  test("a Vision never goes to a site, and faceup it goes to the revealed " +
      "Vision spot, Replace when one is there"):
    val faith = VisionRules.Faith
    assertEquals(options(slotsOf(Table.start.hand(p1, faith).ready, faith)),
      Vector(pile, spot, adviserPlay(0, faceup = false)))
    val over = Table.start.hand(p1, faith)
      .revealedVision(p1, VisionRules.Conquest).ready
    assertEquals(options(slotsOf(over, faith)).filter(_.ref == spot.ref),
      Vector(replacing(spot, VisionRules.Conquest)))

  test("a faceup Conspiracy is Play, and takes no slot"):
    val conspiracy = VisionRules.Conspiracy
    val ready = Table.start.hand(p1, conspiracy)
      .revealedVision(p1, VisionRules.Faith).ready
    val slots = slotsOf(ready, conspiracy)
    assertEquals(options(slots), Vector(pile, play,
      adviserPlay(0, faceup = false)))
    val played = present(slots.find(_.ref == CardSlots.play), "Play")
    assertEquals(CardPlay.operations(catalog, ready, p1, conspiracy,
      played.placement, CardPlay.Origin.TemporaryHand), Right(Vector.empty))

  test("a site-only card goes to advisers facedown only; an adviser-only " +
      "card never to a site"):
    assertEquals(options(slotsOf(Table.start.hand(p1, curfew).ready, curfew)),
      Vector(pile, siteSlot(0), adviserPlay(0, faceup = false)))
    assertEquals(options(slotsOf(Table.start.hand(p1, sealingWard).ready,
      sealingWard)), Vector(pile, adviserPlay(0, faceup = true),
      adviserPlay(0, faceup = false)))

  test("a facedown adviser is offered its own slot faceup, and no other " +
      "adviser slot"):
    val ready = Table.start.adviser(p1, wrestlers, facedown = true)
      .adviser(p1, rainBoots, facedown = true).ready
    assertEquals(options(slotsOf(ready, rainBoots,
      CardPlay.Origin.FacedownAdviser)),
      Vector(pile, siteSlot(0), adviserPlay(1, faceup = true)))

  test("a facedown Vision goes to the revealed Vision spot, and a facedown " +
      "Conspiracy is Play"):
    val ready = Table.start.adviser(p1, VisionRules.Faith, facedown = true)
      .adviser(p1, VisionRules.Conspiracy, facedown = true)
      .revealedVision(p1, VisionRules.Conquest).ready
    assertEquals(options(slotsOf(ready, VisionRules.Faith,
      CardPlay.Origin.FacedownAdviser)),
      Vector(pile, replacing(spot, VisionRules.Conquest)))
    assertEquals(options(slotsOf(ready, VisionRules.Conspiracy,
      CardPlay.Origin.FacedownAdviser)), Vector(pile, play))

  test("a facedown adviser turned faceup must fit its own limit"):
    val rules = CardPlayRules.default.limitPlayedCard(2)
    val three = Vector(rainBoots, binding, wrestlers)
      .foldLeft(Table.start)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(three, wrestlers,
      CardPlay.Origin.FacedownAdviser, rules), faceup = true), Vector.empty)
    val two = Vector(rainBoots, binding)
      .foldLeft(Table.start)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(two, binding,
      CardPlay.Origin.FacedownAdviser, rules), faceup = true),
      Vector(adviserPlay(1, faceup = true)))

  /** Ancient City full: Rain Boots, Ancient Binding and the ruined Hall. */
  private def fullWithHall: ReadyGame = atHome(rainBoots, binding)
    .edifice(hall, EdificeSide.Ruined, at = home).ready
  private val two = permitted.permitSiteDiscard(second)

  private def replacingBoots(ready: ReadyGame, rules: CardPlayRules)
      : CardSlot = present(slotsOf(ready, rules = rules)
    .find(_.option == replacing(siteSlot(0), rainBoots)), "the Replace slot")

  test("after a Replace site slot, the other site cards are offered, an " +
      "edifice included, up to the permissions left"):
    val ready = fullWithHall
    assertEquals(CardSlots.moreDiscards(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, two, replacingBoots(ready, two)),
      Some(MoreDiscards(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(rainBoots)), Vector(
          DecisionOption.Denizen(DecisionOptionRef.Denizen(binding)),
          DecisionOption.Edifice(DecisionOptionRef.Edifice(hall))), 1)))
    // A single permission is spent by the slot itself.
    assertEquals(CardSlots.moreDiscards(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, permitted,
      replacingBoots(ready, permitted)), None)

  test("the credits name the permission each discard spent, in discard " +
      "order, and refuse more discards than permissions"):
    val ready = fullWithHall
    assertEquals(CardSlots.credits(catalog, ready, p1, card, two,
      Vector(hall, rainBoots)), Right(Vector(testPermission.note,
        second.note)))
    assert(CardSlots.credits(catalog, ready, p1, card, permitted,
      Vector(rainBoots, binding)).isLeft)

  test("a play discards every site card named, the replaced one first, " +
      "before the play"):
    val ready = atHome(rainBoots, binding).ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(rainBoots)), CardPlay.Origin.TemporaryHand,
      Vector(binding)))
    assertEquals(operations.collect { case discard: Discard.Denizen =>
      discard.card }, Vector[CardId](rainBoots, binding))
    assert(operations.indexWhere(_.isInstanceOf[Play]) >
      operations.lastIndexWhere(_.isInstanceOf[Discard.Denizen]))
    assert(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(rainBoots)), CardPlay.Origin.TemporaryHand,
      Vector(rainBoots)).isLeft, "a card is discarded twice")

  test("a ruined edifice is discarded back to the edifice deck, not buried"):
    val ready = atHome(rainBoots).edifice(hall, EdificeSide.Ruined, at = home)
      .ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand))
    assert(operations.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!operations.exists(_.isInstanceOf[Bury]))
