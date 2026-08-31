package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
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
        val pending = ready.game.current.pending.get
          .asInstanceOf[PendingProcedure.Search]
        SearchRules.prepareComplete(catalog, ready, pending, playerId, decision,
          kept, discarded, placement).flatMap { outcome =>
          transition(catalog, state, Vector(SearchCompleted(playerId, decision,
            kept, discarded, placement, outcome.favorGained,
            outcome.discardedWorld, outcome.discardedEdifices)),
            ActActionSelection(playerId))
        }
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
      PowerRuntime.requireAudited(catalog))(
      value => Left(UnsupportedSearchState(value)))
  }

  def cost(
      ready: ReadyGame,
      source: SearchSource,
      origin: Region
  ): Either[OathViolation, Int] = source match {
    case SearchSource.WorldDeck =>
      Right(ready.game.current.tracks.visionsDrawn match {
        case 0 => 2
        case 1 | 2 => 3
        case _ => 4
      })
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
    prepareComplete(catalog, ready, pending, event.playerId, event.decision,
      event.kept, event.discardedInOrder, event.placement).flatMap { outcome =>
      val recorded = (event.favorGained, event.discardedWorld,
        event.discardedEdifices)
      val expected = (outcome.favorGained, outcome.discardedWorld,
        outcome.discardedEdifices)
      Either.cond(recorded == expected, outcome.ready,
        SearchChoiceMismatch("recorded card-play effects do not match placement"))
    }
  }

  def prepareComplete(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      pending: PendingProcedure.Search,
      playerId: PlayerId,
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ): Either[OathViolation, CardPlay.Outcome] = {
    val drawn = pending.drawn
    val expectedDiscards = drawn.filterNot(_ == kept)
    for {
      _ <- if (drawn.count(_ == kept) == 1) Right(()) else
        Left(SearchChoiceMismatch("kept card must be one of the drawn cards"))
      _ <- if (discardedInOrder.size == expectedDiscards.size &&
          discardedInOrder.toSet == expectedDiscards.toSet) Right(()) else
        Left(SearchChoiceMismatch(
          "discard order must contain every non-kept drawn card exactly once"))
      outcome <- CardPlay.resolve(catalog, ready, playerId, kept, placement,
        CardPlay.Origin.Search(decision), discardedInOrder)
    } yield outcome
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
      prepareComplete(catalog, ready, pending, pending.actor, pending.decision,
        kept, discarded, placement).isRight
    }
  }

  private implicit final class TakeThrough[A](private val values: Vector[A])
      extends AnyVal {
    def takeThrough(stop: A => Boolean): Vector[A] = {
      val index = values.indexWhere(stop)
      if (index < 0) values else values.take(index + 1)
    }
  }
}
