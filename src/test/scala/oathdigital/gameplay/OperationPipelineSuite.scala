package oathdigital.gameplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted
import oathdigital.model.TestGameFixtures._

class OperationPipelineSuite extends munit.FunSuite:
  private val ready = ReadyGames.of(game)

  test("staged partial gain feeds a later required spend"):
    val operations = Vector[CoreOperation](
      GainSupply(playerId, 3), SpendSupply(playerId, 7))
    val oneBelow = Table.from(ready).supply(playerId, 6).unchecked
    val run = accepted(OperationPipeline.run(oneBelow, operations,
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    assertEquals(run.executed,
      Vector[CoreOperation](GainSupply(playerId, 1), SpendSupply(playerId, 7)))
    assertEquals(run.state.game.current.players.head.board.supply.supply, 0)

  test("optional short spend records actual count with default requiredness"):
    val one = Table.from(ready).supply(playerId, 1).unchecked
    val run = accepted(OperationPipeline.run(one,
      Vector(SpendSupply(playerId, 3, required = false)),
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    assertEquals(run.executed, Vector(SpendSupply(playerId, 1)))
    assertEquals(run.state.game.current.players.head.board.supply.supply, 0)

  test("all-skipped nonempty batch succeeds but empty input rejects"):
    val empty = Table.from(ready).bankFavor(Suit.Order, 0).unchecked
    val run = accepted(OperationPipeline.run(empty,
      Vector(Gain.Favor(playerId, Suit.Order, 2)),
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    assertEquals(run.executed, Vector.empty)
    assertEquals(run.skipped.size, 1)
    assertEquals(run.state, empty)
    assert(OperationPipeline.run(empty, Vector.empty,
      OperationPolicy.Permissive, Vector.empty)(Right(_)).isLeft)

  test("legacy claimed count cannot silently accept a partial effect"):
    val claimed = Vector[CoreOperation](Gain.Favor(playerId, Suit.Order, 7))
    val run = accepted(OperationPipeline.run(ready, claimed,
      OperationPolicy.exact(claimed, "test effect"), Vector.empty)(Right(_)))
    // The Order bank holds only 5 of the 7 claimed.
    assertEquals(run.executed,
      Vector[CoreOperation](Gain.Favor(playerId, Suit.Order, 5)))
    assert(run.expectEffects(claimed, "recorded favor mismatch").isLeft)

  test("immune optional effect skips while later legal effect executes"):
    val blocked = Gain.Favor(playerId, Suit.Order, 1)
    val legal = Gain.Secrets(playerId, 1)
    val restriction = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation) =
        Option.when(operation == blocked)(OperationReason("immune",
          "test effect is immune", OperationReasonKind.Impossible))
    val run = accepted(OperationPipeline.run(ready, Vector(blocked, legal),
      OperationPolicy.Permissive, Vector(restriction))(Right(_)))
    assertEquals(run.executed, Vector[CoreOperation](legal))
    assertEquals(run.skipped.map(_.requested), Vector[CoreOperation](blocked))

  test("a Vision moved to the shared bank leaves the game and the card " +
      "inventory allows it"):
    val card = VisionRules.Conspiracy
    val state = Table.start.hand(p1, card).ready
    val box = Move(Piece.Card(card), PositionedLocation(Location.Hand(p1)),
      PositionedLocation(Location.SharedBank))
    val run = accepted(OperationPipeline.run(state, Vector(box),
      OperationPolicy.Permissive, Vector.empty)(Right(_)))
    assertEquals(run.executed, Vector[CoreOperation](box))
    assertEquals(run.state.game.current.temporaryHands(p1), Vector.empty)
    assert(!accepted(CardIndex.from(run.state.game)).ids.contains(card))
    assertEquals(OperationRun.boxed(run.executed), Set[CardId](card))

  test("a card that is not a Vision cannot be moved to the shared bank"):
    val base = Table.start.ready
    val denizen = base.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId => id
    }.get
    val state = Table.start.hand(p1, denizen).ready
    val box = Move(Piece.Card(denizen), PositionedLocation(Location.Hand(p1)),
      PositionedLocation(Location.SharedBank))
    assertEquals(OperationPipeline.run(state, Vector(box),
      OperationPolicy.Permissive, Vector.empty)(Right(_)).left.toOption, Some(OathViolation.CoreOperationRejected("invalid-destination",
        "shared bank cannot receive denizen card")))

  test("a card that leaves the game without a boxing move still fails the " +
      "inventory check"):
    val card = VisionRules.Conspiracy
    val state = Table.start.hand(p1, card).ready
    val vanish: ReadyGame => Either[OathViolation, ReadyGame] = ready =>
      Right(ready.updateCurrent(current => current.copy(temporaryHands =
        current.temporaryHands.updated(p1, Vector.empty))))
    assertEquals(OperationPipeline.run(state, Vector(GainSupply(p1, 1)),
      OperationPolicy.Permissive, Vector.empty)(vanish).left.toOption, Some(OathViolation.CoreOperationRejected("card-inventory-changed",
        "card inventory changed: 1 missing, 0 unexpected")))
