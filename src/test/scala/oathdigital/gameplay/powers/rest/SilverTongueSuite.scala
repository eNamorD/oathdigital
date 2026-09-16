package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent.BanditsRefilled
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.operations.{Location, Move, Piece,
  PositionedLocation, Sequence}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class SilverTongueSuite extends munit.FunSuite {
  import SilverTongueFixture._
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val use = ActionRef.UsePower(SilverTongue.id)
  private val source = DecisionOptionRef.Denizen(tongue)
  private def favor(state: OathState) = state.asInstanceOf[Ready].value.banks.favor

  test("one matching bank with favor gives one favor without a decision, " +
      "once per turn, and runs the action boundary") {
    val (ready, actor) = arranged(Vector(Suit.Arcane, Suit.Nomad), Set(Suit.Arcane))
    val used = rules.startWalker(Ready(ready), use, actor, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(used.continue, OathContinue.AwaitingRestAction(actor))
    assertEquals(favor(used.state)(Suit.Arcane), 2)
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(tongue),
      SilverTongue.id)
    assertEquals(rules.startWalker(used.state, use, actor, Vector.empty,
      Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))
  }

  test("several matching banks with favor ask the player to choose one of them") {
    val (ready, actor) = arranged(Vector(Suit.Arcane, Suit.Nomad),
      Set(Suit.Arcane, Suit.Nomad, Suit.Order))
    val choice = SilverTongue.choiceDecisionId(ready, actor)
    val parked = rules.startWalker(Ready(ready), use, actor, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(parked.continue,
      OathContinue.AwaitingPowerDecision(actor, DecisionId(choice)))
    assert(rules.resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .isLeft)
    val taken = rules.resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Nomad)))
      .toOption.get
    assertEquals(favor(taken.state)(Suit.Nomad), 2)
    assertEquals(favor(taken.state)(Suit.Arcane), 3)
    assertEquals(taken.continue, OathContinue.AwaitingRestAction(actor))
  }

  test("without matching favor Silver Tongue is not usable and Rest skips ahead") {
    val (ready, actor) = arranged(Vector(Suit.Arcane), Set(Suit.Nomad))
    assertEquals(PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    val act = ready.copy(game = ready.game.copy(current = ready.game.current
      .copy(turn = TurnState(actor, Phase.Act, Set.empty))))
    val rested = rules.startWalker(Ready(act), PhaseTransitionRef.BeginRest,
      actor).toOption.get
    assert(rested.continue.isInstanceOf[OathContinue.AwaitingWakeAction],
      rested.continue.toString)
  }

  test("adviser limit counts visible card moves into and out of the holder's area") {
    val (ready, actor) = arranged(Vector(Suit.Arcane), Set(Suit.Arcane))
    val holder = ready.game.current.players.find(_.player == actor).get
    val hand = PositionedLocation(Location.Hand(actor))
    val area = PositionedLocation(Location.PlayArea(actor))
    val first = DenizenId("10")
    val second = DenizenId("11")
    val overLimit = Sequence(Vector(
      Move(Piece.Card(first), hand, area),
      Move(Piece.Card(second), hand, area)))
    val backAtLimit = Sequence(Vector(
      Move(Piece.Card(first), hand, area),
      Move(Piece.Card(second), hand, area),
      Move(Piece.Card(first), area, hand)))

    assertEquals(SilverTongue.advisersAfter(holder, overLimit), 3)
    assertEquals(SilverTongue.advisersAfter(holder, backAtLimit), 2)
  }
}
