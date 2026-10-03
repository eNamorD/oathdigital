package oathdigital.application.gamelog

import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Table.{p2, p3}
import oathdigital.testkit.Unwrap.accepted
import LogScripts._

class GameLogEventSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]) =
    texts(entries.filter(_.depth == 1))

  test("a revealed relic is named for everyone"):
    val script = revealRelic
    Vector(Some(script.actor), None).foreach { viewer =>
      val revealed = format(script, viewer).last
      assertEquals(revealed.kind, LogKind.Delta)
      assert(text(revealed).startsWith("Revealed "), text(revealed))
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
    // p2's pawn stands at Broken Peaks, so p2 sees its relics anyway; p3's
    // does not.
    assertEquals(Look(state).pawn(p2), site)
    val theirs = formatAppended(script, Vector(event), Some(p3), peeked).last
    // The peeker is the active player, so the subject is left out.
    assertEquals(text(theirs), "Peeked at " +
      (if relics.size == 1 then "a Relic" else s"${relics.size} Relics") +
      s" at ${presentation.siteLabel(site)}")

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
