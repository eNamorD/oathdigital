package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{CampaignTimingWindow, GameStateUpdates, OathLifecycle,
  RuleActivation, RuleOutcome, RuleQueryContext, RuleSourceRef, TypedRuleHandler,
  RuleRegistry}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathContinue._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

sealed trait CampaignCommand extends Product with Serializable
object CampaignCommand {
  final case class Start(playerId: PlayerId, decision: DecisionId, targetSites: Vector[SiteId],
      force: Int) extends CampaignCommand
  object Start {
    def apply(playerId: PlayerId, decision: DecisionId, siteId: SiteId,
        force: Int): Start = new Start(playerId, decision, Vector(siteId), force)
  }
  final case class ChoosePlan(playerId: PlayerId, decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource) extends CampaignCommand
  final case class FinishPlans(playerId: PlayerId, decision: DecisionId,
      attackDice: Vector[AttackDieFace]) extends CampaignCommand
  final case class Sacrifice(playerId: PlayerId, decision: DecisionId, count: Int,
      defenseDice: Vector[DefenseDieFace]) extends CampaignCommand
  final case class Place(playerId: PlayerId, decision: DecisionId,
      allocations: Vector[CampaignForceAllocation])
      extends CampaignCommand
}

trait CampaignLosingForceResolver {
  def id: String
  def resolve(ready: ReadyGame, campaign: PendingProcedure.Campaign)
      : Either[OathViolation, Vector[CampaignLosingForceEffect]]
  def resolveAttackerDefeat(ready: ReadyGame,
      campaign: PendingProcedure.Campaign, surviving: Int)
      : Either[OathViolation, Vector[CampaignLosingForceEffect]] =
    Left(CampaignOutcomeMismatch(
      s"losing-force policy '$id' does not resolve attacker defeat"))
}
object CampaignLosingForceResolver {
  val default: CampaignLosingForceResolver =
    new CampaignLosingForceResolver {
      val id = "campaign.loss.default-defeated-force"
      def resolve(ready: ReadyGame, campaign: PendingProcedure.Campaign) = {
        val removed = campaign.targetSites.foldLeft[
          Either[OathViolation, Vector[CampaignLosingForceEffect]]](
          Right(Vector.empty)) { (result, siteId) => result.flatMap { effects =>
            ready.game.current.map.sites.get(siteId).toRight(
              SiteNotInPlay(siteId)).flatMap(_.forces match {
                case SiteForces.Occupied(force, count) if count > 0 =>
                  Right(effects :+ CampaignLosingForceEffect.Remove(
                    siteId, force, count))
                case other => Left(CampaignOutcomeMismatch(
                  s"target '${siteId.value}' has unsupported losing force $other"))
              })
          }}
        removed.map { effects => campaign.defender match {
          case CampaignDefender.Bandits => effects
          case CampaignDefender.Player(player) =>
            val total = effects.collect {
              case CampaignLosingForceEffect.Remove(_, _, count) => count
            }.sum
            val returned = total - total / 2
            if (returned == 0) effects else effects :+
              CampaignLosingForceEffect.ReturnToBoard(
                campaign.targetSites.head, player,
                effects.collectFirst {
                  case CampaignLosingForceEffect.Remove(_, force, _) => force
                }.get, returned)
        }}
      }
      override def resolveAttackerDefeat(ready: ReadyGame,
          campaign: PendingProcedure.Campaign, surviving: Int) = {
        val owner = ready.game.current.players.find(
          _.player == campaign.actor).get
        val force = ForceKind.Exile(owner.lineage)
        val killed = surviving / 2
        val returned = surviving - killed
        Right(Vector(
          Option.when(killed > 0)(CampaignLosingForceEffect.KillCommitted(
            campaign.targetSites.head, campaign.actor, force, killed)),
          Option.when(returned > 0)(CampaignLosingForceEffect.ReturnToBoard(
            campaign.targetSites.head, campaign.actor, force, returned))
        ).flatten)
      }
    }

  val removeAllBandits: CampaignLosingForceResolver = default
}

final case class CampaignLosingForceRegistry(
    selected: CampaignLosingForceResolver,
    resolvers: Vector[CampaignLosingForceResolver]
) {
  require(resolvers.map(_.id).distinct.size == resolvers.size,
    "Campaign losing-force policy IDs must be unique")
  require(resolvers.exists(_.id == selected.id),
    "selected Campaign losing-force policy must be registered")
  def byId(id: String): Option[CampaignLosingForceResolver] =
    resolvers.find(_.id == id)
}
object CampaignLosingForceRegistry {
  val default: CampaignLosingForceRegistry = CampaignLosingForceRegistry(
    CampaignLosingForceResolver.default,
    Vector(CampaignLosingForceResolver.default))
}

object Campaign {
  val SupplyCost = 2
  val MinimumForce = 0

  /** Validates every non-random fact needed to choose an attacker plan. The
    * application boundary must call this before preparing physical dice.
    */
  def prepareFinishPlans(catalog: ExecutableCatalog, state: OathState,
      player: PlayerId, decision: DecisionId)
      : Either[OathViolation, Int] = state match {
    case Ready(ready) => validatePending(ready, player, decision,
      requireFinished = false, requireResolved = false).flatMap { pending =>
      CampaignRules.validateSelectedPlans(catalog, ready, pending)
        .map(plans => pending.force + plans.map(_.addedAttackDice).sum)
    }
    case _ => Left(GameNotStarted)
  }

  def handle(catalog: ExecutableCatalog, state: OathState, command: CampaignCommand)
      : Either[OathViolation, OathTransition] =
    handle(catalog, state, command, CampaignLosingForceRegistry.default)

