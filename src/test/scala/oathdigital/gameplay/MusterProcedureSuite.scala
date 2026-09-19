package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, MusterSource}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathViolation._

class MusterProcedureSuite extends munit.FunSuite {
  import EconomyFixture._

  private def parked(ready: ReadyGame): (Operation, PendingTree) = {
    val tree = MusterProcedure.build(catalog, ready, player(ready).player)
      .getOrElse(fail("a legal Muster must build"))
    val outcome = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .getOrElse(fail("the Muster tree must walk to its source decision"))
    (tree, outcome.asInstanceOf[WalkerOutcome.Parked].tree)
  }

  private def muster(ready: ReadyGame, ref: DecisionOptionRef): ReadyGame = {
    val (tree, pending) = parked(ready)
    ProcedureWalker.resolve(ready, tree, pending, Answered(
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(ref),
      player(ready).player), WalkerPowers.empty)
      .getOrElse(fail("the answer must be accepted"))
      .asInstanceOf[WalkerOutcome.Finished].treeless
  }

  private def siteTokens(ready: ReadyGame): Tokens =
    ready.game.current.map.sites(player(ready).pawnSite.get).denizens.head.tokens

  test("the tree parks on the token-free cards at the pawn site") {
    val ready = act()
    val (tree, pending) = parked(ready)
    val decide = ProcedureWalker.parkedDecide(ready, tree, pending,
      WalkerPowers.empty).getOrElse(fail("the walk must park on a decision"))
    assertEquals(decide.decisionId, "muster.source")
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Denizen(DecisionOptionRef.Denizen(plainId))),
      Some("Choose a card to Muster from")))
  }

  test("Muster costs one Supply and one favor and gains one warband per " +
      "matching adviser plus one") {
    val after = muster(act(advisers = Vector(matchingAdviser)),
      DecisionOptionRef.Denizen(plainId))
    assertEquals(player(after).board.favor, 3)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.warbands, 5)
    assertEquals(siteTokens(after), Tokens(1, 0))
  }

  test("the gain shrinks to the warbands the supply still holds") {
    val after = muster(act(boardWarbands = 14), DecisionOptionRef.Denizen(plainId))
    assertEquals(player(after).board.warbands, 14)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.favor, 3)
  }

  test("an edifice of either side is a legal source and takes the favor") {
    Vector(EdificeSide.Ruined, EdificeSide.Intact).foreach { side =>
      val ready = spring(act(), side)
      val options = MusterProcedure.startOptions(catalog, ready,
        player(ready).player, WalkerPowers.empty).map(_.option.ref)
      assertEquals(options, Vector(DecisionOptionRef.Edifice(springId)), side.toString)
      assertEquals(siteTokens(muster(ready, DecisionOptionRef.Edifice(springId))),
        Tokens(1, 0), side.toString)
    }
  }

  test("a card carrying tokens is not offered, so there is nothing to start") {
    val ready = act(tokens = Tokens(0, 1))
    val actor = player(ready).player
    // The start itself is legal, so an empty preview is the absence of a
    // source and not a build failure swallowed by `startOptions`.
    assert(MusterProcedure.build(catalog, ready, actor).isRight)
    assertEquals(MusterProcedure.startOptions(catalog, ready, actor,
      WalkerPowers.empty), Vector.empty)
    assert(MusterProcedure.startOptions(catalog, act(), player(act()).player,
      WalkerPowers.empty).nonEmpty)
  }

  test("a power that removes the cost lets an unaffordable Muster start") {
    val ready = act(favor = 0)
    val actor = player(ready).player
    assert(MusterProcedure.startOptions(catalog, ready, actor,
      WalkerPowers.empty).forall(_.outcome.isLeft))
    val previewed = MusterProcedure.startOptions(catalog, ready, actor,
      WalkerPowers(Vector(FreePayment(PowerId("test.free-payment")))))
    assertEquals(previewed.map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(plainId)))
    previewed.head.outcome match {
      case Right(outcome) =>
        assertEquals(outcome.operations.collect { case SpendSupply(_, n, _) => n },
          Vector(1))
        assert(!outcome.operations.exists(_.isInstanceOf[PayCost]))
      case Left(error) => fail(s"the free Muster must be playable: $error")
    }
  }

  test("an option the actor cannot pay for is previewed as dropped") {
    val ready = act(favor = 0)
    val previewed = MusterProcedure.startOptions(catalog, ready,
      player(ready).player, WalkerPowers.empty)
    assertEquals(previewed.map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(plainId)))
    assert(previewed.forall(_.outcome.isLeft))
  }

  test("resolve names why a reference is not a source") {
    val actor = player(act()).player
    val siteId = player(act()).pawnSite.get
    assertEquals(MusterSource.resolve(catalog, act(tokens = Tokens(0, 1)), actor,
      DecisionOptionRef.Denizen(plainId)), Left(EconomyCardNotEmpty(plainId)))
    assertEquals(MusterSource.resolve(catalog, act(), actor,
      DecisionOptionRef.Denizen(matchingId)),
      Left(EconomyCardUnavailable(siteId, matchingId)))
    assert(MusterSource.resolve(catalog, act(), actor,
      DecisionOptionRef.Button("site")).isLeft)
  }

  test("matching counts the actor's faceup advisers of the source's suit") {
    val ready = act(advisers = Vector(matchingAdviser))
    assertEquals(MusterSource.matching(catalog, ready, player(ready).player,
      plain.suit), 1)
    assertEquals(MusterSource.matching(catalog, act(), player(act()).player,
      plain.suit), 0)
  }

  test("a board whose site forces name an unknown lineage cannot start") {
    val ready = act()
    val siteId = player(ready).pawnSite.get
    val broken = ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(siteId,
        current.map.sites(siteId).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(LineageId("ghost")), 1))))))
    assert(MusterProcedure.build(catalog, broken, player(broken).player).isLeft)
  }

  test("Muster cannot start outside the Act phase") {
    val ready = act().updateCurrent(current =>
      current.copy(turn = current.turn.copy(phase = Phase.Wake)))
    assert(MusterProcedure.build(catalog, ready, player(ready).player).isLeft)
  }

  test("a power that adds an option to the source decision has it previewed, " +
      "and this slice's acceptance rule drops it") {
    val ready = act(advisers = Vector(matchingAdviser))
    val previewed = MusterProcedure.startOptions(catalog, ready,
      player(ready).player, WalkerPowers(Vector(AddAdviserSource(
        PowerId("test.add-adviser-source")))))
    assertEquals(previewed.map(_.option.ref), Vector(
      DecisionOptionRef.Denizen(plainId), DecisionOptionRef.Denizen(matchingId)))
    assert(previewed.head.outcome.isRight)
    previewed.last.outcome match {
      case Left(_: EconomyCardUnavailable) => ()
      case other => fail(s"expected the acceptance rule to drop it, got $other")
    }
  }
}
