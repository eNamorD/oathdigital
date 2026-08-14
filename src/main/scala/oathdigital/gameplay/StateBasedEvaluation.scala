package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

/**
 * State-based checks for the fixed, all-Exile first game only.
 *
 * CR p.16 gives Supremacy a strict qualification rule, then gives the current
 * holder two special tie duties: retain a highest tie, or choose among tied
 * leaders when no longer tied. The latter is a player decision and is not
 * guessed here. This is deliberately not a reusable, context-free tie breaker.
 */
object StateBasedEvaluation {
  def banditRefill(catalog: ExecutableCatalog, state: OathState)
      : Either[OathViolation, Option[OathEvent]] = supported(state).map { ready =>
    val capacities = catalog.sites.map(s => s.id -> s.capacity).toMap
    val refills = ready.game.current.map.inPlay.flatMap { siteId =>
      ready.game.current.map.sites.get(siteId).collect {
        case SiteState(SiteForces.Empty, _, _, _) if capacities.getOrElse(siteId, 0) > 0 =>
          siteId -> capacities(siteId)
      }
    }
    Option.when(refills.nonEmpty)(BanditsRefilled(refills))
  }

  def afterAction(state: OathState): Either[OathViolation, Option[OathEvent]] =
    supported(state).flatMap { ready =>
      val current = ready.game.current
      val counts = current.players.map { player =>
        val count = current.map.sites.values.count(_.forces match {
          case SiteForces.Occupied(ForceKind.Exile(lineage), _) =>
            lineage == player.lineage
          case _ => false
        })
        player.player -> count
      }.toMap
      val maximum = counts.values.maxOption.getOrElse(0)
      val leaders = counts.collect { case (player, count)
          if maximum > 0 && count == maximum => player }.toSet
      val wanted = current.title.holder match {
        case Some(holder) if leaders(holder) => Right(Some(holder))
        case Some(_) if leaders.size > 1 => Left(UnsupportedOathkeeperTie(
          "a displaced Oathkeeper must choose among tied Supremacy leaders"))
        case _ if leaders.size == 1 => Right(leaders.headOption)
        case _ => Right(None)
      }
      wanted.map { holder =>
        // Retaining the same holder also retains the physical title side.
        // Only a holder change flips a transferred title back to Oathkeeper.
        Option.when(current.title.holder != holder)(OathkeeperChanged(holder))
      }
    }

  def atWake(state: OathState): Either[OathViolation, Option[OathEvent]] =
    supported(state).map { ready =>
      val current = ready.game.current
      current.title match {
        case OathkeeperState(Some(player), TitleSide.Usurper)
            if player == current.turn.activePlayer => Some(UsurperVictory(player))
        case OathkeeperState(Some(player), TitleSide.Oathkeeper)
            if player == current.turn.activePlayer && !current.tracks.usurperLimited =>
          Some(UsurperFlipped(player))
        case _ => None
      }
    }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent): Either[OathViolation, OathState] =
    event match {
      case recorded: BanditsRefilled => banditRefill(catalog, state).flatMap {
        case Some(expected: BanditsRefilled) if expected == recorded =>
          update(state)(current => current.copy(map = current.map.copy(
            sites = recorded.sites.foldLeft(current.map.sites) {
              case (sites, (id, count)) => sites.updated(id,
                sites(id).copy(forces = SiteForces.Occupied(ForceKind.Bandit, count)))
            })))
        case expected => Left(InvalidEventOrder(
          s"Bandit refill mismatch: expected $expected, recorded $recorded"))
      }
      case recorded: OathkeeperChanged =>
        afterAction(state).flatMap {
          case Some(expected: OathkeeperChanged) if expected == recorded =>
            update(state)(current => current.copy(
              title = OathkeeperState(recorded.holder, TitleSide.Oathkeeper)))
          case expected => Left(InvalidEventOrder(
            s"Oathkeeper evaluation mismatch: expected $expected, recorded $recorded"))
        }
      case recorded: UsurperFlipped =>
        atWake(state).flatMap {
          case Some(expected: UsurperFlipped) if expected == recorded =>
            update(state)(current => current.copy(
              title = current.title.copy(side = TitleSide.Usurper)))
          case expected => Left(InvalidEventOrder(
            s"Usurper flip mismatch: expected $expected, recorded $recorded"))
        }
      case recorded: UsurperVictory =>
        atWake(state).flatMap {
          case Some(expected: UsurperVictory) if expected == recorded =>
            update(state)(current => current.copy(result = Some(GameResult(recorded.playerId))))
          case expected => Left(InvalidEventOrder(
            s"Usurper victory mismatch: expected $expected, recorded $recorded"))
        }
      case _ => Left(InvalidEventOrder("not a state-based evaluation event"))
    }

  private def supported(state: OathState): Either[OathViolation, ReadyGame] = state match {
    case Ready(ready)
        if ready.support.foundationProfile == FirstGameFoundationProfile.FixedUnaltered &&
          ready.game.campaign.oathkeeperGoal == OathkeeperGoal.Supremacy &&
          ready.game.campaign.lineages.values.forall(_.role == Role.Exile) => Right(ready)
    case Ready(_) => Left(UnsupportedWakeVictoryState(
      "state-based Oathkeeper evaluation is limited to the fixed all-Exile first game"))
    case _ => Left(GameNotStarted)
  }

  private def update(state: OathState)(f: CurrentGameState => CurrentGameState) = state match {
    case Ready(ready) => Right(Ready(ready.copy(game = ready.game.copy(
      current = f(ready.game.current)))))
    case _ => Left(GameNotStarted)
  }
}
