package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Garrison Armory (card 255), a defender's battle plan: "[favor] In a
  * conquest, warbands on targeted sites each add +2 defense (instead of
  * +1)."
  *
  * A favor is placed onto the card. It is offered only in a Conquest. When the
  * defense is scored, the warbands at the targets are added once more, after
  * the window's own step has written the dice score plus the force
  * (`CampaignBattle.defenseResultOps`). So it comes after any single-shield
  * rescoring, which applies to the dice only, and the doubler, which the dice
  * score already holds, multiplies the dice only. Bandits pay nothing, so a
  * bandit defender never applies it.
  *
  * When the targets held a warband it writes "Warbands on the targets added
  * {n} more defense."
  */
final case class GarrisonArmory private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = GarrisonArmory.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(GarrisonArmory.added)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Garrison Armory: warbands on the targets add 2 defense each",
        Vector(CampaignPlanCost.Favor(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignDefenseResult -> (use => Vector(
      BuildOps((ready, pending) => CampaignSetup.setup(ready, use.actor, pending)
        .toRight(OathViolation.InvalidEventOrder(
          "Garrison Armory reached the defense without a Campaign setup"))
        .map(GarrisonArmory.again(ready, _))),
      Note(id, states => states.previous.flatMap { case (before, after) =>
        val added = GarrisonArmory.defense(after) - GarrisonArmory.defense(before)
        Option.when(added > 0)(GarrisonArmory.added(PowerSourceRef.Card(cardId),
          NoteArg.Number(added)))
      }))))

object GarrisonArmory:
  val id: PowerId = PowerId("denizen.garrison-armory")

  /** "Warbands on the targets added {n} more defense." */
  val added: NoteKey = NoteKey("added", Vector(
    NotePart.Text("Warbands on the targets added "), NotePart.Arg(0),
    NotePart.Text(" more defense.")))

  /** The recorded defense score. */
  private def defense(ready: ReadyGame): Int =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).fold(0)(_.score)

  /** The recorded defense with the targets' warbands added once more. Nothing
    * is written when the targets hold none. */
  private def again(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] =
    val force = CampaignBattle.defenderForce(ready, setup)
    Option.when(force > 0)(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(defense(ready) + force))).toVector

  def forCatalog(catalog: ExecutableCatalog): Option[GarrisonArmory] =
    CatalogCards.denizen(catalog, id).map(new GarrisonArmory(_))
