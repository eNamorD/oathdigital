package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogSetupSuite extends munit.FunSuite:
  private def setupLines(script: Script, viewer: Option[PlayerId])
      : Vector[LogEntry] = format(script, viewer)
    .takeWhile(entry => text(entry) != "Round 1").filter(_.depth == 1)

  test("every player places a pawn and keeps an adviser, in turn order"):
    val script = woken
    val lines = texts(setupLines(script, None))
    assertEquals(lines.count(_.contains(" placed pawn at ")),
      script.players.size, lines)
    assertEquals(lines.count(_.contains(" kept ")), script.players.size, lines)

  test("a kept adviser is named to its owner and read by its back by others"):
    val script = woken
    val mine = setupLines(script, Some(script.actor))
      .filter(entry => text(entry).contains(" kept "))
    val own = mine.find(_.spans.head == LogSpan.Player(script.actor.value,
      name(script.actor))).get
    assert(own.spans.exists(_.isInstanceOf[LogSpan.Card]), own.spans)
    mine.filterNot(_ == own).foreach { entry =>
      assert("^.+ kept a (Denizen|Vision)$".r.matches(text(entry)), text(entry))
    }
