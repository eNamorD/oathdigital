package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.{LogEntry, LogSpan}
import oathdigital.testkit.Outcomes
import LogScripts._

class GameLogSetupSuite extends munit.FunSuite:
  private def setupLines(script: Script, viewer: Option[PlayerId])
      : Vector[LogEntry] = format(script, viewer)
    .takeWhile(entry => text(entry) != "Round 1").filter(_.depth == 1)

  test("every player places a pawn and keeps an adviser, in turn order"):
    // `woken` replays Setup: its placements and keeps are the subject.
    val script = woken
    val lines = texts(setupLines(script, None))
    assertEquals(lines.count(_.contains(" placed pawn at ")),
      script.players.size, lines)
    assertEquals(lines.count(_.contains(" kept ")), script.players.size, lines)
    // Turn order runs from the first player through the seats.
    val ready = Outcomes.ready(script.history.steps.last.after)
    val seats = ready.game.current.players.map(_.player)
    val first = seats.indexOf(ready.setup.firstPlayer)
    val order = (seats.drop(first) ++ seats.take(first)).map(_.value)
    assertEquals(lines.filter(_.contains(" placed pawn at "))
      .map(_.takeWhile(_ != ' ')), order)
    assertEquals(lines.filter(_.contains(" kept ")).map(_.takeWhile(_ != ' ')),
      order)

  test("a kept adviser is named to its owner and read by its back by others"):
    // `woken` replays Setup: its keep lines are the subject.
    val script = woken
    val mine = setupLines(script, Some(script.actor))
      .filter(entry => text(entry).contains(" kept "))
    val own = mine.find(_.spans.head == LogSpan.Player(script.actor.value,
      playerLabel(script.actor))).getOrElse(
        fail(s"no kept line for ${script.actor} in ${texts(mine)}"))
    assert(own.spans.exists(_.isInstanceOf[LogSpan.Card]), own.spans)
    mine.filterNot(_ == own).foreach { entry =>
      assert("^.+ kept a (Denizen|Vision)$".r.matches(text(entry)), text(entry))
    }

  test("a card revealed in Reveal Cards is named to every viewer"):
    // Replays Setup, answering Reveal Cards by revealing all: the reveal
    // lines are the subject.
    val script = wokenRevealingAll
    val ready = Outcomes.ready(script.history.steps.last.after)
    val revealed = ready.game.current.players.map(player =>
      player.advisers.count(_ match
        case d: DenizenState => d.orientation == Orientation.FaceUp
        case _ => false)).sum
    assert(revealed > 0)
    val public = texts(setupLines(script, None)).filter(_.contains(" revealed "))
    assertEquals(public.size, revealed, public)
    public.foreach(line =>
      assert(!"^.+ revealed a (Denizen|Vision)$".r.matches(line), line))
    script.players.foreach { viewer =>
      assertEquals(texts(setupLines(script, Some(viewer)))
        .filter(_.contains(" revealed ")), public, viewer)
    }
