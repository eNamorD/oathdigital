package oathdigital.application

import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** The source decision through the service: Undo takes back the start, and
  * the answer draws, so nothing takes it back (search source design,
  * ruling 6). */
class SearchSourceServiceSuite extends munit.FunSuite:
  private val table = Table.start
    .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
    .banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  test("Undo takes back the start, leaving the table as it was"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val undone = accept(service.handle("g", started.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, table.state)

  test("the source answer draws, so it cannot be taken back"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val picked = accept(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickSearchSource(p1, SearchSource.WorldDeck)))
    assertEquals(service.handle("g", picked.nextSequence, GameCommand.Undo(p1)),
      Left(GameApplicationError.CommandRejected(OathViolation.NothingToUndo)))
