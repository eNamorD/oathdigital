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
  final case class Start(playerId: PlayerId, decision: DecisionId, siteId: SiteId,
      force: Int) extends CampaignCommand
  final case class ChoosePlan(playerId: PlayerId, decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource) extends CampaignCommand
  final case class FinishPlans(playerId: PlayerId, decision: DecisionId,
      attackDice: Vector[AttackDieFace]) extends CampaignCommand
  final case class Sacrifice(playerId: PlayerId, decision: DecisionId, count: Int,
      defenseDice: Vector[DefenseDieFace]) extends CampaignCommand
  final case class Place(playerId: PlayerId, decision: DecisionId, count: Int)
      extends CampaignCommand
}

object Campaign {
  val SupplyCost = 2
  val MinimumForce = 1

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
      : Either[OathViolation, OathTransition] = command match {
    case CampaignCommand.Start(player, decision, site, force) =>
      OathLifecycle.validateAct(state, player).flatMap { ready =>
        CampaignRules.validateStart(catalog, ready, player, site, force).flatMap { _ =>
          transition(catalog, state, Vector(CampaignStarted(player, decision, site,
            SupplyCost, force)), AwaitingCampaignPlan(player, decision))
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
          val defense = DefenseDieFace.score(dice) + CampaignRules.banditForce(ready, pending.site)
          transition(catalog, state, Vector(CampaignSacrificed(player, decision,
            count, dice, attack, defense, pending.skullLosses, attack > defense)),
            if (attack > defense) AwaitingCampaignPlacement(player, decision)
            else ActActionSelection(player))
        }
      case _ => Left(GameNotStarted)
    }
    case CampaignCommand.Place(player, decision, count) => state match {
      case Ready(ready) => validatePending(ready, player, decision, requireResolved = true)
        .flatMap(p => transition(catalog, state, Vector(CampaignConquered(
          player, decision, p.site, count)), ActActionSelection(player)))
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
      : Either[OathViolation, OathState] = event match {
    case e: CampaignStarted => OathLifecycle.validateAct(state, e.playerId).flatMap { ready =>
      for {
        _ <- CampaignRules.validateStart(catalog, ready, e.playerId, e.siteId, e.force)
        _ <- if (e.supplySpent == SupplyCost) Right(()) else Left(CampaignOutcomeMismatch("recorded Supply cost is invalid"))
      } yield {
        val current = ready.game.current
        Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
          players = current.players.map(p => if (p.player != e.playerId) p else
            p.copy(board = p.board.copy(warbands = p.board.warbands - e.force,
              supply = SupplyTrack(p.board.supply.supply - SupplyCost)))),
          pending = Some(PendingProcedure.Campaign(e.decision, e.playerId, e.siteId,
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
        val expectedDefense = DefenseDieFace.score(e.defenseDice) + CampaignRules.banditForce(ready, c.site)
        for {
          _ <- if (e.sacrificed >= 0 && e.sacrificed <= remaining) Right(()) else Left(CampaignOutcomeMismatch("sacrifice exceeds surviving force"))
          _ <- if (e.skullLosses == c.skullLosses) Right(()) else Left(CampaignOutcomeMismatch("recorded skull losses are invalid"))
          definition <- CampaignRules.siteDefinition(catalog, c.site).toRight(SiteNotInPlay(c.site))
          _ <- if (e.defenseDice.size == definition.defense) Right(()) else Left(CampaignOutcomeMismatch("defense dice count is invalid"))
          _ <- if (e.attack == expectedAttack && e.defense == expectedDefense && e.victorious == (expectedAttack > expectedDefense)) Right(())
            else Left(CampaignOutcomeMismatch("recorded battle outcome is invalid"))
        } yield {
          val afterSacrifice = remaining - e.sacrificed
          val current = ready.game.current
          if (e.victorious) Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            pending = Some(c.copy(sacrificed = Some(e.sacrificed), defenseDice = e.defenseDice,
              defense = Some(e.defense), victorious = Some(true))))))
          else {
            val returned = afterSacrifice - afterSacrifice / 2
            Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
              players = current.players.map(p => if (p.player != e.playerId) p else
                p.copy(board = p.board.copy(warbands = p.board.warbands + returned))),
              pending = None)))
          }
        }
      }
      case _ => Left(GameNotStarted)
    }
    case e: CampaignConquered => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision,
        requireFinished = true, requireResolved = true).flatMap { c =>
        val surviving = c.force - c.skullLosses - c.sacrificed.get
        if (e.siteId != c.site) Left(CampaignOutcomeMismatch("conquest site does not match the target"))
        else if (e.placed < 0 || e.placed > surviving) Left(CampaignOutcomeMismatch("placed force exceeds survivors"))
        else {
          val current = ready.game.current
          val player = current.players.find(_.player == e.playerId).get
          val site = current.map.sites(c.site)
          val forces: SiteForces = if (e.placed == 0) SiteForces.Empty else
            SiteForces.Occupied(ForceKind.Exile(player.lineage), e.placed)
          Right(Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            players = current.players.map(p => if (p.player != e.playerId) p else
              p.copy(board = p.board.copy(warbands = p.board.warbands + surviving - e.placed))),
            map = current.map.copy(sites = current.map.sites.updated(c.site, site.copy(forces = forces))),
            pending = None))))
        }
      }
      case _ => Left(GameNotStarted)
    }
    case _ => Left(InvalidEventOrder("Campaign received a non-Campaign event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue) =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (s, e) => s.flatMap(evolve(catalog, _, e))).map(OathTransition(_, events, continue))
}

object CampaignRules {
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

  def banditForce(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites.get(site).collect {
      case SiteState(SiteForces.Occupied(ForceKind.Bandit, count), _, _, _) => count
    }.getOrElse(0)

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
    else accessibleRules(catalog, ready, campaign.actor, campaign.site).collect {
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
    .filter(hasFormationResources).flatMap(_.pawnSite).filter { site =>
      ready.game.current.map.sites.get(site).exists(_.forces match {
        case SiteForces.Occupied(ForceKind.Bandit, _) => true
        case _ => false
      }) && validateSupported(catalog, ready, playerId, site).isRight
    }

  def validateStart(catalog: ExecutableCatalog, ready: ReadyGame, playerId: PlayerId,
      site: SiteId, force: Int): Either[OathViolation, Unit] = {
    val current = ready.game.current
    val player = current.players.find(_.player == playerId).get
    for {
      pawn <- player.pawnSite.toRight(PawnSiteMissing(playerId))
      _ <- if (pawn == site) Right(()) else Left(CampaignUnavailable("target must be the pawn site"))
      target <- current.map.sites.get(site).toRight(SiteNotInPlay(site))
      _ <- target.forces match {
        case SiteForces.Occupied(ForceKind.Bandit, _) => Right(())
        case _ => Left(CampaignUnavailable("bounded Conquest supports bandit-ruled sites only"))
      }
      _ <- if (player.board.supply.supply >= Campaign.SupplyCost) Right(()) else Left(InsufficientSupply(Campaign.SupplyCost, player.board.supply.supply))
      _ <- if (force >= Campaign.MinimumForce && force <= player.board.warbands) Right(()) else Left(CampaignUnavailable("attack force must be between one and board warbands"))
      _ <- validateSupported(catalog, ready, playerId, site)
    } yield ()
  }

  private def hasFormationResources(player: PlayerState): Boolean =
    player.board.warbands >= Campaign.MinimumForce &&
      player.board.supply.supply >= Campaign.SupplyCost

  private def validateSupported(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, site: SiteId): Either[OathViolation, Unit] = {
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
        val discovered = discover(catalog, ready, playerId, site, ruled)
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
                  ready, player, site, window)).head.outcome match {
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
      playerId: PlayerId, target: SiteId, ruled: Vector[SiteId])
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
    val siteRules = (target +: ruled).distinct.flatMap { siteId =>
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
    discover(catalog, ready, playerId, target, ruled)
  }
}
