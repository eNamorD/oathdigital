package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle, StateBasedEvaluation}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

sealed trait RestCommand extends Product with Serializable
object RestCommand {
  final case class Begin(playerId: PlayerId) extends RestCommand
  final case class Finish(playerId: PlayerId) extends RestCommand
}

trait WarExhaustionRandomPort {
  def choose(candidates: Vector[PlayerId]): PlayerId
}
object WarExhaustionRandomPort {
  val random: WarExhaustionRandomPort = new WarExhaustionRandomPort {
    private val rng = new scala.util.Random()
    def choose(candidates: Vector[PlayerId]): PlayerId =
      candidates(rng.nextInt(candidates.size))
  }
}

object Rest {
  private val ExpectedHandlerInventory =
    "5fc88b0d9622a3f523722c288ea7a78d0ec09b7ce191bdabc7f471139ec85898"
  private val ExileWarbands = 14
  private val ExileSupply = SupplyRules(
    SupplyTrack.Maximum,
    Vector(
      SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), 6),
      SupplyRefreshBand(InclusiveIntRange(4, 8), 5),
      SupplyRefreshBand(InclusiveIntRange(0, 3), 4)
    )
  )

  def handle(catalog: ExecutableCatalog, state: OathState, command: RestCommand,
      randomPort: WarExhaustionRandomPort = WarExhaustionRandomPort.random)
      : Either[OathViolation, OathTransition] = command match {
    case RestCommand.Begin(playerId) =>
      validateBegin(catalog, state, playerId).flatMap(_ =>
        transition(catalog, state, Vector(RestStarted(playerId)),
          AwaitingRestAction(playerId)))
    case RestCommand.Finish(playerId) =>
      validateRest(catalog, state, playerId).flatMap { ready =>
        expected(catalog, ready, playerId).flatMap { event =>
          transition(catalog, state, Vector(event),
            AwaitingWakeAction(event.postRestActivePlayerId)).flatMap { rested =>
            if (playerId != turnOrder(ready).last) Right(rested)
            else finishRound(catalog, rested, randomPort)
          }
        }
      }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] = event match {
    case RestStarted(playerId) =>
      validateBegin(catalog, state, playerId).map { ready =>
        Ready(GameStateUpdates.updateCurrent(ready)(current =>
          current.copy(turn = current.turn.copy(phase = Phase.Rest))))
      }
    case recorded: RestCompleted =>
      validateRest(catalog, state, recorded.playerId).flatMap { ready =>
        expected(catalog, ready, recorded.playerId).flatMap { wanted =>
          if (wanted != recorded)
            Left(RestOutcomeMismatch(s"expected $wanted but recorded $recorded"))
          else RestCleanupPlan.derive(catalog, ready, recorded.playerId)
            .left.map(UnsupportedRestState).map(_ =>
              Ready(applyCompletion(ready, recorded)))
        }
      }
    case _ => Left(InvalidEventOrder("Rest received a non-Rest event"))
  }

  /** Single legality path for command handling, replay, and projection. */
  def validateBegin(catalog: ExecutableCatalog, state: OathState, playerId: PlayerId)
      : Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, playerId).flatMap { ready =>
      validateSupportedState(catalog, ready).map(_ => ready)
    }

  private def validateRest(catalog: ExecutableCatalog,
      state: OathState, playerId: PlayerId)
      : Either[OathViolation, ReadyGame] = state match {
    case Ready(ready) =>
      val current = ready.game.current
      if (current.result.nonEmpty) Left(GameEnded)
      else if (current.turn.activePlayer != playerId)
        Left(WrongPlayer(current.turn.activePlayer, playerId))
      else if (current.turn.phase != Phase.Rest)
        Left(WrongPhase(Phase.Rest, current.turn.phase))
      else if (current.pending.nonEmpty)
        Left(PendingProcedureBlocksAction(current.pending.get.decision))
      else validateSupportedState(catalog, ready).map(_ => ready)
    case _ => Left(GameNotStarted)
  }

  def validateSupportedState(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] = {
    validateAllExileAndRules(catalog, ready)
  }

  private def validateAllExileAndRules(catalog: ExecutableCatalog,
      ready: ReadyGame): Either[OathViolation, Unit] = {
    val game = ready.game
    val actualHandlerInventory = CatalogHandlerInventory.structuralFingerprint(catalog)
    if (game.campaign.lineages.values.exists(_.role != Role.Exile))
      Left(UnsupportedRestState("Rest is limited to the exile-only first game"))
    else if (actualHandlerInventory != ExpectedHandlerInventory)
      Left(UnsupportedRoundEndCatalogInventory(ExpectedHandlerInventory,
        actualHandlerInventory))
    else Right(())
  }

  private def expected(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Either[OathViolation, RestCompleted] = {
    val current = ready.game.current
    val player = current.players.find(_.player == playerId).get
    val lineage = player.lineage
    val ruledSites = current.map.sites.collect {
      case (id, site) if (site.forces match {
        case SiteForces.Occupied(ForceKind.Exile(owner), _) => owner == lineage
        case _ => false
      }) => id -> site
    }
    val siteWarbands = ruledSites.valuesIterator.map(_.forces).collect {
      case SiteForces.Occupied(ForceKind.Exile(owner), count)
          if owner == lineage => count
    }.sum
    val banked = math.max(0, ExileWarbands - player.board.warbands - siteWarbands)
    val refreshed = ExileSupply.refresh(banked, player.board.supply.supply)
      .toRight(UnsupportedRestState(s"no Supply band for $banked banked warbands"))
    for {
      supply <- refreshed
      cleanup <- RestCleanupPlan.derive(catalog, ready, playerId)
        .left.map(UnsupportedRestState)
    } yield {
      val order = turnOrder(ready)
      val index = order.indexOf(playerId)
      val last = index == order.size - 1
      RestCompleted(playerId, cleanup.returnedFavor, cleanup.returnedSecrets,
        supply.supply,
        if (last) order.head else order(index + 1),
        current.tracks.round,
        current.tracks.usurperLimited)
    }
  }

  private def applyCompletion(ready: ReadyGame, event: RestCompleted): ReadyGame = {
    val current = ready.game.current
    def clear(card: SiteDenizenState): SiteDenizenState = card match {
      case value: DenizenState => value.copy(tokens = Tokens.empty)
      case value: EdificeState => value.copy(tokens = Tokens.empty)
    }
    val players = current.players.map { candidate =>
      val board = if (candidate.player == event.playerId)
        candidate.board.copy(
          faceUpSecrets = candidate.board.faceUpSecrets +
            candidate.board.faceDownSecrets + event.returnedSecrets,
          faceDownSecrets = 0,
          supply = SupplyTrack(event.refreshedSupply))
      else candidate.board
      candidate.copy(
        board = board,
        advisers = candidate.advisers.map {
          case value: DenizenState =>
            value.copy(tokens = Tokens.empty)
          case other => other
        },
        relics = candidate.relics.map(relic =>
          relic.copy(tokens = Tokens(relic.tokens.favor, 0))))
    }
    val sites = current.map.sites.map { case (id, site) =>
      id -> site.copy(
        denizens = site.denizens.map(clear),
        relics = site.relics.map(relic =>
          relic.copy(tokens = Tokens(relic.tokens.favor, 0))))
    }
    val support = ready.support.copy(favorBanks = event.returnedFavor.foldLeft(
      ready.support.favorBanks) { case (banks, (suit, amount)) =>
        banks.updated(suit, banks.getOrElse(suit, 0) + amount)
    })
    ready.copy(
      support = support,
      game = ready.game.copy(current = current.copy(
        players = players,
        map = current.map.copy(sites = sites),
        tracks = current.tracks.copy(usurperLimited = event.usurperLimited),
        turn = TurnState(event.postRestActivePlayerId,
          if (turnOrder(ready).last == event.playerId) Phase.RoundEnd else Phase.Wake,
          Set.empty),
        pending = None)))
  }

  private def turnOrder(ready: ReadyGame): Vector[PlayerId] = {
    val participants = ready.game.current.players.map(_.player)
    val index = participants.indexOf(ready.support.firstPlayer)
    participants.drop(index) ++ participants.take(index)
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue)
      : Either[OathViolation, OathTransition] =
    GameplayTransition(state, events, continue)(evolve(catalog, _, _))

  private def finishRound(catalog: ExecutableCatalog, transition: OathTransition,
      randomPort: WarExhaustionRandomPort)
      : Either[OathViolation, OathTransition] = {
    val choose: (Vector[PlayerId] => PlayerId) = randomPort.choose
    StateBasedEvaluation.endRound(transition.state, choose).flatMap { events =>
      events.foldLeft[Either[OathViolation, OathTransition]](Right(transition)) {
        case (Right(current), event) =>
          StateBasedEvaluation.evolve(catalog, current.state, event).map { next =>
            val continuation = next match {
              case Ready(ready) if ready.game.current.result.nonEmpty =>
                GameFinished(ready.game.current.result.get.winner)
              case _ => current.continue
            }
            current.copy(state = next, events = current.events :+ event,
              continue = continuation)
          }
        case (failure @ Left(_), _) => failure
      }
    }
  }
}
