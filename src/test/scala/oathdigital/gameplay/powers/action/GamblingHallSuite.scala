package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, PowerNoted}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class GamblingHallSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val hall = CatalogNames.denizen("Gambling Hall")
  private val source = DecisionOptionRef.Denizen(hall)
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  /** p1's Act at Ancient City with Gambling Hall at the site and `favor`
    * favor. The Beast bank holds 4 favor and the Nomad bank 3. */
  private def stagedTable(favor: Int = 3): Table =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(hall, at = "Ancient City").favor(actor, favor)
      .bankFavor(Suit.Beast, 4).bankFavor(Suit.Nomad, 3)
  private def staged(favor: Int = 3): ReadyGame = stagedTable(favor).ready

  private val total4 = defenseDice(DefenseDieFace.OneShield,
    DefenseDieFace.OneShield, DefenseDieFace.TwoShields, DefenseDieFace.Blank)

  test("the bank question says how much the roll won"):
    // The take is not a choice of amount: the power gains the whole total
    // the bank can pay, so the question states the number rather than
    // implying a ceiling the player may choose under.
    assertEquals(GamblingHall.bankHeading(4), "Gain 4 favor from one bank")
    assertEquals(GamblingHall.bankHeading(1), "Gain 1 favor from one bank")

  test("it places 2 favor, rolls 4 dice and takes the total from the chosen bank"):
    val rules0 = rules(total4)
    // 3 favor less the 2 placed leave 1; the roll's 4 make 5.
    val ready0 = staged(favor = 3)
    val parked = accepted(use(rules0, ready0, GamblingHall.id, source))
    walkerParked.assertParked(parked.state, ActionRef.UsePower(GamblingHall.id),
      GamblingHall.decisionId, actor)
    val paid = ready(parked.state)
    assertEquals(Look(paid).player(actor).board.favor, 1)
    assertEquals(Look(paid).tokensOn(hall), Tokens(2, 0))
    assertEquals(paid.game.current.rollOutcomes(GamblingHall.pool).score, 4)

    val done = accepted(answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)))
    val end = ready(done.state)
    assertEquals(Look(end).player(actor).board.favor, 5)
    assertEquals(end.banks.favor(Suit.Beast),
      ready0.banks.favor(Suit.Beast) - 4)
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(end.game.current.rollPools, Map.empty[PoolKey, DicePoolState])
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assertWireRoundTrips(parked.events ++ done.events)

  test("a Doubler multiplies the total, and a bank with less than X gives what it has"):
    val doubled = defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)
    val rules0 = rules(doubled)
    // The roll scores 6, more than the Nomad bank's 3.
    val ready0 = staged()
    val parked = accepted(use(rules0, ready0, GamblingHall.id, source))
    assertEquals(ready(parked.state).game.current.rollOutcomes(
      GamblingHall.pool).score, 6)
    val done = accepted(answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Nomad)))
    assertEquals(ready(done.state).banks.favor(Suit.Nomad), 0)
    assertEquals(Look(ready(done.state)).player(actor).board.favor,
      1 + ready0.banks.favor(Suit.Nomad))

  test("an empty bank may be chosen and gives nothing"):
    val drained = stagedTable().bankFavor(Suit.Discord, 0).ready
    val rules0 = rules(total4)
    val parked = accepted(use(rules0, drained, GamblingHall.id, source))
    val done = accepted(answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Discord)))
    assertEquals(Look(ready(done.state)).player(actor).board.favor, 1)
    assertEquals(ready(done.state).banks.favor(Suit.Discord), 0)

  test("a total of zero asks nothing and takes nothing"):
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.Blank, DefenseDieFace.Blank))
    val done = accepted(use(rules0, staged(), GamblingHall.id, source))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(Look(ready(done.state)).player(actor).board.favor, 1)
    assertEquals(Look(ready(done.state)).tokensOn(hall), Tokens(2, 0))

  test("a bank the decision does not offer is rejected"):
    val rules0 = rules(total4)
    val parked = accepted(use(rules0, staged(), GamblingHall.id, source))
    assert(answer(rules0, parked.state, GamblingHall.decisionId,
      DecisionOptionRef.Button("elsewhere")).isLeft)

  test("it is unusable without 2 favor"):
    assert(!usableIds(staged(favor = 1)).contains(GamblingHall.id))
    assert(use(rules(total4), staged(favor = 1), GamblingHall.id, source).isLeft)

  private val card = PowerSourceRef.Card(hall)
  private def notes(events: Vector[OathEvent]): Vector[PowerNoted] =
    events.collect { case noted: PowerNoted => noted }

  test("it notes its roll, then what it gained, each covering the generic line"):
    val rules0 = rules(total4)
    val parked = accepted(use(rules0, staged(), GamblingHall.id, source))
    val done = accepted(answer(rules0, parked.state, GamblingHall.decisionId,
      bank(Suit.Beast)))
    assertEquals(notes(parked.events ++ done.events), Vector(
      PowerNoted(GamblingHall.id, GamblingHall.rolled(card, NoteArg.Player(actor),
        NoteArg.Dice(Vector(DefenseDieFace.OneShield, DefenseDieFace.OneShield,
          DefenseDieFace.TwoShields, DefenseDieFace.Blank)),
        NoteArg.Number(4)), covers = true),
      PowerNoted(GamblingHall.id, GamblingHall.gained(card, NoteArg.Player(actor),
        NoteArg.Amount(4, NoteUnit.Favor), NoteArg.Bank(Suit.Beast)),
        covers = true)))

  test("a gain the bank caps is noted as what the bank paid"):
    val doubled = defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)
    val ready0 = staged()
    val parked = accepted(use(rules(doubled), ready0, GamblingHall.id, source))
    val done = accepted(answer(rules(doubled), parked.state,
      GamblingHall.decisionId, bank(Suit.Nomad)))
    assertEquals(notes(done.events).map(_.note.args), Vector(Vector(
      NoteArg.Player(actor),
      NoteArg.Amount(ready0.banks.favor(Suit.Nomad), NoteUnit.Favor),
      NoteArg.Bank(Suit.Nomad))))

  test("a total of zero notes only the roll"):
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.Blank, DefenseDieFace.Blank))
    val done = accepted(use(rules0, staged(), GamblingHall.id, source))
    assertEquals(notes(done.events).map(_.note.key), Vector(NoteKey.Used))

  test("an empty bank gives nothing, so no gain is noted"):
    val drained = stagedTable().bankFavor(Suit.Discord, 0).ready
    val parked = accepted(use(rules(total4), drained, GamblingHall.id, source))
    val done = accepted(answer(rules(total4), parked.state,
      GamblingHall.decisionId, bank(Suit.Discord)))
    assertEquals(notes(done.events), Vector.empty)
