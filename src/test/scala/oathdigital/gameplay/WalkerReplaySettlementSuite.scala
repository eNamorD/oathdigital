package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{DeltaMeaning, ProcedureWalker, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Replay applies a recorded `PayCost` the way the pipeline ran it: a payer who
  * is not the active player settles at once, so the recorded (requested)
  * operation expands to the same moves it did when it first ran. Without this a
  * live command, whose state is the replay of its own events, would rest a
  * defender's payment on the card it was settled off.
  */
class WalkerReplaySettlementSuite extends munit.FunSuite {
  private val base = initialReady
  private val current = base.game.current
  private val active = current.turn.activePlayer
  private val payer = current.players.map(_.player).find(_ != active).get
  private val siteId = current.map.inPlay.head
  private val denizen = current.commonCards.worldDeck.collectFirst {
    case id: DenizenId => id }.get
  private val suit = catalog.suitOf(denizen).get

  private val arranged: ReadyGame = base.updateCurrent(c => c.copy(
    commonCards = c.commonCards.copy(worldDeck =
      c.commonCards.worldDeck.filterNot(_ == denizen)),
    players = c.players.map(p => p.copy(board = p.board.copy(favor = 3,
      faceUpSecrets = 3, faceDownSecrets = 0))),
    map = c.map.copy(sites = c.map.sites.updated(siteId,
      c.map.sites(siteId).copy(denizens = Vector(DenizenState(denizen,
        Orientation.FaceUp, Tokens.empty)))))))

  private def replay(pay: PayCost): ReadyGame = ProcedureWalker.applyRecorded(
    Ready(arranged), WalkerStepRecorded("0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("PayCost")),
      Vector(pay),
      Vector.empty)) match {
    case Right(Ready(state)) => state
    case other => fail(s"the recorded payment must replay, got $other")
  }

  private def tokens(state: ReadyGame): Tokens =
    state.game.current.map.sites(siteId).denizens.collectFirst {
      case held: DenizenState if held.id == denizen => held.tokens }.get

  private def board(state: ReadyGame, who: PlayerId) =
    state.game.current.players.find(_.player == who).get.board

  test("an off-turn payment replays settled: favor to the matching bank, a secret facedown") {
    val done = replay(PayCost(payer, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), intoOccupied = true, matchingBank = Some(suit)))
    assertEquals(tokens(done), Tokens.empty)
    val after = board(done, payer)
    assertEquals((after.favor, after.faceUpSecrets, after.faceDownSecrets),
      (2, 2, 1))
    assertEquals(done.banks.favor.getOrElse(suit, 0),
      arranged.banks.favor.getOrElse(suit, 0) + 1)
  }

  test("the active player's payment replays resting on the card") {
    val done = replay(PayCost(active, Location.OnCard(denizen),
      Cost(favor = 1, secret = 1), matchingBank = Some(suit)))
    assertEquals(tokens(done), Tokens(1, 1))
    assertEquals(board(done, active).faceDownSecrets, 0)
  }

  test("a recorded batch of other operations replays as before") {
    val done = replay(PayCost(active, Location.OnCard(denizen),
      Cost(favorBurnt = 1)))
    assertEquals(board(done, active).favor, 2)
  }
}
