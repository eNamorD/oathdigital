package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.{CampaignTimingWindow,
  RuleActivation, RuleOutcome, RuleQueryContext, RuleSourceRef, TypedRuleHandler,
  RuleRegistry}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathViolation._

object CampaignRules {
  val Conspiracy: VisionId = VisionId("vision:conspiracy")

  def nextRegion(region: Region): Region = region match {
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
  }

  def currentPlanSide(campaign: PendingProcedure.Campaign)
      : PendingProcedure.CampaignPlanSide =
    if (!campaign.attackerPlansFinished) PendingProcedure.CampaignPlanSide.Attacker
    else PendingProcedure.CampaignPlanSide.Defender

  def planDecisionOwner(campaign: PendingProcedure.Campaign): PlayerId =
    currentPlanSide(campaign) match {
      case PendingProcedure.CampaignPlanSide.Attacker => campaign.actor
      case PendingProcedure.CampaignPlanSide.Defender => campaign.defender match {
        case CampaignDefender.Player(player) => player
        case CampaignDefender.Bandits => campaign.actor
      }
    }

  def defensePlanDice(campaign: PendingProcedure.Campaign): Int =
    CampaignPlanEffects.defenseDice(campaign.plans.flatMap(_.effects))
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
    "denizen.vow-of-peace" -> CampaignTimingWindow.TargetAndForceFormation
  )

  // These reviewed powers cannot affect a single-site Conquest against bandits.
  private val irrelevant: Set[String] = Set(
    "denizen.bear-traps", "denizen.extra-provisions",
    "denizen.gleaming-armor", "denizen.herald", "denizen.insect-swarm",
    "denizen.military-parade", "denizen.pledge-of-defense",
    "denizen.relic-hunter", "denizen.sealing-ward", "denizen.specialist",
    "denizen.true-names", "denizen.wrestlers",
    "relic.bandit-standard", "relic.fearsome-shield",
    "relic.sticky-fire", "relic.obsidian-cage.campaign",
    "relic.the-grand-scepter.campaign"
  )

  private[gameplay] def classify(handlerId: String,
      catalog: ExecutableCatalog): HandlerSupport =
    executable.get(handlerId).orElse(CampaignPlanRegistry.windowFor(handlerId))
      .map(HandlerSupport.Executable)
      .orElse(Option.when(irrelevant(handlerId))(HandlerSupport.IrrelevantToBanditConquest))
      .getOrElse {
        if (CampaignHandlerClassifications.RelevantHandlerIds(handlerId))
          HandlerSupport.Blocked("requires an unmodeled Campaign decision or effect")
        else if (CatalogHandlerInventory.fingerprint(catalog) ==
            CampaignHandlerClassifications.AuditedCatalogFingerprint)
          HandlerSupport.IrrelevantToBanditConquest
        else HandlerSupport.Blocked("handler inventory is not reviewed")
      }

  def siteDefinition(catalog: ExecutableCatalog, site: SiteId) =
    catalog.sites.find(_.id == site)

  def defenderForce(ready: ReadyGame, sites: Vector[SiteId]): Int =
    sites.flatMap(ready.game.current.map.sites.get).map(_.forces match {
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0
    }).sum

  def defenderForce(ready: ReadyGame, campaign: PendingProcedure.Campaign): Int =
    campaign.kind match {
      case CampaignKind.Conquest => defenderForce(ready, campaign.targetSites)
      case CampaignKind.Raid => campaign.defender match {
        case CampaignDefender.Player(player) => ready.game.current.players
          .find(_.player == player).map(_.board.warbands).getOrElse(0)
        case CampaignDefender.Bandits => 0
      }
    }

  def defenseDiceCount(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign): Int = {
    val printed = campaign.kind match {
      case CampaignKind.Conquest => campaign.targetSites.flatMap(
        siteDefinition(catalog, _)).map(_.defense).sum
      case CampaignKind.Raid => campaign.raidTargets.map {
        case _: CampaignRaidTarget.Pawn => 2
        case CampaignRaidTarget.Relic(_, relic) => catalog.relics
          .find(_.id.value == relic.value).map(_.defense).getOrElse(0)
        case CampaignRaidTarget.Banner(_, _) => 3
      }.sum
    }
    printed + defensePlanDice(campaign)
  }

  private[gameplay] def campaignOrigin(ready: ReadyGame,
      campaign: PendingProcedure.Campaign): Either[OathViolation, SiteId] =
    campaign.kind match {
      case CampaignKind.Conquest => campaign.targetSites.headOption.toRight(
        CampaignOutcomeMismatch("Conquest has no mandatory origin target"))
      case CampaignKind.Raid =>
        val current = ready.game.current
        for {
          attacker <- current.players.find(_.player == campaign.actor).toRight(
            CampaignOutcomeMismatch("Raid attacker is not in the game"))
          site <- attacker.pawnSite.toRight(PawnSiteMissing(campaign.actor))
          defender <- campaign.defender match {
            case CampaignDefender.Player(player) => current.players
              .find(_.player == player).toRight(CampaignOutcomeMismatch(
                "Raid defender is not in the game"))
            case _ => Left(CampaignOutcomeMismatch("Raid requires a player defender"))
          }
          _ <- Either.cond(defender.pawnSite.contains(site), (),
            CampaignOutcomeMismatch("Raid pawns are no longer co-located"))
        } yield site
    }

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
    if (campaign.defenderPlansFinished) Vector.empty
    else planOptions(catalog, ready, campaign).map(_.source)
  }

  def planOptions(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign): Vector[CampaignPlanOption] = {
    val side = currentPlanSide(campaign)
    val owner = planDecisionOwner(campaign)
    val activations = campaignOrigin(ready, campaign).toOption.toVector.flatMap(
      accessibleRules(catalog, ready, owner, _)).collect {
      case DiscoveredCampaignRule(a, HandlerSupport.Executable(window), _)
          if (side == PendingProcedure.CampaignPlanSide.Attacker &&
              window == CampaignTimingWindow.AttackerBattlePlans) ||
             (side == PendingProcedure.CampaignPlanSide.Defender &&
              window == CampaignTimingWindow.DefenderBattlePlansAndRoll) => a
    }
    CampaignPlanRegistry.options(CampaignPlanContext(catalog, ready, campaign,
      side, owner), activations).filterNot(o => campaign.plans.exists(_.source == o.source))
  }

  def deterministicBanditPlans(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign)
      : Either[OathViolation, Vector[PendingProcedure.CampaignPlanResolution]] = {
    val activations = banditRules(catalog, ready).collect {
      case DiscoveredCampaignRule(activation, HandlerSupport.Executable(
          CampaignTimingWindow.DefenderBattlePlansAndRoll), facedown)
          if !facedown => activation
    }
    CampaignPlanRegistry.deterministicBandit(CampaignPlanContext(catalog, ready,
      campaign, PendingProcedure.CampaignPlanSide.Defender, campaign.actor), activations)
  }

  def validatePlanChoice(catalog: ExecutableCatalog, ready: ReadyGame,
      campaign: PendingProcedure.Campaign,
      selected: PendingProcedure.CampaignPlanSource)
      : Either[OathViolation, PendingProcedure.CampaignPlanResolution] = {
    if (campaign.plans.exists(_.source == selected)) Left(CampaignPlanUnavailable(
      s"Campaign plan source '${selected.stableKey}' was already used"))
    else {
      val side = currentPlanSide(campaign)
      val owner = planDecisionOwner(campaign)
      campaignOrigin(ready, campaign).flatMap { site =>
        val activations = accessibleRules(catalog, ready, owner, site).map(_.activation)
        CampaignPlanRegistry.resolve(CampaignPlanContext(catalog, ready, campaign,
          side, owner), selected, activations)
      }
    }
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
        val owner = plan.side match {
          case PendingProcedure.CampaignPlanSide.Attacker => campaign.actor
          case PendingProcedure.CampaignPlanSide.Defender => campaign.defender match {
            case CampaignDefender.Player(player) => player
            case CampaignDefender.Bandits => campaign.actor
          }
        }
        CampaignPlanRegistry.validate(CampaignPlanContext(catalog, ready,
          campaign, plan.side, owner), plan).map(_ => accepted :+ plan)
      }}
  }

  def legalTargets(catalog: ExecutableCatalog, ready: ReadyGame, playerId: PlayerId)
      : Vector[SiteId] = ready.game.current.players.find(_.player == playerId).toVector
    .filter(hasCampaignSupply).flatMap(_.pawnSite).flatMap { pawn =>
      val defender = defenderAt(ready, pawn).toOption
      val candidates = pawn +: ready.game.current.map.inPlay.filter(_ != pawn).filter(
        site => defenderAt(ready, site).toOption == defender)
      if (defender.isEmpty || validateStart(catalog, ready, playerId,
          Vector(pawn), Campaign.MinimumForce).isLeft) Vector.empty
      else candidates.filter(site => site == pawn ||
        validateStart(catalog, ready, playerId,
          Vector(pawn, site), Campaign.MinimumForce).isRight)
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
        case CampaignDefender.Bandits => validateBanditDefenderSupported(
          catalog, ready, playerId, sites, force)
      }
    } yield defender
  }

  def legalRaidTargets(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId): Vector[CampaignRaidTarget] = {
    val current = ready.game.current
    current.players.find(_.player == playerId).flatMap(_.pawnSite).toVector.flatMap { site =>
      current.players.filter(p => p.player != playerId && p.pawnSite.contains(site)).flatMap {
        defender =>
          val targets = Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(defender.player)) ++
            defender.relics.filter(_.orientation == Orientation.FaceUp).map(r =>
              CampaignRaidTarget.Relic(defender.player, r.id)) ++
            Vector(
              Option.when(current.banners.peoplesFavor.holder.contains(defender.player))(
                CampaignRaidTarget.Banner(defender.player, Banner.PeoplesFavor)),
              Option.when(current.banners.darkestSecret.holder.contains(defender.player))(
                CampaignRaidTarget.Banner(defender.player, Banner.DarkestSecret))
            ).flatten
          Option.when(validateRaidStart(catalog, ready, playerId,
            Vector(targets.head), Campaign.MinimumForce).isRight)(targets).toVector.flatten
      }
    }
  }

  def validateRaidStart(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, targets: Vector[CampaignRaidTarget], force: Int)
      : Either[OathViolation, CampaignDefender] = {
    val current = ready.game.current
    val attacker = current.players.find(_.player == playerId).get
    for {
      site <- attacker.pawnSite.toRight(PawnSiteMissing(playerId))
      _ <- Either.cond(CampaignRaidTarget.isCanonical(targets), (),
        CampaignUnavailable("Raid targets must be distinct and in canonical pawn-first order"))
      defenderId = targets.head.playerId
      defender <- current.players.find(_.player == defenderId).toRight(
        CampaignUnavailable("Raid defender is not in the game"))
      _ <- Either.cond(defenderId != playerId && defender.pawnSite.contains(site), (),
        CampaignUnavailable("Raid requires a co-located enemy pawn"))
      legal = legalRaidTargetsUnchecked(current, defender)
      _ <- Either.cond(targets.forall(legal.contains), (),
        CampaignUnavailable("Raid targets must be the defender's faceup relics or held banners"))
      _ <- Either.cond(attacker.board.supply.supply >= Campaign.SupplyCost, (),
        InsufficientSupply(Campaign.SupplyCost, attacker.board.supply.supply))
      _ <- Either.cond(force >= Campaign.MinimumForce && force <= attacker.board.warbands, (),
        CampaignUnavailable("attack force must be between zero and board warbands"))
      _ <- validateSupported(catalog, ready, playerId, Vector(site))
      _ <- validateDefenderSupported(catalog, ready, defenderId, site)
    } yield CampaignDefender.Player(defenderId)
  }

  private def legalRaidTargetsUnchecked(current: CurrentGameState,
      defender: PlayerState): Vector[CampaignRaidTarget] =
    Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(defender.player)) ++
      defender.relics.filter(_.orientation == Orientation.FaceUp).map(r =>
        CampaignRaidTarget.Relic(defender.player, r.id)) ++ Vector(
        Option.when(current.banners.peoplesFavor.holder.contains(defender.player))(
          CampaignRaidTarget.Banner(defender.player, Banner.PeoplesFavor)),
        Option.when(current.banners.darkestSecret.holder.contains(defender.player))(
          CampaignRaidTarget.Banner(defender.player, Banner.DarkestSecret))).flatten

  def legalRaidRelocationSites(ready: ReadyGame, defender: PlayerId): Vector[SiteId] = {
    val origin = ready.game.current.players.find(_.player == defender).flatMap(_.pawnSite)
    ready.game.current.map.inPlay.filterNot(site => origin.contains(site))
  }

  private[gameplay] def returnBannerFavor(banks: Map[Suit, Int], amount: Int)
      : Map[Suit, Int] = BannerRules.raidFavorReturn(banks, amount)
        .groupBy(identity).view.mapValues(_.size).toMap

  private def validateDefenderSupported(catalog: ExecutableCatalog,
      ready: ReadyGame, defender: PlayerId, target: SiteId)
      : Either[OathViolation, Unit] = PowerRuntime.requireAudited(catalog)

  private def banditRules(catalog: ExecutableCatalog, ready: ReadyGame)
      : Vector[DiscoveredCampaignRule] = ready.game.current.map.inPlay.filter(site =>
    defenderAt(ready, site).contains(CampaignDefender.Bandits)).flatMap { site =>
      ready.game.current.map.sites(site).denizens.flatMap {
        case d: DenizenState => catalog.denizens.find(_.id.value == d.id.value).toVector
          .flatMap(definition => definition.handlers.map(id => DiscoveredCampaignRule(
            RuleActivation(RuleSourceRef.SiteCard(site, d.id), id, 300),
            classify(id, catalog),
            d.orientation == Orientation.FaceDown)))
        case _ => Vector.empty
      }
    }

  private def validateBanditDefenderSupported(catalog: ExecutableCatalog,
      ready: ReadyGame, attacker: PlayerId, sites: Vector[SiteId], force: Int)
      : Either[OathViolation, Unit] = {
    PowerRuntime.requireAudited(catalog).flatMap { _ =>
      val context = PendingProcedure.Campaign(DecisionId("bandit-validation"),
        attacker, sites, CampaignDefender.Bandits, force, Vector.empty,
        attackerPlansFinished = true, defenderPlansFinished = false,
        Vector.empty, 0, 0, None, Vector.empty, None, None)
      deterministicBanditPlans(catalog, ready, context).map(_ => ())
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
      if (ready.setup.foundationProfile != FirstGameFoundationProfile.FixedUnaltered ||
          game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty))
        Some("altered Foundations are not supported for Campaign")
      else if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Campaign is limited to the all-Exile first game")
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
        unsupportedBase.map(UnsupportedCampaignState).toLeft(()).flatMap(_ =>
          PowerRuntime.requireAudited(catalog)).flatMap { _ =>
          discovered.sortBy(r => (r.activation.priority,
            r.activation.source.stableKey, r.activation.handlerId)).foldLeft[
              Either[OathViolation, Unit]](Right(())) {
            case (failure @ Left(_), _) => failure
            case (Right(_), rule) => rule.support match {
              case HandlerSupport.IrrelevantToBanditConquest => Right(())
              case HandlerSupport.Blocked(_) => Right(())
              case HandlerSupport.Executable(CampaignTimingWindow.AttackerBattlePlans) =>
                Right(())
              case HandlerSupport.Executable(CampaignTimingWindow.DefenderBattlePlansAndRoll) =>
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
          classify(id, catalog), d.orientation == Orientation.FaceDown))
      }
    }.flatten
    val relics = player.relics.filter(_.orientation == Orientation.FaceUp).flatMap { relic =>
      catalog.relics.find(_.id.value == relic.id.value).toVector.flatMap { definition =>
        definition.handlers.map(id => DiscoveredCampaignRule(
          RuleActivation(RuleSourceRef.Relic(playerId, relic.id), id, 200),
          classify(id, catalog), facedown = false))
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
            classify(id, catalog), d.orientation == Orientation.FaceDown))
        }
        case e: EdificeState => catalog.edifices.find(_.id.value == e.id.value).toVector.flatMap { definition =>
          val face = if (e.side == EdificeSide.Intact) definition.intact else definition.ruined
          face.handlers.map(id => DiscoveredCampaignRule(
            RuleActivation(RuleSourceRef.Edifice(siteId, e.id), id, 400),
            classify(id, catalog), facedown = false))
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

  private[gameplay] def accessibleRules(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, target: SiteId): Vector[DiscoveredCampaignRule] = {
    val ruled = ready.game.current.map.inPlay.filter { siteId =>
      SiteRule.ruledBy(ready.game.current.map.sites(siteId).forces,
        ready.game.current.players, playerId).getOrElse(false)
    }
    discover(catalog, ready, playerId, Vector(target), ruled)
  }
}
