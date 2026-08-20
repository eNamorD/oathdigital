package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{CampaignTimingWindow, RuleActivation, RuleSourceRef}
import oathdigital.model._
import oathdigital.model.PendingProcedure._
import oathdigital.setup._
import oathdigital.setup.OathViolation._

/** A server-authored, window-scoped Campaign option. Campaign orchestrates the
  * windows; registered handlers own printed availability, costs and effects.
  */
final case class CampaignPlanOption(
    source: CampaignPlanSource,
    handlerId: String,
    side: CampaignPlanSide,
    decisionOwner: PlayerId,
    label: String,
    description: String,
    costs: Vector[CampaignPlanCost],
    effects: Vector[CampaignPlanEffect],
    order: Int)

final case class CampaignPlanContext(catalog: ExecutableCatalog, ready: ReadyGame,
    campaign: Campaign, side: CampaignPlanSide, decisionOwner: PlayerId)

trait CampaignPlanHandler {
  def id: String
  def side: CampaignPlanSide
  def option(context: CampaignPlanContext, activation: RuleActivation)
      : Either[OathViolation, Option[CampaignPlanOption]]
  def validateRecorded(context: CampaignPlanContext,
      resolution: CampaignPlanResolution): Either[OathViolation, Unit] =
    option(context, CampaignPlanRegistry.activationFor(resolution.source, id, 0))
      .flatMap(_.toRight(CampaignPlanUnavailable(
        s"Campaign plan '${resolution.source.stableKey}' is no longer available")))
      .flatMap(expected => Either.cond(
        expected.handlerId == resolution.handlerId &&
          expected.side == resolution.side && expected.costs == resolution.costs &&
          expected.effects == resolution.effects, (), CampaignOutcomeMismatch(
            s"recorded Campaign plan '${resolution.source.stableKey}' is invalid")))
}

object CampaignPlanEffects {
  /** Extension effects are part of the durable vocabulary so later handlers can
    * model them without changing the event shape. Until an executor is wired
    * into Campaign, reject them explicitly instead of silently ignoring them.
    */
  def validateExecutable(effects: Vector[CampaignPlanEffect])
      : Either[OathViolation, Unit] = effects.collectFirst {
    case CampaignPlanEffect.TransformAttackResult(id) =>
      s"attack-result transform '$id'"
    case CampaignPlanEffect.ReplaceLosingForcePolicy(id) =>
      s"losing-force policy '$id'"
    case CampaignPlanEffect.Suspend(kind) =>
      s"suspended decision '$kind'"
  }.fold[Either[OathViolation, Unit]](Right(()))(effect =>
    Left(CampaignPlanUnavailable(
      s"Campaign plan effect $effect has no registered executor")))

  def attackDice(effects: Vector[CampaignPlanEffect]): Int = effects.collect {
    case CampaignPlanEffect.AddAttackDice(count) => count
  }.sum
  def defenseDice(effects: Vector[CampaignPlanEffect]): Int = effects.collect {
    case CampaignPlanEffect.AddDefenseDice(count) => count
  }.sum
  def ignoreAttackSkulls(effects: Vector[CampaignPlanEffect]): Boolean =
    effects.contains(CampaignPlanEffect.IgnoreAttackSkulls)
  def revealed(effects: Vector[CampaignPlanEffect]): Boolean =
    effects.contains(CampaignPlanEffect.RevealSource)
  def favorCost(costs: Vector[CampaignPlanCost]): Int = costs.collect {
    case CampaignPlanCost.Favor(count) => count
  }.sum
  def secretCost(costs: Vector[CampaignPlanCost]): Int = costs.collect {
    case CampaignPlanCost.Secret(count) => count
  }.sum
}

object CampaignPlanRegistry {
  private val handlers: Vector[CampaignPlanHandler] = Vector(
    CampaignPlanHandlers.Outriders,
    CampaignPlanHandlers.BrassArmy,
    CampaignPlanHandlers.OathkeeperTitle,
    CampaignPlanHandlers.Watchdog)
  private val byId = handlers.map(h => h.id -> h).toMap
  require(byId.size == handlers.size, "Campaign plan handler IDs must be unique")
  def windowFor(handlerId: String): Option[CampaignTimingWindow] =
    byId.get(handlerId).map(_.side match {
      case CampaignPlanSide.Attacker => CampaignTimingWindow.AttackerBattlePlans
      case CampaignPlanSide.Defender => CampaignTimingWindow.DefenderBattlePlansAndRoll
    })

