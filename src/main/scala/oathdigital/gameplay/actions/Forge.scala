package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameplayTransition, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.GameStateUpdates.updateCurrent
import oathdigital.gameplay.operations._

sealed trait ForgeCommand extends Product with Serializable
object ForgeCommand {
  final case class Begin(playerId: PlayerId, decision: DecisionId)
      extends ForgeCommand
  final case class Complete(playerId: PlayerId, decision: DecisionId,
      assignments: Vector[ForgeResourceAssignment], relicId: RelicId)
      extends ForgeCommand
}

object Forge {
  /** Validates every non-random completion fact before the application asks
    * its server-owned relic preparation port for the authoritative deck top.
    */
  def prepareComplete(catalog: ExecutableCatalog, state: OathState,
      player: PlayerId, decision: DecisionId,
      assignments: Vector[ForgeResourceAssignment])
      : Either[OathViolation, Unit] = state match {
    case Ready(ready) => validateCompletion(catalog, ready, player, decision,
      assignments).map(_ => ())
    case _ => Left(GameNotStarted)
  }

  def handle(catalog: ExecutableCatalog, state: OathState,
      command: ForgeCommand): Either[OathViolation, OathTransition] = command match {
    case ForgeCommand.Begin(player, decision) => for {
      ready <- OathLifecycle.validateAct(state, player)
      p = ready.game.current.players.find(_.player == player).get
      site <- p.pawnSite.toRight(PawnSiteMissing(player))
      facts <- ForgeRules.validate(catalog, ready, p, site)
      event = ForgeStarted(player, decision, site, facts._1, facts._2, 1)
      next <- transition(catalog, state, Vector(event),
        AwaitingForgeAssignment(player, decision))
    } yield next
    case ForgeCommand.Complete(player, decision, assignments, relic) => state match {
      case Ready(ready) => validateCompletion(catalog, ready, player, decision,
          assignments).flatMap { case (pending, _) =>
        transition(catalog, state, Vector(ForgeCompleted(player, decision,
          pending.site, assignments, relic)), ActActionSelection(player))
      }
      case _ => Left(GameNotStarted)
    }
  }

  private def validateCompletion(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, decision: DecisionId,
      assignments: Vector[ForgeResourceAssignment])
      : Either[OathViolation, (PendingProcedure.Forge, Map[Suit, Int])] =
    validatePending(ready, player, decision).flatMap { f =>
      val expectedResources = Vector.fill(f.cost.favor)(ForgeResource.Favor) ++
        Vector.fill(f.cost.secrets)(ForgeResource.Secret)
      val actualTargets = assignments.map(_.target)
      val favorBySuit = assignments.collect {
        case a if a.resource == ForgeResource.Favor =>
          catalog.denizens.find(_.id.value == a.target.denizenId.value)
            .flatMap(d => Suit.all.find(_.key == d.suit.value))
            .toRight(ForgeOutcomeMismatch(
              s"no catalog suit for ${a.target.denizenId.value}"))
      }.foldLeft[Either[OathViolation, Map[Suit, Int]]](Right(Map.empty)) {
        case (acc, next) => for {
          counts <- acc
          suit <- next
        } yield counts.updated(suit, counts.getOrElse(suit, 0) + 1)
      }
      for {
        _ <- Either.cond(assignments.size == 3 && actualTargets.distinct.size == 3,
          (), ForgeOutcomeMismatch("assign exactly one resource to each of three distinct denizens"))
        _ <- Either.cond(actualTargets.toSet == f.eligibleTargets.toSet,
          (), ForgeOutcomeMismatch("assignment targets are stale or ineligible"))
        _ <- Either.cond(assignments.map(_.resource).sortBy(_.key) ==
          expectedResources.sortBy(_.key), (), ForgeOutcomeMismatch(
          "assignments do not match the printed Forge resources"))
        favorNeeded <- favorBySuit
        _ <- favorNeeded.toVector.foldLeft[Either[OathViolation, Unit]](Right(())) {
          case (result, (suit, needed)) => result.flatMap(_ => Either.cond(
            ready.banks.favor.getOrElse(suit, 0) >= needed, (),
            ForgeUnavailable(s"$suit favor bank lacks $needed favor")))
        }
      } yield f -> favorNeeded
    }

