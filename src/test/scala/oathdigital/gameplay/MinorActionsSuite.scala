package oathdigital.gameplay

import oathdigital.gameplay.actions.{MinorActionCommand,
  MinorActionOperationPolicy, MinorActions}
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready

class MinorActionsSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)

  /** p1 in Act at Broken Peaks (where p2 also stands), ruling it with 3
    * warbands and holding 4 more, a facedown Magician's Code and a facedown
    * Cursed Cauldron. Broken Peaks holds Sticky Fire, facedown. */
  private def ready(): (ReadyGame, PlayerState, SiteId, WorldCardId, RelicId) =
    val site = CatalogNames.site("Broken Peaks")
    val adviser = CatalogNames.denizen("Magician's Code")
    val siteRelic = CatalogNames.relic("Sticky Fire")
    val staged = Table.start
      .pawn(p1, at = site).warbands(p1, 4).warbandsAt(site, p1, 3)
      .adviser(p1, adviser, facedown = true)
      .relic(p1, "Cursed Cauldron", facedown = true)
      .relicAt(siteRelic, at = site)
      .ready
    (staged, Look(staged).player(p1), site, adviser, siteRelic)

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
  ): ReadyGame =
    val boardDelta = if toSite then -amount else amount
    val siteDelta = -boardDelta
    val current = ready.game.current
    val players = current.players.map { player =>
      if player.player == playerId then player.copy(board = player.board.copy(
        warbands = player.board.warbands + boardDelta))
      else player
    }
    val site = current.map.sites(siteId)
    val forces = site.forces match
      case occupied: SiteForces.Occupied =>
        occupied.copy(count = occupied.count + siteDelta)
      case SiteForces.Empty => fail("fixture site must have warbands")
    ready.updateCurrent(_.copy(
      players = players,
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        site.copy(forces = forces)))))

  test("facedown adviser discard uses the next region and costs no Supply"):
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
    val expected = origin match
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == actor.player).get.board.supply, before)
    assert(after.game.current.commonCards.discard(expected).contains(adviser))

  test("site relic peek uses core operations and preserves knowledge"):
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
    parked.assertResumed(peeked.state, Phase.Act, actor.player)
    assertEquals(rules.evolve(Ready(base), event), Right(Ready(expected)))

    val previouslyKnown = actor.relics.head.id
    val withPriorKnowledge = base.copy(knowledge = base.knowledge.copy(siteRelics =
      Map(actor.player -> Map(siteId -> Vector(previouslyKnown)))))
    val expectedMerged = withPriorKnowledge.copy(knowledge =
      withPriorKnowledge.knowledge.copy(siteRelics = Map(actor.player -> Map(
        siteId -> (Vector(previouslyKnown) ++ event.relics)))))
    assertEquals(MinorActions.evolve(catalog, Ready(withPriorKnowledge), event),
      Right(Ready(expectedMerged)))

  test("owned relic reveal uses core operation and preserves replay"):
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
    parked.assertResumed(revealed.state, Phase.Act, actor.player)
    assertEquals(rules.evolve(Ready(base), revealEvent), Right(Ready(expected)))
    val replayed = completed.events.foldLeft[
      Either[OathViolation, OathState]](Right(Ready(base))):
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure
    assertEquals(replayed, Right(completed.state))

  test("owned relic reveal rejects unheld and already-faceup relics"):
    val (base, actor, _, _, siteRelic) = ready()
    val held = actor.relics.head.id
    val revealEvent = OwnedRelicRevealed(actor.player, held)
    assertEquals(MinorActions.evolve(catalog, Ready(base),
      OwnedRelicRevealed(actor.player, siteRelic)).left.toOption, Some(OathViolation.MinorActionUnavailable("relic is not held by the actor")))
    val alreadyFaceUp = withRevealedRelic(base, actor.player, held)
    assertEquals(MinorActions.evolve(catalog, Ready(alreadyFaceUp),
      revealEvent).left.toOption, Some(OathViolation.MinorActionOutcomeMismatch(
        "recorded relic was not facedown")))

  test("minor-action operation policy permits roots only in validated context"):
    val (base, actor, _, _, _) = ready()
    val relic = actor.relics.head.id
    val reveal = oathdigital.model.Reveal(relic,
      oathdigital.model.Location.PlayArea(actor.player))
    val directFlip = oathdigital.model.Flip(relic,
      oathdigital.model.Location.PlayArea(actor.player),
      Orientation.FaceUp)

    assertEquals(MinorActionOperationPolicy.validate(base, reveal), Right(()))
    assertEquals(MinorActionOperationPolicy.validate(base, directFlip).left.toOption,
      Some(OperationError.RestrictedOperation(
        "minor-action semantic root is not permitted")))

  test("warband moves use core operations in both directions and replay"):
    val (base, actor, siteId, _, _) = ready()
    val toSite = MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(actor.player, toSite = true, 2)).toOption.get
    val toSiteEvent = toSite.events.collectFirst { case value: WarbandsMoved => value }.get
    val Ready(atSite) = toSite.state: @unchecked
    val expectedAtSite = withMovedWarbands(base, actor.player, siteId,
      toSite = true, 2)
    assertEquals(atSite, expectedAtSite)
    assertEquals(toSite.events, Vector(toSiteEvent))
    parked.assertResumed(toSite.state, Phase.Act, actor.player)
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

  test("source-scoped fallback and replay use the recorded off-turn actor"):
    val (base, active, _, _, _) = ready()
    val other0 = base.game.current.players.find(_.player != active.player).get
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.revelation")).get.id.value)
    val other = other0.copy(advisers = Vector(
      DenizenState(powered, Orientation.FaceUp, Tokens.empty)))
    val changed = base.updateCurrent(_.copy(
      players = base.game.current.players.map(player =>
        if player.player == other.player then other else player)))
    val source = RuleSourceRef.Adviser(other.player, powered)
    val expected = PowerRuntime.ignoredAtSource(catalog, changed, other.player,
      ActionKind.WhenPlayed, source).toOption.get
    assertEquals(expected.map(_.handlerId), Vector("denizen.revelation"))
    assertEquals(PowerRuntime.ignoredAtSource(catalog, changed, active.player,
      ActionKind.WhenPlayed, source).toOption.get, Vector.empty)
    // Replay accepts the off-turn player's diagnostics and changes no state.
    val event = IgnoredRulesRecorded(other.player, ActionKind.WhenPlayed, expected)
    assertEquals(rules.evolve(Ready(changed), event), Right(Ready(changed)))

  // The lock itself, which stops a faceup adviser being discarded, is
  // OperationRestrictionsSuite's; this is the facedown side of it.
  test("a locked adviser held facedown can still be played and discarded"):
    val (base, actor, _, _, _) = ready()
    val locked = DenizenId(catalog.denizens.find(_.restrictions ==
      oathdigital.catalog.CardRestrictions.LockedAdviserOnly).get.id.value)
    val modified = base.updateCurrent(_.copy(
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(_ == locked)),
      players = base.game.current.players.map(p => if p.player == actor.player then
        p.copy(advisers = Vector(DenizenState(locked, Orientation.FaceDown, Tokens.empty))) else p)))
    val started = rules.startWalker(Ready(modified), ActionRef.PlayFacedownAdviser,
      actor.player, startArgs = Vector(DecisionOptionRef.Denizen(locked)))
      .toOption.get
    val discarded = rules.resolveWalker(started.state, actor.player,
      s"cardplay.place.denizen.${locked.value}",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))
      .toOption.get
    assertEquals(Look(discarded.state).advisers(actor.player), Vector.empty)

  test("valid setup history replays exactly through a completed minor action"):
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