  private[gameplay] def handle(catalog: ExecutableCatalog, state: OathState,
      command: CampaignCommand, losingForceRegistry: CampaignLosingForceRegistry)
      : Either[OathViolation, OathTransition] = command match {
    case CampaignCommand.Start(player, decision, sites, force) =>
      OathLifecycle.validateAct(state, player).flatMap { ready =>
        CampaignRules.validateStart(catalog, ready, player, sites, force).flatMap { defender =>
          transition(catalog, state, Vector(CampaignStarted(player, decision, sites,
            defender, SupplyCost, force)), AwaitingCampaignPlan(player, decision))
        }
      }
    case CampaignCommand.ChoosePlan(player, decision, source) => state match {
      case Ready(ready) => validatePending(ready, player, decision, requireFinished = false,
          requireResolved = false).flatMap { pending =>
        CampaignRules.validatePlanChoice(catalog, ready, pending, source).flatMap { choice =>
          transition(catalog, state, Vector(CampaignPlanChosen(player, decision,
            choice.source, choice.handlerId, choice.favorCost, choice.secretCost,
            choice.revealed, choice.ignoreAttackSkulls, choice.addedAttackDice)),
            AwaitingCampaignPlan(player, decision))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case CampaignCommand.FinishPlans(player, decision, dice) => state match {
      case Ready(ready) => validatePending(ready, player, decision,
          requireFinished = false, requireResolved = false).flatMap { pending =>
        CampaignRules.validateSelectedPlans(catalog, ready, pending).flatMap { plans =>
          val ignoreSkulls = plans.exists(_.ignoreAttackSkulls)
          val addedDice = plans.map(_.addedAttackDice).sum
          val (attack, skulls) = CampaignRules.attackResult(
            dice, pending.force, ignoreSkulls)
          transition(catalog, state, Vector(CampaignPlansFinished(player, decision,
            plans.map(_.source), addedDice, ignoreSkulls, dice, attack, skulls)),
            AwaitingCampaignSacrifice(player, decision))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case CampaignCommand.Sacrifice(player, decision, count, dice) => state match {
      case Ready(ready) => validatePending(ready, player, decision,
        requireFinished = true, requireResolved = false)
        .flatMap { pending =>
          val attack = pending.attack + count
          val defense = DefenseDieFace.score(dice) +
            CampaignRules.defenderForce(ready, pending.targetSites)
          val victorious = attack > defense
          val surviving = pending.force - pending.skullLosses - count
          val losses = if (victorious) Right(Vector.empty) else
            losingForceRegistry.selected.resolveAttackerDefeat(
              ready, pending, surviving)
          losses.flatMap(result => transition(catalog, state,
            Vector(CampaignSacrificed(player, decision, count, dice, attack,
              defense, pending.skullLosses, victorious,
              Option.when(!victorious)(losingForceRegistry.selected.id), result)),
            if (victorious) AwaitingCampaignPlacement(player, decision)
            else ActActionSelection(player), losingForceRegistry))
        }
      case _ => Left(GameNotStarted)
    }
    case CampaignCommand.Place(player, decision, allocations) => state match {
      case Ready(ready) => validatePending(ready, player, decision, requireResolved = true)
        .flatMap(p => losingForceRegistry.selected.resolve(ready, p)
          .flatMap(losses => transition(catalog, state, Vector(CampaignConquered(
            player, decision, losingForceRegistry.selected.id, losses,
            allocations)), ActActionSelection(player), losingForceRegistry)))
      case _ => Left(GameNotStarted)
    }
  }

  private def validatePending(ready: ReadyGame, player: PlayerId,
      decision: DecisionId, requireFinished: Boolean = true, requireResolved: Boolean)
      : Either[OathViolation, PendingProcedure.Campaign] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player) Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act) Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(c: PendingProcedure.Campaign) if c.actor != player => Left(WrongPlayer(c.actor, player))
      case Some(c: PendingProcedure.Campaign) if c.decision != decision => Left(CampaignDecisionMismatch(c.decision, decision))
      case Some(c: PendingProcedure.Campaign) if requireFinished && !c.plansFinished =>
        Left(CampaignOutcomeMismatch("Campaign is awaiting attacker battle plans"))
      case Some(c: PendingProcedure.Campaign) if !requireFinished && c.plansFinished =>
        Left(CampaignOutcomeMismatch("Campaign attacker battle plans are finished"))
      case Some(c: PendingProcedure.Campaign) if requireResolved && !c.victorious.contains(true) => Left(CampaignOutcomeMismatch("Campaign is not awaiting conquest placement"))
      case Some(c: PendingProcedure.Campaign) if !requireResolved && c.victorious.nonEmpty => Left(CampaignOutcomeMismatch("Campaign battle is already resolved"))
      case Some(c: PendingProcedure.Campaign) => Right(c)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Campaign procedure is pending"))
    }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] =
    evolve(catalog, state, event, CampaignLosingForceRegistry.default)

  private[gameplay] def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent, losingForceRegistry: CampaignLosingForceRegistry)
      : Either[OathViolation, OathState] = event match {
    case e: CampaignStarted => OathLifecycle.validateAct(state, e.playerId).flatMap { ready =>
      for {
        defender <- CampaignRules.validateStart(catalog, ready, e.playerId,
          e.targetSites, e.force)
        _ <- Either.cond(defender == e.defender, (), CampaignOutcomeMismatch(
          "recorded Campaign defender is invalid"))
        _ <- if (e.supplySpent == SupplyCost) Right(()) else Left(CampaignOutcomeMismatch("recorded Supply cost is invalid"))
      } yield {
        val current = ready.game.current
        Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
          players = current.players.map(p => if (p.player != e.playerId) p else
            p.copy(board = p.board.copy(warbands = p.board.warbands - e.force,
              supply = SupplyTrack(p.board.supply.supply - SupplyCost)))),
          pending = Some(PendingProcedure.Campaign(e.decision, e.playerId, e.targetSites,
            e.defender,
            e.force, Vector.empty, plansFinished = false, Vector.empty, 0, 0,
            None, Vector.empty, None, None)))))
      }
    }
    case e: CampaignPlanChosen => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = false, requireResolved = false).flatMap { c =>
        for {
          expected <- CampaignRules.validatePlanChoice(catalog, ready, c, e.source)
          _ <- if (e.handlerId == expected.handlerId &&
            e.favorCost == expected.favorCost && e.secretCost == expected.secretCost &&
            e.revealed == expected.revealed &&
            e.ignoreAttackSkulls == expected.ignoreAttackSkulls &&
            e.addedAttackDice == expected.addedAttackDice) Right(())
            else Left(CampaignOutcomeMismatch("recorded attacker plan result is invalid"))
        } yield {
          val resolution = PendingProcedure.CampaignPlanResolution(e.source,
            e.handlerId, e.favorCost, e.secretCost, e.revealed,
            e.ignoreAttackSkulls, e.addedAttackDice)
          val current = ready.game.current
          Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            players = current.players.map { p =>
              if (p.player != e.playerId) p
              else p.copy(
                board = p.board.copy(favor = p.board.favor - e.favorCost,
                  faceUpSecrets = p.board.faceUpSecrets - e.secretCost),
                advisers = p.advisers.map {
                  case d: DenizenState if e.revealed && e.source ==
                    PendingProcedure.CampaignPlanSource.Adviser(e.playerId, d.id) =>
                    d.copy(orientation = Orientation.FaceUp)
                  case other => other
                },
                relics = p.relics.map {
                  case r if e.source == PendingProcedure.CampaignPlanSource.Relic(
                    e.playerId, r.id) => r.copy(tokens = Tokens(
                      r.tokens.favor + e.favorCost, r.tokens.secrets + e.secretCost))
                  case other => other
                })
            },
            map = current.map.copy(sites = current.map.sites.map {
              case (siteId, site) => siteId -> site.copy(denizens = site.denizens.map {
                case d: DenizenState if e.revealed && e.source ==
                    PendingProcedure.CampaignPlanSource.SiteCard(siteId, d.id) =>
                  d.copy(orientation = Orientation.FaceUp)
                case other => other
              })
            }),
            pending = Some(c.copy(plans = c.plans :+ resolution)))))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignPlansFinished => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = false, requireResolved = false).flatMap { c =>
        for {
          expected <- CampaignRules.validateSelectedPlans(catalog, ready, c)
          expectedSources = expected.map(_.source)
          expectedAdded = expected.map(_.addedAttackDice).sum
          expectedIgnore = expected.exists(_.ignoreAttackSkulls)
          _ <- Either.cond(e.orderedSources == expectedSources &&
            e.addedAttackDice == expectedAdded &&
            e.ignoreAttackSkulls == expectedIgnore, (),
            CampaignOutcomeMismatch("recorded attacker plan order or result is invalid"))
          _ <- Either.cond(e.attackDice.size == c.force + expectedAdded, (),
            CampaignOutcomeMismatch("attack dice count is invalid"))
          expectedResult = CampaignRules.attackResult(
            e.attackDice, c.force, expectedIgnore)
          _ <- Either.cond((e.attack, e.skullLosses) == expectedResult, (),
            CampaignOutcomeMismatch("recorded attack roll result is invalid"))
        } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
          pending = Some(c.copy(plansFinished = true, attackDice = e.attackDice,
            attack = e.attack, skullLosses = e.skullLosses)))))
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignSacrificed => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = true, requireResolved = false).flatMap { c =>
        val remaining = c.force - c.skullLosses
        val expectedAttack = c.attack + e.sacrificed
        val expectedDefense = DefenseDieFace.score(e.defenseDice) +
          CampaignRules.defenderForce(ready, c.targetSites)
        for {
          _ <- if (e.sacrificed >= 0 && e.sacrificed <= remaining) Right(()) else Left(CampaignOutcomeMismatch("sacrifice exceeds surviving force"))
          _ <- if (e.skullLosses == c.skullLosses) Right(()) else Left(CampaignOutcomeMismatch("recorded skull losses are invalid"))
          defenseDiceCount = c.targetSites.flatMap(
            CampaignRules.siteDefinition(catalog, _)).map(_.defense).sum
          _ <- if (e.defenseDice.size == defenseDiceCount) Right(()) else Left(CampaignOutcomeMismatch("defense dice count is invalid"))
          _ <- if (e.attack == expectedAttack && e.defense == expectedDefense && e.victorious == (expectedAttack > expectedDefense)) Right(())
            else Left(CampaignOutcomeMismatch("recorded battle outcome is invalid"))
          expectedLosses <- if (e.victorious) Right(Vector.empty)
            else e.losingForcePolicyId.flatMap(losingForceRegistry.byId)
              .toRight(CampaignOutcomeMismatch(
                "unknown attacker losing-force policy")).flatMap(
                _.resolveAttackerDefeat(ready, c, remaining - e.sacrificed))
          _ <- Either.cond(e.losingForces == expectedLosses &&
            (e.victorious == e.losingForcePolicyId.isEmpty), (),
            CampaignOutcomeMismatch(
              "recorded attacker losing-force resolution is invalid"))
          resolved <- if (e.victorious) Right(
            ready.game.current.players -> ready.game.current.map.sites)
            else applyCommittedLosses(ready.game.current.players,
              ready.game.current.map.sites, c, remaining - e.sacrificed,
              e.losingForces)
        } yield {
          val current = ready.game.current
          if (e.victorious) Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            pending = Some(c.copy(sacrificed = Some(e.sacrificed), defenseDice = e.defenseDice,
              defense = Some(e.defense), victorious = Some(true))))))
          else {
            Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
              players = resolved._1,
              map = current.map.copy(sites = resolved._2), pending = None)))
          }
        }
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignConquered => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = true, requireResolved = true).flatMap { c =>
        val surviving = c.force - c.skullLosses - c.sacrificed.get
        val allocationSites = e.allocations.map(_.site)
        val canonicalAllocations = c.targetSites.map(site => CampaignForceAllocation(
          site, e.allocations.find(_.site == site).map(_.count).getOrElse(0)))
        losingForceRegistry.byId(e.losingForcePolicyId).toRight(
          CampaignOutcomeMismatch("unknown losing-force policy")).flatMap(
          _.resolve(ready, c)).flatMap {
          expectedLosses =>
        if (e.losingForces != expectedLosses) Left(CampaignOutcomeMismatch(
          "recorded losing-force resolution is invalid"))
        else if (allocationSites.distinct.size != allocationSites.size ||
            allocationSites.exists(site => !c.targetSites.contains(site)))
          Left(CampaignOutcomeMismatch(
            "placement sites must be unique Campaign targets"))
        else if (e.allocations != canonicalAllocations)
          Left(CampaignOutcomeMismatch(
            "placements must include every target in canonical order"))
        else if (e.allocations.map(_.count).sum > surviving)
          Left(CampaignOutcomeMismatch("placed force exceeds survivors"))
        else applyLosingForces(ready.game.current.map.sites,
            ready.game.current.players,
            e.losingForces).flatMap { resolvedSites =>
          val blockedPlacement = e.allocations.exists(allocation =>
            allocation.count > 0 && resolvedSites._1(allocation.site).forces !=
              SiteForces.Empty)
          if (blockedPlacement) Left(CampaignOutcomeMismatch(
            "force can be placed only at a cleared Campaign target"))
          else {
          val current = ready.game.current
          val player = current.players.find(_.player == e.playerId).get
          val placed = e.allocations.map(_.count).sum
          val sites = e.allocations.foldLeft(resolvedSites._1) {
            case (updated, CampaignForceAllocation(siteId, count)) =>
              if (count == 0) updated else updated.updated(siteId,
                updated(siteId).copy(forces = SiteForces.Occupied(
                  ForceKind.Exile(player.lineage), count)))
          }
          Right(Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            players = resolvedSites._2.map(p => if (p.player != e.playerId) p else
              p.copy(board = p.board.copy(warbands =
                p.board.warbands + surviving - placed))),
            map = current.map.copy(sites = sites),
            pending = None))))
          }
        }
        }
      }
      case _ => Left(GameNotStarted)
    }
    case _ => Left(InvalidEventOrder("Campaign received a non-Campaign event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue,
      losingForceRegistry: CampaignLosingForceRegistry =
        CampaignLosingForceRegistry.default) =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (s, e) => s.flatMap(evolve(catalog, _, e, losingForceRegistry)))
      .map(OathTransition(_, events, continue))

  private def applyLosingForces(initial: Map[SiteId, SiteState],
      initialPlayers: Vector[PlayerState],
      effects: Vector[CampaignLosingForceEffect])
      : Either[OathViolation, (Map[SiteId, SiteState], Vector[PlayerState])] =
    effects.foldLeft[Either[OathViolation,
      (Map[SiteId, SiteState], Vector[PlayerState])]](
      Right(initial -> initialPlayers)) { (result, effect) => result.flatMap {
        case (sites, players) =>
        def source(force: ForceKind, count: Int) = sites.get(effect.site)
          .toRight(SiteNotInPlay(effect.site)).flatMap { site =>
            Either.cond(site.forces == SiteForces.Occupied(force, count), site,
              CampaignOutcomeMismatch(
                s"losing force at '${effect.site.value}' changed"))
          }
        effect match {
          case CampaignLosingForceEffect.Remove(site, force, count) =>
            source(force, count).map(value => sites.updated(site,
              value.copy(forces = SiteForces.Empty)) -> players)
          case CampaignLosingForceEffect.Preserve(_, force, count) =>
            source(force, count).map(_ => sites -> players)
          case CampaignLosingForceEffect.Relocate(site, destination, force, count) =>
            for {
              from <- source(force, count)
              to <- sites.get(destination).toRight(SiteNotInPlay(destination))
              moved <- to.forces match {
                case SiteForces.Empty => Right(SiteForces.Occupied(force, count))
                case SiteForces.Occupied(existing, existingCount)
                    if existing == force =>
                  Right(SiteForces.Occupied(force, existingCount + count))
                case _ => Left(CampaignOutcomeMismatch(
                  "relocated losing force cannot join a different force"))
              }
            } yield sites.updated(site, from.copy(forces = SiteForces.Empty))
              .updated(destination, to.copy(forces = moved)) -> players
          case CampaignLosingForceEffect.Replace(site, force, count,
              replacement, replacementCount) => source(force, count).map { value =>
            val next = replacement.fold[SiteForces](SiteForces.Empty)(kind =>
              SiteForces.Occupied(kind, replacementCount))
            sites.updated(site, value.copy(forces = next)) -> players
          }
          case CampaignLosingForceEffect.ReturnToBoard(_, player, force, count) =>
            players.find(_.player == player).toRight(CampaignOutcomeMismatch(
              "losing-force return references an unknown player")).flatMap { owner =>
              Either.cond(force == ForceKind.Exile(owner.lineage), (),
                CampaignOutcomeMismatch("returned force does not belong to player"))
              .map(_ => sites -> players.map(p => if (p.player != player) p else
                p.copy(board = p.board.copy(warbands = p.board.warbands + count))))
            }
          case _: CampaignLosingForceEffect.KillCommitted |
              _: CampaignLosingForceEffect.RelocateCommitted |
              _: CampaignLosingForceEffect.PreserveCommitted =>
            Left(CampaignOutcomeMismatch(
              "committed-force disposition cannot resolve defender forces"))
      }}}

  private def applyCommittedLosses(players: Vector[PlayerState],
      sites: Map[SiteId, SiteState], campaign: PendingProcedure.Campaign,
      surviving: Int, effects: Vector[CampaignLosingForceEffect])
      : Either[OathViolation, (Vector[PlayerState], Map[SiteId, SiteState])] = {
    val owner = players.find(_.player == campaign.actor).get
    val expectedForce = ForceKind.Exile(owner.lineage)
    effects.foldLeft[Either[OathViolation,
      (Int, Int, Map[SiteId, SiteState])]](Right((0, 0, sites))) {
      case (result, effect) => result.flatMap {
        case (removed, returned, currentSites) => effect match {
          case CampaignLosingForceEffect.KillCommitted(_, player, force, count)
              if player == campaign.actor && force == expectedForce =>
            Right((removed + count, returned, currentSites))
          case CampaignLosingForceEffect.ReturnToBoard(_, player, force, count)
              if player == campaign.actor && force == expectedForce =>
            Right((removed, returned + count, currentSites))
          case CampaignLosingForceEffect.PreserveCommitted(_, player, force, count)
              if player == campaign.actor && force == expectedForce =>
            Right((removed, returned + count, currentSites))
          case CampaignLosingForceEffect.RelocateCommitted(site, player, force, count)
              if player == campaign.actor && force == expectedForce =>
            currentSites.get(site).toRight(SiteNotInPlay(site)).flatMap {
              destination => destination.forces match {
                case SiteForces.Empty => Right((removed + count, returned,
                  currentSites.updated(site, destination.copy(forces =
                    SiteForces.Occupied(force, count)))))
                case SiteForces.Occupied(existing, present) if existing == force =>
                  Right((removed + count, returned, currentSites.updated(site,
                    destination.copy(forces = SiteForces.Occupied(force,
                      present + count)))))
                case _ => Left(CampaignOutcomeMismatch(
                  "committed force cannot relocate onto a different force"))
              }
            }
          case _ => Left(CampaignOutcomeMismatch(
            "attacker loss contains an invalid committed-force disposition"))
        }
      }
    }.flatMap { case (removed, returned, nextSites) =>
      Either.cond(removed + returned == surviving,
        players.map(p => if (p.player != campaign.actor) p else p.copy(
          board = p.board.copy(warbands = p.board.warbands + returned))) -> nextSites,
        CampaignOutcomeMismatch(
          "attacker loss does not dispose of every surviving force warband"))
    }
  }
}

