package oathdigital.application.gamelog

import oathdigital.gameplay.walker.{PowerNoted, WalkerStepRecorded}
import oathdigital.model._
import LogSpan.Text

/** The lines powers write about themselves (power log lines design,
  * section 2): "{source}: {sentence}", from the template the note's power
  * declares. A note names a card its viewer identified at its action's
  * start, at the step it restates, or after it.
  *
  * An action whose start line has not posted yet holds its notes, and they
  * post right after it. A note identical to an earlier one in the same action
  * posts nothing. Both rules compare journal facts, never rendered text, so
  * every viewer receives the same entries.
  */
private[gamelog] final class PowerLines(words: LogWords,
    wordings: NoteWordings):

  /** The notes of `run` before `at`, held for the start line `at` posts. */
  def held(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    (run.first until at).toVector.flatMap(line(journal, run, _, viewer))

  /** `at`'s own note, unless `run` still waits for its start line. */
  def own(journal: LogJournal, run: Run, at: Int, opens: Boolean,
      viewer: Option[PlayerId]): Vector[Posted] =
    if opens && run.started.isEmpty then Vector.empty
    else line(journal, run, at, viewer).toVector

  private def line(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] = journal.event(at) match
    case PowerNoted(power, note, _) if !repeated(journal, run, at, power, note) =>
      for
        template <- wordings.template(power, note.key)
        after <- journal.readyAfter(at)
        seen = states(journal, run, at) :+ after
      yield Posted.line(kind(run, power, note),
        words.source(note.source, seen, viewer) ++
          (Text(": ") +: sentence(template, note.args, seen, viewer)))
    case _ => None

  /** The states before `at` that a note's cards are judged at: its action's
    * start, and the step it restates, the last step before it. A generic
    * line judges a card before and after its own operation, so a note
    * restating it never names less. */
  private def states(journal: LogJournal, run: Run, at: Int): Vector[ReadyGame] =
    val restated = (run.first until at).reverseIterator.find(index =>
      journal.event(index).isInstanceOf[WalkerStepRecorded])
    journal.readyBefore(run.first).toVector ++
      restated.toVector.flatMap(journal.readyBefore)

  /** A phase power's own `used` note, or a variant of it, is its action's
    * line. */
  private def kind(run: Run, power: PowerId, note: PowerNote): LogKind =
    if NoteKey.isUse(note.key) && run.procedure == ActionRef.UsePower(power)
    then LogKind.Action
    else LogKind.Trigger

  private def repeated(journal: LogJournal, run: Run, at: Int, power: PowerId,
      note: PowerNote): Boolean =
    (run.first until at).exists(index => journal.event(index) match
      case PowerNoted(`power`, `note`, _) => true
      case _ => false)

  private def sentence(template: Vector[NotePart], args: Vector[NoteArg],
      seen: Vector[ReadyGame], viewer: Option[PlayerId]): Vector[LogSpan] =
    template.flatMap:
      case NotePart.Text(written) => Vector(Text(written))
      case NotePart.Arg(index) =>
        args.lift(index).toVector.flatMap(argument(_, seen, viewer))
      case NotePart.Plural(index, one, many) => Vector(Text(args.lift(index) match
        case Some(NoteArg.Amount(1, _)) | Some(NoteArg.Number(1)) => one
        case _ => many))

  private def argument(arg: NoteArg, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = arg match
    case NoteArg.Player(id) => Vector(words.player(id))
    case NoteArg.Card(id) => words.one(words.seen(id, seen, viewer))
    case NoteArg.Site(id) => Vector(words.site(id))
    case NoteArg.Amount(value, unit) =>
      Vector(LogSpan.Amount(value, unit.word(value)))
    case NoteArg.Number(value) => Vector(Text(value.toString))
    case NoteArg.Bank(suit) => Vector(Text(s"the $suit bank"))
    case NoteArg.Dice(faces) => LogWords.dice(faces).toVector
    case NoteArg.Cards(ids) => words.cards(ids.map(words.seen(_, seen, viewer)))
    case NoteArg.Banner(banner) => Vector(words.banner(banner))
