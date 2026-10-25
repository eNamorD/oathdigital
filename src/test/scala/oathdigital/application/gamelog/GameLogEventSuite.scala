package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.{LogEntry, LogKind, LogSpan}
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p3
import LogScripts._

class GameLogEventSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]) =
    texts(entries.filter(_.depth == 1))

  test("a revealed relic is named for everyone"):
    val script = revealRelic
    Vector(Some(script.actor), None).foreach { viewer =>
      val revealed = entryStarting(format(script, viewer), "Revealed ")
      assertEquals(revealed.kind, LogKind.Action)
      assert(revealed.spans.exists(_.isInstanceOf[LogSpan.Card]),
        revealed.spans)
    }

  test("Peek Site Relics names the relics for the peeker only"):
    val script = peekRelics
    val site = CatalogNames.site("Broken Peaks")
    val mine = entryStarting(format(script, Some(script.actor)), "Peeked at ")
    assertEquals(mine.kind, LogKind.Action)
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    assertEquals(mine.section.map(_.action), Some("peek-site-relics"))
    // p3 stands at Buried Giant, so p3 reads only that a relic was peeked
    // at. The peeker is the active player, so the subject is left out.
    val theirs = entryStarting(format(script, Some(p3)), "Peeked at ")
    assertEquals(text(theirs),
      s"Peeked at a Relic at ${presentation.siteLabel(site)}")

  test("each Move Warbands posts one line, in a section of its own"):
    val peaks = presentation.siteLabel(CatalogNames.site("Broken Peaks"))
    val moves = format(warbandsMoved, None)
      .filter(entry => text(entry).startsWith("Moved "))
    assertEquals(texts(moves), Vector(
      s"Moved 2 warbands from board to $peaks",
      s"Moved 1 warband from $peaks to board"))
    assert(!format(warbandsMoved, None).exists(entry =>
      text(entry).startsWith("Chose ")), "the move line tells the direction")
    assert(moves.forall(_.kind == LogKind.Action), moves)
    assertEquals(moves.map(_.section.map(_.action)),
      Vector.fill(2)(Some("move-warbands")))
    assertEquals(moves.map(_.section).distinct.size, 2)

  test("bandits returned and a new Usurper each post one line"):
    val script = recordedEventsBoard
    val site = CatalogNames.site("Ancient City")
    val other = script.otherSeat
    val cases = Vector(
      OathEvent.BanditsRefilled(Vector(site -> 1)) ->
        s"Bandits returned to ${presentation.siteLabel(site)}",
      OathEvent.UsurperFlipped(other) ->
        s"${playerLabel(other)} became the Usurper")
    cases.foreach { case (event, expected) =>
      assertEquals(lines(formatAppended(script, Vector(event))).last, expected)
    }
