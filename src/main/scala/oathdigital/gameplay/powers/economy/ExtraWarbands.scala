package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** A vow's warbands gained after a Muster's own gain (Vow of Beastkin, Vow
  * of Wandering), with the vow's line: "Gained {n} more warband."
  *
  * The vow's warbands are their own gain, inside an `AutomaticUse` a
  * power-use restriction may refuse where the walk reaches it (catalog
  * batch 8, slice 10). Its line tells what that gain added, at most the
  * vow's own count, and covers the gain's generic line. Under two vows each
  * line tells its own gain. A short bank shortens the line of the vow that
  * gains last. An empty bank writes nothing.
  */
private[economy] object ExtraWarbands:
  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("ExtraWarbands")
  /** "Gained {n} more warband." */
  val gained: NoteKey = notes.key("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more warband.", " more warbands.")))

  /** `operations` and, after them, `power`'s use from `card`: `extra`
    * warbands of the kind the actor's gain in `operations` takes, and its
    * line. A gain a `BuildOps` builds is not found. Unchanged when they hold
    * no such gain. A Muster's gain window holds one gain. */
  def appended(ctx: PowerCtx, power: PowerId, card: DenizenId, extra: Int,
      operations: Vector[Operation]): Vector[Operation] =
    operations.iterator.flatMap(gainKind(_, ctx.actor)).nextOption()
      .fold(operations)(kind => operations :+ AutomaticUse(ctx.actor, power,
        card, (_, _) => Vector(Gain.Warbands(ctx.actor, kind, extra),
          Note(power, line(_, card, ctx.actor, extra), covers = true))))

  /** The force kind of `actor`'s warband gain in `node`, when it holds
    * one. */
  private def gainKind(node: Operation, actor: PlayerId): Option[ForceKind] =
    node match
      case Gain.Warbands(`actor`, kind, _) => Some(kind)
      case _: LeafOperation | _: BuildOps => None
      case other =>
        other.children.iterator.flatMap(gainKind(_, actor)).nextOption()

  /** What the gain's step added, at most `extra`. */
  private def line(states: NoteStates, card: DenizenId, actor: PlayerId,
      extra: Int): Option[LogNote] = for
    step <- states.previous
    more = math.min(extra, NoteSupport.warbands(step, actor))
    if more > 0
  yield gained(PowerSourceRef.Card(card), NoteArg.Number(more))
