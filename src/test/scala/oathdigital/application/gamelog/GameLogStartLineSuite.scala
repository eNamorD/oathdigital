package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.Look
import oathdigital.testkit.Table.{p1, p2}
import LogScripts._

class GameLogStartLineSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]): Vector[String] =
    texts(entries.filter(_.depth == 1))

  test("Travel opens with its tag and cost, then says where it went"):
    val script = round
    val entries = format(script, Some(script.actor))
    val tag = entries.find(_.kind == LogKind.Section).getOrElse(
      fail(s"no tag in ${texts(entries)}"))
    assertEquals(tag.spans.head, LogSpan.Text("Travel"))
    // A tag with no modifiers has no line of its own: the action's line
    // follows it.
    val travelled = entries(entries.indexOf(tag) + 1)
    assert(text(travelled).startsWith("Travelled to "), text(travelled))
    assertEquals(travelled.section, tag.section)
    val cost = tag.spans.collect { case cost: LogSpan.Cost => cost }
    assertEquals(cost.size, 1)
    val spentAt = tag.sequence.toInt
    def supplyAfter(index: Int) =
      Look(script.history.steps(index).after).supply(script.actor)
    assertEquals(cost.head.value,
      supplyAfter(spentAt - 1) - supplyAfter(spentAt))
    assertEquals(cost.head.unit, "Supply")
    val destination = travelled.spans.collect { case site: LogSpan.Site => site }
    assertEquals(destination.size, 1)

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

  test("a chosen modifier follows the tag as a line of its own"):
    val entries = format(augury, None)
    val tag = entryStarting(entries, "Search")
    assertEquals(tag.kind, LogKind.Section)
    // Search's printed cost; Augury, a free modifier, adds nothing.
    assertEquals(text(tag), "Search −2 Supply")
    val chosen = entries(entries.indexOf(tag) + 1)
    assertEquals(text(chosen), "With Augury")
    assertEquals(chosen.kind, LogKind.Action)
    assertEquals(chosen.section, tag.section)

  test("a Search that pays Forced Labor's toll opens with one tag above " +
      "the toll, carrying the Supply spent before it"):
    val log = texts(format(forcedLaborSearch, None))
    val tag = log.indexOf("Search −2 Supply")
    val toll = log.indexWhere(_.startsWith("Forced Labor: Gave 1 favor"))
    assert(tag >= 0 && toll > tag, log)
    assertEquals(log.count(_.startsWith("Search")), 1, log)

  test("a Campaign's kind and defender follow its tag"):
    val entries = format(raid, None)
    val tag = entryStarting(entries, "Campaign")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(text(tag), "Campaign −2 Supply")
    // The raid script's p1 raids p2.
    assertEquals(text(entries(entries.indexOf(tag) + 1)),
      s"Raid against ${playerLabel(p2)}")

  test("playing a facedown adviser costs nothing, so its tag is its name"):
    val tag = entryStarting(format(facedownAdviser, None), "Play facedown")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(tag.spans, Vector[LogSpan](LogSpan.Text("Play facedown adviser")))

  test("a Search with two picks names both once, in pick order, with no " +
      "Chose line"):
    val entries = format(twoPicks, None)
    val tag = entryStarting(entries, "Search")
    assertEquals(tag.kind, LogKind.Section)
    // News from Afar spends no Supply, so the tag carries no cost.
    assertEquals(text(tag), "Search")
    assertEquals(text(entries(entries.indexOf(tag) + 1)),
      "With News from Afar and Augury")
    assertEquals(texts(entries).count(_.startsWith("With ")), 1)
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))

  test("a facedown-adviser play with a pick is tagged at the adviser's " +
      "pick, naming it"):
    val entries = format(facedownWithCry, None)
    val tag = entryStarting(entries, "Play facedown")
    assertEquals(text(entries(entries.indexOf(tag) + 1)), "With Wild Cry")
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))

  test("a Travel with a pick names it on its own line, with no Chose line"):
    val entries = format(travelWithTents, None)
    val tag = entryStarting(entries, "Travel")
    assertEquals(tag.kind, LogKind.Section)
    // Tents makes the Travel free, so the tag carries no cost.
    assertEquals(text(tag), "Travel")
    assertEquals(text(entries(entries.indexOf(tag) + 1)), "With Tents")
    assert(text(entries(entries.indexOf(tag) + 2))
      .startsWith("Travelled to "), texts(entries))
    assert(!texts(entries).exists(_.startsWith("Chose")), texts(entries))
