package oathdigital.gameplay.powerresolver

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Unwrap.as

/** A composite that is not the walker's own and carries no window, as a
  * custom composite inside its window's subtree does. */
private final case class WindowlessComposite(
    override val children: Vector[Operation]) extends Operation

/** A windowless composite that rebuilds itself with its children
  * rewritten, as a card play's body does. */
private final case class RebuiltComposite(
    override val children: Vector[Operation]) extends RewritableComposite:
  def rewritten(rewrite: Operation => Operation): Operation =
    RebuiltComposite(children.map(rewrite))

/** `OperationRewrite`: what it descends, what it leaves alone, and when it
  * matches (operation rewrite design). Pure: no walker and no catalog. */
class OperationRewriteSuite extends munit.FunSuite:
  private val actor = Table.p1
  private val foldState = Table.start.ready
  private val walkState = Table.start.supply(actor, 1).ready
  private val walkAnswers = PendingTree(Vector("0", "2"), Vector.empty)
  private val window = PowerWindow.RecoverEligibility
  private val otherWindow = PowerWindow.RecoverBeforeFirstRoll
  private val gainOne: GainSupply = GainSupply(actor, 1)

  /** The context of folding `operation` at `window`, with `foldState`. */
  private def foldingCtx(operation: Operation): PowerCtx =
    PowerCtx(foldState, actor, RuleSourceRef.GameRule("test"), window,
      Vector("0"), operation)

  /** The context of folding a windowed `Sequence`. */
  private val foldingSequence: PowerCtx =
    foldingCtx(Sequence(Vector.empty, Some(window)))

  /** A supply gain becomes a gain of 2 when matched with the fold's state,
    * and of 3 when matched with the walk's. */
  private val gainByMoment
      : OperationRewrite.Matcher[CoreOperation, CoreOperation] =
    (ready, _) => {
      case GainSupply(player, _) =>
        Vector(GainSupply(player, if ready eq walkState then 3 else 2))
    }

  /** The node kinds of `operation` and its static children, in order. */
  private def kinds(operation: Operation): Vector[String] = operation match
    case sequence: Sequence => "Sequence" +: sequence.children.flatMap(kinds)
    case repeat: Repeat => "Repeat" +: kinds(repeat.body)
    case other => Vector(other.getClass.getSimpleName)

  test("a core op among the children is matched at fold time, with the " +
      "fold's state"):
    val rewritten = OperationRewrite.ops(foldingSequence,
      Vector(gainOne, SpendSupply(actor, 1)))(gainByMoment)
    assertEquals(rewritten, Vector[Operation](GainSupply(actor, 2),
      SpendSupply(actor, 1)))

  test("several replacements for one op go in a Sequence in its place, " +
      "and none leave an empty one"):
    val rewritten = OperationRewrite.ops(foldingSequence,
      Vector(gainOne, SpendSupply(actor, 1)))((_, _) => {
        case gain: GainSupply => Vector(gain, gain)
        case _: SpendSupply => Vector.empty
      })
    assertEquals(rewritten, Vector[Operation](
      Sequence(Vector(gainOne, gainOne)), Sequence(Vector.empty)))

  test("a Sequence's children and a Repeat's body are rewritten, and the " +
      "Repeat keeps its guard"):
    val guard: (ReadyGame, PendingTree) => Boolean = (_, _) => false
    val rewritten = OperationRewrite.ops(foldingSequence, Vector(
      Sequence(Vector(gainOne)), Repeat(guard, gainOne)))(gainByMoment)
    assertEquals(rewritten.head, Sequence(Vector(GainSupply(actor, 2))))
    val repeat = as[Repeat](rewritten(1))
    assert(repeat.guard eq guard)
    assertEquals(repeat.body, GainSupply(actor, 2))

  test("a BuildOps' ops are matched when the walk builds them, with the " +
      "walk's state, and its closure does not run at fold time"):
    var builds = 0
    val build = BuildOps((_, _) => { builds += 1; Right(Vector(gainOne)) })
    val rewritten = as[BuildOps](OperationRewrite.ops(foldingSequence,
      Vector(build))(gainByMoment).head)
    assertEquals(builds, 0)
    assertEquals(rewritten.build(walkState, walkAnswers),
      Right(Vector[CoreOperation](GainSupply(actor, 3))))
    assertEquals(builds, 1)

  test("a windowed leaf handed to its own transforms is entered, and keeps " +
      "its window and required"):
    val leaf = BuildOps((_, _) => Right(Vector(gainOne)), Some(window),
      required = true)
    val rewritten = as[BuildOps](OperationRewrite.ops(foldingCtx(leaf),
      Vector(leaf))(gainByMoment).head)
    assertEquals(rewritten.window, Some(window))
    assert(rewritten.required)
    assertEquals(rewritten.build(walkState, walkAnswers),
      Right(Vector[CoreOperation](GainSupply(actor, 3))))

  test("a Branch's selection is rewritten when the walk selects it, with " +
      "the walk's state"):
    var selections = 0
    val branch = Branch((_, _) => { selections += 1; Vector(gainOne) })
    val rewritten = as[Branch](OperationRewrite.ops(foldingSequence,
      Vector(branch))(gainByMoment).head)
    assertEquals(selections, 0)
    assertEquals(rewritten.select(walkState, walkAnswers),
      Vector[Operation](GainSupply(actor, 3)))

  test("two folds give the same shape"):
    val children = Vector[Operation](Branch((_, _) => Vector(gainOne)),
      BuildOps((_, _) => Right(Vector(gainOne))), Sequence(Vector(gainOne)),
      gainOne)
    def fold = OperationRewrite.ops(foldingSequence, children)(gainByMoment)
    assertEquals(fold.flatMap(kinds), fold.flatMap(kinds))
    assertEquals(fold.flatMap(kinds), children.flatMap(kinds))

  test("a node with its own window and a custom composite are left alone"):
    val nested = Sequence(Vector(gainOne), Some(otherWindow))
    val custom = WindowlessComposite(Vector(gainOne))
    val rewritten = OperationRewrite.ops(foldingSequence,
      Vector(nested, custom))(gainByMoment)
    assert(rewritten(0) eq nested)
    assert(rewritten(1) eq custom)

  test("a rewritable composite is entered by ops and by nodes, and rebuilt " +
      "with its children rewritten"):
    assertEquals(OperationRewrite.ops(foldingSequence,
      Vector(RebuiltComposite(Vector(gainOne))))(gainByMoment),
      Vector[Operation](RebuiltComposite(Vector(GainSupply(actor, 2)))))
    val build = BuildOps((_, _) => Right(Vector(gainOne)))
    val note = Note(PowerId("test.note"), _ => None)
    assertEquals(OperationRewrite.nodes(foldingSequence,
      Vector(RebuiltComposite(Vector(build))))(
      (_, _) => { case _: BuildOps => Vector(note) }),
      Vector[Operation](RebuiltComposite(Vector(note))))

  test("a leaf with the fold's window is left alone below a composite, and " +
      "entered below the leaf being folded"):
    val ask = Decide("test.ask", actor, DecisionQuery.ChooseOne(Vector.empty),
      window = Some(window))
    val dropAsks: OperationRewrite.Matcher[CoreOperation, CoreOperation] =
      (_, _) => { case _: Decide => Vector.empty }
    assertEquals(OperationRewrite.ops(foldingSequence, Vector(ask))(dropAsks),
      Vector[Operation](ask))
    assertEquals(OperationRewrite.ops(foldingCtx(ask), Vector(ask))(dropAsks),
      Vector[Operation](Sequence(Vector.empty)))

  test("a semantic composite is matched whole, never entered"):
    val discard = Discard.Denizen(CatalogNames.denizen("Wild Mounts"),
      PositionedLocation(Location.PlayArea(actor)), Region.Cradle,
      Suit.Nomad, favor = 0, secrets = 0, actingPlayer = actor)
    val dropMoves: OperationRewrite.Matcher[CoreOperation, CoreOperation] =
      (_, _) => { case _: Move => Vector.empty }
    assert(OperationRewrite.ops(foldingSequence, Vector(discard))(dropMoves)
      .head eq discard)
    assertEquals(OperationRewrite.ops(foldingSequence, Vector(discard))(
      (_, _) => { case _: Discard.Denizen => Vector(gainOne) }),
      Vector[Operation](gainOne))

  test("nodes replaces a BuildOps whole without entering it, and leaves one " +
      "it does not match as it is"):
    var builds = 0
    val build = BuildOps((_, _) => { builds += 1; Right(Vector(gainOne)) })
    val note = Note(PowerId("test.note"), _ => None)
    assertEquals(OperationRewrite.nodes(foldingSequence, Vector(build))(
      (_, _) => { case _: BuildOps => Vector(note) }),
      Vector[Operation](note))
    assert(OperationRewrite.nodes(foldingSequence, Vector(build))(
      (_, _) => { case _: WindowlessComposite => Vector.empty }).head eq build)
    assertEquals(builds, 0)

  test("nodes matches a custom composite in the tree and in what a Branch " +
      "selects"):
    val custom = WindowlessComposite(Vector(gainOne))
    val unwrap: OperationRewrite.Matcher[Operation, Operation] =
      (_, _) => { case found: WindowlessComposite => found.children }
    assertEquals(OperationRewrite.nodes(foldingSequence, Vector(custom))(
      unwrap), Vector[Operation](gainOne))
    val branch = as[Branch](OperationRewrite.nodes(foldingSequence,
      Vector(Branch((_, _) => Vector(custom))))(unwrap).head)
    assertEquals(branch.select(walkState, walkAnswers),
      Vector[Operation](gainOne))
