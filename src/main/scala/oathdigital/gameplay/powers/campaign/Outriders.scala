package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object OutridersCard extends Denizen(DenizenId("104"), "Outriders", Suit.Order):
  val power = PrintedPower(PowerId("denizen.outriders"),
    persistent = false, cost = Cost.free,
    text = "Ignore all skulls [skull] you roll.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Outriders (card 104), an attacker's battle plan: "Ignore all skulls you roll."
  *
  * Choosing it costs nothing and changes no dice. Once the attack is scored, the
  * plan writes the roll outcome again without the skull cap, so no skull removes
  * a warband and every skull face keeps its two swords. A facedown Outriders is
  * revealed when it is chosen, as any plan's source is. The rewrite is
  * `Outriders.ignoreSkulls`, which The Great Levy shares.
  *
  * When the attack rolled a skull it writes "Skulls ignored."
  */
final case class Outriders private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = Outriders.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Outriders: ignore all attack skulls", Vector.empty, Vector.empty))

  override def noteKeys: Vector[NoteKey] = Vector(Outriders.ignored)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (_ =>
      Outriders.ignoreSkulls(id, PowerSourceRef.Card(cardId))))

object Outriders:
  val id: PowerId = PowerId("denizen.outriders")
  /** "Skulls ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Skulls ignored.")))

  /** The attack written again without the skull cap, then `power`'s "Skulls
    * ignored." line from `source`, only when the attack rolled a skull to
    * ignore. The rewrite keeps the faces, so they still show the skulls. It
    * is computed from the faces, so two plans that both ignore the skulls
    * write the same score (Outriders, The Great Levy). */
  private[campaign] def ignoreSkulls(power: PowerId, source: PowerSourceRef)
      : Vector[Operation] = Vector(
    BuildOps((ready, _) =>
      Right(ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
        .map(_ => ModifyRollOutcome(CampaignIds.attackPool, Some(0),
          Some(AttackDieFace.score(CampaignBattle.attackFacesOf(ready))))))),
    Note(power, states => Option.when(
      AttackDieFace.skulls(CampaignBattle.attackFacesOf(states.now)) > 0)(
      ignored(source))))

  def forCatalog(catalog: ExecutableCatalog): Option[Outriders] =
    CatalogCards.denizen(catalog, id).map(new Outriders(_))