object CampaignRules {
  def validateStart(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, site: SiteId, force: Int)
      : Either[OathViolation, CampaignDefender] =
    validateStart(catalog, ready, playerId, Vector(site), force)

  /** Classification is deliberately exact-ID based. A reviewed catalog handler
    * is not executable merely because its printed text contains a keyword.
    */
  sealed trait HandlerSupport extends Product with Serializable
  object HandlerSupport {
    final case class Executable(window: CampaignTimingWindow) extends HandlerSupport
    final case class Blocked(reason: String) extends HandlerSupport
    case object IrrelevantToBanditConquest extends HandlerSupport
  }

  final case class DiscoveredCampaignRule(
      activation: RuleActivation,
      support: HandlerSupport,
      facedown: Boolean
  )

  private val vowOfPeace = new TypedRuleHandler {
    def resolve(activation: RuleActivation, context: RuleQueryContext): RuleOutcome =
      context match {
        case _: RuleQueryContext.Campaign =>
          RuleOutcome.Block(CampaignUnavailable("Vow of Peace prevents its ruler from campaigning"))
        case _ => RuleOutcome.Allow
      }
  }

  private val registry = RuleRegistry(
    "denizen.vow-of-peace" -> vowOfPeace
  )

  private val executable: Map[String, CampaignTimingWindow] = Map(
    "denizen.vow-of-peace" -> CampaignTimingWindow.TargetAndForceFormation,
    "denizen.outriders" -> CampaignTimingWindow.AttackerBattlePlans,
    "relic.brass-army" -> CampaignTimingWindow.AttackerBattlePlans
  )

