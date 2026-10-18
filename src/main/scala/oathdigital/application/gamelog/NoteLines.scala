package oathdigital.application.gamelog

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.powers.OathkeeperGoalChoice
import oathdigital.gameplay.walker.{ChoicePayload, LogNoted,
  WalkerCompleted, WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** The lines a power, a printed rule or a procedure writes about its own
  * effect (power log lines design, section 2): "{source}: {sentence}" for
  * the first two, the sentence alone for a procedure, from the template the
  * note's key declares. A note names a card its viewer identified at its
  * action's start, at the step it restates, or after it.
  *
  * An action whose start line has not posted yet holds its notes, and they
  * post right after it. A note identical to an earlier one in the same action
  * posts nothing. Both rules compare journal facts, never rendered text, so
  * every viewer receives the same entries.
  *
  * It also holds the one action line still inferred from the journal: the
  * "Used {card}" fallback for a power that writes no `used` note of its
  * own. `powerSource` is the Game Log's last forward read to
  * `segmentEnd`; the prefix-stability property at
  * `GameLogPropertiesSuite` holds over that one rule now.
  */
private[gamelog] final class NoteLines(words: LogWords,
    wordings: NoteWordings, kinds: NoteKinds):

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
    case LogNoted(power, note, _) if posts(journal, run, at, power, note) =>
      for
        after <- journal.readyAfter(at)
        spans <- spoken(power, note, states(journal, run, at) :+ after, viewer)
      yield Posted(kind(run, power, note), spans)
    case _ => None

  /** "{source}: {sentence}" for `note`, its cards judged at `seen`, or the
    * sentence alone when the note has no source, or `None` when `power`
    * declares no template for the note's key.
    */
  def spoken(power: PowerId, note: LogNote, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] =
    wordings.template(power, note.key).map(template =>
      prefix(power, note, seen, viewer) ++
        sentence(template, note.args, seen, viewer))

  /** "{source}: " for a power's or a game rule's line, and nothing for a
    * procedure's, which starts with its sentence. */
  private def prefix(power: PowerId, note: LogNote, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] =
    note.source.toVector.flatMap(source =>
      wordings.source(power).fold(words.source(source, seen, viewer))(
        named => Vector(Text(named))) :+ Text(": "))

  /** The states before `at` that a note's cards are judged at: its action's
    * start, and the step it restates, the last step before it. A generic
    * line judges a card before and after its own operation, so a note
    * restating it never names less. */
  private def states(journal: LogJournal, run: Run, at: Int): Vector[ReadyGame] =
    val restated = (run.first until at).reverseIterator.find(index =>
      journal.event(index).isInstanceOf[WalkerStepRecorded])
    journal.readyBefore(run.first).toVector ++
      restated.toVector.flatMap(journal.readyBefore)

  /** Whether the note at `at` posts at all. The identical-note drop exists
    * for the hide hook, which fires once per hidden option; a procedure has
    * no hide hook, and two identical procedure notes in one run are two
    * real effects, so a procedure's note always posts.
    *
    * It is deliberately not suppressed by a covering note. Of the lines
    * this migration replaces, only Muster's consulted `covered`, and it did
    * so on the *step* index, to stop a vow's own warband gain matching the
    * Muster pattern a second time -- a second inferred line, which no
    * longer exists. Suppressing a procedure's note when a power's covering
    * note follows it would be a new rule for all eighteen, and it would
    * delete Trade's line, because Mounted Library writes a covering note
    * into the same gain window. */
  private def posts(journal: LogJournal, run: Run, at: Int, power: PowerId,
      note: LogNote): Boolean =
    ProcedureNotes.writes(power) || !repeated(journal, run, at, power, note)

  /** A procedure's own note is its action's line at the kind its registry
    * entry declares, and a phase power's `used` note, or a variant of it, is
    * its action's line.
    *
    * The procedure is read from the note's id, never from `run.procedure`: a
    * queued follow-up can run one procedure's note under another's run. */
  private def kind(run: Run, power: PowerId, note: LogNote): LogKind =
    ProcedureNotes.procedureOf(power) match
      case Some(procedure) => kinds(procedure, note.key)
      case None =>
        if NoteKey.isUse(note.key) && run.procedure == ActionRef.UsePower(power)
        then LogKind.Action
        else LogKind.Trigger

  private def repeated(journal: LogJournal, run: Run, at: Int, power: PowerId,
      note: LogNote): Boolean =
    (run.first until at).exists(index => journal.event(index) match
      case LogNoted(`power`, `note`, _) => true
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
    case NoteArg.Cards(ids) => words.listed(ids, seen, viewer)
    case NoteArg.Banner(banner) => Vector(words.banner(banner))
    case NoteArg.Pile(pile) => Vector(Text(SearchSource.name(pile)))
    case NoteArg.RegionName(region) => Vector(Text(region.toString))
    case NoteArg.SuitName(suit) => Vector(Text(suit.toString))
    case NoteArg.Goal(goal) => Vector(Text(OathkeeperGoalChoice.named(goal)))
    case NoteArg.Players(ids) =>
      LogWords.join(ids.map(id => Vector(words.player(id))))
    case NoteArg.Clauses(parts) =>
      LogWords.join(parts.map(clause(_, seen, viewer)))
    case NoteArg.Placements(entries) => LogWords.join(entries.map(
      (count, site) => Vector(Text(s"$count " +
        s"${LogWords.plural(count, "warband", "warbands")} on "),
        words.site(site))))
    case NoteArg.Things(items) => LogWords.join(items.map(thing(_, seen,
      viewer)))

  /** One item of a joined list, worded as the line that joined them by hand
    * worded it. */
  private def thing(item: NoteThing, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = item match
    case NoteThing.Site(id) => Vector(words.site(id))
    case NoteThing.Pawn(player) =>
      Vector(words.player(player), Text("'s pawn"))
    case NoteThing.Card(id) => words.one(words.seen(id, seen, viewer))
    case NoteThing.Banner(banner) => Vector(words.banner(banner))

  /** One clause of a joined sentence, worded as the line that joined them
    * by hand worded it. */
  private def clause(part: NoteClause, seen: Vector[ReadyGame],
      viewer: Option[PlayerId]): Vector[LogSpan] = part match
    case NoteClause.BurnedFavor(count) => Vector(Text(s"burned $count favor"))
    case NoteClause.Discarded(ids) =>
      Text("discarded ") +: words.cards(ids.map(words.seen(_, seen, viewer)))
    case NoteClause.SetAside(ids) =>
      Text("set aside ") +: words.cards(ids.map(words.seen(_, seen, viewer)))

  /** The "Used {card}" fallback, for a power that writes no `used` note of
    * its own. */
  def usePowerLines(power: PowerId)(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    usedPower(journal, run, at, power,
      journal.event(at).isInstanceOf[WalkerCompleted], viewer)

  /** "Used {card}" at the first step recording one of the power's own
    * effects: a step that is not only the payment onto its card or to the
    * bank, and not only the use record. At completion if it recorded none. */
  private def usedPower(journal: LogJournal, run: Run, at: Int, power: PowerId,
      completing: Boolean, viewer: Option[PlayerId]): Vector[Posted] =
    val postedEarlier = (run.first until at).exists(effect(journal, run, _))
    if postedEarlier || !(effect(journal, run, at) || completing) ||
        journal.notedUse(run, power, at) then Vector.empty
    else Vector(Posted(LogKind.Action,
      Text("Used ") +: powerSource(journal, run, at, power, viewer)))

  private def effect(journal: LogJournal, run: Run, index: Int): Boolean =
    journal.event(index) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, ops, _) =>
        !ops.forall {
          case RecordPowerUse(_) => true
          case QueueProcedure(_, _) => true
          case Move(Piece.Favor(_) | Piece.Secrets(_),
              PositionedLocation(Location.PlayArea(payer), _, _),
              PositionedLocation(Location.OnCard(_) | Location.SharedBank, _, _),
              _, _) => payer == run.actor
          case _ => false
        }
      // A power's own question: its source is already known, so "Used"
      // comes before the "Chose" line its answer posts.
      case WalkerStepRecorded(_, _: ChoicePayload, _, _) => true
      case _ => false

  /** The source the player named when starting (kept on every park), else
    * the use record's source, else the card the power is printed on. */
  private def powerSource(journal: LogJournal, run: Run, at: Int,
      power: PowerId, viewer: Option[PlayerId]): Vector[LogSpan] =
    val through = (run.first to journal.segmentEnd(at)).toVector
    val ready = journal.readyBefore(run.first)
    val started = through.map(journal.event).collectFirst {
      case parked: WalkerParked => parked.startArgs
    }.flatMap(_.headOption).flatMap(ref => ready.flatMap(state =>
      named(ref, state, viewer)))
    val used = through.flatMap(journal.ops).collectFirst {
      case OpStep(RecordPowerUse(PowerUseRef(_, source, _)), _, _) => source
    }.flatMap(source => ready.map(state => words.source(source, Vector(state), viewer)))
    started.orElse(used).orElse(ready.map(state =>
      words.power(state, run.actor, power, viewer)))
      .getOrElse(Vector(Text(power.value)))

  private def named(ref: DecisionOptionRef, ready: ReadyGame,
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] = ref match
    case DecisionOptionRef.Denizen(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Relic(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Vision(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Edifice(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Banner(banner) => Some(Vector(words.banner(banner)))
    case _ => None
