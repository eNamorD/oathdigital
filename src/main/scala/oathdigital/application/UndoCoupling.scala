package oathdigital.application

import oathdigital.model.{ConsentEvent, OathState}

/** The batches one Undo or one Redo moves (automatic power consent
  * design, "Undo coupling"). Neither stops at a position parked at an
  * automatic power's unanswered step, which nobody would answer: the
  * batch that answered a step goes back with the batch that walked to it.
  * `parks` says whether a state is parked at such a step.
  *
  * A walk reaches a step only in a batch its request closes, and a step
  * is answered only in a batch its answer opens: a consent fact always
  * sits beside the join. `parks` rebuilds the walk, so it is asked only
  * there, and never of the batches between. */
private[application] final class UndoCoupling(parks: OathState => Boolean):
  /** The live batches one Undo takes back, latest first: the latest, and
    * while the state before it is parked at a step, the one before. `None`
    * when there is nothing to undo, or when the chain needs a batch a
    * barrier keeps off the stack. */
  def undoing(journal: FoldedJournal): Option[Vector[CommandBatch]] =
    def from(live: List[CommandBatch]): Option[Vector[CommandBatch]] =
      live match
        case top :: rest =>
          if consentAt(journal, top.from - 1) && parks(top.before) then
            from(rest).map(top +: _)
          else Some(Vector(top))
        case Nil => None
    from(journal.live)

  /** The taken-back batches one Redo restores, earliest first: the latest
    * taken back, and while the state after it is parked at a step, the
    * next. A chain that ends parked at a step leaves its request to be
    * asked again. */
  def redoing(journal: FoldedJournal): Option[Vector[CommandBatch]] =
    def from(redo: List[CommandBatch]): Vector[CommandBatch] = redo match
      case top :: rest if rest.nonEmpty && consentAt(journal, top.until) &&
          parks(top.after) =>
        top +: from(rest)
      case top :: _ => Vector(top)
      case Nil => Vector.empty
    Some(from(journal.redo)).filter(_.nonEmpty)

  /** Whether the event at `index` is a consent fact. */
  private def consentAt(journal: FoldedJournal, index: Long): Boolean =
    index >= 0 && journal.steps.lift(index.toInt)
      .exists(_.event.event.isInstanceOf[ConsentEvent])
