package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class LevelersSuite extends munit.FunSuite:
  import TargetsFixture._

  private val levelers = CatalogNames.denizen("Levelers")
  private val source = DecisionOptionRef.Denizen(levelers)

  /** p1's Act holding Levelers, with the banks holding exactly `favor`. */
  private def staged(favor: (Suit, Int)*): ReadyGame =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(Table.start.turn(p1, Phase.Act)
      .adviser(p1, levelers)) { case (table, (suit, n)) =>
      table.bankFavor(suit, n) }.ready

  /** Every bank's favor, in suit order: Discord, Arcane, Order, Hearth,
    * Beast, Nomad. */
  private def banks(ready: ReadyGame): Vector[Int] =
    Suit.all.map(ready.banks.favor.getOrElse(_, 0))
  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def offeredBanks(suits: Suit*) =
    Some(suits.toVector.map(suit => "favor-bank" -> suit.key))

  test("a single fullest and a single emptiest bank move 2 favor, unasked"):
    val ready = staged(Suit.Discord -> 2, Suit.Arcane -> 5, Suit.Hearth -> 2,
      Suit.Beast -> 2, Suit.Nomad -> 2)
    val t = use(ready, Levelers, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(levelers), Tokens(0, 1))
    assertEquals(banks(end), Vector(2, 3, 2, 2, 2, 2))
    assertEquals(NoteText.said(Levelers, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor from the Arcane bank to the Order bank.",
      covers = false)))

  test("tied banks are asked, the source first, then the destination"):
    val ready = staged(Suit.Arcane -> 4, Suit.Beast -> 4, Suit.Discord -> 1,
      Suit.Hearth -> 1, Suit.Order -> 2, Suit.Nomad -> 2)
    val t = use(ready, Levelers, source).toOption.get
    assert(awaits(t, Levelers.sourceDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), offeredBanks(Suit.Arcane, Suit.Beast))
    val second = answer(t, p1, Levelers.sourceDecisionId,
      pick(bank(Suit.Beast))).toOption.get
    assert(awaits(second, Levelers.destinationDecisionId),
      parked.parkedDecision(second.state).toString)
    assertEquals(offered(second, p1), offeredBanks(Suit.Discord, Suit.Hearth))
    val done = answer(second, p1, Levelers.destinationDecisionId,
      pick(bank(Suit.Hearth))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(banks(after(done)), Vector(1, 4, 2, 3, 2, 2))
    val events = t.events ++ second.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(events))

  test("a source holding 1 favor moves what it holds"):
    val t = use(staged(Suit.Arcane -> 1), Levelers, source).toOption.get
    assertEquals(offered(t, p1), offeredBanks(Suit.Discord, Suit.Order,
      Suit.Hearth, Suit.Beast, Suit.Nomad))
    val done = answer(t, p1, Levelers.destinationDecisionId,
      pick(bank(Suit.Order))).toOption.get
    assertEquals(banks(after(done)), Vector(0, 0, 1, 0, 0, 0))
    assertEquals(NoteText.said(Levelers, done.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 1 favor from the Arcane bank to the Order bank.",
      covers = false)))

  test("when every bank holds the same, any source, then any other bank"):
    val t = use(staged(Suit.all.map(_ -> 1)*), Levelers, source).toOption.get
    assertEquals(offered(t, p1), offeredBanks(Suit.all*))
    val second = answer(t, p1, Levelers.sourceDecisionId,
      pick(bank(Suit.Nomad))).toOption.get
    assertEquals(offered(second, p1), offeredBanks(Suit.Discord, Suit.Arcane,
      Suit.Order, Suit.Hearth, Suit.Beast))
    val done = answer(second, p1, Levelers.destinationDecisionId,
      pick(bank(Suit.Arcane))).toOption.get
    assertEquals(banks(after(done)), Vector(1, 2, 1, 1, 1, 0))

  test("with every bank empty nothing moves, and the line says so"):
    val t = use(staged(), Levelers, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(levelers), Tokens(0, 1))
    assertEquals(NoteText.said(Levelers, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank was empty.", covers = false)))
