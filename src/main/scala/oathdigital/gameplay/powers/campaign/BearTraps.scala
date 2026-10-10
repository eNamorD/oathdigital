package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object BearTrapsCard extends Denizen(DenizenId("3"), "Bear Traps", Suit.Order):
  val power = PrintedPower(PowerId("denizen.bear-traps"),
    persistent = false, cost = Cost.free,
    text = "- [attack-die] Kill one warband on the attacker's board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bear Traps (card 3), a defender's battle plan: "- attack die. Kill one
  * warband on the attacker's board."
  *
  * It is free, so a bandit defender applies it at a site it rules. It
  * removes one attack die and kills one of the attacker's warbands on their
  * board beyond the committed force, which stays on the board until the
  * losses. With none beyond the force, nothing dies.
  *
  * When a warband died it writes "Killed 1 warband on {Red}'s board.".
  */
case object BearTraps extends BattlePlan:
  val cardId: DenizenId = BearTrapsCard.id
  val id: PowerId = BearTrapsCard.power.id
  /** "Killed {1 warband} on {Red}'s board." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" on "), NotePart.Arg(1),
    NotePart.Text("'s board.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(BearTraps.killed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Bear Traps: remove 1 attack die and kill a warband on the " +
        "attacker's board", Vector.empty, Vector(
        CampaignPlanEffect.RemoveAttackDice(1),
        CampaignPlanEffect.Run(Vector(trap(context.setup),
          Note(id, killedNote(context.setup.actor), covers = true))))))

  /** One warband off the attacker's board, when one stands there beyond
    * the committed force. Read when the walk reaches it. */
  private def trap(setup: CampaignSetup): Operation =
    BuildOps((ready, _) =>
      if warbands(ready, setup.actor) <= setup.force then Right(Vector.empty)
      else PlayerFacts.forceKind(ready, setup.actor).map(kind =>
        Vector[CoreOperation](Kill(Piece.Warbands(kind, 1),
          PositionedLocation(Location.PlayArea(setup.actor))))))

  private def warbands(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.warbands)

  /** The line, when the step before killed `attacker`'s warbands. */
  private def killedNote(attacker: PlayerId)(states: NoteStates)
      : Option[PowerNote] =
    states.previous.map((before, after) =>
      warbands(before, attacker) - warbands(after, attacker))
      .filter(_ > 0).map(count => BearTraps.killed(
        PowerSourceRef.Card(cardId), NoteArg.Amount(count, NoteUnit.Warband),
        NoteArg.Player(attacker)))
