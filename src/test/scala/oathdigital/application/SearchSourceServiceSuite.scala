package oathdigital.application

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.powers.search.NewsFromAfar
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** The source decision through the service: Undo takes back the start, and
  * the answer draws, so nothing takes it back (search source design,
  * ruling 6). */
class SearchSourceServiceSuite extends munit.FunSuite:
  private val table = Table.start
    .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
    .banditsRefilled

  test("Undo takes back the start, leaving the table as it was"):
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val undone = accepted(service.handle("g", started.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, table.state)

  test("Undo takes back a pick, and then the start"):
    val withNews = table.adviser(p1, "News from Afar").favor(p1, 2)
    val (service, _) = withNews.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("search.modifiers",
        ModifierPlay.pick(NewsFromAfar.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withNews.state)

  test("the source answer draws, so it cannot be taken back"):
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startSearch(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickSearchSource(p1, SearchSource.WorldDeck)))
    assertEquals(service.handle("g", picked.nextSequence, GameCommand.Undo(p1)),
      Left(GameApplicationError.CommandRejected(OathViolation.NothingToUndo)))
