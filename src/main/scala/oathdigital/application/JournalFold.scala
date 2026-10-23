package oathdigital.application

import oathdigital.engine.{EventReplayFailure, RecordedEvent, ReplayStep}
import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, StepRestored, StepTakenBack}

/** One command's batch (undo design, "Terms"): the events in `[from,
  * until)`, opened by `CommandAccepted(issuer)`, with the states around it. */
final case class CommandBatch(from: Long, until: Long, issuer: PlayerId,
    before: OathState, after: OathState):
  /** Whether a marker for `[from, until)` by `by` names this batch. */
  def namedBy(from: Long, until: Long, by: PlayerId): Boolean =
    this.from == from && this.until == until && issuer == by

/** The folded journal (undo design, "Replay: one forward fold"): the current
  * state, one step per event (markers included), the live stack (the
  * batches since the latest barrier, latest first) and the redo stack (the
  * batches taken back since the last command batch, latest first). */
final case class FoldedJournal(state: OathState,
    steps: Vector[ReplayStep[OathState, OathEvent]],
    live: List[CommandBatch], redo: List[CommandBatch] = Nil):
  def undoable: Option[CommandBatch] = live.headOption

  /** The sequence of the latest void marker: a client whose log predates it
    * must refetch the whole log (undo design, "Game Log"). */
  def logRevision: Option[Long] = steps.reverseIterator.collectFirst {
    case ReplayStep(RecordedEvent(index,
        _: StepTakenBack | _: StepRestored), _, _) => index }

  private[application] def recorded(record: RecordedEvent[OathEvent],
      after: OathState): FoldedJournal =
    copy(state = after, steps = steps :+ ReplayStep(record, state, after))

/** Replays a journal forward, every event in order, voided or not. A
  * marker rewinds to a state the fold already holds, so no event is evolved
  * twice and no snapshot is stored. */
private[application] final class JournalFold(
    evolve: (OathState, OathEvent) => Either[OathViolation, OathState]):
  private type Result[A] = Either[EventReplayFailure[OathViolation], A]

  def fold(initial: OathState, records: Iterable[RecordedEvent[OathEvent]])
      : Result[FoldedJournal] =
    extend(FoldedJournal(initial, Vector.empty, Nil), records)

  /** `journal` continued by `records`, which begin at its end. A batch never
    * spans two appends, so the batch `records` leave open closes at their
    * end. */
  def extend(journal: FoldedJournal,
      records: Iterable[RecordedEvent[OathEvent]]): Result[FoldedJournal] =
    records.foldLeft[Result[Progress]](Right(Progress(journal, None))) {
      case (Right(progress), record) => progress.next(record)
      case (failure, _) => failure
    }.map(progress => progress.closedAt(progress.journal.steps.lastOption
      .fold(0L)(_.event.index + 1)))

  private final case class Open(from: Long, issuer: PlayerId,
      before: OathState)

  private final case class Progress(journal: FoldedJournal,
      open: Option[Open]):
    /** The journal with the open batch, if any, closed before `until`: a
      * barrier empties the live stack, anything else is pushed onto it. */
    def closedAt(until: Long): FoldedJournal = open.fold(journal) { batch =>
      val events = journal.steps.slice(batch.from.toInt, until.toInt)
        .map(_.event.event)
      val closed = CommandBatch(batch.from, until, batch.issuer,
        batch.before, journal.state)
      journal.copy(live =
        if UndoBarrier.blocks(events, batch.before, journal.state) then Nil
        else closed :: journal.live)
    }

    def next(record: RecordedEvent[OathEvent]): Result[Progress] =
      record.event match
        case CommandAccepted(by) =>
          val settled = closedAt(record.index)
          Right(Progress(settled.recorded(record, settled.state)
            .copy(redo = Nil), Some(Open(record.index, by, settled.state))))
        case StepTakenBack(from, until, by) =>
          val settled = closedAt(record.index)
          settled.live match
            case top :: rest if top.namedBy(from, until, by) =>
              Right(Progress(settled.recorded(record,
                JournalFold.carried(top.before, settled.state))
                .copy(live = rest, redo = top :: settled.redo), None))
            case _ => Left(EventReplayFailure(record.index,
              OathViolation.InvalidEventOrder(s"taken-back [$from, $until) " +
                s"by ${by.value} is not the latest live batch")))
        case StepRestored(from, until, by) =>
          val settled = closedAt(record.index)
          settled.redo match
            case top :: rest if top.namedBy(from, until, by) =>
              Right(Progress(settled.recorded(record,
                JournalFold.carried(top.after, settled.state))
                .copy(live = top :: settled.live, redo = rest), None))
            case _ => Left(EventReplayFailure(record.index,
              OathViolation.InvalidEventOrder(s"restored [$from, $until) " +
                s"by ${by.value} is not the latest taken-back batch")))
        // A consent fact opens no batch and joins none (consent design,
        // "Events"): it closes the open batch, as a batch opening does, so
        // a whole-journal replay batches exactly as live appends did.
        case event: ConsentEvent =>
          val settled = closedAt(record.index)
          evolve(settled.state, event).left
            .map(EventReplayFailure(record.index, _))
            .map(after => Progress(settled.recorded(record, after), None))
        case event => evolve(journal.state, event).left
          .map(EventReplayFailure(record.index, _))
          .map(after => copy(journal = journal.recorded(record, after)))

private[application] object JournalFold:
  /** `target`, a state an undo or a redo returns to, with the consent
    * ledger of `current` minus any waiting request (consent design,
    * "Undo"): grants, refusals and preferences outlive the rewind. */
  def carried(target: OathState, current: OathState): OathState =
    (target, current) match
      case (OathState.Ready(game), OathState.Ready(now)) =>
        OathState.Ready(game.copy(consent = now.consent.carried))
      case _ => target
