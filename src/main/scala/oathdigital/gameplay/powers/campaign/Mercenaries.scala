package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.model._

object MercenariesCard extends Denizen(DenizenId("12"), "Mercenaries", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.mercenaries"),
    persistent = false, cost = Cost(favor = 1),
    text = "±3 [attack-die] If you're defeated while using this power, " +
      "discard Mercenaries.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mercenaries (card 12), a battle plan for either side: "[favor] +-3 attack
  * dice. If you are defeated while using this power, discard Mercenaries."
  *
  * It costs a favor, placed onto the card. An attacker adds three dice to the
  * attack pool. A defender removes three from it, and the pool never goes below
  * zero. The sign is fixed by the side, so a player cannot choose the other one
  * (a deferred rule). When the user is defeated, the card is discarded by the
  * standard denizen discard once the Campaign has resolved, so its favor returns
  * to the bank.
  *
  * The discard writes "Discarded after {Red} lost.", read from the discard's
  * step.
  */
final case class Mercenaries private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = MercenariesCard.id
  def id: PowerId = Mercenaries.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val (label, effect) = context.side match
        case CampaignPlanSide.Attacker => "Mercenaries: add 3 attack dice" ->
          CampaignPlanEffect.AddAttackDice(Mercenaries.Dice)
        case CampaignPlanSide.Defender => "Mercenaries: remove 3 attack dice" ->
          CampaignPlanEffect.RemoveAttackDice(Mercenaries.Dice)
      CampaignPlanOffer(source, label, Vector(CampaignPlanCost.Favor(1)),
        Vector(effect))
    }

  override def noteKeys: Vector[NoteKey] = Vector(Mercenaries.discarded)

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(false) then Vector.empty
      else use.user.toVector.flatMap(user => Vector(
        PlanDiscard.denizen(catalog, user, cardId),
        Note(id, discardNote(user))))))

  /** Its line, when the discard's step took the card out of play. */
  private def discardNote(user: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    step <- states.previous
    if inPlay(step._1) && !inPlay(step._2)
  yield Mercenaries.discarded(PowerSourceRef.Card(cardId), NoteArg.Player(user))

  private def inPlay(ready: ReadyGame): Boolean =
    val current = ready.game.current
    current.players.exists(_.advisers.exists(_.id == cardId)) ||
      current.map.sites.values.exists(_.denizens.exists(_.id == cardId))

object Mercenaries:
  val id: PowerId = MercenariesCard.power.id
  val Dice: Int = 3
  /** "Discarded after {Red} lost." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Discarded after "), NotePart.Arg(0), NotePart.Text(" lost.")))

  def forCatalog(catalog: ExecutableCatalog): Mercenaries =
    new Mercenaries(catalog)
