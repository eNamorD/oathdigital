package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent.BanditsRefilled
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
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

  test("Silver Tongue requires replacement when a third adviser is played") {
    val (base, actor) = arranged(Vector.empty, Set.empty)
    val current = base.game.current
    val ids = current.commonCards.worldDeck.collect { case id: DenizenId => id }
      .filter(id => catalog.denizens.find(_.id.value == id.value).exists(
        _.restrictions == oathdigital.catalog.CardRestrictions.Unrestricted))
    val second = ids.head
    val played = ids(1)
    val ready = base.copy(game = base.game.copy(current = current.copy(
      turn = current.turn.copy(phase = Phase.Act),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = p.advisers :+ DenizenState(second,
          Orientation.FaceDown, Tokens.empty)) else p),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id => id == second || id == played)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(played)))))
    val tree = CardPlayProcedure.build(catalog, ready, actor, played,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val powers = WalkerPowers(Vector(SilverTongue.forCatalog(catalog).get))
    val parked = ProcedureWalker.advance(ready, tree, None, powers).toOption.get
      .asInstanceOf[WalkerOutcome.Parked].tree
    val choice = ProcedureWalker.parkedDecide(ready, tree, parked, powers).get
    assert(choice.query.asInstanceOf[DecisionQuery.ChooseOne].options.exists(
      _.ref == DecisionOptionRef.Button("adviser-faceup")))
    val afterChoice = ProcedureWalker.resolve(ready, tree, parked,
      Answered(choice.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("adviser-faceup")), actor), powers).toOption.get
    val replacement = afterChoice.asInstanceOf[WalkerOutcome.Parked].tree
    val query = ProcedureWalker.parkedDecide(ready, tree, replacement, powers).get
    assert(query.query.asInstanceOf[DecisionQuery.ChooseOne].options.exists(
      _.ref == DecisionOptionRef.Denizen(second)))
  }
}
