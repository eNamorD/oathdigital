package oathdigital.gameplay.walker

import oathdigital.testkit.PowerStubs.TransformStub
import oathdigital.gameplay.walker.WalkerSimulation.PreviewOutcome
import oathdigital.model._
import oathdigital.testkit.{Outcomes, Table}
import oathdigital.testkit.Table.p1

class WalkerPreviewSuite extends munit.FunSuite:
  private val cheap = DecisionOptionRef.Button("cheap")
  private val dear = DecisionOptionRef.Button("dear")
  private val extra = DecisionOptionRef.Button("extra")
  private val hooked = PowerWindow.SearchEligibility

  /** The start with p1's Supply at `amount`. */
  private def withSupply(amount: Int): ReadyGame =
    Table.start.supply(p1, amount).ready

  /** A decision, then a cost that depends on the answer. */
  private def tree(actor: PlayerId): Operation = Sequence(Vector[Operation](
    Decide("pick", actor, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(cheap, "Cheap"),
      DecisionOption.Button(dear, "Dear"))), window = Some(hooked)),
    Branch((_, pending) => pending.answered.collectFirst {
      case Answered("pick", DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    } match {
      case Some(`cheap`) => Vector[Operation](SpendSupply(actor, 1))
      case Some(`dear`) => Vector[Operation](SpendSupply(actor, 3))
      case _ => Vector.empty[Operation]
    })))

  test("each option of a parked choose-one is answered against the tree"):
    val ready = withSupply(2)
    // The search hides the unaffordable option before the preview reads it.
    val previewed =
      WalkerSimulation.preview(tree(p1), ready, WalkerPowers.empty)
        .getOrElse(fail("a tree parked on a choose-one must preview"))
    assertEquals(previewed.map(_.option.ref), Vector(cheap))
    assertEquals(previewed.head.outcome, Right(PreviewOutcome(
      Vector(SpendSupply(p1, 1)), complete = true)))
    // With the search off, the preview reports why each option cannot run.
    val unsearched = WalkerSimulation.preview(tree(p1), ready,
      WalkerPowers.empty.copy(probing = false))
      .getOrElse(fail("a tree parked on a choose-one must preview"))
    assertEquals(unsearched.map(_.option.ref), Vector(cheap, dear))
    assertEquals(unsearched(1).outcome, Left(
      OathViolation.CoreOperationRejected("insufficient-supply",
        "a supply spend of 3 exceeds the 2 available")),
      "three Supply is not affordable with two")

  test("an option that finishes the tree is complete and one that parks again is not"):
    val ready = withSupply(5)
    val twoStep = Sequence(Vector[Operation](
      Decide("first", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))),
      Decide("second", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(dear, "Dear"))))))
    val previewed = WalkerSimulation.preview(twoStep, ready, WalkerPowers.empty)
      .getOrElse(fail("a two-decision tree must preview its first decision"))
    assertEquals(previewed.map(_.outcome.map(_.complete)), Vector(Right(false)))

  test("a tree that never parks, or runs an operation first, or parks on another " +
      "query shape has nothing to preview"):
    val ready = withSupply(5)
    assertEquals(WalkerSimulation.preview(Sequence(Vector[Operation](
      SpendSupply(p1, 1))), ready, WalkerPowers.empty).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "a previewed tree finished without parking on a decision")))
    assertEquals(WalkerSimulation.preview(Sequence(Vector[Operation](
      SpendSupply(p1, 1),
      Decide("late", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))))), ready,
      WalkerPowers.empty).left.toOption, Some(OathViolation.InvalidEventOrder(
        "a previewed tree may not run operations before its first decision")))
    val distribute = Sequence(Vector[Operation](Decide("split", p1,
      DecisionQuery.Distribute.exactly(Vector(
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Arcane), 0, 2, Some(2)),
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Nomad), 0, 6, Some(0))),
        total = 2, heading = Some("League Treaty"), confirmLabel = "Move"))))
    assertEquals(WalkerSimulation.preview(distribute, ready, WalkerPowers.empty)
      .left.toOption, Some(OathViolation.InvalidEventOrder(
        "decision split is not a choose-one, so it cannot be previewed")))

  test("a transform on the decision's window changes what is previewed"):
    val ready = withSupply(5)
    val addExtra = TransformStub(PowerId("test.add-extra"), hooked,
      (_, operations) => operations.map {
        case decide: Decide => decide.query match {
          case DecisionQuery.ChooseOne(options, heading) =>
            decide.copy(query = DecisionQuery.ChooseOne(
              options :+ DecisionOption.Button(extra, "Extra"), heading)): Operation
          case _ => decide
        }
        case other => other
      })
    val previewed = WalkerSimulation.preview(tree(p1), ready,
      WalkerPowers(Vector(addExtra)))
      .getOrElse(fail("the folded decision must preview"))
    assertEquals(previewed.map(_.option.ref), Vector(cheap, dear, extra))
    assertEquals(previewed.last.outcome, Right(PreviewOutcome(Vector.empty,
      complete = true)))

  test("previewParked reads the same options from an already-parked position"):
    val ready = withSupply(2)
    val action = tree(p1)
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, action, None, WalkerPowers.empty))
    assertEquals(
      WalkerSimulation.previewParked(ready, action, parked.tree, WalkerPowers.empty),
      WalkerSimulation.preview(action, ready, WalkerPowers.empty))

  test("an option shown as unavailable is not previewed"):
    val ready = withSupply(2)
    val action = Sequence(Vector[Operation](Decide("pick", p1,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Button(cheap, "Cheap"),
        DecisionOption.Unavailable(DecisionOption.Button(dear, "Dear"),
          OptionReason.Written("Too dear")))))))
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, action, None, WalkerPowers.empty))
    assertEquals(WalkerSimulation.previewParked(ready, action, parked.tree,
      WalkerPowers.empty).map(_.map(_.option.ref)), Right(Vector(cheap)))

  test("starts is true for a tree a start would walk and false for one it rejects"):
    val ready = withSupply(2)
    val spend = Sequence(Vector[Operation](SpendSupply(p1, 1)))
    assert(WalkerSimulation.starts(spend, ready, WalkerPowers.empty))
    val unaffordable = Sequence(Vector[Operation](SpendSupply(p1, 3)))
    assert(!WalkerSimulation.starts(unaffordable, ready, WalkerPowers.empty))
