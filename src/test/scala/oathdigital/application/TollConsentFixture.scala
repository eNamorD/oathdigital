package oathdigital.application

import oathdigital.gameplay.powers.travel.{TollRoads, TravelFixture}
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** A game for a toll's consent through the service: TravelFixture's board
  * with Toll Roads at the Provinces' plains, and p2 ruling it and the coast.
  * p1, active at the Cradle's plains with 1 favor, travels to the coast. */
object TollConsentFixture:
  import TravelFixture.{board, coast, on, provincePlains}

  val tolled: ReadyGame = on(board(supply = 7))(_
    .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
    .favor(p1, 1).warbandsAt(provincePlains, p2, 1).warbandsAt(coast, p2, 1))

  /** `tolled` with p1 holding no favor. */
  val broke: ReadyGame = on(tolled)(_.favor(p1, 0))

  /** p1 starts a Travel and picks the coast; the walk parks on Toll Roads'
    * step. */
  def atTheStep(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = ConsentFixture.startTravel(service)
    accepted(service.handle(ConsentFixture.gameId, started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, coast)))

  /** p1 asks to travel to the coast without paying. */
  val requestFree: GameCommand = GameCommand.RequestConsent(p1,
    TollRoads.decisionId, TollRoads.free(coast))
