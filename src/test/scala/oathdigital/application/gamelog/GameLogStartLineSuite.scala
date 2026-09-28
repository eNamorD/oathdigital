package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogStartLineSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 1))

  private def supply(script: Script, player: PlayerId, index: Int): Int =
    script.history.steps(index).after match
      case OathState.Ready(ready) => ready.game.current.players
        .find(_.player == player).get.board.supply.supply
      case _ => fail("expected a ready game")

  test("Travel opens with its start line and cost, then says where it went"):
    val script = round
    val entries = format(script, Some(script.actor))
    val start = entries.find(entry => text(entry).startsWith("Started Travel")).get
    val travelled = entries.find(entry =>
      text(entry).startsWith("Travelled to ")).get
    assert(start.sequence < travelled.sequence)
    val cost = start.spans.collect { case cost: LogSpan.Cost => cost }
    assertEquals(cost.size, 1)
    val spentAt = start.sequence.toInt
    assertEquals(cost.head.value,
      supply(script, script.actor, spentAt - 1) -
        supply(script, script.actor, spentAt))
    assertEquals(cost.head.unit, "Supply")
    val destination = travelled.spans.collect { case site: LogSpan.Site => site }
    assertEquals(destination.size, 1)
    assertEquals(start.kind, LogKind.Action)

  test("the first player's Finish Rest reports the supply it restored"):
    val script = round
    val restored = lines(format(script, None))
      .filter(_.startsWith("Increased supply from "))
    assert(restored.nonEmpty, lines(format(script, None)))
    restored.foreach { line =>
      val numbers = "\\d+".r.findAllIn(line).map(_.toInt).toVector
      assertEquals(numbers.size, 2, line)
      assert(numbers(0) < numbers(1), line) }

  test("End Wake and Begin Rest post only their phase headlines"):
    val entries = format(round, None)
    val lines = texts(entries.filter(_.depth == 1))
      .filterNot(_ == "Nothing happened in Wake")
    assert(!lines.exists(line => line.contains("Wake") || line.contains("Rest")),
      lines)
    assert(entries.exists(entry => entry.kind == LogKind.Phase &&
      text(entry) == "Rest"), texts(entries))

  test("an Oathkeeper change names the new holder"):
    val script = oathkeeper
    val passed = format(script, None).filter(entry =>
      text(entry).startsWith("Oathkeeper passed to "))
    assertEquals(passed.size, 1)
    assertEquals(passed.head.kind, LogKind.Trigger)
    assert(passed.head.spans.exists(_.isInstanceOf[LogSpan.Player]),
      passed.head.spans)

  test("a start line without modifiers has no 'with' clause"):
    val start = texts(format(round, None)).find(_.startsWith("Started Travel")).get
    assert(!start.contains(" with "), start)

  test("a chosen modifier is named on the start line by its card"):
    val script = augury
    val start = texts(format(script, None)).find(_.startsWith("Started Search"))
      .get
    assert(start.startsWith("Started Search with Augury"), start)
    assert(start.endsWith("−2 Supply"), start)
