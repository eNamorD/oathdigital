package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** What the adviser rules against an enemy's battle plans share (Beast
  * Tamer, True Names): the faceup holder, when they are the enemy of the
  * side whose plan window is being gathered, and a plan's suit.
  */
private[campaign] object EnemyPlans:
  /** The player holding `card` faceup as an adviser, when the window `ctx`
    * is gathered for is their enemy's: a defending holder at the attacker's
    * window, an attacking holder at the defender's. */
  def holderAgainst(ctx: PowerCtx, card: DenizenId): Option[PlayerId] =
    PlanContext.of(ctx).flatMap(context =>
      faceUpHolder(ctx.state, card).filter(holder =>
        context.enemy == CampaignDefender.Player(holder)))

  private def faceUpHolder(ready: ReadyGame, card: DenizenId)
      : Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(held, Orientation.FaceUp, _) => held == card
      case _ => false
    }).map(_.player)

  /** The suit of the plan named by `ref`: its source card's, as the rules
    * see it (`EffectiveSuit`). A relic and the title have none. */
  def suitOf(catalog: ExecutableCatalog, ready: ReadyGame,
      ref: DecisionOptionRef): Option[Suit] = ref match
    case DecisionOptionRef.Denizen(id) => EffectiveSuit.of(catalog, ready, id)
    case DecisionOptionRef.Edifice(id) => EffectiveSuit.of(catalog, ready, id)
    case _ => None