  // These reviewed powers cannot affect a single-site Conquest against bandits.
  private val irrelevant: Set[String] = Set(
    "denizen.bear-traps", "denizen.extra-provisions",
    "denizen.gleaming-armor", "denizen.herald", "denizen.insect-swarm",
    "denizen.military-parade", "denizen.pledge-of-defense",
    "denizen.relic-hunter", "denizen.sealing-ward", "denizen.specialist",
    "denizen.true-names", "denizen.watchdog", "denizen.wrestlers",
    "relic.bandit-standard", "relic.fearsome-shield",
    "relic.sticky-fire", "relic.obsidian-cage",
    "relic.the-grand-scepter"
  )

  private def textRelevant(text: String): Boolean = {
    val lower = text.toLowerCase
    Vector("campaign", "battle plan", "attack-die", "defense-die", "skull",
      "sword", "victorious", "defeated").exists(lower.contains)
  }

  private[gameplay] def classify(handlerId: String, rulesText: String): HandlerSupport =
    executable.get(handlerId).map(HandlerSupport.Executable)
      .orElse(Option.when(irrelevant(handlerId))(HandlerSupport.IrrelevantToBanditConquest))
      .getOrElse(if (textRelevant(rulesText)) HandlerSupport.Blocked(
        "requires an unmodeled Campaign decision or effect")
      else HandlerSupport.IrrelevantToBanditConquest)

