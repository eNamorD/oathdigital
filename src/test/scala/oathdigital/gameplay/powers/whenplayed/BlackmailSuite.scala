package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}

class BlackmailSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Blackmail]
  private val blackmail = power.cardId
  private val played = hook(blackmail)
  private val horn = CatalogNames.relic("Cracked Horn")
  private val ring = CatalogNames.relic("Ring of Devotion")
  private val trumpet = CatalogNames.relic("Painted Trumpet")

  /** Blackmail as p1's adviser. p2 stands at p1's site holding the Cracked
    * Horn, faceup unless `facedown`, and `favor`. p1 holds the Ring of
    * Devotion, and p3, away at Buried Giant, the Painted Trumpet. */
  private def p2HoldingTheHorn(favor: Int, facedown: Boolean = false): Table =
    Table.start.adviser(p1, blackmail).pawn(p2, homeSite)
      .relic(p2, horn, facedown).favor(p2, favor)
      .relic(p1, ring).relic(p3, trumpet)

  private def tookTheHorn: NoteText.Said = NoteText.Said("took",
    s"Took ${horn.value} from ${p2.value}.", covers = false)

  test("the holder gives 3 favor to keep their relic, and only other " +
      "players' relics at p1's site are offered"):
    val ready = p2HoldingTheHorn(favor = 4).ready
    val walked = walkAnswering(ready, power, played,
      chooseOne(DecisionOptionRef.Relic(horn)), chooseOne(Blackmail.pay))
    assertEquals(walked.asked.map(_.owner), Vector(p1, p2))
    assertEquals(chooseOneOptions(walked.asked.head), Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(horn))))
    val end = Look(walked.end)
    // p1's 1 favor and the 3 p2 gave.
    assertEquals(end.favor(p1), 1 + 3)
    assertEquals(end.favor(p2), 4 - 3)
    assertEquals(end.relics(p2), Vector(horn))
    assertEquals(said(power, walked.events), Vector(NoteText.Said("paid",
      s"${p2.value} gave 3 favor to keep ${horn.value}.", covers = false)))
    assertEquals(replayed(ready, walked.events), walked.end)
    assertWireRoundTrips(walked.events)

  test("the holder may hand the relic over instead"):
    val walked = walkAnswering(p2HoldingTheHorn(favor = 4).ready, power,
      played, chooseOne(DecisionOptionRef.Relic(horn)), chooseOne(Blackmail.handOver))
    val end = Look(walked.end)
    assertEquals(end.relics(p1), Vector(ring, horn))
    assertEquals(end.relics(p2), Vector.empty)
    assertEquals(end.favor(p2), 4)
    assertEquals(said(power, walked.events), Vector(tookTheHorn))

  test("a facedown relic is offered by its slot, and a holder with fewer " +
      "than 3 favor is not asked"):
    val slot = DecisionOptionRef.RelicSlot(p2, 0)
    val walked = walkAnswering(
      p2HoldingTheHorn(favor = 2, facedown = true).ready, power, played,
      chooseOne(slot))
    assertEquals(walked.asked.map(_.owner), Vector(p1))
    assertEquals(chooseOneOptions(walked.asked.head),
      Vector(DecisionOption.RelicSlot(slot)))
    val end = Look(walked.end)
    assertEquals(end.relics(p1), Vector(ring, horn))
    assertEquals(end.favor(p2), 2)
    assertEquals(said(power, walked.events), Vector(tookTheHorn))

  test("a relic Lost Tongue protects is not offered, and with none left the " +
      "line says so"):
    // p2 holds Lost Tongue faceup, and p1 rules no Nomad card.
    val ready = p2HoldingTheHorn(favor = 4).adviser(p2, "Lost Tongue").ready
    val done = Outcomes.finished(play(ready, power, blackmail))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "No relic could be taken.", covers = false)))
