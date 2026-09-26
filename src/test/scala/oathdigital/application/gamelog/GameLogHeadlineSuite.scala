package oathdigital.application.gamelog

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
    assertEquals(entries.map(_.kind).take(3),
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
