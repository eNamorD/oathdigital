package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, raidAnswers, texts}

/** How Keep reads in the Game Log: its line says where the defender's
  * pawn ended up, and the closing line neither sends the pawn there nor
  * reports a burn or a discard. */
class KeepLogSuite extends munit.FunSuite:
  private val keep = CatalogNames.denizen("Keep")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")

  /** p1 raids p2 at Ancient City and wins. p2 holds a facedown Birdsong,
    * and Keep stands at `keepSite`, where p2 holds 2 warbands. The log as
    * p3 sees it. */
  private def raidOnKeepsRuler(keepSite: SiteId): Vector[String] =
    val table = Table.start.pawn(p1, ancientCity).pawn(p2, ancientCity)
      .pawn(p3, buriedGiant).adviser(p2, "Birdsong", facedown = true)
      .denizen(keep, at = keepSite).warbandsAt(keepSite, p2, 2)
      .banditsRefilled
    val (service, act) = atTable("keep", table, Dice.raid)
    raidAnswers(act).after(MenuPlay.start(p1, ActionRef.Campaign))
    texts(format(Script("keep", service, p1), Some(p3)))

  test("its line names where the pawn went, and the closing line neither " +
      "sends it there nor discards"):
    val log = raidOnKeepsRuler(brokenPeaks)
    assert(log.contains(s"Keep: Ended the Raid with ${playerLabel(p2)}'s " +
      "pawn at Broken Peaks."), log.mkString("\n"))
    assert(!log.exists(_.contains(" was sent to ")), log.mkString("\n"))
    assert(!log.exists(_.contains("discarded")), log.mkString("\n"))

  test("with Keep at the Raid's own site, its line says the pawn is " +
      "there"):
    val log = raidOnKeepsRuler(ancientCity)
    assert(log.contains(s"Keep: Ended the Raid with ${playerLabel(p2)}'s " +
      "pawn at Ancient City."), log.mkString("\n"))
    assert(!log.exists(_.contains(" was sent to ")), log.mkString("\n"))
