package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

sealed trait CampaignCommand extends Product with Serializable
object CampaignCommand {
  final case class Start(playerId: PlayerId, decision: DecisionId, targetSites: Vector[SiteId],
      force: Int) extends CampaignCommand
  object Start {
    def apply(playerId: PlayerId, decision: DecisionId, siteId: SiteId,
        force: Int): Start = new Start(playerId, decision, Vector(siteId), force)
  }
  final case class StartRaid(playerId: PlayerId, decision: DecisionId,
      targets: Vector[CampaignRaidTarget], force: Int) extends CampaignCommand
  final case class ChoosePlan(playerId: PlayerId, decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource) extends CampaignCommand
  final case class FinishPlans(playerId: PlayerId, decision: DecisionId,
      attackDice: Vector[AttackDieFace]) extends CampaignCommand
  final case class Sacrifice(playerId: PlayerId, decision: DecisionId, count: Int,
      defenseDice: Vector[DefenseDieFace]) extends CampaignCommand
  final case class Place(playerId: PlayerId, decision: DecisionId,
      allocations: Vector[CampaignForceAllocation])
      extends CampaignCommand
  final case class RelocateRaidPawn(playerId: PlayerId, decision: DecisionId,
      destination: SiteId) extends CampaignCommand
}

object Campaign {
  val SupplyCost = 2
  val MinimumForce = 0

