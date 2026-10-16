package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}

/** A choose-one passed when empty parks on a Continue button when the
  * search leaves it nothing, its options shown with their reasons; a
  * search still passes it (catalog batch 8, slice 10, part 2b). */
class EmptyChoicesSuite extends munit.FunSuite:
  private val ready = Table.start.ready
  private val powers = WalkerPowers(Vector.empty)
  private val pick = "test.pick"
  private val left = DecisionOptionRef.Button("left")
  private val right = DecisionOptionRef.Button("right")

  /** Nothing, unless `test.pick` was answered with a button in `refused`,
    * which is rejected. */
  private def refusing(refused: Set[DecisionOptionRef])(
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, pick) match
      case Some(ref) if refused(ref) => Left(
        OathViolation.InvalidEventOrder(s"${ref.wireId} is refused"))
      case _ => Right(Vector.empty)

  /** `test.pick` over Left and Right, passed when empty, then a required
    * batch that refuses an answer naming a button in `refused`. */
  private def pickRefusing(refused: Set[DecisionOptionRef],
      options: Vector[DecisionOption] = Vector(
        DecisionOption.Button(left, "Left"),
        DecisionOption.Button(right, "Right"))): Vector[Operation] =
    Vector(
      Decide(pick, p1, DecisionQuery.ChooseOne(options,
        heading = Some("Pick a side")), passWhenEmpty = true),
      BuildOps((_, pending) => refusing(refused)(pending), required = true))

  private def parkedAt(tree: Operation)(using munit.Location)
      : (PendingTree, DecisionQuery.ChooseOne) =
    val pending = accepted(ProcedureWalker.advance(ready, tree, None,
      powers)) match
      case WalkerOutcome.Parked(at, _) => at
      case other => fail(s"the walk did not park: $other")
    present(ProcedureWalker.parkedDecide(ready, tree, pending, powers),
      "the parked decision").query match
      case one: DecisionQuery.ChooseOne => (pending, one)
      case other => fail(s"expected a choose-one, got $other")

  test("with every option hidden it parks, each shown unavailable, and " +
      "offers Continue"):
    val (_, query) = parkedAt(Sequence(pickRefusing(Set(left, right))))
    assertEquals(query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Unavailable(DecisionOption.Button(left, "Left"),
        OptionReason.Written("Cannot be chosen now")),
      DecisionOption.Unavailable(DecisionOption.Button(right, "Right"),
        OptionReason.Written("Cannot be chosen now")),
      DecisionOption.Button(Decide.Pass, "Continue")),
      heading = Some("Nothing to choose")))

  test("an option already unavailable keeps its own reason"):
    val noted = OptionReason.Written("Not today")
    val (_, query) = parkedAt(Sequence(pickRefusing(Set(right), Vector(
      DecisionOption.Unavailable(DecisionOption.Button(left, "Left"), noted),
      DecisionOption.Button(right, "Right")))))
    assertEquals(query.options.headOption, Some(DecisionOption.Unavailable(
      DecisionOption.Button(left, "Left"), noted)))
    assertEquals(query.options.lastOption,
      Some(DecisionOption.Button(Decide.Pass, "Continue")))

  test("with no option at all it parks on Continue alone"):
    val (_, query) = parkedAt(Sequence(pickRefusing(Set.empty, Vector.empty)))
    assertEquals(query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(Decide.Pass, "Continue")),
      heading = Some("Nothing to choose")))

  test("pressing Continue passes it, and the power reads no answer"):
    val tree = Sequence(pickRefusing(Set(left, right)))
    val (pending, _) = parkedAt(tree)
    val answer = Answered(pick, DecisionAnswer.ChooseOneAnswer(Decide.Pass),
      p1)
    accepted(ProcedureWalker.resolve(ready, tree, pending, answer,
      powers)) match
      case _: WalkerOutcome.Finished => ()
      case other => fail(s"the walk did not finish: $other")
    val answered = pending.copy(answered = Vector(answer))
    assertEquals(PowerAnswers.one(answered, pick), None)
    assertEquals(PowerAnswers.last(Vector(answer), pick), None)

  test("with one option usable it asks as before, with no Continue"):
    val (_, query) = parkedAt(Sequence(pickRefusing(Set(right))))
    assertEquals(query.options, Vector(DecisionOption.Button(left, "Left")))
    assertEquals(query.heading, Some("Pick a side"))

  test("a search still passes it, so the option that leads there stays " +
      "open"):
    val go = DecisionOptionRef.Button("go")
    val tree = Sequence(Decide("test.outer", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(go, "Go")))) +:
      pickRefusing(Set(left, right)))
    val (_, outer) = parkedAt(tree)
    assertEquals(outer.options, Vector(DecisionOption.Button(go, "Go")))
