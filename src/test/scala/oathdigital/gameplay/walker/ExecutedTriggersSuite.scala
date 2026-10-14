package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{Contribution, ExecutedBatch,
  ExecutedTrigger}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Outcomes.{finished, parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._
import oathdigital.testkit.WalkerStubs.continueOption

class ExecutedTriggersSuite extends munit.FunSuite:
  /** Whether `executed` moves the fixture actor's pawn. */
  private def movesThePawn(executed: Vector[CoreOperation]): Boolean =
    executed.exists {
      case Move(Piece.Pawn(player), _, _, _) => player == actor
      case _ => false
    }

  /** A test trigger: after a batch that moves the actor's pawn, asks the
    * fixture's continue-or-stop question, then spends a Supply. */
  private object AsksAfterThePawnMoves extends ExecutedTrigger:
    val id: PowerId = PowerId("test.asks-after-the-pawn-moves")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    def onExecuted(batch: ExecutedBatch): Option[Operation] =
      Option.when(movesThePawn(batch.executed))(Sequence(decide,
        BuildOps((_, _) => Right(Vector[CoreOperation](spendOneSupply)))))

  /** The move back from S3 to where the fixture's pawn starts. */
  private val movePawnBack: Move = Move(Piece.Pawn(actor), movePawnToS3.to,
    movePawnToS3.from)

  /** A test trigger: after a batch that moves the actor's pawn, moves it
    * back, asking nothing. */
  private object MovesThePawnBack extends ExecutedTrigger:
    val id: PowerId = PowerId("test.moves-the-pawn-back")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    def onExecuted(batch: ExecutedBatch): Option[Operation] =
      Option.when(movesThePawn(batch.executed))(
        BuildOps((_, _) => Right(Vector[CoreOperation](movePawnBack))))

  private val asking = WalkerPowers(Vector(AsksAfterThePawnMoves))
  /** The pawn moves to S3, then a Supply is spent. */
  private val movedThenSpent: Operation = Sequence(movePawnToS3,
    spendOneSupply)
  private val heldMove: CoreOperation =
    Defer(ExecutedTriggers.key, Vector(movePawnToS3))

  test("a batch that moves the pawn holds its operations in its own step " +
      "and parks on the follow-up's question, under the batch's node"):
    val park = parked(ProcedureWalker.advance(ready, movedThenSpent, None,
      asking))
    assertEquals(park.tree.at, Vector("0", "0", "0"))
    assertEquals(park.events.map(recorded(_).ops),
      Vector(Vector[CoreOperation](movePawnToS3, heldMove)))
    assertEquals(park.events.map(recorded(_).nodeId), Vector("0"))

  test("an answer resumes inside the follow-up, which runs before the " +
      "rest of the walk and then takes the held batch"):
    val park = parked(ProcedureWalker.advance(ready, movedThenSpent, None,
      asking))
    val atPark = applyEvents(ready, park.events)
    assert(ExecutedTriggers.holds(atPark))
    val done = finished(ProcedureWalker.resolve(atPark, movedThenSpent,
      park.tree, Answered(decide.decisionId, ChooseOneAnswer(continueOption),
        actor), asking))
    assertEquals(done.events.map(recorded(_).ops), Vector(
      Vector.empty[CoreOperation], Vector[CoreOperation](spendOneSupply),
      Vector[CoreOperation](TakeDeferred(ExecutedTriggers.key)),
      Vector[CoreOperation](spendOneSupply)))
    assertEquals(done.events.map(recorded(_).nodeId),
      Vector("0.0.0", "0.0.1", "0.1", "1"))
    assertEquals(done.events.map(recorded(_).contributions).take(2),
      Vector.fill(2)(Vector(AsksAfterThePawnMoves.id)))
    assertEquals(supplyOf(done.treeless), supplyOf(ready) - 2)

  test("a follow-up's own batches, and a batch no trigger watches, start " +
      "no follow-up"):
    val done = finished(ProcedureWalker.advance(ready, movedThenSpent, None,
      WalkerPowers(Vector(MovesThePawnBack))))
    assertEquals(done.events.map(recorded(_).ops), Vector(
      Vector[CoreOperation](movePawnToS3, heldMove),
      Vector[CoreOperation](movePawnBack),
      Vector[CoreOperation](TakeDeferred(ExecutedTriggers.key)),
      Vector[CoreOperation](spendOneSupply)))
    assertEquals(pawnSiteOf(done.treeless), pawnSiteOf(ready))
