package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.oathkeeper.OathkeeperFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption

/** Muster and Trade through the rules, as a client drives them: start, park on
  * the source decision, answer.
  */
class EconomyWalkerSuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))
  private val card = DecisionOptionRef.Denizen(plainId)

  private def start(ready: ReadyGame, action: StartableRef = ActionRef.Muster,
      args: Vector[DecisionOptionRef] = Vector.empty) =
    rules.startWalker(Ready(ready), action, player(ready).player, startArgs = args)

  private def answer(state: OathState, actor: PlayerId, decisionId: String,
      ref: DecisionOptionRef) =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  test("starting Muster parks on the source decision and changes nothing yet") {
    val board = act()
    val actor = player(board).player
    val started = start(board).getOrElse(fail("a legal Muster must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(started.continue, OathContinue.AwaitingEconomyDecision(actor,
      DecisionId(MusterProcedure.decisionId)))
    val parked = ready(started.state)
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Muster))
    assert(parked.game.current.walkerPending.nonEmpty)
    assertEquals(player(parked).board.favor, 4)
    assertEquals(player(parked).board.supply.supply, 7)
  }

  test("answering the source decision pays, gains and completes the action") {
    val board = act(advisers = Vector(matchingAdviser))
    val actor = player(board).player
    val started = start(board).toOption.get
    val accepted = answer(started.state, actor, MusterProcedure.decisionId, card)
      .getOrElse(fail("the offered card must be accepted"))
    val after = ready(accepted.state)
    assertEquals(player(after).board.favor, 3)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.warbands, 5)
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(accepted.continue, OathContinue.ActActionSelection(actor))
    assert(accepted.events.exists {
      case WalkerCompleted(ActionRef.Muster) => true
      case _ => false
    })
  }

  test("the action boundary starts the Oathkeeper procedure within the " +
      "answering command") {
    val initial = act()
    val actor = player(initial).player
    val leader = initial.game.current.players.map(_.player).find(_ != actor).get
    val board = OathkeeperFixture.ruled(initial, Vector(Some(leader)))
    val started = start(board).toOption.get
    val accepted = answer(started.state, actor, MusterProcedure.decisionId, card)
      .toOption.get
    assertEquals(accepted.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    assertEquals(ready(accepted.state).game.current.title,
      OathkeeperState(Some(leader), TitleSide.Oathkeeper))
  }

  test("Trade carries its resource as the start selection through the park") {
    Vector(favor -> "favor", secret -> "secret").foreach { case (args, name) =>
      val board = act(advisers = Vector(matchingAdviser))
      val actor = player(board).player
      val started = start(board, ActionRef.Trade, args)
        .getOrElse(fail(s"Trade for $name must start"))
      assertEquals(ready(started.state).game.current.walkerStartArgs, args)
      assertEquals(started.continue, OathContinue.AwaitingEconomyDecision(actor,
        DecisionId(TradeProcedure.decisionId)))
      val accepted = answer(started.state, actor, TradeProcedure.decisionId, card)
        .getOrElse(fail(s"Trade for $name must complete"))
      assertEquals(ready(accepted.state).game.current.walkerPending, None)
    }
  }

  test("a start with nothing playable is rejected before anything is persisted") {
    assertEquals(start(act(favor = 0)).left.toOption,
      Some(NoPlayableOption("muster")))
    assertEquals(start(act(secrets = 0), ActionRef.Trade, favor).left.toOption,
      Some(NoPlayableOption("trade")))
    assertEquals(start(act(supply = 0)).left.toOption,
      Some(NoPlayableOption("muster")))
  }

  test("a start with no token-free card, or a wrong selection, is rejected") {
    assert(start(act(tokens = Tokens(0, 1))).isLeft)
    assert(start(act(), ActionRef.Muster, favor).isLeft)
    assert(start(act(), ActionRef.Trade).isLeft)
    assert(start(act(), ActionRef.Trade,
      Vector(DecisionOptionRef.Button("gold"))).isLeft)
  }

  test("only the actor can answer, and only with an offered card") {
    val board = act(advisers = Vector(matchingAdviser))
    val actor = player(board).player
    val other = board.game.current.players.map(_.player).find(_ != actor).get
    val started = start(board).toOption.get
    assert(answer(started.state, other, MusterProcedure.decisionId, card).isLeft)
    assert(answer(started.state, actor, MusterProcedure.decisionId,
      DecisionOptionRef.Denizen(matchingId)).isLeft)
    assert(answer(started.state, actor, MusterProcedure.decisionId, card).isRight)
  }

  test("a lineage with no warband supply cannot Muster") {
    val board = act()
    val actor = player(board)
    val malformed = board.copy(banks = board.banks.copy(warbandSupply =
      board.banks.warbandSupply - ForceKind.Exile(actor.lineage)))
    assert(start(malformed).isLeft)
  }

  test("an unimplemented optional Economy power does not block a base Trade") {
    val board = spring(act(), EdificeSide.Intact)
    val actor = player(board).player
    val started = start(board, ActionRef.Trade, secret)
      .getOrElse(fail("the Trade must start"))
    val finished = answer(started.state, actor, TradeProcedure.decisionId,
      DecisionOptionRef.Edifice(springId))
    assert(finished.isRight, finished.toString)
  }
}
