package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerUse, PowerUseRefusal, PowerUseRestriction, PowerUseStep}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** The walker judges an `AutomaticUse` where the walk reaches it: its
  * effect runs unless a power-use restriction refuses the use in the state
  * at that node (catalog batch 8, slice 10, part 2). */
class AutomaticUseSuite extends munit.FunSuite:
  private val automatic = PowerId("test.automatic")
  private val card = CatalogNames.denizen("Wrestlers")

  /** A test power: refuses every use of `automatic` while p1 holds no
    * favor, and nobody may waive it. */
  private object RefusesTestPowerWithoutFavor extends ContributingPower
      with PowerUseRestriction:
    val id: PowerId = PowerId("test.refuses-without-favor")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def powerUseRestrictions: Vector[PowerUseRestriction] =
      Vector(this)
    def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
      Option.when(use.power == automatic && Look(ready).favor(p1) == 0)(
        PowerUseRefusal(id,
          LogNote(Some(PowerSourceRef.Card(card)), "refused",
          Vector.empty), OperationReason("test-refused", "no favor",
          OperationReasonKind.Impossible)))

  /** A test power: refuses every use of `automatic` while p1 holds no
    * favor, and p2 may waive it. */
  private object RefusesTestPowerWithoutFavorUnlessP2Consents
      extends ContributingPower with PowerUseRestriction:
    val id: PowerId = PowerId("test.refuses-without-favor-waivably")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def powerUseRestrictions: Vector[PowerUseRestriction] =
      Vector(this)
    private val note = LogNote(Some(PowerSourceRef.Card(card)), "refused",
      Vector.empty)
    def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
      Option.when(use.power == automatic && Look(ready).favor(p1) == 0)(
        PowerUseRefusal(id, note, OperationReason("test-refused",
          "no favor", OperationReasonKind.Impossible, Some(OperationWaiver(
            id, Some(p2), p1sUseKey, note, note)))))

  /** The key p2's answer on p1's use of the test power is remembered
    * under. */
  private val p1sUseKey: ConsentKey = ConsentKey(
    RefusesTestPowerWithoutFavorUnlessP2Consents.id, p1,
    PowerUseRules.useKey, PowerUseRules.cardRef(card))

  private val askable = WalkerPowers(Vector(
    RefusesTestPowerWithoutFavorUnlessP2Consents))
  private val stepId = PowerUseStep.decisionId(automatic)

  /** `ready` with p2's `answer` on p1's use remembered this turn. */
  private def rememberingP2s(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame = ready.copy(consent = ConsentState(answers = Map(
    p1sUseKey -> answer.by(p2)), turn = Some(ConsentTurn.of(ready))))

  private def parkedAt(from: ReadyGame, tree: Operation,
      walkWith: WalkerPowers)(using munit.Location): PendingTree =
    accepted(ProcedureWalker.advance(from, tree, None, walkWith)) match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"the walk did not park: $other")

  private def finishedWithEvents(outcome: Either[OathViolation,
      WalkerOutcome])(using munit.Location): (ReadyGame, Vector[OathEvent]) =
    accepted(outcome) match
      case WalkerOutcome.Finished(end, events) => (end, events)
      case other => fail(s"the walk did not finish: $other")

  private def answeringStep(ref: DecisionOptionRef): Answered =
    Answered(stepId, DecisionAnswer.ChooseOneAnswer(ref), p1)

  private val powers = WalkerPowers(Vector(RefusesTestPowerWithoutFavor))
  private val oneFavor: Operation = Gain.Favor(p1, Suit.Order, 1)

  /** p1 holding `favor`, with favor in the Order bank. */
  private def p1Holding(favor: Int): ReadyGame =
    Table.start.favor(p1, favor).bankFavor(Suit.Order, 3).ready

  /** An automatic use of the test power that gains p1 one favor. */
  private val gainsOneFavor: AutomaticUse =
    AutomaticUse(p1, automatic, card, (_, _) => Vector(oneFavor))

  private def finished(from: ReadyGame, tree: Operation)(
      using munit.Location): ReadyGame =
    accepted(ProcedureWalker.advance(from, tree, None, powers)) match
      case WalkerOutcome.Finished(end, _) => end
      case other => fail(s"the walk did not finish: $other")

  test("an allowed automatic use runs its effect"):
    assertEquals(Look(finished(p1Holding(1), gainsOneFavor)).favor(p1), 2)

  test("a refused automatic use runs nothing"):
    assertEquals(Look(finished(p1Holding(0), gainsOneFavor)).favor(p1), 0)

  test("an automatic use is judged where the walk reaches it"):
    val tree = Sequence(oneFavor, gainsOneFavor)
    assertEquals(Look(finished(p1Holding(0), tree)).favor(p1), 2)

  test("a park inside an automatic use resumes at the same decision"):
    val ask = Decide("test.ask", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"))))
    // Nothing runs before the park, so the walk resumes from `start`.
    val tree = Sequence(AutomaticUse(p1, automatic, card,
      (_, _) => Vector(ask, oneFavor)))
    val start = p1Holding(1)
    val parked = accepted(ProcedureWalker.advance(start, tree, None,
      powers)) match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"the walk did not park: $other")
    assertEquals(parked.at, Vector("0", "1", "0"))
    val resumed = accepted(ProcedureWalker.resolve(start, tree, parked,
      Answered("test.ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("go")), p1), powers)) match
      case WalkerOutcome.Finished(end, _) => end
      case other => fail(s"the walk did not finish: $other")
    assertEquals(Look(resumed).favor(p1), 2)

  test("a resume inside an automatic use runs its effect, though the " +
      "effect's own step turned the verdict"):
    // p1 holds 1 favor, so the use is allowed; the effect burns it, parks,
    // then gains 1. At the park p1 holds none, which a fresh judgement
    // would refuse.
    val ask = Decide("test.ask", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"))))
    val burnsThenAsks = Sequence(AutomaticUse(p1, automatic, card, (_, _) =>
      Vector(Burn.favor(1, PositionedLocation(Location.PlayArea(p1)),
        Some(p1)), ask, oneFavor)))
    val parked = accepted(ProcedureWalker.advance(p1Holding(1),
      burnsThenAsks, None, powers)) match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"the walk did not park: $other")
    val resumed = accepted(ProcedureWalker.resolve(p1Holding(0),
      burnsThenAsks, parked, Answered("test.ask",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("go")), p1),
      powers)) match
      case WalkerOutcome.Finished(end, _) => end
      case other => fail(s"the walk did not finish: $other")
    assertEquals(Look(resumed).favor(p1), 1)

  test("a waivable refusal parks at the use's step, which its user owns " +
      "and whose use its granter may be asked about"):
    val tree = Sequence(gainsOneFavor)
    val start = p1Holding(0)
    val pending = parkedAt(start, tree, askable)
    assertEquals(pending.at, Vector("0", "0"))
    val step = present(ProcedureWalker.parkedDecide(start, tree, pending,
      askable), "the step")
    assertEquals((step.decisionId, step.owner), (stepId, p1))
    step.query match
      case one: DecisionQuery.ChooseOne =>
        assertEquals(one.options.map(_.ref),
          Vector(PowerUseStep.use, PowerUseStep.skip))
        one.options.head match
          case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
            assertEquals(ask.asks, Vector(ConsentAsk(p2, Vector(
              RefusesTestPowerWithoutFavorUnlessP2Consents.id))))
            assertEquals(ask.covers, Vector(p1sUseKey))
          case other => fail(s"expected an askable use, got $other")
      case other => fail(s"expected a choose-one, got $other")

  test("the step's answer decides: skip runs nothing, use runs the effect"):
    val tree = Sequence(gainsOneFavor)
    val start = p1Holding(0)
    val pending = parkedAt(start, tree, askable)
    val (skipped, _) = finishedWithEvents(ProcedureWalker.resolve(start,
      tree, pending, answeringStep(PowerUseStep.skip), askable))
    assertEquals(Look(skipped).favor(p1), 0)
    val granted = rememberingP2s(start, ConsentAnswer.Granted)
    val (used, _) = finishedWithEvents(ProcedureWalker.resolve(granted,
      tree, pending, answeringStep(PowerUseStep.use), askable))
    assertEquals(Look(used).favor(p1), 1)

  test("a waivable refusal beside one nobody may waive deactivates the " +
      "use unasked, with no line"):
    val both = WalkerPowers(Vector(RefusesTestPowerWithoutFavor,
      RefusesTestPowerWithoutFavorUnlessP2Consents))
    val (end, events) = finishedWithEvents(ProcedureWalker.advance(
      p1Holding(0), Sequence(gainsOneFavor), None, both))
    assertEquals(Look(end).favor(p1), 0)
    assertEquals(events.collect { case noted: LogNoted => noted },
      Vector.empty)

  test("a remembered grant runs the effect without a step"):
    val (end, _) = finishedWithEvents(ProcedureWalker.advance(
      rememberingP2s(p1Holding(0), ConsentAnswer.Granted),
      Sequence(gainsOneFavor), None, askable))
    assertEquals(Look(end).favor(p1), 1)

  test("a remembered refusal runs nothing and says the use stays " +
      "deactivated"):
    val (end, events) = finishedWithEvents(ProcedureWalker.advance(
      rememberingP2s(p1Holding(0), ConsentAnswer.Refused),
      Sequence(gainsOneFavor), None, askable))
    assertEquals(Look(end).favor(p1), 0)
    assertEquals(events.collect { case LogNoted(_, note, _) => note },
      Vector(PowerUseStep.staysDeactivated(PowerSourceRef.Card(card),
        NoteArg.Card(card))))

  test("a park inside the effect, after its step was answered, resumes at " +
      "the same decision"):
    val ask = Decide("test.ask", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"))))
    val tree = Sequence(AutomaticUse(p1, automatic, card,
      (_, _) => Vector(ask, oneFavor)))
    val start = p1Holding(0)
    val atStep = parkedAt(start, tree, askable)
    val granted = rememberingP2s(start, ConsentAnswer.Granted)
    val atAsk = accepted(ProcedureWalker.resolve(granted, tree, atStep,
      answeringStep(PowerUseStep.use), askable)) match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"the walk did not park: $other")
    assertEquals(atAsk.at, Vector("0", "1", "0"))
    val (end, _) = finishedWithEvents(ProcedureWalker.resolve(granted, tree,
      atAsk, Answered("test.ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("go")), p1), askable))
    assertEquals(Look(end).favor(p1), 1)

  test("a preview walks a use waiting on its rulers as if they let it " +
      "work"):
    assertEquals(WalkerSimulation.run(Sequence(gainsOneFavor), p1Holding(0),
      askable).map(_.nonEmpty), Right(true))

  test("the walk names the automatic use whose step it is parked at, and " +
      "no other"):
    val tree = Sequence(gainsOneFavor)
    val start = p1Holding(0)
    val atStep = parkedAt(start, tree, askable)
    assertEquals(ParkedUses.of(start, tree, atStep, askable)
      .map(use => (use.user, use.power, use.card)),
      Some((p1, automatic, card)))
    val ask = Decide("test.ask", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"))))
    val asking = Sequence(AutomaticUse(p1, automatic, card,
      (_, _) => Vector(ask)))
    assertEquals(ParkedUses.of(p1Holding(1), asking,
      parkedAt(p1Holding(1), asking, askable), askable), None)

  test("a decision before the step keeps every option: the search counts " +
      "the step as answerable"):
    val pick = Decide("test.pick", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("a"), "A"),
      DecisionOption.Button(DecisionOptionRef.Button("b"), "B"))))
    val tree = Sequence(pick, gainsOneFavor)
    val start = p1Holding(0)
    val decide = present(ProcedureWalker.parkedDecide(start, tree,
      parkedAt(start, tree, askable), askable), "the pick")
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        assert(one.options.forall(_.available), one.options)
      case other => fail(s"expected a choose-one, got $other")