  def siteDefinition(catalog: ExecutableCatalog, site: SiteId) =
    catalog.sites.find(_.id == site)

  def defenderForce(ready: ReadyGame, sites: Vector[SiteId]): Int =
    sites.flatMap(ready.game.current.map.sites.get).map(_.forces match {
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0
    }).sum

  private[gameplay] def attackResult(dice: Vector[AttackDieFace], force: Int,
      ignoreSkulls: Boolean): (Int, Int) = {
    val rolledSkulls = AttackDieFace.skulls(dice)
    if (ignoreSkulls) AttackDieFace.score(dice) -> 0
    else {
      val payableSkulls = math.min(rolledSkulls, force)
      (AttackDieFace.score(dice) - (rolledSkulls - payableSkulls) * 2) ->
        payableSkulls
    }
  }

  def legalPlanChoices(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign)
      : Vector[PendingProcedure.CampaignPlanSource] = {
    if (campaign.plansFinished) Vector.empty
    else accessibleRules(catalog, ready, campaign.actor,
      campaign.targetSites.head).collect {
      case DiscoveredCampaignRule(activation, HandlerSupport.Executable(
          CampaignTimingWindow.AttackerBattlePlans), _) if
          Set("denizen.outriders", "relic.brass-army")(activation.handlerId) => activation.source match {
        case RuleSourceRef.Adviser(player, id: DenizenId) =>
          Some(PendingProcedure.CampaignPlanSource.Adviser(player, id))
        case RuleSourceRef.SiteCard(site, id: DenizenId) =>
          Some(PendingProcedure.CampaignPlanSource.SiteCard(site, id))
        case RuleSourceRef.Relic(player, id) =>
          ready.game.current.players.find(_.player == player).flatMap { owner =>
            owner.relics.find(_.id == id).filter(relic =>
              relic.tokens.isEmpty && owner.board.faceUpSecrets >= 1).map(_ =>
              PendingProcedure.CampaignPlanSource.Relic(player, id))
          }
        case _ => None
      }
    }.flatten.distinct.filterNot(source => campaign.plans.exists(_.source == source))
      .sortBy(_.stableKey)
  }

