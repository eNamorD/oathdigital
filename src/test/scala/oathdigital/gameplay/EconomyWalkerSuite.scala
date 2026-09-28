package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted, WalkerParked}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Muster and Trade through the rules, as a client drives them: start, park on
  * the source decision, answer.
  */
class EconomyWalkerSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)
  private val favor = Vector[DecisionOptionRef](DecisionOptionRef.Button("favor"))
  private val secret = Vector[DecisionOptionRef](DecisionOptionRef.Button("secret"))
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val card = DecisionOptionRef.Denizen(alchemist)

  /** p1 stands at Ancient City, which holds the token-free Alchemist
    * (Arcane) and nothing else. p1 has 4 favor and 2 faceup secrets. */
  private def atAlchemist: Table = Table.start
    .denizen(alchemist, at = Table.homeOf(p1))
    .favor(p1, 4).secrets(p1, faceUp = 2)

  /** Magician's Code is Arcane, like Alchemist. */
  private def withMatchingAdviser: Table = atAlchemist.adviser(p1, "Magician's Code")

  private def start(ready: ReadyGame, action: StartableRef = ActionRef.Muster,
      args: Vector[DecisionOptionRef] = Vector.empty) =
    rules.startWalker(Ready(ready), action, p1, startArgs = args)

  private def answer(state: OathState, actor: PlayerId, decisionId: String,
      ref: DecisionOptionRef) =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  private def current(state: OathState): CurrentGameState = state match
    case Ready(ready) => ready.game.current
    case other => fail(s"expected a ready game, got $other")

  test("starting Muster parks on the source decision and changes nothing yet"):
    val started = start(atAlchemist.ready)
      .getOrElse(fail("a legal Muster must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    parked.assertParked(started.state, ActionRef.Muster,
      MusterProcedure.decisionId, p1)
    assertEquals(current(started.state).walkerProcedure, Some(ActionRef.Muster))
    assert(current(started.state).walkerPending.nonEmpty)
    assertEquals(Look(started.state).favor(p1), 4)
    assertEquals(Look(started.state).supply(p1), 7)

  test("answering the source decision pays, gains and completes the action"):
    val started = start(withMatchingAdviser.ready).toOption.get
    val accepted = answer(started.state, p1, MusterProcedure.decisionId, card)
      .getOrElse(fail("the offered card must be accepted"))
    val after = Look(accepted.state)
    assertEquals(after.favor(p1), 3)
    assertEquals(after.supply(p1), 6)
    assertEquals(after.warbands(p1), 5)
    assertEquals(current(accepted.state).walkerPending, None)
    parked.assertResumed(accepted.state, Phase.Act, p1)
    assert(accepted.events.exists {
      case WalkerCompleted(ActionRef.Muster) => true
      case _ => false
    })

  test("the action boundary starts the Oathkeeper procedure within the " +
      "answering command"):
    // p2 rules a site and p1 none, so p2 leads the Oath.
    val started = start(atAlchemist.warbandsAt("Dunes", p2, 1).ready).toOption.get
    val accepted = answer(started.state, p1, MusterProcedure.decisionId, card)
      .toOption.get
    assertEquals(accepted.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    assertEquals(current(accepted.state).title,
      OathkeeperState(Some(p2), TitleSide.Oathkeeper))

  test("Trade carries its resource as the start selection through the park"):
    Vector(favor -> "favor", secret -> "secret").foreach { case (args, name) =>
      val started = start(withMatchingAdviser.ready, ActionRef.Trade, args)
        .getOrElse(fail(s"Trade for $name must start"))
      assertEquals(current(started.state).walkerStartArgs, args)
      parked.assertParked(started.state, ActionRef.Trade,
        TradeProcedure.decisionId, p1)
      val accepted = answer(started.state, p1, TradeProcedure.decisionId, card)
        .getOrElse(fail(s"Trade for $name must complete"))
      assertEquals(current(accepted.state).walkerPending, None)
    }

  test("a start with nothing playable is rejected before anything is persisted"):
    assertEquals(start(atAlchemist.favor(p1, 0).ready).left.toOption,
      Some(NoPlayableOption("muster")))
    assertEquals(start(atAlchemist.secrets(p1, faceUp = 0).ready,
      ActionRef.Trade, favor).left.toOption, Some(NoPlayableOption("trade")))
    assertEquals(start(atAlchemist.supply(p1, 0).ready).left.toOption,
      Some(NoPlayableOption("muster")))

  test("a start with no token-free card, or a wrong selection, is rejected"):
    assert(start(atAlchemist.tokens(alchemist, secrets = 1).ready).isLeft)
    assert(start(atAlchemist.ready, ActionRef.Muster, favor).isLeft)
    assert(start(atAlchemist.ready, ActionRef.Trade).isLeft)
    assert(start(atAlchemist.ready, ActionRef.Trade,
      Vector(DecisionOptionRef.Button("gold"))).isLeft)

  test("only the actor can answer, and only with an offered card"):
    val started = start(withMatchingAdviser.ready).toOption.get
    assert(answer(started.state, p2, MusterProcedure.decisionId, card).isLeft)
    assert(answer(started.state, p1, MusterProcedure.decisionId,
      DecisionOptionRef.Denizen(CatalogNames.denizen("Magician's Code"))).isLeft)
    assert(answer(started.state, p1, MusterProcedure.decisionId, card).isRight)

  test("a lineage with no warband supply cannot Muster"):
    val board = atAlchemist.ready
    // No step removes a lineage's printed supply: the state is malformed.
    val malformed = board.copy(banks = board.banks.copy(warbandSupply =
      board.banks.warbandSupply - ForceKind.Exile(Look(board).player(p1).lineage)))
    assert(start(malformed).isLeft)

  test("an unimplemented optional Economy power does not block a base Trade"):
    val board = Table.start.favor(p1, 4).secrets(p1, faceUp = 2)
      .edifice("Hallowed Spring", EdificeSide.Intact, at = Table.homeOf(p1)).ready
    val started = start(board, ActionRef.Trade, secret)
      .getOrElse(fail("the Trade must start"))
    val finished = answer(started.state, p1, TradeProcedure.decisionId,
      DecisionOptionRef.Edifice(CatalogNames.edifice("Hallowed Spring")))
    assert(finished.isRight, finished.toString)
