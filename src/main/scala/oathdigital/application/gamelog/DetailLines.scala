package oathdigital.application.gamelog

import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}

/** The detail lines of a walker run (spec, "Detail lines"): decisions, rolls
  * and deltas, between a start line and the action line that closes the
  * action. A rule here stays silent wherever an action line, a start line or
  * another procedure's own lines already say the same thing.
  */
private[gamelog] final class DetailLines(words: LogWords,
    choices: ChoiceWords):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    decision(journal, run, at, viewer)

  private def decision(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _, _)
        if !DetailLines.narrated(id) =>
      val refs = answer match
        case ChooseOneAnswer(ref) => Vector(ref)
        case ChooseManyAnswer(selected) => selected
        case _ => Vector.empty
      (for
        before <- journal.readyBefore(at)
        after <- journal.readyAfter(at)
        if refs.nonEmpty
      yield Posted.line(LogKind.Decision, words.subject(by, run.actor, "chose")
        ++ choices.options(refs, before, after, viewer))).toVector
    case _ => Vector.empty

private[gamelog] object DetailLines:
  /** Decisions other lines tell: Setup's own lines, Card Play's placement
    * line and the discard line of a replaced adviser, every Campaign and
    * Negotiation line, and each action line that names its choice. */
  private val NarratedPrefixes = Vector("setup.", ActionLines.PlacePrefix,
    "cardplay.replace.", "campaign.", "negotiation.")
  private val NarratedIds = Set(SearchProcedure.cardDecisionId,
    RecoverProcedure.choiceDecisionId, RecoverProcedure.relicDecisionId,
    MusterProcedure.decisionId, TradeProcedure.decisionId,
    ForgeProcedure.assignmentDecisionId, ChallengeProcedure.bannerDecisionId,
    ChallengeProcedure.amountDecisionId,
    PlaceBannerResourceProcedure.bannerDecisionId,
    PlaceBannerResourceProcedure.amountDecisionId,
    OathkeeperProcedure.recipientDecisionId)

  def narrated(decisionId: String): Boolean =
    NarratedIds(decisionId) || NarratedPrefixes.exists(decisionId.startsWith)
