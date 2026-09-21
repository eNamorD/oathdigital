package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.TestGameFixtures._

/** A `Repeat` around a `Branch` around a decision two players may answer: the
  * shape Negotiation's deal loop takes, exercised with a synthetic tree.
  */
class CoOwnedDecideSuite extends munit.FunSuite {
  private val owner = playerId
  private val guest = PlayerId("player-blue")
  private val outsider = PlayerId("player-green")
  private val noPowers = WalkerPowers.empty
  private val go = DecisionOptionRef.Button("go")
  private val stop = DecisionOptionRef.Button("stop")
  private val ready: ReadyGame = ReadyGames.of(game)

  private val shared = Decide("shared.deal", owner,
    DecisionQuery.ChooseOne(Vector(DecisionOption.Button(go, "Go"),
      DecisionOption.Button(stop, "Stop"))), coOwners = Vector(guest))

  /** Re-parks the shared decision until someone answers stop. The guard and
    * the branch read only the answered list, as the walk rule requires.
    */
  private val loop: Operation = Sequence(Repeat(
    (_, pending) => !pending.answered.lastOption.map(_.answer)
      .contains(ChooseOneAnswer(stop)),
    Branch((_, _) => Vector(shared))))

  private def answer(by: PlayerId, ref: DecisionOptionRef.Button) =
    Answered(shared.decisionId, ChooseOneAnswer(ref), by)

  private def parkedAt(outcome: Either[OathViolation, WalkerOutcome]): PendingTree =
    outcome match {
      case Right(WalkerOutcome.Parked(pending, _)) => pending
      case other => fail(s"expected a park, got $other")
    }

  test("owners lists the owner first and drops a repeated owner") {
    assertEquals(shared.owners, Vector(owner, guest))
    assertEquals(shared.copy(coOwners = Vector(owner, guest)).owners,
      Vector(owner, guest))
  }

  test("the park names every owner and one open decision") {
    val pending = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    assertEquals(pending.at, Vector("0", "0", "0"))
    assertEquals(ProcedureWalker.openDecisions(ready, loop, pending, noPowers),
      Vector(shared))
    assertEquals(ProcedureWalker.awaitedPlayers(ready, loop, pending, noPowers),
      Set(owner, guest))
    assertEquals(ProcedureWalker.awaitedPlayer(ready, loop, pending, noPowers),
      Some(owner))
    assertEquals(ProcedureWalker.parkedDecide(ready, loop, pending, noPowers),
      Some(shared))
  }

  test("a plain decision is awaited by its owner alone") {
    val plain = Sequence(shared.copy(coOwners = Vector.empty))
    val pending = parkedAt(ProcedureWalker.advance(ready, plain, None, noPowers))
    assertEquals(ProcedureWalker.awaitedPlayers(ready, plain, pending, noPowers),
      Set(owner))
  }

  test("owners answer in any order, the loop re-parks, and a stop finishes it") {
    val first = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    val second = parkedAt(ProcedureWalker.resolve(ready, loop, first,
      answer(guest, go), noPowers))
    val third = parkedAt(ProcedureWalker.resolve(ready, loop, second,
      answer(owner, go), noPowers))
    assertEquals(third.at, Vector("0", "0", "0"))
    assertEquals(third.answered.map(_.by), Vector(guest, owner))
    ProcedureWalker.resolve(ready, loop, third, answer(guest, stop), noPowers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.size, 1)
      case other => fail(s"expected the loop to finish, got $other")
    }
  }

  test("a player who is neither owner nor co-owner is rejected") {
    val pending = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    assertEquals(ProcedureWalker.resolve(ready, loop, pending,
      answer(outsider, go), noPowers), Left(OathViolation.WrongPlayer(owner, outsider)))
  }

  test("an answer for a decision that is not open is rejected") {
    val pending = parkedAt(ProcedureWalker.advance(ready, loop, None, noPowers))
    val stale = Answered("other.decision", ChooseOneAnswer(go), guest)
    assert(ProcedureWalker.resolve(ready, loop, pending, stale, noPowers).isLeft)
  }
}
