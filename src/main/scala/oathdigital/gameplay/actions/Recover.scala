package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.{PowerHandler, PowerInspector,
  PowerResolution, PowerWindow}

sealed trait RecoverCommand extends Product with Serializable
trait RecoverModifierContribution extends Product with Serializable
final case class PreparedRecoverModifier(events: Vector[RecoverPowerEvent])
    extends RecoverModifierContribution

/** Typed execution seam owned by the Recover procedure. Concrete powers may
  * prepare Recover contributions, but the generic resolver never interprets
  * their costs or effects.
  */
trait RecoverPowerHandler extends PowerHandler {
  def prepare(input: RecoverPowerPreparation)
      : Either[OathViolation, RecoverModifierContribution]
  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: RecoverPowerEvent): Either[OathViolation, OathState]
}

object RecoverPowerHandler {
  /** Functional adapter for power events composed from typed semantic
    * operations. Canonical reconstruction protects replay from altered event
    * payloads before any operation is applied.
    */
  def operationBackedSelected[E <: RecoverPowerEvent](powerId: PowerId,
      windowValue: PowerWindow, inspectPower: PowerInspector,
      selectEvent: PartialFunction[RecoverPowerEvent, E])(
      prepareEvent: RecoverPowerPreparation => Either[OathViolation, E],
      canonicalEvent: (ExecutableCatalog, ReadyGame, E) =>
        Either[OathViolation, E],
      eventOperations: E => Either[OathViolation, Vector[CoreOperation]])
      : RecoverPowerHandler = new RecoverPowerHandler {
    val window = windowValue
    val resolution = PowerResolution.PlayerSelected
    val implemented = true

    def inspect(context: powerresolver.PowerContext)
        : powerresolver.PowerInspection = inspectPower(context)

    def prepare(input: RecoverPowerPreparation)
        : Either[OathViolation, RecoverModifierContribution] = for {
      event <- prepareEvent(input)
      _ <- Either.cond(event.powerId == powerId, (), InvalidEventOrder(
        s"prepared Recover power ID does not match ${powerId.value}"))
    } yield PreparedRecoverModifier(Vector(event))

    def evolve(catalog: ExecutableCatalog, state: OathState,
        event: RecoverPowerEvent): Either[OathViolation, OathState] = for {
      selected <- selectEvent.lift(event).toRight(InvalidEventOrder(
        s"${powerId.value} handler received another Recover power event"))
      _ <- Either.cond(selected.powerId == powerId, (), InvalidEventOrder(
        s"Recover power ID does not match ${powerId.value}"))
      ready <- state match {
        case Ready(value) => Right(value)
        case _ => Left(GameNotStarted)
      }
      canonical <- canonicalEvent(catalog, ready, selected)
      _ <- Either.cond(selected == canonical, (), InvalidEventOrder(
        s"${powerId.value} recorded operation payload does not match"))
      operations <- eventOperations(canonical)
      execution <- OperationPipeline.run(
        ready, operations, OperationPolicy.exact(
          operations, s"${powerId.value} semantic root is not permitted")
      )(Right(_))
    } yield Ready(execution)
  }
}
final case class RecoverPowerPreparation(catalog: ExecutableCatalog,
    ready: ReadyGame, actor: PlayerId, decision: DecisionId,
    source: RuleSourceRef,
    drawnRelic: RelicId)
