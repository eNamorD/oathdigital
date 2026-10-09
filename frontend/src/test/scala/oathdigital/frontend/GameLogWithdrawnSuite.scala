package oathdigital.frontend

import oathdigital.protocol.projection.{LogSectionWire, LogTakenBackWire}

/** A withdrawn consent request in the log: struck through in place, with
  * no group header (consent design, "Game Log"). */
class GameLogWithdrawnSuite extends munit.FunSuite:
  test("a withdrawn request's line stays in place, struck through, with no " +
      "toggle"):
    val section = Some(LogSectionWire(5L, "travel"))
    val started = TestLog.textEntry(5, "action", depth = 1, "Started Travel",
      section = section)
    val asked = TestLog.textEntry(7, "decision", depth = 1,
      "Asked Red for consent to ignore Narrow Pass and select Deep Woods.",
      section = section, takenBack = Some(LogTakenBackWire(7L, Vector.empty)))
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|blue", Vector(started, asked),
      Map("red" -> "player-red"))
    val line = content.querySelector(".log-withdrawn")
    assert(line != null)
    assertEquals(line.textContent,
      "Asked Red for consent to ignore Narrow Pass and select Deep Woods.")
    assertEquals(content.querySelector(".log-taken-back-toggle"), null)
