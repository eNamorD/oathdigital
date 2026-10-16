package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerUse, PowerUseRefusal, PowerUseRestriction}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

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
        PowerUseRefusal(id, PowerNote(PowerSourceRef.Card(card), "refused",
          Vector.empty), OperationReason("test-refused", "no favor",
          OperationReasonKind.Impossible)))

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
    assertEquals(parked.at, Vector("0", "0"))
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
