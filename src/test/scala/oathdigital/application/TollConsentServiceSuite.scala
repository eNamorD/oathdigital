package oathdigital.application

import oathdigital.gameplay.powers.PayOrAsk
import oathdigital.gameplay.powers.travel.{TollRoads, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.ConsentAnswered
import oathdigital.protocol.projection._
import oathdigital.testkit.{Look, Outcomes}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Asking a toll's ruler to travel without paying, through the service
  * (consent design, "Toll and penalty steps", rulings 7 and 10 to 13). */
class TollConsentServiceSuite extends munit.FunSuite:
  import ConsentFixture.gameId
  import TollConsentFixture._
  private val projector = new GameProjector(catalog)
  private val coast = TravelFixture.coast
  private val free = TollRoads.free(coast)

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    accepted(service.handle(gameId, atTheStep(service).nextSequence,
      requestFree))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  /** The rows of the decision p1 is shown. */
  private def rows(at: GameAccepted): Vector[DecisionOptionProjection] =
    projector.project(gameId, at.loaded, p1).walkerDecision
      .flatMap(_.query).toVector.flatMap(_.offeredOptions)

  private def freeRow(at: GameAccepted)(using munit.Location)
      : DecisionOptionProjection =
    present(rows(at).find(_.id == free.wireId), "the free row")

  test("the free option is askable, with the toll's consent reason and its " +
      "ruler"):
    val (service, _) = ConsentFixture.service(tolled)
    val row = freeRow(atTheStep(service))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Toll Roads: Cannot travel to its " +
      "ruler's sites without giving favor or ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Toll Roads"))))

  test("the ruler's prompt names the free option by its label"):
    val (service, _) = ConsentFixture.service(tolled)
    assertEquals(projector.project(gameId, asking(service).loaded, p2)
      .consentPrompt, Some(ConsentPromptProjection("p1",
        Vector("Toll Roads"), "Travel without paying")))

  test("Yes travels without paying, as p1's own undoable step"):
    val (service, repository) = ConsentFixture.service(tolled)
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    assertEquals(yes.events.head, ConsentAnswered(p2, granted = true))
    val after = Look(Outcomes.ready(yes.state))
    assertEquals(after.player(p1).pawnSite, Some(coast))
    assertEquals(after.favor(p1), 1)
    assertEquals(yes.undo.map(_.issuer), Some(p1))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((yes.state, yes.undo))))

  test("No leaves only paying, with the ordinary reason"):
    val (service, _) = ConsentFixture.service(tolled)
    val no = answering(service, asking(service), ConsentChoice.No)
    assertEquals(no.events, Vector(ConsentAnswered(p2, granted = false)))
    val row = freeRow(no)
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some(
      "Toll Roads: Cannot travel to its ruler's sites without giving favor."))
    val paid = accepted(service.handle(gameId, no.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(TollRoads.decisionId,
        DecisionAnswer.ChooseOneAnswer(PayOrAsk.pay)))))
    assertEquals(Look(Outcomes.ready(paid.state)).favor(p1), 0)

  test("a traveller who cannot pay travels free on Yes"):
    val (service, _) = ConsentFixture.service(broke)
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    assertEquals(Look(Outcomes.ready(yes.state)).player(p1).pawnSite,
      Some(coast))

  test("after No, a traveller who cannot pay has only Undo, and then the " +
      "coast is shut for the turn"):
    val (service, _) = ConsentFixture.service(broke)
    val no = answering(service, asking(service), ConsentChoice.No)
    assert(rows(no).nonEmpty && rows(no).forall(!_.choosable), rows(no))
    assert(projector.project(gameId, no.loaded, p1).undo.nonEmpty)
    val undone = accepted(service.handle(gameId, no.nextSequence,
      GameCommand.Undo(p1)))
    val coastRow = present(rows(undone).find(_.id == coast.value),
      "the coast row")
    assertEquals(coastRow.state, "unavailable")
    assertEquals(coastRow.reason, Some("Needs 1 favor; you have 0"))
