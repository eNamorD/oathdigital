package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class SaladDaysSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[SaladDays]
  private val salad = power.cardId
  private val played = hook(salad)

  /** Salad Days as p1's adviser, each bank holding what `stocks` gives it
    * and every other bank empty. */
  private def staged(stocks: (Suit, Int)*): ReadyGame =
    val held = stocks.toMap
    Suit.all.foldLeft(Table.start.adviser(p1, salad))((table, suit) =>
      table.bankFavor(suit, held.getOrElse(suit, 0))).ready

  private def banks(ready: ReadyGame, suits: Suit*): Vector[Int] =
    suits.toVector.map(ready.banks.favor.getOrElse(_, 0))

  private def choose(suits: Suit*): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(
      suits.toVector.map(DecisionOptionRef.FavorBank(_)))

  private val four = Vector(Suit.Discord -> 2, Suit.Order -> 1,
    Suit.Hearth -> 3, Suit.Beast -> 1)

  test("with four stocked banks it asks for three, and takes 1 favor from " +
      "each"):
    val ready = staged(four*)
    val first = parked(play(ready, power, salad))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, SaladDays.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(3, 3,
      four.map((suit, _) => DecisionOption.FavorBank(
        DecisionOptionRef.FavorBank(suit))), decide.query.heading))
    val done = finished(resume(ready, power, played, first.tree,
      SaladDays.decisionId, choose(Suit.Discord, Suit.Hearth, Suit.Beast)))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(banks(done.treeless, Suit.Discord, Suit.Order, Suit.Hearth,
      Suit.Beast), Vector(1, 1, 2, 0))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector.empty)

  test("with three stocked banks it takes 1 from each without asking"):
    val ready = staged(Suit.Arcane -> 1, Suit.Order -> 2, Suit.Nomad -> 5)
    val done = finished(play(ready, power, salad))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(banks(done.treeless, Suit.Arcane, Suit.Order, Suit.Nomad),
      Vector(0, 1, 4))

  test("with two stocked banks it takes 1 from each"):
    val done = finished(play(staged(Suit.Order -> 2, Suit.Nomad -> 5), power,
      salad))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)

  test("with every bank empty it gains nothing, and the line says so"):
    val done = finished(play(staged(), power, salad))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("none", "Every favor bank was empty.",
        covers = false)))

  test("an empty bank is not a choice"):
    val ready = staged(four*)
    val first = parked(play(ready, power, salad))
    assert(resume(ready, power, played, first.tree, SaladDays.decisionId,
      choose(Suit.Discord, Suit.Arcane, Suit.Beast)).isLeft)
