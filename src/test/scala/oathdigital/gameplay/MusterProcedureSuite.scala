package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, MusterSource}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

class MusterProcedureSuite extends munit.FunSuite:
  import EconomyFixture.{AddSource, AskAtGain, FreePayment}

  private val alchemist = CatalogNames.denizen("Alchemist")
  private val magiciansCode = CatalogNames.denizen("Magician's Code")
  private val hallowedSpring = CatalogNames.edifice("Hallowed Spring")

  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")

  /** p1 stands at Ancient City, which holds the token-free Alchemist
    * (Arcane) and nothing else; p1 has 4 favor, 7 Supply and 3 warbands on
    * the board. */
  private def atAlchemist: Table = Table.start
    .pawn(p1, at = ancientCity)
    .denizen(alchemist, at = ancientCity)
    .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)

  /** The Muster tree, and the walk parked on its source decision. */
  private final case class ParkedMuster(tree: Operation, pending: PendingTree)

  private def parked(ready: ReadyGame,
      powers: WalkerPowers = WalkerPowers.empty): ParkedMuster =
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ParkedMuster(tree, Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, powers)).tree)

  private def muster(ready: ReadyGame, ref: DecisionOptionRef,
      powers: WalkerPowers = WalkerPowers.empty): ReadyGame =
    val walk = parked(ready, powers)
    val answer = Answered(MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(ref), p1)
    Outcomes.finished(ProcedureWalker.resolve(ready, walk.tree, walk.pending,
      answer, powers)).treeless

  test("the tree parks on the token-free cards at the pawn site"):
    val ready = atAlchemist.ready
    val walk = parked(ready)
    val decide = present(ProcedureWalker.parkedDecide(ready, walk.tree,
      walk.pending, WalkerPowers.empty), "the source decision")
    assertEquals(decide.decisionId, "muster.source")
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Denizen(DecisionOptionRef.Denizen(alchemist))),
      Some("Choose a card to Muster from")))

  test("Muster costs one Supply and one favor and gains one warband per " +
      "matching adviser plus one"):
    val ready = atAlchemist.adviser(p1, magiciansCode)   // Arcane, like Alchemist
      .ready
    val after = Look(muster(ready, DecisionOptionRef.Denizen(alchemist)))
    assertEquals(after.favor(p1), 4 - 1)
    assertEquals(after.supply(p1), 7 - 1)
    assertEquals(after.boardWarbands(p1), 3 + 1 + 1)       // 1 matching, plus 1
    assertEquals(after.tokensOn(alchemist), Tokens(1, 0))

  test("the gain shrinks to the warbands the supply still holds"):
    // An Exile lineage has 14 warbands; all of them are on p1's board.
    val after = Look(muster(atAlchemist.boardWarbands(p1, 14).ready,
      DecisionOptionRef.Denizen(alchemist)))
    assertEquals(after.boardWarbands(p1), 14)
    assertEquals(after.supply(p1), 7 - 1)
    assertEquals(after.favor(p1), 4 - 1)

  test("an edifice of either side is a legal source and takes the favor"):
    Vector(EdificeSide.Ruined, EdificeSide.Intact).foreach { side =>
      val ready = Table.start.pawn(p1, at = ancientCity).favor(p1, 4)
        .edifice(hallowedSpring, side, at = ancientCity).ready
      val options = MusterProcedure.startOptions(catalog, ready, p1,
        WalkerPowers.empty).map(_.option.ref)
      assertEquals(options, Vector(DecisionOptionRef.Edifice(hallowedSpring)),
        side.toString)
      assertEquals(Look(muster(ready, DecisionOptionRef.Edifice(hallowedSpring)))
        .tokensOn(hallowedSpring), Tokens(1, 0), side.toString)
    }

  test("a card carrying tokens is shown unavailable with why, so there is " +
      "nothing to start"):
    val ready = atAlchemist.tokens(alchemist, secrets = 1).ready
    // The start itself is legal, so an empty preview is the absence of a
    // source and not a build failure swallowed by `startOptions`.
    accepted(MusterProcedure.build(catalog, ready, p1))
    val walk = parked(ready)
    val decide = present(ProcedureWalker.parkedDecide(ready, walk.tree,
      walk.pending, WalkerPowers.empty), "the source decision")
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(alchemist)),
        OptionReason.Written("Already holds favor or secrets"))),
      Some("Choose a card to Muster from")))
    assertEquals(MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers.empty), Vector.empty)
    assertEquals(MusterProcedure.startOptions(catalog, atAlchemist.ready, p1,
      WalkerPowers.empty).map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(alchemist)))

  test("a power that removes the cost lets an unaffordable Muster start"):
    val ready = atAlchemist.favor(p1, 0).ready
    // Without the power the search finds no legal path from the only source,
    // so the decision is left with nothing to offer and the start is refused.
    assertEquals(MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers.empty), Vector.empty)
    val previewed = MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers(Vector(FreePayment(PowerId("test.free-payment")))))
    assertEquals(previewed.map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(alchemist)))
    val outcome = accepted(previewed.head.outcome)
    // The power waives the favor payment, not the Supply spend.
    assertEquals(outcome.operations.collect { case SpendSupply(_, n, _) => n },
      Vector(1))
    assertEquals(outcome.operations.collect { case pay: PayCost => pay },
      Vector.empty)

  test("resolve finds a source at any site in play or among the actor's " +
      "advisers, whatever it holds"):
    val ready = atAlchemist.tokens(alchemist, secrets = 1)
      .adviser(p1, magiciansCode).denizen(wrestlers, at = brokenPeaks).ready
    assertEquals(MusterSource.resolve(catalog, ready, p1,
      DecisionOptionRef.Denizen(alchemist)), Right(MusterSource(alchemist,
        Suit.Arcane, RuleSourceRef.SiteCard(ancientCity, alchemist))))
    assertEquals(MusterSource.resolve(catalog, ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)), Right(MusterSource(
        magiciansCode, Suit.Arcane, RuleSourceRef.Adviser(p1, magiciansCode))))
    assertEquals(MusterSource.resolve(catalog, ready, p1,
      DecisionOptionRef.Denizen(wrestlers)), Right(MusterSource(wrestlers,
        Suit.Order, RuleSourceRef.SiteCard(brokenPeaks, wrestlers))))

  test("resolve names why a reference is not a source"):
    // Magician's Code is in no site and no adviser of p1's: first in the
    // deck, then another player's adviser.
    assertEquals(MusterSource.resolve(catalog, atAlchemist.ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)),
      Left(EconomyCardUnavailable(ancientCity, magiciansCode)))
    assertEquals(MusterSource.resolve(catalog,
      atAlchemist.adviser(p2, magiciansCode).ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)),
      Left(EconomyCardUnavailable(ancientCity, magiciansCode)))
    assertEquals(MusterSource.resolve(catalog, atAlchemist.ready, p1,
      DecisionOptionRef.Button("site")), Left(InvalidEventOrder(
        "button/site is not a card a Muster or Trade can draw on")))

  test("matching counts the actor's faceup advisers of the source's suit"):
    assertEquals(MusterSource.matching(catalog,
      atAlchemist.adviser(p1, magiciansCode).ready, p1, Suit.Arcane), 1)
    assertEquals(MusterSource.matching(catalog, atAlchemist.ready, p1,
      Suit.Arcane), 0)

  test("Muster cannot start outside the Act phase"):
    val ready = atAlchemist.turn(p1, Phase.Wake).ready
    assertEquals(MusterProcedure.build(catalog, ready, p1).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Act, Phase.Wake)))

  test("a power that adds the actor's adviser to the source decision has " +
      "it accepted, and the favor is placed on the adviser"):
    val ready = atAlchemist.adviser(p1, magiciansCode).ready
    val powers = WalkerPowers(Vector(AddSource(
      PowerId("test.add-source"), magiciansCode)))
    assertEquals(MusterProcedure.startOptions(catalog, ready, p1, powers)
      .map(_.option.ref), Vector(DecisionOptionRef.Denizen(alchemist),
        DecisionOptionRef.Denizen(magiciansCode)))
    val after = Look(muster(ready, DecisionOptionRef.Denizen(magiciansCode),
      powers))
    assertEquals(after.tokensOn(magiciansCode), Tokens(1, 0))
    assertEquals(after.tokensOn(alchemist), Tokens(0, 0))

  test("a Muster resumed after its cost, whose source now holds the favor, " +
      "still finds its source"):
    val rules = new OathRules(catalog, walkerPowerCatalog =
      WalkerPowers(Vector(AskAtGain(PowerId("test.ask-at-gain")))))
    val ready = atAlchemist.ready
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Muster,
      p1))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))))
    assertEquals(Look(Outcomes.ready(asked.state)).tokensOn(alchemist),
      Tokens(1, 0))
    val done = accepted(rules.resolveWalker(asked.state, p1,
      AskAtGain.decisionId, ChooseOneAnswer(AskAtGain.go)))
    // 1, and p1 holds no Arcane adviser.
    assertEquals(Look(Outcomes.ready(done.state)).boardWarbands(p1), 3 + 1)
