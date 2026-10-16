package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** The events that happen outside any walker run (spec, "Detail lines"):
  * two state-based changes at Wake. The subject is left out when the active
  * player acts. */
private[gamelog] final class EventLines(words: LogWords):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    (for
      before <- journal.readyBefore(at)
      after <- journal.readyAfter(at)
    yield
      journal.event(at) match
        case OathEvent.BanditsRefilled(sites) =>
          Vector(Posted.line(LogKind.Trigger, Text("Bandits returned to ") +:
            LogWords.join(sites.map { case (site, _) =>
              Vector(words.site(site)) })))
        case OathEvent.UsurperFlipped(player) =>
          Vector(Posted.line(LogKind.Trigger, Vector(words.player(player),
            Text(" became the Usurper"))))
        case _ => Vector.empty
    ).getOrElse(Vector.empty)
