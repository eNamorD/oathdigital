package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GamblingHallSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val hall = DenizenId("93")
  private val source = DecisionOptionRef.Denizen(hall)
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def staged(favor: Int = 3) =
    act(withBoard(atHome(base, hall))(_.copy(favor = favor)))

  private val total4 = defenseDice(DefenseDieFace.OneShield,
    DefenseDieFace.OneShield, DefenseDieFace.TwoShields, DefenseDieFace.Blank)

  test("Gambling Hall is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(GamblingHall.id).isDefined)
  }

  test("the bank question says how much the roll won") {
    // The take is not a choice of amount: the power gains the whole total
    // the bank can pay, so the question states the number rather than
    // implying a ceiling the player may choose under.
    assertEquals(GamblingHall.bankHeading(4), "Gain 4 favor from one bank")
    assertEquals(GamblingHall.bankHeading(1), "Gain 1 favor from one bank")
  }

  test("it places 2 favor, rolls 4 dice and takes the total from the chosen bank") {
    val rules0 = rules(total4)
    val ready0 = staged()
    val parked = use(rules0, ready0, GamblingHall.id, source).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingPowerDecision(actor,
      DecisionId(GamblingHall.decisionId)))
    val paid = ready(parked.state)
    assertEquals(player(paid).board.favor, 1)
    assertEquals(tokensOn(paid, hall), Tokens(2, 0))
    assertEquals(paid.game.current.rollOutcomes(GamblingHall.pool).score, 4)

    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.favor, 5)
    assertEquals(end.banks.favor(Suit.Beast),
      ready0.banks.favor(Suit.Beast) - 4)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(end.game.current.rollPools, Map.empty[PoolKey, DicePoolState])
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assert(wireRoundTrips(parked.events ++ done.events))
  }

  test("a Doubler multiplies the total, and a bank with less than X gives what it has") {
    val doubled = defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)
    val rules0 = rules(doubled)
    val ready0 = staged()
    val parked = use(rules0, ready0, GamblingHall.id, source).toOption.get
    assertEquals(ready(parked.state).game.current.rollOutcomes(
      GamblingHall.pool).score, 6)
    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Nomad)).toOption.get
    assertEquals(ready(done.state).banks.favor(Suit.Nomad), 0)
    assertEquals(player(ready(done.state)).board.favor,
      1 + ready0.banks.favor(Suit.Nomad))
  }

  test("an empty bank may be chosen and gives nothing") {
    val drained = staged().copy(banks = staged().banks.copy(
      favor = staged().banks.favor.updated(Suit.Discord, 0)))
    val rules0 = rules(total4)
    val parked = use(rules0, drained, GamblingHall.id, source).toOption.get
    val done = answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Discord)).toOption.get
    assertEquals(player(ready(done.state)).board.favor, 1)
    assertEquals(ready(done.state).banks.favor(Suit.Discord), 0)
  }

  test("a total of zero asks nothing and takes nothing") {
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.Blank, DefenseDieFace.Blank))
    val done = use(rules0, staged(), GamblingHall.id, source).toOption.get
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(player(ready(done.state)).board.favor, 1)
    assertEquals(tokensOn(ready(done.state), hall), Tokens(2, 0))
  }

  test("a bank the decision does not offer is rejected") {
    val rules0 = rules(total4)
    val parked = use(rules0, staged(), GamblingHall.id, source).toOption.get
    assert(answer(rules0, parked.state, GamblingHall.decisionId,
      DecisionOptionRef.Button("elsewhere")).isLeft)
  }

  test("it is unusable without 2 favor, and again while its card holds favor") {
    assert(!usableIds(staged(favor = 1)).contains(GamblingHall.id))
    assert(use(rules(total4), staged(favor = 1), GamblingHall.id, source).isLeft)
    val parked = use(rules(total4), staged(), GamblingHall.id, source).toOption.get
    val done = answer(rules(total4), parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)).toOption.get
    val again = ready(done.state)
    assert(!usableIds(again).contains(GamblingHall.id))
    assert(use(rules(total4), again, GamblingHall.id, source).isLeft)
  }

  test("a Gambling Hall at a site the actor rules is usable from another site") {
    val far = base.game.current.map.inPlay.toVector.sortBy(_.value)
      .find(_ != home(base)).get
    val kind = oathdigital.gameplay.powers.PlayerFacts.forceKind(base, actor)
      .toOption.get
    val ready0 = act(withBoard(atSite(base, hall, far).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(far,
        c.map.sites(far).copy(forces = SiteForces.Occupied(kind, 1)))))))(
      _.copy(favor = 3)))
    assert(usableIds(ready0).contains(GamblingHall.id))
  }
}
