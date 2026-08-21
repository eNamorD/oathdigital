package oathdigital.gameplay

import oathdigital.gameplay.actions.{MinorActionCommand, MinorActions}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState.Ready

class MinorActionsSuite extends munit.FunSuite {
  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def ready(): (ReadyGame, PlayerState, SiteId, WorldCardId, RelicId) = {
    val Ready(base) = execute(setupRules)._1: @unchecked
    val active0 = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val siteId = base.game.current.map.inPlay.find(id =>
      base.game.current.map.sites(id).relics.nonEmpty).get
    val adviser = DenizenId(catalog.denizens.find(d =>
      d.restrictions == oathdigital.catalog.CardRestrictions.Unrestricted &&
      !d.rulesText.toUpperCase.contains("WHEN PLAYED")).get.id.value)
    val siteRelic = base.game.current.map.sites(siteId).relics.head.id
    val heldRelic = base.game.current.map.sites.valuesIterator.flatMap(_.relics)
      .map(_.id).find(_ != siteRelic).get
    val active = active0.copy(pawnSite = Some(siteId),
      board = active0.board.copy(warbands = 4),
      advisers = Vector(DenizenState(adviser, Orientation.FaceDown, Tokens.empty)),
      relics = Vector(RelicState(heldRelic, Orientation.FaceDown, Tokens.empty)))
    val site = base.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(active.lineage), 3),
      denizens = Vector.empty)
    val current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == active.player) active else p),
      map = base.game.current.map.copy(sites = base.game.current.map.sites.map {
        case (id, _) if id == siteId => id -> site
        case (id, value) => id -> value.copy(relics = value.relics.filterNot(_.id == heldRelic))
      }),
      turn = base.game.current.turn.copy(phase = Phase.Act))
    (base.copy(game = base.game.copy(current = current)), active, siteId,
      adviser, siteRelic)
  }

  test("facedown adviser discard uses the next region and costs no Supply") {
    val (base, actor, _, adviser, _) = ready()
    val before = actor.board.supply
    val accepted = rules.handle(Ready(base),
      MinorActionCommand.DiscardFacedownAdviser(actor.player, adviser)).toOption.get
    val event = accepted.events.collectFirst { case e: FacedownAdviserDiscarded => e }.get
    val origin = base.game.current.map.regionOf(actor.pawnSite.get).get
    val expected = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    assertEquals(event.destination, expected)
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == actor.player).get.board.supply, before)
    assert(after.game.current.commonCards.discard(expected).contains(adviser))
  }

  test("facedown adviser can play faceup as adviser or at the pawn site") {
    val (base, actor, siteId, adviser, _) = ready()
    val asAdviser = rules.handle(Ready(base), MinorActionCommand.PlayFacedownAdviser(
      actor.player, adviser, SearchPlacement.Adviser(Orientation.FaceUp, None))).toOption.get
    val Ready(adviserReady) = asAdviser.state: @unchecked
    assertEquals(adviserReady.game.current.players.find(_.player == actor.player).get
      .advisers.head.asInstanceOf[DenizenState].orientation, Orientation.FaceUp)

    val atSite = rules.handle(Ready(base), MinorActionCommand.PlayFacedownAdviser(
      actor.player, adviser, SearchPlacement.Site(None))).toOption.get
    val Ready(siteReady) = atSite.state: @unchecked
    assert(siteReady.game.current.map.sites(siteId).denizens.exists(_.id == adviser))
    assertEquals(siteReady.game.current.players.find(_.player == actor.player).get.advisers,
      Vector.empty)
  }

  test("relic peek knowledge is private and reveal changes only owned orientation") {
    val (base, actor, siteId, _, siteRelic) = ready()
    val peeked = rules.handle(Ready(base),
      MinorActionCommand.PeekSiteRelics(actor.player)).toOption.get
    val Ready(peekReady) = peeked.state: @unchecked
    assert(peekReady.support.relicKnowledge(actor.player)(siteId).contains(siteRelic))
    val other = base.game.current.players.find(_.player != actor.player).get.player
    val projector = new oathdigital.application.GameProjector(catalog)
    val loaded = oathdigital.application.LoadedGame(peeked.state, 1)
    val ownerKnown = projector.project("minor", loaded, actor.player).world
      .flatMap(_.sites).find(_.siteId == siteId.value).get.relics.knownRelics
    val otherKnown = projector.project("minor", loaded, other).world
      .flatMap(_.sites).find(_.siteId == siteId.value).get.relics.knownRelics
    assert(ownerKnown.exists(_.cardId == siteRelic.value))
    assertEquals(otherKnown, Vector.empty)
    assertEquals(projector.projectPublic("minor", loaded).world.flatMap(_.sites)
      .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)

    val held = actor.relics.head.id
    val revealed = rules.handle(Ready(base),
      MinorActionCommand.RevealOwnedRelic(actor.player, held)).toOption.get
    val Ready(revealedReady) = revealed.state: @unchecked
    assertEquals(revealedReady.game.current.players.find(_.player == actor.player).get
      .relics.head.orientation, Orientation.FaceUp)
  }

  test("warbands move both ways but site to board must leave one") {
    val (base, actor, siteId, _, _) = ready()
    val toSite = rules.handle(Ready(base),
      MinorActionCommand.MoveWarbands(actor.player, toSite = true, 2)).toOption.get
    val Ready(atSite) = toSite.state: @unchecked
    assertEquals(atSite.game.current.players.find(_.player == actor.player).get.board.warbands, 2)
    assertEquals(atSite.game.current.map.sites(siteId).forces,
      SiteForces.Occupied(ForceKind.Exile(actor.lineage), 5))
    assert(rules.handle(Ready(base), MinorActionCommand.MoveWarbands(
      actor.player, toSite = false, 3)).isLeft)
    val toBoard = rules.handle(Ready(base), MinorActionCommand.MoveWarbands(
      actor.player, toSite = false, 2)).toOption.get
    val Ready(onBoard) = toBoard.state: @unchecked
    assertEquals(onBoard.game.current.players.find(_.player == actor.player).get.board.warbands, 6)
    assertEquals(onBoard.game.current.map.sites(siteId).forces,
      SiteForces.Occupied(ForceKind.Exile(actor.lineage), 1))
  }

  test("tampered event facts and unsupported adviser handlers fail replay evolution") {
    val (base, actor, siteId, adviser, _) = ready()
    assert(MinorActions.evolve(catalog, Ready(base), WarbandsMoved(actor.player,
      siteId, toSite = true, 1, priorBoardWarbands = 99, priorSiteWarbands = 3)).isLeft)
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.dazzle")).get.id.value)
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(powered, Orientation.FaceDown, Tokens.empty))) else p))))
    assert(rules.handle(Ready(modified), MinorActionCommand.PlayFacedownAdviser(
      actor.player, powered, SearchPlacement.Adviser(Orientation.FaceUp, None))).isLeft)
  }
}
