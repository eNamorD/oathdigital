package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.{CampaignConsentFixture, PassMap}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Which campaign targets the Narrow Pass makes askable (consent design,
  * ruling 2 and slice 2). The Pass's own campaign clause grants consent, so
  * an extra target only the Pass forbids may be asked of its ruler. */
class NarrowPassCampaignConsentSuite extends munit.FunSuite:
  import CampaignConsentFixture._
  private val passPower = NarrowPassSite.pass

  /** `ready` remembering `answer` for Deep Woods at `decisionId`, this
    * turn. */
  private def remembering(ready: ReadyGame, answer: ConsentAnswer,
      decisionId: String = CampaignIds.targets): ReadyGame =
    ready.copy(consent = ConsentState(
      answers = Map(ConsentKey(passPower, p1, decisionId, deepWoodsRef) ->
        answer.by(p2)),
      turn = Some(ConsentTurn.of(ready))))

  /** A target restriction nobody can waive, on Deep Woods too. */
  private object WoodsBarrier extends ContributingPower:
    val id: PowerId = PowerId("test.woods-barrier")
    val source: RuleSourceRef = RuleSourceRef.Site(PassMap.deepWoods)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(
        (_, ref) => Option.when(ref == deepWoodsRef)(
          OathViolation.CampaignUnavailable("the barrier")),
        (_, _) => Some(NarrowPassSitePower.noTarget(
          PowerSourceRef.Site(PassMap.deepWoods))))))

  private def woods(options: Vector[DecisionOption])(
      using munit.Location): DecisionOption =
    present(options.find(_.ref == deepWoodsRef), "Deep Woods among the targets")

  private def assertNotedBy(option: DecisionOption, noteKey: String)(
      using munit.Location): Unit = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      assertEquals(note.key, noteKey)
    case other => fail(s"expected an unavailable option noted $noteKey, got $other")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  private val woodsOnly =
    DecisionAnswer.ChooseManyAnswer(Vector(deepWoodsRef))

  test("a target only a player-ruled Pass forbids is askable of its ruler"):
    woods(targetOptions(started())) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(power, note,
          asks, _)) =>
        assertEquals(power, passPower)
        assertEquals(note, NarrowPassSitePower.noTargetConsent(
          PowerSourceRef.Site(NarrowPassSite.id)))
        assertEquals(asks, Vector(ConsentAsk(p2, Vector(passPower))))
      case other => fail(s"expected an askable target, got $other")

  test("a Pass the bandits hold forbids with its ordinary reason: bandits " +
      "never consent"):
    assertNotedBy(woods(targetOptions(started(passHeldByBandits))),
      "no-target")

  test("a Pass the campaigner rules forbids nothing"):
    val ruled = Table.from(passRuledByP2).warbandsAt(PassMap.pass, p1, 1)
      .ready
    assert(woods(targetOptions(started(ruled))).available)

  test("a block nobody can waive beside the Pass leaves the target " +
      "unavailable, not askable"):
    val extra = Vector(WoodsBarrier)
    woods(targetOptions(started(extra = extra), extra)) match
      case DecisionOption.Unavailable(_, _: OptionReason.Noted) => ()
      case other => fail(s"expected a plain unavailable target, got $other")

  test("a grant remembered for the targets opens Deep Woods, and the " +
      "Campaign takes it"):
    val state = started(remembering(passRuledByP2, ConsentAnswer.Granted))
    assert(woods(targetOptions(state)).available)
    val answered = accepted(rules().resolveWalker(state, p1,
      CampaignIds.targets, woodsOnly))
    assertEquals(parkedAt(answered.state), Some(CampaignIds.force))

  test("a grant for another decision opens nothing here: a grant is tied " +
      "to its decision"):
    val travelGrant = remembering(passRuledByP2, ConsentAnswer.Granted,
      TravelProcedure.destinationDecisionId)
    assert(askable(woods(targetOptions(started(travelGrant)))))

  test("a refusal remembered this turn leaves the ordinary reason, and " +
      "the target is refused"):
    val state = started(remembering(passRuledByP2, ConsentAnswer.Refused))
    assertNotedBy(woods(targetOptions(state)), "no-target")
    assert(rules().resolveWalker(state, p1, CampaignIds.targets, woodsOnly)
      .isLeft)

  test("a grant from another turn counts for nothing"):
    val stale = passRuledByP2.copy(consent = ConsentState(
      answers = Map(ConsentKey(passPower, p1, CampaignIds.targets,
        deepWoodsRef) -> ConsentAnswer.Granted.by(p2)),
      turn = Some(ConsentTurn(passRuledByP2.game.current.tracks.round, p2))))
    assert(askable(woods(targetOptions(started(stale)))))

  test("the asks of an askable target"):
    assertEquals(rules().consentAsks(started(), p1, CampaignIds.targets,
      deepWoodsRef), Right(Vector(ConsentAsk(p2, Vector(passPower)))))

  test("at a choose-one hooked by its own window the Pass's target clause " +
      "is askable: the search finds a grant would answer the decision " +
      "(consent slice 4)"):
    val passOnly = WalkerPowers(Vector(present(TravelSitePowers.all
      .collectFirst { case power: NarrowPassSitePower => power },
      "the Pass power")))
    val tree = Sequence(Vector[Operation](Decide(CampaignIds.targets, p1,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Site(deepWoodsRef),
        DecisionOption.Site(DecisionOptionRef.Site(PassMap.brokenPeaks)))),
      window = Some(PowerWindow.CampaignTargetSelection))))
    val pending = Outcomes.parked(ProcedureWalker.advance(passRuledByP2, tree,
      None, passOnly)).tree
    present(ProcedureWalker.parkedDecide(passRuledByP2, tree, pending,
        passOnly), "the choose-one").query match
      case one: DecisionQuery.ChooseOne =>
        assert(askable(woods(one.options)))
      case other => fail(s"expected a choose-one, got $other")

  test("a target the Pass alone forbids is askable when it is the only " +
      "extra, and no extras is still an answer"):
    val state = started(onlyWoodsExtra)
    assertEquals(parkedAt(state), Some(CampaignIds.targets))
    woods(targetOptions(state)) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(_, _, asks, _)) =>
        assertEquals(asks, Vector(ConsentAsk(p3, Vector(passPower))))
      case other => fail(s"expected an askable target, got $other")
    val none = accepted(rules().resolveWalker(state, p1, CampaignIds.targets,
      DecisionAnswer.ChooseManyAnswer(Vector.empty)))
    assertEquals(parkedAt(none.state), Some(CampaignIds.force))
    assert(rules().resolveWalker(state, p1, CampaignIds.targets, woodsOnly)
      .isLeft)