  def validatePlanChoice(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign,
      selected: PendingProcedure.CampaignPlanSource)
      : Either[OathViolation, PendingProcedure.CampaignPlanResolution] = {
    if (campaign.plans.exists(_.source == selected)) Left(CampaignPlanUnavailable(
      s"Campaign plan source '${selected.stableKey}' was already used"))
    else if (legalPlanChoices(catalog, ready, campaign).contains(selected))
      selected match {
        case PendingProcedure.CampaignPlanSource.Adviser(player, id) =>
          val revealed = ready.game.current.players.find(_.player == player).toVector
            .flatMap(_.advisers).collectFirst {
              case d: DenizenState if d.id == id => d.orientation == Orientation.FaceDown
            }.getOrElse(false)
          Right(PendingProcedure.CampaignPlanResolution(selected,
            "denizen.outriders", 0, 0, revealed, true, 0))
        case PendingProcedure.CampaignPlanSource.SiteCard(site, id) =>
          val revealed = ready.game.current.map.sites.get(site).toVector.flatMap(_.denizens)
            .collectFirst {
              case d: DenizenState if d.id == id => d.orientation == Orientation.FaceDown
            }.getOrElse(false)
          Right(PendingProcedure.CampaignPlanResolution(selected,
            "denizen.outriders", 0, 0, revealed, true, 0))
        case PendingProcedure.CampaignPlanSource.Relic(player, id) =>
          val owner = ready.game.current.players.find(_.player == player).get
          val relic = owner.relics.find(_.id == id).get
          if (owner.board.faceUpSecrets < 1)
            Left(InsufficientSecrets(1, owner.board.faceUpSecrets))
          else if (!relic.tokens.isEmpty)
            Left(CampaignPlanUnavailable("Brass Army must be empty to receive its cost"))
          else Right(PendingProcedure.CampaignPlanResolution(selected,
            "relic.brass-army", 0, 1, false, false, 4))
      }
    else Left(CampaignPlanUnavailable(
      s"Campaign plan source '${selected.stableKey}' is stale, inaccessible, or unsupported"))
  }

  def validateSelectedPlans(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign)
      : Either[OathViolation, Vector[PendingProcedure.CampaignPlanResolution]] = {
    val sources = campaign.plans.map(_.source)
    if (sources.distinct.size != sources.size) Left(CampaignOutcomeMismatch(
      "Campaign plan sources must be distinct"))
    else campaign.plans.foldLeft[
      Either[OathViolation, Vector[PendingProcedure.CampaignPlanResolution]]](
      Right(Vector.empty)) { (result, plan) => result.flatMap { accepted =>
        val valid = plan.handlerId match {
          case "denizen.outriders" =>
            plan.favorCost == 0 && plan.secretCost == 0 &&
              plan.ignoreAttackSkulls && plan.addedAttackDice == 0 &&
              (plan.source match {
                case PendingProcedure.CampaignPlanSource.Adviser(player, id) =>
                  player == campaign.actor && ready.game.current.players
                    .find(_.player == player).exists(_.advisers.exists {
                      case d: DenizenState => d.id == id &&
                        d.orientation == Orientation.FaceUp
                      case _ => false
                    })
                case PendingProcedure.CampaignPlanSource.SiteCard(site, id) =>
                  ready.game.current.map.sites.get(site).exists(_.denizens.exists {
                    case d: DenizenState => d.id == id &&
                      d.orientation == Orientation.FaceUp
                    case _ => false
                  })
                case _ => false
              })
          case "relic.brass-army" =>
            !plan.revealed && plan.favorCost == 0 && plan.secretCost == 1 &&
              !plan.ignoreAttackSkulls && plan.addedAttackDice == 4 &&
              (plan.source match {
                case PendingProcedure.CampaignPlanSource.Relic(player, id) =>
                  player == campaign.actor && ready.game.current.players
                    .find(_.player == player).exists(_.relics.exists(r =>
                      r.id == id && r.orientation == Orientation.FaceUp &&
                        r.tokens == Tokens(0, 1)))
                case _ => false
              })
          case _ => false
        }
        Either.cond(valid, accepted :+ plan, CampaignOutcomeMismatch(
          s"recorded Campaign plan '${plan.source.stableKey}' is invalid"))
      }}
  }

