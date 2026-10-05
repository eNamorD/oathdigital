package oathdigital.application.gamelog

import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.protocol.projection.{LogPageCodec, LogSectionWire}
import oathdigital.server.{DevelopmentRoutes, GameServerGateway}
import oathdigital.testkit.HttpTestServer
import oathdigital.testkit.Unwrap.{accepted, present}

class GameLogRouteSuite extends munit.FunSuite:
  test("the projector pages the log after a sequence, and refuses past the end"):
    val script = LogScripts.round
    val history = script.history
    val projector = new GameProjector(catalog)
    val whole = present(projector.logPage("round", history, 0L, script.actor),
      "the whole log page")
    assertEquals(whole.nextSequence, history.nextSequence)
    assert(whole.entries.nonEmpty)
    val after = whole.entries(whole.entries.size / 2).sequence
    val tail = present(projector.logPage("round", history, after, script.actor),
      s"the log page after $after")
    assertEquals(tail.entries, whole.entries.filter(_.sequence >= after))
    assertEquals(tail.after, after)
    assertEquals(projector.logPage("round", history, history.nextSequence,
      script.actor).map(_.entries), Some(Vector.empty))
    assertEquals(projector.logPage("round", history, history.nextSequence + 1,
      script.actor), None)

  test("the log page carries each line's section, and no headline has one"):
    val script = LogScripts.round
    val page = present(new GameProjector(catalog).logPage("round",
      script.history, 0L, script.actor), "the whole log page")
    val tag = page.entries.find(_.kind == "section").getOrElse(
      fail(s"no tag in ${page.entries}"))
    assertEquals(tag.section, Some(LogSectionWire(tag.sequence, "travel")))
    assert(page.entries.filter(_.depth == 0).forall(_.section.isEmpty),
      page.entries)

  test("the development route binds playerId, checks the cursor, and leaves raw events alone"):
    val script = LogScripts.woken
    val gateway = new GameServerGateway(script.service,
      new GameProjector(catalog))
    val server = HttpTestServer.start("game-log-route-test")(blocking =>
      DevelopmentRoutes.route(gateway, blocking))
    val playerId = script.actor.value
    try
      val page =
        server.get(s"/api/dev/first-games/woken/log/0?playerId=$playerId")
      assertEquals(page.statusCode(), 200, page.body())
      val decoded = accepted(LogPageCodec.decode(page.body()))
      assertEquals(decoded.gameId, "woken")
      assertEquals(decoded.entries.head.spans.head.text, "Setup")
      assertEquals(server.get(
        s"/api/dev/first-games/woken/log/-1?playerId=$playerId").statusCode(),
        400)
      // 99999 is past the journal's last sequence.
      assertEquals(server.get(
        s"/api/dev/first-games/woken/log/99999?playerId=$playerId")
        .statusCode(), 400)
      assertEquals(server.get(
        s"/api/dev/first-games/woken/log/x?playerId=$playerId").statusCode(),
        400)
      // The development events endpoint marks raw events as unsupported.
      val events = server.get("/api/dev/first-games/woken/events?limit=5")
      assertEquals(events.statusCode(), 200)
      assert(events.body().contains("\"warning\""))
    finally server.close()
