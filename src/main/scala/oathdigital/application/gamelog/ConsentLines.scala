package oathdigital.application.gamelog

import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequested}
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** The consent lines (consent design, "Game Log"). The request leaves out
  * its actor, as every actor line does; an answer names its granter, who
  * acts off turn. A preference line, and a reset, are formatted for their
  * granter alone. */
private[gamelog] final class ConsentLines(words: LogWords,
    choices: ChoiceWords, powers: ConsentPowers):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    (for
      before <- journal.readyBefore(at)
      after <- journal.readyAfter(at)
    yield
      def named(ids: Vector[PowerId]): Vector[LogSpan] = LogWords.join(
        ids.map(id => powers.source(id).fold(Vector[LogSpan](Text(id.value)))(
          words.source(_, Vector(before), viewer))))
      def line(spans: Vector[LogSpan]) =
        Vector(Posted.line(LogKind.Decision, spans))
      journal.event(at) match
        case ConsentRequested(request) => line(
          (Text("Asked ") +: LogWords.join(request.asks.map(ask =>
            Vector(words.player(ask.granter))))) ++
          (Text(" for consent to ignore ") +:
            named(request.asks.flatMap(_.powers).distinct)) ++
          (Text(" and select ") +: choices.option(request.target, before,
            after, viewer)) :+ Text("."))
        case ConsentAnswered(granter, granted) => line(Vector(
          words.player(granter),
          Text(if granted then " gave consent." else " gave no consent.")))
        case ConsentPreferenceSet(granter, power, requester, setting)
            if viewer.contains(granter) =>
          val (verb, linking) = setting match
            case ConsentSetting.Always => (" now always allows ", " to ignore ")
            case ConsentSetting.Never => (" now never allows ", " to ignore ")
            case ConsentSetting.CaseByCase =>
              (" now decides case by case whether ", " may ignore ")
          line((Vector(words.player(granter), Text(verb),
            words.player(requester), Text(linking)) ++ named(Vector(power))) :+
            Text("."))
        case ConsentPreferencesCleared(granter, power)
            if viewer.contains(granter) =>
          line((Vector(words.player(granter),
            Text("'s consent preferences for ")) ++ named(Vector(power))) :+
            Text(" were reset."))
        case _ => Vector.empty
    ).getOrElse(Vector.empty)
