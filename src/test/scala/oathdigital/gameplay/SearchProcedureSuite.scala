package oathdigital.gameplay

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

class SearchProcedureSuite extends munit.FunSuite {
  private val setupRules = new oathdigital.gameplay.setup.FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def ready: ReadyGame = {
    val OathState.Ready(state) = execute(setupRules)._1: @unchecked
    state.copy(game = state.game.copy(current = state.game.current.copy(
      turn = state.game.current.turn.copy(phase = Phase.Act))))
  }

  test("world Search starts from one generic source argument and parks on card selection") {
    val initial = ready
    val actor = initial.game.current.turn.activePlayer
    val tree = SearchProcedure.build(catalog, initial, actor,
      Vector(DecisionOptionRef.Button("search:world"))).toOption.get
    val outcome = ProcedureWalker.advance(initial, tree, None,
      WalkerPowers.empty).toOption.get
    assert(outcome.isInstanceOf[WalkerOutcome.Parked])
  }

  test("Search rejects a source in another region") {
    val initial = ready
    val actor = initial.game.current.turn.activePlayer
    val region = initial.game.current.players.find(_.player == actor).get
      .pawnSite.flatMap(initial.game.current.map.regionOf).get
    val other = Region.all.find(_ != region).get
    assert(SearchProcedure.build(catalog, initial, actor,
      Vector(DecisionOptionRef.Button(
        s"search:regional-discard:${other.key}"))).isLeft)
  }

  test("Search tree construction defers affordability until cost modifiers run") {
    val base = ready
    val actor = base.game.current.turn.activePlayer
    val players = base.game.current.players.map { player =>
      if (player.player == actor) player.copy(board = player.board.copy(
        supply = player.board.supply.copy(supply = 0))) else player
    }
    val initial = base.copy(game = base.game.copy(current =
      base.game.current.copy(players = players)))
    assert(SearchProcedure.build(catalog, initial, actor,
      Vector(DecisionOptionRef.Button("search:world"))).isRight)
    assertEquals(SearchProcedure.legalSources(catalog, initial, actor,
      WalkerPowers.empty), Vector.empty)
  }

  test("regional Search draws from the end of its pile") {
    val base = ready
    val actor = base.game.current.turn.activePlayer
    val region = base.game.current.players.find(_.player == actor).get
      .pawnSite.flatMap(base.game.current.map.regionOf).get
    val cards = base.game.current.commonCards.worldDeck.take(3)
    val zones = base.game.current.commonCards
    val initial = base.copy(game = base.game.copy(current =
      base.game.current.copy(commonCards = zones.copy(
        worldDeck = zones.worldDeck.drop(3),
        regionalDiscards = zones.regionalDiscards.updated(region,
          zones.discard(region) ++ cards)))))
    val started = rules.startWalker(OathState.Ready(initial), ActionRef.Search,
      actor, startArgs = Vector(DecisionOptionRef.Button(
        s"search:regional-discard:${region.key}"))).toOption.get
    val OathState.Ready(after) = started.state: @unchecked
    assertEquals(after.game.current.temporaryHands(actor), cards.reverse)
    assertEquals(after.game.current.tracks.visionsDrawn,
      initial.game.current.tracks.visionsDrawn)
  }

  test("registered Search parks and resumes into shared card placement") {
    val initial = ready
    val actor = initial.game.current.turn.activePlayer
    val started = rules.startWalker(OathState.Ready(initial), ActionRef.Search,
      actor, startArgs = Vector(DecisionOptionRef.Button("search:world")))
      .toOption.get
    val OathState.Ready(afterDraw) = started.state: @unchecked
    val drawn = afterDraw.game.current.temporaryHands(actor)
    assert(drawn.nonEmpty)
    val afterSelection = if (drawn.size == 1) started else {
      val placements = Vector(DecisionPlacement(ref(drawn.head),
        SearchProcedure.keepKey)) ++ drawn.tail.map(card =>
        DecisionPlacement(ref(card), SearchProcedure.discardKey))
      rules.resolveWalker(started.state, actor, SearchProcedure.cardDecisionId,
        DecisionAnswer.PartitionAnswer(placements)).toOption.get
    }
    assert(afterSelection.continue.isInstanceOf[OathContinue.AwaitingSearchDecision])
    val kept = drawn.head
    val completed = rules.resolveWalker(afterSelection.state, actor,
      s"cardplay.place.${kept.kind}.${kept.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))
      .toOption.get
    val OathState.Ready(finalReady) = completed.state: @unchecked
    assertEquals(finalReady.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(finalReady.game.current.walkerPending, None)
  }

  test("faceup Conspiracy keeps its hand and hands off to legacy continuation") {
    val base = ready
    val actor = base.game.current.turn.activePlayer
    val vision = VisionRules.Conspiracy
    val deck = base.game.current.commonCards.worldDeck
    assert(deck.contains(vision))
    val initial = base.copy(game = base.game.copy(current =
      base.game.current.copy(commonCards = base.game.current.commonCards.copy(
        worldDeck = Vector(vision) ++ deck.filterNot(_ == vision)))))
    val started = rules.startWalker(OathState.Ready(initial), ActionRef.Search,
      actor, startArgs = Vector(DecisionOptionRef.Button("search:world")))
      .toOption.get
    val OathState.Ready(afterDraw) = started.state: @unchecked
    assertEquals(afterDraw.game.current.tracks.visionsDrawn,
      initial.game.current.tracks.visionsDrawn + 1)
    val result = rules.resolveWalker(started.state, actor,
      s"cardplay.place.${vision.kind}.${vision.value}",
      DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("adviser-faceup"))).toOption.get
    val OathState.Ready(after) = result.state: @unchecked
    assertEquals(after.game.current.temporaryHands(actor), Vector(vision))
    assert(after.game.current.pending.exists(
      _.isInstanceOf[PendingProcedure.Conspiracy]))
    assert(result.continue.isInstanceOf[OathContinue.AwaitingConspiracyDecision])
  }

  test("Search uses its registered modifier-selection window") {
    val initial = ready
    val actor = initial.game.current.turn.activePlayer
    assertEquals(rules.offerableWalkerPowers(initial, actor, ActionRef.Search),
      Right(Vector.empty))
    assert(rules.startWalker(OathState.Ready(initial), ActionRef.Search, actor,
      modifiers = Vector(PowerId("unoffered.search")),
      startArgs = Vector(DecisionOptionRef.Button("search:world"))).isLeft)
  }

  private def ref(card: WorldCardId): DecisionOptionRef = card match {
    case id: DenizenId => DecisionOptionRef.Denizen(id)
    case id: VisionId => DecisionOptionRef.Vision(id)
  }
}
