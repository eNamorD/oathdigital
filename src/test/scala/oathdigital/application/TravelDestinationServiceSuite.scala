package oathdigital.application

import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.p1

/** The destination decision through the service: Undo in place of the old
  * Cancel (travel destination design, ruling 8). */
class TravelDestinationServiceSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  /** A site in p1's own region, which any Supply at the start reaches. */
  private def nearby(state: OathState): SiteId =
    val current = Outcomes.ready(state).game.current
    val here = current.players.find(_.player == p1).flatMap(_.pawnSite).get
    current.map.inPlay.find(site => site != here &&
      current.map.regionOf(site) == current.map.regionOf(here)).get

  test("Undo takes back the start, leaving the table as it was"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val undone = accept(service.handle("g", started.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, table.state)

  test("Undo after the pick returns to the destination decision"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accept(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, nearby(started.state))))
    val undone = accept(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, started.state)
    assert(undone.undo.nonEmpty, "the start can still be taken back")
