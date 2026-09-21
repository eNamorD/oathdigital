package oathdigital.gameplay

import oathdigital.model.OathEvent.BanditsRefilled
import oathdigital.model.OathState.Ready
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
      powers), Vector(PhasePowerProcedure.PowerSource(power, PowerSourceRef.Card(card),
        source)))
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
    val state = base.updateCurrent(_.copy(
      turn = TurnState(actor, Phase.Wake, Set(usedFromFirst)),
      players = current.players.map(p => if (p.player != actor) p else
        p.copy(advisers = p.advisers :+ DenizenState(second,
          Orientation.FaceUp, Tokens.empty))),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == second))))
    val power = TestPower(powerId, PowerTiming.Wake)
    val secondSource = DecisionOptionRef.Denizen(second)
    assertEquals(PhasePowerProcedure.check(twice, state, actor, power, source),
      Left(OathViolation.PowerAlreadyUsed(usedFromFirst)))
    assertEquals(PhasePowerProcedure.check(twice, state, actor, power,
      secondSource), Right(PowerSourceRef.Card(second)))
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
    def withCardAt(forces: SiteForces) = base.updateCurrent(_.copy(
        turn = TurnState(actor, Phase.Act, Set.empty),
        players = current.players.map(p =>
          if (p.player != actor) p else p.copy(advisers = Vector.empty)),
        map = current.map.copy(sites = current.map.sites.updated(far,
          current.map.sites(far).copy(forces = forces, denizens = Vector(
            DenizenState(card, Orientation.FaceUp, Tokens.empty)))))))
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

  private def withSecrets(state: ReadyGame, count: Int): ReadyGame =
    state.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != actor) p else
        p.copy(board = p.board.copy(faceUpSecrets = count)))))
  private def heldOnCard(state: ReadyGame): Tokens =
    state.game.current.players.find(_.player == actor).get.advisers.collectFirst {
      case d: DenizenState if d.id == card => d.tokens }.get

  test("a costed ACTION power places its cost on its card, leaves no use " +
      "record and is limited only by the empty-card rule") {
    val power = TestPower(powerId, PowerTiming.Act, cost = Cost(secret = 1))
    val powers = PhasePowers(Vector(power))
    val funded = withSecrets(inPhase(Phase.Act), 2)
    assertEquals(PhasePowerProcedure.usable(catalog, funded, actor, powers)
      .map(_.ref), Vector(source))

    val used = use(power, Ready(funded)).toOption.get
    val after = ready(used.state)
    assertEquals(heldOnCard(after), Tokens(0, 1))
    assertEquals(after.game.current.turn.usedPowers, Set.empty[PowerUseRef])
    assertEquals(PhasePowerProcedure.usable(catalog, after, actor, powers),
      Vector.empty)
    assert(use(power, used.state).isLeft)
  }

  test("an unaffordable cost makes the power unusable") {
    val power = TestPower(powerId, PowerTiming.Act, cost = Cost(secret = 1))
    val broke = withSecrets(inPhase(Phase.Act), 0)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowers(Vector(power))), Vector.empty)
    assert(use(power, Ready(broke)).isLeft)
  }

  test("a free ACTION power is unlimited and records no use") {
    val power = TestPower(powerId, PowerTiming.Act)
    val first = use(power, Ready(inPhase(Phase.Act))).toOption.get
    val second = use(power, first.state).toOption.get
    assertEquals(ready(second.state).game.current.turn.usedPowers,
      Set.empty[PowerUseRef])
  }

  test("a costed WAKE power pays and is still once per turn") {
    val power = TestPower(powerId, PowerTiming.Wake, cost = Cost(secret = 1))
    val used = use(power, Ready(withSecrets(inPhase(Phase.Wake), 2))).toOption.get
    assertEquals(heldOnCard(ready(used.state)), Tokens(0, 1))
    val recorded = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card), powerId)
    assert(ready(used.state).game.current.turn.usedPowers.contains(recorded))
    assertEquals(use(power, used.state).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(recorded)))
  }

  test("an edifice at the pawn's site is a source, on either face") {
    val edifice = catalog.edifices.head
    val id = EdificeId(edifice.id.value)
    val printed = catalog.denizens.find(_.id.value == card.value).get.powers
      .find(_.id == powerId).get
    val powered = catalog.copy(edifices = catalog.edifices.map(e =>
      if (e.id == edifice.id) e.copy(
        intact = e.intact.copy(powers = e.intact.powers :+ printed),
        ruined = e.ruined.copy(powers = e.ruined.powers :+ printed)) else e))
    val current = base.game.current
    val home = current.players.find(_.player == actor).get.pawnSite.get
    val power = TestPower(powerId, PowerTiming.Act)
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val state = base.updateCurrent(c => c.copy(
        turn = TurnState(actor, Phase.Act, Set.empty),
        players = c.players.map(p => if (p.player != actor) p else
          p.copy(advisers = Vector.empty)),
        map = c.map.copy(sites = c.map.sites.updated(home,
          c.map.sites(home).copy(denizens =
            Vector(EdificeState(id, side, Tokens.empty)))))))
      assertEquals(PhasePowerProcedure.usable(powered, state, actor,
        PhasePowers(Vector(power))).map(p => p.source -> p.ref),
        Vector(PowerSourceRef.Card(id) -> DecisionOptionRef.Edifice(id)),
        side.toString)
    }
  }

  test("a banner is a source only for its holder") {
    val bannerPower = PowerId("banner.peoples-favor.grand-council")
    val power = TestPower(bannerPower, PowerTiming.Act)
    def state(holder: Option[PlayerId]) = inPhase(Phase.Act).updateCurrent(c =>
      c.copy(banners = c.banners.copy(peoplesFavor = c.banners.peoplesFavor.copy(
        active = PeoplesFavorFace.GrandCouncil, holder = holder))))
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, state(Some(actor)), actor,
      powers).map(p => p.source -> p.ref), Vector(
      PowerSourceRef.Banner(Banner.PeoplesFavor) ->
        DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    assertEquals(PhasePowerProcedure.usable(catalog, state(None), actor, powers),
      Vector.empty)
  }
}
