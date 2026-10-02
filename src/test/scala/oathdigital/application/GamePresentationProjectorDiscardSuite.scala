package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** A discard pile's cards, top first, through the one visibility rule. */
class GamePresentationProjectorDiscardSuite extends munit.FunSuite:
  private val projector = new GamePresentationProjector(catalog)

  private def cradle(ready: ReadyGame, viewer: PlayerId) =
    projector.readyWorld(ready, Some(viewer)).find(_.regionId == "cradle")
      .getOrElse(fail("no cradle"))

  // Appended in this order, so Scryer is on top.
  private val table = Table.start.discarded(Region.Cradle, "Magician's Code", "Scryer")

  test("a discard pile projects its cards top first, as backs"):
    val region = cradle(table.ready, p2)
    assertEquals(region.discardCards.size, region.discardCount)
    assertEquals(region.discardCards.take(2).map(_.cardId), Vector("hidden", "hidden"))
    assertEquals(region.discardCards.take(2).map(_.name),
      Vector("Facedown denizen", "Facedown denizen"))
    assertEquals(region.discardTopCardKind, Some("denizen"))

  test("a peeked card shows its face to the peeker and a back to others"):
    val scryer = CatalogNames.worldCard("Scryer")
    val peeked = table.update(ready => ready.copy(knowledge = ready.knowledge.copy(
      advisers = ready.knowledge.advisers.updated(p1, Vector(scryer))))).ready
    assertEquals(cradle(peeked, p1).discardCards.take(2).map(_.name),
      Vector("Scryer", "Facedown denizen"))
    assertEquals(cradle(peeked, p1).discardCards.head.hidden, false)
    assertEquals(cradle(peeked, p2).discardCards.take(2).map(_.cardId),
      Vector("hidden", "hidden"))
