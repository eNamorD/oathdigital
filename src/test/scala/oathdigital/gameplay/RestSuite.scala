package oathdigital.gameplay

import oathdigital.gameplay.phases.{RestCommand, WakeCommand}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent.{RestCompleted, RestStarted}
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation.{RestOutcomeMismatch, UnsupportedRestState}

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
      val event = rules.handle(began.state, RestCommand.Finish(player))
        .toOption.get.events.head.asInstanceOf[RestCompleted]
      val tampered = event.copy(refreshedSupply = event.refreshedSupply - 1)
      assert(rules.evolve(began.state, tampered).left.toOption.get
        .isInstanceOf[RestOutcomeMismatch])
      state = rules.evolve(began.state, event).toOption.get
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
    assert(rules.handle(Ready(unsupported), RestCommand.Begin(actor))
      .left.toOption.get.isInstanceOf[UnsupportedRestState])
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "unsupported-rest",
      oathdigital.application.LoadedGame(Ready(unsupported), 12L), actor)
    assert(!projection.legalControls.contains("beginRest"))
  }

  test("last player of round eight cannot enter an unfinishable Rest") {
    val base = act
    val participants = base.game.current.players.map(_.player)
    val start = participants.indexOf(base.support.firstPlayer)
    val last = (participants.drop(start) ++ participants.take(start)).last
    val unsupported = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        tracks = base.game.current.tracks.copy(round = 8),
        turn = TurnState(last, Phase.Act, Set.empty))))

    assert(rules.handle(Ready(unsupported), RestCommand.Begin(last))
      .left.toOption.get.isInstanceOf[UnsupportedRestState])
    val projection = new oathdigital.application.GameProjector(catalog).project(
      "round-eight", oathdigital.application.LoadedGame(
        Ready(unsupported), 30L), last)
    assert(!projection.legalControls.contains("beginRest"))
  }
}