object RecoverCommand {
  final case class Start(playerId: PlayerId, decision: DecisionId,
      dice: Vector[DefenseDieFace], modifier: Option[RecoverModifierContribution])
      extends RecoverCommand
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
    case RecoverCommand.Start(player, decision, dice, None) =>
      handle(catalog, state, RecoverCommand.Roll(player, decision, dice))
    case RecoverCommand.Start(_, _, _, Some(_)) => Left(InvalidEventOrder(
      "Recover modifier contribution must be applied at the aggregate boundary"))
    case RecoverCommand.Roll(player, decision, dice) =>
      val readyResult = state match {
        case Ready(ready) if ready.game.current.pending.isEmpty =>
          OathLifecycle.validateAct(state, player)
        case Ready(ready) if ready.game.current.pending.exists(
            _.isInstanceOf[PendingProcedure.RecoverPowerApplied]) =>
          validatePowerApplied(ready, player, decision)
        case Ready(ready) => validatePending(ready, player, decision, requireSuccess = false)
        case _ => Left(GameNotStarted)
      }
      readyResult.flatMap { ready =>
        val p = ready.game.current.players.find(_.player == player).get
        for {
          siteId <- p.pawnSite.toRight(PawnSiteMissing(player))
          _ <- RecoverRules.validate(catalog, ready, p, siteId)
          pending = ready.game.current.pending.collect { case r: PendingProcedure.Recover => r }
          prepared = ready.game.current.pending.collect {
            case r: PendingProcedure.RecoverPowerApplied => r }
          _ <- pending match {
            case Some(r) if r.site != siteId => Left(RecoverOutcomeMismatch("pawn left the Recover site"))
            case Some(r) if r.successful => Left(RecoverOutcomeMismatch("Recover already succeeded"))
            case _ => Right(())
          }
          _ <- prepared match {
            case Some(value) if value.site != siteId =>
              Left(RecoverOutcomeMismatch("power prepared another Recover site"))
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

  private def validatePowerApplied(ready: ReadyGame, player: PlayerId,
      decision: DecisionId): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player)
      Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act)
      Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(value: PendingProcedure.RecoverPowerApplied)
          if value.actor != player => Left(WrongPlayer(value.actor, player))
      case Some(value: PendingProcedure.RecoverPowerApplied)
          if value.decision != decision =>
        Left(RecoverDecisionMismatch(value.decision, decision))
      case Some(_: PendingProcedure.RecoverPowerApplied) => Right(ready)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Recover power is prepared"))
    }
  }

  def markPowerApplied(state: OathState, event: RecoverPowerEvent)
      : Either[OathViolation, OathState] = state match {
    case Ready(ready) if ready.game.current.pending.isEmpty =>
      Right(Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(
        PendingProcedure.RecoverPowerApplied(event.decision, event.playerId,
          event.siteId, event.powerId))))))
    case Ready(ready) => Left(PendingProcedureBlocksAction(
      ready.game.current.pending.get.decision))
    case _ => Left(GameNotStarted)
  }

  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathState] = event match {
    case e: RecoverRolled =>
      val readyResult = state match {
        case Ready(ready) if ready.game.current.pending.isEmpty => OathLifecycle.validateAct(state, e.playerId)
        case Ready(ready) if ready.game.current.pending.exists(
            _.isInstanceOf[PendingProcedure.RecoverPowerApplied]) =>
          validatePowerApplied(ready, e.playerId, e.decision)
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
          site.relics.find(_.id == e.relicId).toRight(RecoverOutcomeMismatch("chosen relic is not at the site")).flatMap { _ =>
            if (e.siteId != r.site) Left(RecoverOutcomeMismatch("recorded relic site does not match"))
            else {
              val operations = Vector[CoreOperation](Move(
                Piece.Card(e.relicId),
                PositionedLocation(Location.Site(r.site)),
                PositionedLocation(Location.PlayArea(e.playerId)),
                resultingOrientation = Some(Orientation.FaceDown)))
              OperationPipeline.run(
                valid, operations, OperationPolicy.exact(
                  operations, "Recover semantic root is not permitted")
              )(evolved => Right(
                GameStateUpdates.updateCurrent(evolved)(_.copy(pending = None))))
                .map(execution => Ready(execution))
            }
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
  def difficulty(catalog: ExecutableCatalog, site: SiteId): Option[Int] =
    catalog.sites.find(_.id == site).flatMap(_.recoverDifficulty)

  def score(faces: Vector[DefenseDieFace]): Int = DefenseDieFace.score(faces)

  def validateAction(catalog: ExecutableCatalog, state: OathState,
      actor: PlayerId, siteId: SiteId): Either[OathViolation, ReadyGame] = for {
    ready <- OathLifecycle.validateAct(state, actor)
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
    _ <- Either.cond(player.pawnSite.contains(siteId), (),
      RecoverUnavailable("pawn is not at the Recover site"))
    _ <- validatePotential(catalog, ready, player, siteId)
  } yield ready

  def validate(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerState,
      siteId: SiteId): Either[OathViolation, Unit] =
    validatePotential(catalog, ready, player, siteId).flatMap { _ =>
      val site = ready.game.current.map.sites.get(siteId)
      Either.cond(site.exists(_.relics.nonEmpty), (),
        OathViolation.RecoverUnavailable("site has no facedown relic"))
    }

  /** Base procedure eligibility without requiring a relic already at the site.
    * Recover powers may satisfy that final condition before the first roll.
    */
  def validatePotential(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, siteId: SiteId): Either[OathViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Recover is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty)) Some("altered Foundations are not supported for Recover")
      else None
    reason.map(OathViolation.UnsupportedRecoverState).toLeft(()).flatMap(_ =>
      PowerRuntime.requireAudited(catalog)).flatMap { _ =>
      if (difficulty(catalog, siteId).isEmpty) Left(OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
      else if (player.board.supply.supply < 1) Left(OathViolation.InsufficientSupply(1, player.board.supply.supply))
      else Right(())
    }
  }

}
