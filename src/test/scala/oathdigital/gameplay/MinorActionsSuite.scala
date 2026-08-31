package oathdigital.gameplay

import oathdigital.gameplay.actions.{MinorActionCommand, MinorActionPowerSupport,
  MinorActions, VisionRules}
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation.{UnsupportedMinorActionCatalogInventory,
  UnsupportedMinorActionRule}
import oathdigital.catalog.CatalogPower

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

  test("tampered facts fail while reviewed When Played handlers record fallback") {
    val (base, actor, siteId, adviser, _) = ready()
    assert(MinorActions.evolve(catalog, Ready(base), WarbandsMoved(actor.player,
      siteId, toSite = true, 1, priorBoardWarbands = 99, priorSiteWarbands = 3)).isLeft)
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.dazzle")).get.id.value)
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(powered, Orientation.FaceDown, Tokens.empty))) else p))))
    val accepted = rules.handle(Ready(modified), MinorActionCommand.PlayFacedownAdviser(
      actor.player, powered, SearchPlacement.Adviser(Orientation.FaceUp, None)))
      .toOption.get
    assert(accepted.events.head.isInstanceOf[FacedownAdviserPlayed])
    val diagnostic = accepted.events(1).asInstanceOf[IgnoredRulesRecorded]
    assertEquals(diagnostic.action, MajorActionKind.WhenPlayed)
    assertEquals(diagnostic.diagnostics.map(_.handlerId), Vector("denizen.dazzle"))
  }

  test("site play records primary event before replay-valid When Played fallback") {
    val (base, actor, siteId, _, _) = ready()
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.dazzle")).get.id.value)
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(powered, Orientation.FaceDown,
          Tokens.empty))) else p))))
    val accepted = rules.handle(Ready(modified), MinorActionCommand.PlayFacedownAdviser(
      actor.player, powered, SearchPlacement.Site(None))).toOption.get
    val played = accepted.events.head.asInstanceOf[FacedownAdviserPlayed]
    val diagnostic = accepted.events(1).asInstanceOf[IgnoredRulesRecorded]
    assertEquals(diagnostic.diagnostics.map(_.source),
      Vector(RuleSourceRef.SiteCard(siteId, powered)))

    val replayed = accepted.events.foldLeft[
      Either[OathViolation, OathState]](Right(Ready(modified))) {
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure
    }
    assertEquals(replayed, Right(accepted.state))

    val afterPlay = rules.evolve(Ready(modified), played).toOption.get
    val tampered = diagnostic.copy(diagnostics = diagnostic.diagnostics.map(_.copy(
      source = RuleSourceRef.GameRule("tampered"))))
    assert(rules.evolve(afterPlay, tampered).isLeft)
  }

  test("source-scoped fallback and replay use the recorded off-turn actor") {
    val (base, active, _, _, _) = ready()
    val other0 = base.game.current.players.find(_.player != active.player).get
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.dazzle")).get.id.value)
    val other = other0.copy(advisers = Vector(
      DenizenState(powered, Orientation.FaceUp, Tokens.empty)))
    val changed = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(player =>
        if (player.player == other.player) other else player))))
    val source = RuleSourceRef.Adviser(other.player, powered)
    val expected = PowerRuntime.ignoredAtSource(catalog, changed, other.player,
      MajorActionKind.WhenPlayed, source).toOption.get
    assertEquals(expected.map(_.handlerId), Vector("denizen.dazzle"))
    assertEquals(PowerRuntime.ignoredAtSource(catalog, changed, active.player,
      MajorActionKind.WhenPlayed, source).toOption.get, Vector.empty)
    val event = IgnoredRulesRecorded(other.player, MajorActionKind.WhenPlayed, expected)
    assert(rules.evolve(Ready(changed), event).isRight)
  }

  test("Conspiracy play is an explicit unsupported power in command and replay") {
    val (base, actor, _, _, _) = ready()
    val conspiracy = MinorActionPowerSupport.Conspiracy
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(VisionState(conspiracy, Orientation.FaceDown))) else p))))
    val command = MinorActionCommand.PlayFacedownAdviser(actor.player, conspiracy,
      SearchPlacement.Adviser(Orientation.FaceUp, None))
    assertEquals(rules.handle(Ready(modified), command).left.toOption,
      Some(UnsupportedMinorActionRule(conspiracy, Vector("vision.conspiracy"))))
    val tampered = FacedownAdviserPlayed(actor.player, conspiracy,
      SearchPlacement.Adviser(Orientation.FaceUp, None), 0, Vector.empty, Vector.empty)
    assertEquals(MinorActions.evolve(catalog, Ready(modified), tampered).left.toOption,
      Some(UnsupportedMinorActionRule(conspiracy, Vector("vision.conspiracy"))))
  }

  test("true Visions cannot bypass the authoritative reveal procedure") {
    val (base, actor, _, _, _) = ready()
    val vision = VisionRules.Conquest
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(VisionState(vision, Orientation.FaceDown))) else p))))
    val placement = SearchPlacement.Adviser(Orientation.FaceUp, None)
    assertEquals(MinorActions.legalAdviserPlacements(
      catalog, modified, actor.player, vision), Vector.empty)
    assertEquals(rules.handle(Ready(modified), MinorActionCommand.PlayFacedownAdviser(
      actor.player, vision, placement)).left.toOption,
      Some(UnsupportedMinorActionRule(vision, Vector("vision.reveal-procedure"))))
    val projected = new oathdigital.application.GameProjector(catalog).project(
      "vision-minor", oathdigital.application.LoadedGame(Ready(modified), 0),
      actor.player)
    val projectedPlacements = projected.minorActions.toVector.flatMap(_.advisers)
      .filter(_.card.cardId == vision.value).flatMap(_.placements)
    assertEquals(projectedPlacements.map(_.kind), Vector("discard"))
    assert(projected.legalControls.contains("revealVision"))
  }

  test("locked restriction applies only faceup and does not prevent facedown discard") {
    val (base, actor, _, _, _) = ready()
    val locked = DenizenId(catalog.denizens.find(_.restrictions ==
      oathdigital.catalog.CardRestrictions.LockedAdviserOnly).get.id.value)
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(locked, Orientation.FaceDown, Tokens.empty))) else p))))
    assert(rules.handle(Ready(modified),
      MinorActionCommand.DiscardFacedownAdviser(actor.player, locked)).isRight)
    val faceup = modified.copy(game = modified.game.copy(current = modified.game.current.copy(
      players = modified.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(locked, Orientation.FaceUp, Tokens.empty))) else p))))
    assertEquals(MinorActions.legalAdviserPlacements(catalog, faceup, actor.player, locked),
      Vector.empty)
  }

  test("valid setup history replays exactly through a completed minor action") {
    val (setupState, setupEvents) = execute(setupRules)
    val active = setupState.asInstanceOf[Ready].value.game.current.turn.activePlayer
    val adviser = setupState.asInstanceOf[Ready].value.game.current.players
      .find(_.player == active).get.advisers.head.id.asInstanceOf[WorldCardId]
    val act = rules.handle(setupState,
      oathdigital.gameplay.phases.WakeCommand.EndWake(active)).toOption.get
    val discarded = rules.handle(act.state,
      MinorActionCommand.DiscardFacedownAdviser(active, adviser)).toOption.get
    val events = setupEvents ++ act.events ++ discarded.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, discarded.state)
  }

  test("audited minor-action power inventory rejects changed handler vocabulary") {
    val first = catalog.denizens.head
    val changed = catalog.copy(denizens = first.copy(powers = first.powers :+
      CatalogPower("denizen.future-handler", persistent = false, "Future power.")) +:
      catalog.denizens.tail)
    assert(MinorActionPowerSupport.validateInventory(changed).left.toOption.exists(
      _.isInstanceOf[UnsupportedMinorActionCatalogInventory]))
  }

  test("Vision inventory fingerprint covers every runtime handler family and edifice face") {
    val changed = Vector(
      catalog.copy(relics = catalog.relics.head.copy(
        powers = catalog.relics.head.powers :+ CatalogPower(
          "relic.future-vision", persistent = false, "Future power.")) +:
          catalog.relics.tail),
      catalog.copy(edifices = catalog.edifices.head.copy(intact =
        catalog.edifices.head.intact.copy(powers =
          catalog.edifices.head.intact.powers :+ CatalogPower(
            "edifice.future-vision", persistent = false, "Future power."))) +:
          catalog.edifices.tail),
      catalog.copy(edifices = catalog.edifices.head.copy(ruined =
        catalog.edifices.head.ruined.copy(powers =
          catalog.edifices.head.ruined.powers :+ CatalogPower(
            "edifice.future-ruined-vision", persistent = false, "Future power."))) +:
          catalog.edifices.tail),
      catalog.copy(legacies = catalog.legacies.head.copy(
        powers = catalog.legacies.head.powers :+ CatalogPower(
          "legacy.future-vision", persistent = false, "Future power.")) +:
          catalog.legacies.tail),
      catalog.copy(sites = catalog.sites.head.copy(
        handlers = catalog.sites.head.handlers :+ "site.future-vision") +:
          catalog.sites.tail))
    changed.foreach(value => assert(
      MinorActionPowerSupport.validateInventory(value).isLeft))
  }
}
