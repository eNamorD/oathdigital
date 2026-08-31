package oathdigital.gameplay

import oathdigital.gameplay.actions.{SearchCommand, SearchRules, TravelRules}

import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._

class SearchSuite extends munit.FunSuite {
  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def act: ReadyGame = {
    val Ready(ready) = execute(setupRules)._1: @unchecked
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      turn = ready.game.current.turn.copy(phase = Phase.Act))))
  }

  private def active(ready: ReadyGame) = ready.game.current.players.find(
    _.player == ready.game.current.turn.activePlayer).get

  test("world Search cost follows the Visions Drawn track bands") {
    Vector(0 -> 2, 1 -> 3, 2 -> 3, 3 -> 4, 4 -> 4, 5 -> 4).foreach {
      case (visions, expected) =>
        val ready = act.copy(game = act.game.copy(current = act.game.current.copy(
          tracks = act.game.current.tracks.copy(visionsDrawn = visions))))
        assertEquals(SearchRules.cost(ready, SearchSource.WorldDeck, Region.Cradle),
          Right(expected), s"Visions Drawn $visions")
        assertEquals(SearchRules.cost(ready,
          SearchSource.RegionalDiscard(Region.Cradle), Region.Cradle), Right(2))
    }
  }

  test("world Search spends track cost draws in order and stops on a Vision") {
    val base = act
    val player = active(base)
    val d1 = catalog.denizens.headOption.map(d => DenizenId(d.id.value)).get
    val d2 = catalog.denizens.drop(1).headOption.map(d => DenizenId(d.id.value)).get
    val vision = FirstGameRulesData.visions.head
    val d3 = catalog.denizens.drop(2).headOption.map(d => DenizenId(d.id.value)).get
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      commonCards = base.game.current.commonCards.copy(
        worldDeck = Vector(d1, vision, d2, d3)),
      tracks = base.game.current.tracks.copy(visionsDrawn = 1))))

    assertEquals(SearchRules.cost(ready, SearchSource.WorldDeck, Region.Cradle),
      Right(3))
    assertEquals(SearchRules.draw(ready, SearchSource.WorldDeck, Region.Cradle),
      Right(Vector(d1, vision)))
    val accepted = rules.handle(Ready(ready), SearchCommand.Start(
      player.player, DecisionId("search-1"), SearchSource.WorldDeck,
      Vector(d1, vision))).toOption.get
    val Ready(after) = accepted.state: @unchecked
    assertEquals(active(after).board.supply.supply,
      player.board.supply.supply - 3)
    assertEquals(after.game.current.commonCards.worldDeck, Vector(d2, d3))
    assertEquals(after.game.current.tracks.visionsDrawn, 2)
    assert(after.game.current.pending.exists(_.decision == DecisionId("search-1")))
  }

  test("Travel-only unsupported state does not block Search") {
    val base = act
    val player = active(base)
    val withFaceUpAdviser = base.copy(game = base.game.copy(current =
      base.game.current.copy(players = base.game.current.players.map { candidate =>
        if (candidate.player != player.player) candidate
        else candidate.copy(advisers = candidate.advisers.map {
          case DenizenState(id, _, tokens) =>
            DenizenState(id, Orientation.FaceUp, tokens)
          case other => other
        })
      })))
    val drawn = SearchRules.draw(withFaceUpAdviser, SearchSource.WorldDeck,
      player.pawnSite.flatMap(withFaceUpAdviser.game.current.map.regionOf).get)
      .toOption.get

    assert(TravelRules.validateSupportedState(catalog, withFaceUpAdviser).isRight)
    assert(rules.handle(Ready(withFaceUpAdviser), SearchCommand.Start(
      player.player, DecisionId("travel-independent"),
      SearchSource.WorldDeck, drawn)).isRight)
  }

  test("completion preserves chosen discard order and returns to Act") {
    val base = act
    val player = active(base)
    val drawn = base.game.current.commonCards.worldDeck.take(3)
    val started = rules.handle(Ready(base), SearchCommand.Start(
      player.player, DecisionId("search-2"), SearchSource.WorldDeck, drawn))
      .toOption.get
    val discarded = drawn.tail.reverse
    val completed = rules.handle(started.state, SearchCommand.Complete(
      player.player, DecisionId("search-2"), drawn.head, discarded,
      SearchPlacement.Discard)).toOption.get
    val Ready(after) = completed.state: @unchecked
    val origin = player.pawnSite.flatMap(base.game.current.map.regionOf).get
    val destination = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    assertEquals(after.game.current.commonCards.discard(destination).takeRight(3),
      discarded :+ drawn.head)
    assertEquals(after.game.current.pending, None)
    assertEquals(after.game.current.turn.phase, Phase.Act)
  }

  test("Search placement evolves before source-scoped When Played fallback") {
    val base = act
    val player = active(base)
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.dazzle")).get.id.value)
    val origin = player.pawnSite.flatMap(base.game.current.map.regionOf).get
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      pending = Some(PendingProcedure.Search(DecisionId("powered-search"),
        player.player, SearchSource.WorldDeck, origin, 2, Vector(powered))))))
    val accepted = rules.handle(Ready(ready), SearchCommand.Complete(player.player,
      DecisionId("powered-search"), powered, Vector.empty,
      SearchPlacement.Adviser(Orientation.FaceUp, None))).toOption.get
    assert(accepted.events.head.isInstanceOf[SearchCompleted])
    val diagnostic = accepted.events(1).asInstanceOf[IgnoredRulesRecorded]
    assertEquals(diagnostic.diagnostics.map(_.source),
      Vector(RuleSourceRef.Adviser(player.player, powered)))
    val replayed = accepted.events.foldLeft[
      Either[OathViolation, OathState]](Right(Ready(ready))) {
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure
    }
    assertEquals(replayed, Right(accepted.state))
  }

  test("replay rejects tampered draw cost decision and card permutation") {
    val base = act
    val player = active(base)
    val drawn = SearchRules.draw(base, SearchSource.WorldDeck,
      player.pawnSite.flatMap(base.game.current.map.regionOf).get).toOption.get
    val origin = player.pawnSite.flatMap(base.game.current.map.regionOf).get
    assert(rules.evolve(Ready(base), SearchStarted(player.player,
      DecisionId("bad"), SearchSource.WorldDeck, origin, 99, drawn))
      .left.toOption.get.isInstanceOf[SearchCostMismatch])
    val started = rules.handle(Ready(base), SearchCommand.Start(
      player.player, DecisionId("good"), SearchSource.WorldDeck, drawn)).toOption.get
    assert(rules.handle(started.state, SearchCommand.Complete(player.player,
      DecisionId("wrong"), drawn.head, drawn.tail, SearchPlacement.Discard))
      .left.toOption.get.isInstanceOf[SearchDecisionMismatch])
    assert(rules.handle(started.state, SearchCommand.Complete(player.player,
      DecisionId("good"), drawn.head, Vector.empty, SearchPlacement.Discard))
      .left.toOption.get.isInstanceOf[SearchChoiceMismatch])
  }

  test("pending projection exposes identities only to the deciding player") {
    val base = act
    val player = active(base)
    val other = base.game.current.players.find(_.player != player.player).get
    val drawn = SearchRules.draw(base, SearchSource.WorldDeck,
      player.pawnSite.flatMap(base.game.current.map.regionOf).get).toOption.get
    val started = rules.handle(Ready(base), SearchCommand.Start(
      player.player, DecisionId("private"), SearchSource.WorldDeck, drawn))
      .toOption.get
    val loaded = oathdigital.application.LoadedGame(started.state, 10)
    val projector = new oathdigital.application.GameProjector(catalog)
    val owner = projector.project("search", loaded, player.player)
    val hidden = projector.project("search", loaded, other.player)
    assertEquals(owner.phase, "search-decision")
    assertEquals(owner.pendingCardDecision.map(_.cards.map(_.cardId)),
      Some(drawn.map(_.value)))
    assertEquals(hidden.phase, "search-waiting")
    assertEquals(hidden.pendingCardDecision, None)
    assertEquals(hidden.legalControls, Vector.empty)
    assertEquals(hidden.legalSearchSources, Vector.empty)
  }

  test("catalog restrictions reject illegal faceup adviser and site placement") {
    val base = act
    val player = active(base)
    val origin = player.pawnSite.flatMap(base.game.current.map.regionOf).get
    val siteOnly = catalog.denizens.find(_.restrictions ==
      oathdigital.catalog.CardRestrictions.SiteOnly).get
    val adviserOnly = catalog.denizens.find(_.restrictions ==
      oathdigital.catalog.CardRestrictions.AdviserOnly).get
    def pending(id: DenizenId) = base.copy(game = base.game.copy(current =
      base.game.current.copy(pending = Some(PendingProcedure.Search(
        DecisionId("restriction"), player.player, SearchSource.WorldDeck,
        origin, 2, Vector(id))))))
    val siteId = DenizenId(siteOnly.id.value)
    val adviserId = DenizenId(adviserOnly.id.value)
    assert(rules.evolve(Ready(pending(siteId)), SearchCompleted(player.player,
      DecisionId("restriction"), siteId, Vector.empty,
      SearchPlacement.Adviser(Orientation.FaceUp, None))).left.toOption.get
      .isInstanceOf[InvalidSearchPlacement])
    assert(rules.evolve(Ready(pending(adviserId)), SearchCompleted(player.player,
      DecisionId("restriction"), adviserId, Vector.empty,
      SearchPlacement.Site(None))).left.toOption.get
      .isInstanceOf[InvalidSearchPlacement])
  }

  test("forced adviser discard follows non-kept cards on the next-region pile") {
    val base = act
    val player = active(base)
    val ids = catalog.denizens.filter(_.restrictions !=
      oathdigital.catalog.CardRestrictions.LockedAdviserOnly).take(5)
      .map(d => DenizenId(d.id.value))
    val fullPlayer = player.copy(advisers = ids.take(3).map(id =>
      DenizenState(id, Orientation.FaceDown, Tokens.empty)))
    val origin = player.pawnSite.flatMap(base.game.current.map.regionOf).get
    val ready = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p =>
        if (p.player == player.player) fullPlayer else p),
      pending = Some(PendingProcedure.Search(DecisionId("replace"), player.player,
        SearchSource.WorldDeck, origin, 2, ids.slice(3, 5))))))
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "search-replacement", oathdigital.application.LoadedGame(Ready(ready), 10),
      player.player)
    val resolutions = projection.pendingCardDecision.get
      .resolutionsByCard(ids(3).value)
    val facedown = resolutions.filter(resolution =>
      resolution.kind == "adviser" &&
        resolution.orientation.contains("face-down"))
    assertEquals(facedown.size, 1)
    assert(facedown.head.replacementRequired)
    assertEquals(facedown.head.replacementTargets.map(_.cardId).toSet,
      ids.take(3).map(_.value).toSet)
    val wire = ujson.read(oathdigital.server.GameHttpWire
      .encodeProjection(projection))
    val encoded = wire("pendingCardDecision")("resolutionsByCard")(ids(3).value)
      .arr.filter(value => value("kind").str == "adviser" &&
        value("orientation").str == "face-down")
    assertEquals(encoded.size, 1)
    assert(encoded.head("replacementRequired").bool)
    assertEquals(encoded.head("replacementTargets").arr.size, 3)
    val completed = rules.evolve(Ready(ready), SearchCompleted(player.player,
      DecisionId("replace"), ids(3), Vector(ids(4)),
      SearchPlacement.Adviser(Orientation.FaceDown, Some(ids.head)),
      discardedWorld = Vector(ids.head)))
      .toOption.get
    assert(rules.evolve(Ready(ready), SearchCompleted(player.player,
      DecisionId("replace"), ids(3), Vector(ids(4)),
      SearchPlacement.Adviser(Orientation.FaceDown, Some(ids.head)),
      discardedWorld = Vector.empty)).isLeft)
    val Ready(after) = completed: @unchecked
    val destination = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    assertEquals(after.game.current.commonCards.discard(destination).takeRight(2),
      Vector(ids(4), ids.head))
  }
}
