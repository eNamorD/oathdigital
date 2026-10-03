package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.p1

class FabledFeastSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[FabledFeast]
  private val feast = power.cardId
  private val played = hook(feast)

  /** Three Hearth cards p1 rules: Fabled Feast, the adviser Homesteaders, and
    * Land Warden at p1's site, which p1 rules. News from Afar, at a site
    * nobody rules, does not count. p1 holds 1 favor.
    */
  private val ruling: Table = Table.start.adviser(p1, feast)
    .adviser(p1, "Homesteaders").warbandsAt(homeSite, p1, 1)
    .denizen("Land Warden", at = homeSite)
    .denizen("News from Afar", at = nearSite)
    .favor(p1, 1)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(n: Int, suit: Suit): NoteText.Said = NoteText.Said("took",
    s"${p1.value} took $n favor from the $suit bank.", covers = true)

  private def bankAnswer(suit: Suit): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))

  test("with two stocked banks it asks which, and takes X favor from it"):
    val ready = ruling.banksHoldingOnly(Suit.Order -> 5, Suit.Hearth -> 2).ready
    val first = Outcomes.parked(play(ready, power, feast))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, FabledFeast.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(Suit.Order, Suit.Hearth).map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      decide.query.heading))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      FabledFeast.decisionId, bankAnswer(Suit.Order)))
    // p1's 1 favor and the 3 taken from the Order bank's 5.
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(done.treeless.banks.favor(Suit.Order), 5 - 3)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(took(3, Suit.Order)))

  test("a short bank gives what it holds"):
    val ready = ruling.banksHoldingOnly(Suit.Order -> 5, Suit.Hearth -> 2).ready
    val first = Outcomes.parked(play(ready, power, feast))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      FabledFeast.decisionId, bankAnswer(Suit.Hearth)))
    // The Hearth bank holds only 2 of the 3.
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(took(2, Suit.Hearth)))

  test("one stocked bank is taken from without asking"):
    val done = Outcomes.finished(
      play(ruling.banksHoldingOnly(Suit.Nomad -> 5).ready, power, feast))
    assertEquals(said(done.events), Vector(took(3, Suit.Nomad)))

  test("played to a site p1 rules it counts itself"):
    val ready = Table.start.denizen(feast, at = homeSite)
      .warbandsAt(homeSite, p1, 1).banksHoldingOnly(Suit.Hearth -> 5).ready
    val done = Outcomes.finished(fire(ready, power, hookAt(feast, homeSite)))
    assertEquals(said(done.events), Vector(took(1, Suit.Hearth)))

  test("played to a site nobody rules, with no other Hearth card, it takes " +
      "nothing"):
    val ready = Table.start.denizen(feast, at = homeSite)
      .banksHoldingOnly(Suit.Hearth -> 5).ready
    val done = Outcomes.finished(fire(ready, power, hookAt(feast, homeSite)))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} ruled no Hearth card.", covers = false)))

  test("with every bank empty it takes nothing, and the line says so"):
    val empty = ruling.banksHoldingOnly().ready
    val done = Outcomes.finished(play(empty, power, feast))
    assertEquals(said(done.events), Vector(NoteText.Said("empty",
      "Every favor bank was empty.", covers = false)))
