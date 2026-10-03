package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.Look
import oathdigital.testkit.Table.{p1, p2}
import LogScripts._

class GameLogStartLineSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 1))

  test("Travel opens with its start line and cost, then says where it went"):
    val script = round
    val entries = format(script, Some(script.actor))
    val start = entryStarting(entries, "Started Travel")
    val travelled = entryStarting(entries, "Travelled to ")
    assert(start.sequence < travelled.sequence)
    val cost = start.spans.collect { case cost: LogSpan.Cost => cost }
    assertEquals(cost.size, 1)
    val spentAt = start.sequence.toInt
    def supplyAfter(index: Int) =
      Look(script.history.steps(index).after).supply(script.actor)
    assertEquals(cost.head.value,
      supplyAfter(spentAt - 1) - supplyAfter(spentAt))
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
    val script = oathkeeperTie
    val passed = format(script, None).filter(entry =>
      text(entry).startsWith("Oathkeeper passed to "))
    assertEquals(passed.size, 1)
    assertEquals(passed.head.kind, LogKind.Trigger)
    // The tie is between p1 and p2; the old holder, p3, picks one of them.
    val named = passed.head.spans.collect { case player: LogSpan.Player =>
      player.id }
    assertEquals(named.size, 1, passed.head.spans)
    assert(Set(p1.value, p2.value)(named.head), passed.head.spans)

  test("a start line without modifiers has no 'with' clause"):
    val start = lineStarting(texts(format(round, None)), "Started Travel")
    assert(!start.contains(" with "), start)

  test("a chosen modifier is named on the start line by its card"):
    val script = augury
    val start = lineStarting(texts(format(script, None)), "Started Search")
    assert(start.startsWith("Started Search with Augury"), start)
    // Search's printed cost; Augury, a free modifier, adds nothing.
    assert(start.endsWith("−2 Supply"), start)
