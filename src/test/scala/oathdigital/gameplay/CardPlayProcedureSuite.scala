package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, CardPlayRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.{CardPlayProcedure, CardSlots}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** The card play walk (card play slots design, "Placement procedure"): the
  * slot choice, the follow-up discard and the play. p1 stands at Ancient City
  * (Cradle, room for three, no Homeland) on the quiet table; a discard there
  * goes to the Provinces.
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

  /** p1 holds Errand Boy in a temporary hand, on the quiet table. */
  private def handState: ReadyGame = Table.start.hand(actor, card).ready

  /** p1 holds Errand Boy in hand, and Ancient City is full of `others`. */
  private def fullSite: ReadyGame = others.foldLeft(Table.start
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

  private def placementDecision(tree: Operation): Decide =
    as[Decide](present(tree.children.headOption, "the placement decision"))

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
    val full = others.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _, facedown = true)).ready
    val done = Outcomes.finished(parkOnPlacement(full)
      .place(DecisionOptionRef.AdviserPlay(actor, 0, faceup = true)))
    val advisers = Look(done.treeless).player(actor).advisers
    assertEquals(advisers.size, 3)
    assert(advisers.exists(_.id == card))
    assert(!advisers.exists(_.id == others.head))

  test("an unavailable Replace slot is refused"):
    val full = locked.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _)).ready
    // The slot is offered, shown unavailable, and an answer naming it is
    // refused as one the decision does not offer.
    val offered = placementDecision(build(full, actor, card)).query match
      case DecisionQuery.ChooseOne(options, _) => options
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
      locked = Vector(option(others.head))))

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

  test("a Replace site slot that spends the only permission asks nothing more"):
    val powers = WalkerPowers(Vector(PlacementFixture.discardFirst))
    val done = Outcomes.finished(parkOnPlacement(fullSite, powers)
      .place(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(Look(done.treeless).cardsAt(home),
      Vector[CardId](others(0), others(2), card))

  test("facedown adviser starts the shared walker placement tree"):
    // p1 holds a facedown Wrestlers to play.
    val adviser = CatalogNames.denizen("Wrestlers")
    val ready = Table.start.adviser(actor, adviser, facedown = true).ready
    val rules = new OathRules(catalog)
    val walkerParked = new ParkedDecisionAssertions(catalog)
    val started = rules.startWalker(OathState.Ready(ready),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Denizen(adviser)))
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
