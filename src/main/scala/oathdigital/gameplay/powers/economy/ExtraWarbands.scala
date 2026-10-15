package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
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
  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("ExtraWarbands")
  /** "Gained {n} more warband." */
  val gained: NoteKey = notes.key("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more warband.", " more warbands.")))

  /** `operations` with `extra` more warbands on the actor's gain, wherever
    * the tree holds it, and the line of `power`, from `card`, after it. A
    * gain a `BuildOps` builds is not entered. Unchanged when they hold no
    * such gain. A Muster's gain window holds one gain. */
  def added(ctx: PowerCtx, power: PowerId, card: DenizenId, extra: Int,
      operations: Vector[Operation]): Vector[Operation] =
    OperationRewrite.nodes(ctx, operations)((_, _) => {
      case gain @ Gain.Warbands(player, _, _) if player == ctx.activePlayer =>
        Vector(gain.copy(amount = gain.amount + extra),
          Note(power, line(_, card, player, gain.amount, extra)))
    })

  /** What the gain's step added beyond `before`, at most `extra`. */
  private def line(states: NoteStates, card: DenizenId, actor: PlayerId,
      before: Int, extra: Int): Option[PowerNote] = for
    step <- states.previous
    more = math.min(extra, NoteSupport.warbands(step, actor) - before)
    if more > 0
  yield gained(PowerSourceRef.Card(card), NoteArg.Number(more))