  def legalTargets(catalog: ExecutableCatalog, ready: ReadyGame, playerId: PlayerId)
      : Vector[SiteId] = ready.game.current.players.find(_.player == playerId).toVector
    .filter(hasCampaignSupply).flatMap(_.pawnSite).flatMap { pawn =>
      val defender = defenderAt(ready, pawn).toOption
      val candidates = pawn +: ready.game.current.map.inPlay.filter(_ != pawn).filter(
        site => defenderAt(ready, site).toOption == defender)
      if (defender.isEmpty ||
          validateSupported(catalog, ready, playerId, Vector(pawn)).isLeft) Vector.empty
      else candidates.filter(site => site == pawn ||
        validateSupported(catalog, ready, playerId, Vector(pawn, site)).isRight)
    }

  def validateStart(catalog: ExecutableCatalog, ready: ReadyGame, playerId: PlayerId,
      sites: Vector[SiteId], force: Int): Either[OathViolation, CampaignDefender] = {
    val current = ready.game.current
    val player = current.players.find(_.player == playerId).get
    for {
      pawn <- player.pawnSite.toRight(PawnSiteMissing(playerId))
      _ <- Either.cond(sites.nonEmpty && sites.head == pawn, (),
        CampaignUnavailable("the pawn site must be the mandatory first target"))
      _ <- Either.cond(sites.distinct.size == sites.size, (),
        CampaignUnavailable("Conquest targets must be distinct"))
      canonical = pawn +: current.map.inPlay.filter(site => site != pawn && sites.contains(site))
      _ <- Either.cond(sites == canonical, (),
        CampaignUnavailable("Conquest targets must use canonical map order"))
      defender <- defenderAt(ready, pawn)
      _ <- Either.cond(defender != CampaignDefender.Player(playerId), (),
        CampaignUnavailable("a player cannot Conquest their own sites"))
      _ <- sites.foldLeft[Either[OathViolation, Unit]](Right(())) {
        case (result, site) => result.flatMap(_ => defenderAt(ready, site)
          .flatMap(actual => Either.cond(actual == defender, (),
            CampaignUnavailable("all Conquest sites need the same defender"))))
      }
      _ <- sites.foldLeft[Either[OathViolation, Unit]](Right(())) {
        case (result, site) => result.flatMap(_ => Either.cond(
          passAllowsTarget(catalog, ready, playerId, pawn, site), (),
          CampaignUnavailable(
            s"a Pass prevents targeting '${site.value}' from the pawn site")))
      }
      _ <- if (player.board.supply.supply >= Campaign.SupplyCost) Right(()) else Left(InsufficientSupply(Campaign.SupplyCost, player.board.supply.supply))
      _ <- if (force >= Campaign.MinimumForce && force <= player.board.warbands) Right(()) else Left(CampaignUnavailable("attack force must be between zero and board warbands"))
      _ <- validateSupported(catalog, ready, playerId, sites)
      _ <- defender match {
        case CampaignDefender.Player(player) =>
          validateDefenderSupported(catalog, ready, player, sites.head)
        case CampaignDefender.Bandits => Right(())
      }
    } yield defender
  }

  private def validateDefenderSupported(catalog: ExecutableCatalog,
      ready: ReadyGame, defender: PlayerId, target: SiteId)
      : Either[OathViolation, Unit] = {
    if (ready.game.current.title.holder.contains(defender))
      Left(CampaignUnavailable(
        s"${ready.game.current.title.side} title defender battle plan is not supported"))
    else {
      val relevant = accessibleRules(catalog, ready, defender, target)
      relevant.headOption.toLeft(()).left.map { rule =>
      CampaignUnavailable("player-defender battle plan or Campaign power " +
        s"'${rule.activation.handlerId}' is not supported")
      }
    }
  }

  def defenderAt(ready: ReadyGame, site: SiteId)
      : Either[OathViolation, CampaignDefender] =
    ready.game.current.map.sites.get(site).toRight(SiteNotInPlay(site)).flatMap(
      value => SiteRule.ruler(value.forces, ready.game.current.players)
        .left.map(error => CampaignUnavailable(error.toString)).flatMap {
          case SiteRuler.Bandits => Right(CampaignDefender.Bandits)
          case SiteRuler.Player(player) => Right(CampaignDefender.Player(player))
          case SiteRuler.Empire => Left(CampaignUnavailable(
            "Imperial Conquest defenders are not supported"))
          case SiteRuler.Unruled => Left(CampaignUnavailable(
            "Conquest requires a ruled pawn site"))
        })

  private[gameplay] def passAllowsTarget(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, pawn: SiteId, target: SiteId): Boolean = {
    val map = ready.game.current.map
    (map.regionOf(pawn), map.regionOf(target)) match {
      case (Some(from), Some(to)) if from != to =>
        val passes = map.inPlay.filter(site => map.regionOf(site).contains(to) &&
          siteDefinition(catalog, site).exists(_.handlers.contains(
            "site.narrow-pass.pass")))
        passes.forall(pass => pass == target || SiteRule.ruledBy(
          map.sites(pass).forces, ready.game.current.players,
          playerId).getOrElse(false))
      case _ => true
    }
  }

  private def hasCampaignSupply(player: PlayerState): Boolean =
    player.board.supply.supply >= Campaign.SupplyCost

