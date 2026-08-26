package oathdigital.gameplay

import oathdigital.gameplay.phases.{RestCommand, WakeCommand,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent.{IgnoredRulesRecorded, RestCompleted, RestStarted}
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation.{RestOutcomeMismatch,
  UnsupportedRoundEndCatalogInventory, UnsupportedRuleCatalog}

class RestSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def act: ReadyGame = {
    val Ready(initial) = execute(setup)._1: @unchecked
    initial.copy(game = initial.game.copy(current = initial.game.current.copy(
      turn = initial.game.current.turn.copy(phase = Phase.Act))))
  }

  test("Rest returns controlled resources reveals secrets refreshes and wakes next") {
    val base = act
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val adviserId = actor.advisers.collectFirst {
      case DenizenState(id, _, _) => id
    }.get
    val siteId = actor.pawnSite.get
    val siteCardId = catalog.denizens.find(_.id.value != adviserId.value)
      .map(value => DenizenId(value.id.value)).get
    val site = base.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(actor.lineage), 2),
      denizens = Vector(DenizenState(siteCardId, Orientation.FaceUp, Tokens(2, 1))))
    val prepared = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        map = base.game.current.map.copy(sites =
          base.game.current.map.sites.updated(siteId, site)),
        players = base.game.current.players.map { player =>
          if (player.player != actor.player) player
          else player.copy(
            board = player.board.copy(faceDownSecrets = 2,
              supply = SupplyTrack(1)),
            advisers = Vector(DenizenState(adviserId, Orientation.FaceUp,
              Tokens(1, 2))),
            relics = Vector(RelicState(RelicId(catalog.relics.head.id.value),
              Orientation.FaceUp, Tokens(0, 3))))
        })))

    val started = rules.handle(Ready(prepared), RestCommand.Begin(actor.player))
      .toOption.get
    assertEquals(started.events, Vector(RestStarted(actor.player)))
    val completed = rules.handle(started.state, RestCommand.Finish(actor.player))
      .toOption.get
    val event = completed.events.head.asInstanceOf[RestCompleted]
    val Ready(after) = completed.state: @unchecked
    val rested = after.game.current.players.find(_.player == actor.player).get

    assertEquals(event.returnedFavor.values.sum, 3)
    assertEquals(event.returnedSecrets, 6)
    assertEquals(rested.board.faceDownSecrets, 0)
    assertEquals(rested.board.faceUpSecrets,
      actor.board.faceUpSecrets + 2 + 6)
    assertEquals(rested.board.supply, SupplyTrack.full)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, actor.player)
    assertEquals(after.game.current.turn.usedPowers, Set.empty[PowerUseRef])
    assertEquals(after.support.favorBanks.values.sum,
      prepared.support.favorBanks.values.sum + 3)
  }

  test("replay validates recorded Rest outcome and advances the round") {
    var state: OathState = Ready(act)
    val participants = act.game.current.players.map(_.player)
    val start = participants.indexOf(act.support.firstPlayer)
    val order = participants.drop(start) ++ participants.take(start)
    order.foreach { player =>
      val began = rules.handle(state, RestCommand.Begin(player)).toOption.get
      val accepted = rules.handle(began.state, RestCommand.Finish(player))
        .toOption.get
      val event = accepted.events.head.asInstanceOf[RestCompleted]
      val tampered = event.copy(refreshedSupply = event.refreshedSupply - 1)
      assert(rules.evolve(began.state, tampered).left.toOption.get
        .isInstanceOf[RestOutcomeMismatch])
      state = accepted.events.foldLeft[Either[OathViolation, OathState]](
        Right(began.state))((next, recorded) => next.flatMap(rules.evolve(_, recorded)))
        .toOption.get
      if (player != order.last)
        state = rules.handle(state, WakeCommand.EndWake(event.postRestActivePlayerId))
          .toOption.get.state
    }
    val Ready(after) = state: @unchecked
    assertEquals(after.game.current.tracks.round, 2)
    assertEquals(after.game.current.turn.activePlayer, after.support.firstPlayer)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
  }

  test("unrelated active powers and the end-die Arbiter legacy do not block") {
    val base = act
    val lineage = base.game.campaign.lineages.values.head
    val unrelated = catalog.denizens.find(d => !Set(
      "denizen.vow-of-poverty", "denizen.naysayers",
      "denizen.silver-tongue", "denizen.insomnia",
      "denizen.vow-of-obedience").exists(d.handlers.contains)).get
    val supported = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(lineages = base.game.campaign.lineages.updated(
        lineage.id, lineage.copy(legacies = Vector(
          LegacyState(LegacyId("L23"), active = true))))), current =
      base.game.current.copy(players = base.game.current.players.map { player =>
        if (player.player != base.game.current.turn.activePlayer) player
        else player.copy(advisers = Vector(DenizenState(
          DenizenId(unrelated.id.value), Orientation.FaceUp, Tokens.empty)))
      })))
    assert(rules.handle(Ready(supported), RestCommand.Begin(
      supported.game.current.turn.activePlayer)).isRight)
  }

  test("each relevant Rest handler records fallback diagnostics without blocking") {
    val base = act
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val relevant = Set("denizen.vow-of-poverty", "denizen.naysayers",
      "denizen.silver-tongue", "denizen.insomnia", "denizen.vow-of-obedience")
    relevant.foreach { handler =>
      val definition = catalog.denizens.find(_.handlers.contains(handler)).get
      val adviser = DenizenState(DenizenId(definition.id.value),
        Orientation.FaceUp, Tokens.empty)
      val state = base.copy(game = base.game.copy(current = base.game.current.copy(
        players = base.game.current.players.map(p => if (p.player == actor.player)
          p.copy(advisers = Vector(adviser)) else p))))
      val accepted = rules.handle(Ready(state), RestCommand.Begin(actor.player)).toOption.get
      val recorded = accepted.events.head.asInstanceOf[IgnoredRulesRecorded]
      assertEquals(recorded.diagnostics.head.handlerId, handler)
      assert(recorded.diagnostics.head.source.stableKey
        .startsWith(s"adviser:${actor.player.value}:"))
      assertEquals(rules.evolve(Ready(state), recorded), Right(Ready(state)))
      val tampered = recorded.copy(diagnostics = recorded.diagnostics.map(
        _.copy(handlerId = "denizen.tampered")))
      assert(rules.evolve(Ready(state), tampered).isLeft)
    }
    val handler = "denizen.insomnia"
    val definition = catalog.denizens.find(_.handlers.contains(handler)).get
    val siteId = actor.pawnSite.get
    val siteState = base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        siteId, base.game.current.map.sites(siteId).copy(denizens = Vector(
          DenizenState(DenizenId(definition.id.value), Orientation.FaceUp,
            Tokens.empty))))))))
    val siteAccepted = rules.handle(Ready(siteState), RestCommand.Begin(actor.player))
      .toOption.get.events.head.asInstanceOf[IgnoredRulesRecorded]
    assert(siteAccepted.diagnostics.head.source.stableKey
      .startsWith(s"site-card:${siteId.value}:"))
  }

  test("changed inventory in every catalog family fails before runtime discovery") {
    val base = act
    val actor = base.game.current.turn.activePlayer
    def rejects(c: oathdigital.catalog.ExecutableCatalog) =
      new OathRules(c).handle(Ready(base), RestCommand.Begin(actor))
        .left.toOption.exists(error =>
          error.isInstanceOf[UnsupportedRoundEndCatalogInventory] ||
          error.isInstanceOf[UnsupportedRuleCatalog])
    val changed = Vector(
      catalog.copy(denizens = catalog.denizens.updated(0,
        catalog.denizens.head.copy(handlers = catalog.denizens.head.handlers :+ "changed"))),
      catalog.copy(relics = catalog.relics.updated(0,
        catalog.relics.head.copy(handlers = catalog.relics.head.handlers :+ "changed"))),
      catalog.copy(edifices = catalog.edifices.updated(0, catalog.edifices.head.copy(
        intact = catalog.edifices.head.intact.copy(
          handlers = catalog.edifices.head.intact.handlers :+ "changed")))),
      catalog.copy(edifices = catalog.edifices.updated(0, catalog.edifices.head.copy(
        ruined = catalog.edifices.head.ruined.copy(
          handlers = catalog.edifices.head.ruined.handlers :+ "changed")))),
      catalog.copy(legacies = catalog.legacies.updated(0,
        catalog.legacies.head.copy(handlers = catalog.legacies.head.handlers :+ "changed"))),
      catalog.copy(sites = catalog.sites.updated(0,
        catalog.sites.head.copy(handlers = catalog.sites.head.handlers :+ "changed"))))
    assert(changed.forall(rejects))
  }

  test("altered banner and Foundation types record stable fallback identities") {
    val base = act
    val actor = base.game.current.turn.activePlayer
    def diagnostic(ready: ReadyGame) = rules.handle(Ready(ready),
      RestCommand.Begin(actor)).toOption.get.events.head
      .asInstanceOf[IgnoredRulesRecorded].diagnostics.head
    val banner = base.copy(game = base.game.copy(current = base.game.current.copy(
      banners = base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(
          active = PeoplesFavorFace.GrandCouncil)))))
    assertEquals(diagnostic(banner).source.stableKey, "banner:peoples-favor")

    val number = base.game.campaign.foundations.keys.head
    val foundation = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(foundations = base.game.campaign.foundations.updated(
        number, FoundationState(FoundationFace.Altered, Set.empty)))))
    assertEquals(diagnostic(foundation).source.stableKey,
      s"foundation:${number.value}")
  }

  test("last player of round eight finishes the game by War Exhaustion") {
    val base = act
    val participants = base.game.current.players.map(_.player)
    val start = participants.indexOf(base.support.firstPlayer)
    val last = (participants.drop(start) ++ participants.take(start)).last
    val unsupported = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        tracks = base.game.current.tracks.copy(round = 8),
        turn = TurnState(last, Phase.Act, Set.empty))))

    val deterministic = new OathRules(catalog, warExhaustionRandomPort =
      new WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.last
      })
    val started = deterministic.handle(Ready(unsupported), RestCommand.Begin(last))
      .toOption.get
    val finished = deterministic.handle(started.state, RestCommand.Finish(last))
      .toOption.get
    assert(finished.events.exists(_.isInstanceOf[OathEvent.RoundEnded]))
    assert(finished.events.exists(_.isInstanceOf[OathEvent.WarExhaustionResolved]))
    assert(finished.continue.isInstanceOf[OathContinue.GameFinished])
    val result = finished.events.collectFirst {
      case event: OathEvent.WarExhaustionResolved => event
    }.get
    assertEquals(result.kind, VictoryKind.RandomSelection)
    assertEquals(result.winner, result.randomCandidates.last)
    assertEquals(deterministic.handle(finished.state, RestCommand.Begin(last))
      .left.toOption.get, OathViolation.GameEnded)
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "round-eight", oathdigital.application.LoadedGame(
        Ready(unsupported), 30L), last)
    assert(projection.legalControls.contains("beginRest"))
  }
}
