package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class TownMeetingSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[TownMeeting]
  private val meeting = power.cardId
  private val played = hook(meeting)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)

  /** `table` with every bank empty except `stocks`. */
  private def banks(table: Table, stocks: (Suit, Int)*): Table =
    val held = stocks.toMap
    Suit.all.foldLeft(table)((next, suit) =>
      next.bankFavor(suit, held.getOrElse(suit, 0)))

  /** Three Hearth cards p1 rules: Town Meeting and two Hearth advisers. */
  private val ruling: Table = Table.start.adviser(p1, meeting)
    .adviser(p1, hearth(0)).adviser(p1, hearth(1))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def gained(n: Int): NoteText.Said = NoteText.Said("gained",
    s"${p1.value} gained $n favor.", covers = false)

  test("with more than X favor across two banks it asks for the split"):
    val ready = banks(ruling, Suit.Order -> 2, Suit.Hearth -> 2).ready
    val first = parked(play(ready, power, meeting))
    assertEquals(question(ready, power, played, first.tree).decisionId,
      TownMeeting.decisionId)
    val done = finished(resume(ready, power, played, first.tree,
      TownMeeting.decisionId, DecisionAnswer.DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.FavorBank(Suit.Order), 1),
        DistributeAmount(DecisionOptionRef.FavorBank(Suit.Hearth), 2)))))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(Vector(Suit.Order, Suit.Hearth).map(
      done.treeless.banks.favor(_)), Vector(1, 0))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(gained(3)))

  test("with X or less in all it takes everything without asking"):
    val done = finished(play(banks(ruling, Suit.Order -> 1,
      Suit.Hearth -> 1).ready, power, meeting))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(gained(2)))

  test("ruling no Hearth card it takes nothing, and the line says so"):
    val ready = banks(Table.start.denizen(meeting, at = homeSite),
      Suit.Hearth -> 5).ready
    val done = finished(fire(ready, power, hookAt(meeting, homeSite)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} ruled no Hearth card.", covers = false)))

  test("with every bank empty it takes nothing, and the line says so"):
    val done = finished(play(banks(ruling).ready, power, meeting))
    assertEquals(said(done.events), Vector(NoteText.Said("empty",
      "Every favor bank was empty.", covers = false)))
