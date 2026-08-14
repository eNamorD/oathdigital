package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathContinue._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

sealed trait RestCommand extends Product with Serializable
object RestCommand {
  final case class Begin(playerId: PlayerId) extends RestCommand
  final case class Finish(playerId: PlayerId) extends RestCommand
}

object Rest {
  private val ExileWarbands = 14
  private val ExileSupply = SupplyRules(
    SupplyTrack.Maximum,
    Vector(
      SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), 6),
      SupplyRefreshBand(InclusiveIntRange(4, 8), 5),
      SupplyRefreshBand(InclusiveIntRange(0, 3), 4)
    )
  )

  def handle(catalog: ExecutableCatalog, state: OathState, command: RestCommand)
      : Either[OathViolation, OathTransition] = command match {
    case RestCommand.Begin(playerId) =>
      validateBegin(state, playerId).flatMap(_ =>
        transition(catalog, state, Vector(RestStarted(playerId)),
          AwaitingRestAction(playerId)))
    case RestCommand.Finish(playerId) =>
      validateRest(state, playerId).flatMap { ready =>
        expected(catalog, ready, playerId).flatMap { event =>
          transition(catalog, state, Vector(event),
            AwaitingWakeAction(event.nextPlayerId))
        }
      }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] = event match {
    case RestStarted(playerId) =>
      validateBegin(state, playerId).map { ready =>
        Ready(GameStateUpdates.updateCurrent(ready)(current =>
          current.copy(turn = current.turn.copy(phase = Phase.Rest))))
      }
    case recorded: RestCompleted =>
      validateRest(state, recorded.playerId).flatMap { ready =>
        expected(catalog, ready, recorded.playerId).flatMap { wanted =>
          if (wanted != recorded)
            Left(RestOutcomeMismatch(s"expected $wanted but recorded $recorded"))
          else Right(Ready(applyCompletion(ready, recorded)))
        }
      }
    case _ => Left(InvalidEventOrder("Rest received a non-Rest event"))
  }

  /** Single legality path for command handling, replay, and projection. */
  def validateBegin(state: OathState, playerId: PlayerId)
      : Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, playerId).flatMap { ready =>
      validateSupportedState(ready).map(_ => ready)
    }

  private def validateRest(state: OathState, playerId: PlayerId)
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
      else validateSupportedState(ready).map(_ => ready)
    case _ => Left(GameNotStarted)
  }

  def validateSupportedState(ready: ReadyGame): Either[OathViolation, Unit] = {
    val game = ready.game
    if (ready.support.foundationProfile != FirstGameFoundationProfile.FixedUnaltered)
      Left(UnsupportedRestState("altered Foundations are not supported for Rest"))
    else if (game.campaign.lineages.values.exists(_.role != Role.Exile))
      Left(UnsupportedRestState("Rest is limited to the exile-only first game"))
    else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active)))
      Left(UnsupportedRestState("active legacy Rest powers are not supported"))
    else if (game.current.tracks.round >= 8 &&
        game.current.turn.activePlayer == turnOrder(ready).last)
      Left(UnsupportedRestState(
        "round-eight ending resolution is not supported by bounded Rest"))
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
    val denizens = player.advisers.collect { case value: DenizenState => value } ++
      ruledSites.valuesIterator.flatMap(_.denizens).toVector
    val favor = denizens.foldLeft(Map.empty[Suit, Int].withDefaultValue(0)) {
      case (acc, card) => suitOf(catalog, card.id).fold(acc)(suit =>
        acc.updated(suit, acc(suit) + card.tokens.favor))
    }.filter(_._2 > 0)
    val secrets = denizens.map(_.tokens.secrets).sum +
      player.relics.map(_.tokens.secrets).sum
    val siteWarbands = ruledSites.valuesIterator.map(_.forces).collect {
      case SiteForces.Occupied(ForceKind.Exile(owner), count)
          if owner == lineage => count
    }.sum
    val banked = math.max(0, ExileWarbands - player.board.warbands - siteWarbands)
    val refreshed = ExileSupply.refresh(banked, player.board.supply.supply)
      .toRight(UnsupportedRestState(s"no Supply band for $banked banked warbands"))
    refreshed.map { supply =>
      val order = turnOrder(ready)
      val index = order.indexOf(playerId)
      val last = index == order.size - 1
      RestCompleted(playerId, favor, secrets, supply.supply,
        if (last) order.head else order(index + 1),
        current.tracks.round + (if (last) 1 else 0),
        current.tracks.usurperLimited &&
          current.tracks.round + (if (last) 1 else 0) < 4)
    }
  }

  private def applyCompletion(ready: ReadyGame, event: RestCompleted): ReadyGame = {
    val current = ready.game.current
    val player = current.players.find(_.player == event.playerId).get
    val ruled = current.map.sites.collect {
      case (id, site @ SiteState(SiteForces.Occupied(
          ForceKind.Exile(owner), _), _, _, _)) if owner == player.lineage => id
    }.toSet
    def clear(card: SiteDenizenState): SiteDenizenState = card match {
      case value: DenizenState => value.copy(tokens = Tokens.empty)
      case value: EdificeState => value.copy(tokens = Tokens.empty)
    }
    val players = current.players.map { candidate =>
      if (candidate.player != event.playerId) candidate
      else candidate.copy(
        board = candidate.board.copy(
          faceUpSecrets = candidate.board.faceUpSecrets +
            candidate.board.faceDownSecrets + event.returnedSecrets,
          faceDownSecrets = 0,
          supply = SupplyTrack(event.refreshedSupply)),
        advisers = candidate.advisers.map {
          case value: DenizenState => value.copy(tokens = Tokens.empty)
          case other => other
        },
        relics = candidate.relics.map(relic => relic.copy(
          tokens = Tokens(relic.tokens.favor, 0))))
    }
    val sites = current.map.sites.map { case (id, site) =>
      id -> (if (ruled(id)) site.copy(denizens = site.denizens.map(clear)) else site)
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
        tracks = current.tracks.copy(round = event.nextRound,
          usurperLimited = event.usurperLimited),
        turn = TurnState(event.nextPlayerId, Phase.Wake, Set.empty),
        pending = None)))
  }

  private def suitOf(catalog: ExecutableCatalog, id: CardId): Option[Suit] = {
    val key = catalog.denizens.find(_.id.value == id.value).map(_.suit.value)
      .orElse(catalog.edifices.find(_.id.value == id.value).map(_.suit.value))
    key.flatMap(value => Suit.all.find(_.key == value))
  }

  private def turnOrder(ready: ReadyGame): Vector[PlayerId] = {
    val participants = ready.game.current.players.map(_.player)
    val index = participants.indexOf(ready.support.firstPlayer)
    participants.drop(index) ++ participants.take(index)
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue)
      : Either[OathViolation, OathTransition] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (next, event) => next.flatMap(evolve(catalog, _, event)))
      .map(OathTransition(_, events, continue))
}
