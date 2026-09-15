package oathdigital.gameplay

import oathdigital.gameplay.OathEvent.BanditsRefilled
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.operations.Decide
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Synthetic WAKE, ACTION and REST powers on a faceup adviser, injected
  * through `OathRules`, drive the generic phase power path end to end.
  */
class PhasePowerSuite extends munit.FunSuite {
  import PhasePowerFixture._

  private def rules(power: PhasePower) =
    new OathRules(catalog, phasePowerCatalog = PhasePowers(Vector(power)))
  private def use(power: PhasePower, state: OathState, by: PlayerId = actor) =
    rules(power).startWalker(state, ActionRef.UsePower(power.id), by,
      Vector.empty, Vector(source))
  private def ready(state: OathState) = state.asInstanceOf[Ready].value

  test("a WAKE power is usable only in Wake, once per source, and runs the " +
      "action boundary") {
    val power = TestPower(powerId, PowerTiming.Wake)
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, inPhase(Phase.Wake), actor,
      powers), Vector(PhasePowerProcedure.PowerSource(power, card, source)))
    assertEquals(PhasePowerProcedure.usable(catalog, inPhase(Phase.Act), actor,
      powers), Vector.empty)

    val used = use(power, Ready(inPhase(Phase.Wake))).toOption.get
    val ref = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card), powerId)
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    assertEquals(used.continue, OathContinue.AwaitingWakeAction(actor))
    assert(ready(used.state).game.current.turn.usedPowers.contains(ref))
    assertEquals(use(power, used.state).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(ref)))
    assertEquals(PhasePowerProcedure.usable(catalog, ready(used.state), actor,
      powers), Vector.empty)
  }

  test("a use is scoped to its source card: the same power stays usable " +
      "from a second card") {
    val current = base.game.current
    val second = current.commonCards.worldDeck.collectFirst {
      case id: DenizenId if id != card => id
    }.get
    val printed = catalog.denizens.find(_.id.value == card.value).get
      .powers.find(_.id == powerId).get
    val twice = catalog.copy(denizens = catalog.denizens.map(d =>
      if (d.id.value == second.value) d.copy(powers = d.powers :+ printed)
      else d))
    val usedFromFirst = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card),
      powerId)
    val state = base.copy(game = base.game.copy(current = current.copy(
      turn = TurnState(actor, Phase.Wake, Set(usedFromFirst)),
      players = current.players.map(p => if (p.player != actor) p else
        p.copy(advisers = p.advisers :+ DenizenState(second,
          Orientation.FaceUp, Tokens.empty))),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == second)))))
    val power = TestPower(powerId, PowerTiming.Wake)
    val secondSource = DecisionOptionRef.Denizen(second)
    assertEquals(PhasePowerProcedure.check(twice, state, actor, power, source),
      Left(OathViolation.PowerAlreadyUsed(usedFromFirst)))
    assertEquals(PhasePowerProcedure.check(twice, state, actor, power,
      secondSource), Right(second))
    assertEquals(PhasePowerProcedure.usable(twice, state, actor,
      PhasePowers(Vector(power))).map(_.ref), Vector(secondSource))
  }

  test("an ACTION power returns its player to action selection") {
    val used = use(TestPower(powerId, PowerTiming.Act), Ready(inPhase(Phase.Act)))
      .toOption.get
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    assertEquals(used.continue, OathContinue.ActActionSelection(actor))
  }

  test("another player, the wrong phase and an inaccessible source are refused") {
    val power = TestPower(powerId, PowerTiming.Act)
    val other = base.game.current.players.map(_.player).find(_ != actor).get
    assertEquals(use(power, Ready(inPhase(Phase.Act)), other).left.toOption,
      Some(OathViolation.WrongPlayer(actor, other)))
    assert(use(power, Ready(inPhase(Phase.Wake))).isLeft)
    assert(rules(power).startWalker(Ready(inPhase(Phase.Act)),
      ActionRef.UsePower(powerId), actor, Vector.empty,
      Vector(DecisionOptionRef.Denizen(DenizenId("no-such-card")))).isLeft)
  }

  test("a card at a site the player rules is a source, and one at an unruled " +
      "site is not") {
    val power = TestPower(powerId, PowerTiming.Act)
    val current = base.game.current
    val holder = current.players.find(_.player == actor).get
    val far = current.map.inPlay.find(id => !holder.pawnSite.contains(id)).get
    def withCardAt(forces: SiteForces) = base.copy(game = base.game.copy(
      current = current.copy(
        turn = TurnState(actor, Phase.Act, Set.empty),
        players = current.players.map(p =>
          if (p.player != actor) p else p.copy(advisers = Vector.empty)),
        map = current.map.copy(sites = current.map.sites.updated(far,
          current.map.sites(far).copy(forces = forces, denizens = Vector(
            DenizenState(card, Orientation.FaceUp, Tokens.empty))))))))
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, withCardAt(
      SiteForces.Occupied(ForceKind.Exile(holder.lineage), 1)), actor, powers)
      .map(_.ref), Vector(source))
    assertEquals(PhasePowerProcedure.usable(catalog, withCardAt(
      SiteForces.Occupied(ForceKind.Bandit, 1)), actor, powers), Vector.empty)
  }

  test("a usable REST power stops the Rest auto-skip, and using it still " +
      "leaves Finish Rest to the player") {
    val power = TestPower(powerId, PowerTiming.Rest)
    val rested = rules(power).startWalker(Ready(inPhase(Phase.Act)),
      PhaseTransitionRef.BeginRest, actor).toOption.get
    assertEquals(rested.continue, OathContinue.AwaitingRestAction(actor))
    val used = use(power, rested.state).toOption.get
    assertEquals(used.continue, OathContinue.AwaitingRestAction(actor))
    val finished = rules(power).startWalker(used.state,
      PhaseTransitionRef.FinishRest, actor).toOption.get
    val next = ready(finished.state).game.current.turn
    assertNotEquals(next.activePlayer, actor)
    assertEquals(next.usedPowers, Set.empty[PowerUseRef])
  }

  test("a decision inside a power parks as a power decision and replays") {
    val choice = "test-power-choice"
    val power = TestPower(powerId, PowerTiming.Act, player => Decide(choice,
      player, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"),
        DecisionOption.Button(DecisionOptionRef.Button("stop"), "Stop")))))
    val parked = use(power, Ready(inPhase(Phase.Act))).toOption.get
    assertEquals(parked.continue,
      OathContinue.AwaitingPowerDecision(actor, DecisionId(choice)))
    val done = rules(power).resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("go"))).toOption.get
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals((parked.events ++ done.events)
      .foldLeft[Either[OathViolation, OathState]](Right(Ready(inPhase(Phase.Act))))(
        (state, event) => state.flatMap(rules(power).evolve(_, event))),
      Right(done.state))
  }
}
