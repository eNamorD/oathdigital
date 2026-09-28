package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.oathkeeper.OathkeeperOutcome._
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

class OathkeeperRulesSuite extends munit.FunSuite:
  /** Each of `rulers` rules one site with a warband: the first Dunes, the
    * second Fair Isle. `holder` holds the title on `side`. */
  private def outcome(rulers: Vector[PlayerId],
      holder: Option[PlayerId] = None, side: TitleSide = TitleSide.Oathkeeper) =
    val table = rulers.zip(Vector("Dunes", "Fair Isle")).foldLeft(
      Table.start.oathkeeper(holder, side)) { case (t, (ruler, site)) =>
        t.warbandsAt(site, ruler, 1) }
    OathkeeperRules.outcome(table.ready)

  test("a holder who still leads keeps the title, even when tied"):
    assertEquals(outcome(Vector(p1), holder = Some(p1)), NoChange)
    // Pins row order: a tied holder who leads must not be offered a choice.
    assertEquals(outcome(Vector(p1, p2), holder = Some(p1)), NoChange)

  test("a holder tied out of the lead chooses among the leaders, in seat order"):
    assertEquals(outcome(Vector(p2, p1), holder = Some(p3)),
      Choose(p3, Vector(p1, p2)))

  test("a single leader takes the title from a holder or from the bank"):
    assertEquals(outcome(Vector(p2), holder = Some(p1)), Transfer(Some(p2)))
    assertEquals(outcome(Vector(p2)), Transfer(Some(p2)))

  test("a holder with nobody leading returns the title to the bank"):
    assertEquals(outcome(Vector.empty, holder = Some(p1)), Transfer(None))

  test("with nobody holding it, no leader or a tie changes nothing"):
    assertEquals(outcome(Vector.empty), NoChange)
    assertEquals(outcome(Vector(p1, p2)), NoChange)

  test("the Usurper side is not consulted"):
    assertEquals(outcome(Vector(p2), holder = Some(p1),
      side = TitleSide.Usurper), Transfer(Some(p2)))
