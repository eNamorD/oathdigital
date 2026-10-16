package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, CardPlayRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.{CardPlayProcedure, CardSlots}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.operations.{HallOfMinisters,
  OperationRestrictions}
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** The card play walk (card play slots design, "Placement procedure"): the
  * slot choice, the follow-up discard and the play. p1 stands at Ancient City
  * (Cradle, room for three, no Homeland); a discard there goes to the
  * Provinces.
  */
class CardPlayProcedureSuite extends munit.FunSuite:
  import PlacementFixture.{build, cardPlayDecisionId, park, rulePower,
    testPermission}

  private val actor = p1
  private val home = CatalogNames.site("Ancient City")
  // An unrestricted denizen whose power these tests do not use.
  private val card = CatalogNames.denizen("Errand Boy")
  // Three more unrestricted denizens, for full adviser areas and sites.
  private val others = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))
  // Each is printed Locked and adviser-only.
  private val locked = Vector("Faithful Friend", "A Small Favor",
    "Family Heirloom").map(CatalogNames.denizen(_))
  private val noPowers = WalkerPowers.empty
  private val placeId = cardPlayDecisionId(card, "place")
  private val discardId = cardPlayDecisionId(card, "discard")
  /** Two site discard permissions at once. */
  private val twoPermissions = rulePower("test.two-discards")(
    _.permitSiteDiscard(testPermission).permitSiteDiscard(testPermission))

  /** The quiet table with p1's pawn at Ancient City, stated here because
    * the slots and the discard region follow the pawn. */
  private def atHome: Table = Table.start.pawn(actor, at = home)

  /** p1 holds Errand Boy in a temporary hand, at Ancient City. */
  private def handState: ReadyGame = atHome.hand(actor, card).ready

  /** p1 holds Errand Boy in hand, and Ancient City is full of `others`. */
  private def fullSite: ReadyGame = others.foldLeft(atHome
    .hand(actor, card))(_.denizen(_, at = home)).ready

  /** Errand Boy's card play, under `powers`, parked on its placement. */
  private final case class PlacementPark(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers):
    def resolve(at: PendingTree, id: String, answer: DecisionAnswer)
        : Either[OathViolation, WalkerOutcome] =
      ProcedureWalker.resolve(ready, tree, at, Answered(id, answer, actor),
        powers)

    def place(ref: DecisionOptionRef): Either[OathViolation, WalkerOutcome] =
      resolve(pending, placeId, DecisionAnswer.ChooseOneAnswer(ref))

  private def parkOnPlacement(ready: ReadyGame,
      powers: WalkerPowers = noPowers)(using munit.Location): PlacementPark =
    val tree = build(ready, actor, card)
    PlacementPark(ready, tree, park(ready, tree, powers), powers)

  /** The decision the play asks first, inside its one placement body. */
  private def placementDecision(tree: Operation): Decide =
    as[Decide](present(tree.children.headOption.flatMap(_.children.headOption),
      "the placement decision"))

  test("the placement decision offers the card's slots"):
    val ready = handState
    assertEquals(placementDecision(build(ready, actor, card)).query,
      DecisionQuery.ChooseOne(CardSlots.slots(catalog, ready, actor, card,
        CardPlay.Origin.TemporaryHand, CardPlayRules.default).map(_.option),
        heading = Some("Play the card")))

  test("card absent from temporary hand cannot build card play"):
    val absent = Table.start.ready
    assertEquals(CardPlayProcedure.build(catalog, absent, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).left.toOption,
      Some(OathViolation.InvalidSearchPlacement(
        "card is not held at the selected origin")))

  test("an empty site slot plays the card there and empties the hand"):
    val done = Outcomes.finished(parkOnPlacement(handState)
      .place(DecisionOptionRef.SiteSlot(home, 0)))
    assertEquals(Look(done.treeless).cardsAt(home), Vector[CardId](card))
    assertEquals(done.treeless.game.current.temporaryHands(actor),
      Vector.empty)

  test("the discard pile discards the card to the region after the pawn's"):
    val done = Outcomes.finished(parkOnPlacement(handState)
      .place(DecisionOptionRef.DiscardPile(Region.Provinces)))
    assertEquals(done.treeless.game.current.commonCards
      .discard(Region.Provinces).last, card)

  test("a Replace adviser slot discards that adviser and plays the card, " +
      "asking nothing more"):
    val full = others.foldLeft(atHome.hand(actor, card))(
      _.adviser(actor, _, facedown = true)).ready
    val done = Outcomes.finished(parkOnPlacement(full)
      .place(DecisionOptionRef.AdviserPlay(actor, 0, faceup = true)))
    val advisers = Look(done.treeless).player(actor).advisers
    assertEquals(advisers.size, 3)
    assert(advisers.exists(_.id == card))
    assert(!advisers.exists(_.id == others.head))

  test("an unavailable Replace slot is refused"):
    val full = locked.foldLeft(atHome.hand(actor, card))(
      _.adviser(actor, _)).ready
    // The slot is offered, shown unavailable, and an answer naming it is
    // refused as one the decision does not offer.
    val offered = placementDecision(build(full, actor, card)).query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")
    assert(offered.exists(option => !option.available &&
      option.ref == DecisionOptionRef.AdviserPlay(actor, 0, faceup = true)))
    assertEquals(parkOnPlacement(full)
      .place(DecisionOptionRef.AdviserPlay(actor, 0, faceup = true))
      .left.toOption, Some(OathViolation.InvalidEventOrder(
        s"decision $placeId does not offer the selected option")))

  test("a Replace site slot with a permission left asks for more discards, " +
      "the replaced card locked"):
    val parked = parkOnPlacement(fullSite, WalkerPowers(Vector(twoPermissions)))
    val asked = Outcomes.parked(parked.place(DecisionOptionRef.SiteSlot(home, 0)))
    val more = present(ProcedureWalker.parkedDecide(parked.ready, parked.tree,
      asked.tree, parked.powers), "the follow-up")
    assertEquals(more.decisionId, discardId)
    def option(id: DenizenId) = DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
    assertEquals(more.query, DecisionQuery.ChooseMany(0, 1,
      others.tail.map(option),
      heading = Some("Discard more cards from the site"),
      locked = Vector(option(others.head)),
      confirm = Some(CountedConfirm("Discard", "more card", "more cards",
        Some("Discard no more")))))

  test("the follow-up's discards go before the play, and choosing none " +
      "discards only the replaced card"):
    val parked = parkOnPlacement(fullSite, WalkerPowers(Vector(twoPermissions)))
    val asked = Outcomes.parked(parked.place(DecisionOptionRef.SiteSlot(home, 0)))
    val more = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector(
        DecisionOptionRef.Denizen(others(1))))))
    assertEquals(Look(more.treeless).cardsAt(home),
      Vector[CardId](others(2), card))
    val none = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector.empty)))
    assertEquals(Look(none.treeless).cardsAt(home),
      Vector[CardId](others(1), others(2), card))

  test("a grant for the replaced card lets the follow-up discard another " +
      "card the Hall of Ministers protects: a play's discards happen at " +
      "the same time"):
    // Errand Boy is Beast, so Deep Woods' Homeland permits a discard, and
    // the stub a second one; p2 rules Deep Woods and its intact Hall.
    val woods = CatalogNames.site("Deep Woods")
    val hall = CatalogNames.edifice("Hall of Ministers")
    val guarded = Table.start.pawn(actor, at = woods).hand(actor, card)
      .denizen(others(1), at = woods).denizen(others(2), at = woods)
      .edifice(hall, EdificeSide.Intact, at = woods)
      .warbandsAt(woods, p2, 1).ready
    val granted = guarded.copy(consent = ConsentState(answers = Map(
      ConsentKey(HallOfMinisters.id, actor, HallOfMinisters.discardKey,
        DecisionOptionRef.Denizen(others(1))) -> ConsentAnswer.Granted.by(p2)),
      turn = Some(ConsentTurn.of(guarded))))
    val parked = parkOnPlacement(granted, WalkerPowers(
      Vector(PlacementFixture.discardFirst),
      restrictionSet = OperationRestrictions.forCatalog(catalog)))
    val asked = Outcomes.parked(parked.place(
      DecisionOptionRef.SiteSlot(woods, 0)))
    val done = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector(
        DecisionOptionRef.Denizen(others(2))))))
    assertEquals(Look(done.treeless).cardsAt(woods),
      Vector[CardId](hall, card))

  test("a Replace site slot that spends the only permission asks nothing more"):
    val powers = WalkerPowers(Vector(PlacementFixture.discardFirst))
    val done = Outcomes.finished(parkOnPlacement(fullSite, powers)
      .place(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(Look(done.treeless).cardsAt(home),
      Vector[CardId](others(0), others(2), card))

  test("a picked facedown adviser parks on the shared walker placement tree"):
    // p1 holds a facedown Wrestlers to play.
    val adviser = CatalogNames.denizen("Wrestlers")
    val ready = atHome.adviser(actor, adviser, facedown = true).ready
    val rules = new OathRules(catalog)
    val walkerParked = new ParkedDecisionAssertions(catalog)
    val started = FacedownAdviserPlay.parkedOnPlacement(rules,
      OathState.Ready(ready), actor, adviser)
    walkerParked.assertParked(accepted(started).state,
      ActionRef.PlayFacedownAdviser,
      PlacementFixture.cardPlayDecisionId(adviser, "place"), actor)

  test("playedAs names how each placement option plays the card"):
    val refs = Vector[DecisionOptionRef](
      DecisionOptionRef.DiscardPile(Region.Cradle),
      DecisionOptionRef.SiteSlot(home, 0),
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = true),
      DecisionOptionRef.RevealedVision(actor), CardSlots.play,
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = false))
    assertEquals(refs.map(CardPlayProcedure.playedAs), Vector(
      Some(PlayedAs.Discarded), Some(PlayedAs.AtSite), Some(PlayedAs.Faceup),
      Some(PlayedAs.Faceup), Some(PlayedAs.Faceup), Some(PlayedAs.Facedown)))
    // The follow-up answers with site cards, which play nothing.
    assertEquals(CardPlayProcedure.playedAs(
      DecisionOptionRef.Denizen(others.head)), None)

  /** A pending walk that answered decision `id` with the discard pile. */
  private def pendingFor(id: String): PendingTree =
    PendingTree(at = Vector.empty, answered = Vector(Answered(id,
      DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.DiscardPile(Region.Provinces)), actor)))

  test("placementDecisionId and placedCard round-trip a denizen, and the " +
      "follow-up's id is spelled the same way"):
    val denizen = CatalogNames.denizen("Book Binders")
    val denizenId = CardPlayProcedure.placementDecisionId(denizen)
    assertEquals(denizenId, "cardplay.place.denizen.140")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(denizenId)),
      Some(denizen))
    assertEquals(CardPlayProcedure.discardDecisionId(denizen),
      "cardplay.discard.denizen.140")

  test("placementDecisionId and placedCard round-trip a Vision whose value " +
      "contains ':'"):
    val vision = VisionRules.Faith
    val visionId = CardPlayProcedure.placementDecisionId(vision)
    assertEquals(visionId, s"cardplay.place.vision.${vision.value}")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(visionId)),
      Some(vision))

  test("placementDecisionId and placedCard round-trip a value that " +
      "contains '.'"):
    // Only the FIRST '.' after the prefix is the kind/value split, since no
    // kind contains one.
    val dotted = VisionId("vision:has.a.dot")
    val dottedId = CardPlayProcedure.placementDecisionId(dotted)
    assertEquals(dottedId, s"cardplay.place.vision.${dotted.value}")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(dottedId)),
      Some(dotted))

  test("a Replace at another site asks its follow-up over that site's " +
      "cards"):
    val woods = CatalogNames.site("Deep Woods")
    val opensWoods = rulePower("test.opens-woods")(_
      .openSites(PlacementFixture.opening(woods))
      .permitSiteDiscard(testPermission).permitSiteDiscard(testPermission))
    val ready = others.foldLeft(atHome.hand(actor, card))(
      _.denizen(_, at = woods)).ready
    val parked = parkOnPlacement(ready, WalkerPowers(Vector(opensWoods)))
    val asked = Outcomes.parked(parked.place(
      DecisionOptionRef.SiteSlot(woods, 0)))
    val more = present(ProcedureWalker.parkedDecide(parked.ready,
      parked.tree, asked.tree, parked.powers), "the follow-up")
    assertEquals(more.decisionId, discardId)
    val done = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector(
        DecisionOptionRef.Denizen(others(1))))))
    assertEquals(Look(done.treeless).cardsAt(woods),
      Vector[CardId](others(2), card))
    assertEquals(Look(done.treeless).cardsAt(home), Vector.empty[CardId])
