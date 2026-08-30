package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

sealed trait RecoverCommand extends Product with Serializable
object RecoverCommand {
  final case class Roll(playerId: PlayerId, decision: DecisionId,
      dice: Vector[DefenseDieFace]) extends RecoverCommand
  final case class Stop(playerId: PlayerId, decision: DecisionId)
      extends RecoverCommand
  final case class TakeRelic(playerId: PlayerId, decision: DecisionId,
      relicId: RelicId) extends RecoverCommand
}

object Recover {
  def handle(catalog: ExecutableCatalog, state: OathState,
      command: RecoverCommand): Either[OathViolation, OathTransition] = command match {
    case RecoverCommand.Roll(player, decision, dice) =>
      val readyResult = state match {
        case Ready(ready) if ready.game.current.pending.isEmpty =>
          OathLifecycle.validateAct(state, player)
        case Ready(ready) => validatePending(ready, player, decision, requireSuccess = false)
        case _ => Left(GameNotStarted)
      }
      readyResult.flatMap { ready =>
        val p = ready.game.current.players.find(_.player == player).get
        for {
          siteId <- p.pawnSite.toRight(PawnSiteMissing(player))
          _ <- RecoverRules.validate(catalog, ready, p, siteId)
          pending = ready.game.current.pending.collect { case r: PendingProcedure.Recover => r }
          _ <- pending match {
            case Some(r) if r.site != siteId => Left(RecoverOutcomeMismatch("pawn left the Recover site"))
            case Some(r) if r.successful => Left(RecoverOutcomeMismatch("Recover already succeeded"))
            case _ => Right(())
          }
          spent = pending.fold(1)(_.supplySpent + 1)
          _ <- if (p.board.supply.supply >= 1) Right(())
            else Left(InsufficientSupply(1, p.board.supply.supply))
          event = RecoverRolled(player, decision, siteId, spent, dice)
          next <- transition(catalog, state, Vector(event),
            if (RecoverRules.score(pending.toVector.flatMap(_.rolls).flatten ++ dice) >=
                RecoverRules.difficulty(catalog, siteId).get)
              AwaitingRecoverRelic(player, decision)
            else AwaitingRecoverRoll(player, decision))
        } yield next
      }
    case RecoverCommand.Stop(player, decision) => state match {
      case Ready(ready) => validatePending(ready, player, decision, false)
        .flatMap(_ => transition(catalog, state,
          Vector(RecoverStopped(player, decision)), ActActionSelection(player)))
      case _ => Left(GameNotStarted)
    }
    case RecoverCommand.TakeRelic(player, decision, relic) => state match {
      case Ready(ready) => validatePending(ready, player, decision, true).flatMap { _ =>
        val pending = ready.game.current.pending.get.asInstanceOf[PendingProcedure.Recover]
        transition(catalog, state, Vector(RelicRecovered(player, decision,
          pending.site, relic)), ActActionSelection(player))
      }
      case _ => Left(GameNotStarted)
    }
  }

