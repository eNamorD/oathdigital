package oathdigital.gameplay

import oathdigital.gameplay.phases.{RestCommand, WakeCommand,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent.{RestCompleted, RestStarted}
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation.{RestOutcomeMismatch,
  UnsupportedRoundEndRule}

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
        state = rules.handle(state, WakeCommand.EndWake(event.nextPlayerId))
          .toOption.get.state
    }
    val Ready(after) = state: @unchecked
    assertEquals(after.game.current.tracks.round, 2)
    assertEquals(after.game.current.turn.activePlayer, after.support.firstPlayer)
    assertEquals(after.game.current.turn.phase, Phase.Wake)
  }

  test("active legacy Rest powers fail explicitly") {
    val base = act
    val lineage = base.game.campaign.lineages.values.head
    val unsupported = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(lineages = base.game.campaign.lineages.updated(
        lineage.id, lineage.copy(legacies = Vector(
          LegacyState(LegacyId("active-rest"), active = true)))))))
    val actor = unsupported.game.current.turn.activePlayer
    val rejected = rules.handle(Ready(unsupported), RestCommand.Begin(actor))
      .left.toOption.get.asInstanceOf[UnsupportedRoundEndRule]
    assert(rejected.sourceKey.contains("legacy:"))
    assertEquals(rejected.handlerId, "catalog-missing")
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "unsupported-rest",
      oathdigital.application.LoadedGame(Ready(unsupported), 12L), actor)
    assert(!projection.legalControls.contains("beginRest"))
  }

  test("changed round-end handler inventory fails with stable source identity") {
    val base = act
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val adviser = actor.advisers.collectFirst { case d: DenizenState => d }.get
      .copy(orientation = Orientation.FaceUp)
    val changedCatalog = catalog.copy(denizens = catalog.denizens.map { definition =>
      if (definition.id.value != adviser.id.value) definition
      else definition.copy(handlers = Vector("changed.round-end"),
        rulesText = "At end of round, change the result.")
    })
    val changedRules = new OathRules(changedCatalog)
    val changed = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(adviser)) else p))))
    val rejected = changedRules.handle(Ready(changed), RestCommand.Begin(actor.player))
      .left.toOption.get.asInstanceOf[UnsupportedRoundEndRule]
    assertEquals(rejected.sourceKey,
      s"adviser:${actor.player.value}:denizen:${adviser.id.value}")
    assertEquals(rejected.handlerId, "changed.round-end")
  }

  test("site relic edifice banner and Foundation round-end families fail safely") {
    val base = act
    val actor = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    def rejection(c: oathdigital.catalog.ExecutableCatalog, ready: ReadyGame) =
      new OathRules(c).handle(Ready(ready), RestCommand.Begin(actor.player))
        .left.toOption.get.asInstanceOf[UnsupportedRoundEndRule]

    val siteId = base.game.current.map.inPlay.head
    val siteCatalog = catalog.copy(sites = catalog.sites.map(s =>
      if (s.id != siteId) s else s.copy(handlers = s.handlers :+ "site.round-end")))
    assertEquals(rejection(siteCatalog, base).sourceKey, s"site:${siteId.value}")

    val relic = catalog.relics.head
    val relicCatalog = catalog.copy(relics = catalog.relics.map(r =>
      if (r.id != relic.id) r else r.copy(handlers = Vector("relic.victory"),
        rulesText = "Win the game at end of round.")))
    val withRelic = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(relics = Vector(RelicState(RelicId(relic.id.value),
          Orientation.FaceUp, Tokens.empty))) else p))))
    assert(rejection(relicCatalog, withRelic).sourceKey.startsWith("relic:"))

    val edifice = catalog.edifices.head
    val edificeCatalog = catalog.copy(edifices = catalog.edifices.map(e =>
      if (e.id != edifice.id) e else e.copy(intact = e.intact.copy(
        handlers = Vector("edifice.game-end"), rulesText = "At end of round."))))
    val withEdifice = base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        siteId, base.game.current.map.sites(siteId).copy(denizens = Vector(
          EdificeState(EdificeId(edifice.id.value), EdificeSide.Intact,
            Tokens.empty))))))))
    assert(rejection(edificeCatalog, withEdifice).sourceKey.startsWith("edifice:"))

    val banner = base.copy(game = base.game.copy(current = base.game.current.copy(
      banners = base.game.current.banners.copy(peoplesFavor =
        base.game.current.banners.peoplesFavor.copy(
          active = PeoplesFavorFace.GrandCouncil)))))
    assertEquals(rejection(catalog, banner).sourceKey, "banner:peoples-favor")

    val number = base.game.campaign.foundations.keys.head
    val foundation = base.copy(game = base.game.copy(campaign =
      base.game.campaign.copy(foundations = base.game.campaign.foundations.updated(
        number, FoundationState(FoundationFace.Altered, Set.empty)))))
    assertEquals(rejection(catalog, foundation).sourceKey,
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
