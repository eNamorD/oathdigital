package oathdigital.application.gamelog

import oathdigital.application.{ConsentFixture, GameAccepted,
  GameApplicationService, GameCommand, ParkedServiceFixture}
import oathdigital.gameplay.powers.poweruse.CityWall
import oathdigital.gameplay.powers.travel.TravelFixture
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.{formatter, texts}

/** The Game Log of City Wall's ruler deciding on an enemy's Tyrant
  * (automatic power consent design, "Game Log"), as p1 reads it. */
class GameLogAutomaticUseSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val coast = TravelFixture.coast

  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant")
      .denizen(CatalogNames.denizen("City Wall"), at = coast)
      .warbandsAt(coast, p2, 2))

  private def travelling(service: GameApplicationService, sequence: Long,
      site: SiteId)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1, site).picked

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def p1sLog(service: GameApplicationService)(
      using munit.Location): Vector[String] =
    texts(formatter.format(present(accepted(service.history(gameId)),
      "a stored game").steps, Some(p1)))

  /** p1's log once p1 travelled to the walled coast and p2 answered. */
  private def travelledAndAnswered(choice: ConsentChoice)(
      using munit.Location): Vector[String] =
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    answering(service, travelling(service, 0L, coast), choice)
    p1sLog(service)

  /** p1's log once p2 deactivated Tyrant at the coast and p1 travelled
    * away and back. */
  private def deactivatedThenTravelledBack()(using munit.Location)
      : Vector[String] =
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val no = answering(service, travelling(service, 0L, coast),
      ConsentChoice.No)
    val away = travelling(service, no.nextSequence,
      TravelFixture.cradlePlains)
    travelling(service, away.nextSequence, coast)
    p1sLog(service)

  test("the request and a deactivation read as the ruler's decision"):
    val lines = travelledAndAnswered(ConsentChoice.No)
    assert(lines.contains("City Wall: p2 decides whether Tyrant works."),
      lines)
    assert(lines.contains("p2 deactivated Tyrant with City Wall."), lines)
    assert(!lines.exists(_.startsWith("Chose")), lines)

  test("a grant reads as the ruler letting Tyrant work, then Tyrant's own " +
      "line"):
    val lines = travelledAndAnswered(ConsentChoice.Yes)
    val let = lines.indexOf("p2 let Tyrant work.")
    assert(let >= 0, lines)
    assert(lines.drop(let).exists(_.startsWith("Tyrant: Killed 1 p2 " +
      "warband at ")), lines)

  test("a deactivation remembered this turn says so when it applies"):
    val lines = deactivatedThenTravelledBack()
    assert(lines.contains("City Wall: Tyrant stays deactivated."), lines)
    assertEquals(lines.count(_ == "City Wall: p2 decides whether Tyrant " +
      "works."), 1)

  test("a preference's automatic answer reads as a manual one"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val set = accepted(service.handle(gameId, 0L,
      GameCommand.SetConsentPreference(p2, CityWall.id, p1,
        ConsentSetting.Never)))
    travelling(service, set.nextSequence, coast)
    val lines = p1sLog(service)
    assert(lines.contains("City Wall: p2 decides whether Tyrant works."),
      lines)
    assert(lines.contains("p2 deactivated Tyrant with City Wall."), lines)
