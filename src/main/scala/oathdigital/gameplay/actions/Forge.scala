package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathContinue._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

sealed trait ForgeCommand extends Product with Serializable
object ForgeCommand {
  final case class Begin(playerId: PlayerId, decision: DecisionId)
      extends ForgeCommand
  final case class Complete(playerId: PlayerId, decision: DecisionId,
      assignments: Vector[ForgeResourceAssignment], relicId: RelicId)
      extends ForgeCommand
}

object Forge {
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
      case Ready(ready) => validatePending(ready, player, decision).flatMap { pending =>
        transition(catalog, state, Vector(ForgeCompleted(player, decision,
          pending.site, assignments, relic)), ActActionSelection(player))
      }
      case _ => Left(GameNotStarted)
    }
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
    } yield Ready(GameStateUpdates.updateCurrent(ready) { current =>
      current.copy(players = current.players.map(x => if (x.player != e.playerId) x else
        x.copy(board = x.board.copy(supply = SupplyTrack(x.board.supply.supply - 1)))),
        pending = Some(PendingProcedure.Forge(e.decision, e.playerId, site,
          e.targets, e.cost, e.supplySpent)))
    })
    case e: ForgeCompleted => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision).flatMap { f =>
        val current = ready.game.current
        val expectedResources = Vector.fill(f.cost.favor)(ForgeResource.Favor) ++
          Vector.fill(f.cost.secrets)(ForgeResource.Secret)
        val actualTargets = e.assignments.map(_.target)
        val favorBySuit = e.assignments.collect {
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
          _ <- Either.cond(e.siteId == f.site, (), ForgeOutcomeMismatch("recorded Forge site changed"))
          _ <- Either.cond(e.assignments.size == 3 && actualTargets.distinct.size == 3,
            (), ForgeOutcomeMismatch("assign exactly one resource to each of three distinct denizens"))
          _ <- Either.cond(actualTargets.toSet == f.eligibleTargets.toSet,
            (), ForgeOutcomeMismatch("assignment targets are stale or ineligible"))
          _ <- Either.cond(e.assignments.map(_.resource).sortBy(_.key) == expectedResources.sortBy(_.key),
            (), ForgeOutcomeMismatch("assignments do not match the printed Forge resources"))
          _ <- Either.cond(current.commonCards.relicDeck.headOption.contains(e.relicId),
            (), ForgeOutcomeMismatch("recorded relic is not the authoritative deck top"))
          favorNeeded <- favorBySuit
          _ <- favorNeeded.toVector.foldLeft[Either[OathViolation, Unit]](Right(())) {
            case (result, (suit, needed)) => result.flatMap(_ =>
              Either.cond(ready.support.favorBanks.getOrElse(suit, 0) >= needed,
                (), ForgeUnavailable(s"$suit favor bank lacks $needed favor")))
          }
          site <- current.map.sites.get(f.site).toRight(SiteNotInPlay(f.site))
        } yield {
          val byId = e.assignments.map(a => a.target.denizenId -> a.resource).toMap
          val denizens = site.denizens.map {
            case d: DenizenState if byId.contains(d.id) =>
              val add = byId(d.id) match {
                case ForgeResource.Favor => Tokens(1, 0)
                case ForgeResource.Secret => Tokens(0, 1)
              }
              d.copy(tokens = Tokens(d.tokens.favor + add.favor, d.tokens.secrets + add.secrets))
            case other => other
          }
          val updated = GameStateUpdates.updateCurrent(ready)(_.copy(
            map = current.map.copy(sites = current.map.sites.updated(f.site,
              site.copy(denizens = denizens))),
            commonCards = current.commonCards.copy(relicDeck = current.commonCards.relicDeck.tail),
            players = current.players.map(p => if (p.player != e.playerId) p else
              p.copy(relics = p.relics :+ RelicState(e.relicId, Orientation.FaceDown, Tokens.empty))),
            pending = None))
          Ready(updated.copy(support = updated.support.copy(favorBanks =
            favorNeeded.foldLeft(updated.support.favorBanks) {
              case (banks, (suit, amount)) =>
                banks.updated(suit, banks.getOrElse(suit, 0) - amount)
            })))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case _ => Left(InvalidEventOrder("Forge received a non-Forge event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue) =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (s, e) => s.flatMap(evolve(catalog, _, e))).map(OathTransition(_, events, continue))
}

object ForgeRules {
  private def forgeHandlers(ids: Vector[String]): Vector[String] =
    ids.filter(id => id.split("[.-]").contains("forge"))

  def validate(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerState,
      siteId: SiteId): Either[OathViolation, (Vector[SiteDenizenTarget], Tokens)] = {
    val game = ready.game
    val definition = catalog.sites.find(_.id == siteId)
    val site = game.current.map.sites.get(siteId)
    val blocked =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Forge is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty)) Some("altered Foundations are not supported for Forge")
      else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active))) Some("active legacy Forge modifiers are not supported")
      else if (definition.exists(d => forgeHandlers(d.handlers).nonEmpty)) Some("site Forge handlers are not implemented")
      else if (player.advisers.exists {
        case d: DenizenState if d.orientation == Orientation.FaceUp =>
          catalog.denizens.find(_.id.value == d.id.value)
            .exists(x => forgeHandlers(x.handlers).nonEmpty)
        case _ => false
      }) Some("active adviser Forge handlers are not implemented")
      else if (player.relics.exists(r => r.orientation == Orientation.FaceUp &&
          catalog.relics.find(_.id.value == r.id.value)
            .exists(x => forgeHandlers(x.handlers).nonEmpty)))
        Some("active relic Forge handlers are not implemented")
      else if (site.exists(_.denizens.exists {
        case d: DenizenState if d.orientation == Orientation.FaceUp =>
          catalog.denizens.find(_.id.value == d.id.value)
            .exists(x => forgeHandlers(x.handlers).nonEmpty)
        case e: EdificeState if e.side == EdificeSide.Intact =>
          catalog.edifices.find(_.id.value == e.id.value)
            .exists(x => forgeHandlers(x.intact.handlers).nonEmpty)
        case _ => false
      })) Some("active site-card Forge handlers are not implemented")
      else None
    blocked.map(UnsupportedForgeState).toLeft(()).flatMap { _ =>
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
