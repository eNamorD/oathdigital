package oathdigital.application.gamelog

import oathdigital.testkit.MenuPlay
import oathdigital.application.{GameApplicationService}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.{LogEntry, LogKind, LogSpan}
import oathdigital.testkit.{Situation, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts._

class GameLogHeadlineSuite extends munit.FunSuite:
  private def headlines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 0))

  /** The journal steps of stream `id`; fails with the error or the missing
    * stream. */
  private def historyOf(service: GameApplicationService, id: String) =
    present(accepted(service.history(id)), s"history of $id").steps

  test("setup opens the log and hands the first turn to Round 1"):
    // `woken` replays Setup: the headlines Setup opens with are the subject.
    val script = woken
    val entries = format(script, Some(script.actor))
    assertEquals(headlines(entries).take(4),
      Vector("Setup", "Round 1", s"${playerLabel(script.actor)}'s turn",
        "Wake"))
    // Setup's own lines sit between the Setup and Round 1 headlines.
    assertEquals(entries.filter(_.depth == 0).map(_.kind).take(4),
      Vector(LogKind.Round, LogKind.Round, LogKind.Turn, LogKind.Phase))
    assertEquals(entries.head.sequence, 0L)

  test("a journal that starts at a table opens with its round, turn and " +
      "phase"):
    val table = Table.start.turn(p1, Phase.Wake)
    val (service, repository) = table.service()
    table.situation(Situation.journaled(service, catalog, repository, "t"))
      .endingWake(p1)
    val entries = formatter.format(historyOf(service, "t"), Some(p1))
    // No River is in play, so p1's Wake has nothing to decide.
    assertEquals(texts(entries).take(5), Vector("Round 1",
      s"${playerLabel(p1)}'s turn", "Wake", "Nothing happened in Wake", "Act"))
    assertEquals(entries.take(3).map(entry => (entry.sequence, entry.ordinal)),
      Vector((0L, 0), (0L, 1), (0L, 2)))

  test("a table journal opens with its phase, and a prefix formats as a " +
      "prefix of the whole log"):
    val table = Table.start
    val (service, repository) = table.service()
    table.situation(Situation.journaled(service, catalog, repository, "t"))
      .after(MenuPlay.start(p1, PhaseTransitionRef.BeginRest))
    val steps = historyOf(service, "t")
    val whole = formatter.format(steps, Some(p1))
    assertEquals(texts(whole).take(3), Vector("Round 1",
      s"${playerLabel(p1)}'s turn", "Act"))
    (1 to steps.size).foreach { k =>
      val prefix = formatter.format(steps.take(k), Some(p1))
      assertEquals(prefix, whole.take(prefix.size), s"prefix of $k events")
    }

  test("a whole round posts one turn headline per seat and opens Round 2"):
    val script = round
    val all = headlines(format(script, Some(script.actor)))
    assertEquals(all.take(2), Vector("Round 1",
      s"${playerLabel(script.actor)}'s turn"))
    assertEquals(all.count(_.endsWith("'s turn")), script.players.size + 1)
    // Round 2's first Wake has nothing to decide, so it ends at once.
    assertEquals(all.takeRight(4),
      Vector("Round 2", s"${playerLabel(script.actor)}'s turn", "Wake", "Act"))

  test("each turn posts its Wake, Act and Rest headlines in order"):
    val script = round
    val entries = format(script, Some(script.actor))
    val all = headlines(entries)
    val turns = all.indices.filter(all(_).endsWith("'s turn"))
    // Every turn but Round 2's first is played through its Rest; that one
    // stops in Act, its Wake having had nothing to decide.
    turns.init.foreach { at =>
      assertEquals(all.slice(at + 1, at + 4), Vector("Wake", "Act", "Rest"),
        all.toString)
    }
    assertEquals(all.drop(turns.last + 1), Vector("Wake", "Act"))
    val phases = entries.filter(entry => entry.depth == 0 &&
      Set("Wake", "Act", "Rest")(text(entry)))
    assert(phases.forall(_.kind == LogKind.Phase), phases.toString)

  test("a Wake that did nothing says so before the Act headline"):
    val script = round
    val all = texts(format(script, Some(script.actor)))
    val acts = all.indices.filter(all(_) == "Act")
    assert(acts.nonEmpty, all)
    acts.foreach { at =>
      assertEquals(all.slice(at - 2, at),
        Vector("Wake", "Nothing happened in Wake"), all.toString)
    }

  test("a Wake that did something posts no Nothing line"):
    val script = takeWealth
    val all = texts(format(script, Some(script.actor)))
    val wake = all.indexOf("Wake",
      all.indexOf(s"${playerLabel(script.actor)}'s turn"))
    assert(wake >= 0, all)
    assertEquals(all.drop(wake + 1).take(2).map(_.takeWhile(_ != ' ')),
      Vector("Took", "Act"), all.toString)

  test("the turn headline names the player with a player span"):
    // Only the board's actor is read.
    val script = recordedEventsBoard
    val entries = format(script, None)
    val turn = entries.find(_.kind == LogKind.Turn).getOrElse(
      fail(s"no turn headline in ${texts(entries)}"))
    assertEquals(turn.spans, Vector(
      LogSpan.Player(script.actor.value, playerLabel(script.actor)),
      LogSpan.Text("'s turn")))

  test("entries are keyed by journal sequence, in key order, with ordinals from 0"):
    val entries = format(round, None)
    val keys = entries.map(entry => entry.sequence -> entry.ordinal)
    assertEquals(keys, keys.sorted)
    entries.groupBy(_.sequence).values.foreach { same =>
      assertEquals(same.map(_.ordinal), same.indices.toVector) }

  test("each victory posts one victory headline naming the winner"):
    // Only the board's actor and last state are read: the formatter reads a
    // victory from its event alone.
    val script = recordedEventsBoard
    val winner = script.actor
    val vision = VisionRules.Faith
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
      val last = formatAppended(script, Vector(event)).last
      assertEquals(last.kind, LogKind.Victory)
      assertEquals(last.depth, 0)
      assertEquals(text(last), playerLabel(winner) + rest)
    }

  test("the last round's end posts no round headline"):
    // Only the board's journal is read. A game has 8 rounds, so the 8th
    // round's end names no next round.
    val script = recordedEventsBoard
    val before = format(script, None)
    assertEquals(formatAppended(script, Vector(OathEvent.RoundEnded(8, None))),
      before)
