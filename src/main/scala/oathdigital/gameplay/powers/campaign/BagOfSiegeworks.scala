package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object BagOfSiegeworksCard extends Relic(RelicId("R37"), "Bag of Siegeworks", value = 42, defense = 2):
  val power = PrintedPower(PowerId("relic.bag-of-siegeworks"),
    persistent = false, cost = Cost(secret = 1),
    text = "If you're targeting sites, ignore [defense-die] rolls with " +
      "a single [shield].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bag of Siegeworks (relic R37), an attacker's battle plan: "[secret] If
  * you're targeting sites, ignore [defense-die] rolls with a single
  * [shield]."
  *
  * The relic must be faceup in the attacker's play area, and a secret is
  * placed onto it. It is offered only in a Conquest, because a Raid targets
  * no site. Once the defense is rolled, each single-shield die scores 0; two
  * shields and doublers score as usual. The rescoring is `SingleShields`,
  * which Rain Boots shares; it is written before the defender's force is
  * added, so the recorded defense carries the change.
  */
case object BagOfSiegeworks extends BattlePlan:
  val relicId: RelicId = BagOfSiegeworksCard.id
  val id: PowerId = BagOfSiegeworksCard.power.id

  /** "Single shields ignored." */
  val ignored: NoteKey = SingleShields.ignored

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int = SingleShields.score(faces)

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(BagOfSiegeworks.ignored)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Bag of Siegeworks: ignore single shields",
        Vector(CampaignPlanCost.Secret(1)), Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> ((_, children) =>
      SingleShields.ignore(id, PowerSourceRef.Card(relicId)) ++ children))
