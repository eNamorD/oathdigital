package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Battle Honors (card 2), a battle plan for either side: "If you're victorious,
  * gain 2 favor from the Order bank."
  *
  * Choosing it costs nothing and changes no dice. Once the Campaign has resolved,
  * its user gains two favor from the Order bank if they won: the attacker when the
  * attacker won, the defender when the defender did. The gain is best-effort, so an
  * Order bank with less favor gives what it holds. A bandit defender that wins
  * gains it too, settled into the shared bank, because bandits hold no board of
  * their own. It applies the plan by itself, since the plan is free.
  */
final case class BattleHonors private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = BattleHonors.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Battle Honors: gain 2 favor if victorious", Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if (!use.won.contains(true)) Vector.empty
      else Vector(use.user.fold[Operation](toBandits)(
        Gain.Favor(_, Suit.Order, BattleHonors.Favor)))))

  /** The favor a bandit defender gains: from the Order bank to the shared bank. */
  private def toBandits: Operation = Move(Piece.Favor(BattleHonors.Favor),
    PositionedLocation(Location.FavorBank(Suit.Order)),
    PositionedLocation(Location.SharedBank))
}

object BattleHonors {
  val id: PowerId = PowerId("denizen.battle-honors")
  val Favor: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[BattleHonors] =
    CatalogCards.denizen(catalog, id).map(new BattleHonors(_))
}
