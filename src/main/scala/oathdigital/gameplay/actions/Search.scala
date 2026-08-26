package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import GameStateUpdates.updateCurrent

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


object Search {
  def handle(
      catalog: ExecutableCatalog,
      state: OathState,
      command: SearchCommand
  ): Either[OathViolation, OathTransition] = command match {
    case SearchCommand.Start(playerId, decision, source, drawn) =>
      OathLifecycle.validateAct(state, playerId).flatMap { ready =>
        val player = ready.game.current.players.find(_.player == playerId).get
        for {
          origin <- player.pawnSite.flatMap(ready.game.current.map.regionOf)
            .toRight(PawnSiteMissing(playerId))
          cost <- SearchRules.cost(ready, source, origin)
          _ <- SearchRules.validateSupportedState(catalog, ready)
          expected <- SearchRules.draw(ready, source, origin)
          _ <- if (drawn == expected) Right(()) else
            Left(SearchDrawMismatch("prepared draw does not match authoritative source order"))
          _ <- if (drawn.nonEmpty) Right(()) else Left(SearchSourceUnavailable(source))
          _ <- if (player.board.supply.supply >= cost) Right(()) else
            Left(InsufficientSupply(cost, player.board.supply.supply))
          result <- transition(catalog, state, Vector(SearchStarted(
            playerId, decision, source, origin, cost, drawn)),
            AwaitingSearchDecision(playerId, decision))
        } yield result
      }
    case SearchCommand.Complete(playerId, decision, kept, discarded, placement) =>
      OathLifecycle.validateSearchDecision(state, playerId, decision).flatMap { ready =>
        transition(catalog, state, Vector(SearchCompleted(
          playerId, decision, kept, discarded, placement)),
          ActActionSelection(playerId))
      }
  }

  private def evolveSearchStarted(
      state: OathState,
      event: SearchStarted
  ): Either[OathViolation, OathState] =
    OathLifecycle.validateAct(state, event.playerId).flatMap { ready =>
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
      catalog: ExecutableCatalog,
      state: OathState,
      event: SearchCompleted
  ): Either[OathViolation, OathState] =
    OathLifecycle.validateSearchDecision(state, event.playerId, event.decision).flatMap { ready =>
      val pending = ready.game.current.pending.get
        .asInstanceOf[PendingProcedure.Search]
      SearchRules.complete(catalog, ready, pending, event).map(Ready(_))
    }

  def evolve(
      catalog: ExecutableCatalog,
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] = event match {
    case started: SearchStarted => evolveSearchStarted(state, started)
    case completed: SearchCompleted => evolveSearchCompleted(catalog, state, completed)
    case _ => Left(InvalidEventOrder("Search received a non-Search event"))
  }

  private def transition(
      catalog: ExecutableCatalog,
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  ): Either[OathViolation, OathTransition] =
    GameplayTransition(state, events, continue)(evolve(catalog, _, _))
}

object SearchRules {
  import OathViolation._
  import oathdigital.catalog.CardRestrictions

  def validateSupportedState(
      catalog: ExecutableCatalog,
      ready: ReadyGame
  ): Either[OathViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile))
        Some("Search is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f =>
        f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Search")
      else None
    reason.fold[Either[OathViolation, Unit]](
      MajorActionPowerShell.requireAudited(catalog))(
      value => Left(UnsupportedSearchState(value)))
  }

  def cost(
      ready: ReadyGame,
      source: SearchSource,
      origin: Region
  ): Either[OathViolation, Int] = source match {
    case SearchSource.WorldDeck =>
      Right(math.min(4, 2 + ready.game.current.tracks.visionsDrawn))
    case SearchSource.RegionalDiscard(region) if region == origin => Right(2)
    case SearchSource.RegionalDiscard(_) => Left(SearchSourceUnavailable(source))
  }

  /** World decks use head-as-top; discard piles use last-as-top. */
  def draw(
      ready: ReadyGame,
      source: SearchSource,
      origin: Region
  ): Either[OathViolation, Vector[WorldCardId]] =
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
      ready: ReadyGame,
      pending: PendingProcedure.Search,
      event: SearchCompleted
  ): Either[OathViolation, ReadyGame] = {
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
      val conspiracy = event.kept == VisionRules.Conspiracy && event.placement ==
        SearchPlacement.Adviser(Orientation.FaceUp, None)
      updated.copy(game = updated.game.copy(current = current.copy(
        commonCards = current.commonCards.copy(
          regionalDiscards = current.commonCards.regionalDiscards.updated(
            destination,
            current.commonCards.discard(destination) ++
              event.discardedInOrder ++ placementResult.discardedWorld ++ extra),
          edificeDeck = current.commonCards.edificeDeck ++
            placementResult.discardedEdifices),
        pending = Option.when(conspiracy)(PendingProcedure.Conspiracy(
          event.decision, event.playerId, VisionRules.Conspiracy, None, 0,
          awaitingTarget = true))
      )))
    }
  }

  /**
   * Enumerates UI outcomes by running the same completion validator used by
   * command handling and replay. No presentation-only legality is maintained.
   */
  def legalPlacements(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      pending: PendingProcedure.Search,
      kept: WorldCardId
  ): Vector[SearchPlacement] = {
    val player = ready.game.current.players.find(_.player == pending.actor).get
    val siteCards = player.pawnSite.toVector
      .flatMap(ready.game.current.map.sites.get).flatMap(_.denizens.map(_.id))
    val adviserCards = player.advisers.map(_.id)
    val candidates = Vector[SearchPlacement](SearchPlacement.Discard) ++
      (Vector(None) ++ siteCards.map(Some(_))).map(SearchPlacement.Site) ++
      Vector(Orientation.FaceDown, Orientation.FaceUp).flatMap { orientation =>
        (Vector(None) ++ adviserCards.map(Some(_))).map(
          SearchPlacement.Adviser(orientation, _))
      }
    val discarded = pending.drawn.filterNot(_ == kept)
    candidates.distinct.filter { placement =>
      complete(catalog, ready, pending, SearchCompleted(
        pending.actor, pending.decision, kept, discarded, placement)).isRight
    }
  }

  private def place(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      pending: PendingProcedure.Search,
      kept: WorldCardId,
      placement: SearchPlacement
  ): Either[OathViolation, SearchPlacementResult] = {
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
        (kept match {
          case id: VisionId if orientation == Orientation.FaceUp =>
            MinorActionPowerSupport.validateFaceupVision(
              catalog, ready, player.player, id).flatMap { _ =>
              val expected = if (id == VisionRules.Conspiracy) None
                else player.revealedVision.map(_.id)
              if (replace != expected) Left(InvalidSearchPlacement(
                if (expected.nonEmpty) "a revealed Vision must be replaced"
                else "there is no revealed Vision to replace"))
              else Right(player.advisers -> expected.map(identity[WorldCardId]))
            }
          case _ => validateAdviserReplacement(catalog, player, replace)
        }).flatMap {
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
              else if (id == VisionRules.Conspiracy && orientation == Orientation.FaceUp &&
                  replace.nonEmpty) Left(InvalidSearchPlacement(
                "Conspiracy does not replace a revealed Vision"))
              else {
                val nextPlayers = current.players.map { candidate =>
                  if (candidate.player != player.player) candidate
                  else if (orientation == Orientation.FaceUp && id == VisionRules.Conspiracy)
                    candidate.copy(advisers = advisers)
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
  ): Either[OathViolation, (Vector[AdviserState], Option[WorldCardId])] = {
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
  ): Either[OathViolation, (Vector[SiteDenizenState], Option[CardId])] = {
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
      ready: ReadyGame,
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