  /** Validates every non-random fact needed to choose an attacker plan. The
    * application boundary must call this before preparing physical dice.
    */
  def prepareFinishPlans(catalog: ExecutableCatalog, state: OathState,
      player: PlayerId, decision: DecisionId)
      : Either[OathViolation, Option[Int]] = state match {
    case Ready(ready) => validatePlanPending(ready, player, decision).flatMap { pending =>
      CampaignRules.validateSelectedPlans(catalog, ready, pending).flatMap { plans =>
        val rollNow = pending.attackerPlansFinished || pending.defender == CampaignDefender.Bandits
        val automatic = if (pending.defender == CampaignDefender.Bandits)
          CampaignRules.deterministicBanditPlans(catalog, ready, pending)
        else Right(Vector.empty)
        automatic.map(auto => Option.when(rollNow)(pending.force +
          CampaignPlanEffects.attackDice((plans ++ auto).flatMap(_.effects))))
      }
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
    case CampaignCommand.StartRaid(player, decision, targets, force) =>
      OathLifecycle.validateAct(state, player).flatMap { ready =>
        CampaignRules.validateRaidStart(catalog, ready, player, targets, force).flatMap { defender =>
          transition(catalog, state, Vector(CampaignStarted(player, decision,
            Vector.empty, defender, SupplyCost, force, CampaignKind.Raid, targets)),
            AwaitingCampaignPlan(player, decision))
        }
      }
    case CampaignCommand.ChoosePlan(player, decision, source) => state match {
      case Ready(ready) => validatePlanPending(ready, player, decision).flatMap { pending =>
        CampaignRules.validatePlanChoice(catalog, ready, pending, source).flatMap { choice =>
          transition(catalog, state, Vector(CampaignPlanChosen(player, decision,
            choice.source, choice.handlerId, choice.side, choice.costs, choice.effects)),
            AwaitingCampaignPlan(player, decision))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case CampaignCommand.FinishPlans(player, decision, dice) => state match {
      case Ready(ready) => validatePlanPending(ready, player, decision).flatMap { pending =>
        CampaignRules.validateSelectedPlans(catalog, ready, pending).flatMap { plans =>
          val side = CampaignRules.currentPlanSide(pending)
          val sidePlans = plans.filter(_.side == side)
          if (side == PendingProcedure.CampaignPlanSide.Attacker &&
              pending.defender.isInstanceOf[CampaignDefender.Player])
            transition(catalog, state, Vector(CampaignPlansFinished(player, decision,
              side, sidePlans.map(_.source), sidePlans.map(_.handlerId),
              sidePlans.flatMap(_.effects),
              Vector.empty, 0, 0)), AwaitingCampaignPlan(
                pending.defender.asInstanceOf[CampaignDefender.Player].playerId, decision))
          else {
            val banditPlans = if (pending.defender == CampaignDefender.Bandits &&
              side == PendingProcedure.CampaignPlanSide.Attacker)
              CampaignRules.deterministicBanditPlans(catalog, ready, pending)
            else Right(Vector.empty)
            banditPlans.flatMap { automatic =>
              val allPlans = plans ++ automatic
              val allEffects = allPlans.flatMap(_.effects)
              val expectedDice = pending.force + CampaignPlanEffects.attackDice(allEffects)
              if (dice.size != expectedDice) Left(CampaignOutcomeMismatch(
                "attack dice count is invalid"))
              else {
                val (attack, skulls) = CampaignRules.attackResult(dice, pending.force,
                  CampaignPlanEffects.ignoreAttackSkulls(allEffects))
                val events = if (pending.defender == CampaignDefender.Bandits)
                  Vector(
                    CampaignPlansFinished(player, decision,
                      PendingProcedure.CampaignPlanSide.Attacker,
                      sidePlans.map(_.source), sidePlans.map(_.handlerId),
                      sidePlans.flatMap(_.effects),
                      Vector.empty, 0, 0),
                    CampaignPlansFinished(player, decision,
                      PendingProcedure.CampaignPlanSide.Defender,
                      automatic.map(_.source), automatic.map(_.handlerId),
                      automatic.flatMap(_.effects), dice,
                      attack, skulls))
                else Vector(CampaignPlansFinished(player, decision, side,
                  sidePlans.map(_.source), sidePlans.map(_.handlerId),
                  sidePlans.flatMap(_.effects), dice,
                  attack, skulls))
                transition(catalog, state, events,
                  AwaitingCampaignSacrifice(pending.actor, decision))
              }
            }
          }
        }
      }
      case _ => Left(GameNotStarted)
    }
    case CampaignCommand.Sacrifice(player, decision, count, dice) => state match {
      case Ready(ready) => validatePending(ready, player, decision,
        requireFinished = true, requireResolved = false)
        .flatMap { pending =>
          val attack = pending.attack + count
          val defense = DefenseDieFace.score(dice) + CampaignRules.defenderForce(ready, pending)
          val victorious = attack > defense
          val surviving = pending.force - pending.skullLosses - count
          val losses = if (victorious) Right(Vector.empty) else
            losingForceRegistry.selected.resolveAttackerDefeat(
              ready, pending, surviving)
          losses.flatMap(result => transition(catalog, state,
            Vector(CampaignSacrificed(player, decision, count, dice, attack,
              defense, pending.skullLosses, victorious,
              Option.when(!victorious)(losingForceRegistry.selected.id), result)),
            if (victorious) pending.kind match {
              case CampaignKind.Conquest => AwaitingCampaignPlacement(player, decision)
              case CampaignKind.Raid => AwaitingCampaignRaidRelocation(player, decision)
            }
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
    case CampaignCommand.RelocateRaidPawn(player, decision, destination) => state match {
      case Ready(ready) => ready.game.current.pending match {
        case Some(c: PendingProcedure.Campaign) if c.actor == player &&
            c.decision == decision && c.kind == CampaignKind.Raid &&
            c.victorious.contains(true) =>
          resolveRaidVictory(ready, c, losingForceRegistry).flatMap { event =>
            val defender = c.defender.asInstanceOf[CampaignDefender.Player].playerId
            val origin = ready.game.current.players.find(_.player == defender).get.pawnSite.get
            val legal = CampaignRules.legalRaidRelocationSites(ready, defender)
            Either.cond(legal.contains(destination), (), CampaignOutcomeMismatch(
              "Raid pawn destination is not legal")).flatMap(_ => transition(catalog, state,
              Vector(event, CampaignRaidPawnRelocated(player, decision, defender,
                origin, destination)), ActActionSelection(player), losingForceRegistry))
          }
        case Some(c: PendingProcedure.Campaign) => Left(CampaignOutcomeMismatch(
          "Campaign is not awaiting Raid pawn relocation"))
        case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
        case None => Left(InvalidEventOrder("no Raid relocation is pending"))
      }
      case _ => Left(GameNotStarted)
    }
  }

  private def resolveRaidVictory(ready: ReadyGame, c: PendingProcedure.Campaign,
      registry: CampaignLosingForceRegistry): Either[OathViolation, CampaignRaided] = {
    val defenderId = c.defender.asInstanceOf[CampaignDefender.Player].playerId
    val defender = ready.game.current.players.find(_.player == defenderId).get
    val relics = c.raidTargets.collect { case CampaignRaidTarget.Relic(_, id) => id }
    val banners = c.raidTargets.collect { case CampaignRaidTarget.Banner(_, id) => id }
    val revealedPlans = c.plans.collect {
      case plan if plan.effects.contains(
          PendingProcedure.CampaignPlanEffect.RevealSource) => plan.source
    }.toSet
    val advisers = defender.advisers.collect {
      case d: DenizenState if d.orientation == Orientation.FaceDown ||
          revealedPlans(PendingProcedure.CampaignPlanSource.Adviser(defenderId, d.id)) =>
        d.id: WorldCardId
      case v: VisionState if v.orientation == Orientation.FaceDown => v.id: WorldCardId
    }
    val conspiracy = advisers.collectFirst {
      case id: VisionId if id == CampaignRules.Conspiracy => id
    }
    val ordinaryAdvisers = advisers.filterNot(id => conspiracy.contains(id))
    val facedownRelics = defender.relics.collect {
      case r if r.orientation == Orientation.FaceDown => r.id
    }
    val returned = if (banners.contains(CampaignBanner.PeoplesFavor))
      CampaignRules.returnBannerFavor(ready.support.favorBanks,
        ready.game.current.banners.peoplesFavor.favor)
    else Map.empty[Suit, Int]
    for {
      origin <- CampaignRules.campaignOrigin(ready, c)
      region <- ready.game.current.map.regionOf(origin).toRight(
        CampaignOutcomeMismatch("Raid origin has no region"))
      loss <- registry.selected.resolveRaidDefenderDefeat(ready, c)
    } yield CampaignRaided(c.actor, c.decision, registry.selected.id, loss,
      relics, banners, ordinaryAdvisers, CampaignRules.nextRegion(region),
      conspiracy, facedownRelics, defender.board.favor / 2, returned,
      Option.when(banners.contains(CampaignBanner.DarkestSecret))(
        ready.game.current.banners.darkestSecret.secrets).getOrElse(0))
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
      case Some(c: PendingProcedure.Campaign) if requireFinished && !c.defenderPlansFinished =>
        Left(CampaignOutcomeMismatch("Campaign is awaiting battle plans"))
      case Some(c: PendingProcedure.Campaign) if !requireFinished && c.defenderPlansFinished =>
        Left(CampaignOutcomeMismatch("Campaign battle plans are finished"))
      case Some(c: PendingProcedure.Campaign) if requireResolved && !c.victorious.contains(true) => Left(CampaignOutcomeMismatch("Campaign is not awaiting conquest placement"))
      case Some(c: PendingProcedure.Campaign) if !requireResolved && c.victorious.nonEmpty => Left(CampaignOutcomeMismatch("Campaign battle is already resolved"))
      case Some(c: PendingProcedure.Campaign) => Right(c)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Campaign procedure is pending"))
    }
  }

  private def validatePlanPending(ready: ReadyGame, player: PlayerId,
      decision: DecisionId): Either[OathViolation, PendingProcedure.Campaign] =
    ready.game.current.pending match {
      case Some(c: PendingProcedure.Campaign) if c.decision != decision =>
        Left(CampaignDecisionMismatch(c.decision, decision))
      case Some(c: PendingProcedure.Campaign) if c.defenderPlansFinished =>
        Left(CampaignOutcomeMismatch("Campaign battle plans are finished"))
      case Some(c: PendingProcedure.Campaign) =>
        val owner = CampaignRules.planDecisionOwner(c)
        Either.cond(player == owner, c, WrongPlayer(owner, player))
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Campaign procedure is pending"))
    }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] =
    evolve(catalog, state, event, CampaignLosingForceRegistry.default)

  private[gameplay] def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent, losingForceRegistry: CampaignLosingForceRegistry)
      : Either[OathViolation, OathState] = event match {
    case e: CampaignStarted => OathLifecycle.validateAct(state, e.playerId).flatMap { ready =>
      for {
        defender <- e.kind match {
          case CampaignKind.Conquest => CampaignRules.validateStart(catalog, ready,
            e.playerId, e.targetSites, e.force)
          case CampaignKind.Raid => CampaignRules.validateRaidStart(catalog, ready,
            e.playerId, e.raidTargets, e.force)
        }
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
            e.force, Vector.empty, attackerPlansFinished = false,
            defenderPlansFinished = false, Vector.empty, 0, 0,
            None, Vector.empty, None, None, e.kind, e.raidTargets)))))
      }
    }
    case e: CampaignPlanChosen => state match {
      case Ready(ready) => validatePlanPending(ready, e.playerId, e.decision).flatMap { c =>
        for {
          expected <- CampaignRules.validatePlanChoice(catalog, ready, c, e.source)
          _ <- if (e.handlerId == expected.handlerId &&
            e.side == expected.side && e.costs == expected.costs &&
            e.effects == expected.effects) Right(())
            else Left(CampaignOutcomeMismatch("recorded attacker plan result is invalid"))
        } yield {
          val resolution = PendingProcedure.CampaignPlanResolution(e.source,
            e.handlerId, e.side, e.costs, e.effects)
          val favorCost = CampaignPlanEffects.favorCost(e.costs)
          val secretCost = CampaignPlanEffects.secretCost(e.costs)
          val revealed = CampaignPlanEffects.revealed(e.effects)
          val current = ready.game.current
          Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            players = current.players.map { p =>
              if (p.player != e.playerId) p
              else p.copy(
                board = p.board.copy(favor = p.board.favor - favorCost,
                  faceUpSecrets = p.board.faceUpSecrets - secretCost),
                advisers = p.advisers.map {
                  case d: DenizenState if e.source ==
                    PendingProcedure.CampaignPlanSource.Adviser(e.playerId, d.id) =>
                    d.copy(orientation = if (revealed) Orientation.FaceUp else d.orientation,
                      tokens = Tokens(d.tokens.favor + favorCost,
                        d.tokens.secrets + secretCost))
                  case other => other
                },
                relics = p.relics.map {
                  case r if e.source == PendingProcedure.CampaignPlanSource.Relic(
                    e.playerId, r.id) => r.copy(tokens = Tokens(
                      r.tokens.favor + favorCost, r.tokens.secrets + secretCost))
                  case other => other
                })
            },
            map = current.map.copy(sites = current.map.sites.map {
              case (siteId, site) => siteId -> site.copy(denizens = site.denizens.map {
                case d: DenizenState if e.source ==
                    PendingProcedure.CampaignPlanSource.SiteCard(siteId, d.id) =>
                  d.copy(orientation = if (revealed) Orientation.FaceUp else d.orientation,
                    tokens = Tokens(d.tokens.favor + favorCost,
                      d.tokens.secrets + secretCost))
                case other => other
              })
            }),
            pending = Some(c.copy(plans = c.plans :+ resolution)))))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignPlansFinished => state match {
      case Ready(ready) => validatePlanPending(ready, e.playerId, e.decision).flatMap { c =>
        for {
          expected <- CampaignRules.validateSelectedPlans(catalog, ready, c)
          expectedSide = CampaignRules.currentPlanSide(c)
          automatic <- if (expectedSide == PendingProcedure.CampaignPlanSide.Defender &&
              c.defender == CampaignDefender.Bandits)
            CampaignRules.deterministicBanditPlans(catalog, ready, c)
            else Right(Vector.empty)
          expectedPlans = expected.filter(_.side == expectedSide) ++ automatic
          expectedSources = expectedPlans.map(_.source)
          expectedEffects = expectedPlans.flatMap(_.effects)
          _ <- Either.cond(e.orderedSources == expectedSources &&
            e.orderedHandlerIds == expectedPlans.map(_.handlerId) &&
            e.side == expectedSide && e.effects == expectedEffects, (),
            CampaignOutcomeMismatch("recorded attacker plan order or result is invalid"))
          allEffects = (expected ++ automatic).flatMap(_.effects)
          rollsNow = expectedSide == PendingProcedure.CampaignPlanSide.Defender
          _ <- Either.cond(!rollsNow || e.attackDice.size == c.force +
            CampaignPlanEffects.attackDice(allEffects), (),
            CampaignOutcomeMismatch("attack dice count is invalid"))
          expectedResult = CampaignRules.attackResult(
            e.attackDice, c.force, CampaignPlanEffects.ignoreAttackSkulls(allEffects))
          _ <- Either.cond(!rollsNow || (e.attack, e.skullLosses) == expectedResult, (),
            CampaignOutcomeMismatch("recorded attack roll result is invalid"))
        } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
          pending = Some(c.copy(
            attackerPlansFinished = c.attackerPlansFinished ||
              e.side == PendingProcedure.CampaignPlanSide.Attacker,
            defenderPlansFinished = c.defenderPlansFinished || rollsNow,
            plans = c.plans ++ automatic,
            attackDice = e.attackDice, attack = e.attack,
            skullLosses = e.skullLosses)))))
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignSacrificed => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = true, requireResolved = false).flatMap { c =>
        val remaining = c.force - c.skullLosses
        val expectedAttack = c.attack + e.sacrificed
        val expectedDefense = DefenseDieFace.score(e.defenseDice) +
          CampaignRules.defenderForce(ready, c)
        for {
          _ <- if (e.sacrificed >= 0 && e.sacrificed <= remaining) Right(()) else Left(CampaignOutcomeMismatch("sacrifice exceeds surviving force"))
          _ <- if (e.skullLosses == c.skullLosses) Right(()) else Left(CampaignOutcomeMismatch("recorded skull losses are invalid"))
          defenseDiceCount = CampaignRules.defenseDiceCount(catalog, ready, c)
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
    case e: CampaignRaided => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = true, requireResolved = true).flatMap { c =>
        Either.cond(c.kind == CampaignKind.Raid, (), CampaignOutcomeMismatch(
          "Raid resolution recorded for a Conquest")).flatMap { _ =>
          resolveRaidVictory(ready, c, losingForceRegistry).flatMap { expected =>
            Either.cond(e == expected, (), CampaignOutcomeMismatch(
              "recorded Raid resolution is invalid")).map { _ =>
              val current = ready.game.current
              val defenderId = c.defender.asInstanceOf[CampaignDefender.Player].playerId
              val defender = current.players.find(_.player == defenderId).get
              val taken = defender.relics.filter(r => e.takenRelics.contains(r.id))
              val players = current.players.map {
                case p if p.player == c.actor => p.copy(relics = p.relics ++ taken.map(
                  _.copy(orientation = Orientation.FaceUp)))
                case p if p.player == defenderId => p.copy(
                  board = p.board.copy(favor = p.board.favor - e.favorBurned,
                    warbands = e.defenderLoss.returned),
                  advisers = p.advisers.filterNot(a =>
                    e.discardedAdvisers.contains(a.id) || e.boxedConspiracy.contains(a.id)),
                  relics = p.relics.filterNot(r => e.takenRelics.contains(r.id) ||
                    e.discardedRelics.contains(r.id)))
                case p => p
              }
              val banners = current.banners.copy(
                peoplesFavor = if (e.takenBanners.contains(CampaignBanner.PeoplesFavor))
                  current.banners.peoplesFavor.copy(holder = Some(c.actor), favor = 0)
                else current.banners.peoplesFavor,
                darkestSecret = if (e.takenBanners.contains(CampaignBanner.DarkestSecret))
                  current.banners.darkestSecret.copy(holder = Some(c.actor), secrets = 0)
                else current.banners.darkestSecret)
              val origin = defender.pawnSite.get
              val relocation = PendingProcedure.CampaignRaidRelocation(c.decision,
                c.actor, defenderId, origin,
                CampaignRules.legalRaidRelocationSites(ready, defenderId))
              val updated = GameStateUpdates.updateCurrent(ready)(_.copy(players = players,
                banners = banners,
                commonCards = current.commonCards.copy(regionalDiscards =
                  current.commonCards.regionalDiscards.updated(e.adviserDiscardRegion,
                    current.commonCards.discard(e.adviserDiscardRegion) ++
                      e.discardedAdvisers)),
                pending = Some(relocation)))
              Ready(updated.copy(
                game = updated.game.copy(campaign = updated.game.campaign.copy(
                  reliquary = updated.game.campaign.reliquary ++ e.discardedRelics)),
                support = updated.support.copy(favorBanks =
                e.bannerFavorReturned.foldLeft(updated.support.favorBanks) {
                  case (banks, (suit, amount)) => banks.updated(suit,
                    banks.getOrElse(suit, 0) + amount)
                })))
            }
          }
        }
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignRaidPawnRelocated => state match {
      case Ready(ready) => ready.game.current.pending match {
        case Some(c: PendingProcedure.CampaignRaidRelocation) if
            c.actor == e.playerId && c.decision == e.decision &&
            c.defender == e.defender =>
          val owner = ready.game.current.players.find(_.player == e.defender).get
          Either.cond(owner.pawnSite.contains(e.origin) &&
            c.origin == e.origin && c.legalSites.contains(e.destination), (),
            CampaignOutcomeMismatch("recorded Raid pawn relocation is invalid")).map { _ =>
            Ready(GameStateUpdates.updateCurrent(ready)(current => current.copy(
              players = current.players.map(p => if (p.player != e.defender) p else
                p.copy(pawnSite = Some(e.destination))), pending = None)))
          }
        case _ => Left(CampaignOutcomeMismatch("Raid relocation is not pending"))
      }
      case _ => Left(GameNotStarted)
    }
    case _ => Left(InvalidEventOrder("Campaign received a non-Campaign event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue,
      losingForceRegistry: CampaignLosingForceRegistry =
        CampaignLosingForceRegistry.default) =
    GameplayTransition(state, events, continue)(
      evolve(catalog, _, _, losingForceRegistry))

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
