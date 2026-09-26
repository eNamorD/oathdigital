package oathdigital.application.gamelog

import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, DeclineDeal}
import ActionLines.{action, isDelta}
import LogSpan.Text

/** Negotiation's lines (spec, "Negotiation"): who negotiated or who ended
  * it, then the settlement, one line per disclosure and transfer, each with
  * its own subject. Proposals and counter-proposals post nothing. */
private[gamelog] final class NegotiationLines(words: LogWords):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    negotiation(journal, run, at) ++ settlement(journal, at, viewer)

  /** "Negotiation ended by …" at the decline; otherwise "Negotiated with …"
    * at the settlement batch, or at completion when the deal settled
    * nothing. */
  private def negotiation(journal: LogJournal, run: Run, at: Int)
      : Vector[Posted] =
    def negotiated = Vector(action(Text("Negotiated with ") +: LogWords.join(
      negotiators(journal, run, at).map(player => Vector(words.player(player))))))
    val declined = journal.answers(run, at).exists {
      case Answered(NegotiationDeal.dealDecisionId, DeclineDeal, _) => true
      case _ => false
    }
    val settledEarlier = (run.first until at).exists(index =>
      isDelta(journal.event(index)))
    journal.event(at) match
      case WalkerStepRecorded(_, ChoicePayload(NegotiationDeal.dealDecisionId,
          DeclineDeal, by), _, _) =>
        Vector(action(Vector(Text("Negotiation ended by "), words.player(by))))
      case event if isDelta(event) && !settledEarlier => negotiated
      case _: WalkerCompleted if !declined && !settledEarlier => negotiated
      case _ => Vector.empty

  /** The negotiators decision's answer, or the one eligible player when it
    * was not asked. */
  private def negotiators(journal: LogJournal, run: Run, at: Int)
      : Vector[PlayerId] =
    journal.answers(run, at).collectFirst {
      case Answered(NegotiationDeal.negotiatorsDecisionId,
          ChooseManyAnswer(refs), _) =>
        refs.collect { case DecisionOptionRef.Player(id) => id }
    }.getOrElse(journal.readyBefore(run.first).fold(Vector.empty[PlayerId])(
      NegotiationDeal.eligible(_, run.actor)))


  private def settlement(journal: LogJournal, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    def delta(spans: Vector[LogSpan]) = Posted.line(LogKind.Delta, spans)
    journal.ops(at).collect {
      case OpStep(Give(Piece.Favor(amount), giver, _,
          Location.PlayArea(recipient), _), _, _) =>
        delta(Vector(words.player(giver), Text(s" gave $amount favor to "),
          words.player(recipient)))
      case OpStep(Give(Piece.Card(id), giver, _, Location.PlayArea(recipient),
          _), before, after) =>
        delta(Vector(words.player(giver), Text(" gave ")) ++
          words.slotted(id, giver, before, after, viewer) ++
          Vector(Text(" to "), words.player(recipient)))
      case OpStep(Peek(recipient, id, Location.PlayArea(owner)), before,
          after) =>
        delta(Vector(words.player(owner), Text(" showed "),
          words.player(recipient), Text(" ")) ++
          words.slotted(id, owner, before, after, viewer))
      case OpStep(Peek(recipient, id, Location.Site(site)), before, after) =>
        delta(Vector(words.player(recipient), Text(" was shown ")) ++
          words.one(words.card(id, before, after, viewer)) ++
          Vector(Text(" at "), words.site(site)))
    }
