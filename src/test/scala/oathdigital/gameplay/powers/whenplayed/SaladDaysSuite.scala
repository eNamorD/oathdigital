package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.rejected

class SaladDaysSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[SaladDays.type]
  private val salad = power.cardId
  private val played = hook(salad)

  /** Salad Days as p1's adviser, p1 holding 1 favor, and exactly `stocks`
    * holding favor. */
  private def staged(stocks: (Suit, Int)*): ReadyGame =
    Table.start.adviser(p1, salad).favor(p1, 1).banksHoldingOnly(stocks*).ready

  private def choose(suits: Suit*): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(
      suits.toVector.map(DecisionOptionRef.FavorBank(_)))

  private val four = Vector(Suit.Discord -> 2, Suit.Order -> 1,
    Suit.Hearth -> 3, Suit.Beast -> 1)

  test("with four stocked banks it asks for three, and takes 1 favor from " +
      "each"):
    val ready = staged(four*)
    val first = Outcomes.parked(play(ready, power, salad))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, SaladDays.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(3, 3,
      four.map((suit, _) => DecisionOption.FavorBank(
        DecisionOptionRef.FavorBank(suit))), decide.query.heading))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      SaladDays.decisionId, choose(Suit.Discord, Suit.Hearth, Suit.Beast)))
    // p1's 1 favor and 1 from each of the three banks chosen.
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(Vector(Suit.Discord, Suit.Order, Suit.Hearth, Suit.Beast)
      .map(Look(done.treeless).bankFavor), Vector(1, 1, 2, 0))
    assertJournalRebuilds(ready, first.events ++ done.events, done.treeless)
    assertEquals(said(power, done.events), Vector.empty)

  test("with three stocked banks it takes 1 from each without asking"):
    val ready = staged(Suit.Arcane -> 1, Suit.Order -> 2, Suit.Nomad -> 5)
    val done = Outcomes.finished(play(ready, power, salad))
    // p1's 1 favor and 1 from each stocked bank.
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(Vector(Suit.Arcane, Suit.Order, Suit.Nomad)
      .map(Look(done.treeless).bankFavor), Vector(0, 1, 4))

  test("with two stocked banks it takes 1 from each"):
    val done = Outcomes.finished(
      play(staged(Suit.Order -> 2, Suit.Nomad -> 5), power, salad))
    // p1's 1 favor and 1 from each stocked bank.
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)

  test("with every bank empty it gains nothing, and the line says so"):
    val done = Outcomes.finished(play(staged(), power, salad))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events),
      Vector(NoteText.Said("none", "Every favor bank was empty.",
        covers = false)))

  test("an empty bank is not a choice"):
    val ready = staged(four*)
    val first = Outcomes.parked(play(ready, power, salad))
    // The Arcane bank is empty.
    assertEquals(rejected(resume(ready, power, played, first.tree,
      SaladDays.decisionId, choose(Suit.Discord, Suit.Arcane, Suit.Beast))),
      OathViolation.InvalidEventOrder(s"decision ${SaladDays.decisionId} " +
        "does not offer a selected option"))