  private[actions] def activationFor(source: CampaignPlanSource, handlerId: String,
      priority: Int): RuleActivation = RuleActivation(source match {
    case CampaignPlanSource.Adviser(player, id) => RuleSourceRef.Adviser(player, id)
    case CampaignPlanSource.SiteCard(site, id) => RuleSourceRef.SiteCard(site, id)
    case CampaignPlanSource.Relic(player, id) => RuleSourceRef.Relic(player, id)
    case CampaignPlanSource.Title(player) => RuleSourceRef.GameRule(
      s"oathkeeper-title:${player.value}")
  }, handlerId, priority)

  def sourceOf(activation: RuleActivation): Option[CampaignPlanSource] =
    activation.source match {
      case RuleSourceRef.Adviser(player, id: DenizenId) =>
        Some(CampaignPlanSource.Adviser(player, id))
      case RuleSourceRef.SiteCard(site, id: DenizenId) =>
        Some(CampaignPlanSource.SiteCard(site, id))
      case RuleSourceRef.Relic(player, id) => Some(CampaignPlanSource.Relic(player, id))
      case _ => None
    }

  def options(context: CampaignPlanContext,
      activations: Vector[RuleActivation]): Vector[CampaignPlanOption] = {
    val printed = activations.flatMap(a => byId.get(a.handlerId).toVector
      .filter(_.side == context.side).flatMap(_.option(context, a).toOption.flatten))
    val title = if (context.side == CampaignPlanSide.Defender &&
        context.campaign.defender == CampaignDefender.Player(context.decisionOwner))
      byId(CampaignPlanHandlers.OathkeeperTitle.id).option(context,
        activationFor(CampaignPlanSource.Title(context.decisionOwner),
          CampaignPlanHandlers.OathkeeperTitle.id, 0)).toOption.flatten.toVector
    else Vector.empty
    (title ++ printed).sortBy(o => (o.order, o.source.stableKey, o.handlerId))
  }

  def resolve(context: CampaignPlanContext, selected: CampaignPlanSource,
      activations: Vector[RuleActivation]): Either[OathViolation, CampaignPlanResolution] =
    options(context, activations).find(_.source == selected).toRight(
      CampaignPlanUnavailable(s"Campaign plan source '${selected.stableKey}' is stale, inaccessible, or unsupported"))
      .flatMap(o => CampaignPlanEffects.validateExecutable(o.effects).map(_ =>
        CampaignPlanResolution(o.source, o.handlerId, o.side, o.costs, o.effects)))

  def validate(context: CampaignPlanContext,
      resolution: CampaignPlanResolution): Either[OathViolation, Unit] =
    byId.get(resolution.handlerId).toRight(CampaignOutcomeMismatch(
      s"unknown Campaign plan handler '${resolution.handlerId}'"))
      .flatMap(_.validateRecorded(context, resolution))
      .flatMap(_ => CampaignPlanEffects.validateExecutable(resolution.effects))

  /** Bandits have no decision owner. They use every applicable cost-free,
    * choice-free registered defender plan in stable order. Any effect that
    * suspends for a choice is deliberately excluded and remains a blocker.
    */
  def deterministicBandit(context: CampaignPlanContext,
      activations: Vector[RuleActivation]): Either[OathViolation,
      Vector[CampaignPlanResolution]] = {
    val available = options(context, activations)
    available.foldLeft[Either[OathViolation, Vector[CampaignPlanResolution]]](
      Right(Vector.empty)) { (result, option) => result.flatMap { accepted =>
        if (option.costs.nonEmpty)
          Left(CampaignPlanUnavailable(
            s"bandit plan '${option.handlerId}' is not deterministic"))
        else CampaignPlanEffects.validateExecutable(option.effects).map(_ =>
          accepted :+ CampaignPlanResolution(option.source,
            option.handlerId, option.side, option.costs, option.effects))
      }}
  }
}

