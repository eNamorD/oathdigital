package oathdigital.gameplay

import oathdigital.gameplay.actions.{MinorActionCommand, MinorActionPowerSupport,
  MinorActionOperationPolicy, MinorActions, VisionRules}
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
      commonCards = base.game.current.commonCards.copy(
        worldDeck = base.game.current.commonCards.worldDeck.filterNot(_ == adviser)),
      turn = base.game.current.turn.copy(phase = Phase.Act))
    (base.copy(game = base.game.copy(current = current)), active, siteId,
      adviser, siteRelic)
  }

  private def withRevealedRelic(
      ready: ReadyGame,
      playerId: PlayerId,
      relicId: RelicId
  ): ReadyGame = ready.copy(game = ready.game.copy(current =
    ready.game.current.copy(players = ready.game.current.players.map {
      case player if player.player == playerId => player.copy(
        relics = player.relics.map {
          case relic if relic.id == relicId =>
            relic.copy(orientation = Orientation.FaceUp)
          case relic => relic
        })
      case player => player
    })))

  private def withMovedWarbands(
      ready: ReadyGame,
      playerId: PlayerId,
      siteId: SiteId,
      toSite: Boolean,
      amount: Int
  ): ReadyGame = {
    val boardDelta = if (toSite) -amount else amount
    val siteDelta = -boardDelta
    val current = ready.game.current
    val players = current.players.map { player =>
      if (player.player == playerId) player.copy(board = player.board.copy(
        warbands = player.board.warbands + boardDelta))
      else player
    }
    val site = current.map.sites(siteId)
    val forces = site.forces match {
      case occupied: SiteForces.Occupied =>
        occupied.copy(count = occupied.count + siteDelta)
      case SiteForces.Empty => fail("fixture site must have warbands")
    }
    ready.copy(game = ready.game.copy(current = current.copy(
      players = players,
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        site.copy(forces = forces))))))
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

  test("site relic peek uses core operations and preserves knowledge") {
    val (base, actor, siteId, _, siteRelic) = ready()
    val command = MinorActionCommand.PeekSiteRelics(actor.player)
    val peeked = MinorActions.handle(catalog, Ready(base), command).toOption.get
    val Ready(peekReady) = peeked.state: @unchecked
    assert(peekReady.knowledge.siteRelics(actor.player)(siteId).contains(siteRelic))
    val event = peeked.events.collectFirst { case value: SiteRelicsPeeked => value }.get
    val expected = base.copy(knowledge = base.knowledge.copy(siteRelics =
      base.knowledge.siteRelics.updated(actor.player,
        base.knowledge.siteRelics.getOrElse(actor.player, Map.empty)
          .updated(siteId, event.relics))))
    assertEquals(peeked.state, Ready(expected))
    assertEquals(peeked.events, Vector(event))
    assertEquals(peeked.continue, OathContinue.ActActionSelection(actor.player))
    assertEquals(rules.evolve(Ready(base), event), Right(Ready(expected)))

    val other = base.game.current.players.find(_.player != actor.player).get.player
    val projector = new oathdigital.application.GameProjector(catalog)
    val loaded = oathdigital.application.LoadedGame(Ready(expected), 1)
    val ownerKnown = projector.project("minor", loaded, actor.player).world
      .flatMap(_.sites).find(_.siteId == siteId.value).get.relics.knownRelics
    val otherKnown = projector.project("minor", loaded, other).world
      .flatMap(_.sites).find(_.siteId == siteId.value).get.relics.knownRelics
    assert(ownerKnown.exists(_.cardId == siteRelic.value))
    assertEquals(otherKnown, Vector.empty)
    assertEquals(projector.projectPublic("minor", loaded).world.flatMap(_.sites)
      .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)

    val previouslyKnown = actor.relics.head.id
    val withPriorKnowledge = base.copy(knowledge = base.knowledge.copy(siteRelics =
      Map(actor.player -> Map(siteId -> Vector(previouslyKnown)))))
    val expectedMerged = withPriorKnowledge.copy(knowledge =
      withPriorKnowledge.knowledge.copy(siteRelics = Map(actor.player -> Map(
        siteId -> (Vector(previouslyKnown) ++ event.relics)))))
    assertEquals(MinorActions.evolve(catalog, Ready(withPriorKnowledge), event),
      Right(Ready(expectedMerged)))
  }

  test("owned relic reveal uses core operation and preserves replay") {
    val (base, actor, _, _, _) = ready()
    val held = actor.relics.head.id
    val revealCommand = MinorActionCommand.RevealOwnedRelic(actor.player, held)
    val revealed = MinorActions.handle(catalog, Ready(base), revealCommand)
      .toOption.get
    val completed = rules.handle(Ready(base), revealCommand).toOption.get
    val Ready(revealedReady) = revealed.state: @unchecked
    assertEquals(revealedReady.game.current.players.find(_.player == actor.player).get
      .relics.head.orientation, Orientation.FaceUp)

    val revealEvent = revealed.events.collectFirst {
      case event: OwnedRelicRevealed => event
    }.get
    val expected = withRevealedRelic(base, actor.player, held)
    assertEquals(revealedReady, expected)
    assertEquals(revealed.events, Vector(revealEvent))
    assertEquals(revealed.continue,
      OathContinue.ActActionSelection(actor.player))
    assertEquals(rules.evolve(Ready(base), revealEvent), Right(Ready(expected)))
    val replayed = completed.events.foldLeft[
      Either[OathViolation, OathState]](Right(Ready(base))) {
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure
    }
    assertEquals(replayed, Right(completed.state))
  }

  test("owned relic reveal rejects unheld and already-faceup relics") {
    val (base, actor, _, _, siteRelic) = ready()
    val held = actor.relics.head.id
    val revealEvent = OwnedRelicRevealed(actor.player, held)
    assert(MinorActions.evolve(catalog, Ready(base),
      OwnedRelicRevealed(actor.player, siteRelic)).isLeft)
    val alreadyFaceUp = withRevealedRelic(base, actor.player, held)
    assert(MinorActions.evolve(catalog, Ready(alreadyFaceUp),
      revealEvent).isLeft)
  }

  test("minor-action operation policy permits roots only in validated context") {
    val (base, actor, _, _, _) = ready()
    val relic = actor.relics.head.id
    val reveal = oathdigital.gameplay.operations.Reveal(relic,
      oathdigital.gameplay.operations.Location.PlayArea(actor.player))
    val directFlip = oathdigital.gameplay.operations.Flip(relic,
      oathdigital.gameplay.operations.Location.PlayArea(actor.player),
      Orientation.FaceUp)

    assert(MinorActionOperationPolicy.validate(base, reveal).isRight)
    assert(MinorActionOperationPolicy.validate(base, directFlip).isLeft)
  }

  test("warband moves use core operations in both directions and replay") {
    val (base, actor, siteId, _, _) = ready()
    val toSite = MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(actor.player, toSite = true, 2)).toOption.get
    val toSiteEvent = toSite.events.collectFirst { case value: WarbandsMoved => value }.get
    val Ready(atSite) = toSite.state: @unchecked
    val expectedAtSite = withMovedWarbands(base, actor.player, siteId,
      toSite = true, 2)
    assertEquals(atSite, expectedAtSite)
    assertEquals(toSite.events, Vector(toSiteEvent))
    assertEquals(toSite.continue, OathContinue.ActActionSelection(actor.player))
    assertEquals(rules.evolve(Ready(base), toSiteEvent), Right(Ready(atSite)))
    assertEquals(atSite.game.current.players.find(_.player == actor.player).get.board.warbands, 2)
    assertEquals(atSite.game.current.map.sites(siteId).forces,
      SiteForces.Occupied(ForceKind.Exile(actor.lineage), 5))

    assert(rules.handle(Ready(base), MinorActionCommand.MoveWarbands(
      actor.player, toSite = false, 3)).isLeft)
    val toBoard = MinorActions.handle(catalog, Ready(base), MinorActionCommand.MoveWarbands(
      actor.player, toSite = false, 2)).toOption.get
    val toBoardEvent = toBoard.events.collectFirst { case value: WarbandsMoved => value }.get
    val Ready(onBoard) = toBoard.state: @unchecked
    val expectedOnBoard = withMovedWarbands(base, actor.player, siteId,
      toSite = false, 2)
    assertEquals(onBoard, expectedOnBoard)
    assertEquals(rules.evolve(Ready(base), toBoardEvent), Right(Ready(onBoard)))
    assertEquals(onBoard.game.current.players.find(_.player == actor.player).get.board.warbands, 6)
    assertEquals(onBoard.game.current.map.sites(siteId).forces,
      SiteForces.Occupied(ForceKind.Exile(actor.lineage), 1))
  }

  test("tampered facts fail while reviewed When Played handlers record fallback") {
    val (base, actor, siteId, adviser, _) = ready()
    assert(MinorActions.evolve(catalog, Ready(base), WarbandsMoved(actor.player,
      siteId, toSite = true, 1, priorBoardWarbands = 99, priorSiteWarbands = 3)).isLeft)
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.revelation")).get.id.value)
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(powered, Orientation.FaceDown, Tokens.empty))) else p))))
    val accepted = rules.handle(Ready(modified), MinorActionCommand.PlayFacedownAdviser(
      actor.player, powered, SearchPlacement.Adviser(Orientation.FaceUp, None)))
      .toOption.get
    assert(accepted.events.head.isInstanceOf[FacedownAdviserPlayed])
    val diagnostic = accepted.events(1).asInstanceOf[IgnoredRulesRecorded]
    assertEquals(diagnostic.action, MajorActionKind.WhenPlayed)
    assertEquals(diagnostic.diagnostics.map(_.handlerId), Vector("denizen.revelation"))
  }

  test("site play records primary event before replay-valid When Played fallback") {
    val (base, actor, siteId, _, _) = ready()
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.revelation")).get.id.value)
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
      _.handlers.contains("denizen.revelation")).get.id.value)
    val other = other0.copy(advisers = Vector(
      DenizenState(powered, Orientation.FaceUp, Tokens.empty)))
    val changed = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(player =>
        if (player.player == other.player) other else player))))
    val source = RuleSourceRef.Adviser(other.player, powered)
    val expected = PowerRuntime.ignoredAtSource(catalog, changed, other.player,
      MajorActionKind.WhenPlayed, source).toOption.get
    assertEquals(expected.map(_.handlerId), Vector("denizen.revelation"))
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
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(_ == locked)),
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
    val act = rules.startWalker(setupState, PhaseTransitionRef.EndWake, active)
      .toOption.get
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
