package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TradeProcedureSuite extends munit.FunSuite:
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))
  private val alchemist = CatalogNames.denizen("Alchemist")

  /** p1 stands at Ancient City, which holds the token-free Alchemist
    * (Arcane) and nothing else. p1 has 4 favor and 2 faceup secrets, and
    * the Arcane bank holds 5. */
  private def atAlchemist: Table = Table.start
    .denizen(alchemist, at = Table.homeOf(p1))
    .favor(p1, 4).secrets(p1, faceUp = 2)
    .bankFavor(Suit.Arcane, 5)

  private def trade(ready: ReadyGame, args: Vector[DecisionOptionRef]): ReadyGame =
    val tree = TradeProcedure.build(catalog, ready, p1, args)
      .getOrElse(fail("a legal Trade must build"))
    val pending = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .getOrElse(fail("the Trade tree must walk to its source decision"))
      .asInstanceOf[WalkerOutcome.Parked].tree
    ProcedureWalker.resolve(ready, tree, pending, Answered(
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist)), p1),
      WalkerPowers.empty).getOrElse(fail("the answer must be accepted"))
      .asInstanceOf[WalkerOutcome.Finished].treeless

  test("Trade for favor moves a secret and its yield is capped by the bank"):
    val ready = atAlchemist
      .adviser(p1, "Magician's Code")       // Arcane, like Alchemist
      .bankFavor(Suit.Arcane, 1)
      .ready
    val after = trade(ready, favor)
    assertEquals(Look(after).faceUpSecrets(p1), 1)
    assertEquals(Look(after).favor(p1), 5)
    assertEquals(after.banks.favor(Suit.Arcane), 0)

  test("Trade for favor without a matching adviser yields one favor"):
    val after = trade(atAlchemist.ready, favor)
    assertEquals(Look(after).favor(p1), 5)
    assertEquals(after.banks.favor(Suit.Arcane), 4)

  test("Trade for secrets places one favor, burns one and yields the matches"):
    val ready = atAlchemist.adviser(p1, "Magician's Code").ready  // Arcane too
    val after = Look(trade(ready, secret))
    assertEquals(after.favor(p1), 2)
    assertEquals(after.faceUpSecrets(p1), 3)
    assertEquals(after.tokensOn(alchemist), Tokens(1, 0))

  test("Trade for secrets with no matching adviser yields nothing and still pays"):
    val after = Look(trade(atAlchemist.ready, secret))
    assertEquals(after.favor(p1), 2)
    assertEquals(after.faceUpSecrets(p1), 2)

  test("a Trade the actor cannot pay for is not offered at all"):
    assertEquals(TradeProcedure.startOptions(catalog,
      atAlchemist.favor(p1, 1).ready, p1, TradeResource.Secret,
      WalkerPowers.empty), Vector.empty)
    assertEquals(TradeProcedure.startOptions(catalog,
      atAlchemist.secrets(p1, faceUp = 0).ready, p1, TradeResource.Favor,
      WalkerPowers.empty), Vector.empty)

  test("the start selection must be exactly one resource button"):
    val ready = atAlchemist.ready
    assertEquals(TradeProcedure.resourceOf(favor), Right(TradeResource.Favor))
    assertEquals(TradeProcedure.resourceOf(secret), Right(TradeResource.Secret))
    Vector(Vector.empty[DecisionOptionRef],
      Vector[DecisionOptionRef](DecisionOptionRef.Button("gold")),
      Vector[DecisionOptionRef](DecisionOptionRef.Denizen(alchemist)),
      favor ++ secret).foreach { args =>
      assert(TradeProcedure.build(catalog, ready, p1, args).isLeft, args.toString)
      assert(TradeProcedure.rebuild(catalog, ready, p1, args).isLeft, args.toString)
    }

  test("an intact edifice is a legal Trade source"):
    val ready = Table.start.favor(p1, 4).secrets(p1, faceUp = 2)
      .edifice("Hallowed Spring", EdificeSide.Intact, at = Table.homeOf(p1)).ready
    assert(TradeProcedure.startOptions(catalog, ready, p1,
      TradeResource.Secret, WalkerPowers.empty).exists(_.outcome.isRight))
