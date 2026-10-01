package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Disgraced Captain (card 20), a battle plan for either side: "[favor]
  * [favor-burnt] ±4 [attack-die] if the defender rules an [suit-order] card
  * (even an adviser)."
  *
  * A favor is placed onto the card and a favor is burnt. An attacker adds four
  * attack dice; a defender removes four. It is offered only while the
  * Campaign's defender rules an order card (`RuledCards`). It reads the
  * defender whichever side uses it, as printed, so a defender's use checks
  * their own cards. Against bandits it holds when an order card stands at any
  * site bandits rule. Bandits pay nothing, so a bandit defender never applies
  * it.
  */
final case class DisgracedCaptain private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = DisgracedCaptain.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.cardsRuled(catalog, context.setup.defender,
        Suit.Order).nonEmpty)
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Disgraced Captain", context.side,
          DisgracedCaptain.Dice),
        Vector(CampaignPlanCost.Favor(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(PlanDice.effect(context.side, DisgracedCaptain.Dice))))

object DisgracedCaptain:
  val id: PowerId = PowerId("denizen.disgraced-captain")
  val Dice: Int = 4

  def forCatalog(catalog: ExecutableCatalog): Option[DisgracedCaptain] =
    CatalogCards.denizen(catalog, id).map(new DisgracedCaptain(_, catalog))
