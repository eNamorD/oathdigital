package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameContinue._
import oathdigital.setup.FirstGameSetupEvent._
import oathdigital.setup.FirstGameSetupState._
import oathdigital.setup.FirstGameSetupViolation._

sealed trait WakeCommand extends Product with Serializable
object WakeCommand {
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends WakeCommand
  final case class EndWake(playerId: PlayerId) extends WakeCommand
}

sealed trait TravelCommand extends Product with Serializable
object TravelCommand {
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends TravelCommand
}

sealed trait SearchCommand extends Product with Serializable
object SearchCommand {
  /** Trusted, server-prepared outcome. HTTP clients never construct this. */
  final case class Start(
      playerId: PlayerId,
      decision: DecisionId,
      source: SearchSource,
      drawn: Vector[WorldCardId]
  ) extends SearchCommand
  final case class Complete(
      playerId: PlayerId,
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ) extends SearchCommand
}

/** Unified setup/gameplay evolution for bounded first-turn Wake and Travel. */
final class FirstGameRules(catalog: ExecutableCatalog)
    extends EventEvolution[
      FirstGameSetupState,
      FirstGameSetupEvent,
      FirstGameSetupViolation
    ] {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: FirstGameSetupState = setup.initialState

  def handle(
      state: FirstGameSetupState,
      command: WakeCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    command match {
      case WakeCommand.TakeWealth(playerId, resource) =>
        validateReady(state, playerId).flatMap { ready =>
          val player = ready.game.current.players.find(_.player == playerId).get
          player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { siteId =>
            TakeWealthRules.validate(ready, player, siteId, resource).flatMap {
              _ => transition(
                  state,
                  Vector(WealthTaken(playerId, siteId, resource)),
                  AwaitingWakeAction(playerId)
                )
            }
          }
        }
      case WakeCommand.EndWake(playerId) =>
        validateReady(state, playerId).flatMap(_ =>
          transition(
            state,
            Vector(WakeEnded(playerId)),
            ActActionSelection(playerId)
          ))
    }

  def handle(
      state: FirstGameSetupState,
      command: TravelCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] = command match {
    case TravelCommand.Travel(playerId, destination) =>
      validateAct(state, playerId).flatMap { ready =>
        val player = ready.game.current.players.find(_.player == playerId).get
        player.pawnSite.toRight(PawnSiteMissing(playerId)).flatMap { source =>
          TravelRules.cost(catalog, ready, player, source, destination)
            .flatMap { cost =>
              if (player.board.supply.supply < cost)
                Left(InsufficientSupply(cost, player.board.supply.supply))
              else transition(
                state,
                Vector(Traveled(playerId, source, destination, cost)),
                ActActionSelection(playerId)
              )
            }
        }
      }
  }

  def handle(
      state: FirstGameSetupState,
      command: SearchCommand
  ): Either[FirstGameSetupViolation, FirstGameTransition] = command match {
    case SearchCommand.Start(playerId, decision, source, drawn) =>
      validateAct(state, playerId).flatMap { ready =>
        val player = ready.game.current.players.find(_.player == playerId).get
        for {
          origin <- player.pawnSite.flatMap(ready.game.current.map.regionOf)
            .toRight(PawnSiteMissing(playerId))
          cost <- SearchRules.cost(ready, source, origin)
          _ <- SearchRules.validateSupportedState(ready)
          expected <- SearchRules.draw(ready, source, origin)
          _ <- if (drawn == expected) Right(()) else
            Left(SearchDrawMismatch("prepared draw does not match authoritative source order"))
          _ <- if (drawn.nonEmpty) Right(()) else Left(SearchSourceUnavailable(source))
          _ <- if (player.board.supply.supply >= cost) Right(()) else
            Left(InsufficientSupply(cost, player.board.supply.supply))
          result <- transition(state, Vector(SearchStarted(
            playerId, decision, source, origin, cost, drawn)),
            AwaitingSearchDecision(playerId, decision))
        } yield result
      }
    case SearchCommand.Complete(playerId, decision, kept, discarded, placement) =>
      validateSearchDecision(state, playerId, decision).flatMap { ready =>
        transition(state, Vector(SearchCompleted(
          playerId, decision, kept, discarded, placement)),
          ActActionSelection(playerId))
      }
  }

  override def evolve(
      state: FirstGameSetupState,
      event: FirstGameSetupEvent
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    event match {
      case event: WealthTaken => evolveWealth(state, event)
      case WakeEnded(playerId) =>
        validateReady(state, playerId).map { ready =>
          Ready(updateCurrent(ready)(current =>
            current.copy(turn = current.turn.copy(phase = Phase.Act))))
        }
      case event: Traveled => evolveTravel(state, event)
      case event: SearchStarted => evolveSearchStarted(state, event)
      case event: SearchCompleted => evolveSearchCompleted(state, event)
      case setupEvent => setup.evolve(state, setupEvent)
    }

  private def evolveSearchStarted(
      state: FirstGameSetupState,
      event: SearchStarted
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    validateAct(state, event.playerId).flatMap { ready =>
      val current = ready.game.current
      val player = current.players.find(_.player == event.playerId).get
      for {
        origin <- player.pawnSite.flatMap(current.map.regionOf)
          .toRight(PawnSiteMissing(event.playerId))
        _ <- if (origin == event.origin) Right(()) else
          Left(SearchDrawMismatch("recorded Search origin is not the pawn region"))
        cost <- SearchRules.cost(ready, event.source, origin)
        _ <- if (cost == event.supplySpent) Right(()) else
          Left(SearchCostMismatch(cost, event.supplySpent))
        drawn <- SearchRules.draw(ready, event.source, origin)
        _ <- if (drawn == event.drawn && drawn.nonEmpty) Right(()) else
          Left(SearchDrawMismatch("recorded cards do not match source order"))
        _ <- if (player.board.supply.supply >= cost) Right(()) else
          Left(InsufficientSupply(cost, player.board.supply.supply))
      } yield {
        val zones = SearchRules.removeDrawn(current.commonCards, event.source, drawn)
        val visions = if (event.source == SearchSource.WorldDeck &&
          drawn.exists(_.isInstanceOf[VisionId])) 1 else 0
        Ready(updateCurrent(ready) { existing =>
          existing.copy(
            players = existing.players.map { candidate =>
              if (candidate.player != event.playerId) candidate else
                candidate.copy(board = candidate.board.copy(
                  supply = SupplyTrack(candidate.board.supply.supply - cost)))
            },
            commonCards = zones,
            tracks = existing.tracks.copy(
              visionsDrawn = existing.tracks.visionsDrawn + visions),
            pending = Some(PendingProcedure.Search(
              event.decision, event.playerId, event.source, origin, cost, drawn))
          )
        })
      }
    }

  private def evolveSearchCompleted(
      state: FirstGameSetupState,
      event: SearchCompleted
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    validateSearchDecision(state, event.playerId, event.decision).flatMap { ready =>
      val pending = ready.game.current.pending.get
        .asInstanceOf[PendingProcedure.Search]
      SearchRules.complete(catalog, ready, pending, event).map(Ready(_))
    }

  private def evolveTravel(
      state: FirstGameSetupState,
      event: Traveled
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    validateAct(state, event.playerId).flatMap { ready =>
      val current = ready.game.current
      val player = current.players.find(_.player == event.playerId).get
      player.pawnSite.toRight(PawnSiteMissing(event.playerId)).flatMap { source =>
        if (source != event.sourceSiteId)
          Left(TravelSourceMismatch(source, event.sourceSiteId))
        else TravelRules.cost(
          catalog, ready, player, source, event.destinationSiteId
        ).flatMap { expected =>
          if (expected != event.supplySpent)
            Left(TravelCostMismatch(expected, event.supplySpent))
          else if (player.board.supply.supply < expected)
            Left(InsufficientSupply(expected, player.board.supply.supply))
          else Right(Ready(updateCurrent(ready) { existing =>
            existing.copy(players = existing.players.map { candidate =>
              if (candidate.player != event.playerId) candidate
              else candidate.copy(
                pawnSite = Some(event.destinationSiteId),
                board = candidate.board.copy(supply = SupplyTrack(
                  candidate.board.supply.supply - expected)))
            })
          }))
        }
      }
    }

  private def evolveWealth(
      state: FirstGameSetupState,
      event: WealthTaken
  ): Either[FirstGameSetupViolation, FirstGameSetupState] =
    validateReady(state, event.playerId).flatMap { ready =>
      val player = ready.game.current.players.find(
        _.player == event.playerId).get
      if (!player.pawnSite.contains(event.siteId))
        Left(InvalidEventOrder("Take Wealth site must be the current pawn site"))
      else {
        val power = takeWealthPower(event.siteId)
        ready.game.current.map.sites.get(event.siteId)
          .toRight(SiteNotInPlay(event.siteId)).flatMap { site =>
            TakeWealthRules.validate(ready, player, event.siteId, event.resource)
              .map(_ => Ready(updateCurrent(ready) { current =>
              val players = current.players.map { existing =>
                if (existing.player != event.playerId) existing
                else existing.copy(board = event.resource match {
                  case WakeResource.Favor =>
                    existing.board.copy(favor = existing.board.favor + 1)
                  case WakeResource.Secret =>
                    existing.board.copy(
                      faceUpSecrets = existing.board.faceUpSecrets + 1)
                })
              }
              val sites = current.map.sites.updated(
                event.siteId,
                site.copy(tokens = event.resource match {
                  case WakeResource.Favor =>
                    site.tokens.copy(favor = site.tokens.favor - 1)
                  case WakeResource.Secret =>
                    site.tokens.copy(secrets = site.tokens.secrets - 1)
                })
              )
              current.copy(
                players = players,
                map = current.map.copy(sites = sites),
                turn = current.turn.copy(
                  usedPowers = current.turn.usedPowers + power
                )
              )
            }))
          }
      }
    }

  private def validateReady(
      state: FirstGameSetupState,
      playerId: PlayerId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] =
    state match {
      case NoGame | _: InProgress => Left(GameNotStarted)
      case Ready(ready) =>
        val current = ready.game.current
        if (current.result.nonEmpty) Left(GameEnded)
        else if (current.turn.activePlayer != playerId)
          Left(WrongPlayer(current.turn.activePlayer, playerId))
        else if (current.turn.phase != Phase.Wake)
          Left(WrongPhase(Phase.Wake, current.turn.phase))
        else if (current.title.holder.nonEmpty)
          Left(UnsupportedWakeVictoryState("Oathkeeper or Usurper is held"))
        else if (current.players.exists(_.revealedVision.nonEmpty))
          Left(UnsupportedWakeVictoryState("a Vision is revealed"))
        else Right(ready)
    }

  private def validateAct(
      state: FirstGameSetupState,
      playerId: PlayerId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] = state match {
    case NoGame | _: InProgress => Left(GameNotStarted)
    case Ready(ready) =>
      val current = ready.game.current
      if (current.result.nonEmpty) Left(GameEnded)
      else if (current.turn.activePlayer != playerId)
        Left(WrongPlayer(current.turn.activePlayer, playerId))
      else if (current.turn.phase != Phase.Act)
        Left(WrongPhase(Phase.Act, current.turn.phase))
      else current.pending match {
        case Some(value) => Left(PendingProcedureBlocksAction(value.decision))
        case None => TravelRules.validateSupportedState(ready).map(_ => ready)
      }
  }

  private def validateSearchDecision(
      state: FirstGameSetupState,
      playerId: PlayerId,
      decision: DecisionId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] = state match {
    case Ready(ready) if ready.game.current.turn.activePlayer != playerId =>
      Left(WrongPlayer(ready.game.current.turn.activePlayer, playerId))
    case Ready(ready) if ready.game.current.turn.phase != Phase.Act =>
      Left(WrongPhase(Phase.Act, ready.game.current.turn.phase))
    case Ready(ready) => ready.game.current.pending match {
      case Some(value: PendingProcedure.Search) if value.actor != playerId =>
        Left(WrongPlayer(value.actor, playerId))
      case Some(value: PendingProcedure.Search) if value.decision != decision =>
        Left(SearchDecisionMismatch(value.decision, decision))
      case Some(_: PendingProcedure.Search) => Right(ready)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Search decision is pending"))
    }
    case _ => Left(GameNotStarted)
  }

  private def transition(
      state: FirstGameSetupState,
      events: Vector[FirstGameSetupEvent],
      continue: FirstGameContinue
  ): Either[FirstGameSetupViolation, FirstGameTransition] =
    events.foldLeft[Either[FirstGameSetupViolation, FirstGameSetupState]](
      Right(state))((next, event) => next.flatMap(evolve(_, event)))
      .map(FirstGameTransition(_, events, continue))

  private def updateCurrent(
      ready: ReadyFirstGame
  )(f: CurrentGameState => CurrentGameState): ReadyFirstGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))

  def takeWealthPower(siteId: SiteId): PowerUseRef =
    PowerUseRef(
      PowerTiming.Wake,
      PowerSourceRef.Site(siteId),
      PowerId("take-wealth")
    )
}

object TravelRules {
  import FirstGameSetupViolation._

  def legalDestinations(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState
  ): Vector[(SiteId, Int)] =
    player.pawnSite.toVector.flatMap(source =>
      ready.game.current.map.inPlay.flatMap(destination =>
        cost(catalog, ready, player, source, destination).toOption
          .filter(_ <= player.board.supply.supply)
          .map(destination -> _)))

  def cost(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId
  ): Either[FirstGameSetupViolation, Int] = {
    val map = ready.game.current.map
    for {
      _ <- validateSupportedState(ready)
      from <- map.regionOf(source).toRight(SiteNotInPlay(source))
      to <- map.regionOf(destination).toRight(SiteNotInPlay(destination))
      _ <- if (source == destination) Left(SameTravelSite(source)) else Right(())
      sourceDefinition <- catalog.sites.find(_.id == source)
        .toRight(SiteNotInPlay(source))
      destinationDefinition <- catalog.sites.find(_.id == destination)
        .toRight(SiteNotInPlay(destination))
      base = (from, to) match {
        case (Region.Cradle, Region.Cradle) => 1
        case (Region.Cradle, Region.Provinces) => 2
        case (Region.Cradle, Region.Hinterland) => 4
        case (Region.Provinces, _) => 2
        case (Region.Hinterland, Region.Cradle) => 4
        case (Region.Hinterland, Region.Provinces) => 2
        case (Region.Hinterland, Region.Hinterland) => 3
      }
      resolved <- resolveTravel(catalog, ready, player, source, destination,
        from, to, base, sourceDefinition.handlers, destinationDefinition.handlers)
    } yield resolved
  }

  def validateSupportedState(
      ready: ReadyFirstGame
  ): Either[FirstGameSetupViolation, Unit] = {
    val game = ready.game
    val current = game.current
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile))
        Some("Travel is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f =>
        f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Travel")
      else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active)))
        Some("active legacy Travel modifiers are not supported")
      else if (current.players.exists(_.advisers.exists {
        case DenizenState(_, Orientation.FaceUp, _) => true
        case _ => false
      })) Some("face-up adviser Travel modifiers are not supported")
      else if (current.players.exists(_.relics.nonEmpty))
        Some("held relic Travel modifiers are not supported")
      else if (current.map.sites.values.exists(_.denizens.exists {
        case EdificeState(_, EdificeSide.Intact, _) => true
        case _ => false
      })) Some("intact edifice Travel modifiers are not supported")
      else None
    reason match {
      case Some(value) => Left(UnsupportedTravelState(value))
      case None => Right(())
    }
  }

  private def resolveTravel(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId,
      from: Region,
      to: Region,
      baseCost: Int,
      sourceHandlers: Vector[String],
      destinationHandlers: Vector[String]
  ): Either[FirstGameSetupViolation, Int] = {
    val registry = RuntimeRuleRegistry.default
    def handlersWithRole(
        handlers: Vector[String],
        roles: Set[TravelModifierKind]
    ): Vector[String] = handlers.filter(id =>
      registry.lookup(id).flatMap(_.travelModifierKind).exists(roles))
    def activations(
        id: SiteId,
        handlers: Vector[String],
        roles: Set[TravelModifierKind],
        priority: Int
    ) = handlersWithRole(handlers, roles).map(RuleActivation(
      RuleSourceRef.Site(id), _, priority))
    val coastRoute = handlersWithRole(sourceHandlers,
      Set(TravelModifierKind.Coast)).nonEmpty && handlersWithRole(
      destinationHandlers, Set(TravelModifierKind.Coast,
        TravelModifierKind.Island)
    ).nonEmpty
    val passActivations = if (coastRoute || from == to) Vector.empty else
      ready.game.current.map.inPlay.flatMap { id =>
        if (ready.game.current.map.regionOf(id).contains(to) && id != destination)
          catalog.sites.find(_.id == id).toVector.flatMap(definition =>
            activations(id, definition.handlers,
              Set(TravelModifierKind.Pass), 10))
        else Vector.empty
      }
    val active = if (coastRoute)
      activations(source, sourceHandlers, Set(TravelModifierKind.Coast), 0)
    else passActivations ++
      activations(destination, destinationHandlers,
        Set(TravelModifierKind.Island, TravelModifierKind.Mountain), 20)
    val context = RuleQueryContext.Travel(
      ready, player, source, destination, from, to, baseCost)
    registry.resolve(active, context).foldLeft[
      Either[FirstGameSetupViolation, Int]](Right(baseCost)) {
      case (failure @ Left(_), _) => failure
      case (Right(cost), ResolvedRule(_, RuleOutcome.Allow)) => Right(cost)
      case (_, ResolvedRule(_, RuleOutcome.Block(value))) => Left(value)
      case (Right(cost), ResolvedRule(_, RuleOutcome.ModifyCost(value, false))) =>
        Right(cost + value)
      case (_, ResolvedRule(_, RuleOutcome.ModifyCost(value, true))) => Right(value)
      case (_, ResolvedRule(_, RuleOutcome.RequireDecision(decision))) =>
        Left(UnsupportedTravelState(s"decision ${decision.decision.value} required"))
      case (Right(cost), ResolvedRule(_, RuleOutcome.PostActionEffect(_))) =>
        Right(cost)
      case (_, ResolvedRule(_, RuleOutcome.UnsupportedRelevantRule(handler))) =>
        Left(UnsupportedTravelState(s"unsupported active handler $handler"))
    }
  }
}

