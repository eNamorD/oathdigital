package oathdigital.gameplay

import oathdigital.model.OathEvent.BanditsRefilled
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table, TestCards}
import oathdigital.testkit.Table.{p1, p2}

/** Synthetic WAKE, ACTION and REST powers on a faceup adviser, injected
  * through `OathRules`, drive the generic phase power path end to end.
  */
class PhasePowerSuite extends munit.FunSuite:
  import PhasePowerFixture._

  private def rules(power: PhasePower) =
    new OathRules(catalog, phasePowerCatalog = PhasePowers(Vector(power)))

  /** The parked decision, as this file rebuilds it: the same catalog and
    * phase power catalog `rules` was built with, per test's own power.
    */
  private def walkerParked(power: PhasePower) = new ParkedDecisionAssertions(
    catalog, phasePowerCatalog = PhasePowers(Vector(power)))
  private def use(power: PhasePower, state: OathState, by: PlayerId = p1) =
    rules(power).startWalker(state, ActionRef.UsePower(power.id), by,
      Vector.empty, Vector(source))
  private def ready(state: OathState) = state.asInstanceOf[Ready].value
  /** `state`'s turn put back in Wake, its uses kept: a once-per-turn power
    * that was Wake's last option ended Wake. */
  private def rewoken(state: OathState): OathState =
    Ready(ready(state).updateCurrent(current => current.copy(turn =
      current.turn.copy(phase = Phase.Wake))))

  test("a WAKE power is usable only in Wake, once per source, and runs the " +
      "action boundary"):
    val power = TestPower(powerId, PowerTiming.Wake)
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, holding(Phase.Wake), p1,
      powers, WalkerPowers.empty), Vector(PhasePowerProcedure.PowerSource(power, PowerSourceRef.Card(card),
        source)))
    assertEquals(PhasePowerProcedure.usable(catalog, holding(Phase.Act), p1,
      powers, WalkerPowers.empty), Vector.empty)

    val used = use(power, Ready(holding(Phase.Wake))).toOption.get
    val ref = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card), powerId)
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    // It was Wake's only option, so Wake ended with it.
    walkerParked(power).assertResumed(used.state, Phase.Act, p1)
    assert(ready(used.state).game.current.turn.usedPowers.contains(ref))
    val again = rewoken(used.state)
    assertEquals(use(power, again).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(ref)))
    assertEquals(PhasePowerProcedure.usable(catalog, ready(again), p1,
      powers, WalkerPowers.empty), Vector.empty)

  test("a use is scoped to its source card: the same power stays usable " +
      "from a second card"):
    // Errand Boy stands in as a second card printed with the same power.
    val second = CatalogNames.denizen("Errand Boy")
    val printed = catalog.denizens.find(_.id.value == card.value).get
      .powers.find(_.id == powerId).get
    val twice = catalog.copy(denizens = catalog.denizens.map(d =>
      if d.id.value == second.value then
        TestCards.denizenLike(d)(powers = d.powers :+ printed)
      else d))
    val usedFromFirst = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card),
      powerId)
    val state = Table.start.adviser(p1, card).adviser(p1, second)
      // The use from the first card is the fact under test; no step records one.
      .update(_.updateCurrent(_.copy(
        turn = TurnState(p1, Phase.Wake, Set(usedFromFirst)))))
      .ready
    val power = TestPower(powerId, PowerTiming.Wake)
    val secondSource = DecisionOptionRef.Denizen(second)
    assertEquals(PhasePowerProcedure.check(twice, state, p1, power, source),
      Left(OathViolation.PowerAlreadyUsed(usedFromFirst)))
    assertEquals(PhasePowerProcedure.check(twice, state, p1, power,
      secondSource), Right(PowerSourceRef.Card(second)))
    assertEquals(PhasePowerProcedure.usable(twice, state, p1,
      PhasePowers(Vector(power)), WalkerPowers.empty).map(_.ref), Vector(secondSource))

  test("an ACTION power returns its player to action selection"):
    val power = TestPower(powerId, PowerTiming.Act)
    val used = use(power, Ready(holding(Phase.Act))).toOption.get
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    walkerParked(power).assertResumed(used.state, Phase.Act, p1)

  test("another player, the wrong phase and an inaccessible source are refused"):
    val power = TestPower(powerId, PowerTiming.Act)
    assertEquals(use(power, Ready(holding(Phase.Act)), p2).left.toOption,
      Some(OathViolation.WrongPlayer(p1, p2)))
    assertEquals(use(power, Ready(holding(Phase.Wake))).left.toOption, Some(OathViolation.InvalidEventOrder(
      "denizen.magicians-code is a Act power and cannot be used in the Wake phase")))
    assertEquals(rules(power).startWalker(Ready(holding(Phase.Act)),
      ActionRef.UsePower(powerId), p1, Vector.empty,
      Vector(DecisionOptionRef.Denizen(DenizenId("no-such-card")))).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "denizen/no-such-card is not an accessible source of denizen.magicians-code")))

  test("a card at a site the player rules is a source, and one at an unruled " +
      "site is not"):
    val power = TestPower(powerId, PowerTiming.Act)
    // The card lies at Dunes, away from p1's pawn at Ancient City.
    val atDunes = Table.start.denizen(card, at = "Dunes")
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog,
      atDunes.warbandsAt("Dunes", p1, 1).ready, p1, powers, WalkerPowers.empty).map(_.ref),
      Vector(source))
    assertEquals(PhasePowerProcedure.usable(catalog,
      atDunes.bandits("Dunes", 1).ready, p1, powers, WalkerPowers.empty), Vector.empty)

  test("a usable REST power stops the Rest auto-skip, and using it still " +
      "leaves Finish Rest to the player"):
    val power = TestPower(powerId, PowerTiming.Rest)
    val rested = rules(power).startWalker(Ready(holding(Phase.Act)),
      PhaseTransitionRef.BeginRest, p1).toOption.get
    walkerParked(power).assertResumed(rested.state, Phase.Rest, p1)
    val used = use(power, rested.state).toOption.get
    walkerParked(power).assertResumed(used.state, Phase.Rest, p1)
    val finished = rules(power).startWalker(used.state,
      PhaseTransitionRef.FinishRest, p1).toOption.get
    val next = ready(finished.state).game.current.turn
    assertNotEquals(next.activePlayer, p1)
    assertEquals(next.usedPowers, Set.empty[PowerUseRef])

  test("a decision inside a power parks as a power decision and replays"):
    val choice = "test-power-choice"
    val power = TestPower(powerId, PowerTiming.Act, player => Decide(choice,
      player, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(DecisionOptionRef.Button("go"), "Go"),
        DecisionOption.Button(DecisionOptionRef.Button("stop"), "Stop")))))
    val parked = use(power, Ready(holding(Phase.Act))).toOption.get
    walkerParked(power).assertParked(parked.state, ActionRef.UsePower(powerId),
      choice, p1)
    val done = rules(power).resolveWalker(parked.state, p1, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("go"))).toOption.get
    walkerParked(power).assertResumed(done.state, Phase.Act, p1)
    assertEquals((parked.events ++ done.events)
      .foldLeft[Either[OathViolation, OathState]](Right(Ready(holding(Phase.Act))))(
        (state, event) => state.flatMap(rules(power).evolve(_, event))),
      Right(done.state))

  /** p1 holds the card and `count` faceup secrets, in `phase`. */
  private def withSecrets(phase: Phase, count: Int): ReadyGame =
    Table.start.adviser(p1, card).secrets(p1, faceUp = count)
      .turn(p1, phase).ready

  test("a costed ACTION power places its cost on its card, leaves no use " +
      "record and is limited only by the empty-card rule"):
    val power = TestPower(powerId, PowerTiming.Act, cost = Cost(secret = 1))
    val powers = PhasePowers(Vector(power))
    val funded = withSecrets(Phase.Act, 2)
    assertEquals(PhasePowerProcedure.usable(catalog, funded, p1, powers, WalkerPowers.empty)
      .map(_.ref), Vector(source))

    val used = use(power, Ready(funded)).toOption.get
    val after = ready(used.state)
    assertEquals(Look(after).tokensOn(card), Tokens(0, 1))
    assertEquals(after.game.current.turn.usedPowers, Set.empty[PowerUseRef])
    assertEquals(PhasePowerProcedure.usable(catalog, after, p1, powers, WalkerPowers.empty),
      Vector.empty)
    assert(use(power, used.state).isLeft)

  test("an unaffordable cost makes the power unusable"):
    val power = TestPower(powerId, PowerTiming.Act, cost = Cost(secret = 1))
    val broke = withSecrets(Phase.Act, 0)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, p1,
      PhasePowers(Vector(power)), WalkerPowers.empty), Vector.empty)
    assertEquals(use(power, Ready(broke)).left.toOption, Some(OathViolation.InsufficientSecrets(1, 0)))

  test("a free ACTION power is unlimited and records no use"):
    val power = TestPower(powerId, PowerTiming.Act)
    val first = use(power, Ready(holding(Phase.Act))).toOption.get
    val second = use(power, first.state).toOption.get
    assertEquals(ready(second.state).game.current.turn.usedPowers,
      Set.empty[PowerUseRef])

  test("a costed WAKE power pays and is still once per turn"):
    val power = TestPower(powerId, PowerTiming.Wake, cost = Cost(secret = 1))
    val used = use(power, Ready(withSecrets(Phase.Wake, 2))).toOption.get
    assertEquals(Look(used.state).tokensOn(card), Tokens(0, 1))
    val recorded = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(card), powerId)
    assert(ready(used.state).game.current.turn.usedPowers.contains(recorded))
    assertEquals(use(power, rewoken(used.state)).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(recorded)))

  test("an edifice at the pawn's site is a source, on either face"):
    // Hall of Debate is printed, for this test, with the test power's power.
    val id = CatalogNames.edifice("Hall of Debate")
    val edifice = catalog.edifices.find(_.id.value == id.value).get
    val printed = catalog.denizens.find(_.id.value == card.value).get.powers
      .find(_.id == powerId).get
    val powered = catalog.copy(edifices = catalog.edifices.map(e =>
      if e.id == edifice.id then TestCards.edificeLike(e)(
        intact = e.intact.powers :+ printed,
        ruined = e.ruined.powers :+ printed) else e))
    val power = TestPower(powerId, PowerTiming.Act)
    Vector(EdificeSide.Intact, EdificeSide.Ruined).foreach { side =>
      val state = Table.start.edifice(id, side, at = Table.homeOf(p1)).ready
      assertEquals(PhasePowerProcedure.usable(powered, state, p1,
        PhasePowers(Vector(power)), WalkerPowers.empty).map(p => p.source -> p.ref),
        Vector(PowerSourceRef.Card(id) -> DecisionOptionRef.Edifice(id)),
        side.toString)
    }

  test("a banner is a source only for its holder"):
    val bannerPower = PowerId("banner.peoples-favor.grand-council")
    val power = TestPower(bannerPower, PowerTiming.Act)
    def state(holder: Option[PlayerId]) = holding(Phase.Act).updateCurrent(c =>
      c.copy(banners = c.banners.copy(peoplesFavor = c.banners.peoplesFavor.copy(
        active = PeoplesFavorFace.GrandCouncil, holder = holder))))
    val powers = PhasePowers(Vector(power))
    assertEquals(PhasePowerProcedure.usable(catalog, state(Some(p1)), p1,
      powers, WalkerPowers.empty).map(p => p.source -> p.ref), Vector(
      PowerSourceRef.Banner(Banner.PeoplesFavor) ->
        DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    assertEquals(PhasePowerProcedure.usable(catalog, state(None), p1, powers, WalkerPowers.empty),
      Vector.empty)

  test("a held relic's power is usable only while the relic is faceup"):
    val relicPower = TestPower(PowerId("relic.brass-horse"), PowerTiming.Act)
    val relic = CatalogNames.relic("Brass Horse")
    def holdingRelic(facedown: Boolean) = Table.start
      .relic(p1, relic, facedown = facedown).turn(p1, Phase.Act).ready
    val powers = PhasePowers(Vector(relicPower))
    assert(PhasePowerProcedure.usable(catalog, holdingRelic(facedown = false), p1,
      powers, WalkerPowers.empty).nonEmpty)
    assertEquals(PhasePowerProcedure.usable(catalog, holdingRelic(facedown = true),
      p1, powers, WalkerPowers.empty), Vector.empty)
    assertEquals(rules(relicPower).startWalker(Ready(holdingRelic(facedown = true)),
      ActionRef.UsePower(relicPower.id), p1, Vector.empty,
      Vector(DecisionOptionRef.Relic(relic))).left.toOption, Some(OathViolation.InvalidEventOrder(
        s"relic/${relic.value} is not an accessible source of relic.brass-horse")))
