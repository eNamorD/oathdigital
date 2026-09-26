package oathdigital.application.gamelog

import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.model._
import LogScripts._

class GameLogHeadlineSuite extends munit.FunSuite:
  private def headlines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 0))

  test("setup opens the log and hands the first turn to Round 1"):
    val script = woken
    val entries = format(script, Some(script.actor))
    assertEquals(headlines(entries),
      Vector("Setup", "Round 1", s"${name(script.actor)}'s turn"))
    // Setup's own lines sit between the Setup and Round 1 headlines.
    assertEquals(entries.filter(_.depth == 0).map(_.kind).take(3),
      Vector(LogKind.Round, LogKind.Round, LogKind.Turn))
    assertEquals(entries.head.sequence, 0L)

  test("a whole round posts one turn headline per seat and opens Round 2"):
    val script = round
    val all = headlines(format(script, Some(script.actor)))
    assertEquals(all.take(2), Vector("Setup", "Round 1"))
    assertEquals(all.count(_.endsWith("'s turn")), script.players.size + 1)
    assertEquals(all.takeRight(2),
      Vector("Round 2", s"${name(script.actor)}'s turn"))

  test("the turn headline names the player with a player span"):
    val script = woken
    val turn = format(script, None).find(_.kind == LogKind.Turn).get
    assertEquals(turn.spans, Vector(
      LogSpan.Player(script.actor.value, name(script.actor)),
      LogSpan.Text("'s turn")))

  test("entries are keyed by journal sequence, in key order, with ordinals from 0"):
    val entries = format(round, None)
    val keys = entries.map(entry => entry.sequence -> entry.ordinal)
    assertEquals(keys, keys.sorted)
    entries.groupBy(_.sequence).values.foreach { same =>
      assertEquals(same.map(_.ordinal), same.indices.toVector) }

  /** `script`'s journal with `events` appended on its last state: the
    * formatter reads a victory from its event alone. */
  private def ending(script: Script, events: OathEvent*): Vector[LogEntry] =
    val steps = script.history.steps
    val last = steps.last.after
    formatter.format(steps ++ events.zipWithIndex.map { case (event, index) =>
      ReplayStep(RecordedEvent(steps.size.toLong + index, event), last, last)
    }, None)

  test("each victory posts one victory headline naming the winner"):
    val script = woken
    val winner = script.actor
    val vision = VisionId("vision:vision-of-faith")
    val headlines = Vector(
      OathEvent.UsurperVictory(winner) -> " won as the Usurper",
      OathEvent.VisionVictory(winner, vision) -> " won with Vision of Faith",
      OathEvent.WarExhaustionResolved(winner, VictoryKind.Usurper, None,
        Vector.empty) -> " won as the Usurper",
      OathEvent.WarExhaustionResolved(winner, VictoryKind.Visionary,
        Some(vision), Vector.empty) -> " won with Vision of Faith",
      OathEvent.WarExhaustionResolved(winner, VictoryKind.Visionary, None,
        Vector.empty) -> " won as a Visionary",
      OathEvent.WarExhaustionResolved(winner, VictoryKind.Oathkeeper, None,
        Vector.empty) -> " won as the Oathkeeper",
      OathEvent.WarExhaustionResolved(winner, VictoryKind.RandomSelection, None,
        Vector.empty) -> " won by random selection")
    headlines.foreach { case (event, rest) =>
      val last = ending(script, event).last
      assertEquals(last.kind, LogKind.Victory)
      assertEquals(last.depth, 0)
      assertEquals(text(last), name(winner) + rest)
    }

  test("the last round's end posts no round headline"):
    val script = woken
    val before = format(script, None)
    assertEquals(ending(script, OathEvent.RoundEnded(8, None)), before)
