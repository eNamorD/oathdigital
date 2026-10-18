package oathdigital.gameplay

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.Transform
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks, PowerStubs, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

class SearchProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)

  private val actor = p1
  // p1 stands at Ancient City, in the Cradle.
  private val region = Region.Cradle
  // Three denizens whose powers these tests do not use.
  private val topCards = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))

  /** p1's Act on the quiet table, at Ancient City. */
  private def atAncientCity: Table = Table.start.pawn(actor, "Ancient City")

  private def searched(under: OathRules, ready: ReadyGame,
      pile: SearchSource = SearchSource.WorldDeck)
      : Either[OathViolation, OathTransition] =
    SearchPlay.searched(under, OathState.Ready(ready), actor, pile)

  test("a Search parks on its source and draws on the answer"):
    val initial = atAncientCity.worldDeckTop(topCards*).ready
    val started = accepted(rules.startWalker(OathState.Ready(initial),
      ActionRef.Search, actor))
    parked.assertParked(started.state, ActionRef.Search,
      SearchProcedure.sourceDecisionId, actor)
    assertEquals(Outcomes.ready(started.state).game.current.temporaryHands
      .getOrElse(actor, Vector.empty), Vector.empty)
    val picked = accepted(rules.resolveWalker(started.state, actor,
      SearchProcedure.sourceDecisionId, SearchPlay.source(SearchSource.WorldDeck)))
    parked.assertParked(picked.state, ActionRef.Search,
      SearchProcedure.cardDecisionId, actor)

  test("the source offers the world deck and the actor's region's discard"):
    val ready = atAncientCity.ready
    val tree = accepted(SearchProcedure.build(catalog, ready, actor,
      Vector.empty))
    // The modifier decision comes first; the source opens what follows it.
    val offered = tree.children(1).children.head match
      case Decide(SearchProcedure.sourceDecisionId, `actor`,
          DecisionQuery.ChooseOne(options, Some("Choose a Search source"),
            Some("Search")),
          _, _, _, _) => options.map(_.ref)
      case other => fail(s"not the source decision: $other")
    assertEquals(offered, Vector[DecisionOptionRef](
      DecisionOptionRef.Deck(CardDeck.World),
      DecisionOptionRef.DiscardPile(region)))

  test("a start that names a selection is refused"):
    assertEquals(rejected(SearchProcedure.build(catalog, atAncientCity.ready,
      actor, Vector(DecisionOptionRef.Button("search:world")))),
      OathViolation.InvalidEventOrder("search takes no start selection"))

  test("a start with no playable source is refused, with the first " +
      "source's rejection"):
    // No Supply, and the Cradle discard is empty on the quiet table. The
    // walk's look-ahead leaves the source decision nothing to offer.
    val broke = atAncientCity.supply(actor, 0).ready
    rejected(rules.startWalker(OathState.Ready(broke), ActionRef.Search,
      actor)) match
      case OathViolation.CoreOperationRejected("insufficient-supply", _) => ()
      case other => fail(s"unexpected refusal: $other")

  test("another region's discard is not offered, and naming it is refused"):
    val initial = atAncientCity.discarded(Region.Provinces, topCards*).ready
    assertEquals(rejected(searched(rules, initial,
      SearchSource.RegionalDiscard(Region.Provinces))),
      OathViolation.InvalidEventOrder(
        "decision search.source does not offer the selected option"))

  test("regional Search draws from the end of its pile"):
    val initial = atAncientCity.discarded(region, topCards*).ready
    val started = accepted(searched(rules, initial,
      SearchSource.RegionalDiscard(region)))
    val after = Outcomes.ready(started.state)
    assertEquals(after.game.current.temporaryHands(actor), topCards.reverse)
    assertEquals(after.game.current.tracks.visionsDrawn,
      initial.game.current.tracks.visionsDrawn)

  test("registered Search parks and resumes into shared card placement"):
    // Three denizens top the deck, so a world Search draws them and asks
    // which to keep.
    val initial = atAncientCity.worldDeckTop(topCards*).ready
    val started = accepted(searched(rules, initial))
    val drawn = Outcomes.ready(started.state).game.current.temporaryHands(actor)
    assertEquals(drawn, topCards)
    val kept = drawn.head
    val placements = Vector(DecisionPlacement(
      PlacementPicks.optionNaming(kept), SearchProcedure.keepKey)) ++
      drawn.tail.map(card => DecisionPlacement(
        PlacementPicks.optionNaming(card), SearchProcedure.discardKey))
    val afterSelection = accepted(
      rules.resolveWalker(started.state, actor, SearchProcedure.cardDecisionId,
        DecisionAnswer.PartitionAnswer(placements)))
    val placeId = PlacementFixture.cardPlayDecisionId(kept, "place")
    parked.assertParked(afterSelection.state, ActionRef.Search, placeId, actor)
    val completed = accepted(
      rules.resolveWalker(afterSelection.state, actor, placeId,
        PlacementPicks.parkedAnswer(afterSelection.state, PlayedAs.Discarded,
          WalkerPowers.empty)))
    val finalReady = Outcomes.ready(completed.state)
    assertEquals(finalReady.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(MenuPlay.pendingBeyondMenu(finalReady), None)

  test("a faceup Conspiracy from Search with nothing to take is played and boxed"):
    val vision = VisionRules.Conspiracy
    // Conspiracy tops the deck; on the quiet table no one shares p1's site,
    // so it has nothing to take.
    val initial = atAncientCity.worldDeckTop(vision).ready
    val withPowers = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
    val withPowersParked = new ParkedDecisionAssertions(catalog,
      WalkerPowerCatalog.default(catalog))
    val started = accepted(searched(withPowers, initial))
    val result = accepted(withPowers.resolveWalker(started.state, actor,
      PlacementFixture.cardPlayDecisionId(vision, "place"),
      PlacementPicks.parkedAnswer(started.state, PlayedAs.Faceup,
        WalkerPowerCatalog.default(catalog))))
    val after = Outcomes.ready(result.state)
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(MenuPlay.pendingBeyondMenu(after), None)
    withPowersParked.assertNotParked(result.state)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(vision),
      "the played Conspiracy is boxed")

  test("a single-card Search settles from its placement answer, so a " +
      "power's Decide added after the play still resolves"):
    val vision = VisionRules.Faith
    // A Vision on top stops the draw at one card, so no Partition decision
    // is ever asked -- the scenario the settle fix covers.
    val initial = atAncientCity.worldDeckTop(vision).ready
    val settleDecision = "test.search-settle"
    val settlePower = PowerStubs.ContributingStub(
      PowerId("test.search-settle"),
      Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform(
        (ctx, children) => children :+ Decide(settleDecision, ctx.actor,
          DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
            DecisionOptionRef.Button("ok"), "OK"))))))))
    val withPower = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(settlePower)))
    val withPowerParked = new ParkedDecisionAssertions(catalog,
      WalkerPowers(Vector(settlePower)))
    val started = accepted(searched(withPower, initial))
    val placed = accepted(
      withPower.resolveWalker(started.state, actor,
        PlacementFixture.cardPlayDecisionId(vision, "place"),
        PlacementPicks.parkedAnswer(started.state, PlayedAs.Faceup,
          WalkerPowers(Vector(settlePower)))))
    withPowerParked.assertParked(placed.state, ActionRef.Search, settleDecision,
      actor)
    val completed = accepted(
      withPower.resolveWalker(placed.state, actor, settleDecision,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("ok"))))
    val finalReady = Outcomes.ready(completed.state)
    assertEquals(MenuPlay.pendingBeyondMenu(finalReady), None)

  test("a follow-up discard answered before a power's Decide after the play " +
      "resolves on resume, which settles from the answers"):
    // Three denizens top the deck; the kept one replaces a card of a full
    // Ancient City, and two permissions let a second card go too.
    val fillers = Vector("Rain Boots", "Ancient Binding", "Wrestlers")
      .map(CatalogNames.denizen(_))
    val home = CatalogNames.site("Ancient City")
    val initial = fillers.foldLeft(atAncientCity.worldDeckTop(topCards*))(
      _.denizen(_, at = home)).ready
    val twoPermissions = PlacementFixture.rulePower("test.two-discards")(
      _.permitSiteDiscard(PlacementFixture.testPermission)
        .permitSiteDiscard(PlacementFixture.testPermission))
    val afterDecision = "test.after-follow-up"
    val afterPlay = PowerStubs.ContributingStub(PowerId("test.after-follow-up"),
      Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform(
        (ctx, children) => children :+ Decide(afterDecision, ctx.actor,
          DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
            DecisionOptionRef.Button("ok"), "OK"))))))))
    val powers = WalkerPowers(Vector(twoPermissions, afterPlay))
    val withPowers = new OathRules(catalog, walkerPowerCatalog = powers)
    val withPowersParked = new ParkedDecisionAssertions(catalog, powers)
    val kept = topCards.head
    val started = accepted(searched(withPowers, initial))
    val selected = accepted(withPowers.resolveWalker(started.state, actor,
      SearchProcedure.cardDecisionId, DecisionAnswer.PartitionAnswer(
        DecisionPlacement(DecisionOptionRef.Denizen(kept),
          SearchProcedure.keepKey) +: topCards.tail.map(card =>
          DecisionPlacement(DecisionOptionRef.Denizen(card),
            SearchProcedure.discardKey)))))
    val replaced = accepted(withPowers.resolveWalker(selected.state, actor,
      PlacementFixture.cardPlayDecisionId(kept, "place"),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.SiteSlot(home, 0))))
    withPowersParked.assertParked(replaced.state, ActionRef.Search,
      PlacementFixture.cardPlayDecisionId(kept, "discard"), actor)
    val more = accepted(withPowers.resolveWalker(replaced.state, actor,
      PlacementFixture.cardPlayDecisionId(kept, "discard"),
      DecisionAnswer.ChooseManyAnswer(Vector(
        DecisionOptionRef.Denizen(fillers(1))))))
    // The card is played and both site cards are gone; the walk waits on the
    // power's Decide, which a resume must reach at the same child index.
    withPowersParked.assertParked(more.state, ActionRef.Search, afterDecision,
      actor)
    val played = Outcomes.ready(more.state)
    assertEquals(Look(played).cardsAt(home), Vector[CardId](fillers(2), kept))
    val completed = accepted(withPowers.resolveWalker(more.state, actor,
      afterDecision, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("ok"))))
    assertEquals(MenuPlay.pendingBeyondMenu(Outcomes.ready(completed.state)),
      None)

  test("Search uses its registered modifier-selection window"):
    val initial = atAncientCity.ready
    // A power applicable only at Search's modifier window is offered for
    // Search; one applicable only at Forge's is not.
    val atSearch = PowerStubs.WindowScopedStub(
      PowerId("test.search-windowed"), PowerWindow.SearchModifierSelection)
    val atForge = PowerStubs.WindowScopedStub(
      PowerId("test.forge-windowed"), PowerWindow.ForgeModifierSelection)
    val scoped = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(atSearch, atForge)))
    assertEquals(scoped.offerableWalkerPowers(initial, actor, ActionRef.Search)
      .map(_.map(_.id)), Right(Vector(atSearch.id)))

