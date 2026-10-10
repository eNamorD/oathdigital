package oathdigital.application

import oathdigital.gameplay.CampaignConsentFixture
import oathdigital.gameplay.CampaignConsentFixture.{deepWoodsRef,
  onlyWoodsExtra, passRuledByP2}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.travel.NarrowPassSite
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentRequested}
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Asking for consent at a Campaign's targets through the service (consent
  * design, slice 2): a grant opens the target, and the campaigner goes on
  * choosing targets. */
class CampaignConsentServiceSuite extends munit.FunSuite:
  private val gameId = "campaign-consent"
  private val pass = NarrowPassSite.pass
  private val request = ConsentRequest(p1, CampaignIds.targets, deepWoodsRef,
    Vector(ConsentAsk(p2, Vector(pass))))
  private val startCampaign = GameCommand.StartWalker(ActionRef.Campaign,
    StartPayload(p1))
  private val requestWoods = GameCommand.RequestConsent(p1,
    CampaignIds.targets, deepWoodsRef)
  private val projector = new GameProjector(catalog)

  private def started(service: GameApplicationService, at: Long = 0L)(
      using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at, startCampaign))

  /** p1 starts a Campaign and asks about Deep Woods. */
  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    accepted(service.handle(gameId, started(service).nextSequence,
      requestWoods))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def woods(at: GameAccepted)(using munit.Location): DecisionOption =
    present(CampaignConsentFixture.targetOptions(at.state)
      .find(_.ref == deepWoodsRef), "Deep Woods among the targets")

  test("Yes opens Deep Woods and answers nothing: the campaigner goes on " +
      "choosing"):
    val (service, repository) = Table.from(passRuledByP2).service()
    val begun = started(service)
    val asked = accepted(service.handle(gameId, begun.nextSequence,
      requestWoods))
    assertEquals(asked.events, Vector(ConsentRequested(request)))
    val yes = answering(service, asked, ConsentChoice.Yes)
    assertEquals(yes.events, Vector(ConsentAnswered(p2, granted = true)))
    assertEquals(CampaignConsentFixture.parkedAt(yes.state),
      Some(CampaignIds.targets))
    assert(woods(yes).available)
    // Still p1's Campaign to undo: the grant opened no batch.
    assertEquals(yes.undo.map(step => (step.issuer, step.from)),
      begun.undo.map(step => (step.issuer, step.from)))
    val chosen = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(CampaignIds.targets,
        DecisionAnswer.ChooseManyAnswer(Vector(deepWoodsRef))))))
    assertEquals(CampaignConsentFixture.parkedAt(chosen.state),
      Some(CampaignIds.force))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((chosen.state, chosen.undo))))

  test("No leaves Deep Woods with the ordinary reason"):
    val (service, _) = Table.from(passRuledByP2).service()
    val no = answering(service, asking(service), ConsentChoice.No)
    assertEquals(no.events, Vector(ConsentAnswered(p2, granted = false)))
    woods(no) match
      case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
        assertEquals(note.key, "no-target")
      case other => fail(s"expected a plain unavailable target, got $other")

  test("Always Allow opens Deep Woods and sets the ruler's preference"):
    val (service, _) = Table.from(passRuledByP2).service()
    val always = answering(service, asking(service), ConsentChoice.Always)
    assertEquals(always.events, Vector(ConsentAnswered(p2, granted = true),
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always)))
    assert(woods(always).available)

  test("after a grant, the campaigner may undo the Campaign, and Deep " +
      "Woods is open when it starts again"):
    val (service, _) = Table.from(passRuledByP2).service()
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    val undone = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(CampaignConsentFixture.parkedAt(undone.state), None)
    assert(woods(started(service, undone.nextSequence)).available)

  test("the campaigner sees Deep Woods askable, with the consent reason " +
      "and whom to ask"):
    val (service, _) = Table.from(passRuledByP2).service()
    val begun = started(service)
    val row = present(projector.project(gameId, begun.loaded, p1)
      .walkerDecision.flatMap(_.query).toVector.flatMap(_.offeredOptions)
      .find(_.id == deepWoodsRef.wireId), "the Deep Woods row")
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Narrow Pass: Cannot target other sites " +
      "in the region without ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Narrow Pass"))))

  test("a ruler's Always opens Deep Woods at once, and the campaigner goes " +
      "on choosing"):
    val (service, _) = Table.from(passRuledByP2).service()
    val set = accepted(service.handle(gameId, 0L,
      GameCommand.SetConsentPreference(p2, pass, p1, ConsentSetting.Always)))
    val begun = started(service, set.nextSequence)
    val asked = accepted(service.handle(gameId, begun.nextSequence,
      requestWoods))
    assertEquals(asked.events, Vector(ConsentRequested(request),
      ConsentAnswered(p2, granted = true)))
    assertEquals(CampaignConsentFixture.parkedAt(asked.state),
      Some(CampaignIds.targets))
    assert(woods(asked).available)

  test("when Deep Woods is the only extra, a grant opens it and the " +
      "Campaign takes it"):
    val (service, _) = Table.from(onlyWoodsExtra).service()
    val begun = started(service)
    val asked = accepted(service.handle(gameId, begun.nextSequence,
      requestWoods))
    val yes = accepted(service.handle(gameId, asked.nextSequence,
      GameCommand.AnswerConsent(p3, ConsentChoice.Yes)))
    assert(woods(yes).available)
    val chosen = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(CampaignIds.targets,
        DecisionAnswer.ChooseManyAnswer(Vector(deepWoodsRef))))))
    assertEquals(CampaignConsentFixture.parkedAt(chosen.state),
      Some(CampaignIds.force))
