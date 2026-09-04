package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.gameplay.operations.{Burn, Kill,
  Location, Move => CoreMove, OperationExecutor, OperationPolicy,
  OperationTransaction, Piece, PositionedLocation, Reveal,
  StackPosition, Take}
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
      CampaignRules.returnBannerFavor(ready.banks.favor,
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
        // The committed force stays in the attacker's play area throughout the
        // battle; it leaves the board only when it dies or is placed during
        // resolution. `pending.force` tracks how many board warbands are
        // committed so battle arithmetic and the plan projections stay exact.
        Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
          players = current.players.map(p => if (p.player != e.playerId) p else
            p.copy(board = p.board.copy(
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
          completed <- applyPlanChosen(catalog, ready, e, c).map(Ready(_))
        } yield completed
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
          completed <- applySacrifice(ready, e, c, expectedLosses,
            remaining - e.sacrificed).map(Ready(_))
        } yield completed
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
          _.resolve(ready, c)).flatMap { expectedLosses =>
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
        else applyConquest(ready, e, c, e.losingForces, surviving).map(Ready(_))
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
              "recorded Raid resolution is invalid")).flatMap { _ =>
              applyRaid(ready, e, c).map(Ready(_))
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
            CampaignOutcomeMismatch("recorded Raid pawn relocation is invalid")).flatMap { _ =>
            val operation = CoreMove(
              Piece.Pawn(e.defender),
              PositionedLocation(Location.Site(e.origin)),
              PositionedLocation(Location.Site(e.destination)))
            val executor = new OperationExecutor(OperationPolicy.exact(
              Vector(operation), "Raid pawn relocation is not permitted"))
            OperationTransaction.evolve(ready, Vector(operation), executor)(
              evolved => Right(GameStateUpdates.updateCurrent(evolved)(current =>
                current.copy(pending = None))))
              .map(execution => Ready(execution.ready))
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

  private def exileForce(ready: ReadyGame, player: PlayerId): ForceKind =
    ForceKind.Exile(ready.game.current.players.find(_.player == player).get.lineage)

  private def opponentOf(c: PendingProcedure.Campaign): PlayerId =
    c.defender.asInstanceOf[CampaignDefender.Player].playerId

  /** CampaignPlanChosen: the payer spends favor/secret. Attacker plan costs are
    * paid onto the source card; a defender plan cost (latent: no registered
    * defender plan has a cost) would send favor to the source denizen's suit
    * bank and flip secrets facedown in the play area. A reveal effect flips the
    * facedown source adviser faceup (Flip supports PlayArea only).
    */
  private def applyPlanChosen(catalog: ExecutableCatalog, ready: ReadyGame,
      e: CampaignPlanChosen, c: PendingProcedure.Campaign)
      : Either[OathViolation, ReadyGame] = {
    val resolution = PendingProcedure.CampaignPlanResolution(e.source,
      e.handlerId, e.side, e.costs, e.effects)
    val favorCost = CampaignPlanEffects.favorCost(e.costs)
    val secretCost = CampaignPlanEffects.secretCost(e.costs)
    val sourceCard = e.source match {
      case PendingProcedure.CampaignPlanSource.Adviser(_, id) => Some(id)
      case PendingProcedure.CampaignPlanSource.Relic(_, id) => Some(id)
      case PendingProcedure.CampaignPlanSource.SiteCard(_, id) => Some(id)
      case PendingProcedure.CampaignPlanSource.Title(_) => None
    }
    e.side match {
      case PendingProcedure.CampaignPlanSide.Attacker =>
        val payments = sourceCard.toVector.flatMap { card =>
          Option.when(favorCost > 0)(CoreMove(
            Piece.Favor(favorCost),
            PositionedLocation(Location.PlayArea(e.playerId)),
            PositionedLocation(Location.OnCard(card)))).toVector ++
          Option.when(secretCost > 0)(CoreMove(
            Piece.Secrets(secretCost),
            PositionedLocation(Location.PlayArea(e.playerId)),
            PositionedLocation(Location.OnCard(card)))).toVector
        }
        // Reveal flips the facedown source faceup: an adviser moves within its
        // own PlayArea, a site denizen flips in place at its site.
        val reveal = e.source match {
          case PendingProcedure.CampaignPlanSource.Adviser(player, id)
              if CampaignPlanEffects.revealed(e.effects) =>
            Vector(CoreMove(
              Piece.Card(id),
              PositionedLocation(Location.PlayArea(player)),
              PositionedLocation(Location.PlayArea(player)),
              resultingOrientation = Some(Orientation.FaceUp)))
          case PendingProcedure.CampaignPlanSource.SiteCard(site, id)
              if CampaignPlanEffects.revealed(e.effects) =>
            Vector(Reveal(id, Location.Site(site)))
          case _ => Vector.empty
        }
        execute(ready, payments ++ reveal,
          "Campaign plan choice is not permitted") { state =>
          Right(GameStateUpdates.updateCurrent(state)(current => current.copy(
            pending = Some(c.copy(plans = c.plans :+ resolution)))))
        }
      case PendingProcedure.CampaignPlanSide.Defender =>
        // Latent: no registered defender plan carries a cost today. When one
        // lands, defender favor must go to the source denizen's suit bank and
        // defender secrets flip facedown in the play area (Q11/Q12).
        execute(ready, Vector.empty,
          "Campaign plan choice is not permitted") { state =>
          Right(GameStateUpdates.updateCurrent(state)(current => current.copy(
            pending = Some(c.copy(plans = c.plans :+ resolution)))))
        }
    }
  }

  /** Runs the CoreOperations vector as one authoritative transaction. */
  private def execute(ready: ReadyGame,
      operations: Vector[oathdigital.gameplay.operations.CoreOperation],
      detail: String)(
      update: ReadyGame => Either[OathViolation, ReadyGame])
      : Either[OathViolation, ReadyGame] = {
    val executor = new OperationExecutor(OperationPolicy.exact(operations, detail))
    if (operations.isEmpty) update(ready)
    else OperationTransaction.evolve(ready, operations, executor)(update).map(_.ready)
  }

  private def warbandMove(kind: ForceKind, count: Int, from: Location,
      to: Location): Vector[oathdigital.gameplay.operations.CoreOperation] =
    Option.when(count > 0)(CoreMove(
      Piece.Warbands(kind, count),
      PositionedLocation(from), PositionedLocation(to))).toVector

  private def killAt(kind: ForceKind, count: Int, at: Location) =
    Option.when(count > 0)(oathdigital.gameplay.operations.Kill(
      Piece.Warbands(kind, count), PositionedLocation(at))).toVector

  /** CampaignSacrificed: a victory only records the pending procedure. A
    * defeat kills the warbands lost to skulls and sacrifice plus the committed
    * warbands the losing-force policy removes, and relocates the committed
    * survivors it relocates (returned survivors never left the board).
    */
  private def applySacrifice(ready: ReadyGame, e: CampaignSacrificed,
      c: PendingProcedure.Campaign, losses: Vector[CampaignLosingForceEffect],
      surviving: Int): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    if (e.victorious) Right(GameStateUpdates.updateCurrent(ready)(_.copy(
      pending = Some(c.copy(sacrificed = Some(e.sacrificed), defenseDice = e.defenseDice,
        defense = Some(e.defense), victorious = Some(true))))))
    else {
      val actorForce = exileForce(ready, c.actor)
      val killed = losses.collect {
        case CampaignLosingForceEffect.KillCommitted(_, _, _, count) => count
      }.sum
      val relocated = losses.collect {
        case CampaignLosingForceEffect.RelocateCommitted(site, _, _, count) =>
          site -> count
      }
      val returned = losses.collect {
        case CampaignLosingForceEffect.ReturnToBoard(_, _, _, count) => count
        case CampaignLosingForceEffect.PreserveCommitted(_, _, _, count) => count
      }.sum
      for {
        _ <- Either.cond(surviving >= 0, (), CampaignOutcomeMismatch(
          "attacker loss cannot dispose of negative survivors"))
        _ <- Either.cond(killed + relocated.map(_._2).sum + returned == surviving, (),
          CampaignOutcomeMismatch(
            "attacker loss does not dispose of every surviving force warband"))
        deaths = e.skullLosses + e.sacrificed + killed
        ops = killAt(actorForce, deaths, Location.PlayArea(c.actor)) ++
          relocated.map { case (site, count) =>
            warbandMove(actorForce, count, Location.PlayArea(c.actor),
              Location.Site(site))
          }.flatten
        evolved <- execute(ready, ops, "Campaign defeat is not permitted") { state =>
          Right(GameStateUpdates.updateCurrent(state)(_.copy(pending = None)))
        }
      } yield evolved
    }
  }

  /** Pure site-preview of the losing-force effects (validation only). */
  private def losingSiteEffects(ready: ReadyGame,
      effects: Vector[CampaignLosingForceEffect])
      : Either[OathViolation, Map[SiteId, SiteForces]] = {
    val initial = ready.game.current.map.sites
    effects.foldLeft[Either[OathViolation, Map[SiteId, SiteForces]]](
      Right(initial.map { case (site, state) => site -> state.forces })) {
      case (result, effect) => result.flatMap { sites =>
        def current(site: SiteId) = sites.get(site).toRight(SiteNotInPlay(site))
        effect match {
          case CampaignLosingForceEffect.Remove(site, force, count) =>
            current(site).flatMap(value =>
              Either.cond(value == SiteForces.Occupied(force, count), (),
                CampaignOutcomeMismatch(
                  s"losing force at '${site.value}' changed")))
              .map(_ => sites.updated(site, SiteForces.Empty))
          case CampaignLosingForceEffect.Preserve(site, force, count) =>
            current(site).flatMap(value =>
              Either.cond(value == SiteForces.Occupied(force, count), (),
                CampaignOutcomeMismatch(
                  s"losing force at '${site.value}' changed")))
              .map(_ => sites)
          case CampaignLosingForceEffect.Relocate(site, destination, force, count) =>
            for {
              from <- current(site)
              _ <- Either.cond(from == SiteForces.Occupied(force, count), (),
                CampaignOutcomeMismatch(
                  s"losing force at '${site.value}' changed"))
              to <- current(destination)
              moved <- to match {
                case SiteForces.Empty => Right(SiteForces.Occupied(force, count))
                case SiteForces.Occupied(existing, present) if existing == force =>
                  Right(SiteForces.Occupied(force, present + count))
                case _ => Left(CampaignOutcomeMismatch(
                  "relocated losing force cannot join a different force"))
              }
            } yield sites.updated(site, SiteForces.Empty)
              .updated(destination, moved)
          case CampaignLosingForceEffect.Replace(site, force, count,
              replacement, replacementCount) =>
            current(site).flatMap(value =>
              Either.cond(value == SiteForces.Occupied(force, count), (),
                CampaignOutcomeMismatch(
                  s"losing force at '${site.value}' changed")))
              .map { _ =>
                val next = replacement.fold[SiteForces](SiteForces.Empty)(kind =>
                  SiteForces.Occupied(kind, replacementCount))
                sites.updated(site, next)
              }
          case CampaignLosingForceEffect.ReturnToBoard(_, player, force, count) =>
            ready.game.current.players.find(_.player == player)
              .toRight(CampaignOutcomeMismatch(
                "losing-force return references an unknown player"))
              .flatMap(owner => Either.cond(force == ForceKind.Exile(owner.lineage),
                sites, CampaignOutcomeMismatch(
                  "returned force does not belong to player")))
          case _: CampaignLosingForceEffect.KillCommitted |
              _: CampaignLosingForceEffect.RelocateCommitted |
              _: CampaignLosingForceEffect.PreserveCommitted =>
            Left(CampaignOutcomeMismatch(
              "committed-force disposition cannot resolve defender forces"))
        }
      }
    }
  }

  /** CampaignConquered: kills the attacker warbands lost to skulls and
    * sacrifice, clears defender losing forces at target sites, then moves the
    * allocated survivors onto the cleared sites (unplaced survivors remain).
    */
  private def applyConquest(ready: ReadyGame, e: CampaignConquered,
      c: PendingProcedure.Campaign, losses: Vector[CampaignLosingForceEffect],
      surviving: Int): Either[OathViolation, ReadyGame] = {
    val attacker = ready.game.current.players.find(_.player == c.actor).get
    val actorForce = ForceKind.Exile(attacker.lineage)
    for {
      // Validation: allocations may only land on a cleared Campaign target.
      cleared <- losingSiteEffects(ready, losses)
      _ <- Either.cond(e.allocations.forall(allocation =>
        allocation.count == 0 ||
          cleared(allocation.site) == SiteForces.Empty), (),
        CampaignOutcomeMismatch(
          "force can be placed only at a cleared Campaign target"))
      _ <- Either.cond(surviving >= e.allocations.map(_.count).sum, (),
        CampaignOutcomeMismatch("placed force exceeds survivors"))
      // Physical: defender losing forces (site warbands and returns to board).
      forceOps <- losses.foldLeft[
        Either[OathViolation,
          Vector[oathdigital.gameplay.operations.CoreOperation]]](Right(Vector.empty)) {
        case (result, effect) => result.flatMap { ops => effect match {
          case CampaignLosingForceEffect.Remove(site, force, count) =>
            Right(ops ++ killAt(force, count, Location.Site(site)))
          case CampaignLosingForceEffect.Preserve(_, _, _) => Right(ops)
          case CampaignLosingForceEffect.Relocate(site, destination, force, count) =>
            Right(ops ++ warbandMove(force, count, Location.Site(site),
              Location.Site(destination)))
          case CampaignLosingForceEffect.Replace(site, force, count,
              replacement, replacementCount) =>
            Right(ops ++ killAt(force, count, Location.Site(site)) ++
              replacement.toVector.flatMap(kind => warbandMove(kind,
                replacementCount, Location.WarbandBank(kind), Location.Site(site))))
          case CampaignLosingForceEffect.ReturnToBoard(_, player, force, count) =>
            Right(ops ++ warbandMove(force, count, Location.WarbandBank(force),
              Location.PlayArea(player)))
          case _ => Left(CampaignOutcomeMismatch(
            "committed-force disposition cannot resolve defender forces"))
        }}
      }
      deaths = c.skullLosses + c.sacrificed.get
      placements = e.allocations.flatMap(allocation => warbandMove(actorForce,
        allocation.count, Location.PlayArea(c.actor), Location.Site(allocation.site)))
      evolved <- execute(ready,
        killAt(actorForce, deaths, Location.PlayArea(c.actor)) ++
          forceOps ++ placements,
        "Campaign conquest is not permitted") { state =>
        Right(GameStateUpdates.updateCurrent(state)(_.copy(pending = None)))
      }
    } yield evolved
  }

  /** CampaignRaided: kills the attacker warbands lost to skulls and sacrifice
    * while every committed survivor stays on the board (they never left); the
    * defender's board force loses its killed half. Targeted faceup relics and
    * banners transfer to the attacker; People's Favor favor returns to banks;
    * Darkest Secret secrets are burned; facedown advisers discard to the next
    * region; Conspiracy leaves the game; facedown relics are set aside.
    */
  private def applyRaid(ready: ReadyGame, e: CampaignRaided,
      c: PendingProcedure.Campaign): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    val defenderId = opponentOf(c)
    val defender = current.players.find(_.player == defenderId).get
    val defenderForce = ForceKind.Exile(defender.lineage)
    val attackerForce = exileForce(ready, c.actor)
    val taken = defender.relics.filter(r => e.takenRelics.contains(r.id))
    val relicTakes = taken.map { relic =>
      oathdigital.gameplay.operations.Take(Piece.Card(relic.id), c.actor,
        Location.PlayArea(defenderId), Location.PlayArea(c.actor))
    }
    val bannerOps: Vector[oathdigital.gameplay.operations.CoreOperation] =
      e.takenBanners.flatMap {
        case CampaignBanner.PeoplesFavor =>
          val favorReturns = e.bannerFavorReturned.toVector.flatMap {
            case (suit, amount) => Option.when(amount > 0)(CoreMove(
              Piece.Favor(amount),
              PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
              PositionedLocation(Location.FavorBank(suit)))).toVector
          }
          favorReturns :+ Take(Piece.Banner(Banner.PeoplesFavor), c.actor,
            Location.PlayArea(defenderId), Location.PlayArea(c.actor))
        case CampaignBanner.DarkestSecret =>
          val burn = Option.when(e.darkestSecretBurned > 0)(
            oathdigital.gameplay.operations.Burn.secrets(e.darkestSecretBurned,
              PositionedLocation(Location.OnBanner(Banner.DarkestSecret)))).toVector
          burn :+ Take(Piece.Banner(Banner.DarkestSecret), c.actor,
            Location.PlayArea(defenderId), Location.PlayArea(c.actor))
      }
    val adviserDiscards = e.discardedAdvisers.map { id =>
      CoreMove(Piece.Card(id),
        PositionedLocation(Location.PlayArea(defenderId)),
        PositionedLocation(Location.RegionalDiscard(e.adviserDiscardRegion),
          StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown))
    }
    val setAside = e.discardedRelics.map { id =>
      // Facedown relics leave until the Chronicle. SetAsideRelics enforces
      // empty tokens on entry, so a token-carrying facedown relic rejects the
      // batch loudly instead of silently carrying tokens away (no current flow
      // produces one — a raid only sets aside token-less facedown relics).
      CoreMove(Piece.Card(id),
        PositionedLocation(Location.PlayArea(defenderId)),
        PositionedLocation(Location.SetAsideRelics))
    }
    val favorBurn = Option.when(e.favorBurned > 0)(
      oathdigital.gameplay.operations.Burn.favor(e.favorBurned,
        PositionedLocation(Location.PlayArea(defenderId)))).toVector
    val defenderKills = killAt(defenderForce, e.defenderLoss.killed,
      Location.PlayArea(defenderId))
    val attackerDeaths = killAt(attackerForce,
      c.skullLosses + c.sacrificed.get, Location.PlayArea(c.actor))
    val relocation = PendingProcedure.CampaignRaidRelocation(c.decision,
      c.actor, defenderId, defender.pawnSite.get,
      CampaignRules.legalRaidRelocationSites(ready, defenderId))
    val ops: Vector[oathdigital.gameplay.operations.CoreOperation] =
      (relicTakes: Vector[oathdigital.gameplay.operations.CoreOperation]) ++
      (bannerOps: Vector[oathdigital.gameplay.operations.CoreOperation]) ++
      (adviserDiscards: Vector[oathdigital.gameplay.operations.CoreOperation]) ++
      (setAside: Vector[oathdigital.gameplay.operations.CoreOperation]) ++
      (favorBurn: Vector[oathdigital.gameplay.operations.CoreOperation]) ++
      (defenderKills: Vector[oathdigital.gameplay.operations.CoreOperation]) ++
      (attackerDeaths: Vector[oathdigital.gameplay.operations.CoreOperation])
    execute(ready, ops, "Campaign raid is not permitted") { state =>
      Right(GameStateUpdates.updateCurrent(state)(current =>
        current.copy(pending = Some(relocation))))
    }.map { state =>
      // Conspiracy leaves the game only after the batch validates (the
      // executor conserves card inventory), mirroring the Visions slice.
      // executor bypass: Conspiracy is removed from the game after the batch.
      e.boxedConspiracy.fold(state) { conspiracy =>
        GameStateUpdates.updateCurrent(state)(current =>
          current.copy(players = current.players.map(p =>
            if (p.player != defenderId) p else p.copy(advisers =
              p.advisers.filterNot(_.id == conspiracy)))))
      }
    }
  }
}
