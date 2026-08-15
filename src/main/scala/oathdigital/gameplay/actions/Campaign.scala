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
      force: Int, attackDice: Vector[AttackDieFace]) extends CampaignCommand
  final case class Sacrifice(playerId: PlayerId, decision: DecisionId, count: Int,
      defenseDice: Vector[DefenseDieFace]) extends CampaignCommand
  final case class Place(playerId: PlayerId, decision: DecisionId, count: Int)
      extends CampaignCommand
}

object Campaign {
  val SupplyCost = 2

  def handle(catalog: ExecutableCatalog, state: OathState, command: CampaignCommand)
      : Either[OathViolation, OathTransition] = command match {
    case CampaignCommand.Start(player, decision, site, force, dice) =>
      OathLifecycle.validateAct(state, player).flatMap { ready =>
        CampaignRules.validateStart(catalog, ready, player, site, force).flatMap { _ =>
          transition(catalog, state, Vector(CampaignStarted(player, decision, site,
            SupplyCost, force, dice)), AwaitingCampaignSacrifice(player, decision))
        }
      }
    case CampaignCommand.Sacrifice(player, decision, count, dice) => state match {
      case Ready(ready) => validatePending(ready, player, decision, requireResolved = false)
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
      decision: DecisionId, requireResolved: Boolean)
      : Either[OathViolation, PendingProcedure.Campaign] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player) Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act) Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(c: PendingProcedure.Campaign) if c.actor != player => Left(WrongPlayer(c.actor, player))
      case Some(c: PendingProcedure.Campaign) if c.decision != decision => Left(CampaignDecisionMismatch(c.decision, decision))
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
        _ <- if (e.attackDice.size == e.force) Right(()) else Left(CampaignOutcomeMismatch("attack dice must equal the committed force"))
      } yield {
        val current = ready.game.current
        val attack = AttackDieFace.score(e.attackDice)
        val skulls = AttackDieFace.skulls(e.attackDice)
        Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
          players = current.players.map(p => if (p.player != e.playerId) p else
            p.copy(board = p.board.copy(warbands = p.board.warbands - e.force,
              supply = SupplyTrack(p.board.supply.supply - SupplyCost)))),
          pending = Some(PendingProcedure.Campaign(e.decision, e.playerId, e.siteId,
            e.force, e.attackDice, attack, skulls, None, Vector.empty, None, None)))))
      }
    }
    case e: CampaignSacrificed => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision, false).flatMap { c =>
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
      case Ready(ready) => validatePending(ready, e.playerId, e.decision, true).flatMap { c =>
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
    "denizen.vow-of-peace" -> CampaignTimingWindow.TargetAndForceFormation
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

  def legalTargets(catalog: ExecutableCatalog, ready: ReadyGame, playerId: PlayerId)
      : Vector[SiteId] = ready.game.current.players.find(_.player == playerId).toVector
    .flatMap(_.pawnSite).filter { site =>
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
      _ <- if (force >= 1 && force <= player.board.warbands) Right(()) else Left(CampaignUnavailable("attack force must be between one and board warbands"))
      _ <- validateSupported(catalog, ready, playerId, site)
    } yield ()
  }

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
}
