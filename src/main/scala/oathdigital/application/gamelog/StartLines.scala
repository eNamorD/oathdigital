package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureDequeued,
  WalkerCompleted, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** The tag that opens every action that can take modifiers (log action
  * sections design, "The tag entry"): the action's name and the Supply it
  * has spent by the end of the tag's segment, then, when there is more to
  * say, one line with a Campaign's kind and defender and the modifiers the
  * player chose.
  *
  * Which procedures have a tag, where each tag posts and what its opening
  * line says are declarations, so they live in the registry
  * ([[ProcedureLog]]); this class assembles the tag from the entry it is
  * given and holds the anchors and the opening the entries name.
  *
  * `resolutions` is each walker power's resolution: a power that resolves
  * automatically is not a choice, so it is not named. `picks` reads the
  * modifiers picked in a run's answers, in pick order.
  */
private[gamelog] final class StartLines(words: LogWords,
    resolutions: Map[PowerId, PowerResolution],
    picks: Vector[Answered] => Vector[PowerId]):

  def start(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId], log: ProcedureLog): Vector[Posted] =
    if run.started.nonEmpty then Vector.empty
    else log.tag.filter(_ => log.anchor(journal, run, at)).toVector
      .flatMap { named =>
        val said = log.opening(journal, run, at, viewer)
        val chosen = modifiers(journal, run, at, viewer)
        val line =
          if said.isEmpty then
            Option.when(chosen.nonEmpty)(Text("With ") +: chosen)
          else Some(said ++ (if chosen.isEmpty then Vector.empty
            else Text(" with ") +: chosen))
        Posted(LogKind.Section, Text(named) +:
          (queuedBy(journal, run, at, viewer) ++ cost(journal, run, at))) +:
          line.toVector.map(Posted(LogKind.Action, _))
      }

  /** " · Knights Errant" when the run was started from the queue: its
    * `ProcedureDequeued` is in the run's first segment. */
  private def queuedBy(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    (run.first to journal.segmentEnd(at)).iterator.map(journal.event)
      .collectFirst { case ProcedureDequeued(entry) => entry }
      .toVector.flatMap(entry => entry.source.toVector.flatMap(source =>
        Text(" · ") +: words.source(source,
          journal.readyBefore(run.first).toVector, viewer)))

  /** Recover spends Supply again each time the player continues rolling. */
  def continuedRecover(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] =
    if !run.started.exists(_ != journal.segmentEnd(at)) then None
    else
      val spent = journal.supplySpent(at, run.actor)
      Option.when(spent > 0)(Posted(LogKind.Action,
        Vector(Text("Continued"), LogSpan.Cost(spent))))

  private def completing(journal: LogJournal, at: Int): Boolean =
    journal.event(at).isInstanceOf[WalkerCompleted]

  /** Muster and Trade: their cost step, after the source decision. */
  val paysCost: Anchor = (journal, run, at) =>
    paysCostAt(journal, run, at) || completing(journal, at)

  /** Recover: the step that opens its dice pool, after its modifier
    * decision. */
  val opensDicePool: Anchor = (journal, run, at) =>
    opensDicePoolAt(journal, at) || completing(journal, at)

  /** Travel and Search: the first step that does anything after the source
    * or destination is picked — a toll, a modifier's payment, the Supply,
    * the draw or the move. A pick's payment does not count: the tag waits
    * until no more picks can follow, so that it names them all. */
  val picksSettled: Anchor = (journal, run, at) =>
    (journal.ops(at).nonEmpty &&
      ModifierPicks.settled(journal.answers(run, at))) ||
      completing(journal, at)

  /** The facedown adviser's pick: every modifier is picked by then. */
  val adviserPicked: Anchor = (journal, run, at) =>
    completing(journal, at) || (journal.event(at) match
      case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _) =>
        id == CardPlayProcedure.facedownAdviserDecisionId
      case _ => false)

  /** Campaign: the first event by which kind and defender are both settled.
    * A Campaign that ended before its kind was settled is tagged where it
    * completes, with an empty opening. */
  val campaignSettled: Anchor = (journal, run, at) =>
    campaign(journal, run, at).nonEmpty || completing(journal, at)

  /** Campaign's kind and defender, or nothing when the Campaign completed
    * before they were settled. Read twice per tagged event, once by
    * [[campaignSettled]] and once here; both reads are pure. */
  val campaignWords: Opening = (journal, run, at, _) =>
    campaign(journal, run, at).getOrElse(Vector.empty)

  private def paysCostAt(journal: LogJournal, run: Run, at: Int): Boolean =
    journal.ops(at).exists {
      case OpStep(SpendSupply(player, _, _), _, _) => player == run.actor
      case OpStep(Move(Piece.Favor(_) | Piece.Secrets(_),
          PositionedLocation(Location.PlayArea(player), _, _),
          PositionedLocation(Location.OnCard(_), _, _), _, _), _, _) =>
        player == run.actor
      case _ => false
    }

  private def opensDicePoolAt(journal: LogJournal, at: Int): Boolean =
    journal.ops(at).exists {
      case OpStep(pool: ModifyDicePool, _, _) =>
        pool.pool == RecoverProcedure.recoverPool
      case _ => false
    }

  /** Kind and defender from the answers so far and the state after `at`,
    * read exactly as the procedure reads them, through the one function that
    * owns that read; failing that, from the recorded result, which is a
    * journal read and so stays here. */
  private def campaign(journal: LogJournal, run: Run, at: Int)
      : Option[Vector[LogSpan]] =
    val pending = PendingTree(Vector.empty, journal.answers(run, at))
    journal.readyAfter(at)
      .flatMap(ready => CampaignSetup.declaredAt(ready, run.actor, pending))
      .map((kind, defender) => campaignLine(kind, defender))
      .orElse(journal.ops(at).collectFirst {
        case OpStep(RecordCampaignResult(result), _, _) =>
          campaignLine(result.kind, result.defender)
      })

  private def campaignLine(kind: CampaignKind, defender: CampaignDefender)
      : Vector[LogSpan] =
    val named = kind match
      case CampaignKind.Raid => "Raid"
      case CampaignKind.Conquest => "Conquest"
    defender match
      case CampaignDefender.Bandits =>
        Vector(Text(s"$named against the bandits"))
      case CampaignDefender.Player(player) =>
        Vector(Text(s"$named against "), words.player(player))

  /** The modifiers the run's answers pick, in pick order. Automatic powers
    * are left out. */
  private def modifiers(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    val shown = picks(journal.answers(run, journal.segmentEnd(at)))
      .filterNot(id =>
        resolutions.get(id).contains(PowerResolution.Automatic))
    journal.readyBefore(run.first).filter(_ => shown.nonEmpty)
      .fold(Vector.empty[LogSpan])(ready => LogWords.join(
        shown.map(id => words.power(ready, run.actor, id, viewer))))

  private def cost(journal: LogJournal, run: Run, at: Int): Vector[LogSpan] =
    val spent = (run.first to journal.segmentEnd(at))
      .map(journal.supplySpent(_, run.actor)).sum
    if spent > 0 then Vector(LogSpan.Cost(spent)) else Vector.empty
