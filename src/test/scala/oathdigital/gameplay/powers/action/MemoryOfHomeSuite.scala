package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class MemoryOfHomeSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val memory = CatalogNames.denizen("Memory of Home")
  private val source = DecisionOptionRef.Denizen(memory)

  /** p1's Act holding Memory of Home with two secrets, the banks holding
    * exactly `favor`. */
  private def staged(favor: (Suit, Int)*): Table =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(Table.start.turn(p1, Phase.Act)
      .adviser(p1, memory).secrets(p1, 2)) { case (table, (suit, n)) =>
      table.bankFavor(suit, n) }

  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  test("one stocked bank is emptied into the Hearth bank, unasked"):
    val ready = staged(Suit.Order -> 3, Suit.Hearth -> 1).ready
    val t = accepted(use(ready, MemoryOfHome, source))
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(memory), Tokens(0, 1))
    assertEquals(Look(end).faceUpSecrets(p1), 0)
    assertEquals((bank(end, Suit.Order), bank(end, Suit.Hearth)), (0, 4))
    assertEquals(NoteText.said(MemoryOfHome, t.events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 3 favor from the Order bank to the Hearth bank.",
      covers = false)))

  test("several stocked banks are asked, the Hearth bank not among them"):
    val ready = staged(Suit.Arcane -> 2, Suit.Nomad -> 1, Suit.Hearth -> 5)
      .ready
    val t = accepted(use(ready, MemoryOfHome, source))
    assert(awaits(t, MemoryOfHome.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("favor-bank" -> "arcane",
      "favor-bank" -> "nomad")))
    val done = accepted(answer(t, p1, MemoryOfHome.decisionId,
      pick(DecisionOptionRef.FavorBank(Suit.Nomad))))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Vector(Suit.Arcane, Suit.Nomad, Suit.Hearth).map(bank(end, _)),
      Vector(2, 0, 6))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("with only the Hearth bank stocked nothing moves, and the line " +
      "says so"):
    val t = accepted(use(staged(Suit.Hearth -> 4).ready, MemoryOfHome, source))
    assertEquals(bank(after(t), Suit.Hearth), 4)
    assertEquals(NoteText.said(MemoryOfHome, t.events), Vector(NoteText.Said(
      "used.empty", "Every other favor bank was empty.", covers = false)))

  test("it is unusable with a single secret"):
    val short = staged(Suit.Order -> 3).secrets(p1, 1).ready
    assert(!usableNow(short).exists(_.power.id == MemoryOfHome.id))
    assertEquals(use(short, MemoryOfHome, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(2, 1)))
