package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.NoteSupport
import oathdigital.model._

object ShieldWallCard extends Denizen(DenizenId("108"), "Shield Wall", Suit.Order):
  val power = PrintedPower(PowerId("denizen.shield-wall"),
    persistent = false, cost = Cost(favor = 1),
    text = "+2 [defense-die] If you're defeated, kill all of your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Shield Wall (card 108), a defender's battle plan: "[favor] +2 defense
  * dice. If you're defeated, kill all of your force."
  *
  * A favor is placed onto the card, so a bandit defender never uses it. When
  * its user is defeated, their whole force dies after the losses
  * (`KillWholeForce`): a Conquest defender keeps none of the warbands the victory
  * returned, and a Raid defender loses its whole board. It folds at the
  * default priority, before Billowing Fog and Traveling Doctor, which ignore
  * it, and before Hospital, which still saves what it kills.
  *
  * Its line names its user, the defender, who acts off turn: "{Blue}'s whole
  * force was killed.", when the kill killed a warband. The dice write no line.
  */
case object ShieldWall extends BattlePlan:
  val cardId: DenizenId = ShieldWallCard.id
  val id: PowerId = ShieldWallCard.power.id
  /** The favor placed to choose it. */
  val Favor: Int = 1
  /** The defense dice it adds. */
  val Dice: Int = 2

  private val notes = NoteSet(id)
  /** "{Blue}'s whole force was killed." */
  val killed: NoteKey = notes.key("killed", Vector(NotePart.Arg(0),
    NotePart.Text("'s whole force was killed.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Shield Wall: add 2 defense dice; if defeated, kill all of your force",
      Vector(CampaignPlanCost.Favor(ShieldWall.Favor)),
      Vector(CampaignPlanEffect.AddDefenseDice(ShieldWall.Dice))))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignLosses -> ((_, use, losses) => (for
      user <- use.user
      result <- use.result
      if use.won.contains(false)
    yield
      val returns = KillWholeForce.returns(use.ready, result)
      losses :+ BuildOps((ready, _) =>
        KillWholeForce.ofDefender(ready, result, returns)) :+
        Note(id, killedNote(user))).getOrElse(losses)))

  /** What the kill took from the user's board, read from its step. */
  private def killedNote(user: PlayerId)(states: NoteStates)
      : Option[LogNote] = for
    step <- states.previous
    if NoteSupport.warbands(step, user) < 0
  yield ShieldWall.killed(PowerSourceRef.Card(cardId), NoteArg.Player(user))
