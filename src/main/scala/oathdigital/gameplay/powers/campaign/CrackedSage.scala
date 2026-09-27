package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Cracked Sage (card 83), a battle plan for either side: "[secret]
  * [favor-burnt] ±4 [attack-die] if your enemy has an [suit-arcane] adviser."
  *
  * A secret is placed onto the card and a favor is burnt. An attacker adds
  * four attack dice; a defender removes four. It is offered only when the
  * enemy, the other side's player, has a faceup arcane adviser. Bandits hold
  * no advisers, so an attacker is never offered it against them.
  */
final case class CrackedSage private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = CrackedSage.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => enemyHasArcane(context))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Cracked Sage", context.side, CrackedSage.Dice),
        Vector(CampaignPlanCost.Secret(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(PlanDice.effect(context.side, CrackedSage.Dice))))

  /** The other side's player: the defender for an attacker's plan, when a
    * player defends, and the attacker for a defender's. */
  private def enemy(context: PlanContext): Option[PlayerId] = context.side match
    case CampaignPlanSide.Attacker => context.setup.defender match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None
    case CampaignPlanSide.Defender => Some(context.setup.actor)

  private def enemyHasArcane(context: PlanContext): Boolean =
    enemy(context).exists(player => context.ready.game.current.players
      .find(_.player == player).exists(_.advisers.exists {
        case DenizenState(held, Orientation.FaceUp, _) =>
          catalog.suitOf(held).contains(Suit.Arcane)
        case _ => false
      }))

object CrackedSage:
  val id: PowerId = PowerId("denizen.cracked-sage")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[CrackedSage] =
    CatalogCards.denizen(catalog, id).map(new CrackedSage(_, catalog))
