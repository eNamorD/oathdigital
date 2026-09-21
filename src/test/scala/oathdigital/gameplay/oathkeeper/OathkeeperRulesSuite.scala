package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.oathkeeper.OathkeeperFixture._
import oathdigital.gameplay.oathkeeper.OathkeeperOutcome._
import oathdigital.model._

class OathkeeperRulesSuite extends munit.FunSuite {
  private val p = players
  private def outcome(owners: Vector[Option[PlayerId]],
      holder: Option[PlayerId] = None, side: TitleSide = TitleSide.Oathkeeper) =
    OathkeeperRules.outcome(ruled(base, owners, holder, side))

  test("a holder who still leads keeps the title, even when tied") {
    assertEquals(outcome(Vector(Some(p(0))), holder = Some(p(0))), NoChange)
    // Pins row order: a tied holder who leads must not be offered a choice.
    assertEquals(outcome(Vector(Some(p(0)), Some(p(1))), holder = Some(p(0))),
      NoChange)
  }

  test("a holder tied out of the lead chooses among the leaders, in seat order") {
    assertEquals(outcome(Vector(Some(p(1)), Some(p(0))), holder = Some(p(2))),
      Choose(p(2), Vector(p(0), p(1))))
  }

  test("a single leader takes the title from a holder or from the bank") {
    assertEquals(outcome(Vector(Some(p(1))), holder = Some(p(0))),
      Transfer(Some(p(1))))
    assertEquals(outcome(Vector(Some(p(1)))), Transfer(Some(p(1))))
  }

  test("a holder with nobody leading returns the title to the bank") {
    assertEquals(outcome(Vector.empty, holder = Some(p(0))), Transfer(None))
  }

  test("with nobody holding it, no leader or a tie changes nothing") {
    assertEquals(outcome(Vector.empty), NoChange)
    assertEquals(outcome(Vector(Some(p(0)), Some(p(1)))), NoChange)
  }

  test("the Usurper side is not consulted") {
    assertEquals(outcome(Vector(Some(p(1))), holder = Some(p(0)),
      side = TitleSide.Usurper), Transfer(Some(p(1))))
  }
}
