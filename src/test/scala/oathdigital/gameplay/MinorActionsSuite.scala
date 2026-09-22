package oathdigital.gameplay

import oathdigital.gameplay.actions.{MinorActionCommand,
  MinorActionOperationPolicy, MinorActions}
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready

class MinorActionsSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)

  private def ready(): (ReadyGame, PlayerState, SiteId, WorldCardId, RelicId) = {
    val base = initialReady
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
  ): ReadyGame = ready.updateCurrent(_.copy(players = ready.game.current.players.map {
      case player if player.player == playerId => player.copy(
        relics = player.relics.map {
          case relic if relic.id == relicId =>
            relic.copy(orientation = Orientation.FaceUp)
          case relic => relic
        })
      case player => player
    }))

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
    ready.updateCurrent(_.copy(
      players = players,
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        site.copy(forces = forces)))))
  }

  test("facedown adviser discard uses the next region and costs no Supply") {
    val (base, actor, _, adviser, _) = ready()
    val before = actor.board.supply
    val started = rules.startWalker(Ready(base), ActionRef.PlayFacedownAdviser,
      actor.player, startArgs = Vector(DecisionOptionRef.Denizen(
        adviser.asInstanceOf[DenizenId]))).toOption.get
    val accepted = rules.resolveWalker(started.state, actor.player,
      s"cardplay.place.${adviser.kind}.${adviser.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))
      .toOption.get
    val origin = base.game.current.map.regionOf(actor.pawnSite.get).get
    val expected = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == actor.player).get.board.supply, before)
    assert(after.game.current.commonCards.discard(expected).contains(adviser))
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
    val reveal = oathdigital.model.Reveal(relic,
      oathdigital.model.Location.PlayArea(actor.player))
    val directFlip = oathdigital.model.Flip(relic,
      oathdigital.model.Location.PlayArea(actor.player),
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

  test("source-scoped fallback and replay use the recorded off-turn actor") {
    val (base, active, _, _, _) = ready()
    val other0 = base.game.current.players.find(_.player != active.player).get
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.revelation")).get.id.value)
    val other = other0.copy(advisers = Vector(
      DenizenState(powered, Orientation.FaceUp, Tokens.empty)))
    val changed = base.updateCurrent(_.copy(
      players = base.game.current.players.map(player =>
        if (player.player == other.player) other else player)))
    val source = RuleSourceRef.Adviser(other.player, powered)
    val expected = PowerRuntime.ignoredAtSource(catalog, changed, other.player,
      ActionKind.WhenPlayed, source).toOption.get
    assertEquals(expected.map(_.handlerId), Vector("denizen.revelation"))
    assertEquals(PowerRuntime.ignoredAtSource(catalog, changed, active.player,
      ActionKind.WhenPlayed, source).toOption.get, Vector.empty)
    val event = IgnoredRulesRecorded(other.player, ActionKind.WhenPlayed, expected)
    assert(rules.evolve(Ready(changed), event).isRight)
  }

  test("locked restriction applies only faceup and does not prevent facedown discard") {
    val (base, actor, _, _, _) = ready()
    val locked = DenizenId(catalog.denizens.find(_.restrictions ==
      oathdigital.catalog.CardRestrictions.LockedAdviserOnly).get.id.value)
    val modified = base.updateCurrent(_.copy(
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(_ == locked)),
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(locked, Orientation.FaceDown, Tokens.empty))) else p)))
    val started = rules.startWalker(Ready(modified), ActionRef.PlayFacedownAdviser,
      actor.player, startArgs = Vector(DecisionOptionRef.Denizen(locked)))
      .toOption.get
    assert(rules.resolveWalker(started.state, actor.player,
      s"cardplay.place.denizen.${locked.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard"))).isRight)
    val faceup = modified.updateCurrent(_.copy(
      players = modified.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = Vector(DenizenState(locked, Orientation.FaceUp, Tokens.empty))) else p)))
    assert(oathdigital.gameplay.actions.cardplay.CardPlayProcedure.buildFacedown(
      catalog, faceup, actor.player, Vector(DecisionOptionRef.Denizen(locked))).isLeft)
  }

  test("valid setup history replays exactly through a completed minor action") {
    val (setupState, setupEvents) = execute()
    val active = setupState.asInstanceOf[Ready].value.game.current.turn.activePlayer
    val adviser = setupState.asInstanceOf[Ready].value.game.current.players
      .find(_.player == active).get.advisers.head.id.asInstanceOf[WorldCardId]
    val act = rules.startWalker(setupState, PhaseTransitionRef.EndWake, active)
      .toOption.get
    val started = rules.startWalker(act.state, ActionRef.PlayFacedownAdviser,
      active, startArgs = Vector(adviser match {
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      })).toOption.get
    val discarded = rules.resolveWalker(started.state, active,
      s"cardplay.place.${adviser.kind}.${adviser.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))
      .toOption.get
    val events = setupEvents ++ act.events ++ started.events ++ discarded.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, discarded.state)
  }
}
