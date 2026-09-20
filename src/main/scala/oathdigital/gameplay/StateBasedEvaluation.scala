package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.model.{Move => CoreMove}

/**
 * State-based checks for the fixed, unaltered all-Exile game only.
 *
 * CR p.16 gives each goal a strict qualification rule, then gives the current
 * holder two special tie duties: retain a qualifying tie, or choose among tied
 * leaders when no longer tied. The latter is a player decision and is not
 * guessed here. This is deliberately not a reusable, context-free tie breaker.
 */
object StateBasedEvaluation {
  private val operationAllowlist: OperationPolicy =
    StateBasedOperationPolicy
  private val visionPriority = Vector(VisionRules.Conquest,
    VisionRules.Rebellion, VisionRules.Sanctuary, VisionRules.Faith)
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
      current.players.find(_.player == current.turn.activePlayer).flatMap { actor =>
        actor.revealedVision.flatMap(v => Option.when(
          VisionVictoryEligibility.qualifies(actor.player, current))(
          VisionVictory(actor.player, v.id)))
      }
    }

  def endRound(state: OathState, randomWinner: Vector[PlayerId] => PlayerId)
      : Either[OathViolation, Vector[OathEvent]] = supported(state).flatMap { ready =>
    val current = ready.game.current
    val order = ready.game.current.players.map(_.player)
    val firstIndex = order.indexOf(ready.setup.firstPlayer)
    val turnOrder = order.drop(firstIndex) ++ order.take(firstIndex)
    if (current.turn.phase != Phase.RoundEnd ||
        current.turn.activePlayer != turnOrder.head)
      Left(InvalidEventOrder("round ending requires the completed round's final Rest"))
    else if (current.tracks.round < 8)
      Right(Vector(RoundEnded(current.tracks.round,
        Some(current.tracks.round + 1))))
    else {
      val usurper = current.title match {
        case OathkeeperState(Some(player), TitleSide.Usurper) => Some(
          WarExhaustionResolved(player, VictoryKind.Usurper, None, Vector.empty))
        case _ => None
      }
      val visionary = visionPriority.iterator.flatMap { vision =>
          current.players.find(p => p.revealedVision.exists(_.id == vision) &&
            VisionVictoryEligibility.qualifies(p.player, current))
            .map(p => WarExhaustionResolved(p.player, VictoryKind.Visionary,
              Some(vision), Vector.empty))
        }.toSeq.headOption
      val fallback = current.title.holder.map(player => WarExhaustionResolved(
        player, VictoryKind.Oathkeeper, None, Vector.empty)).getOrElse {
        val candidates = turnOrder
        WarExhaustionResolved(randomWinner(candidates),
          VictoryKind.RandomSelection, None, candidates)
      }
      Right(Vector(RoundEnded(8, None), usurper.orElse(visionary).getOrElse(fallback)))
    }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent): Either[OathViolation, OathState] =
    event match {
      case recorded: BanditsRefilled => banditRefill(catalog, state).flatMap {
        case Some(expected: BanditsRefilled) if expected == recorded =>
          supported(state).flatMap { ready =>
            val operations = recorded.sites.map { case (site, count) =>
              CoreMove(
                Piece.Warbands(ForceKind.Bandit, count),
                PositionedLocation(Location.WarbandBank(ForceKind.Bandit)),
                PositionedLocation(Location.Site(site))
              )
            }
            OperationPipeline.run(
              ready, operations, operationAllowlist)(Right(_))
              .flatMap(_.expectEffects(operations,
                "Bandit refill effect differs from recorded outcome"))
              .map(Ready(_))
          }
        case expected => Left(InvalidEventOrder(
          s"Bandit refill mismatch: expected $expected, recorded $recorded"))
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
            update(state)(current => current.copy(result = Some(GameResult(
              recorded.playerId, VictoryKind.Usurper))))
          case expected => Left(InvalidEventOrder(
            s"Usurper victory mismatch: expected $expected, recorded $recorded"))
        }
      case recorded: VisionVictory =>
        visionAtWake(state).flatMap {
          case Some(expected: VisionVictory) if expected == recorded =>
            update(state)(current => current.copy(result = Some(GameResult(
              recorded.playerId, VictoryKind.Visionary))))
          case expected => Left(InvalidEventOrder(
            s"Vision victory mismatch: expected $expected, recorded $recorded"))
        }
      case recorded: RoundEnded => state match {
        case Ready(ready) =>
          val round = ready.game.current.tracks.round
          val expected = RoundEnded(round, Option.when(round < 8)(round + 1))
          val players = ready.game.current.players.map(_.player)
          val firstIndex = players.indexOf(ready.setup.firstPlayer)
          val first = (players.drop(firstIndex) ++ players.take(firstIndex)).head
          if (ready.game.current.turn.phase != Phase.RoundEnd ||
              ready.game.current.turn.activePlayer != first)
            Left(InvalidEventOrder("round-end event is outside the round-end procedure"))
          else if (recorded != expected) Left(InvalidEventOrder(
            s"round-end mismatch: expected $expected, recorded $recorded"))
          else update(state)(current => current.copy(
            tracks = current.tracks.copy(
              round = recorded.nextRound.getOrElse(round),
              usurperLimited = current.tracks.usurperLimited &&
                recorded.completedRound < 3),
            turn = current.turn.copy(phase = recorded.nextRound.fold[Phase](
              Phase.WarExhaustion)(_ => Phase.Wake))))
        case _ => Left(GameNotStarted)
      }
      case recorded: WarExhaustionResolved => expectedWarExhaustion(state).flatMap {
        case ExactWar(expected) if expected == recorded => update(state)(current => current.copy(
          result = Some(GameResult(recorded.winner, recorded.kind))))
        case LeftRandom(candidates) if recorded.kind == VictoryKind.RandomSelection &&
            recorded.visionId.isEmpty && recorded.randomCandidates == candidates &&
            candidates.contains(recorded.winner) => update(state)(current => current.copy(
          result = Some(GameResult(recorded.winner, recorded.kind))))
        case expected => Left(InvalidEventOrder(
          s"War Exhaustion mismatch: expected $expected, recorded $recorded"))
      }
      case _ => Left(InvalidEventOrder("not a state-based evaluation event"))
    }

  private[gameplay] def supported(state: OathState): Either[OathViolation, ReadyGame] = state match {
    case Ready(ready) if ready.game.campaign.lineages.values.forall(
      _.role == Role.Exile) => Right(ready)
    case Ready(_) => Left(UnsupportedWakeVictoryState(
      "state-based Oathkeeper evaluation is limited to the fixed, unaltered all-Exile game"))
    case _ => Left(GameNotStarted)
  }

  private sealed trait ExpectedWarExhaustion
  private final case class ExactWar(value: WarExhaustionResolved)
      extends ExpectedWarExhaustion
  private final case class LeftRandom(candidates: Vector[PlayerId])
      extends ExpectedWarExhaustion

  private def expectedWarExhaustion(state: OathState)
      : Either[OathViolation, ExpectedWarExhaustion] = supported(state).flatMap { ready =>
    val current = ready.game.current
    if (current.tracks.round != 8 || current.result.nonEmpty ||
        current.turn.phase != Phase.WarExhaustion)
      Left(InvalidEventOrder("War Exhaustion is only resolved after round eight"))
    else endRoundWinner(current, ready.setup.firstPlayer)
  }

  private def endRoundWinner(current: CurrentGameState, first: PlayerId)
      : Either[OathViolation, ExpectedWarExhaustion] = {
    current.title match {
      case OathkeeperState(Some(player), TitleSide.Usurper) => Right(ExactWar(
        WarExhaustionResolved(player, VictoryKind.Usurper, None, Vector.empty)))
      case _ =>
        visionPriority.iterator.flatMap { vision =>
            current.players.find(p => p.revealedVision.exists(_.id == vision) &&
              VisionVictoryEligibility.qualifies(p.player, current))
              .map(p => WarExhaustionResolved(p.player, VictoryKind.Visionary,
                Some(vision), Vector.empty))
          }.toSeq.headOption.orElse(current.title.holder.map(player =>
          WarExhaustionResolved(player, VictoryKind.Oathkeeper, None, Vector.empty))) match {
          case Some(value) => Right(ExactWar(value))
          case None =>
            val players = current.players.map(_.player)
            val i = players.indexOf(first)
            val candidates = players.drop(i) ++ players.take(i)
            Right(LeftRandom(candidates))
        }
    }
  }

  private def update(state: OathState)(f: CurrentGameState => CurrentGameState) = state match {
    case Ready(ready) => Right(Ready(ready.copy(game = ready.game.copy(
      current = f(ready.game.current)))))
    case _ => Left(GameNotStarted)
  }
}
