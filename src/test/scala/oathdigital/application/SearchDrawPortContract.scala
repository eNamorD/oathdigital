package oathdigital.application

import oathdigital.model.{OathViolation, Region, SearchSource}
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.*

/** What every `SearchDrawPort` promises (the trait's doc): a draw takes its
  * cards from the pile of the source it was asked for, in the pile's own
  * order, and a source the origin region cannot reach is refused. Only the
  * authoritative port exists in production; the one stub of this port, which
  * draws a card the pile does not hold, breaks the promise on purpose to test
  * that the service refuses it. */
trait SearchDrawPortContract extends munit.FunSuite:
  protected def port: SearchDrawPort

  // Ancient City lies in the Cradle.
  private val origin = Region.Cradle
  private val atAncientCity = Table.start.pawn(p1, "Ancient City")

  test("a world deck draw takes the top cards, top first"):
    val table = atAncientCity.worldDeckTop("Errand Boy", "Wrestlers",
      "Alchemist", "Homesteaders")
    assertEquals(
      accepted(port.prepare(table.ready, SearchSource.WorldDeck, origin)),
      table.ready.game.current.commonCards.worldDeck.take(3))

  test("a regional discard draw takes the pile's last-placed cards first"):
    val table = atAncientCity.discarded(origin, "Errand Boy", "Wrestlers",
      "Alchemist", "Homesteaders")
    assertEquals(
      accepted(port.prepare(table.ready,
        SearchSource.RegionalDiscard(origin), origin)),
      table.ready.game.current.commonCards.discard(origin).reverse.take(3))

  test("another region's discard is refused"):
    val table = atAncientCity.discarded(Region.Provinces, "Errand Boy")
    val source = SearchSource.RegionalDiscard(Region.Provinces)
    assertEquals(rejected(port.prepare(table.ready, source, origin)),
      OathViolation.SearchSourceUnavailable(source))

class AuthoritativeSearchDrawPortSuite extends SearchDrawPortContract:
  protected def port = SearchDrawPort.authoritative
