package oathdigital.application

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** A game for consent through the service: TravelFixture's board with the
  * Narrow Pass ruled by p2. p1 is active at the Cradle's plains, and the
  * Pass blocks Dunes, the Provinces' plains, from there. */
object ConsentFixture:
  import TravelFixture.{board, on, pass, provincePlains}

  val passRuledByP2: ReadyGame = on(board())(_.warbandsAt(pass, p2, 1))
  val dunes: DecisionOptionRef.Site = DecisionOptionRef.Site(provincePlains)
  val gameId = "consent"

  def service(at: ReadyGame = passRuledByP2)(using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    Table.from(at).service()

  /** p1 starts a Travel; the walk parks on the destination. */
  def startTravel(service: GameApplicationService, at: Long = 0L)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at, ParkedServiceFixture.startTravel(p1)))

  def setPreference(setting: ConsentSetting, granter: PlayerId = p2,
      requester: PlayerId = p1): GameCommand =
    GameCommand.SetConsentPreference(granter, NarrowPassSite.pass, requester,
      setting)

  /** p1 asks for consent to travel to Dunes. */
  val requestDunes: GameCommand = GameCommand.RequestConsent(p1,
    TravelProcedure.destinationDecisionId, dunes)

  def consent(accepted: GameAccepted)(using munit.Location): ConsentState =
    Outcomes.ready(accepted.state).consent
