package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.model._

object NatureWorshipCard extends Denizen(DenizenId("175"), "Nature Worship", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.nature-worship"),
    persistent = false, cost = Cost(secret = 1),
    text = "± [attack-die] per [suit-beast] adviser you have.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Nature Worship (card 175), a battle plan for either side: "[secret] ±
  * [attack-die] per [suit-beast] adviser you have."
  *
  * A secret is placed onto the card. The dice are one per faceup beast
  * adviser its user has, since only a faceup card has a suit. It counts
  * itself when it is an adviser: a facedown one is revealed when chosen, so
  * it is counted whatever its face now, and the count does not change when
  * the plan reveals it. At a site it is not an adviser and counts only the
  * others. A plan that would add no die is not offered.
  */
final case class NatureWorship private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = NatureWorshipCard.id
  def id: PowerId = NatureWorship.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).flatMap { source =>
      val itself = source match
        case _: CampaignPlanSource.Adviser => 1
        case _ => 0
      val dice = otherBeasts(context) + itself
      Option.when(dice > 0)(CampaignPlanOffer(source,
        PlanDice.label("Nature Worship", context.side, dice),
        Vector(CampaignPlanCost.Secret(1)),
        Vector(PlanDice.effect(context.side, dice))))
    }

  /** The user's faceup beast advisers other than this card. */
  private def otherBeasts(context: PlanContext): Int =
    context.ready.game.current.players.filter(p => context.user.contains(p.player))
      .flatMap(_.advisers).count {
        case DenizenState(held, Orientation.FaceUp, _) =>
          held != cardId && catalog.suitOf(held).contains(Suit.Beast)
        case _ => false
      }

object NatureWorship:
  val id: PowerId = NatureWorshipCard.power.id

  def forCatalog(catalog: ExecutableCatalog): NatureWorship =
    new NatureWorship(catalog)
