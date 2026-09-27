package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Bag of Siegeworks (relic R37), an attacker's battle plan: "[secret] If
  * you're targeting sites, ignore [defense-die] rolls with a single
  * [shield]."
  *
  * The relic must be faceup in the attacker's play area, and a secret is
  * placed onto it. It is offered only in a Conquest, because a Raid targets
  * no site. Once the defense is rolled, each single-shield die scores 0; two
  * shields and doublers score as usual, so Blank, OneShield and Doubler
  * score 0. The score is written again before the defender's force is added
  * to it (`CampaignDefenseResult`), so the recorded defense carries the
  * change.
  */
final case class BagOfSiegeworks private (relicId: RelicId) extends BattlePlan:
  def id: PowerId = BagOfSiegeworks.id
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
      Vector[Operation](
        BuildOps((ready, _) => Right(rescored(ready))),
        Note(id, states => Option.when(
          BagOfSiegeworks.faces(states.now).contains(DefenseDieFace.OneShield))(
          BagOfSiegeworks.ignored(PowerSourceRef.Card(relicId))))) ++ children))

  /** The defense roll's score without its single shields. Nothing is written
    * when no single shield was rolled. */
  private def rescored(ready: ReadyGame): Vector[CoreOperation] =
    val faces = BagOfSiegeworks.faces(ready)
    if !faces.contains(DefenseDieFace.OneShield) then Vector.empty
    else Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(BagOfSiegeworks.score(faces))))

object BagOfSiegeworks:
  val id: PowerId = PowerId("relic.bag-of-siegeworks")

  /** "Single shields ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Single shields ignored.")))

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int =
    DefenseDieFace.score(faces.filterNot(_ == DefenseDieFace.OneShield))

  private def faces(ready: ReadyGame): Vector[DefenseDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).toVector
      .flatMap(_.faces.collect { case face: DefenseDieFace => face })

  def forCatalog(catalog: ExecutableCatalog): Option[BagOfSiegeworks] =
    CatalogCards.relic(catalog, id).map(new BagOfSiegeworks(_))
