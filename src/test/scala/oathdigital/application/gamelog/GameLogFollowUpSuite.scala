package oathdigital.application.gamelog

import oathdigital.gameplay.walker.FollowUpSkipped
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p1
import LogScripts._

/** A queued action's Game Log (follow-up actions design, "Game Log and roll
  * feedback"). */
class GameLogFollowUpSuite extends munit.FunSuite:
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))

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

  test("Skip writes its line in the latest section"):
    val script = queuedRaid
    val entries = formatAppended(script, Vector(FollowUpSkipped(
      QueuedProcedure(p1, ActionRef.Campaign, Vector.empty, Some(knights),
      QueueKind.Offer))))
    val skip = entries.last
    assertEquals(text(skip), "Knights Errant: Campaign skipped.")
    assertEquals(skip.section, entryStarting(entries, "Campaign").section)
