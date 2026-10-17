package oathdigital.application

import oathdigital.application.gamelog.LogScripts.{formatter, texts}
import oathdigital.gameplay.powers.action.{Palanquin, PalanquinCard}
import oathdigital.gameplay.powers.travel.TravelFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Palanquin's toll asked of the target or the ruler, through the service
  * (catalog batch 8, slice 15, rulings 8, 13, 14 and 17). */
class PalanquinConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  private val projector = new GameProjector(catalog)
  private val coast = TravelFixture.coast
  private val coastRef = DecisionOptionRef.Site(coast)

  /** TollConsentFixture's tolled board, with p1 holding Palanquin and p3
    * beside p1 with 1 favor. */
  private val p3BesideP1UnderTheToll: ReadyGame =
    TravelFixture.on(TollConsentFixture.tolled)(_
      .adviser(p1, PalanquinCard.id).pawn(p3, TravelFixture.cradlePlains)
      .favor(p3, 1))

  /** p1 uses Palanquin and aims it at p3; the walk parks on the
    * destination. */
  private def atTheDestination(service: GameApplicationService)(
      using munit.Location): GameAccepted =
    val used = accepted(service.handle(gameId, 0L, MenuPlay.starting(p1,
      ActionRef.UsePower(Palanquin.id),
      Vector(DecisionOptionRef.Denizen(PalanquinCard.id)))))
    accepted(service.handle(gameId, used.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(Palanquin.targetDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(p3))))))

  private def asking(service: GameApplicationService,
      granters: Vector[PlayerId])(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, atTheDestination(service).nextSequence,
      GameCommand.RequestConsent(p1, Palanquin.destinationDecisionId,
        coastRef, granters)))

  private def answering(service: GameApplicationService, at: GameAccepted,
      granter: PlayerId, choice: ConsentChoice)(using munit.Location)
      : GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(granter, choice)))

  private def coastRow(at: GameAccepted)(using munit.Location)
      : DecisionOptionProjection =
    present(projector.project(gameId, at.loaded, p1).walkerDecision
      .flatMap(_.query).toVector.flatMap(_.offeredOptions)
      .find(_.id == coastRef.wireId), "the coast row")

  test("the tolled coast is askable of either player"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val row = coastRow(atTheDestination(service))
    assertEquals(row.state, "askable")
    assertEquals(row.consent, Some(ConsentAskProjection(Vector.empty,
      Vector.empty, Vector(
        ConsentAlternativeProjection("p3", Vector("Toll Roads"),
          paying = true),
        ConsentAlternativeProjection("p2", Vector("Toll Roads"),
          paying = false)))))

  test("under bandits the target alone is asked, as a payer"):
    val bandits = TravelFixture.on(TravelFixture.board())(_
      .adviser(p1, PalanquinCard.id).favor(p1, 1)
      .pawn(p3, TravelFixture.cradlePlains).favor(p3, 1)
      .denizen(CatalogNames.denizen("Toll Roads"),
        at = TravelFixture.provincePlains)
      .bandits(TravelFixture.provincePlains, 1).bandits(coast, 1))
    val (service, _) = ConsentFixture.service(bandits)
    assertEquals(coastRow(atTheDestination(service)).consent,
      Some(ConsentAskProjection(Vector.empty, Vector.empty, payers = Vector(
        ConsentAlternativeProjection("p3", Vector("Toll Roads"),
          paying = true)))))

  test("asking only the target prompts them to pay and travel"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val asked = asking(service, Vector(p3))
    val prompt = projector.project(gameId, asked.loaded, p3).consentPrompt
    assertEquals(prompt.map(_.paying), Some(true))
    assertEquals(prompt.map(_.powers), Some(Vector("Toll Roads")))
    assertEquals(projector.project(gameId, asked.loaded, p2).consentPrompt,
      None)

  test("the target's Yes sends them paying, as p1's undoable step"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val yes = answering(service, asking(service, Vector(p3)), p3,
      ConsentChoice.Yes)
    val after = Look(Outcomes.ready(yes.state))
    assertEquals(after.pawn(p3), coast)
    assertEquals(after.favor(p3), 0)
    assertEquals(yes.undo.map(_.issuer), Some(p1))

  test("asking both: the ruler's Yes settles it and closes the target's " +
      "prompt"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val yes = answering(service, asking(service, Vector.empty), p2,
      ConsentChoice.Yes)
    assertEquals(Look(Outcomes.ready(yes.state)).favor(p3), 1)
    assertEquals(projector.project(gameId, yes.loaded, p3).consentPrompt,
      None)

  test("under Ask both, one refusal leaves the other asked"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    val no = answering(service, asking(service, Vector.empty), p3,
      ConsentChoice.No)
    assert(projector.project(gameId, no.loaded, p2).consentPrompt.nonEmpty)
    assertEquals(projector.project(gameId, no.loaded, p1).consentWaiting,
      Some(ConsentWaitingProjection(Vector("p2"))))

  test("the log writes one line per kind of ask"):
    val (service, _) = ConsentFixture.service(p3BesideP1UnderTheToll)
    asking(service, Vector.empty)
    val lines = texts(formatter.format(present(accepted(service.history(
      gameId)), "a stored game").steps, Some(p1)))
    assert(lines.exists(line => line.startsWith(
      "Asked p3 for consent to pay Toll Roads and travel to ")), lines)
    assert(lines.exists(line => line.startsWith(
      "Asked p2 for consent to ignore Toll Roads and select ")), lines)