object SearchRules {
  import FirstGameSetupViolation._
  import oathdigital.catalog.CardRestrictions

  def validateSupportedState(
      ready: ReadyFirstGame
  ): Either[FirstGameSetupViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile))
        Some("Search is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f =>
        f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Search")
      else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active)))
        Some("active legacy Search modifiers are not supported")
      else if (game.current.players.exists(_.relics.nonEmpty))
        Some("held relic Search modifiers are not supported")
      else None
    reason.fold[Either[FirstGameSetupViolation, Unit]](Right(()))(
      value => Left(UnsupportedSearchState(value)))
  }

  def cost(
      ready: ReadyFirstGame,
      source: SearchSource,
      origin: Region
  ): Either[FirstGameSetupViolation, Int] = source match {
    case SearchSource.WorldDeck =>
      Right(math.min(4, 2 + ready.game.current.tracks.visionsDrawn))
    case SearchSource.RegionalDiscard(region) if region == origin => Right(2)
    case SearchSource.RegionalDiscard(_) => Left(SearchSourceUnavailable(source))
  }

  /** World decks use head-as-top; discard piles use last-as-top. */
  def draw(
      ready: ReadyFirstGame,
      source: SearchSource,
      origin: Region
  ): Either[FirstGameSetupViolation, Vector[WorldCardId]] =
    cost(ready, source, origin).map { _ => source match {
      case SearchSource.WorldDeck =>
        ready.game.current.commonCards.worldDeck.take(3)
          .takeThrough(_.isInstanceOf[VisionId])
      case SearchSource.RegionalDiscard(region) =>
        ready.game.current.commonCards.discard(region).reverse.take(3)
    }}

  def removeDrawn(
      zones: CardZones,
      source: SearchSource,
      drawn: Vector[WorldCardId]
  ): CardZones = source match {
    case SearchSource.WorldDeck => zones.copy(worldDeck = zones.worldDeck.drop(drawn.size))
    case SearchSource.RegionalDiscard(region) => zones.copy(
      regionalDiscards = zones.regionalDiscards.updated(
        region, zones.discard(region).dropRight(drawn.size)))
  }

  def complete(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      pending: PendingProcedure.Search,
      event: SearchCompleted
  ): Either[FirstGameSetupViolation, ReadyFirstGame] = {
    val drawn = pending.drawn
    val expectedDiscards = drawn.filterNot(_ == event.kept)
    for {
      _ <- if (drawn.count(_ == event.kept) == 1) Right(()) else
        Left(SearchChoiceMismatch("kept card must be one of the drawn cards"))
      _ <- if (event.discardedInOrder.size == expectedDiscards.size &&
          event.discardedInOrder.toSet == expectedDiscards.toSet) Right(()) else
        Left(SearchChoiceMismatch(
          "discard order must contain every non-kept drawn card exactly once"))
      placementResult <- place(catalog, ready, pending, event.kept, event.placement)
    } yield {
      val updated = placementResult.ready
      val destination = nextRegion(pending.origin)
      val extra = event.placement match {
        case SearchPlacement.Discard => Vector(event.kept)
        case _ => Vector.empty
      }
      val current = updated.game.current
      updated.copy(game = updated.game.copy(current = current.copy(
        commonCards = current.commonCards.copy(
          regionalDiscards = current.commonCards.regionalDiscards.updated(
            destination,
            current.commonCards.discard(destination) ++
              event.discardedInOrder ++ placementResult.discardedWorld ++ extra),
          edificeDeck = current.commonCards.edificeDeck ++
            placementResult.discardedEdifices),
        pending = None
      )))
    }
  }

  private def place(
      catalog: ExecutableCatalog,
      ready: ReadyFirstGame,
      pending: PendingProcedure.Search,
      kept: WorldCardId,
      placement: SearchPlacement
  ): Either[FirstGameSetupViolation, SearchPlacementResult] = {
    val current = ready.game.current
    val player = current.players.find(_.player == pending.actor).get
    placement match {
      case SearchPlacement.Discard => Right(SearchPlacementResult(ready))
      case SearchPlacement.Site(replace) => kept match {
        case _: VisionId => Left(InvalidSearchPlacement("Visions cannot be played to sites"))
        case id: DenizenId =>
          catalog.denizens.find(_.id.value == id.value).toRight(UnknownWorldCard(id))
            .flatMap { definition =>
              if (definition.restrictions == CardRestrictions.AdviserOnly ||
                  definition.restrictions == CardRestrictions.LockedAdviserOnly)
                Left(InvalidSearchPlacement("adviser-only card cannot be played to a site"))
              else player.pawnSite.flatMap(current.map.sites.get)
                .toRight(PawnSiteMissing(player.player)).flatMap { site =>
                  val siteId = player.pawnSite.get
                  validateSiteReplacement(catalog, current, siteId, site, definition.suit.value,
                    replace).map { case (remaining, removed) =>
                    val modelSuit = Suit.all.find(_.key == definition.suit.value).get
                    val gain = math.min(1, ready.support.favorBanks.getOrElse(modelSuit, 0))
                    val updatedSite = site.copy(denizens = remaining :+
                      DenizenState(id, Orientation.FaceUp, Tokens.empty))
                    val next = ready.copy(
                      game = ready.game.copy(current = current.copy(
                        map = current.map.copy(sites = current.map.sites.updated(siteId, updatedSite)),
                        players = current.players.map(p => if (p.player != player.player) p else
                          p.copy(board = p.board.copy(favor = p.board.favor + gain))))),
                      support = ready.support.copy(favorBanks =
                        ready.support.favorBanks.updated(modelSuit,
                          ready.support.favorBanks.getOrElse(modelSuit, 0) - gain)))
                    removed match {
                      case Some(id: DenizenId) => SearchPlacementResult(next,
                        discardedWorld = Vector(id))
                      case Some(id: EdificeId) => SearchPlacementResult(next,
                        discardedEdifices = Vector(id))
                      case _ => SearchPlacementResult(next)
                    }
                  }
                }
            }
      }
      case SearchPlacement.Adviser(orientation, replace) =>
        validateAdviserReplacement(catalog, player, replace).flatMap {
          case (advisers, removed) =>
          kept match {
            case id: DenizenId =>
              catalog.denizens.find(_.id.value == id.value).toRight(UnknownWorldCard(id))
                .flatMap { definition =>
                  if (orientation == Orientation.FaceUp &&
                      definition.restrictions == CardRestrictions.SiteOnly)
                    Left(InvalidSearchPlacement(
                      "site-only card can only be held as a facedown adviser"))
                  else {
                    val nextPlayers = current.players.map { candidate =>
                      if (candidate.player != player.player) candidate
                      else candidate.copy(advisers = advisers :+
                        DenizenState(id, orientation, Tokens.empty))
                    }
                    val next = ready.copy(game = ready.game.copy(current =
                      current.copy(players = nextPlayers)))
                    Right(SearchPlacementResult(next, removed.toVector))
                  }
                }
            case id: VisionId =>
              if (!FirstGameRulesData.visions.contains(id)) Left(UnknownWorldCard(id))
              else {
                val nextPlayers = current.players.map { candidate =>
                  if (candidate.player != player.player) candidate
                  else if (orientation == Orientation.FaceUp)
                    candidate.copy(advisers = advisers, revealedVision =
                      Some(VisionState(id, Orientation.FaceUp)))
                  else candidate.copy(advisers = advisers :+
                    VisionState(id, Orientation.FaceDown))
                }
                val next = ready.copy(game = ready.game.copy(current =
                  current.copy(players = nextPlayers)))
                Right(SearchPlacementResult(next, removed.toVector))
              }
          }
        }
    }
  }

  private def validateAdviserReplacement(
      catalog: ExecutableCatalog,
      player: PlayerState,
      replace: Option[CardId]
  ): Either[FirstGameSetupViolation, (Vector[AdviserState], Option[WorldCardId])] = {
    val mustReplace = player.advisers.size >= 3
    if (mustReplace != replace.nonEmpty)
      Left(InvalidSearchPlacement(
        if (mustReplace) "a full adviser area requires a discard"
        else "an adviser cannot be discarded when there is free capacity"))
    else replace match {
      case None => Right(player.advisers -> None)
      case Some(id) => player.advisers.find(_.id == id).toRight(
        InvalidSearchPlacement("replacement adviser is not held"))
        .flatMap { _ =>
          val locked = id match {
            case d: DenizenId => catalog.denizens.find(_.id.value == d.value)
              .exists(_.restrictions == CardRestrictions.LockedAdviserOnly)
            case _ => false
          }
          if (locked) Left(LockedAdviserCannotBeDiscarded(id))
          else Right(player.advisers.filterNot(_.id == id) ->
            Some(id.asInstanceOf[WorldCardId]))
        }
    }
  }

  private def validateSiteReplacement(
      catalog: ExecutableCatalog,
      current: CurrentGameState,
      siteId: SiteId,
      site: SiteState,
      playedSuit: String,
      replace: Option[CardId]
  ): Either[FirstGameSetupViolation, (Vector[SiteDenizenState], Option[CardId])] = {
    val capacity = catalog.sites.find(_.id == siteId).map(_.capacity).getOrElse(0)
    val full = site.denizens.size >= capacity
    if (!full && replace.nonEmpty)
      Left(InvalidSearchPlacement("site replacement is allowed only at a full Homeland"))
    else if (!full) Right(site.denizens -> None)
    else {
      val homelandMatches = site.denizens.collectFirst {
        case e: EdificeState => catalog.edifices.find(_.id.value == e.id.value)
          .exists(_.suit.value == playedSuit)
      }.contains(true)
      if (!homelandMatches)
        Left(InvalidSearchPlacement("full non-matching site cannot accept a denizen"))
      else replace.flatMap(id => site.denizens.find(_.id == id)).toRight(
        InvalidSearchPlacement("full matching Homeland requires a site-card discard"))
        .map(_ => site.denizens.filterNot(card => replace.contains(card.id)) -> replace)
    }
  }

  private final case class SearchPlacementResult(
      ready: ReadyFirstGame,
      discardedWorld: Vector[WorldCardId] = Vector.empty,
      discardedEdifices: Vector[EdificeId] = Vector.empty
  )

  private def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }

  private implicit final class TakeThrough[A](private val values: Vector[A])
      extends AnyVal {
    def takeThrough(stop: A => Boolean): Vector[A] = {
      val index = values.indexWhere(stop)
      if (index < 0) values else values.take(index + 1)
    }
  }
}
