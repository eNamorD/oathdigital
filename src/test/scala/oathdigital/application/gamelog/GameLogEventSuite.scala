package oathdigital.application.gamelog

import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.model._
import LogScripts._

class GameLogEventSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]) =
    texts(entries.filter(_.depth == 1))

  /** `script`'s journal with one event appended, from its last state to
    * `after`. */
  private def ending(script: Script, event: OathEvent,
      after: OathState => OathState = identity, viewer: Option[PlayerId])
      : Vector[LogEntry] =
    val steps = script.history.steps
    val last = steps.last.after
    formatter.format(steps :+ ReplayStep(RecordedEvent(steps.size.toLong,
      event), last, after(last)), viewer)

  private def ready(state: OathState): ReadyGame = state match
    case OathState.Ready(ready) => ready
    case other => fail(s"expected a ready game, got $other")

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
    val script = woken
    val state = ready(script.history.steps.last.after)
    val (site, relics) = state.game.current.map.sites.collectFirst {
      case (id, site) if site.relics.nonEmpty => id -> site.relics.map(_.id)
    }.get
    val peeker = script.actor
    val peeked = (state: OathState) => OathState.Ready(relics.foldLeft(ready(state))(
      (known, relic) => new OperationExecutor().execute(known,
        Peek(peeker, relic, Location.Site(site))).toOption.get))
    val event = OathEvent.SiteRelicsPeeked(peeker, site, relics)
    val mine = ending(script, event, peeked, Some(peeker)).last
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    assert(text(mine).startsWith("Peeked at "), text(mine))
    // A player whose pawn stands at the site sees its relics anyway.
    val other = state.game.current.players.find(player =>
      player.player != peeker && !player.pawnSite.contains(site)).map(_.player)
    val theirs = ending(script, event, peeked, other).last
    // The peeker is the active player, so the subject is left out.
    assertEquals(text(theirs), "Peeked at " +
      (if relics.size == 1 then "a Relic" else s"${relics.size} Relics") +
      s" at ${presentation.siteLabel(site)}")

  test("warbands moved, bandits returned and a new Usurper each post one line"):
    val script = woken
    val state = ready(script.history.steps.last.after)
    val site = state.game.current.map.inPlay.head
    val other = script.players.find(_ != script.actor).get
    val cases = Vector(
      OathEvent.WarbandsMoved(script.actor, site, true, 2, 3, 1) ->
        s"Moved 2 warbands to ${presentation.siteLabel(site)}",
      OathEvent.WarbandsMoved(other, site, false, 1, 3, 2) ->
        s"${name(other)} moved 1 warband from ${presentation.siteLabel(site)}",
      OathEvent.BanditsRefilled(Vector(site -> 1)) ->
        s"Bandits returned to ${presentation.siteLabel(site)}",
      OathEvent.UsurperFlipped(other) -> s"${name(other)} became the Usurper")
    cases.foreach { case (event, expected) =>
      assertEquals(lines(ending(script, event, viewer = None)).last, expected)
    }
