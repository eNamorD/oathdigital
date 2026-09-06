package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
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

    // The PendingTree's context snapshots the pre-park ReadyGame (the state it
    // was derived from), so the tree is constructible before the state that
    // stores it.
    val tree = PendingTree(
      at = Vector("recover.roll"),
      answered = Vector("recover.choice"),
      actor = actor,
      action = Repeat((_: ReadyGame, _: PendingTree) => false,
        AdjustSupply(actor, -1)),
      ctx = WalkerCtx(baseReady, Map("iteration" -> 1)))

    val ready = baseReady.copy(game = baseReady.game.copy(current =
      baseReady.game.current.copy(
        walkerPending = Some(tree),
        rollPools = Map(PoolKey("recover") -> DicePoolState(2)))))

    assertEquals(ready.game.current.walkerPending, Some(tree))
    assertEquals(ready.game.current.rollPools(PoolKey("recover")),
      DicePoolState(2))
    assertEquals(ready.game.current.walkerPending.get.actor, actor)
    assertEquals(ready.game.current.walkerPending.get.ctx.ready, baseReady)
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
