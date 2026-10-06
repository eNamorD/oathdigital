package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.model._

object KindredWarriorsCard extends Denizen(DenizenId("60"), "Kindred Warriors", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.kindred-warriors"),
    persistent = false, cost = Cost(secret = 1),
    text = "Ignore all skulls [skull] you roll. ±X [attack-die] up to " +
      "the number of other suits you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Kindred Warriors (card 60), a battle plan for either side: "[secret]
  * Ignore all skulls you roll. ±X [attack-die] up to the number of other
  * suits you rule."
  *
  * A secret is placed onto the card. X is the number of suits other than
  * Arcane among the cards its user rules (`RuledCards`), and the plan always
  * adds or removes the full X: no amount is asked. An attacker also ignores
  * every skull, exactly as Outriders does (`Outriders.ignoreSkulls`), so an
  * attacker is offered it at X = 0. A defender rolls no attack dice, so at
  * X = 0 it would do nothing and is not offered. Bandits pay nothing, so a
  * bandit defender never applies it.
  */
final case class KindredWarriors private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = KindredWarriorsCard.id
  def id: PowerId = KindredWarriors.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  /** X: the suits other than Arcane among the cards the user rules. */
  private def suits(context: PlanContext): Int =
    Suit.all.filterNot(_ == Suit.Arcane).count(suit =>
      context.cardsRuled(catalog, context.ruler, suit).nonEmpty)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val dice = suits(context)
    context.denizen(cardId)
      .filter(_ => dice > 0 || context.side == CampaignPlanSide.Attacker)
      .map(source => CampaignPlanOffer(source,
        KindredWarriors.label(context.side, dice),
        Vector(CampaignPlanCost.Secret(1)),
        Option.when(dice > 0)(PlanDice.effect(context.side, dice)).toVector))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      if use.side == CampaignPlanSide.Attacker then
        Outriders.ignoreSkulls(use, id, PowerSourceRef.Card(cardId))
      else Vector.empty))

object KindredWarriors:
  val id: PowerId = KindredWarriorsCard.power.id

  /** "Kindred Warriors: add 2 attack dice and ignore all attack skulls", or
    * without the dice at X = 0; "Kindred Warriors: remove 2 attack dice"
    * for a defender. */
  private def label(side: CampaignPlanSide, dice: Int): String = side match
    case CampaignPlanSide.Attacker if dice == 0 =>
      "Kindred Warriors: ignore all attack skulls"
    case CampaignPlanSide.Attacker =>
      s"${PlanDice.label("Kindred Warriors", side, dice)} and ignore all " +
        "attack skulls"
    case CampaignPlanSide.Defender =>
      PlanDice.label("Kindred Warriors", side, dice)

  def forCatalog(catalog: ExecutableCatalog): KindredWarriors =
    new KindredWarriors(catalog)
