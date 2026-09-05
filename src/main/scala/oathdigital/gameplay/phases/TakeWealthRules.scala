package oathdigital.gameplay.phases

import oathdigital.gameplay._
import oathdigital.gameplay.OathViolation._
import oathdigital.model._

/** Wake-owned take-wealth legality. `TurnState.usedPowers` per-site use tracking
  * stays outside the power framework (program Decision 11); this object keeps
  * the typed query in the phase module that owns the action.
  */
object TakeWealthRules {
  def validate(
      ready: ReadyGame,
      player: PlayerState,
      siteId: SiteId,
      resource: WakeResource
  ): Either[OathViolation, Unit] = {
    val context = RuleQueryContext.TakeWealth(ready, player, siteId, resource)
    val power = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(siteId),
      PowerId("site.take-wealth"))
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
