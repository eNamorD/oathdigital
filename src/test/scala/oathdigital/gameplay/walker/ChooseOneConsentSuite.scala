package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{OptionRestriction, Waiver}
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
  private val plain = NoteKey("plain", Vector(NotePart.Text("Pay first.")))
  private val consent = NoteKey("consent",
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
      case DecisionQuery.ChooseOne(options, _) =>
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
