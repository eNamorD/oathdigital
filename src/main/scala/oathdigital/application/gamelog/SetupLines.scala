package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** Setup's lines (spec, "Setup lines"): one per pawn placed, one per
  * adviser kept and one per card revealed. Setup has no turn yet, so every
  * line names its player. */
private[gamelog] final class SetupLines(words: LogWords):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] = journal.ops(at).collect {
    case OpStep(Move(Piece.Pawn(player), _,
        PositionedLocation(Location.Site(site), _), _), _, _) =>
      Posted.line(LogKind.Action, Vector(words.player(player),
        Text(" placed pawn at "), words.site(site)))
    case OpStep(Move(Piece.Card(card),
        PositionedLocation(Location.Hand(holder), _),
        PositionedLocation(Location.PlayArea(owner), _), _), before, after)
        if holder == owner =>
      Posted.line(LogKind.Decision, Vector(words.player(owner),
        Text(" kept ")) ++ words.one(words.card(card, before, after, viewer)))
    case OpStep(Reveal(card, Location.PlayArea(owner)), before, after) =>
      Posted.line(LogKind.Decision, Vector(words.player(owner),
        Text(" revealed ")) ++ words.one(words.card(card, before, after, viewer)))
  }
