package oathdigital.application.gamelog

import oathdigital.application.{GamePresentationProjector,
  WalkerDecisionProjector}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.ReplayStep
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{ConsentPowers, WalkerPowerCatalog}
import oathdigital.gameplay.walker.{FollowUpSkipped,
  FollowUpSupplyWaiver, PowerNoted, ProcedureDequeued, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import LogSpan.Text

/** The game log: a pure function of the scanned journal prefix and the
  * viewer (spec, "The formatter").
  *
  * Every event is matched by name. A new `OathEvent` case fails compilation
  * here until it has a decision; `WalkerEvent` is open, so a walker event
  * this build does not know is silent, the same fallback `OathRules.evolve`
  * takes.
  */
private[application] final class GameLogFormatter(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector, wordings: NoteWordings):
  def this(catalog: ExecutableCatalog, presentation: GamePresentationProjector) =
    this(catalog, presentation, NoteWordings.default(catalog))
  private val words = new LogWords(catalog, presentation)
  // The engine's follow-up waiver is offered beside the catalog's powers
  // (`WalkerPowers.selected`), so it is named automatic here too.
  private val walkerPowers = WalkerPowerCatalog.default(catalog).powers
  private val starts = new StartLines(words,
    (walkerPowers :+ FollowUpSupplyWaiver)
      .map(power => power.id -> power.resolution).toMap,
    ModifierPicks.picks(walkerPowers, _))
  private val choices = new ChoiceWords(words,
    new WalkerDecisionProjector(catalog, presentation))
  private val actions = new ActionLines(words, choices, catalog)
  private val details = new DetailLines(words, choices, wordings.narrated)
  private val events = new EventLines(words)
  private val notes = new PowerLines(words, wordings)
  private val takenBack = new TakenBackGroups(words)
  private val consent = new ConsentLines(words, choices,
    ConsentPowers.default(catalog))

  /** Taken-back moves (undo design, "Game Log"). The live entries are the
    * live journal formatted alone, so no line reads a voided event or a
    * marker. Each voided batch reads as it did before its undo: its entries
    * come from the live journal as it stood at its marker. That costs one
    * more formatting per standing marker. A restored batch is live again,
    * so its lines read as if it had never been taken back (undo design,
    * "Redo"). */
  def format(steps: Vector[ReplayStep[OathState, OathEvent]],
      viewer: Option[PlayerId]): Vector[LogEntry] =
    val markers = steps.indices.toVector
      .filter(at => isVoidMarker(steps(at).event.event))
    // The taken-back markers still standing before `end`, with their
    // positions. A restore lifts the latest one: the fold accepts a restore
    // only of the batch most recently taken back.
    def standing(end: Int): Vector[(Int, OathEvent.StepTakenBack)] =
      markers.takeWhile(_ < end).foldLeft(
          Vector.empty[(Int, OathEvent.StepTakenBack)]) { (stood, at) =>
        steps(at).event.event match
          case marker: OathEvent.StepTakenBack => stood :+ (at -> marker)
          case _ => stood.dropRight(1)
      }
    // The journal before `end`: no marker, and nothing a marker standing
    // there voids. Its positions then differ from its sequences.
    def live(end: Int) =
      val voids = standing(end).map(_._2)
      steps.take(end).filter { step =>
        val sequence = step.event.index
        !isVoidMarker(step.event.event) && !voids.exists(marker =>
          sequence >= marker.from && sequence < marker.until)
      }
    val stood = standing(steps.size)
    val voided = stood.flatMap((at, marker) =>
      formatLive(live(at), viewer).filter(entry =>
        entry.sequence >= marker.from && entry.sequence < marker.until))
    withdrawn(steps, takenBack.mark(stood.map(_._2),
      (formatLive(live(steps.size), viewer) ++ voided)
        .sortBy(entry => (entry.sequence, entry.ordinal))))

  /** A withdrawn request's line, struck through under no header (consent
    * design, "Game Log"). At most one request waits at a time, so each
    * withdrawal takes back the latest request before it. */
  private def withdrawn(steps: Vector[ReplayStep[OathState, OathEvent]],
      entries: Vector[LogEntry]): Vector[LogEntry] =
    val struck = steps.foldLeft((Option.empty[Long], Set.empty[Long])) {
      case ((open, done), step) => step.event.event match
        case _: OathEvent.ConsentRequested => (Some(step.event.index), done)
        case _: OathEvent.ConsentRequestWithdrawn => (None, done ++ open)
        case _ => (open, done)
    }._2
    entries.map(entry =>
      if struck(entry.sequence) then entry.copy(takenBack =
        Some(LogTakenBack(entry.sequence, Vector.empty)))
      else entry)

  /** Whether `event` voids or restores a batch: it posts no line and is no
    * part of any journal a formatting reads. */
  private def isVoidMarker(event: OathEvent): Boolean = event match
    case _: OathEvent.StepTakenBack | _: OathEvent.StepRestored => true
    case _ => false

  private def formatLive(steps: Vector[ReplayStep[OathState, OathEvent]],
      viewer: Option[PlayerId]): Vector[LogEntry] =
    val journal = new LogJournal(steps)
    (0 until journal.size).foldLeft(
        (Option.empty[Run], Vector.empty[LogEntry])) {
      case ((run, entries), at) =>
        val (lines, next) = eventLines(journal, run, at, viewer)
        val opened = if at == 0 then opening(journal) ++ lines else lines
        val filed = journal.event(at) match
          // A Skip follows the previous run's completion, and a consent
          // line comes between a walk's steps, so each belongs to the latest
          // section in time order (follow-up actions and consent designs).
          case _: FollowUpSkipped | _: ConsentEvent => opened.map(_.copy(
            section = entries.reverseIterator.flatMap(_.section).nextOption()))
          case _ => opened
        val posted = quietWake(entries.lastOption, filed)
        (next, entries ++ posted.zipWithIndex.map { case (entry, ordinal) =>
          LogEntry(journal.sequence(at), ordinal, entry.kind, entry.depth,
            entry.spans, entry.section) })
    }._2

  /** A journal that does not begin with `GameStarted` began at a position
    * already in play (a test's Table): it opens with that position's round,
    * turn and phase, the headlines a `RoundEnded` would have posted. When
    * its first batch was taken back, the headlines open the live journal
    * again; a played journal begins with `GameStarted`, which no undo
    * reaches. */
  private def opening(journal: LogJournal): Vector[Posted] =
    journal.event(0) match
      case OathEvent.GameStarted(_, _) => Vector.empty
      case _ => journal.readyBefore(0).toVector.flatMap { ready =>
        val current = ready.game.current
        Vector(roundHeadline(current.tracks.round),
          turnHeadline(current.turn.activePlayer),
          phaseHeadline(current.turn.phase))
      }

  /** "Nothing happened in Wake" before an Act headline that follows its
    * Wake headline directly. It reads only entries already posted, so a
    * longer journal never takes it back. */
  private def quietWake(last: Option[LogEntry], posted: Vector[Posted])
      : Vector[Posted] =
    val previous = last.map(entry => (entry.kind, entry.depth, entry.spans))
    posted.foldLeft((previous, Vector.empty[Posted])) {
      case ((before, done), entry) =>
        val quiet = entry == phaseHeadline(Phase.Act) &&
          before.contains((LogKind.Phase, 0, phaseWords(Phase.Wake)))
        val lines = if quiet then Vector(Posted.line(LogKind.Action,
          Vector(Text("Nothing happened in Wake"))), entry) else Vector(entry)
        (Some((entry.kind, entry.depth, entry.spans)), done ++ lines)
    }._2

  private def eventLines(journal: LogJournal, run: Option[Run], at: Int,
      viewer: Option[PlayerId]): (Vector[Posted], Option[Run]) =
    journal.event(at) match
      case OathEvent.GameStarted(_, _) =>
        (Vector(Posted.headline(LogKind.Round, Vector(Text("Setup")))), run)
      case OathEvent.RoundEnded(_, Some(next)) =>
        (roundHeadline(next) +: journal.readyAfter(at).toVector.flatMap(ready =>
          Vector(turnHeadline(ready.game.current.turn.activePlayer),
            phaseHeadline(Phase.Wake))), run)
      // After the eighth round no round begins; the victory headline follows.
      case OathEvent.RoundEnded(_, None) => (Vector.empty, run)
      case OathEvent.UsurperVictory(player) =>
        (Vector(victory(player, Vector(Text(" won as the Usurper")))), run)
      case OathEvent.VisionVictory(player, vision) =>
        (Vector(victory(player, Vector(Text(" won with "),
          words.vision(vision)))), run)
      case OathEvent.WarExhaustionResolved(winner, kind, vision, _) =>
        (Vector(victory(winner, exhaustion(kind, vision))), run)
      // A diagnostic, not play.
      case _: OathEvent.IgnoredRulesRecorded => (Vector.empty, run)
      case _: ConsentEvent => (consent.lines(journal, at, viewer), run)
      // Undo's facts post no line of their own; `format` keeps markers out
      // of every journal it formats.
      case _: OathEvent.CommandAccepted | _: OathEvent.StepTakenBack |
          _: OathEvent.StepRestored =>
        (Vector.empty, run)
      // A minor action, outside any walker run, is a section of its own.
      case _: OathEvent.SiteRelicsPeeked | _: OathEvent.OwnedRelicRevealed |
          _: OathEvent.WarbandsMoved =>
        val section = LogSection(journal.sequence(at), LogSections.Minor)
        (events.lines(journal, at, viewer).map(_.copy(section = Some(section))),
          run)
      // State-based changes, outside any section.
      case _: OathEvent.BanditsRefilled | _: OathEvent.UsurperFlipped =>
        (events.lines(journal, at, viewer), run)
      case _: WalkerStepRecorded | _: WalkerParked | _: WalkerCompleted |
          _: PowerNoted | _: ProcedureDequeued =>
        walker(journal, run, at, viewer)
      // Outside any run; `formatLive` files it in the latest section.
      case FollowUpSkipped(entry) =>
        (Vector(Posted.line(LogKind.Action,
          entry.source.toVector.flatMap(words.source(_,
            journal.readyBefore(at).toVector, viewer)) ++
            Vector(Text(s": ${starts.name(entry.procedure)
              .getOrElse(entry.procedure.key)} skipped.")))), run)
      case _: WalkerEvent => (Vector.empty, run)

  /** Lines for one event of a walker run. A run begins at the first walker
    * event after the previous run completed, and its procedure is read from
    * the closing event of that first segment. */
  private def walker(journal: LogJournal, current: Option[Run], at: Int,
      viewer: Option[PlayerId]): (Vector[Posted], Option[Run]) =
    current.orElse(for
      procedure <- journal.procedureAt(at)
      ready <- journal.readyBefore(at)
    yield Run(procedure, ready.game.current.turn.activePlayer, at, None)) match
      case None => (Vector.empty, None)
      case Some(run) =>
        val start = starts.start(journal, run, at, viewer)
        val begun = if start.isEmpty then run
          else run.copy(started = Some(journal.segmentEnd(at)))
        val section = LogSections.forRun(journal, begun)
        val lines = start ++
          (if start.isEmpty then Vector.empty
            else notes.held(journal, begun, at, viewer)) ++
          starts.continued(journal, begun, at).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          details.lines(journal, begun, at, viewer) ++
          notes.own(journal, begun, at, starts.opens(begun.procedure), viewer)
        // A headline the run posts (a turn or phase change) is in no section.
        val posted = lines.map(_.copy(section = section)) ++
          headlines(journal, at)
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => Some(begun)
        (posted, next)

  /** A turn begins at `BeginTurn(player, Wake)`, with its Wake headline.
    * Setup's closing one also opens Round 1; a `BeginTurn` into the round's
    * end posts nothing, since `gameplay.round-ended` posts the next round and
    * its first turn. Act and Rest begin at the `EnterPhase` into them. */
  private def headlines(journal: LogJournal, at: Int): Vector[Posted] =
    journal.ops(at).flatMap {
      case OpStep(BeginTurn(player, Phase.Wake), before, _) =>
        if before.game.current.turn.phase == Phase.Setup then
          Vector(roundHeadline(1), turnHeadline(player),
            phaseHeadline(Phase.Wake))
        else Vector(turnHeadline(player), phaseHeadline(Phase.Wake))
      case OpStep(EnterPhase(phase @ (Phase.Act | Phase.Rest)), before, _)
          if before.game.current.turn.phase != phase =>
        Vector(phaseHeadline(phase))
      case _ => Vector.empty
    }

  private def phaseWords(phase: Phase): Vector[LogSpan] = phase match
    case Phase.Wake => Vector(Text("Wake"))
    case Phase.Act => Vector(Text("Act"))
    case _ => Vector(Text("Rest"))

  private def phaseHeadline(phase: Phase): Posted =
    Posted.headline(LogKind.Phase, phaseWords(phase))

  private def roundHeadline(round: Int): Posted =
    Posted.headline(LogKind.Round, Vector(Text(s"Round $round")))

  private def turnHeadline(player: PlayerId): Posted =
    Posted.headline(LogKind.Turn, Vector(words.player(player), Text("'s turn")))

  private def victory(player: PlayerId, rest: Vector[LogSpan]): Posted =
    Posted.headline(LogKind.Victory, words.player(player) +: rest)

  private def exhaustion(kind: VictoryKind, vision: Option[VisionId])
      : Vector[LogSpan] = kind match
    case VictoryKind.Usurper => Vector(Text(" won as the Usurper"))
    case VictoryKind.Visionary => vision.fold(
      Vector[LogSpan](Text(" won as a Visionary")))(id =>
        Vector(Text(" won with "), words.vision(id)))
    case VictoryKind.Oathkeeper => Vector(Text(" won as the Oathkeeper"))
    case VictoryKind.RandomSelection => Vector(Text(" won by random selection"))
