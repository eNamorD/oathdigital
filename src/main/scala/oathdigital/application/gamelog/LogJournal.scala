package oathdigital.application.gamelog

import oathdigital.engine.ReplayStep
import oathdigital.gameplay.walker.{ChoicePayload, PowerNoted, ProcedureWalker,
  WalkerCompleted, WalkerParked, WalkerStepRecorded}
import oathdigital.model._

/** One operation of a recorded batch with the state just before and just
  * after it (spec, "Input"). */
private[gamelog] final case class OpStep(operation: CoreOperation,
    before: ReadyGame, after: ReadyGame)

/** A walker run in progress: its procedure, whose action it is, the index of
  * its first event, and -- once posted -- the segment end of its start line.
  */
private[gamelog] final case class Run(procedure: ProcedureRef, actor: PlayerId,
    first: Int, started: Option[Int])

/** The scanned journal a formatting reads. Indices are positions in `steps`,
  * which equal journal sequences because the journal starts at 0.
  *
  * A segment is what one command appends, ending at a `walker.parked` or
  * `walker.completed`; a line may read ahead to the end of its own segment
  * and no further (spec, "Posting").
  */
private[gamelog] final class LogJournal(
    steps: Vector[ReplayStep[OathState, OathEvent]]):
  private val closings: Vector[Option[Int]] =
    steps.indices.foldRight(List.empty[Option[Int]]) { (index, later) =>
      val next = later.headOption.flatten
      (steps(index).event.event match
        case _: WalkerParked | _: WalkerCompleted => Some(index)
        case _ => next) :: later
    }.toVector

  private val batches: Vector[Vector[OpStep]] = steps.map(LogJournal.opSteps)

  def size: Int = steps.size
  def event(index: Int): OathEvent = steps(index).event.event
  def sequence(index: Int): Long = steps(index).event.index
  def readyBefore(index: Int): Option[ReadyGame] =
    LogJournal.ready(steps(index).before)
  def readyAfter(index: Int): Option[ReadyGame] =
    LogJournal.ready(steps(index).after)
  def ops(index: Int): Vector[OpStep] = batches(index)

  /** The last event of `index`'s segment: its closing event, or `index`
    * itself when no closing event follows in this prefix. */
  def segmentEnd(index: Int): Int = closings(index).getOrElse(index)

  /** The procedure `index`'s segment belongs to, named only by the segment's
    * closing event: a step carries none. */
  def procedureAt(index: Int): Option[ProcedureRef] =
    closings(index).map(event).collect {
      case parked: WalkerParked => parked.procedure
      case completed: WalkerCompleted => completed.procedure
    }

  /** Whether `power` journals its own `used` note in `run`, up to the end of
    * `at`'s segment. That line then replaces "Used {card}". */
  def notedUse(run: Run, power: PowerId, at: Int): Boolean =
    (run.first to segmentEnd(at)).exists(index => event(index) match
      case PowerNoted(`power`, note, _) => note.key == NoteKey.Used
      case _ => false)

  /** Whether a covering note restates the step at `index`: one follows it
    * in its segment before the next step. */
  def covered(index: Int): Boolean = event(index) match
    case _: WalkerStepRecorded =>
      (index + 1 to segmentEnd(index)).iterator.map(event)
        .takeWhile(!_.isInstanceOf[WalkerStepRecorded])
        .exists {
          case noted: PowerNoted => noted.covers
          case _ => false
        }
    case _ => false

  /** Every operation of `run` from its first event through `at`. */
  def runOps(run: Run, at: Int): Vector[(Int, OpStep)] =
    (run.first to at).toVector.flatMap(index => ops(index).map(index -> _))

  /** Every answer `run` recorded from its first event through `at`. */
  def answers(run: Run, at: Int): Vector[Answered] =
    (run.first to at).toVector.map(event).collect {
      case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _, _) =>
        Answered(id, answer, by)
    }

  /** Supply `player` lost to `SpendSupply` in event `index`, from the states
    * around each spend: a spend is clamped when applied. */
  def supplySpent(index: Int, player: PlayerId): Int =
    ops(index).collect {
      case OpStep(SpendSupply(spender, _, _), before, after)
          if spender == player =>
        LogJournal.supply(before, player) - LogJournal.supply(after, player)
    }.sum

private[gamelog] object LogJournal:
  def ready(state: OathState): Option[ReadyGame] = state match
    case OathState.Ready(ready) => Some(ready)
    case _ => None

  def supply(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.supply.supply)

  def pawnSite(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)

  /** A step's batch applied one operation at a time from the state before
    * the step. Replay already applied it whole, so a failure here cannot
    * happen on a journal that loaded; if it did, the rest of the batch keeps
    * the last good state rather than losing its lines. */
  private def opSteps(step: ReplayStep[OathState, OathEvent]): Vector[OpStep] =
    (step.before, step.event.event) match
      case (OathState.Ready(start), recorded: WalkerStepRecorded) =>
        recorded.ops.foldLeft((start, Vector.empty[OpStep])) {
          case ((state, done), operation) =>
            val next = ProcedureWalker.applyRecordedOperation(state, operation)
              .getOrElse(state)
            (next, done :+ OpStep(operation, state, next))
        }._2
      case _ => Vector.empty
