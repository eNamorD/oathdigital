package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** The battle-plan vocabulary the plan window and the powers that offer plans
  * share: who uses a plan, how a plan's source is named in a decision, and the
  * order the plans are listed in. The plans themselves are `Offer`
  * contributions of powers, folded by [[CampaignPlanChoice]].
  */
object CampaignPlans {
  /** The player who uses a plan on `side`: the attacker, or a player defender.
    * `None` for a bandit defender, which uses plans without choosing them.
    */
  def userOf(setup: CampaignSetup, side: CampaignPlanSide): Option[PlayerId] =
    side match {
      case CampaignPlanSide.Attacker => Some(setup.actor)
      case CampaignPlanSide.Defender => setup.defender match {
        case CampaignDefender.Player(player) => Some(player)
        case CampaignDefender.Bandits => None
      }
    }

  /** The card a plan's source is printed on. The title has none. */
  def cardOf(source: CampaignPlanSource): Option[CardId] = source match {
    case CampaignPlanSource.Adviser(_, id) => Some(id)
    case CampaignPlanSource.SiteCard(_, id) => Some(id)
    case CampaignPlanSource.SiteEdifice(_, id) => Some(id)
    case CampaignPlanSource.Relic(_, id) => Some(id)
    case CampaignPlanSource.Title(_) => None
  }

  /** What a decision option names a source by: the card, or the title's button. */
  def refOf(source: CampaignPlanSource): DecisionOptionRef = source match {
    case CampaignPlanSource.Adviser(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.SiteCard(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.SiteEdifice(_, id) => DecisionOptionRef.Edifice(id)
    case CampaignPlanSource.Relic(_, id) => DecisionOptionRef.Relic(id)
    case CampaignPlanSource.Title(_) => DecisionOptionRef.Button("title")
  }

  /** The pool a bandit defender's application of the plan named by `ref` is
    * recorded in. A plan a player chooses is an answer, which a later window
    * reads; a bandit chooses nothing, so its plan is recorded here instead. The
    * pool is never rolled, and it is cleared with the others when the Campaign
    * ends.
    */
  def appliedMarker(ref: DecisionOptionRef): PoolKey =
    PoolKey(s"campaign.plan-applied.${ref.kind}.${ref.wireId}")

  /** A card is named by the projector; the title has no card, so its button
    * carries the label its offer authored.
    */
  def optionOf(offered: OfferedPlan): DecisionOption = offered.offer.source match {
    case CampaignPlanSource.Title(_) => DecisionOption.Button(
      DecisionOptionRef.Button("title"), offered.offer.label)
    case source => DecisionOption.forRef(refOf(source)).getOrElse(
      throw new IllegalStateException(s"no option for plan source $source"))
  }

  /** The title first, then advisers, relics, and cards at sites. */
  private def order(source: CampaignPlanSource): Int = source match {
    case _: CampaignPlanSource.Title => 0
    case _: CampaignPlanSource.Adviser => 100
    case _: CampaignPlanSource.Relic => 200
    case _: CampaignPlanSource.SiteCard => 300
    case _: CampaignPlanSource.SiteEdifice => 300
  }

  /** The offers in a stable order: by kind of source, then source, then power. */
  def sorted(offers: Vector[OfferedPlan]): Vector[OfferedPlan] =
    offers.sortBy(o => (order(o.offer.source), o.offer.source.stableKey,
      o.power.value))
}
