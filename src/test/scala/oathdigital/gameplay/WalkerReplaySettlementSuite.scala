package oathdigital.gameplay

import oathdigital.gameplay.walker.{DeltaMeaning, ProcedureWalker,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.model.OathState.Ready

/** Replay applies a recorded `PayCost` the way the pipeline ran it: a payer who
  * is not the active player settles at once, so the recorded (requested)
  * operation expands to the same moves it did when it first ran. Without this a
  * live command, whose state is the replay of its own events, would rest a
  * defender's payment on the card it was settled off.
  */
class WalkerReplaySettlementSuite extends munit.FunSuite:
  private val active = p1
  private val payer = p2
  private val denizen = CatalogNames.denizen("Errand Boy")
  /** Errand Boy is a Beast. */
  private val suit = Suit.Beast

  /** p1 is active; p1 and p2 each hold 3 favor and 3 faceup secrets, and
    * Errand Boy stands at Ancient City with nothing on it. */
  private val arranged: ReadyGame = Table.start
    .favor(p1, 3).secrets(p1, faceUp = 3).favor(p2, 3).secrets(p2, faceUp = 3)
    .denizen(denizen, at = "Ancient City").ready

  private def replay(pay: PayCost): ReadyGame = ProcedureWalker.applyRecorded(
    Ready(arranged), WalkerStepRecorded("0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("PayCost")),
      Vector(pay),
      Vector.empty)) match
    case Right(Ready(state)) => state
    case other => fail(s"the recorded payment must replay, got $other")

  test("an off-turn payment replays settled: favor to the matching bank, a " +
      "secret facedown"):
    val done = replay(PayCost(payer, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), intoOccupied = true, matchingBank = Some(suit)))
    assertEquals(Look(done).tokensOn(denizen), Tokens.empty)
    val after = Look(done).player(payer).board
    // 3 favor less 1 paid; 3 faceup secrets, 1 of them turned facedown.
    assertEquals((after.favor, after.faceUpSecrets, after.faceDownSecrets),
      (2, 2, 1))
    assertEquals(done.banks.favor.getOrElse(suit, 0),
      arranged.banks.favor.getOrElse(suit, 0) + 1)

  test("the active player's payment replays resting on the card"):
    val done = replay(PayCost(active, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), matchingBank = Some(suit)))
    assertEquals(Look(done).tokensOn(denizen), Tokens(1, 1))
    assertEquals(Look(done).player(active).board.faceDownSecrets, 0)

  test("a recorded PayCost that only burns replays as it first ran"):
    val done = replay(PayCost(active, Location.OnCard(denizen),
      Cost(favorBurnt = 1)))
    // 3 favor less 1 burnt.
    assertEquals(Look(done).favor(active), 2)
