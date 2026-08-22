package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathContinue._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

sealed trait VisionCommand extends Product with Serializable
object VisionCommand {
  final case class Reveal(player: PlayerId, vision: VisionId) extends VisionCommand
  final case class PlayConspiracy(player: PlayerId, decision: DecisionId,
      target: Option[ConspiracyTargetRef]) extends VisionCommand
  final case class ChooseSecretSite(player: PlayerId, decision: DecisionId,
      site: SiteId) extends VisionCommand
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
      automatic = resolved.collect {
        case ConspiracyTarget.Banner(_, Banner.DarkestSecret) =>
          BannerRules.automaticSitePrefix(ready.game.current, Vector.empty,
            BannerRules.resources(ready.game.current, Banner.DarkestSecret) / 2)
      }.getOrElse(Vector.empty)
      favorReturns = resolved.collect {
        case ConspiracyTarget.Banner(_, Banner.PeoplesFavor) =>
          BannerRules.raidFavorReturn(ready.support.favorBanks,
            BannerRules.resources(ready.game.current, Banner.PeoplesFavor))
      }.getOrElse(Vector.empty)
      started = ConspiracyStarted(player, decision, VisionRules.Conspiracy,
        resolved, automatic, favorReturns)
      after <- evolve(catalog, state, started)
      pending = after.asInstanceOf[Ready].value.game.current.pending.get
        .asInstanceOf[PendingProcedure.Conspiracy]
      result <- if (pending.remainingSecretPlacements == 0) {
        val completed = ConspiracyCompleted(player, decision,
          VisionRules.Conspiracy, pending.target, pending.secretSites,
          pending.favorReturnOrder)
        evolve(catalog, after, completed).map(s => OathTransition(s,
          Vector(started, completed), ActActionSelection(player)))
      } else Right(OathTransition(after, Vector(started),
        AwaitingConspiracyDecision(player, decision)))
    } yield result

    case VisionCommand.ChooseSecretSite(player, decision, site) => state match {
      case Ready(ready) => validatePending(ready, player, decision).flatMap { p =>
        val legal = BannerRules.leastSites(ready.game.current, p.secretSites)
        for {
          _ <- Either.cond(legal.size > 1 && legal.contains(site), (),
            ConspiracyOutcomeMismatch("site is not a tied least-stocked site"))
          chosen = p.secretSites :+ site
          automatic = BannerRules.automaticSitePrefix(ready.game.current, chosen,
            p.remainingSecretPlacements - 1)
          choice = ConspiracySecretSiteChosen(player, decision, site, automatic)
          after <- evolve(catalog, state, choice)
          next = after.asInstanceOf[Ready].value.game.current.pending.get
            .asInstanceOf[PendingProcedure.Conspiracy]
          result <- if (next.remainingSecretPlacements == 0) {
            val completed = ConspiracyCompleted(player, decision, next.source,
              next.target, next.secretSites, next.favorReturnOrder)
            evolve(catalog, after, completed).map(s => OathTransition(s,
              Vector(choice, completed), ActActionSelection(player)))
          } else Right(OathTransition(after, Vector(choice),
            AwaitingConspiracyDecision(player, decision)))
        } yield result
      }
      case _ => Left(GameNotStarted)
    }
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
    } yield Ready(GameStateUpdates.updateCurrent(ready) { c =>
      val players = c.players.map { p => if (p.player != e.playerId) p else
        p.copy(advisers = p.advisers.filterNot(_.id == e.visionId),
          revealedVision = Some(VisionState(e.visionId, Orientation.FaceUp))) }
      val discards = e.replaced.fold(c.commonCards.discard(e.destination))(
        old => c.commonCards.discard(e.destination) :+ old)
      c.copy(players = players, commonCards = c.commonCards.copy(
        regionalDiscards = c.commonCards.regionalDiscards.updated(e.destination, discards)))
    })

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
      count = e.target.collect {
        case ConspiracyTarget.Banner(_, Banner.DarkestSecret) =>
          BannerRules.resources(ready.game.current, Banner.DarkestSecret) / 2
      }.getOrElse(0)
      expected = BannerRules.automaticSitePrefix(ready.game.current, Vector.empty, count)
      expectedFavor = e.target.collect {
        case ConspiracyTarget.Banner(_, Banner.PeoplesFavor) =>
          BannerRules.raidFavorReturn(ready.support.favorBanks,
            BannerRules.resources(ready.game.current, Banner.PeoplesFavor))
      }.getOrElse(Vector.empty)
      _ <- Either.cond(e.automaticSecretSites == expected, (),
        ConspiracyOutcomeMismatch("recorded automatic site placements changed"))
      _ <- Either.cond(e.automaticFavorReturns == expectedFavor, (),
        ConspiracyOutcomeMismatch("recorded automatic favor returns changed"))
    } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
      pending = Some(PendingProcedure.Conspiracy(e.decision, e.playerId,
        e.source, e.target, count - expected.size, expected, expectedFavor)))))

    case e: ConspiracySecretSiteChosen => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision).flatMap { p =>
        val legal = BannerRules.leastSites(ready.game.current, p.secretSites)
        val chosen = p.secretSites :+ e.siteId
        val expected = BannerRules.automaticSitePrefix(ready.game.current, chosen,
          p.remainingSecretPlacements - 1)
        Either.cond(legal.size > 1 && legal.contains(e.siteId) &&
          expected == e.automaticSecretSites, (),
          ConspiracyOutcomeMismatch("recorded site choice is invalid")).map { _ =>
          Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(p.copy(
            remainingSecretPlacements = p.remainingSecretPlacements - 1 - expected.size,
            secretSites = chosen ++ expected)))))
        }
      }
      case _ => Left(GameNotStarted)
    }

    case e: ConspiracyCompleted => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision).flatMap { p =>
        Either.cond(p.remainingSecretPlacements == 0 && e.source == p.source &&
          e.target == p.target && e.secretSites == p.secretSites &&
          e.favorReturnOrder == p.favorReturnOrder, (),
          ConspiracyOutcomeMismatch("recorded completion is stale or invalid")).map { _ =>
          Ready(applyCompletion(ready, p))
        }
      }
      case _ => Left(GameNotStarted)
    }

    case _ => Left(InvalidEventOrder("Visions received an unrelated event"))
  }

  private def applyCompletion(ready: ReadyGame,
      pending: PendingProcedure.Conspiracy): ReadyGame = {
    val current = ready.game.current
    val sitesAdded = pending.secretSites.groupBy(identity).view.mapValues(_.size).toMap
    val priorSecretCount = pending.target.collect {
      case ConspiracyTarget.Banner(_, Banner.DarkestSecret) =>
        current.banners.darkestSecret.secrets
    }.getOrElse(0)
    val players = current.players.map { p =>
      val withoutSource = if (p.player == pending.actor)
        p.copy(advisers = p.advisers.filterNot(_.id == pending.source)) else p
      pending.target match {
        case Some(ConspiracyTarget.Relic(owner, relic)) if p.player == owner =>
          withoutSource.copy(relics = withoutSource.relics.filterNot(_.id == relic))
        case Some(ConspiracyTarget.Relic(owner, relic)) if p.player == pending.actor =>
          val taken = current.players.find(_.player == owner).get.relics.find(_.id == relic).get
          withoutSource.copy(relics = withoutSource.relics :+ taken)
        case Some(ConspiracyTarget.Banner(owner, Banner.DarkestSecret)) if p.player == owner =>
          withoutSource.copy(board = withoutSource.board.copy(faceUpSecrets =
            withoutSource.board.faceUpSecrets + priorSecretCount - pending.secretSites.size))
        case _ => withoutSource
      }
    }
    val sites = sitesAdded.foldLeft(current.map.sites) { case (all, (id, n)) =>
      val site = all(id)
      all.updated(id, site.copy(tokens = site.tokens.copy(secrets = site.tokens.secrets + n)))
    }
    val banners = pending.target match {
      case Some(ConspiracyTarget.Banner(_, Banner.PeoplesFavor)) =>
        current.banners.copy(peoplesFavor = current.banners.peoplesFavor.copy(
          holder = Some(pending.actor), favor = 0))
      case Some(ConspiracyTarget.Banner(_, Banner.DarkestSecret)) =>
        current.banners.copy(darkestSecret = current.banners.darkestSecret.copy(
          holder = Some(pending.actor), secrets = 0))
      case _ => current.banners
    }
    val updated = GameStateUpdates.updateCurrent(ready)(_.copy(players = players,
      map = current.map.copy(sites = sites), banners = banners, pending = None))
    updated.copy(support = updated.support.copy(favorBanks =
      BannerRules.addFavor(updated.support.favorBanks, pending.favorReturnOrder)))
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
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (next, event) => next.flatMap(evolve(catalog, _, event)))
      .map(OathTransition(_, events, continue))
}
