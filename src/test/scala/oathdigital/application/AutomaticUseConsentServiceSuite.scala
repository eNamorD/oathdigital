package oathdigital.application

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.PowerUseStep
import oathdigital.gameplay.powers.poweruse.CityWall
import oathdigital.gameplay.powers.travel.{TravelFixture, Tyrant, TyrantCard}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** City Wall's ruler decides whether an enemy's Tyrant works, through the
  * service (automatic power consent design, user story and rulings 2, 5
  * and 6). */
class AutomaticUseConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val wall = CatalogNames.denizen("City Wall")
  private val coast = TravelFixture.coast

  /** The key Tyrant's use by p1 is remembered under, against City Wall. */
  private val tyrantKey = ConsentKey(CityWall.id, p1, PowerUseRules.useKey,
    DecisionOptionRef.Denizen(TyrantCard.id))

  /** p1 holds Tyrant faceup at the Cradle's plains; City Wall stands at
    * the coast, which p2 rules with 2 warbands. */
  private val tyrantBesideAWalledCoast: ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(_
      .adviser(p1, "Tyrant").denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))

  /** p1 travels to `site` at `sequence`; returns the pick's batch. */
  private def travelling(service: GameApplicationService, sequence: Long,
      site: SiteId)(using munit.Location): GameAccepted =
    ParkedServiceFixture.travelTo(service, gameId, sequence, p1, site).picked

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice, granter: PlayerId = p2)(using munit.Location)
      : GameAccepted = accepted(service.handle(gameId, at.nextSequence,
    GameCommand.AnswerConsent(granter, choice)))

  private def readyAfter(at: GameAccepted)(using munit.Location)
      : ReadyGame = Outcomes.ready(at.state)

  private def coastWarbands(at: GameAccepted)(using munit.Location): Int =
    Look(readyAfter(at)).forces(coast) match
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0

  private def remembered(at: GameAccepted)(using munit.Location)
      : Option[ConsentAnswer] = readyAfter(at).consent.remembered(tyrantKey,
    ConsentTurn.of(readyAfter(at)))

  test("a Travel to City Wall's site asks its ruler whether Tyrant works, " +
      "and asks the traveller nothing"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    assertEquals(readyAfter(waiting).consent.pending, Some(ConsentRequest(
      p1, PowerUseStep.decisionId(Tyrant.id), PowerUseStep.use,
      Vector(ConsentAsk(p2, Vector(CityWall.id))), Vector(tyrantKey),
      use = Some(ConsentUse(TyrantCard.id, p1)))))
    assertEquals(Look(readyAfter(waiting)).pawn(p1), coast)
    assertEquals(coastWarbands(waiting), 2)

  test("Deactivate kills nothing, and the refusal is remembered"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val no = answering(service, travelling(service, 0L, coast),
      ConsentChoice.No)
    assertEquals(readyAfter(no).consent.pending, None)
    assertEquals(coastWarbands(no), 2)
    assertEquals(remembered(no), Some(ConsentAnswer.Refused))
    assert(!readyAfter(no).game.current.walkerProcedure
      .contains(ActionRef.Travel))

  test("Let it work kills one warband, and the grant is remembered"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val yes = answering(service, travelling(service, 0L, coast),
      ConsentChoice.Yes)
    assertEquals(coastWarbands(yes), 1)
    assertEquals(remembered(yes), Some(ConsentAnswer.Granted))

  test("Always deactivate and Always let it work also set the ruler's " +
      "preference for the traveller"):
    for (choice, setting, warbands) <- Vector(
        (ConsentChoice.Never, ConsentPreference.Never, 2),
        (ConsentChoice.Always, ConsentPreference.Always, 1)) do
      val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
      val answered = answering(service, travelling(service, 0L, coast),
        choice)
      assertEquals(readyAfter(answered).consent.preference(p2, CityWall.id,
        p1), Some(setting))
      assertEquals(coastWarbands(answered), warbands)

  test("a preference answers the request in the command that opens it"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val set = accepted(service.handle(gameId, 0L,
      GameCommand.SetConsentPreference(p2, CityWall.id, p1,
        ConsentSetting.Never)))
    val travelled = travelling(service, set.nextSequence, coast)
    assertEquals(travelled.events.collect {
      case OathEvent.ConsentRequested(request) => request.use
      case OathEvent.ConsentAnswered(granter, granted) => (granter, granted)
    }, Vector(Some(ConsentUse(TyrantCard.id, p1)), (p2, false)))
    assertEquals(readyAfter(travelled).consent.pending, None)
    assertEquals(coastWarbands(travelled), 2)

  test("the power's user cannot answer its step themselves"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val waiting = travelling(service, 0L, coast)
    val withdrawn = accepted(service.handle(gameId, waiting.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(service.handle(gameId, withdrawn.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(
        PowerUseStep.decisionId(Tyrant.id), DecisionAnswer.ChooseOneAnswer(
          PowerUseStep.skip)))), Left(GameApplicationError.CommandRejected(
            OathViolation.ConsentUnavailable("the refusing cards' rulers " +
              "decide whether this power works"))))

  test("a deactivation remembered this turn applies to a later Travel " +
      "there without asking"):
    val (service, _) = ConsentFixture.service(tyrantBesideAWalledCoast)
    val no = answering(service, travelling(service, 0L, coast),
      ConsentChoice.No)
    val away = travelling(service, no.nextSequence,
      TravelFixture.cradlePlains)
    val back = travelling(service, away.nextSequence, coast)
    assertEquals(readyAfter(back).consent.pending, None)
    assertEquals(coastWarbands(back), 2)

  test("a grant settling a Narrow Pass ask resumes into Tyrant's step, " +
      "which asks its own ruler"):
    // The Pass, ruled by p2, blocks Dunes; City Wall stands at Dunes,
    // which p3 rules.
    val dunes = TravelFixture.provincePlains
    val ready = TravelFixture.on(ConsentFixture.passRuledByP2)(_
      .adviser(p1, "Tyrant").denizen(wall, at = dunes)
      .warbandsAt(dunes, p3, 1))
    val (service, _) = ConsentFixture.service(ready)
    val started = ConsentFixture.startTravel(service)
    val asked = accepted(service.handle(gameId, started.nextSequence,
      ConsentFixture.requestDunes))
    val granted = answering(service, asked, ConsentChoice.Yes)
    val request = present(readyAfter(granted).consent.pending,
      "Tyrant's request")
    assertEquals(request.asks, Vector(ConsentAsk(p3, Vector(CityWall.id))))
    assertEquals(request.use, Some(ConsentUse(TyrantCard.id, p1)))
    assertEquals(Look(readyAfter(granted)).pawn(p1), dunes)
