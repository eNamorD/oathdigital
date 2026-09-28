package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, MusterSource}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathViolation._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class MusterProcedureSuite extends munit.FunSuite:
  import EconomyFixture.{AddAdviserSource, FreePayment}

  private val alchemist = CatalogNames.denizen("Alchemist")
  private val magiciansCode = CatalogNames.denizen("Magician's Code")
  private val hallowedSpring = CatalogNames.edifice("Hallowed Spring")

  /** p1 stands at Ancient City, which holds the token-free Alchemist
    * (Arcane) and nothing else; p1 has 4 favor. */
  private def atAlchemist: Table = Table.start
    .denizen(alchemist, at = Table.homeOf(p1))
    .favor(p1, 4)

  private def parked(ready: ReadyGame): (Operation, PendingTree) =
    val tree = MusterProcedure.build(catalog, ready, p1)
      .getOrElse(fail("a legal Muster must build"))
    val outcome = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .getOrElse(fail("the Muster tree must walk to its source decision"))
    (tree, outcome.asInstanceOf[WalkerOutcome.Parked].tree)

  private def muster(ready: ReadyGame, ref: DecisionOptionRef): ReadyGame =
    val (tree, pending) = parked(ready)
    ProcedureWalker.resolve(ready, tree, pending, Answered(
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(ref), p1),
      WalkerPowers.empty)
      .getOrElse(fail("the answer must be accepted"))
      .asInstanceOf[WalkerOutcome.Finished].treeless

  test("the tree parks on the token-free cards at the pawn site"):
    val ready = atAlchemist.ready
    val (tree, pending) = parked(ready)
    val decide = ProcedureWalker.parkedDecide(ready, tree, pending,
      WalkerPowers.empty).getOrElse(fail("the walk must park on a decision"))
    assertEquals(decide.decisionId, "muster.source")
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Denizen(DecisionOptionRef.Denizen(alchemist))),
      Some("Choose a card to Muster from")))

  test("Muster costs one Supply and one favor and gains one warband per " +
      "matching adviser plus one"):
    val ready = atAlchemist.adviser(p1, magiciansCode)   // Arcane, like Alchemist
      .ready
    val after = Look(muster(ready, DecisionOptionRef.Denizen(alchemist)))
    assertEquals(after.favor(p1), 3)
    assertEquals(after.supply(p1), 6)
    assertEquals(after.warbands(p1), 5)                  // 3 + 1 matching + 1
    assertEquals(after.tokensOn(alchemist), Tokens(1, 0))

  test("the gain shrinks to the warbands the supply still holds"):
    // An Exile lineage has 14 warbands; all of them are on p1's board.
    val after = Look(muster(atAlchemist.warbands(p1, 14).ready,
      DecisionOptionRef.Denizen(alchemist)))
    assertEquals(after.warbands(p1), 14)
    assertEquals(after.supply(p1), 6)
    assertEquals(after.favor(p1), 3)

  test("an edifice of either side is a legal source and takes the favor"):
    Vector(EdificeSide.Ruined, EdificeSide.Intact).foreach { side =>
      val ready = Table.start.favor(p1, 4)
        .edifice(hallowedSpring, side, at = Table.homeOf(p1)).ready
      val options = MusterProcedure.startOptions(catalog, ready, p1,
        WalkerPowers.empty).map(_.option.ref)
      assertEquals(options, Vector(DecisionOptionRef.Edifice(hallowedSpring)),
        side.toString)
      assertEquals(Look(muster(ready, DecisionOptionRef.Edifice(hallowedSpring)))
        .tokensOn(hallowedSpring), Tokens(1, 0), side.toString)
    }

  test("a card carrying tokens is not offered, so there is nothing to start"):
    val ready = atAlchemist.tokens(alchemist, secrets = 1).ready
    // The start itself is legal, so an empty preview is the absence of a
    // source and not a build failure swallowed by `startOptions`.
    assert(MusterProcedure.build(catalog, ready, p1).isRight)
    assertEquals(MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers.empty), Vector.empty)
    assert(MusterProcedure.startOptions(catalog, atAlchemist.ready, p1,
      WalkerPowers.empty).nonEmpty)

  test("a power that removes the cost lets an unaffordable Muster start"):
    val ready = atAlchemist.favor(p1, 0).ready
    // Without the power the option is still listed, previewed as dropped.
    val unpaid = MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers.empty)
    assertEquals(unpaid.map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(alchemist)))
    assert(unpaid.forall(_.outcome.isLeft))
    val previewed = MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers(Vector(FreePayment(PowerId("test.free-payment")))))
    assertEquals(previewed.map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(alchemist)))
    previewed.head.outcome match
      case Right(outcome) =>
        assertEquals(outcome.operations.collect { case SpendSupply(_, n, _) => n },
          Vector(1))
        assert(!outcome.operations.exists(_.isInstanceOf[PayCost]))
      case Left(error) => fail(s"the free Muster must be playable: $error")

  test("resolve names why a reference is not a source"):
    assertEquals(MusterSource.resolve(catalog,
      atAlchemist.tokens(alchemist, secrets = 1).ready, p1,
      DecisionOptionRef.Denizen(alchemist)), Left(EconomyCardNotEmpty(alchemist)))
    assertEquals(MusterSource.resolve(catalog, atAlchemist.ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)),
      Left(EconomyCardUnavailable(Table.homeOf(p1), magiciansCode)))
    assert(MusterSource.resolve(catalog, atAlchemist.ready, p1,
      DecisionOptionRef.Button("site")).isLeft)

  test("matching counts the actor's faceup advisers of the source's suit"):
    assertEquals(MusterSource.matching(catalog,
      atAlchemist.adviser(p1, magiciansCode).ready, p1, Suit.Arcane), 1)
    assertEquals(MusterSource.matching(catalog, atAlchemist.ready, p1,
      Suit.Arcane), 0)

  test("Muster cannot start outside the Act phase"):
    val ready = atAlchemist.turn(p1, Phase.Wake).ready
    assertEquals(MusterProcedure.build(catalog, ready, p1).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Act, Phase.Wake)))

  test("a power that adds an option to the source decision has it previewed, " +
      "and this slice's acceptance rule drops it"):
    val ready = atAlchemist.adviser(p1, magiciansCode).ready
    val previewed = MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers(Vector(AddAdviserSource(
        PowerId("test.add-adviser-source"), magiciansCode))))
    assertEquals(previewed.map(_.option.ref), Vector(
      DecisionOptionRef.Denizen(alchemist),
      DecisionOptionRef.Denizen(magiciansCode)))
    assert(previewed.head.outcome.isRight)
    previewed.last.outcome match
      case Left(_: EconomyCardUnavailable) => ()
      case other => fail(s"expected the acceptance rule to drop it, got $other")
