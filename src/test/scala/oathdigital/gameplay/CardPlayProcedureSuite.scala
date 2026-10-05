package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, VisionRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerPowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present}

class CardPlayProcedureSuite extends munit.FunSuite:
  private val actor = p1
  // An unrestricted denizen whose power these tests do not use.
  private val card = CatalogNames.denizen("Errand Boy")
  // Three more unrestricted denizens, for full adviser areas and sites.
  private val others = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))
  // Each is printed Locked and adviser-only.
  private val locked = Vector("Faithful Friend", "A Small Favor",
    "Family Heirloom").map(CatalogNames.denizen(_))
  private val noPowers = WalkerPowers.empty

  /** p1 holds `card` in a temporary hand, on the quiet table: p1 stands at
    * Ancient City, which holds no card, and holds no adviser. */
  private def handState: ReadyGame = Table.start.hand(actor, card).ready

  private def build(ready: ReadyGame): Operation =
    PlacementFixture.build(ready, actor, card)

  /** The placement decision the card play opens with. */
  private def placementDecision(tree: Operation): Decide =
    as[Decide](present(tree.children.headOption, "the placement decision"))

  private def placementOptions(tree: Operation): Vector[DecisionOptionRef] =
    as[DecisionQuery.ChooseOne](
      placementDecision(tree).query).options.map(_.ref)

  test("CardPlay exposes legal placement choices in decision order"):
    val choices = CardPlay.legalChoices(catalog, handState, actor, card,
      CardPlay.Origin.TemporaryHand)
    val expected = Vector[SearchPlacement](SearchPlacement.Discard,
      SearchPlacement.Site(None),
      SearchPlacement.Adviser(Orientation.FaceUp, None),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    assertEquals(choices.map(_.placement), expected)

  test("temporary-hand card builds a reusable placement decision"):
    val ready = handState
    val tree = build(ready)
    assert(placementOptions(tree).contains(DecisionOptionRef.Button("discard")))
    val domain = CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
    val expected = domain.map(_.placement).map:
      case SearchPlacement.Discard => DecisionOptionRef.Button("discard")
      case _: SearchPlacement.Site => DecisionOptionRef.Button("site")
      case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
        DecisionOptionRef.Button("adviser-faceup")
      case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
        DecisionOptionRef.Button("adviser-facedown")
    assertEquals(placementOptions(tree), expected)

  test("card absent from temporary hand cannot build card play"):
    val absent = Table.start.ready
    assertEquals(CardPlayProcedure.build(catalog, absent, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).left.toOption,
      Some(OathViolation.InvalidSearchPlacement(
        "card is not held at the selected origin")))

  test("full adviser area offers placement then a discardable replacement"):
    // Three advisers fill the area: the limit is three.
    val full = others.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _, facedown = true)).ready
    val tree = build(full)
    val decision = placementDecision(tree)
    assert(placementOptions(tree)
      .contains(DecisionOptionRef.Button("adviser-faceup")))
    val parked = PlacementFixture.park(full, tree, noPowers)
    val faceupButton = DecisionOptionRef.Button("adviser-faceup")
    val faceupAnswer = Answered(decision.decisionId,
      DecisionAnswer.ChooseOneAnswer(faceupButton), actor)
    val replacementPark = Outcomes.parked(
      ProcedureWalker.resolve(full, tree, parked, faceupAnswer, noPowers)).tree
    val replacement = present(
      ProcedureWalker.parkedDecide(full, tree, replacementPark, noPowers),
      "the replacement decision")
    val faceupPlacement = SearchPlacement.Adviser(Orientation.FaceUp, None)
    val choices = CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand)
    val faceup = present(choices.find(_.placement == faceupPlacement),
      "the faceup choice")
    val replacementOptions =
      as[DecisionQuery.ChooseOne](replacement.query).options.map(_.ref)
    assertEquals(replacementOptions, faceup.replacements.map {
      case id: DenizenId => DecisionOptionRef.Denizen(id)
      case id: VisionId => DecisionOptionRef.Vision(id)
      case id: EdificeId => DecisionOptionRef.Edifice(id)
      case id => DecisionOptionRef.Button(s"replace:${id.kind}:${id.value}")
    })
    val chosen = DecisionOptionRef.Denizen(others.head)
    assert(replacementOptions.contains(chosen))
    val finished = Outcomes.finished(ProcedureWalker.resolve(full, tree,
      replacementPark, Answered(replacement.decisionId,
        DecisionAnswer.ChooseOneAnswer(chosen), actor), noPowers))
    val advisers = Look(finished.treeless).player(actor).advisers
    assertEquals(advisers.size, 3)
    assert(advisers.exists(_.id == card))
    assert(!advisers.exists(_.id == others.head))

  test("selected site placement moves kept card and empties temporary hand"):
    val ready = handState
    val planned = accepted(CardPlay.plannedOperations(catalog, ready, actor,
      card, SearchPlacement.Site(None), CardPlay.Origin.TemporaryHand))
    assert(planned.exists { case value: Play => value.required; case _ => false })
    val tree = build(ready)
    val parked = PlacementFixture.park(ready, tree, noPowers)
    val siteAnswer = Answered(placementDecision(tree).decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("site")), actor)
    val result = Outcomes.finished(
      ProcedureWalker.resolve(ready, tree, parked, siteAnswer, noPowers))
    assert(Look(result.treeless).cardsAt("Ancient City").contains(card))
    assertEquals(result.treeless.game.current.temporaryHands(actor),
      Vector.empty)

  test("selected temporary-hand discard is a required semantic discard"):
    val planned = accepted(CardPlay.plannedOperations(catalog, handState,
      actor, card, SearchPlacement.Discard, CardPlay.Origin.TemporaryHand))
    assert(planned.exists {
      case value: Discard.Denizen => value.card == card && value.required
      case _ => false
    })

  test("locked full adviser area offers no adviser placement"):
    val full = locked.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _)).ready
    assert(!placementOptions(build(full))
      .contains(DecisionOptionRef.Button("adviser-faceup")))
    assert(!CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand).exists(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)))

  test("a full adviser area of facedown locked advisers offers the adviser " +
      "placement, since a facedown card has no restrictions"):
    val full = locked.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _, facedown = true)).ready
    assert(CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand).exists(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)))

  test("a faceup Conspiracy is planned as no placement and offers no replacement"):
    val vision = VisionRules.Conspiracy
    val revealed = VisionRules.Faith
    val ready = Table.start.hand(actor, vision)
      .revealedVision(actor, revealed).ready
    val faceup = SearchPlacement.Adviser(Orientation.FaceUp, None)
    val choices = CardPlay.legalChoices(catalog, ready, actor, vision,
      CardPlay.Origin.TemporaryHand)
    assertEquals(choices.find(_.placement == faceup).map(_.replacements),
      Some(Vector.empty))
    assertEquals(CardPlay.plannedOperations(catalog, ready, actor, vision,
      faceup, CardPlay.Origin.TemporaryHand), Right(Vector.empty))
    assert(CardPlay.plannedOperations(catalog, ready, actor, vision,
      SearchPlacement.Adviser(Orientation.FaceUp, Some(revealed)),
      CardPlay.Origin.TemporaryHand).isLeft)

  test("a facedown Vision is offered discard and faceup play only, with no " +
      "replacement"):
    val held = Vector(VisionRules.Faith, VisionRules.Conspiracy)
    val ready = held.foldLeft(Table.start)(_.adviser(actor, _, facedown = true))
      .revealedVision(actor, VisionRules.Conquest).ready
    held.foreach { vision =>
      val choices = CardPlay.legalChoices(catalog, ready, actor, vision,
        CardPlay.Origin.FacedownAdviser)
      assertEquals(choices.map(_.placement), Vector[SearchPlacement](
        SearchPlacement.Discard,
        SearchPlacement.Adviser(Orientation.FaceUp, None)), vision.value)
      assertEquals(choices.map(_.replacements),
        Vector(Vector.empty, Vector.empty), vision.value)
    }

  test("full site without Homeland permission offers no site placement"):
    // Ancient City, p1's site, holds 3 cards and is no suit's Homeland.
    val full = others.foldLeft(Table.start.hand(actor, card))(
      _.denizen(_, at = "Ancient City")).ready
    assert(!placementOptions(build(full))
      .contains(DecisionOptionRef.Button("site")))

  test("the placement decision labels its four buttons"):
    val decision = placementDecision(build(handState))
    val query = as[DecisionQuery.ChooseOne](decision.query)
    val labels = query.options.collect {
      case DecisionOption.Button(ref, label) => ref.key -> label
    }.toMap
    assertEquals(labels, Map("discard" -> "Discard", "site" -> "Play at site",
      "adviser-faceup" -> "Play faceup", "adviser-facedown" -> "Play facedown"))

  test("Hall of Ministers hides site-card replacement from the ruler's enemy"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    // The actor plays a card of its suit to the Beast Homeland, Deep Woods,
    // capacity 3. The test puts the Hall there and has `ruler` rule the site.
    val home = CatalogNames.site("Deep Woods")
    assertEquals(CardPlay.homelandSuit(catalog, home), Some(Suit.Beast))
    val beastCard = others.head
    assertEquals(catalog.suitOf(beastCard), Some(Suit.Beast))
    val fillers = others.tail
    /** Deep Woods full with the fillers and the Hall, ruled by `ruler`. */
    def ruledBy(ruler: PlayerId) = fillers.foldLeft(Table.start
      .pawn(actor, home).hand(actor, beastCard))(_.denizen(_, at = home))
      .edifice(hall, EdificeSide.Intact, at = home).warbandsAt(home, ruler, 1)
      .ready
    def siteReplacements(ready: ReadyGame): Vector[CardId] =
      CardPlay.legalChoices(catalog, ready, actor, beastCard,
        CardPlay.Origin.TemporaryHand).collectFirst {
          case choice if choice.placement.isInstanceOf[SearchPlacement.Site] =>
            choice.replacements
        }.getOrElse(Vector.empty)
    assertEquals(siteReplacements(ruledBy(actor))
      .collect { case id: DenizenId => id }.toSet, fillers.toSet)
    assertEquals(siteReplacements(ruledBy(p2))
      .collect { case id: DenizenId => id }, Vector.empty)

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

  /** A pending walk that answered decision `id` with "adviser-faceup". */
  private def pendingFor(id: String): PendingTree =
    val faceup = DecisionOptionRef.Button("adviser-faceup")
    PendingTree(at = Vector.empty, answered = Vector(
      Answered(id, DecisionAnswer.ChooseOneAnswer(faceup), actor)))

  test("placementDecisionId and placedCard round-trip a denizen"):
    val denizen = CatalogNames.denizen("Book Binders")
    val denizenId = CardPlayProcedure.placementDecisionId(denizen)
    assertEquals(denizenId, "cardplay.place.denizen.140")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(denizenId)),
      Some(denizen))

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

  test("playedAs names how each placement button plays the card, and the " +
      "replacement decision's answers play nothing"):
    val keys = Vector("discard", "site", "adviser-faceup", "adviser-facedown")
    assertEquals(keys.map(key =>
      CardPlayProcedure.playedAs(DecisionOptionRef.Button(key))),
      Vector(Some(PlayedAs.Discarded), Some(PlayedAs.AtSite),
        Some(PlayedAs.Faceup), Some(PlayedAs.Facedown)))
    // The replacement decision answers with the card to discard, or declines.
    assertEquals(CardPlayProcedure.playedAs(DecisionOptionRef.Denizen(card)),
      None)
    assertEquals(CardPlayProcedure.playedAs(
      CardPlayProcedure.noReplacement.ref), None)
