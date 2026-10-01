package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** The Great Levy (card 137), a battle plan for either side: "[favor] [favor]
  * ±3 [attack-die] and ignore all skulls you roll, unless your enemy has the
  * People's Favor."
  *
  * Two favor are placed onto the card. An attacker adds three attack dice; a
  * defender removes three. It is offered only while the enemy does not hold
  * the People's Favor; bandits hold no banner. For an attacker it also ignores
  * every skull the attack rolls, exactly as Outriders does
  * (`Outriders.ignoreSkulls`), with Outriders' line. A defender rolls no
  * attack dice, so for a defender it only removes three. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
final case class GreatLevy private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = GreatLevy.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => !context.enemyHolds(Banner.PeoplesFavor))
      .map(source => CampaignPlanOffer(source, GreatLevy.label(context.side),
        Vector(CampaignPlanCost.Favor(2)),
        Vector(PlanDice.effect(context.side, GreatLevy.Dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      if use.side == CampaignPlanSide.Attacker then
        Outriders.ignoreSkulls(id, PowerSourceRef.Card(cardId))
      else Vector.empty))

object GreatLevy:
  val id: PowerId = PowerId("denizen.the-great-levy")
  val Dice: Int = 3

  /** "The Great Levy: add 3 attack dice and ignore all attack skulls" for an
    * attacker, "The Great Levy: remove 3 attack dice" for a defender. */
  private def label(side: CampaignPlanSide): String =
    val dice = PlanDice.label("The Great Levy", side, Dice)
    side match
      case CampaignPlanSide.Attacker => s"$dice and ignore all attack skulls"
      case CampaignPlanSide.Defender => dice

  def forCatalog(catalog: ExecutableCatalog): Option[GreatLevy] =
    CatalogCards.denizen(catalog, id).map(new GreatLevy(_))
