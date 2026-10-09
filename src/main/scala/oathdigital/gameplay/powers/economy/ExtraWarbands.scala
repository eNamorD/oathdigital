package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** A vow's warbands added to a Muster's own gain (Vow of Beastkin, Vow of
  * Wandering), with the vow's line right after the gain: "Gained {n} more
  * warband."
  *
  * The line tells what the gain added beyond its amount before this vow, at
  * most the vow's own count. Under two vows, each adds to the gain the other
  * already changed, and each line tells its own part. A short bank shortens
  * the line of the vow that folded last. An empty bank writes nothing.
  */
private[economy] object ExtraWarbands:
  /** "Gained {n} more warband." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more warband.", " more warbands.")))

  /** `operations` with `extra` more warbands on `actor`'s gain, and the line
    * of `power`, from `card`, after it. Unchanged when they hold no such
    * gain. */
  def added(power: PowerId, card: DenizenId, extra: Int, actor: PlayerId,
      operations: Vector[Operation]): Vector[Operation] =
    val at = operations.indexWhere {
      case Gain.Warbands(player, _, _) => player == actor
      case _ => false
    }
    if at < 0 then operations
    else operations(at) match
      case gain: Gain.Warbands => operations.patch(at, Vector(
        gain.copy(amount = gain.amount + extra),
        Note(power, line(_, card, actor, gain.amount, extra))), 1)
      case _ => operations

  /** What the gain's step added beyond `before`, at most `extra`. */
  private def line(states: NoteStates, card: DenizenId, actor: PlayerId,
      before: Int, extra: Int): Option[PowerNote] = for
    step <- states.previous
    more = math.min(extra, NoteSupport.warbands(step, actor) - before)
    if more > 0
  yield gained(PowerSourceRef.Card(card), NoteArg.Number(more))
