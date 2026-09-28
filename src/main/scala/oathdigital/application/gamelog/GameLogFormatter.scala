package oathdigital.application.gamelog

import oathdigital.application.{GamePresentationProjector,
  WalkerDecisionProjector}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.ReplayStep
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.{PowerNoted, WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
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
  private val starts = new StartLines(words, WalkerPowerCatalog.default(catalog)
    .powers.map(power => power.id -> power.resolution).toMap)
  private val choices = new ChoiceWords(words,
    new WalkerDecisionProjector(catalog, presentation))
  private val actions = new ActionLines(words, choices, catalog)
  private val details = new DetailLines(words, choices, wordings.narrated)
  private val events = new EventLines(words)
  private val notes = new PowerLines(words, wordings)

  def format(steps: Vector[ReplayStep[OathState, OathEvent]],
      viewer: Option[PlayerId]): Vector[LogEntry] =
    val journal = new LogJournal(steps)
    (0 until journal.size).foldLeft(
        (Option.empty[Run], Vector.empty[LogEntry])) {
      case ((run, entries), at) =>
        val (posted, next) = eventLines(journal, run, at, viewer)
        (next, entries ++ posted.zipWithIndex.map { case (entry, ordinal) =>
          LogEntry(journal.sequence(at), ordinal, entry.kind, entry.depth,
            entry.spans) })
    }._2

  private def eventLines(journal: LogJournal, run: Option[Run], at: Int,
      viewer: Option[PlayerId]): (Vector[Posted], Option[Run]) =
    journal.event(at) match
      case OathEvent.GameStarted(_, _) =>
        (Vector(Posted.headline(LogKind.Round, Vector(Text("Setup")))), run)
      case OathEvent.RoundEnded(_, Some(next)) =>
        (roundHeadline(next) +: journal.readyAfter(at).toVector.map(ready =>
          turnHeadline(ready.game.current.turn.activePlayer)), run)
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
      // Minor actions and state-based changes, outside any walker run.
      case _: OathEvent.SiteRelicsPeeked | _: OathEvent.OwnedRelicRevealed |
          _: OathEvent.WarbandsMoved | _: OathEvent.BanditsRefilled |
          _: OathEvent.UsurperFlipped =>
        (events.lines(journal, at, viewer), run)
      case _: WalkerStepRecorded | _: WalkerParked | _: WalkerCompleted |
          _: PowerNoted =>
        walker(journal, run, at, viewer)
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
        val posted = start.toVector ++
          (if start.isEmpty then Vector.empty
            else notes.held(journal, begun, at, viewer)) ++
          starts.continued(journal, begun, at).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          details.lines(journal, begun, at, viewer) ++
          notes.own(journal, begun, at, starts.opens(begun.procedure), viewer) ++
          turnHeadlines(journal, at)
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => Some(begun)
        (posted, next)

  /** A turn begins at `BeginTurn(player, Wake)`. Setup's closing one also
    * opens Round 1; a `BeginTurn` into the round's end posts nothing, since
    * `gameplay.round-ended` posts the next round and its first turn. */
  private def turnHeadlines(journal: LogJournal, at: Int): Vector[Posted] =
    journal.ops(at).flatMap {
      case OpStep(BeginTurn(player, Phase.Wake), before, _) =>
        if before.game.current.turn.phase == Phase.Setup then
          Vector(roundHeadline(1), turnHeadline(player))
        else Vector(turnHeadline(player))
      case _ => Vector.empty
    }

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
