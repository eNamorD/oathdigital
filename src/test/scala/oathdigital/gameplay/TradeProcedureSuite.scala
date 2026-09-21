package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

class TradeProcedureSuite extends munit.FunSuite {
  import EconomyFixture._

  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))

  private def trade(ready: ReadyGame, args: Vector[DecisionOptionRef]): ReadyGame = {
    val actor = player(ready).player
    val tree = TradeProcedure.build(catalog, ready, actor, args)
      .getOrElse(fail("a legal Trade must build"))
    val pending = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .getOrElse(fail("the Trade tree must walk to its source decision"))
      .asInstanceOf[WalkerOutcome.Parked].tree
    ProcedureWalker.resolve(ready, tree, pending, Answered(
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(plainId)), actor),
      WalkerPowers.empty).getOrElse(fail("the answer must be accepted"))
      .asInstanceOf[WalkerOutcome.Finished].treeless
  }

  private def siteTokens(ready: ReadyGame): Tokens =
    ready.game.current.map.sites(player(ready).pawnSite.get).denizens.head.tokens

  test("Trade for favor moves a secret and its yield is capped by the bank") {
    val after = trade(act(advisers = Vector(matchingAdviser), bank = 1), favor)
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(player(after).board.favor, 5)
    assertEquals(after.banks.favor(plain.suit), 0)
  }

  test("Trade for favor without a matching adviser yields one favor") {
    val after = trade(act(), favor)
    assertEquals(player(after).board.favor, 5)
    assertEquals(after.banks.favor(plain.suit), 4)
  }

  test("Trade for secrets places one favor, burns one and yields the matches") {
    val after = trade(act(advisers = Vector(matchingAdviser)), secret)
    assertEquals(player(after).board.favor, 2)
    assertEquals(player(after).board.faceUpSecrets, 3)
    assertEquals(siteTokens(after), Tokens(1, 0))
  }

  test("Trade for secrets with no matching adviser yields nothing and still pays") {
    val after = trade(act(), secret)
    assertEquals(player(after).board.favor, 2)
    assertEquals(player(after).board.faceUpSecrets, 2)
  }

  test("a Trade the actor cannot pay for is previewed as dropped") {
    val poor = act(favor = 1)
    val previewed = TradeProcedure.startOptions(catalog, poor,
      player(poor).player, TradeResource.Secret, WalkerPowers.empty)
    assert(previewed.nonEmpty && previewed.forall(_.outcome.isLeft))
    val noSecrets = act(secrets = 0)
    val unaffordable = TradeProcedure.startOptions(catalog, noSecrets,
      player(noSecrets).player, TradeResource.Favor, WalkerPowers.empty)
    assert(unaffordable.nonEmpty && unaffordable.forall(_.outcome.isLeft))
  }

  test("the start selection must be exactly one resource button") {
    val ready = act()
    val actor = player(ready).player
    assertEquals(TradeProcedure.resourceOf(favor), Right(TradeResource.Favor))
    assertEquals(TradeProcedure.resourceOf(secret), Right(TradeResource.Secret))
    Vector(Vector.empty[DecisionOptionRef],
      Vector[DecisionOptionRef](DecisionOptionRef.Button("gold")),
      Vector[DecisionOptionRef](DecisionOptionRef.Denizen(plainId)),
      favor ++ secret).foreach { args =>
      assert(TradeProcedure.build(catalog, ready, actor, args).isLeft, args.toString)
      assert(TradeProcedure.rebuild(catalog, ready, actor, args).isLeft, args.toString)
    }
  }

  test("an intact edifice is a legal Trade source") {
    val ready = spring(act(), EdificeSide.Intact)
    assert(TradeProcedure.startOptions(catalog, ready, player(ready).player,
      TradeResource.Secret, WalkerPowers.empty).exists(_.outcome.isRight))
  }
}
