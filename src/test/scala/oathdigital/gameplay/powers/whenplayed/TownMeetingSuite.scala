package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.p1

class TownMeetingSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[TownMeeting]
  private val meeting = power.cardId
  private val played = hook(meeting)

  /** Three Hearth cards p1 rules: Town Meeting and the advisers Homesteaders
    * and Land Warden. p1 holds 1 favor.
    */
  private val ruling: Table = Table.start.adviser(p1, meeting)
    .adviser(p1, "Homesteaders").adviser(p1, "Land Warden").favor(p1, 1)

  private def gained(n: Int): NoteText.Said = NoteText.Said("gained",
    s"${p1.value} gained $n favor.", covers = false)

  test("with more than X favor across two banks it asks for the split"):
    val ready = ruling.banksHoldingOnly(Suit.Order -> 2, Suit.Hearth -> 2).ready
    val first = Outcomes.parked(play(ready, power, meeting))
    assertEquals(question(ready, power, played, first.tree).decisionId,
      TownMeeting.decisionId)
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      TownMeeting.decisionId, DecisionAnswer.DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.FavorBank(Suit.Order), 1),
        DistributeAmount(DecisionOptionRef.FavorBank(Suit.Hearth), 2)))))
    // p1's 1 favor and the 3 the split takes.
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(Vector(Suit.Order, Suit.Hearth).map(
      done.treeless.banks.favor(_)), Vector(1, 0))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(power, done.events), Vector(gained(3)))

  test("with X or less in all it takes everything without asking"):
    val ready = ruling.banksHoldingOnly(Suit.Order -> 1, Suit.Hearth -> 1).ready
    val done = Outcomes.finished(play(ready, power, meeting))
    // p1's 1 favor and both banks' 1.
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(power, done.events), Vector(gained(2)))

  test("ruling no Hearth card it takes nothing, and the line says so"):
    val ready = Table.start.denizen(meeting, at = homeSite)
      .banksHoldingOnly(Suit.Hearth -> 5).ready
    val done = Outcomes.finished(fire(ready, power, hookAt(meeting, homeSite)))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      s"${p1.value} ruled no Hearth card.", covers = false)))

  test("with every bank empty it takes nothing, and the line says so"):
    val empty = ruling.banksHoldingOnly().ready
    val done = Outcomes.finished(play(empty, power, meeting))
    assertEquals(said(power, done.events), Vector(NoteText.Said("empty",
      "Every favor bank was empty.", covers = false)))
