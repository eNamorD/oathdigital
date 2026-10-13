package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureDequeued,
  WalkerCompleted, WalkerStepRecorded}
import oathdigital.model._
import LogSpan.Text

/** The tag that opens every action that can take modifiers (log action
  * sections design, "The tag entry"): the action's name and the Supply it
  * has spent by the end of the tag's segment, then, when there is more to
  * say, one line with a Campaign's kind and defender and the modifiers the
  * player chose.
  *
  * `resolutions` is each walker power's resolution: a power that resolves
  * automatically is not a choice, so it is not named. `picks` reads the
  * modifiers picked in a run's answers, in pick order.
  */
private[gamelog] final class StartLines(words: LogWords,
    resolutions: Map[PowerId, PowerResolution],
    picks: Vector[Answered] => Vector[PowerId]):

  def start(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    if run.started.nonEmpty then Vector.empty
    else opening(journal, run, at).toVector.flatMap { (named, said) =>
      val chosen = modifiers(journal, run, at, viewer)
      val line =
        if said.isEmpty then Option.when(chosen.nonEmpty)(Text("With ") +: chosen)
        else Some(said ++ (if chosen.isEmpty then Vector.empty
          else Text(" with ") +: chosen))
      Posted.line(LogKind.Section, Text(named) +:
        (queuedBy(journal, run, at, viewer) ++ cost(journal, run, at))) +:
        line.toVector.map(Posted.line(LogKind.Action, _))
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
  def continued(journal: LogJournal, run: Run, at: Int): Option[Posted] =
    run.procedure match
      case ActionRef.Recover
          if run.started.exists(_ != journal.segmentEnd(at)) =>
        val spent = journal.supplySpent(at, run.actor)
        Option.when(spent > 0)(Posted.line(LogKind.Action,
          Vector(Text("Continued"), LogSpan.Cost(spent, "Supply"))))
      case _ => None

  /** The tag's name and what its line says beyond the modifiers, but only at
    * the tag's anchor: the run's first event, except Muster and Trade (their
    * cost step, after the source decision), Recover (the step that opens its
    * dice pool, after its modifier decision), Travel (its first step after the
    * destination decision), Search (its first step once no more modifiers
    * can be picked), the facedown-adviser play (its adviser's pick) and
    * Campaign (the first event by which kind and defender are both
    * settled, or its completion). */
  private def opening(journal: LogJournal, run: Run, at: Int)
      : Option[(String, Vector[LogSpan])] =
    val completing = journal.event(at).isInstanceOf[WalkerCompleted]
    run.procedure match
      // A Campaign that ended before its kind was settled is tagged where it
      // completes.
      case ActionRef.Campaign =>
        campaign(journal, run, at).orElse(Option.when(completing)(Vector.empty))
          .map(said => ("Campaign", said))
      case ActionRef.Muster | ActionRef.Trade =>
        name(run.procedure).filter(_ =>
          paysCost(journal, run, at) || completing)
          .map(named => (named, Vector.empty))
      // The step that opens Recover's dice pool: the first of the Recover
      // itself, after its modifier decision.
      case ActionRef.Recover =>
        name(run.procedure).filter(_ =>
          opensDicePool(journal, at) || completing)
          .map(named => (named, Vector.empty))
      // The first step that does anything after the source or destination
      // is picked: a toll, a modifier's payment, the Supply, the draw or the
      // move.
      // A pick's payment does not count: the tag waits until no more picks
      // can follow, so that it names them all.
      case ActionRef.Travel | ActionRef.Search =>
        name(run.procedure).filter(_ =>
          (journal.ops(at).nonEmpty &&
            ModifierPicks.settled(journal.answers(run, at))) || completing)
          .map(named => (named, Vector.empty))
      // The adviser's pick: every modifier is picked by then.
      case ActionRef.PlayFacedownAdviser =>
        name(run.procedure).filter(_ => completing ||
          (journal.event(at) match
            case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _) =>
              id == CardPlayProcedure.facedownAdviserDecisionId
            case _ => false))
          .map(named => (named, Vector.empty))
      case other =>
        name(other).filter(_ => at == run.first)
          .map(named => (named, Vector.empty))

  /** Whether `procedure` opens with a tag, so its power lines wait for it. */
  def opens(procedure: ProcedureRef): Boolean = name(procedure).nonEmpty

  private[gamelog] def name(procedure: ProcedureRef): Option[String] =
    procedure match
      case ActionRef.Search => Some("Search")
      case ActionRef.PlayFacedownAdviser => Some("Play facedown adviser")
      case ActionRef.Recover => Some("Recover")
      case ActionRef.Forge => Some("Forge")
      case ActionRef.Travel => Some("Travel")
      case ActionRef.Muster => Some("Muster")
      case ActionRef.Trade => Some("Trade")
      case ActionRef.Challenge => Some("Challenge")
      case ActionRef.Campaign => Some("Campaign")
      // No modifier window, so no tag.
      case ActionRef.TakeWealth | ActionRef.PlaceBannerResource |
          ActionRef.Negotiation | ActionRef.PeekSiteRelics |
          ActionRef.RevealOwnedRelic | ActionRef.MoveWarbands |
          _: ActionRef.UsePower => None
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

  private def opensDicePool(journal: LogJournal, at: Int): Boolean =
    journal.ops(at).exists {
      case OpStep(pool: ModifyDicePool, _, _) =>
        pool.pool == RecoverProcedure.recoverPool
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
    if spent > 0 then Vector(LogSpan.Cost(spent, "Supply")) else Vector.empty
