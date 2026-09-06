package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.gameplay.walker.{OwnerQuery, WalkerCtx}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

object WalkerStateSuite {
  /** Test-only open payload (D2: powers define their own payloads later). */
  final case class TestDecisionPayload(decision: String) extends DecisionPayload
  final case class TestOwner(actor: PlayerId) extends OwnerQuery {
    def owner(ctx: WalkerCtx): Option[PlayerId] = Some(actor)
  }
}

/** Task 2 spec: walker leaves (Decide/Repeat) and PendingTree/pool state are
  * constructible and readable from a ReadyGame's CurrentGameState.
  */
class WalkerStateSuite extends munit.FunSuite {
  private val actor = playerId
  private val decide = Decide(
    payload = WalkerStateSuite.TestDecisionPayload("continue-or-stop"),
    owner = WalkerStateSuite.TestOwner(actor),
    decisionId = "recover.choice")

  private val baseReady = ReadyGame(
    game,
    Map(actor -> PlayerColor("red")),
    FirstGameSupportState(FirstGameFoundationProfile.FixedUnaltered, actor),
    MaterialBankState(
      Suit.all.map(_ -> 5).toMap,
      Map(ForceKind.Exile(lineageId) -> 14, ForceKind.Bandit -> 24)))

  test("a walker PendingTree with a dice pool slot stores in state and reads back") {
    // Legacy game state carries neither walkerPending nor pools by default.
    assert(baseReady.game.current.walkerPending.isEmpty)
    assert(baseReady.game.current.rollPools.isEmpty)

    // Pending is a pointer only (spec S1): at/answered/actor. The action tree
    // is derived per command and the walker ctx rebuilt from state, so nothing
    // gameplay-typed is stored here.
    val answered = Answered("recover.choice",
      WalkerStateSuite.TestDecisionPayload("continue"))
    val tree = PendingTree(
      at = Vector("recover.roll"),
      answered = Vector(answered),
      actor = actor)

    val ready = baseReady.copy(game = baseReady.game.copy(current =
      baseReady.game.current.copy(
        walkerPending = Some(tree),
        rollPools = Map(PoolKey("recover") -> DicePoolState(2)))))

    assertEquals(ready.game.current.walkerPending, Some(tree))
    assertEquals(ready.game.current.walkerPending.get.at,
      Vector("recover.roll"))
    assertEquals(ready.game.current.walkerPending.get.answered,
      Vector(answered))
    assertEquals(ready.game.current.walkerPending.get.actor, actor)
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
