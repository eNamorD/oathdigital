package oathdigital.gameplay

import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathViolation._

/** Stable identity for a runtime rule source. PowerUseRef remains the narrower,
  * wire-compatible identity for use-limited powers.
  */
sealed trait RuleSourceRef extends Product with Serializable {
  def stableKey: String
}
object RuleSourceRef {
  final case class Site(id: SiteId) extends RuleSourceRef {
    def stableKey: String = s"site:${id.value}"
  }
  final case class SiteCard(siteId: SiteId, id: CardId) extends RuleSourceRef {
    def stableKey: String = s"site-card:${siteId.value}:${id.kind}:${id.value}"
  }
  final case class Adviser(playerId: PlayerId, id: CardId) extends RuleSourceRef {
    def stableKey: String = s"adviser:${playerId.value}:${id.kind}:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId) extends RuleSourceRef {
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
  }
  final case class Edifice(siteId: SiteId, id: EdificeId) extends RuleSourceRef {
    def stableKey: String = s"edifice:${siteId.value}:${id.value}"
  }
  final case class Banner(id: String) extends RuleSourceRef {
    def stableKey: String = s"banner:$id"
  }
  final case class Foundation(number: FoundationNumber) extends RuleSourceRef {
    def stableKey: String = s"foundation:${number.value}"
  }
  final case class Legacy(lineageId: LineageId, id: LegacyId)
      extends RuleSourceRef {
    def stableKey: String = s"legacy:${lineageId.value}:${id.value}"
  }
  final case class GameRule(id: String) extends RuleSourceRef {
    def stableKey: String = s"game:$id"
  }
}

sealed trait RuleQueryContext extends Product with Serializable
object RuleQueryContext {
  final case class Travel(
      ready: ReadyGame,
      player: PlayerState,
      source: SiteId,
      destination: SiteId,
      from: Region,
      to: Region,
      baseCost: Int
  ) extends RuleQueryContext

  final case class TakeWealth(
      ready: ReadyGame,
      player: PlayerState,
      siteId: SiteId,
      resource: WakeResource
  ) extends RuleQueryContext

  final case class Economy(ready: ReadyGame, player: PlayerState)
      extends RuleQueryContext
}

final case class RuleActivation(
    source: RuleSourceRef,
    handlerId: String,
    priority: Int
)

sealed trait RuleOutcome extends Product with Serializable
object RuleOutcome {
  case object Allow extends RuleOutcome
  final case class Block(violation: OathViolation) extends RuleOutcome
  final case class ModifyCost(value: Int, replace: Boolean = false)
      extends RuleOutcome
  final case class RequireDecision(decision: RuleDecisionBoundary)
      extends RuleOutcome
  final case class PostActionEffect(id: String) extends RuleOutcome
  final case class UnsupportedRelevantRule(handlerId: String)
      extends RuleOutcome
}

/** Minimal seam for a later command-owned PendingProcedure; this slice does not
  * create a decision or implement Search.
  */
final case class RuleDecisionBoundary(
    decision: DecisionId,
    actor: PlayerId,
    source: RuleSourceRef
)

final case class ResolvedRule(
    activation: RuleActivation,
    outcome: RuleOutcome
)

trait TypedRuleHandler {
  def travelModifierKind: Option[TravelModifierKind] = None
  def resolve(
      activation: RuleActivation,
      context: RuleQueryContext
  ): RuleOutcome
}

sealed trait TravelModifierKind extends Product with Serializable
object TravelModifierKind {
  case object Coast extends TravelModifierKind
  case object Island extends TravelModifierKind
  case object Mountain extends TravelModifierKind
  case object Pass extends TravelModifierKind
}

/** Explicit registry for handlers activated by a caller. Activations resolve by
  * priority, then stable source identity, then handler ID. This total ordering is
  * the precedence contract used by command handling, projections, and replay.
  */
final class RuleRegistry private (
    handlers: Map[String, TypedRuleHandler]
) {
  def lookup(handlerId: String): Option[TypedRuleHandler] = handlers.get(handlerId)

  def resolve(
      activations: Vector[RuleActivation],
      context: RuleQueryContext
  ): Vector[ResolvedRule] =
    activations.sortBy(value =>
      (value.priority, value.source.stableKey, value.handlerId)).map { activation =>
      ResolvedRule(
        activation,
        lookup(activation.handlerId)
          .map(_.resolve(activation, context))
          .getOrElse(RuleOutcome.UnsupportedRelevantRule(activation.handlerId))
      )
    }
}

