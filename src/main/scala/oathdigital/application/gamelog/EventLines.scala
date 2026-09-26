package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** The events that happen outside any walker run (spec, "Detail lines"):
  * three minor actions, and two state-based changes at Wake. The subject is
  * left out when the active player acts. */
private[gamelog] final class EventLines(words: LogWords):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    (for
      before <- journal.readyBefore(at)
      after <- journal.readyAfter(at)
    yield
      val active = before.game.current.turn.activePlayer
      def card(id: CardId) = words.card(id, before, after, viewer)
      journal.event(at) match
        case OathEvent.SiteRelicsPeeked(player, site, relics) =>
          Vector(Posted.line(LogKind.Delta, words.subject(player, active,
            "peeked at") ++ words.cards(relics.map(card)) ++
            Vector(Text(" at "), words.site(site))))
        case OathEvent.OwnedRelicRevealed(player, relic) =>
          Vector(Posted.line(LogKind.Delta, words.subject(player, active,
            "revealed") ++ words.one(card(relic))))
        case OathEvent.WarbandsMoved(player, site, toSite, amount, _, _) =>
          Vector(Posted.line(LogKind.Delta, words.subject(player, active,
            "moved") ++ Vector(Text(s"$amount " +
              ActionLines.plural(amount, "warband", "warbands") +
              (if toSite then " to " else " from ")), words.site(site))))
        case OathEvent.BanditsRefilled(sites) =>
          Vector(Posted.line(LogKind.Trigger, Text("Bandits returned to ") +:
            LogWords.join(sites.map { case (site, _) =>
              Vector(words.site(site)) })))
        case OathEvent.UsurperFlipped(player) =>
          Vector(Posted.line(LogKind.Trigger, Vector(words.player(player),
            Text(" became the Usurper"))))
        case _ => Vector.empty
    ).getOrElse(Vector.empty)
