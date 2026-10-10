package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.CatalogNames
import LogScripts._

/** Which entries share a band (log action sections design, "Server"): every
  * line of one top-level action carries that action's section; headlines,
  * Wake's Take Wealth, phase transitions, triggered procedures and
  * state-based changes carry none; a minor action is a section of its own. */
class GameLogSectionSuite extends munit.FunSuite:
  private def withAction(entries: Vector[LogEntry], action: String)
      : Vector[LogEntry] =
    entries.filter(_.section.exists(_.action == action))

  test("the lines of one action share one section, keyed by its first event"):
    val travel = withAction(format(round, None), "travel")
    // The tag (or start line) and "Travelled to".
    assertEquals(travel.size, 2, travel)
    assertEquals(travel.map(_.section).distinct.size, 1, travel)
    // Travel's opening line posts at its cost step, after the destination
    // pick; its section stays keyed by the run's first event, the start.
    assert(travel.head.section.exists(_.id < travel.head.sequence), travel)

  test("headlines, a quiet Wake and Rest's supply carry no section"):
    val entries = format(round, None)
    assert(entries.filter(_.depth == 0).forall(_.section.isEmpty), entries)
    assertEquals(entryStarting(entries, "Increased supply from ").section,
      None)
    assertEquals(entryStarting(entries, "Nothing happened in Wake").section,
      None)

  test("Wake's Take Wealth, the Oathkeeper and Setup carry no section"):
    // `woken` replays Setup: the pawn placements it posts are the subject.
    assertEquals(entryStarting(format(takeWealth, None), "Took 1 favor from ")
      .section, None)
    assertEquals(entryStarting(format(oathkeeperTie, None),
      "Oathkeeper passed to ").section, None)
    val placed = format(woken, None).filter(entry =>
      text(entry).contains(" placed pawn at "))
    assert(placed.nonEmpty)
    assert(placed.forall(_.section.isEmpty), placed)

  test("a minor action is a section of its own, apart from the action " +
      "around it"):
    val entries = format(revealRelic, None)
    val revealed = entryStarting(entries, "Revealed ")
    assertEquals(revealed.section.map(_.action), Some("reveal-owned-relic"))
    val recovered = entryStarting(entries, "Recovered ")
    assertEquals(recovered.section.map(_.action), Some("recover"))

  test("refilled bandits and a new Usurper are in no section"):
    val script = recordedEventsBoard
    val site = CatalogNames.site("Ancient City")
    assertEquals(formatAppended(script, Vector(OathEvent.BanditsRefilled(
      Vector(site -> 1)))).last.section, None)
    assertEquals(formatAppended(script, Vector(OathEvent.UsurperFlipped(
      script.otherSeat))).last.section, None)

  test("a power used as an action is one section named by its power"):
    val all = format(silverTongue, None).filter(_.depth == 1)
    // The power was the last REST power, so Rest finishes and the next
    // player's Wake follows it, in no section.
    val lines = all.takeWhile(_.section.nonEmpty)
    assert(lines.nonEmpty)
    assert(lines.forall(_.section.exists(_.action.startsWith("use-power:"))),
      lines)
    assertEquals(lines.map(_.section).distinct.size, 1, lines)
    assert(all.drop(lines.size).forall(_.section.isEmpty), all)

  test("playing a facedown adviser is its own section"):
    val lines = format(facedownAdviser, None).filter(_.depth == 1)
    assert(lines.nonEmpty)
    assert(lines.forall(_.section.exists(_.action == "play-facedown-adviser")),
      lines)
