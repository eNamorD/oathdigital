package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts}
import oathdigital.model._

object FieldPromotionCard extends Denizen(DenizenId("106"), "Field Promotion", Suit.Order):
  val power = PrintedPower(PowerId("denizen.field-promotion"),
    persistent = false, cost = Cost(favor = 1),
    text = "If you're victorious, gain three warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Field Promotion (card 106), a battle plan for either side: "[favor] If
  * you're victorious, gain three warbands."
  *
  * A favor is placed onto the card. Once the Campaign has resolved, its user
  * gains three warbands if they won, or what their supply holds. Bandits pay
  * nothing, so a bandit defender never applies it.
  *
  * When a warband was gained it writes "{Red} gained {n warbands}."
  */
final case class FieldPromotion private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = FieldPromotion.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(FieldPromotion.gained)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Field Promotion: gain 3 warbands if victorious",
      Vector(CampaignPlanCost.Favor(FieldPromotion.Favor)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.user.toVector.flatMap(user => Vector(
        BuildOps((ready, _) => PlayerFacts.forceKind(ready, user).map(kind =>
          Vector[CoreOperation](Gain.Warbands(user, kind,
            FieldPromotion.Warbands)))),
        Note(id, NoteSupport.gainedNote(FieldPromotion.gained,
          PowerSourceRef.Card(cardId), user, NoteUnit.Warband,
          NoteSupport.warbands))))))

object FieldPromotion:
  val id: PowerId = PowerId("denizen.field-promotion")
  /** The favor placed to choose it. */
  val Favor: Int = 1
  val Warbands: Int = 3
  /** "{Red} gained {n warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Option[FieldPromotion] =
    CatalogCards.denizen(catalog, id).map(new FieldPromotion(_))
