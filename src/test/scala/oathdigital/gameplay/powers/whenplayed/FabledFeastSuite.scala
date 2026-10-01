package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class FabledFeastSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[FabledFeast]
  private val feast = power.cardId
  private val played = hook(feast)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)

  /** `table` with every bank empty except `stocks`. */
  private def banks(table: Table, stocks: (Suit, Int)*): Table =
    val held = stocks.toMap
    Suit.all.foldLeft(table)((next, suit) =>
      next.bankFavor(suit, held.getOrElse(suit, 0)))

  /** Three Hearth cards p1 rules: Fabled Feast and a Hearth adviser, and a
    * Hearth denizen at p1's site, which p1 rules. A Hearth denizen at a site
    * nobody rules does not count. */
  private val ruling: Table = Table.start.adviser(p1, feast)
    .adviser(p1, hearth(0)).warbandsAt(homeSite, p1, 1)
    .denizen(hearth(1), at = homeSite).denizen(hearth(2), at = nearSite)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(n: Int, suit: Suit): NoteText.Said = NoteText.Said("took",
    s"${p1.value} took $n favor from the $suit bank.", covers = true)

  private def bankAnswer(suit: Suit): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))

  test("with two stocked banks it asks which, and takes X favor from it"):
    val ready = banks(ruling, Suit.Order -> 5, Suit.Hearth -> 2).ready
    val first = parked(play(ready, power, feast))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, FabledFeast.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(Suit.Order, Suit.Hearth).map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      decide.query.heading))
    val done = finished(resume(ready, power, played, first.tree,
      FabledFeast.decisionId, bankAnswer(Suit.Order)))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(done.treeless.banks.favor(Suit.Order), 2)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(took(3, Suit.Order)))

  test("a short bank gives what it holds"):
    val ready = banks(ruling, Suit.Order -> 5, Suit.Hearth -> 2).ready
    val first = parked(play(ready, power, feast))
    val done = finished(resume(ready, power, played, first.tree,
      FabledFeast.decisionId, bankAnswer(Suit.Hearth)))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(took(2, Suit.Hearth)))

  test("one stocked bank is taken from without asking"):
    val done = finished(play(banks(ruling, Suit.Nomad -> 5).ready, power,
      feast))
    assertEquals(said(done.events), Vector(took(3, Suit.Nomad)))

  test("played to a site p1 rules it counts itself"):
    val ready = banks(Table.start.denizen(feast, at = homeSite)
      .warbandsAt(homeSite, p1, 1), Suit.Hearth -> 5).ready
    val done = finished(fire(ready, power, hookAt(feast, homeSite)))
    assertEquals(said(done.events), Vector(took(1, Suit.Hearth)))

  test("played to a site nobody rules, with no other Hearth card, it takes " +
      "nothing"):
    val ready = banks(Table.start.denizen(feast, at = homeSite),
      Suit.Hearth -> 5).ready
    val done = finished(fire(ready, power, hookAt(feast, homeSite)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} ruled no Hearth card.", covers = false)))

  test("with every bank empty it takes nothing, and the line says so"):
    val done = finished(play(banks(ruling).ready, power, feast))
    assertEquals(said(done.events), Vector(NoteText.Said("empty",
      "Every favor bank was empty.", covers = false)))
