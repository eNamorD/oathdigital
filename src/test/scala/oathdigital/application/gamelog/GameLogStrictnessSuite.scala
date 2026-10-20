package oathdigital.application.gamelog

import oathdigital.gameplay.walker.{DeltaPayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p2
import LogScripts._

/** The log re-applies a recorded batch to read the state around each of its
  * operations. A journal that loaded has had that batch applied already --
  * `history` and `load` share one `reconstruct` -- so a rejection here is a
  * programming error, and the formatting fails rather than narrating lines
  * from a state the operations never reached (log operation strictness
  * decision).
  *
  * Only a hand-built step can reach it, which is what this suite builds:
  * production has no path that formats a journal the engine did not accept.
  */
class GameLogStrictnessSuite extends munit.FunSuite:
  test("a recorded operation that does not apply fails the formatting"):
    val script = recordedEventsBoard
    // The board's relic lies at Broken Peaks, so there is none at Ancient
    // City for this peek to find.
    val peekWhereNoRelicLies = Peek(p2, CatalogNames.relic("Sticky Fire"),
      Location.Site(CatalogNames.site("Ancient City")))
    val failure = intercept[IllegalStateException](formatAppended(script,
      Vector[OathEvent](
        WalkerStepRecorded("batch",
          DeltaPayload(Vector(peekWhereNoRelicLies)), Vector.empty),
        WalkerCompleted(ActionRef.Travel))))
    assert(failure.getMessage.contains("does not apply"), failure.getMessage)
    assert(failure.getMessage.contains("missing-piece"), failure.getMessage)
