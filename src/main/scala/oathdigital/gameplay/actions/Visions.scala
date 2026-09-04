package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{Burn, Give, Location, Move => CoreMove,
  OperationExecutor, OperationPolicy, OperationTransaction, Piece,
  PositionedLocation, StackPosition}

sealed trait VisionCommand extends Product with Serializable
object VisionCommand {
  final case class Reveal(player: PlayerId, vision: VisionId) extends VisionCommand
  final case class PlayConspiracy(player: PlayerId, decision: DecisionId,
      target: Option[ConspiracyTargetRef]) extends VisionCommand
}

object VisionRules {
  val Conquest = VisionId("vision:vision-of-conquest")
  val Sanctuary = VisionId("vision:vision-of-sanctuary")
  val Rebellion = VisionId("vision:vision-of-rebellion")
  val Faith = VisionId("vision:vision-of-faith")
  val Conspiracy = VisionId("vision:conspiracy")

  val goals: Map[VisionId, OathkeeperGoal] = Map(
    Conquest -> OathkeeperGoal.Supremacy,
    Sanctuary -> OathkeeperGoal.Protection,
    Rebellion -> OathkeeperGoal.ThePeople,
    Faith -> OathkeeperGoal.Devotion)

  def trueGoal(id: VisionId): Option[OathkeeperGoal] = goals.get(id)
}

object Visions {
  def canReveal(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, vision: VisionId): Boolean =
    VisionRules.trueGoal(vision).nonEmpty &&
      MinorActionPowerSupport.validateFaceupVision(
        catalog, ready, player, vision).isRight

