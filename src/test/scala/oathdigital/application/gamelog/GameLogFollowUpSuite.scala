package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

/** A queued action's Game Log (follow-up actions design, "Game Log and roll
  * feedback"). */
class GameLogFollowUpSuite extends munit.FunSuite:
  test("a queued Campaign is its own section, tagged with its source and " +
      "no Supply"):
    val entries = format(queuedRaid, None)
    val tag = entryStarting(entries, "Campaign")
    assertEquals(tag.kind, LogKind.Section)
    assertEquals(text(tag), "Campaign · Knights Errant")
    assertEquals(tag.section.map(_.action), Some(ActionRef.Campaign.key))

  test("a queued Campaign writes the battle lines a normal one writes"):
    val queued = texts(format(queuedRaid, None))
    val normal = texts(format(raid, None))
    assertEquals(queued.dropWhile(!_.startsWith("Campaign")).tail,
      normal.dropWhile(!_.startsWith("Campaign")).tail)

  test("taking the offer writes nothing before the Campaign"):
    val before = (script: Script) => texts(format(script, None))
      .takeWhile(!_.startsWith("Campaign"))
    assertEquals(before(queuedRaid), before(raid))

  test("Skip writes its line in the latest section"):
    val entries = format(queuedRaidThenSkip, None)
    val skip = entries.last
    assertEquals(text(skip), "Knights Errant: Campaign skipped.")
    assertEquals(skip.section, entryStarting(entries, "Campaign").section)
