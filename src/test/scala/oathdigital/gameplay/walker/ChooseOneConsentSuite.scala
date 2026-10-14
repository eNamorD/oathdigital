package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{OptionRestriction, Waiver}
import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome,
  PreviewedOption}
import oathdigital.model._
import oathdigital.testkit.PowerStubs.ContributingStub
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** An option a waivable `OptionRestriction` bars at a choose-one hooked by
  * its own window: askable when a grant would open it, judged with the
  * search where the walker narrows the parked decision (consent slice 4). */
class ChooseOneConsentSuite extends munit.FunSuite:
  private val ready = Table.start.supply(p1, 5).ready
  private val stepId = "test.step"
  private val pay = DecisionOptionRef.Button("pay")
  private val free = DecisionOptionRef.Button("free")
  private val payButton = DecisionOption.Button(pay, "Pay")
  private val freeButton = DecisionOption.Button(free, "Go free")
  private val source = PowerSourceRef.Site(SiteId("test-site"))
  /** This suite's own keys: a test declares what it writes,
    * as a power does. */
  private val testNotes = NoteSet.unowned("ChooseOneConsentSuite")
  private val plain = testNotes.key("plain", Vector(NotePart.Text("Pay first.")))
  private val consent = testNotes.key("consent",
    Vector(NotePart.Text("Pay first, unless its ruler consents.")))

  /** A power barring `free` at the step until `granter` consents. */
  private def barrier(granter: Option[PlayerId]): ContributingStub =
    ContributingStub(PowerId("test.toll"), Map(PowerWindow.PayOrAsk ->
      Vector(OptionRestriction(
        (_, ref) => Option.when(ref == free)(
          OathViolation.InvalidEventOrder("the toll is owed")),
        (_, ref) => Option.when(ref == free)(plain(source)),
        Some(Waiver(_ => granter, source, (_, _) => consent(source)))))))
  private val toll = barrier(Some(p2))
  private val key = ConsentKey(toll.id, p1, stepId, free)
  private val askable = OptionReason.Askable(toll.id, consent(source),
    Vector(ConsentAsk(p2, Vector(toll.id))))
  private val refused = ready.copy(consent = ConsentState(
    answers = Map(key -> ConsentAnswer.Refused),
    turn = Some(ConsentTurn.of(ready))))

  /** The step offering `payOption` and the free option, then `afterPay` or
    * `afterFree` by its answer. */
  private def step(payOption: DecisionOption = payButton,
      afterPay: Vector[Operation] = Vector.empty,
      afterFree: Vector[Operation] = Vector.empty): Operation =
    Sequence(Vector[Operation](
      Decide(stepId, p1, DecisionQuery.ChooseOne(Vector(payOption,
        freeButton)), window = Some(PowerWindow.PayOrAsk)),
      Branch((_, pending) => pending.answered.collectFirst {
        case Answered(`stepId`, DecisionAnswer.ChooseOneAnswer(ref), _) =>
          if ref == free then afterFree else afterPay
      }.getOrElse(Vector.empty))))

  private def parkedAt(at: ReadyGame, action: Operation,
      powers: WalkerPowers)(using munit.Location): PendingTree =
    Outcomes.parked(ProcedureWalker.advance(at, action, None, powers)).tree

  /** The free option as the parked step offers it. */
  private def freeOffered(at: ReadyGame, action: Operation = step(),
      powers: WalkerPowers = WalkerPowers(Vector(toll)))(
      using munit.Location): DecisionOption =
    ProcedureWalker.openDecisions(at, action, parkedAt(at, action, powers),
      powers).head.query match
      case DecisionQuery.ChooseOne(options, _, _) =>
        options.find(_.ref == free).getOrElse(fail("free is listed"))
      case other => fail(s"a choose-one, not $other")

  private def plainly: DecisionOption = DecisionOption.Unavailable(freeButton,
    OptionReason.Noted(toll.id, plain(source)))

  test("a barred option a grant would open is askable, asking the " +
      "barrier's ruler"):
    assertEquals(freeOffered(ready),
      DecisionOption.Unavailable(freeButton, askable))

  test("a barred option a grant would not open keeps its ordinary reason"):
    // Going free costs 99 Supply, which p1 does not have.
    assertEquals(freeOffered(ready, step(afterFree =
      Vector(SpendSupply(p1, 99)))), plainly)

  test("a barrier nobody may waive keeps its ordinary reason"):
    val rulerless = barrier(None)
    assertEquals(freeOffered(ready, powers = WalkerPowers(Vector(rulerless))),
      DecisionOption.Unavailable(freeButton,
        OptionReason.Noted(rulerless.id, plain(source))))

  test("a remembered refusal keeps its ordinary reason"):
    assertEquals(freeOffered(refused), plainly)

  test("a remembered grant opens the option, and the walk takes it"):
    val granted = ready.copy(consent = ConsentState()
      .granting(Vector(key), ConsentTurn.of(ready)))
    val powers = WalkerPowers(Vector(toll))
    assertEquals(freeOffered(granted), freeButton)
    accepted(ProcedureWalker.resolve(granted, step(),
      parkedAt(granted, step(), powers),
      Answered(stepId, DecisionAnswer.ChooseOneAnswer(free), p1), powers))

  private val unpayable = DecisionOption.Unavailable(payButton,
    OptionReason.Written("Needs more"))
  private val go = DecisionOptionRef.Button("go")

  /** A first decision whose one answer leads on to `next`. */
  private def behind(next: Operation): Operation = Sequence(Vector[Operation](
    Decide("go", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(go, "Go")))), next))

  test("a step its payer cannot pay is still reached, to ask"):
    val powers = WalkerPowers(Vector(toll))
    val action = behind(step(unpayable))
    val stepped = Outcomes.parked(ProcedureWalker.resolve(ready, action,
      parkedAt(ready, action, powers),
      Answered("go", DecisionAnswer.ChooseOneAnswer(go), p1), powers)).tree
    assertEquals(ProcedureWalker.openDecisions(ready, action, stepped,
      powers).head.query, DecisionQuery.ChooseOne(Vector(unpayable,
        DecisionOption.Unavailable(freeButton, askable))))

  test("a step whose paying option fails the search is kept for the ask"):
    // Paying then costs 99 Supply, which p1 does not have.
    val powers = WalkerPowers(Vector(toll))
    val action = step(afterPay = Vector(SpendSupply(p1, 99)))
    assertEquals(ProcedureWalker.openDecisions(ready, action,
      parkedAt(ready, action, powers), powers).head.query,
      DecisionQuery.ChooseOne(Vector(
        DecisionOption.Unavailable(freeButton, askable))))

  test("after a refusal, a step nobody can pay is not reached"):
    assert(ProcedureWalker.advance(refused, behind(step(unpayable)), None,
      WalkerPowers(Vector(toll))).isLeft)

  test("a preview reads an unpayable step's free way on as if granted"):
    val powers = WalkerPowers(Vector(toll))
    val action = behind(step(unpayable,
      afterFree = Vector(SpendSupply(p1, 1))))
    assertEquals(WalkerSimulation.previewParked(ready, action,
      parkedAt(ready, action, powers), powers, followUp = true)
      .map(_.map(_.outcome)), Right(Vector(Right(PreviewOutcome(Vector.empty,
        complete = false, next = Vector(PreviewedOption(
          DecisionOption.Unavailable(freeButton, askable),
          Right(PreviewOutcome(Vector(SpendSupply(p1, 1)),
            complete = true)))))))))
