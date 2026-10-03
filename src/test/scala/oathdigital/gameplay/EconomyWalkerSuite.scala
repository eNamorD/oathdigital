package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted, WalkerParked}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

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
    * (Arcane) and nothing else. p1 has 4 favor, 2 faceup secrets, 7 Supply
    * and 3 warbands on the board. */
  private def atAlchemist: Table = Table.start
    .denizen(alchemist, at = "Ancient City")
    .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, 7).boardWarbands(p1, 3)

  /** Magician's Code is Arcane, like Alchemist. */
  private def withMatchingAdviser: Table = atAlchemist.adviser(p1, "Magician's Code")

  private def start(ready: ReadyGame, action: StartableRef = ActionRef.Muster,
      args: Vector[DecisionOptionRef] = Vector.empty) =
    rules.startWalker(Ready(ready), action, p1, startArgs = args)

  private def answer(state: OathState, actor: PlayerId, decisionId: String,
      ref: DecisionOptionRef) =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  private def current(state: OathState): CurrentGameState =
    Outcomes.ready(state).game.current

  test("starting Muster parks on the source decision and changes nothing yet"):
    val started = start(atAlchemist.ready)
      .getOrElse(fail("a legal Muster must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    parked.assertParked(started.state, ActionRef.Muster,
      MusterProcedure.decisionId, p1)
    assertEquals(current(started.state).walkerProcedure, Some(ActionRef.Muster))
    assert(current(started.state).walkerPending.nonEmpty)
    // Still the 4 favor and 7 Supply p1 started with.
    assertEquals(Look(started.state).favor(p1), 4)
    assertEquals(Look(started.state).supply(p1), 7)

  test("answering the source decision pays, gains and completes the action"):
    val started = accepted(start(withMatchingAdviser.ready))
    val answered = accepted(
      answer(started.state, p1, MusterProcedure.decisionId, card))
    val after = Look(answered.state)
    // 1 favor goes onto Alchemist and Muster costs 1 Supply. It gains one
    // warband, plus one for the matching adviser, Magician's Code.
    assertEquals(after.favor(p1), 4 - 1)
    assertEquals(after.supply(p1), 7 - 1)
    assertEquals(after.boardWarbands(p1), 3 + 2)
    assertEquals(current(answered.state).walkerPending, None)
    parked.assertResumed(answered.state, Phase.Act, p1)
    assert(answered.events.exists {
      case WalkerCompleted(ActionRef.Muster) => true
      case _ => false
    })

  test("the action boundary starts the Oathkeeper procedure within the " +
      "answering command"):
    // p2 rules a site and p1 none, so p2 leads the Oath.
    val started = accepted(start(atAlchemist.warbandsAt("Dunes", p2, 1).ready))
    val answered = accepted(
      answer(started.state, p1, MusterProcedure.decisionId, card))
    assertEquals(answered.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    assertEquals(current(answered.state).title,
      OathkeeperState(Some(p2), TitleSide.Oathkeeper))

  test("Trade carries its resource as the start selection through the park"):
    Vector(favor -> "favor", secret -> "secret").foreach { case (args, name) =>
      val started = start(withMatchingAdviser.ready, ActionRef.Trade, args)
        .getOrElse(fail(s"Trade for $name must start"))
      assertEquals(current(started.state).walkerStartArgs, args)
      parked.assertParked(started.state, ActionRef.Trade,
        TradeProcedure.decisionId, p1)
      val answered = answer(started.state, p1, TradeProcedure.decisionId, card)
        .getOrElse(fail(s"Trade for $name must complete"))
      assertEquals(current(answered.state).walkerPending, None)
    }

  test("a start with nothing playable is rejected before anything is persisted"):
    // The search finds no legal path from any source, so the start is refused
    // with the first rejection it met rather than parking on a decision.
    assert(start(atAlchemist.favor(p1, 0).ready).isLeft)
    assert(start(atAlchemist.secrets(p1, faceUp = 0).ready, ActionRef.Trade,
      favor).isLeft)
    assert(start(atAlchemist.supply(p1, 0).ready).isLeft)

  test("a start with no token-free card, or a wrong selection, is rejected"):
    assertEquals(start(atAlchemist.tokens(alchemist, secrets = 1).ready)
      .left.toOption, Some(OathViolation.NoPlayableOption("muster")))
    assertEquals(start(atAlchemist.ready, ActionRef.Muster, favor).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "walker procedure muster takes no start selection, got button")))
    assertEquals(start(atAlchemist.ready, ActionRef.Trade).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "trade takes exactly one resource button, favor or secret, as its " +
          "start selection, got ")))
    assertEquals(start(atAlchemist.ready, ActionRef.Trade,
      Vector(DecisionOptionRef.Button("gold"))).left.toOption, Some(OathViolation.InvalidEventOrder(
        "trade takes exactly one resource button, favor or secret, as its " +
          "start selection, got button/gold")))

  test("only the actor can answer, and only with an offered card"):
    val started = accepted(start(withMatchingAdviser.ready))
    assertEquals(answer(started.state, p2, MusterProcedure.decisionId, card)
      .left.toOption, Some(OathViolation.WrongPlayer(p1, p2)))
    assertEquals(answer(started.state, p1, MusterProcedure.decisionId,
      DecisionOptionRef.Denizen(CatalogNames.denizen("Magician's Code")))
      .left.toOption, Some(OathViolation.InvalidEventOrder(
        "decision muster.source does not offer the selected option")))
    assert(answer(started.state, p1, MusterProcedure.decisionId, card).isRight)

  test("an unimplemented optional Economy power does not block a base Trade"):
    val board = Table.start.favor(p1, 4).secrets(p1, faceUp = 2)
      .edifice("Hallowed Spring", EdificeSide.Intact, at = "Ancient City").ready
    val started = start(board, ActionRef.Trade, secret)
      .getOrElse(fail("the Trade must start"))
    val finished = answer(started.state, p1, TradeProcedure.decisionId,
      DecisionOptionRef.Edifice(CatalogNames.edifice("Hallowed Spring")))
    // The base Trade runs to its end: nothing is left pending.
    assertEquals(current(accepted(finished).state).walkerPending, None)