private object CampaignPlanHandlers {
  object Outriders extends CampaignPlanHandler {
    val id = "denizen.outriders"
    val side = CampaignPlanSide.Attacker
    def option(context: CampaignPlanContext, activation: RuleActivation) = {
      val source = CampaignPlanRegistry.sourceOf(activation)
      Right(source.map { selected =>
        val reveal = selected match {
          case CampaignPlanSource.Adviser(player, card) => context.ready.game.current.players
            .find(_.player == player).exists(_.advisers.exists {
              case d: DenizenState => d.id == card && d.orientation == Orientation.FaceDown
              case _ => false
            })
          case CampaignPlanSource.SiteCard(site, card) => context.ready.game.current.map.sites
            .get(site).exists(_.denizens.exists {
              case d: DenizenState => d.id == card && d.orientation == Orientation.FaceDown
              case _ => false
            })
          case _ => false
        }
        CampaignPlanOption(selected, id, side, context.decisionOwner, "Outriders",
          "Ignore all attack-roll skull losses", Vector.empty,
          Vector(Option.when(reveal)(CampaignPlanEffect.RevealSource),
            Some(CampaignPlanEffect.IgnoreAttackSkulls)).flatten, activation.priority)
      })
    }
    override def validateRecorded(context: CampaignPlanContext,
        resolution: CampaignPlanResolution) = {
      val validSource = resolution.source match {
        case CampaignPlanSource.Adviser(player, card) => player == context.campaign.actor &&
          context.ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
            case d: DenizenState => d.id == card && d.orientation == Orientation.FaceUp
            case _ => false
          })
        case CampaignPlanSource.SiteCard(site, card) =>
          context.ready.game.current.map.sites.get(site).exists(_.denizens.exists {
            case d: DenizenState => d.id == card && d.orientation == Orientation.FaceUp
            case _ => false
          })
        case _ => false
      }
      val base = Vector[CampaignPlanEffect](CampaignPlanEffect.IgnoreAttackSkulls)
      Either.cond(resolution.handlerId == id && resolution.side == side &&
        resolution.costs.isEmpty && validSource &&
        (resolution.effects == base || resolution.effects ==
          Vector(CampaignPlanEffect.RevealSource, CampaignPlanEffect.IgnoreAttackSkulls)), (),
        CampaignOutcomeMismatch(s"recorded Campaign plan '${resolution.source.stableKey}' is invalid"))
    }
  }

  object BrassArmy extends CampaignPlanHandler {
    val id = "relic.brass-army"
    val side = CampaignPlanSide.Attacker
    def option(context: CampaignPlanContext, activation: RuleActivation) =
      CampaignPlanRegistry.sourceOf(activation) match {
        case Some(source @ CampaignPlanSource.Relic(player, relicId)) =>
          val owner = context.ready.game.current.players.find(_.player == player)
          val legal = player == context.campaign.actor && owner.exists(p =>
            p.board.faceUpSecrets >= 1 && p.relics.exists(r => r.id == relicId &&
              r.orientation == Orientation.FaceUp && r.tokens.isEmpty))
          Right(Option.when(legal)(CampaignPlanOption(source, id, side,
            context.decisionOwner, "Brass Army", "Add 4 attack dice",
            Vector(CampaignPlanCost.Secret(1)),
            Vector(CampaignPlanEffect.AddAttackDice(4)), activation.priority)))
        case _ => Right(None)
      }
    override def validateRecorded(context: CampaignPlanContext,
        resolution: CampaignPlanResolution) = {
      val validSource = resolution.source match {
        case CampaignPlanSource.Relic(player, relicId) => player == context.campaign.actor &&
          context.ready.game.current.players.find(_.player == player).exists(_.relics.exists(
            r => r.id == relicId && r.orientation == Orientation.FaceUp &&
              r.tokens == Tokens(0, 1)))
        case _ => false
      }
      Either.cond(resolution.handlerId == id && resolution.side == side && validSource &&
        resolution.costs == Vector(CampaignPlanCost.Secret(1)) &&
        resolution.effects == Vector(CampaignPlanEffect.AddAttackDice(4)), (),
        CampaignOutcomeMismatch(s"recorded Campaign plan '${resolution.source.stableKey}' is invalid"))
    }
  }

  object OathkeeperTitle extends CampaignPlanHandler {
    val id = "title.oathkeeper-defense"
    val side = CampaignPlanSide.Defender
    def option(context: CampaignPlanContext, activation: RuleActivation) = {
      val title = context.ready.game.current.title
      Right(Option.when(title.holder.contains(context.decisionOwner)) {
        val count = title.side match {
          case TitleSide.Oathkeeper => 1
          case TitleSide.Usurper => 2
        }
        CampaignPlanOption(CampaignPlanSource.Title(context.decisionOwner), id,
          side, context.decisionOwner, title.side.toString,
          s"Add $count defense ${if (count == 1) "die" else "dice"}", Vector.empty,
          Vector(CampaignPlanEffect.AddDefenseDice(count)), 0)
      })
    }
  }

  object Watchdog extends CampaignPlanHandler {
    val id = "denizen.watchdog"
    val side = CampaignPlanSide.Defender
    def option(context: CampaignPlanContext, activation: RuleActivation) = {
      val inCradle = context.campaign.targetSites.exists(site =>
        context.ready.game.current.map.regionOf(site).contains(Region.Cradle))
      Right(CampaignPlanRegistry.sourceOf(activation).filter(_ => inCradle).map(
        source => CampaignPlanOption(source, id, side, context.decisionOwner,
          "Watchdog", "Add 1 defense die", Vector.empty,
          Vector(CampaignPlanEffect.AddDefenseDice(1)), activation.priority)))
    }
  }
}
