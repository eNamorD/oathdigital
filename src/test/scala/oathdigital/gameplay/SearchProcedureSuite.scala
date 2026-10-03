package oathdigital.gameplay

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.Transform
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, PowerStubs, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class SearchProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)

  private val actor = p1
  // p1 stands at Ancient City, in the Cradle.
  private val region = Region.Cradle
  // Three denizens whose powers these tests do not use.
  private val topCards = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))

  private def ready: ReadyGame = Table.start.pawn(actor, "Ancient City").ready

  test("world Search starts from one generic source argument and parks on card selection"):
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Search, actor,
      startArgs = Vector(DecisionOptionRef.Button("search:world"))))
    parked.assertParked(started.state, ActionRef.Search,
      SearchProcedure.cardDecisionId, actor)

  test("Search rejects a source in another region"):
    assertEquals(ready.game.current.map.regionOf(
      CatalogNames.site("Ancient City")), Some(region))
    val other = Region.Provinces
    assert(SearchProcedure.build(catalog, ready, actor,
      Vector(DecisionOptionRef.Button(
        s"search:regional-discard:${other.key}"))).isLeft)

  test("Search tree construction defers affordability until cost modifiers run"):
    val initial = Table.start.supply(actor, 0).ready
    assert(SearchProcedure.build(catalog, initial, actor,
      Vector(DecisionOptionRef.Button("search:world"))).isRight)
    assertEquals(SearchProcedure.legalSources(catalog, initial, actor,
      WalkerPowers.empty), Vector.empty)

  test("regional Search draws from the end of its pile"):
    val initial = Table.start.discarded(region, topCards*).ready
    val started = accepted(rules.startWalker(OathState.Ready(initial),
      ActionRef.Search, actor, startArgs = Vector(DecisionOptionRef.Button(
        s"search:regional-discard:${region.key}"))))
    val after = Outcomes.ready(started.state)
    assertEquals(after.game.current.temporaryHands(actor), topCards.reverse)
    assertEquals(after.game.current.tracks.visionsDrawn,
      initial.game.current.tracks.visionsDrawn)

  test("registered Search parks and resumes into shared card placement"):
    // Three denizens top the deck, so a world Search draws them and asks
    // which to keep.
    val initial = Table.start.worldDeckTop(topCards*).ready
    val started = accepted(rules.startWalker(OathState.Ready(initial),
      ActionRef.Search, actor,
      startArgs = Vector(DecisionOptionRef.Button("search:world"))))
    val drawn = Outcomes.ready(started.state).game.current.temporaryHands(actor)
    assertEquals(drawn, topCards)
    val kept = drawn.head
    val placements = Vector(DecisionPlacement(ref(kept),
      SearchProcedure.keepKey)) ++ drawn.tail.map(card =>
      DecisionPlacement(ref(card), SearchProcedure.discardKey))
    val afterSelection = accepted(
      rules.resolveWalker(started.state, actor, SearchProcedure.cardDecisionId,
        DecisionAnswer.PartitionAnswer(placements)))
    val placeId = PlacementFixture.cardPlayDecisionId(kept, "place")
    parked.assertParked(afterSelection.state, ActionRef.Search, placeId, actor)
    val completed = accepted(
      rules.resolveWalker(afterSelection.state, actor, placeId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard"))))
    val finalReady = Outcomes.ready(completed.state)
    assertEquals(finalReady.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(finalReady.game.current.walkerPending, None)

  test("a faceup Conspiracy from Search with nothing to take is played and boxed"):
    val vision = VisionRules.Conspiracy
    // Conspiracy tops the deck. No Table step removes a pawn, so the other
    // players' pawns are lifted with a patch: no one shares p1's site.
    val initial = Table.start.worldDeckTop(vision).unchecked
      .updateCurrent(c => c.copy(players = c.players.map(player =>
        if player.player == actor then player else player.copy(
          pawnSite = None))))
    val withPowers = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
    val withPowersParked = new ParkedDecisionAssertions(catalog,
      WalkerPowerCatalog.default(catalog))
    val started = accepted(
      withPowers.startWalker(OathState.Ready(initial), ActionRef.Search, actor,
        startArgs = Vector(DecisionOptionRef.Button("search:world"))))
    val result = accepted(withPowers.resolveWalker(started.state, actor,
      PlacementFixture.cardPlayDecisionId(vision, "place"),
      DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("adviser-faceup"))))
    val after = Outcomes.ready(result.state)
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(after.game.current.walkerPending, None)
    withPowersParked.assertNotParked(result.state)
    assert(!accepted(CardIndex.from(after.game)).ids.contains(vision),
      "the played Conspiracy is boxed")

  test("a single-card Search settles from its placement answer, so a " +
      "power's Decide added after the play still resolves"):
    val vision = VisionRules.Faith
    // A Vision on top stops the draw at one card, so no Partition decision
    // is ever asked -- the scenario the settle fix covers.
    val initial = Table.start.worldDeckTop(vision).ready
    val settleDecision = "test.search-settle"
    val settlePower = PowerStubs.ContributingStub(
      PowerId("test.search-settle"),
      Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform(
        (ctx, children) => children :+ Decide(settleDecision, ctx.activePlayer,
          DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
            DecisionOptionRef.Button("ok"), "OK"))))))))
    val withPower = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(settlePower)))
    val withPowerParked = new ParkedDecisionAssertions(catalog,
      WalkerPowers(Vector(settlePower)))
    val started = accepted(
      withPower.startWalker(OathState.Ready(initial), ActionRef.Search, actor,
        startArgs = Vector(DecisionOptionRef.Button("search:world"))))
    val placed = accepted(
      withPower.resolveWalker(started.state, actor,
        PlacementFixture.cardPlayDecisionId(vision, "place"),
        DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Button("adviser-faceup"))))
    withPowerParked.assertParked(placed.state, ActionRef.Search, settleDecision,
      actor)
    val completed = accepted(
      withPower.resolveWalker(placed.state, actor, settleDecision,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("ok"))))
    val finalReady = Outcomes.ready(completed.state)
    assertEquals(finalReady.game.current.walkerPending, None)

  test("Search uses its registered modifier-selection window"):
    val initial = ready
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

  private def ref(card: WorldCardId): DecisionOptionRef = card match
    case id: DenizenId => DecisionOptionRef.Denizen(id)
    case id: VisionId => DecisionOptionRef.Vision(id)
