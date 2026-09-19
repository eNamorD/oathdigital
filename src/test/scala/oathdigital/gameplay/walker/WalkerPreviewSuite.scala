package oathdigital.gameplay.walker

import oathdigital.gameplay.ProcedureWalkerSuite.TestTransformPower
import oathdigital.gameplay.walker.WalkerSimulation.PreviewOutcome
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class WalkerPreviewSuite extends munit.FunSuite {
  private val cheap = DecisionOptionRef.Button("cheap")
  private val dear = DecisionOptionRef.Button("dear")
  private val extra = DecisionOptionRef.Button("extra")
  private val hooked = PowerWindow.SearchEligibility

  private def withSupply(amount: Int): (ReadyGame, PlayerId) = {
    val base = initialReady
    val actor = base.game.current.turn.activePlayer
    val players = base.game.current.players.map(player =>
      if (player.player == actor)
        player.copy(board = player.board.copy(supply = SupplyTrack(amount)))
      else player)
    (base.updateCurrent(_.copy(players = players)), actor)
  }

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

  test("each option of a parked choose-one is answered against the tree") {
    val (ready, actor) = withSupply(2)
    val previewed = WalkerSimulation.preview(tree(actor), ready, WalkerPowers.empty)
      .getOrElse(fail("a tree parked on a choose-one must preview"))
    assertEquals(previewed.map(_.option.ref), Vector(cheap, dear))
    assertEquals(previewed.head.outcome, Right(PreviewOutcome(
      Vector(SpendSupply(actor, 1)), complete = true)))
    assert(previewed(1).outcome.isLeft, "three Supply is not affordable with two")
  }

  test("an option that finishes the tree is complete and one that parks again is not") {
    val (ready, actor) = withSupply(5)
    val twoStep = Sequence(Vector[Operation](
      Decide("first", actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))),
      Decide("second", actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(dear, "Dear"))))))
    val previewed = WalkerSimulation.preview(twoStep, ready, WalkerPowers.empty)
      .getOrElse(fail("a two-decision tree must preview its first decision"))
    assertEquals(previewed.map(_.outcome.map(_.complete)), Vector(Right(false)))
  }

  test("a tree that never parks, or runs an operation first, or parks on another " +
      "query shape has nothing to preview") {
    val (ready, actor) = withSupply(5)
    assert(WalkerSimulation.preview(Sequence(Vector[Operation](
      SpendSupply(actor, 1))), ready, WalkerPowers.empty).isLeft)
    assert(WalkerSimulation.preview(Sequence(Vector[Operation](
      SpendSupply(actor, 1),
      Decide("late", actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(cheap, "Cheap")))))), ready,
      WalkerPowers.empty).isLeft)
    val distribute = Sequence(Vector[Operation](Decide("split", actor,
      DecisionQuery.Distribute(Vector(
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Arcane), 0, 2, Some(2)),
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Nomad), 0, 6, Some(0))),
        total = 2, heading = Some("League Treaty"), confirmLabel = "Move"))))
    assert(WalkerSimulation.preview(distribute, ready, WalkerPowers.empty).isLeft)
  }

  test("a transform on the decision's window changes what is previewed") {
    val (ready, actor) = withSupply(5)
    val addExtra = TestTransformPower(PowerId("test.add-extra"), hooked,
      (_, operations) => operations.map {
        case decide: Decide => decide.query match {
          case DecisionQuery.ChooseOne(options, heading) =>
            decide.copy(query = DecisionQuery.ChooseOne(
              options :+ DecisionOption.Button(extra, "Extra"), heading)): Operation
          case _ => decide
        }
        case other => other
      })
    val previewed = WalkerSimulation.preview(tree(actor), ready,
      WalkerPowers(Vector(addExtra)))
      .getOrElse(fail("the folded decision must preview"))
    assertEquals(previewed.map(_.option.ref), Vector(cheap, dear, extra))
    assertEquals(previewed.last.outcome, Right(PreviewOutcome(Vector.empty,
      complete = true)))
  }

  test("previewParked reads the same options from an already-parked position") {
    val (ready, actor) = withSupply(2)
    val action = tree(actor)
    val parked = ProcedureWalker.advance(ready, action, None, WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Parked]
    assertEquals(
      WalkerSimulation.previewParked(ready, action, parked.tree, WalkerPowers.empty),
      WalkerSimulation.preview(action, ready, WalkerPowers.empty))
  }
}
