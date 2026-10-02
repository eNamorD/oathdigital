package oathdigital.gameplay.powers.wake

import oathdigital.catalog.{Edifice, EdificeFace, Locked, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object MarbleFountainsCard extends Edifice(EdificeId("E15"), Suit.Arcane):
  object intact extends EdificeFace("Marble Fountains") with Locked:
    val power = PrintedPower(PowerId("edifice.e15.intact"),
      persistent = false, cost = Cost.free,
      text = "**WAKE:** If your pawn is at this site, refresh your Supply " +
        "marker to the leftmost space.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Murky Fountain"):
    val power = PrintedPower(PowerId("edifice.e15.ruined"),
      persistent = false, cost = Cost(secret = 1),
      text = "**ACTION:** If your pawn is at this site, roll 2 " +
        "[defense-die] and gain Supply equal to the total [shield] " +
        "rolled. If you roll none, end your Act Phase.")
    val powers: Vector[PrintedPower] = Vector(power)

/** Marble Fountains (edifice E15, intact), WAKE: if your pawn is at this
  * site, refresh your Supply to the leftmost space. `GainSupply` clamps at
  * the track maximum, so gaining the maximum refreshes it. Wake powers are
  * once per turn, which the engine enforces.
  */
case object MarbleFountains extends PhasePower:
  val id: PowerId = PowerId("edifice.e15.intact")
  def timing: PowerTiming = PowerTiming.Wake
  /** Its own line: the Supply it refreshed to. */
  val refreshed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text("'s Supply refreshed to "), NotePart.Arg(1),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(refreshed)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = source match
    case DecisionOptionRef.Edifice(edifice) =>
      PowerAccess.pawnSite(ready, player).flatMap(ready.game.current.map.sites.get)
        .exists(_.denizens.exists {
          case card: EdificeState => card.id == edifice
          case _ => false
        })
    case _ => false

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, SupplyTrack.Maximum),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      board <- NoteSupport.board(states.now, player)
    yield refreshed(card, NoteArg.Player(player),
      NoteArg.Number(board.supply.supply))))))
