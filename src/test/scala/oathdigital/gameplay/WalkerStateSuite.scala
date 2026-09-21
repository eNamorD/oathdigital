package oathdigital.gameplay

import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.TestGameFixtures._

/** Task 2 spec: walker leaves (Decide/Repeat) and PendingTree/pool state are
  * constructible and readable from a ReadyGame's CurrentGameState.
  */
class WalkerStateSuite extends munit.FunSuite {
  private val actor = playerId
  private val decide = Decide(
    decisionId = "recover.choice",
    owner = actor,
    query = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("continue"), "Continue"),
      DecisionOption.Button(DecisionOptionRef.Button("stop"), "Stop"))))

  private val baseReady = ReadyGames.of(game)

  test("a walker PendingTree with a dice pool slot stores in state and reads back") {
    // Legacy game state carries neither walkerPending nor pools by default.
    assert(baseReady.game.current.walkerPending.isEmpty)
    assert(baseReady.game.current.rollPools.isEmpty)

    // Pending is a pointer only (spec S1): at/answered. The action tree
    // is derived per command and the walker ctx rebuilt from state, so nothing
    // gameplay-typed is stored here.
    val answered = Answered("recover.choice",
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)
    val tree = PendingTree(at = Vector("recover.roll"),
      answered = Vector(answered))

    val ready = baseReady.updateCurrent(_.copy(
        walkerPending = Some(tree),
        rollPools = Map(PoolKey("recover") -> DicePoolState(2))))

    assertEquals(ready.game.current.walkerPending, Some(tree))
    assertEquals(ready.game.current.walkerPending.get.at,
      Vector("recover.roll"))
    assertEquals(ready.game.current.walkerPending.get.answered,
      Vector(answered))
    assertEquals(ready.game.current.walkerPending.get.answered.map(_.by),
      Vector(actor))
    assertEquals(ready.game.current.rollPools(PoolKey("recover")),
      DicePoolState(2))
  }

  test("a Decide leaf flattens to itself") {
    assertEquals(Operation.flatten(decide), Vector(decide))
  }

  test("a Repeat composite flattens to its body's leaves regardless of guard") {
    // Guard is evaluated by the walker only; the accessor always descends.
    val body = PayCost(actor, Location.OnCard(DenizenId("pay:target")),
      Cost(favor = 1, secret = 1))
    val repeat = Repeat((_: ReadyGame, _: PendingTree) => false, body)
    assertEquals(Operation.flatten(repeat), Operation.flatten(body))
    assertEquals(Operation.flatten(repeat).size, 2)
  }
}