  private def validatePending(ready: ReadyGame, player: PlayerId,
      decision: DecisionId): Either[OathViolation, PendingProcedure.Forge] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player) Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act) Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(f: PendingProcedure.Forge) if f.actor != player => Left(WrongPlayer(f.actor, player))
      case Some(f: PendingProcedure.Forge) if f.decision != decision => Left(ForgeDecisionMismatch(f.decision, decision))
      case Some(f: PendingProcedure.Forge) => Right(f)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Forge procedure is pending"))
    }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathState] = event match {
    case e: ForgeStarted => for {
      ready <- OathLifecycle.validateAct(state, e.playerId)
      p = ready.game.current.players.find(_.player == e.playerId).get
      site <- p.pawnSite.toRight(PawnSiteMissing(e.playerId))
      facts <- ForgeRules.validate(catalog, ready, p, site)
      _ <- Either.cond(e.siteId == site, (), ForgeOutcomeMismatch("recorded site is not the pawn site"))
      _ <- Either.cond(e.targets == facts._1, (), ForgeOutcomeMismatch("recorded targets are not the exact eligible denizens"))
      _ <- Either.cond(e.cost == facts._2, (), ForgeOutcomeMismatch("recorded printed Forge cost is invalid"))
      _ <- Either.cond(e.supplySpent == 1, (), ForgeOutcomeMismatch("Forge must spend exactly 1 Supply"))
    } yield Ready(updateCurrent(ready) { current =>
      current.copy(players = current.players.map(x => if (x.player != e.playerId) x else
        x.copy(board = x.board.copy(supply = SupplyTrack(x.board.supply.supply - 1)))),
        pending = Some(PendingProcedure.Forge(e.decision, e.playerId, site,
          e.targets, e.cost, e.supplySpent)))
    })
    case e: ForgeCompleted => state match {
      case Ready(ready) => validateCompletion(catalog, ready, e.playerId,
          e.decision, e.assignments).flatMap { case (f, _) =>
        val current = ready.game.current
        for {
          _ <- Either.cond(e.siteId == f.site, (), ForgeOutcomeMismatch("recorded Forge site changed"))
          _ <- Either.cond(current.commonCards.relicDeck.headOption.contains(e.relicId),
            (), ForgeOutcomeMismatch("recorded relic is not the authoritative deck top"))
          operations <- completionOperations(catalog, e)
          executor = new OperationExecutor(OperationPolicy.exact(
            operations, "Forge semantic root is not permitted"))
          execution <- OperationTransaction.evolve(
            ready, operations, executor)(evolved =>
              Right(updateCurrent(evolved)(_.copy(pending = None))))
        } yield Ready(execution)
      }
      case _ => Left(GameNotStarted)
    }
    case _ => Left(InvalidEventOrder("Forge received a non-Forge event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue) =
    GameplayTransition(state, events, continue)(evolve(catalog, _, _))

  private def completionOperations(
      catalog: ExecutableCatalog,
      event: ForgeCompleted
  ): Either[OathViolation, Vector[CoreOperation]] =
    event.assignments.foldLeft[
      Either[OathViolation, Vector[CoreOperation]]](Right(Vector.empty)) {
      case (result, assignment) => for {
        operations <- result
        operation <- assignment.resource match {
          case ForgeResource.Favor => suitOf(catalog,
            assignment.target.denizenId).toRight(ForgeOutcomeMismatch(
              s"no catalog suit for ${assignment.target.denizenId.value}"))
            .map(suit => Move(
              Piece.Favor(1),
              PositionedLocation(Location.FavorBank(suit)),
              PositionedLocation(Location.OnCard(
                assignment.target.denizenId))))
          case ForgeResource.Secret => Right(Move(
            Piece.Secrets(1),
            PositionedLocation(Location.SharedBank),
            PositionedLocation(Location.OnCard(
              assignment.target.denizenId))))
        }
      } yield operations :+ operation
    }.map(_ :+ Play(
      event.relicId,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(event.playerId),
      Orientation.FaceDown))

  private def suitOf(
      catalog: ExecutableCatalog,
      denizen: DenizenId
  ): Option[Suit] = catalog.denizens.find(_.id.value == denizen.value)
    .flatMap(definition => Suit.all.find(_.key == definition.suit.value))
}

object ForgeRules {
  /** The complete pre-release component corpus was audited against CR p.25 / NF
    * p.14 and contains no handler that changes the base Forge procedure. The
    * exact handler vocabulary is pinned: an added/changed handler makes active
    * component powers conservative blockers until explicitly re-audited.
    */
  def validate(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerState,
      siteId: SiteId): Either[OathViolation, (Vector[SiteDenizenTarget], Tokens)] = {
    val game = ready.game
    val definition = catalog.sites.find(_.id == siteId)
    val site = game.current.map.sites.get(siteId)
    val blocked =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Forge is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty)) Some("altered Foundations are not supported for Forge")
      else None
    blocked.map(UnsupportedForgeState).toLeft(()).flatMap(_ =>
      PowerRuntime.requireAudited(catalog)).flatMap { _ =>
      for {
        s <- site.toRight(SiteNotInPlay(siteId))
        ruled <- SiteRule.ruledBy(s.forces, game.current.players, player.player)
          .left.map(x => ForgeUnavailable(s"cannot derive site ruler: $x"))
        _ <- Either.cond(ruled, (), ForgeUnavailable("actor does not rule their pawn site"))
        cost <- definition.flatMap(_.forgeRequirements)
          .toRight(ForgeUnavailable("site has no printed Forge cost"))
        empty = s.denizens.collect { case d: DenizenState if d.orientation == Orientation.FaceUp && d.tokens == Tokens.empty =>
          SiteDenizenTarget(siteId, d.id) }
        _ <- Either.cond(empty.size == 3, (), ForgeUnavailable("site must contain exactly three empty denizens"))
        _ <- Either.cond(cost.favor + cost.secrets == 3, (), ForgeUnavailable("printed Forge cost must contain three resources"))
        _ <- Either.cond(player.board.supply.supply >= 1, (), InsufficientSupply(1, player.board.supply.supply))
        _ <- Either.cond(game.current.commonCards.relicDeck.nonEmpty, (), ForgeUnavailable("relic deck is empty"))
      } yield empty -> cost
    }
  }
}