object RuleRegistry {
  def apply(entries: (String, TypedRuleHandler)*): RuleRegistry = {
    require(entries.map(_._1).distinct.size == entries.size,
      "rule handler IDs must be unique")
    new RuleRegistry(entries.toMap)
  }
}

object RuntimeRuleRegistry {
  import RuleOutcome._

  private val coast = new TypedRuleHandler {
    override val travelModifierKind = Some(TravelModifierKind.Coast)
    def resolve(a: RuleActivation, context: RuleQueryContext): RuleOutcome =
      context match {
        case _: RuleQueryContext.Travel => ModifyCost(1, replace = true)
        case _ => Allow
      }
  }
  private val island = new TypedRuleHandler {
    override val travelModifierKind = Some(TravelModifierKind.Island)
    def resolve(a: RuleActivation, context: RuleQueryContext): RuleOutcome =
      context match {
        case _: RuleQueryContext.Travel => ModifyCost(2)
        case _ => Allow
      }
  }
  private val mountain = new TypedRuleHandler {
    override val travelModifierKind = Some(TravelModifierKind.Mountain)
    def resolve(a: RuleActivation, context: RuleQueryContext): RuleOutcome =
      context match {
        case _: RuleQueryContext.Travel => ModifyCost(1)
        case _ => Allow
      }
  }
  private val pass = new TypedRuleHandler {
    override val travelModifierKind = Some(TravelModifierKind.Pass)
    def resolve(activation: RuleActivation, context: RuleQueryContext): RuleOutcome =
      (activation.source, context) match {
        case (RuleSourceRef.Site(pass), travel: RuleQueryContext.Travel)
            if pass == travel.destination || travel.from == travel.to => Allow
        case (RuleSourceRef.Site(pass), travel: RuleQueryContext.Travel) =>
          SiteRule.ruler(travel.ready.game.current.map.sites(pass).forces,
            travel.ready.game.current.players) match {
            case Right(SiteRuler.Player(player))
                if player == travel.player.player => Allow
            case Right(SiteRuler.Player(player)) =>
              Block(TravelConsentUnsupported(pass, player))
            case Right(_) => Block(TravelPassBlocked(pass, travel.destination))
            case Left(error) => Block(UnsupportedTravelState(
              s"invalid site ruler mapping: $error"))
          }
        case _ => Allow
      }
  }

  // Catalog handler IDs are durable identifiers. Listing them here is
  // intentional: catalog presence alone never activates arbitrary behavior.
  val default: RuleRegistry = RuleRegistry(
    "site.broken-peaks.mountain" -> mountain,
    "site.desolate-shore.coast" -> coast,
    "site.fair-isle.coast" -> coast,
    "site.fair-isle.island" -> island,
    "site.green-shore.coast" -> coast,
    "site.headwaters.mountain" -> mountain,
    "site.hidden-place.mountain" -> mountain,
    "site.mines.mountain" -> mountain,
    "site.narrow-pass.pass" -> pass,
    "site.rocky-coast.coast" -> coast,
    "site.sunken-isles.coast" -> coast,
    "site.sunken-isles.island" -> island,
    "site.tidal-marshes.coast" -> coast
  )
}

object TakeWealthRules {
  def validate(
      ready: ReadyGame,
      player: PlayerState,
      siteId: SiteId,
      resource: WakeResource
  ): Either[OathViolation, Unit] = {
    val context = RuleQueryContext.TakeWealth(ready, player, siteId, resource)
    val power = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(siteId),
      PowerId("take-wealth"))
    val enemies = ready.game.current.players.collect {
      case other if other.player != player.player &&
          other.pawnSite.contains(siteId) => other.player
    }
    ready.game.current.map.sites.get(siteId).toRight(SiteNotInPlay(siteId))
      .flatMap { site =>
        val outcomes: Vector[RuleOutcome] = Vector(
          if (ready.game.current.turn.usedPowers.contains(power))
            RuleOutcome.Block(PowerAlreadyUsed(power)) else RuleOutcome.Allow,
          if (enemies.nonEmpty)
            RuleOutcome.Block(EnemyPawnBlocksTakeWealth(siteId, enemies))
          else RuleOutcome.Allow,
          if (available(site.tokens, context.resource)) RuleOutcome.Allow
          else RuleOutcome.Block(ResourceUnavailable(siteId, resource))
        )
        outcomes.collectFirst { case RuleOutcome.Block(value) => value }
          .toLeft(())
      }
  }

  private def available(tokens: Tokens, resource: WakeResource): Boolean =
    resource match {
      case WakeResource.Favor => tokens.favor > 0
      case WakeResource.Secret => tokens.secrets > 0
    }
}
