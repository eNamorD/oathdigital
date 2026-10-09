package oathdigital.application

import oathdigital.gameplay.powers.travel.NarrowPassSite
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection._
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** What each viewer is shown of consent (consent design, "Projection" and
  * "Testing", hidden information). */
class ConsentProjectionSuite extends munit.FunSuite:
  import ConsentFixture._
  private val projector = new GameProjector(catalog)
  private val askReason = "Narrow Pass: Cannot travel to other sites in " +
    "the region without ruler's consent."

  private def view(at: GameAccepted, viewer: PlayerId): GameProjection =
    projector.project(gameId, at.loaded, viewer)

  private def dunesRow(at: GameAccepted)(using munit.Location)
      : DecisionOptionProjection =
    present(view(at, p1).walkerDecision.flatMap(_.query).toVector
      .flatMap(_.offeredOptions).find(_.id == dunes.wireId), "the Dunes row")

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = startTravel(service)
    accepted(service.handle(gameId, started.nextSequence, requestDunes))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  test("a destination its ruler may be asked about is askable, with the " +
      "consent reason and whom to ask"):
    val (service, _) = ConsentFixture.service()
    val row = dunesRow(startTravel(service))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some(askReason))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Narrow Pass"))))
    assert(!row.choosable)

  test("after a refusal it is unavailable, with the ordinary reason"):
    val (service, _) = ConsentFixture.service()
    val row = dunesRow(answering(service, asking(service), ConsentChoice.No))
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some(
      "Narrow Pass: Cannot travel to other sites in the region."))
    assertEquals(row.consent, None)

  test("while a request waits, the requester sees the wait and an Undo " +
      "that withdraws, and no decision"):
    val (service, _) = ConsentFixture.service()
    val shown = view(asking(service), p1)
    assertEquals(shown.walkerDecision, None)
    assertEquals(shown.consentWaiting,
      Some(ConsentWaitingProjection(Vector("p2"))))
    assertEquals(shown.consentPrompt, None)
    assertEquals(shown.undo, Some(StepOfferProjection(
      "Withdraw your consent request")))
    assertEquals(shown.redo, None)

  test("the asked ruler sees the prompt; a third player and the public see " +
      "neither"):
    val (service, _) = ConsentFixture.service()
    val asked = asking(service)
    assertEquals(view(asked, p2).consentPrompt, Some(ConsentPromptProjection(
      "p1", Vector("Narrow Pass"), "Dunes")))
    assertEquals(view(asked, p2).consentWaiting, None)
    Vector(view(asked, p3), projector.projectPublic(gameId, asked.loaded))
      .foreach { shown =>
        assertEquals(shown.consentPrompt, None)
        assertEquals(shown.consentWaiting, None)
      }

  test("the Pass's ruler alone sees their preferences, one row per other " +
      "player"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    assertEquals(view(set, p2).consentPreferences, Vector(
      ConsentPreferencesProjection("site.narrow-pass.pass", "Narrow Pass",
        Vector(ConsentPreferenceRowProjection("p1", "always"),
          ConsentPreferenceRowProjection("p3", "case-by-case")))))
    assertEquals(view(set, p1).consentPreferences, Vector.empty)
    assertEquals(projector.projectPublic(gameId, set.loaded)
      .consentPreferences, Vector.empty)

  test("a site power carries its power id"):
    val (service, _) = ConsentFixture.service()
    val shown = projector.projectPublic(gameId, startTravel(service).loaded)
    assertEquals(shown.world.flatMap(_.sites)
      .find(_.siteId == NarrowPassSite.id.value).map(_.powers.map(_.id)),
      Some(Vector("site.narrow-pass.pass")))

  test("the requester's view cannot tell an automatic grant from a manual " +
      "one"):
    val (manualService, _) = ConsentFixture.service()
    val manual = answering(manualService, asking(manualService),
      ConsentChoice.Yes)
    val (autoService, _) = ConsentFixture.service()
    val set = accepted(autoService.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val started = startTravel(autoService, set.nextSequence)
    val automatic = accepted(autoService.handle(gameId, started.nextSequence,
      requestDunes))
    assertEquals(view(automatic, p1).copy(nextSequence = 0L),
      view(manual, p1).copy(nextSequence = 0L))