  private def validatePending(ready: ReadyGame, player: PlayerId,
      decision: DecisionId, requireSuccess: Boolean): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player) Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act) Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(r: PendingProcedure.Recover) if r.actor != player => Left(WrongPlayer(r.actor, player))
      case Some(r: PendingProcedure.Recover) if r.decision != decision => Left(RecoverDecisionMismatch(r.decision, decision))
      case Some(r: PendingProcedure.Recover) if requireSuccess && !r.successful => Left(RecoverOutcomeMismatch("Recover has not succeeded"))
      case Some(_: PendingProcedure.Recover) => Right(ready)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Recover procedure is pending"))
    }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathState] = event match {
    case e: RecoverRolled =>
      val readyResult = state match {
        case Ready(ready) if ready.game.current.pending.isEmpty => OathLifecycle.validateAct(state, e.playerId)
        case Ready(ready) => validatePending(ready, e.playerId, e.decision, false)
        case _ => Left(GameNotStarted)
      }
      readyResult.flatMap { ready =>
        val current = ready.game.current
        val player = current.players.find(_.player == e.playerId).get
        val old = current.pending.collect { case r: PendingProcedure.Recover => r }
        for {
          site <- player.pawnSite.toRight(PawnSiteMissing(e.playerId))
          _ <- RecoverRules.validate(catalog, ready, player, site)
          difficulty <- RecoverRules.difficulty(catalog, site).toRight(RecoverUnavailable("site has no Recover Difficulty"))
          _ <- if (e.siteId == site) Right(()) else Left(RecoverOutcomeMismatch("recorded site is not the pawn site"))
          _ <- if (e.dice.size == 2) Right(()) else Left(RecoverOutcomeMismatch("each Supply payment must record exactly two dice"))
          expectedSpent = old.fold(1)(_.supplySpent + 1)
          _ <- if (e.supplySpent == expectedSpent) Right(()) else Left(RecoverOutcomeMismatch("recorded Supply total is invalid"))
          _ <- if (player.board.supply.supply >= 1) Right(()) else Left(InsufficientSupply(1, player.board.supply.supply))
        } yield {
          val rolls = old.fold(Vector(e.dice))(_.rolls :+ e.dice)
          val pending = PendingProcedure.Recover(e.decision, e.playerId, site,
            difficulty, rolls, e.supplySpent, RecoverRules.score(rolls.flatten) >= difficulty)
          Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            players = current.players.map(p => if (p.player != e.playerId) p else
              p.copy(board = p.board.copy(supply = SupplyTrack(p.board.supply.supply - 1)))),
            pending = Some(pending))))
        }
      }
    case e: RecoverStopped => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision, false).flatMap { valid =>
        val r = valid.game.current.pending.get.asInstanceOf[PendingProcedure.Recover]
        if (r.successful) Left(RecoverOutcomeMismatch("a successful Recover cannot be stopped"))
        else Right(Ready(GameStateUpdates.updateCurrent(valid)(_.copy(pending = None))))
      }
      case _ => Left(GameNotStarted)
    }
    case e: RelicRecovered => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision, true).flatMap { valid =>
        val r = valid.game.current.pending.get.asInstanceOf[PendingProcedure.Recover]
        val current = valid.game.current
        current.map.sites.get(r.site).toRight(SiteNotInPlay(r.site)).flatMap { site =>
          site.relics.find(_.id == e.relicId).toRight(RecoverOutcomeMismatch("chosen relic is not at the site")).flatMap { relic =>
            if (e.siteId != r.site) Left(RecoverOutcomeMismatch("recorded relic site does not match"))
            else Right(Ready(GameStateUpdates.updateCurrent(valid)(_.copy(
              map = current.map.copy(sites = current.map.sites.updated(r.site,
                site.copy(relics = site.relics.filterNot(_.id == e.relicId)))),
              players = current.players.map(p => if (p.player != e.playerId) p else
                p.copy(relics = p.relics :+ relic.copy(orientation = Orientation.FaceDown))),
              pending = None))))
          }
        }
      }
      case _ => Left(GameNotStarted)
    }
    case _ => Left(InvalidEventOrder("Recover received a non-Recover event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue) =
    GameplayTransition(state, events, continue)(evolve(catalog, _, _))
}

object RecoverRules {
  private val RelevantHandlerIds = Set(
    "denizen.relic-worship",
    "edifice.e13.ruined",
    "edifice.e17.intact",
    "edifice.e17.ruined"
  )

  private[gameplay] def isRelevantHandler(handlerId: String): Boolean =
    RelevantHandlerIds(handlerId)

  def difficulty(catalog: ExecutableCatalog, site: SiteId): Option[Int] =
    catalog.sites.find(_.id == site).flatMap(_.recoverDifficulty)

  def score(faces: Vector[DefenseDieFace]): Int = DefenseDieFace.score(faces)

  def validate(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerState,
      siteId: SiteId): Either[OathViolation, Unit] = {
    val game = ready.game
    val site = game.current.map.sites.get(siteId)
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Recover is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty)) Some("altered Foundations are not supported for Recover")
      else None
    reason.map(OathViolation.UnsupportedRecoverState).toLeft(()).flatMap(_ =>
      PowerRuntime.requireAudited(catalog)).flatMap { _ =>
      if (difficulty(catalog, siteId).isEmpty) Left(OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
      else if (site.forall(_.relics.isEmpty)) Left(OathViolation.RecoverUnavailable("site has no facedown relic"))
      else if (player.board.supply.supply < 1) Left(OathViolation.InsufficientSupply(1, player.board.supply.supply))
      else Right(())
    }
  }
}
