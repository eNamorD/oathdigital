package oathdigital.gameplay

import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote, CardPlay,
  CardPlayRules, PlacementLimit, RuleNotes, VisionRules}
import oathdigital.gameplay.actions.cardplay.{CardSlot, CardSlots,
  MoreDiscards}
import oathdigital.gameplay.operations.{AncientBloodline, HallOfMinisters}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, PlacementPicks, Table, TestCards}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** `CardSlots`: where a card may be played, as the options of its placement
  * decision (card play slots design, "Slot builder"). `atAncientCity` stands
  * p1 at Ancient City (Cradle, room for three, no Homeland), with no adviser,
  * unless a test says otherwise; a discard there goes to the Provinces.
  */
class CardSlotsSuite extends munit.FunSuite:
  import PlacementFixture.{secondTestPermission, testPermission}

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
  /** Faceup, it does not count toward the adviser limit. */
  private val piper = CatalogNames.denizen("Pied Piper")
  private val ignoresPiper = CardPlayRules.default
    .ignoreLimit(AdviserFace(piper, Orientation.FaceUp))
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val pile = DecisionOption.DiscardPile(
    DecisionOptionRef.DiscardPile(Region.Provinces))
  private val play = DecisionOption.Button(CardSlots.play, "Play")
  private val spot = DecisionOption.RevealedVision(
    DecisionOptionRef.RevealedVision(p1))
  private val permitted = CardPlayRules.default.permitSiteDiscard(testPermission)

  private def slotsOf(ready: ReadyGame, played: WorldCardId = card,
      origin: CardPlay.Origin = CardPlay.Origin.TemporaryHand,
      rules: CardPlayRules = CardPlayRules.default,
      picked: Vector[CardId] = Vector.empty): Vector[CardSlot] =
    CardSlots.slots(catalog, ready, p1, played, origin, rules, picked)

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
    PlacementPicks.siteSlotOptions(options(slots))

  /** The adviser play options of `slots` with that facing, in order. */
  private def advisers(slots: Vector[CardSlot], faceup: Boolean)
      : Vector[DecisionOption] =
    options(slots).filter(_.ref match
      case DecisionOptionRef.AdviserPlay(_, _, up) => up == faceup
      case _ => false)

  /** The quiet table with p1's pawn stood at Ancient City, where the site
    * slots below are. */
  private def atAncientCity: Table = Table.start.pawn(p1, home)

  /** Ancient City holds `cards`. */
  private def atHome(cards: DenizenId*): Table =
    cards.foldLeft(atAncientCity)((table, held) =>
      table.denizen(held, at = home))

  /** p1 holds `held` as facedown advisers. */
  private def holding(held: DenizenId*): Table =
    held.foldLeft(atAncientCity)(_.adviser(p1, _, facedown = true))

  /** p1 rules Deep Woods, stands there with `played` in hand, and Deep Woods
    * holds `cards`. */
  private def atWoods(played: DenizenId, cards: DenizenId*): ReadyGame =
    cards.foldLeft(Table.start.pawn(p1, at = woods).warbandsAt(woods, p1, 1)
      .hand(p1, played))((table, held) => table.denizen(held, at = woods)).ready

  test("an empty table offers the discard pile, the first empty site slot " +
      "and the first adviser slot each way, in that order"):
    assertEquals(options(slotsOf(atHome().hand(p1, card).ready)),
      Vector(pile, siteSlot(0), adviserPlay(0, faceup = true),
        adviserPlay(0, faceup = false)))

  test("a site with room offers only its first empty slot when nothing " +
      "permits a discard"):
    val ready = atHome(rainBoots, binding).hand(p1, card).ready
    assertEquals(atSite(slotsOf(ready)), Vector(siteSlot(2)))

  test("a permission marks each site card Replace, after the empty slot"):
    val ready = atHome(rainBoots, binding).hand(p1, card).ready
    val slots = slotsOf(ready, rules = permitted)
    assertEquals(atSite(slots), Vector(siteSlot(2),
      replacing(siteSlot(0), rainBoots), replacing(siteSlot(1), binding)))
    assertEquals(slots.find(_.option == replacing(siteSlot(0), rainBoots))
      .map(_.placement), Some(SearchPlacement.Site(Some(rainBoots))))

  test("a full site is offered only as Replace slots, and not at all " +
      "without a permission"):
    val full = atHome(rainBoots, binding, wrestlers).hand(p1, card).ready
    assertEquals(atSite(slotsOf(full)), Vector.empty)
    assertEquals(atSite(slotsOf(full, rules = permitted)), Vector(
      replacing(siteSlot(0), rainBoots), replacing(siteSlot(1), binding),
      replacing(siteSlot(2), wrestlers)))

  test("the Homeland suit is the one the site prints"):
    assertEquals(CardPlay.homelandSuit(catalog, woods), Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, home), None)
    // A variant Ancient City printing an Order Homeland keeps its handlers.
    val city = present(catalog.site(home), "Ancient City")
    val variant = catalog.copy(sites = catalog.sites.map(printed =>
      if printed.id == city.id then
        TestCards.siteLike(city)(homeland = Some(Suit.Order))
      else printed))
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
    val hallAtHome = atHome(rainBoots).hand(p1, card)
      .edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).ready
    assertEquals(atSite(slotsOf(hallAtHome, rules = permitted)), Vector(siteSlot(2),
      replacing(siteSlot(0), rainBoots), locked(replacing(siteSlot(1), hall))))
    def full(lockedFacedown: Boolean) = holding(rainBoots, binding)
      .hand(p1, card).adviser(p1, sealingWard, facedown = lockedFacedown).ready
    assertEquals(advisers(slotsOf(full(lockedFacedown = false)), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding),
        locked(replacing(adviserPlay(2, faceup = true), sealingWard))))
    assertEquals(advisers(slotsOf(full(lockedFacedown = true)), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding),
        replacing(adviserPlay(2, faceup = true), sealingWard)))

  test("a card picked as a modifier this action is refused as a Replace " +
      "slot"):
    val crop = CatalogNames.denizen("Crop Rotation")
    val ready = atHome(crop).hand(p1, card).ready
    assertEquals(atSite(slotsOf(ready, rules = permitted,
      picked = Vector(crop))), Vector(siteSlot(1),
      refused(replacing(siteSlot(0), crop),
        "In use as a modifier this action")))

  /** p1 stands at Deep Woods with Threatening Roar, a Beast card, so Deep
    * Woods' Homeland permits a discard; the intact Hall and two plain cards
    * stand there, with `ruled` deciding who rules it. */
  private def guarded(ruled: Table => Table): ReadyGame =
    val roar = CatalogNames.denizen("Threatening Roar")
    ruled(Vector("Fae Merchant", "Second Chance").map(CatalogNames.denizen(_))
      .foldLeft(Table.start.pawn(p1, at = woods).hand(p1, roar))(
        _.denizen(_, at = woods))
      .edifice(CatalogNames.edifice("Hall of Ministers"), EdificeSide.Intact,
        at = woods)).ready

  private def askingP2(replaced: DenizenId): OptionReason =
    OptionReason.Askable(HallOfMinisters.id,
      HallOfMinisters.noDiscardConsent(HallOfMinisters.source),
      Vector(ConsentAsk(p2, Vector(HallOfMinisters.id))),
      Vector(ConsentKey(HallOfMinisters.id, p1, HallOfMinisters.discardKey,
        DecisionOptionRef.Denizen(replaced))))

  /** p1 stands at Deep Woods, full with three plain cards, with Threatening
    * Roar, a Beast card, in hand. p2 rules Deep Woods and holds Ancient
    * Bloodline faceup. */
  private def fullWoodsBesideBloodlineHolder: ReadyGame =
    val roar = CatalogNames.denizen("Threatening Roar")
    Vector("Fae Merchant", "Second Chance", "Wrestlers")
      .map(CatalogNames.denizen(_))
      .foldLeft(Table.start.pawn(p1, at = woods).hand(p1, roar))(
        _.denizen(_, at = woods))
      .warbandsAt(woods, p2, 1)
      .adviser(p2, CatalogNames.denizen("Ancient Bloodline")).ready

  private def askingBloodlineHolder(replaced: DenizenId): OptionReason =
    OptionReason.Askable(AncientBloodline.id,
      AncientBloodline.lockedConsent(AncientBloodline.source),
      Vector(ConsentAsk(p2, Vector(AncientBloodline.id))),
      Vector(ConsentKey(AncientBloodline.id, p1, AncientBloodline.moveKey,
        DecisionOptionRef.Denizen(replaced))))

  test("Ancient Bloodline makes its holder's site cards askable of the " +
      "holder in a Replace"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fillers = Vector("Fae Merchant", "Second Chance", "Wrestlers")
      .map(CatalogNames.denizen(_))
    assertEquals(atSite(slotsOf(fullWoodsBesideBloodlineHolder, roar)),
      fillers.zipWithIndex.map((filler, index) => DecisionOption.Unavailable(
        replacing(siteSlot(index, woods), filler),
        askingBloodlineHolder(filler))))

  test("the Hall of Ministers makes its ruler's site cards askable of that " +
      "ruler, protects them from everyone under bandits, and binds no ruler " +
      "of its own"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fillers = Vector("Fae Merchant", "Second Chance")
      .map(CatalogNames.denizen(_))
    def slotsAt(ruled: Table => Table) =
      atSite(slotsOf(guarded(ruled), roar))
    assertEquals(slotsAt(_.warbandsAt(woods, p2, 1)), Vector(
      DecisionOption.Unavailable(replacing(siteSlot(0, woods), fillers(0)),
        askingP2(fillers(0))),
      DecisionOption.Unavailable(replacing(siteSlot(1, woods), fillers(1)),
        askingP2(fillers(1))),
      locked(replacing(siteSlot(2, woods), hall))))
    val hallProtects = "Protected by the Hall of Ministers"
    assertEquals(slotsAt(_.bandits(woods, 2)), Vector(
      refused(replacing(siteSlot(0, woods), fillers(0)), hallProtects),
      refused(replacing(siteSlot(1, woods), fillers(1)), hallProtects),
      locked(replacing(siteSlot(2, woods), hall))))
    assertEquals(slotsAt(_.warbandsAt(woods, p1, 1)), Vector(
      replacing(siteSlot(0, woods), fillers(0)),
      replacing(siteSlot(1, woods), fillers(1)),
      locked(replacing(siteSlot(2, woods), hall))))

  test("a grant remembered for a site card opens its Replace slot; a " +
      "refusal leaves the Hall's reason"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fae = CatalogNames.denizen("Fae Merchant")
    val ready = guarded(_.warbandsAt(woods, p2, 1))
    def remembering(answer: ConsentAnswer) = ready.copy(consent =
      ConsentState(answers = Map(ConsentKey(HallOfMinisters.id, p1,
        HallOfMinisters.discardKey, DecisionOptionRef.Denizen(fae)) ->
        answer.by(p2)), turn = Some(ConsentTurn.of(ready))))
    assertEquals(atSite(slotsOf(remembering(ConsentAnswer.Granted), roar))
      .head, replacing(siteSlot(0, woods), fae))
    assertEquals(atSite(slotsOf(remembering(ConsentAnswer.Refused), roar))
      .head, refused(replacing(siteSlot(0, woods), fae),
        "Protected by the Hall of Ministers"))

  test("a full adviser area is offered only as Replace, each adviser, each " +
      "facing"):
    val ready = holding(rainBoots, binding, wrestlers).hand(p1, card).ready
    Vector(true, false).foreach { faceup =>
      assertEquals(advisers(slotsOf(ready), faceup), Vector(
        replacing(adviserPlay(0, faceup), rainBoots),
        replacing(adviserPlay(1, faceup), binding),
        replacing(adviserPlay(2, faceup), wrestlers)), s"faceup = $faceup")
    }

  test("a lowered limit offers fewer slots, and nothing at or past it"):
    val limited = CardPlayRules.default.limitAdvisers(2)
    val twoHeld = holding(rainBoots, binding).hand(p1, card)
    assertEquals(advisers(slotsOf(twoHeld.ready), faceup = true),
      Vector(adviserPlay(2, faceup = true)))
    assertEquals(advisers(slotsOf(twoHeld.ready, rules = limited),
      faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding)))
    val three = twoHeld.adviser(p1, wrestlers, facedown = true).ready
    Vector(true, false).foreach(faceup =>
      assertEquals(advisers(slotsOf(three, rules = limited), faceup),
        Vector.empty, s"faceup = $faceup"))

  test("the played card's own limit holds only when it is played faceup"):
    val rules = CardPlayRules.default.limitWhileFaceUp(card, 2)
    val twoHeld = holding(rainBoots, binding).hand(p1, card).ready
    assertEquals(advisers(slotsOf(twoHeld, rules = rules), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding)))
    assertEquals(advisers(slotsOf(twoHeld, rules = rules), faceup = false),
      Vector(adviserPlay(2, faceup = false)))

  test("a played card that will not count takes a free slot at the limit, " +
      "and only that slot"):
    val full = holding(rainBoots, binding, wrestlers).hand(p1, piper).ready
    val slots = slotsOf(full, piper, rules = ignoresPiper)
    assertEquals(advisers(slots, faceup = true),
      Vector(adviserPlay(3, faceup = true)))
    // Facedown it has no power, so it counts.
    assertEquals(advisers(slots, faceup = false), Vector(
      replacing(adviserPlay(0, faceup = false), rainBoots),
      replacing(adviserPlay(1, faceup = false), binding),
      replacing(adviserPlay(2, faceup = false), wrestlers)))

  test("at the limit, an adviser that does not count is shown unavailable " +
      "for Replace"):
    val ready = atAncientCity.adviser(p1, piper)
      .adviser(p1, rainBoots, facedown = true)
      .adviser(p1, binding, facedown = true)
      .adviser(p1, wrestlers, facedown = true).hand(p1, card).ready
    assertEquals(advisers(slotsOf(ready, rules = ignoresPiper),
      faceup = true), Vector(
      refused(replacing(adviserPlay(0, faceup = true), piper),
        "Does not count toward the adviser limit"),
      replacing(adviserPlay(1, faceup = true), rainBoots),
      replacing(adviserPlay(2, faceup = true), binding),
      replacing(adviserPlay(3, faceup = true), wrestlers)))

  test("a play that replaces the card setting the limit is checked without " +
      "that limit"):
    // Wrestlers, faceup, limits p1 to two, and p1 holds three: only
    // replacing Wrestlers leaves an area within the limit.
    val rules = CardPlayRules.default.limitWhileFaceUp(wrestlers, 2)
    val ready = atAncientCity.adviser(p1, wrestlers)
      .adviser(p1, rainBoots, facedown = true)
      .adviser(p1, binding, facedown = true).hand(p1, card).ready
    assertEquals(advisers(slotsOf(ready, rules = rules), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), wrestlers)))

  test("a Vision never goes to a site, and faceup it goes to the revealed " +
      "Vision spot, Replace when one is there"):
    val faith = VisionRules.Faith
    assertEquals(options(slotsOf(atAncientCity.hand(p1, faith).ready, faith)),
      Vector(pile, spot, adviserPlay(0, faceup = false)))
    val over = atAncientCity.hand(p1, faith)
      .revealedVision(p1, VisionRules.Conquest).ready
    assertEquals(options(slotsOf(over, faith)).filter(_.ref == spot.ref),
      Vector(replacing(spot, VisionRules.Conquest)))

  test("a faceup Conspiracy is Play, and takes no slot"):
    val conspiracy = VisionRules.Conspiracy
    val ready = atAncientCity.hand(p1, conspiracy)
      .revealedVision(p1, VisionRules.Faith).ready
    val slots = slotsOf(ready, conspiracy)
    assertEquals(options(slots), Vector(pile, play,
      adviserPlay(0, faceup = false)))
    val played = present(slots.find(_.ref == CardSlots.play), "Play")
    assertEquals(CardPlay.operations(catalog, ready, p1, conspiracy,
      played.placement, CardPlay.Origin.TemporaryHand), Right(Vector.empty))

  test("a site-only card goes to advisers facedown only; an adviser-only " +
      "card never to a site"):
    assertEquals(options(slotsOf(atAncientCity.hand(p1, curfew).ready, curfew)),
      Vector(pile, siteSlot(0), adviserPlay(0, faceup = false)))
    assertEquals(options(slotsOf(atAncientCity.hand(p1, sealingWard).ready,
      sealingWard)), Vector(pile, adviserPlay(0, faceup = true),
      adviserPlay(0, faceup = false)))

  test("a facedown adviser is offered its own slot faceup, and no other " +
      "adviser slot"):
    val ready = atAncientCity.adviser(p1, wrestlers, facedown = true)
      .adviser(p1, rainBoots, facedown = true).ready
    assertEquals(options(slotsOf(ready, rainBoots,
      CardPlay.Origin.FacedownAdviser)),
      Vector(pile, siteSlot(0), adviserPlay(1, faceup = true)))

  test("a facedown Vision goes to the revealed Vision spot, and a facedown " +
      "Conspiracy is Play"):
    val ready = atAncientCity.adviser(p1, VisionRules.Faith, facedown = true)
      .adviser(p1, VisionRules.Conspiracy, facedown = true)
      .revealedVision(p1, VisionRules.Conquest).ready
    assertEquals(options(slotsOf(ready, VisionRules.Faith,
      CardPlay.Origin.FacedownAdviser)),
      Vector(pile, replacing(spot, VisionRules.Conquest)))
    assertEquals(options(slotsOf(ready, VisionRules.Conspiracy,
      CardPlay.Origin.FacedownAdviser)), Vector(pile, play))

  test("a facedown adviser turned faceup must fit its own limit"):
    def ownLimit(held: DenizenId) =
      CardPlayRules.default.limitWhileFaceUp(held, 2)
    val three = Vector(rainBoots, binding, wrestlers)
      .foldLeft(atAncientCity)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(three, wrestlers,
      CardPlay.Origin.FacedownAdviser, ownLimit(wrestlers)), faceup = true),
      Vector.empty)
    val two = Vector(rainBoots, binding)
      .foldLeft(atAncientCity)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(two, binding,
      CardPlay.Origin.FacedownAdviser, ownLimit(binding)), faceup = true),
      Vector(adviserPlay(1, faceup = true)))

  test("a card that lifts the limit, faceup after the play, gives a new " +
      "slot past three; played facedown it lifts nothing"):
    val rules = CardPlayRules.default.liftWhileFaceUp(card)
    val three = holding(rainBoots, binding, wrestlers).ready
    assertEquals(advisers(slotsOf(three, rules = rules), faceup = true),
      Vector(adviserPlay(3, faceup = true)))
    assertEquals(advisers(slotsOf(three, rules = rules), faceup = false),
      Vector(replacing(adviserPlay(0, faceup = false), rainBoots),
        replacing(adviserPlay(1, faceup = false), binding),
        replacing(adviserPlay(2, faceup = false), wrestlers)))

  test("an adviser play after which a note applies carries it and stays " +
      "on offer"):
    val note = OptionReason.Written("Test note")
    val rules = CardPlayRules.default.noteAdviserPlay(AdviserPlayNote(note,
      (_, played) => played.orientation == Orientation.FaceDown))
    val slots = slotsOf(atAncientCity.ready, rules = rules)
    assertEquals(advisers(slots, faceup = true),
      Vector(adviserPlay(0, faceup = true)))
    assertEquals(advisers(slots, faceup = false),
      Vector(DecisionOption.Warned(adviserPlay(0, faceup = false), note)))

  /** Ancient City full: Rain Boots, Ancient Binding and the ruined Hall. */
  private def fullWithHall: ReadyGame = atHome(rainBoots, binding)
    .hand(p1, card).edifice(hall, EdificeSide.Ruined, at = home).ready
  private val twoDiscards = permitted.permitSiteDiscard(secondTestPermission)

  private def replacingBoots(ready: ReadyGame, rules: CardPlayRules)
      : CardSlot = present(slotsOf(ready, rules = rules)
    .find(_.option == replacing(siteSlot(0), rainBoots)), "the Replace slot")

  test("after a Replace site slot, the other site cards are offered, an " +
      "edifice included, up to the permissions left"):
    val ready = fullWithHall
    assertEquals(CardSlots.moreDiscards(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, twoDiscards,
      replacingBoots(ready, twoDiscards)),
      Some(MoreDiscards(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(rainBoots)), Vector(
          DecisionOption.Denizen(DecisionOptionRef.Denizen(binding)),
          DecisionOption.Edifice(DecisionOptionRef.Edifice(hall))), 1)))
    // A single permission is spent by the slot itself.
    assertEquals(CardSlots.moreDiscards(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, permitted,
      replacingBoots(ready, permitted)), None)

  test("the credits name one permission per discard, in the order the " +
      "permissions are given, and refuse more discards than permissions"):
    val ready = fullWithHall
    assertEquals(CardSlots.credits(catalog, ready, p1, card, twoDiscards,
      Vector(hall, rainBoots)), Right(Vector(testPermission.note,
        secondTestPermission.note)))
    assertEquals(CardSlots.credits(catalog, ready, p1, card, permitted,
      Vector(rainBoots, binding)), Left(OathViolation.InvalidSearchPlacement(
        "more site cards are discarded than the permissions in play allow")))

  test("a play discards every site card named, the replaced one first, " +
      "before the play"):
    val ready = atHome(rainBoots, binding).hand(p1, card).ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(rainBoots)), CardPlay.Origin.TemporaryHand,
      Vector(binding)))
    assertEquals(operations.collect { case discard: Discard.Denizen =>
      discard.card }, Vector[CardId](rainBoots, binding))
    assert(operations.indexWhere(_.isInstanceOf[Play]) >
      operations.lastIndexWhere(_.isInstanceOf[Discard.Denizen]))
    assertEquals(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(rainBoots)), CardPlay.Origin.TemporaryHand,
      Vector(rainBoots)), Left(OathViolation.InvalidSearchPlacement(
        "a site card is discarded twice")))

  test("a ruined edifice is discarded back to the edifice deck, not buried"):
    val ready = atHome(rainBoots).hand(p1, card).edifice(hall, EdificeSide.Ruined, at = home)
      .ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand))
    assertEquals(operations.collect { case ruined: Discard.RuinedEdifice =>
      ruined.card }, Vector(hall))
    assertEquals(operations.collect { case bury: Bury => bury }, Vector.empty)

  test("a grant for the replaced card frees the follow-up's other site " +
      "cards: they are discarded with it, at the same time"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fae = CatalogNames.denizen("Fae Merchant")
    val secondChance = CatalogNames.denizen("Second Chance")
    val ready = guarded(_.warbandsAt(woods, p2, 1))
    val granted = ready.copy(consent = ConsentState(answers = Map(
      ConsentKey(HallOfMinisters.id, p1, HallOfMinisters.discardKey,
        DecisionOptionRef.Denizen(fae)) -> ConsentAnswer.Granted.by(p2)),
      turn = Some(ConsentTurn.of(ready))))
    val slot = present(slotsOf(granted, roar, rules = permitted)
      .find(_.option == replacing(siteSlot(0, woods), fae)), "the Replace slot")
    val more = present(CardSlots.moreDiscards(catalog, granted, p1, roar,
      CardPlay.Origin.TemporaryHand, permitted, slot), "the follow-up")
    assert(more.options.contains(DecisionOption.Denizen(
      DecisionOptionRef.Denizen(secondChance))), more.options.toString)
    assertEquals(more.most, 1)

  /** A limit that leaves only plays to a site open. */
  private val siteOnly = CardPlayRules.default.limitPlacements(
    PlacementLimit(OptionReason.Written("Test: site only"), {
      case SearchPlacement.Site(_) => true
      case _ => false
    }))

  test("a placement limit shows each play it refuses unavailable, with its " +
      "reason"):
    assertEquals(options(slotsOf(atHome().hand(p1, card).ready,
      rules = siteOnly)), Vector(refused(pile, "Test: site only"),
      siteSlot(0), refused(adviserPlay(0, faceup = true), "Test: site only"),
      refused(adviserPlay(0, faceup = false), "Test: site only")))

  test("when the limits leave no play open, the discard pile stays open"):
    val full = atHome(rainBoots, binding, wrestlers).hand(p1, card).ready
    assertEquals(options(slotsOf(full, rules = siteOnly)), Vector(pile,
      refused(adviserPlay(0, faceup = true), "Test: site only"),
      refused(adviserPlay(0, faceup = false), "Test: site only")))
