package oathdigital.application.gamelog

import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Table.p3
import oathdigital.testkit.Unwrap.accepted
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

  test("a site peek names the relics for the peeker only"):
    val script = recordedEventsBoard
    val state = Outcomes.ready(script.history.steps.last.after)
    // The board's relic at Broken Peaks.
    val site = CatalogNames.site("Broken Peaks")
    val relics = Look(state).relicsAt(site)
    val peeker = script.actor
    val peeked = (state: OathState) => OathState.Ready(relics.foldLeft(
      Outcomes.ready(state))((known, relic) => accepted(new OperationExecutor()
        .execute(known, Peek(peeker, relic, Location.Site(site))))))
    val event = OathEvent.SiteRelicsPeeked(peeker, site, relics)
    val mine = formatAppended(script, Vector(event), Some(peeker), peeked).last
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    assert(text(mine).startsWith("Peeked at "), text(mine))
    // p3's pawn stands at Buried Giant, not Broken Peaks, so p3 reads only
    // that a relic was peeked at. (p2, at Broken Peaks, would read it anyway.)
    assertEquals(Look(state).pawn(p3), CatalogNames.site("Buried Giant"))
    val theirs = formatAppended(script, Vector(event), Some(p3), peeked).last
    // The peeker is the active player, so the subject is left out.
    assertEquals(text(theirs), "Peeked at " +
      (if relics.size == 1 then "a Relic" else s"${relics.size} Relics") +
      s" at ${presentation.siteLabel(site)}")

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
    assertEquals(texts(moves), Vector(s"Moved 2 warbands to $peaks",
      s"Moved 1 warband from $peaks"))
    assert(moves.forall(_.kind == LogKind.Action), moves)
    assertEquals(moves.map(_.section.map(_.action)),
      Vector.fill(2)(Some("move-warbands")))
    assertEquals(moves.map(_.section).distinct.size, 2)

  test("warbands moved, bandits returned and a new Usurper each post one line"):
    val script = recordedEventsBoard
    val site = CatalogNames.site("Ancient City")
    val other = script.otherSeat
    // A moved-warbands line reads only who moved, which way and how many;
    // the prior counts are the event's record of the board.
    val cases = Vector(
      OathEvent.WarbandsMoved(script.actor, site, toSite = true, amount = 2,
        priorBoardWarbands = 3, priorSiteWarbands = 1) ->
        s"Moved 2 warbands to ${presentation.siteLabel(site)}",
      OathEvent.WarbandsMoved(other, site, toSite = false, amount = 1,
        priorBoardWarbands = 3, priorSiteWarbands = 2) ->
        (s"${playerLabel(other)} moved 1 warband from " +
          presentation.siteLabel(site)),
      OathEvent.BanditsRefilled(Vector(site -> 1)) ->
        s"Bandits returned to ${presentation.siteLabel(site)}",
      OathEvent.UsurperFlipped(other) ->
        s"${playerLabel(other)} became the Usurper")
    cases.foreach { case (event, expected) =>
      assertEquals(lines(formatAppended(script, Vector(event))).last, expected)
    }
