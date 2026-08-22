package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._
import oathdigital.gameplay.actions.VisionRules

/**
 * State-based checks for the fixed, unaltered all-Exile game only.
 *
 * CR p.16 gives each goal a strict qualification rule, then gives the current
 * holder two special tie duties: retain a qualifying tie, or choose among tied
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
      val leaders = qualifyingPlayers(
        ready.game.campaign.oathkeeperGoal, current)
      current.title.holder match {
        case Some(holder) if leaders(holder) => Right(None)
        case Some(holder) if leaders.size > 1 =>
          val candidates = current.players.map(_.player).filter(leaders)
          val decision = DecisionId(s"oathkeeper-recipient-${current.tracks.round}-" +
            s"${current.turn.activePlayer.value}-${holder.value}-" +
            candidates.map(_.value).mkString("-"))
          Right(Some(OathkeeperRecipientChoiceStarted(
            holder, decision, candidates)))
        case _ if leaders.size == 1 => Right(Some(OathkeeperChanged(
          leaders.headOption)))
        case Some(_) => Right(Some(OathkeeperChanged(None)))
        case None => Right(None)
      }
    }

  def chooseRecipient(catalog: ExecutableCatalog, state: OathState,
      actor: PlayerId, decision: DecisionId,
      recipient: PlayerId): Either[OathViolation, OathTransition] = state match {
    case Ready(ready) => ready.game.current.pending match {
      case Some(p: PendingProcedure.OathkeeperRecipient) if p.actor != actor =>
        Left(WrongPlayer(p.actor, actor))
      case Some(p: PendingProcedure.OathkeeperRecipient)
          if p.decision != decision => Left(InvalidEventOrder(
            "Oathkeeper recipient decision does not match"))
      case Some(p: PendingProcedure.OathkeeperRecipient)
          if !p.candidates.contains(recipient) => Left(InvalidEventOrder(
            "Oathkeeper recipient is not an authorized tied leader"))
      case Some(p: PendingProcedure.OathkeeperRecipient) =>
        val event = OathkeeperRecipientChosen(actor, decision, recipient)
        evolve(catalog, state, event).map(next => OathTransition(next,
          Vector(event), OathContinue.ActActionSelection(
            ready.game.current.turn.activePlayer)))
      case Some(_) => Left(InvalidEventOrder(
        "another procedure is pending"))
      case None => Left(InvalidEventOrder(
        "no Oathkeeper recipient decision is pending"))
    }
    case _ => Left(GameNotStarted)
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

  def visionAtWake(state: OathState): Either[OathViolation, Option[OathEvent]] =
    supported(state).map { ready =>
      val current = ready.game.current
      Option.when(current.tracks.visionsDrawn >= 3)(current.players.find(
        _.player == current.turn.activePlayer).flatMap { actor =>
        actor.revealedVision.flatMap(v => VisionRules.trueGoal(v.id).flatMap { goal =>
          Option.when(qualifyingPlayers(goal, current) == Set(actor.player))(
            VisionVictory(actor.player, v.id))
        })
      }).flatten
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
      case recorded: OathkeeperRecipientChoiceStarted =>
        afterAction(state).flatMap {
          case Some(expected: OathkeeperRecipientChoiceStarted)
              if expected == recorded => update(state)(current => current.copy(
                pending = Some(PendingProcedure.OathkeeperRecipient(
                  recorded.decision, recorded.actor, recorded.candidates))))
          case expected => Left(InvalidEventOrder(
            s"Oathkeeper recipient decision mismatch: expected $expected, recorded $recorded"))
        }
      case recorded: OathkeeperRecipientChosen => state match {
        case Ready(ready) => ready.game.current.pending match {
          case Some(p: PendingProcedure.OathkeeperRecipient)
              if p.actor == recorded.actor && p.decision == recorded.decision &&
                p.candidates.contains(recorded.recipient) =>
            update(state)(current => current.copy(
              title = OathkeeperState(Some(recorded.recipient),
                TitleSide.Oathkeeper), pending = None))
          case _ => Left(InvalidEventOrder(
            "recorded Oathkeeper recipient choice is stale or unauthorized"))
        }
        case _ => Left(GameNotStarted)
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
      case recorded: VisionVictory =>
        visionAtWake(state).flatMap {
          case Some(expected: VisionVictory) if expected == recorded =>
            update(state)(current => current.copy(result = Some(GameResult(recorded.playerId))))
          case expected => Left(InvalidEventOrder(
            s"Vision victory mismatch: expected $expected, recorded $recorded"))
        }
      case _ => Left(InvalidEventOrder("not a state-based evaluation event"))
    }

  private def supported(state: OathState): Either[OathViolation, ReadyGame] = state match {
    case Ready(ready)
        if ready.support.foundationProfile == FirstGameFoundationProfile.FixedUnaltered &&
          ready.game.campaign.lineages.values.forall(_.role == Role.Exile) => Right(ready)
    case Ready(_) => Left(UnsupportedWakeVictoryState(
      "state-based Oathkeeper evaluation is limited to the fixed, unaltered all-Exile game"))
    case _ => Left(GameNotStarted)
  }

  private def qualifyingPlayers(goal: OathkeeperGoal,
      current: CurrentGameState): Set[PlayerId] =
    goal match {
      case OathkeeperGoal.Supremacy =>
        leadersWithPositiveCount(current.players.map { player =>
          player.player -> current.map.sites.values.count(_.forces match {
            case SiteForces.Occupied(ForceKind.Exile(lineage), _) =>
              lineage == player.lineage
            case _ => false
          })
        })
      case OathkeeperGoal.Protection =>
        leadersWithPositiveCount(current.players.map(player =>
          player.player -> player.relics.size))
      case OathkeeperGoal.ThePeople =>
        current.banners.peoplesFavor.holder.toSet
      case OathkeeperGoal.Devotion =>
        current.banners.darkestSecret.holder.toSet
    }

  private def leadersWithPositiveCount(
      counts: Vector[(PlayerId, Int)]): Set[PlayerId] = {
    val maximum = counts.map(_._2).maxOption.getOrElse(0)
    counts.collect { case (player, count) if maximum > 0 && count == maximum =>
      player
    }.toSet
  }

  private def update(state: OathState)(f: CurrentGameState => CurrentGameState) = state match {
    case Ready(ready) => Right(Ready(ready.copy(game = ready.game.copy(
      current = f(ready.game.current)))))
    case _ => Left(GameNotStarted)
  }
}