  def canPlayConspiracy(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Boolean =
    MinorActionPowerSupport.validateFaceupVision(
      catalog, ready, player, VisionRules.Conspiracy).isRight

  def handle(catalog: ExecutableCatalog, state: OathState, command: VisionCommand)
      : Either[OathViolation, OathTransition] = command match {
    case VisionCommand.Reveal(player, vision) => for {
      ready <- validateAct(catalog, state, player)
      actor = ready.game.current.players.find(_.player == player).get
      _ <- Either.cond(VisionRules.trueGoal(vision).nonEmpty, (),
        VisionUnavailable("only a true Vision can be revealed"))
      _ <- facedownVision(actor, vision)
      _ <- MinorActionPowerSupport.validateFaceupVision(catalog, ready, player, vision)
      region <- actor.pawnSite.flatMap(ready.game.current.map.regionOf)
        .toRight(PawnSiteMissing(player))
      event = VisionRevealed(player, vision, actor.revealedVision.map(_.id),
        nextRegion(region))
      next <- transition(catalog, state, Vector(event), ActActionSelection(player))
    } yield next

    case VisionCommand.PlayConspiracy(player, decision, target) => for {
      ready <- state match {
        case Ready(r) => r.game.current.pending match {
          case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&
              p.actor == player && p.decision == decision => Right(r)
          case _ => validateAct(catalog, state, player)
        }
        case _ => Left(GameNotStarted)
      }
      actor = ready.game.current.players.find(_.player == player).get
      fromSearch = ready.game.current.pending.exists {
        case p: PendingProcedure.Conspiracy => p.awaitingTarget
        case _ => false
      }
      _ <- if (fromSearch) Right(()) else facedownVision(actor, VisionRules.Conspiracy)
      _ <- MinorActionPowerSupport.validateFaceupVision(
        catalog, ready, player, VisionRules.Conspiracy)
      legal = legalTargetRefs(ready, player)
      _ <- Either.cond(if (legal.isEmpty) target.isEmpty else target.exists(legal.contains), (),
        ConspiracyUnavailable(if (legal.isEmpty) "no target is legal"
          else "a legal co-located enemy asset must be chosen"))
      resolved = target.flatMap(resolveTarget(ready, _))
      favorReturns = resolved.collect {
        case ConspiracyTarget.Banner(_, Banner.PeoplesFavor) =>
          BannerRules.raidFavorReturn(ready.banks.favor,
            BannerRules.resources(ready.game.current, Banner.PeoplesFavor))
      }.getOrElse(Vector.empty)
      started = ConspiracyStarted(player, decision, VisionRules.Conspiracy,
        resolved, favorReturns)
      after <- evolve(catalog, state, started)
      pending = after.asInstanceOf[Ready].value.game.current.pending.get
        .asInstanceOf[PendingProcedure.Conspiracy]
      completed = ConspiracyCompleted(player, decision,
        VisionRules.Conspiracy, pending.target, pending.favorReturnOrder)
      finished <- evolve(catalog, after, completed)
    } yield OathTransition(finished, Vector(started, completed),
      ActActionSelection(player))
  }

  def legalTargetRefs(ready: ReadyGame,
      actor: PlayerId): Vector[ConspiracyTargetRef] = {
    val current = ready.game.current
    val site = current.players.find(_.player == actor).flatMap(_.pawnSite)
    current.players.filter(p => p.player != actor && p.pawnSite == site).flatMap { p =>
      p.relics.indices.map(i => ConspiracyTargetRef.RelicSlot(p.player, i)) ++
        Vector(Banner.PeoplesFavor, Banner.DarkestSecret).filter(b =>
          BannerRules.holder(current, b).contains(p.player))
          .map(b => ConspiracyTargetRef.Banner(p.player, b))
    }
  }

  private def resolveTarget(ready: ReadyGame,
      target: ConspiracyTargetRef): Option[ConspiracyTarget] = target match {
    case ConspiracyTargetRef.RelicSlot(owner, slot) =>
      ready.game.current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
        .map(r => ConspiracyTarget.Relic(owner, r.id))
    case ConspiracyTargetRef.Banner(owner, banner) =>
      Some(ConspiracyTarget.Banner(owner, banner))
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] = event match {
    case e: VisionRevealed => for {
      ready <- validateAct(catalog, state, e.playerId)
      actor = ready.game.current.players.find(_.player == e.playerId).get
      _ <- Either.cond(VisionRules.trueGoal(e.visionId).nonEmpty, (),
        VisionUnavailable("recorded card is not a true Vision"))
      _ <- facedownVision(actor, e.visionId)
      _ <- MinorActionPowerSupport.validateFaceupVision(
        catalog, ready, e.playerId, e.visionId)
      region <- actor.pawnSite.flatMap(ready.game.current.map.regionOf)
        .toRight(PawnSiteMissing(e.playerId))
      _ <- Either.cond(e.replaced == actor.revealedVision.map(_.id) &&
        e.destination == nextRegion(region), (),
        MinorActionOutcomeMismatch("recorded Vision replacement facts changed"))
      from = PositionedLocation(Location.PlayArea(e.playerId))
      operations = actor.revealedVision.toVector.map { replaced =>
        CoreMove(
          Piece.Card(replaced.id), from,
          PositionedLocation(Location.RegionalDiscard(e.destination),
            StackPosition.Top))
      } :+ CoreMove(
        Piece.Card(e.visionId), from, from,
        resultingOrientation = Some(Orientation.FaceUp))
      executor = new OperationExecutor(OperationPolicy.exact(
        operations, "Vision reveal semantic root is not permitted"))
      execution <- OperationTransaction.evolve(
        ready, operations, executor)(Right(_))
    } yield Ready(execution)

    case e: ConspiracyStarted => for {
      ready <- state match {
        case Ready(r) => r.game.current.pending match {
          case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&
              p.actor == e.playerId && p.decision == e.decision => Right(r)
          case _ => validateAct(catalog, state, e.playerId)
        }
        case _ => Left(GameNotStarted)
      }
      actor = ready.game.current.players.find(_.player == e.playerId).get
      _ <- Either.cond(e.source == VisionRules.Conspiracy, (),
        ConspiracyOutcomeMismatch("recorded source is not Conspiracy"))
      fromSearch = ready.game.current.pending.exists {
        case p: PendingProcedure.Conspiracy => p.awaitingTarget
        case _ => false
      }
      _ <- if (fromSearch) Right(()) else facedownVision(actor, e.source)
      _ <- MinorActionPowerSupport.validateFaceupVision(
        catalog, ready, e.playerId, e.source)
      legal = legalTargetRefs(ready, e.playerId).flatMap(resolveTarget(ready, _))
      _ <- Either.cond(if (legal.isEmpty) e.target.isEmpty else e.target.exists(legal.contains), (),
        ConspiracyOutcomeMismatch("recorded target is not legal"))
      expectedFavor = e.target.collect {
        case ConspiracyTarget.Banner(_, Banner.PeoplesFavor) =>
          BannerRules.raidFavorReturn(ready.banks.favor,
            BannerRules.resources(ready.game.current, Banner.PeoplesFavor))
      }.getOrElse(Vector.empty)
      _ <- Either.cond(e.automaticFavorReturns == expectedFavor, (),
        ConspiracyOutcomeMismatch("recorded automatic favor returns changed"))
    } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
      pending = Some(PendingProcedure.Conspiracy(e.decision, e.playerId,
        e.source, e.target, e.automaticFavorReturns)))))

    case e: ConspiracyCompleted => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision).flatMap { p =>
        Either.cond(e.source == p.source &&
          e.target == p.target && e.favorReturnOrder == p.favorReturnOrder, (),
          ConspiracyOutcomeMismatch("recorded completion is stale or invalid")).flatMap { _ =>
          applyCompletion(ready, p).map(Ready(_))
        }
      }
      case _ => Left(GameNotStarted)
    }

    case _ => Left(InvalidEventOrder("Visions received an unrelated event"))
  }

  private def applyCompletion(ready: ReadyGame,
      pending: PendingProcedure.Conspiracy): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    // The played Conspiracy was held either as a facedown adviser (direct
    // play) or in the actor's temporary hand (a Conspiracy kept from a
    // Search). It is removed from whichever zone holds it below.
    val sourceHeldInAdvisers = current.players.find(_.player == pending.actor)
      .exists(_.advisers.exists(_.id == pending.source))
    val sourceHeldInHand = current.temporaryHands
      .getOrElse(pending.actor, Vector.empty).contains(pending.source)
    val taken = pending.target match {
      case Some(ConspiracyTarget.Relic(owner, relic)) =>
        Vector(Give(Piece.Card(relic), owner,
          Location.PlayArea(owner), Location.PlayArea(pending.actor)))
      case _ => Vector.empty
    }
    val bannerOps = pending.target match {
      case Some(ConspiracyTarget.Banner(owner, Banner.PeoplesFavor)) =>
        val drains = pending.favorReturnOrder.map(suit => CoreMove(
          Piece.Favor(1),
          PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
          PositionedLocation(Location.FavorBank(suit))))
        drains :+ CoreMove(
          Piece.Banner(Banner.PeoplesFavor),
          PositionedLocation(Location.PlayArea(owner)),
          PositionedLocation(Location.PlayArea(pending.actor)))
      case Some(ConspiracyTarget.Banner(owner, Banner.DarkestSecret)) =>
        // A Conspiracy that takes the Darkest Secret burns every secret on the
        // banner (they return to the untracked SharedBank sink); nothing is
        // placed on sites and nothing returns to the previous holder. The burn
        // amount is read from the banner at completion: it is provably the same
        // as at ConspiracyStarted because the banner is immutable between the
        // two events (ConspiracyStarted changes nothing and the pending
        // procedure blocks any other banner mutation).
        val prior = current.banners.darkestSecret.secrets
        val burn = Option.when(prior > 0)(Burn.secrets(prior,
          PositionedLocation(Location.OnBanner(Banner.DarkestSecret)))).toVector
        burn :+ CoreMove(
          Piece.Banner(Banner.DarkestSecret),
          PositionedLocation(Location.PlayArea(owner)),
          PositionedLocation(Location.PlayArea(pending.actor)))
      case _ => Vector.empty
    }
    val operations = taken ++ bannerOps
    val executor = new OperationExecutor(OperationPolicy.exact(
      operations, "Conspiracy semantic root is not permitted"))
    def update(state: ReadyGame): Either[OathViolation, ReadyGame] =
      Right(GameStateUpdates.updateCurrent(state)(_.copy(pending = None)))
    val evolved = if (operations.isEmpty) update(ready)
    else OperationTransaction.evolve(ready, operations, executor)(update)
    // The played Conspiracy was held either as a facedown adviser (direct play)
    // or in the actor's temporary hand (kept from a Search). Removing it here
    // is a documented executor bypass.
    // executor bypass: Conspiracy leaves the game only after the batch validates.
    evolved.map { state =>
      if (!sourceHeldInAdvisers && !sourceHeldInHand) state
      else GameStateUpdates.updateCurrent(state) { gameState =>
        val players = gameState.players.map { player =>
          if (player.player != pending.actor) player
          else player.copy(advisers =
            player.advisers.filterNot(_.id == pending.source))
        }
        val hands = if (sourceHeldInHand) gameState.temporaryHands
          .updated(pending.actor,
            gameState.temporaryHands.getOrElse(pending.actor, Vector.empty)
              .filterNot(_ == pending.source))
        else gameState.temporaryHands
        gameState.copy(players = players, temporaryHands = hands)
      }
    }
  }

  private def validatePending(ready: ReadyGame, player: PlayerId,
      decision: DecisionId): Either[OathViolation, PendingProcedure.Conspiracy] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player) Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act) Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(p: PendingProcedure.Conspiracy) if p.actor != player => Left(WrongPlayer(p.actor, player))
      case Some(p: PendingProcedure.Conspiracy) if p.decision != decision =>
        Left(ConspiracyDecisionMismatch(p.decision, decision))
      case Some(p: PendingProcedure.Conspiracy) => Right(p)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Conspiracy decision is pending"))
    }
  }

  private def validateAct(catalog: ExecutableCatalog, state: OathState,
      player: PlayerId): Either[OathViolation, ReadyGame] =
    OathLifecycle.validateAct(state, player).flatMap { ready =>
      MinorActionPowerSupport.validateInventory(catalog).map(_ => ready)
    }

  private def facedownVision(player: PlayerState, id: VisionId) =
    Either.cond(player.advisers.exists {
      case VisionState(`id`, Orientation.FaceDown) => true
      case _ => false
    }, (), MinorActionUnavailable("Vision is not a facedown adviser"))

  private def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue) =
    GameplayTransition(state, events, continue)(evolve(catalog, _, _))
}
