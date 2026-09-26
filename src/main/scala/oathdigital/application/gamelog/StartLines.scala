package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.model._
import LogSpan.Text

/** The line that opens every action that can take modifiers (spec, "Start
  * lines"): its opening words, the modifiers the player chose, and the Supply
  * the action has spent by the end of the start line's segment.
  *
  * `resolutions` is each walker power's resolution: a power that resolves
  * automatically is not a choice, so it is not named.
  */
private[gamelog] final class StartLines(words: LogWords,
    resolutions: Map[PowerId, PowerResolution]):

  def start(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Option[Posted] =
    if run.started.nonEmpty then None
    else opening(journal, run, at).map(spans => Posted.line(LogKind.Action,
      spans ++ modifiers(journal, run, at, viewer) ++ cost(journal, run, at)))

  /** Recover spends Supply again each time the player continues rolling. */
  def continued(journal: LogJournal, run: Run, at: Int): Option[Posted] =
    run.procedure match
      case ActionRef.Recover
          if run.started.exists(_ != journal.segmentEnd(at)) =>
        val spent = journal.supplySpent(at, run.actor)
        Option.when(spent > 0)(Posted.line(LogKind.Action,
          Vector(Text("Continued Recover"), LogSpan.Cost(spent, "Supply"))))
      case _ => None

  /** The opening words, but only at the start line's anchor: the run's
    * first event, except Muster and Trade (their cost step, after the source
    * decision) and Campaign (the first event by which kind and defender are
    * both settled). */
  private def opening(journal: LogJournal, run: Run, at: Int)
      : Option[Vector[LogSpan]] =
    val completing = journal.event(at).isInstanceOf[WalkerCompleted]
    run.procedure match
      case ActionRef.Campaign => campaign(journal, run, at)
      case ActionRef.Muster | ActionRef.Trade =>
        title(run.procedure).filter(_ =>
          paysCost(journal, run, at) || completing).map(t => Vector(Text(t)))
      case other =>
        title(other).filter(_ => at == run.first).map(t => Vector(Text(t)))

  private def title(procedure: ProcedureRef): Option[String] =
    procedure match
      case ActionRef.Search => Some("Started Search")
      case ActionRef.PlayFacedownAdviser => Some("Playing Facedown Adviser")
      case ActionRef.Recover => Some("Started Recover")
      case ActionRef.Forge => Some("Started Forge")
      case ActionRef.Travel => Some("Started Travel")
      case ActionRef.Muster => Some("Started Muster")
      case ActionRef.Trade => Some("Started Trade")
      case ActionRef.Challenge => Some("Started Challenge")
      // Built from its kind and defender by `campaign`.
      case ActionRef.Campaign => None
      // No modifier window, so no start line.
      case ActionRef.TakeWealth | ActionRef.PlaceBannerResource |
          ActionRef.Negotiation | _: ActionRef.UsePower => None
      case _: PhaseTransitionRef | _: TriggeredProcedureRef => None

  private def paysCost(journal: LogJournal, run: Run, at: Int): Boolean =
    journal.ops(at).exists {
      case OpStep(SpendSupply(player, _, _), _, _) => player == run.actor
      case OpStep(Move(Piece.Favor(_) | Piece.Secrets(_),
          PositionedLocation(Location.PlayArea(player), _),
          PositionedLocation(Location.OnCard(_), _), _), _, _) =>
        player == run.actor
      case _ => false
    }

  /** Kind and defender from the answers so far and the state after `at`,
    * read exactly as the procedure reads them; failing that, from the
    * recorded result. */
  private def campaign(journal: LogJournal, run: Run, at: Int)
      : Option[Vector[LogSpan]] =
    val pending = PendingTree(Vector.empty, journal.answers(run, at))
    journal.readyAfter(at).flatMap(ready => for
      kind <- CampaignSetup.kindOf(ready, run.actor, pending)
      defender <- CampaignSetup.defenderOf(ready, run.actor, pending, kind)
    yield campaignWords(kind, defender)).orElse(journal.ops(at).collectFirst {
      case OpStep(RecordCampaignResult(result), _, _) =>
        campaignWords(result.kind, result.defender)
    })

  private def campaignWords(kind: CampaignKind, defender: CampaignDefender)
      : Vector[LogSpan] =
    val named = kind match
      case CampaignKind.Raid => "Raid"
      case CampaignKind.Conquest => "Conquest"
    defender match
      case CampaignDefender.Bandits =>
        Vector(Text(s"Started Campaign: $named against the bandits"))
      case CampaignDefender.Player(player) =>
        Vector(Text(s"Started Campaign: $named against "), words.player(player))

  /** The player's selection as a parked run records it, or, for a run that
    * never parked, the powers its steps record as contributing; automatic
    * powers are left out either way. */
  private def modifiers(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    val through = (run.first to journal.segmentEnd(at)).toVector
    val chosen = through.reverseIterator.map(journal.event).collectFirst {
      case parked: WalkerParked => parked.modifiers
    }.getOrElse(through.map(journal.event).flatMap {
      case step: WalkerStepRecorded => step.contributions
      case _ => Vector.empty
    }.distinct)
    val shown = chosen.filterNot(id =>
      resolutions.get(id).contains(PowerResolution.Automatic))
    journal.readyBefore(run.first).filter(_ => shown.nonEmpty)
      .fold(Vector.empty[LogSpan])(ready => Text(" with ") +: LogWords.join(
        shown.map(id => words.power(ready, run.actor, id, viewer))))

  private def cost(journal: LogJournal, run: Run, at: Int): Vector[LogSpan] =
    val spent = (run.first to journal.segmentEnd(at))
      .map(journal.supplySpent(_, run.actor)).sum
    if spent > 0 then Vector(LogSpan.Cost(spent, "Supply")) else Vector.empty