  private def validateSupported(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, sites: Vector[SiteId]): Either[OathViolation, Unit] = {
    val game = ready.game
    val player = game.current.players.find(_.player == playerId).get
    val unsupportedBase =
      if (ready.support.foundationProfile != FirstGameFoundationProfile.FixedUnaltered ||
          game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Campaign")
      else if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Campaign is limited to the all-Exile first game")
      else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active))) Some("active legacy Campaign powers are not supported")
      else None
    val ruledSites = game.current.map.inPlay.foldLeft[
      Either[SiteRuleError, Vector[SiteId]]](Right(Vector.empty)) {
      case (Right(acc), siteId) =>
        SiteRule.ruledBy(game.current.map.sites(siteId).forces,
          game.current.players, playerId).map(ruled =>
          if (ruled) acc :+ siteId else acc)
      case (failure @ Left(_), _) => failure
    }
    ruledSites.left.map(error => UnsupportedCampaignState(
      s"cannot resolve Campaign access because site rule is corrupt: $error"))
      .flatMap { ruled =>
        val discovered = discover(catalog, ready, playerId, sites, ruled)
        unsupportedBase.map(UnsupportedCampaignState).toLeft(()).flatMap { _ =>
          discovered.sortBy(r => (r.activation.priority,
            r.activation.source.stableKey, r.activation.handlerId)).foldLeft[
              Either[OathViolation, Unit]](Right(())) {
            case (failure @ Left(_), _) => failure
            case (Right(_), rule) => rule.support match {
              case HandlerSupport.IrrelevantToBanditConquest => Right(())
              case HandlerSupport.Blocked(reason) => Left(UnsupportedCampaignState(
                s"Campaign handler '${rule.activation.handlerId}' at ${rule.activation.source.stableKey} is blocked: $reason"))
              case HandlerSupport.Executable(CampaignTimingWindow.AttackerBattlePlans) =>
                Right(())
              case HandlerSupport.Executable(_) if rule.facedown => Right(())
              case HandlerSupport.Executable(window) =>
                registry.resolve(Vector(rule.activation), RuleQueryContext.Campaign(
                  ready, player, sites.head, window)).head.outcome match {
                  case RuleOutcome.Allow => Right(())
                  case RuleOutcome.Block(violation) => Left(violation)
                  case RuleOutcome.UnsupportedRelevantRule(id) => Left(UnsupportedCampaignState(
                    s"Campaign handler '$id' is not registered"))
                  case other => Left(UnsupportedCampaignState(
                    s"Campaign handler '${rule.activation.handlerId}' produced unsupported outcome $other"))
                }
            }
          }
        }
      }
  }

  private[gameplay] def discover(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, targets: Vector[SiteId], ruled: Vector[SiteId])
      : Vector[DiscoveredCampaignRule] = {
    val player = ready.game.current.players.find(_.player == playerId).get
    val advisers = player.advisers.collect { case d: DenizenState =>
      catalog.denizens.find(_.id.value == d.id.value).toVector.flatMap { definition =>
        definition.handlers.map(id => DiscoveredCampaignRule(
          RuleActivation(RuleSourceRef.Adviser(playerId, d.id), id, 100),
          classify(id, definition.rulesText), d.orientation == Orientation.FaceDown))
      }
    }.flatten
    val relics = player.relics.filter(_.orientation == Orientation.FaceUp).flatMap { relic =>
      catalog.relics.find(_.id.value == relic.id.value).toVector.flatMap { definition =>
        definition.handlers.map(id => DiscoveredCampaignRule(
          RuleActivation(RuleSourceRef.Relic(playerId, relic.id), id, 200),
          classify(id, definition.rulesText), facedown = false))
      }
    }
    val siteRules = (targets ++ ruled).distinct.flatMap { siteId =>
      val printedSite = siteDefinition(catalog, siteId).toVector.flatMap(_.handlers)
        .filter(id => id.endsWith(".mountain") || id.endsWith(".plains"))
        .map(id => DiscoveredCampaignRule(
          RuleActivation(RuleSourceRef.Site(siteId), id, 250),
          HandlerSupport.Blocked("printed site Campaign defense is not executable"),
          facedown = false))
      printedSite ++ ready.game.current.map.sites(siteId).denizens.flatMap {
        case d: DenizenState => catalog.denizens.find(_.id.value == d.id.value).toVector.flatMap { definition =>
          definition.handlers.map(id => DiscoveredCampaignRule(
            RuleActivation(RuleSourceRef.SiteCard(siteId, d.id), id, 300),
            classify(id, definition.rulesText), d.orientation == Orientation.FaceDown))
        }
        case e: EdificeState => catalog.edifices.find(_.id.value == e.id.value).toVector.flatMap { definition =>
          val face = if (e.side == EdificeSide.Intact) definition.intact else definition.ruined
          face.handlers.map(id => DiscoveredCampaignRule(
            RuleActivation(RuleSourceRef.Edifice(siteId, e.id), id, 400),
            classify(id, face.rulesText), facedown = false))
        }
        case _ => Vector.empty
      }
    }
    (advisers ++ relics ++ siteRules).filter(rule => rule.support match {
      case HandlerSupport.Blocked(_) => true
      case HandlerSupport.Executable(_) => true
      case HandlerSupport.IrrelevantToBanditConquest => false
    })
  }

  private def accessibleRules(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, target: SiteId): Vector[DiscoveredCampaignRule] = {
    val ruled = ready.game.current.map.inPlay.filter { siteId =>
      SiteRule.ruledBy(ready.game.current.map.sites(siteId).forces,
        ready.game.current.players, playerId).getOrElse(false)
    }
    discover(catalog, ready, playerId, Vector(target), ruled)
  }
}
