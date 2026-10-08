package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class TerrorSpellsSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val spells = CatalogNames.denizen("Terror Spells")
  private val source = DecisionOptionRef.Denizen(spells)
  /** p1's pawn site and Broken Peaks, p2's, lie in the Cradle; Buried
    * Giant, p3's, lies in the Provinces. */
  private val home = CatalogNames.site("Ancient City")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")

  /** p1's Act at Ancient City beside Terror Spells, holding the Darkest
    * Secret and the faceup secret that pays for it. p2 stands at Broken
    * Peaks and p3 at Buried Giant. Every board holds 3 warbands. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).pawn(p2, at = peaks).pawn(p3, at = giant)
    .denizen(spells, at = home).secrets(p1, faceUp = 1)
    .darkestSecret(Some(p1), 1)
    .boardWarbands(p1, 3).boardWarbands(p2, 3).boardWarbands(p3, 3)

  private def killing(amounts: (DecisionOptionRef, Int)*): DecisionAnswer =
    DecisionAnswer.DistributeAnswer(amounts.toVector.map((ref, amount) =>
      DistributeAmount(ref, amount)))

  test("without the Darkest Secret it pays its cost and kills nothing"):
    val done = accepted(use(staged.darkestSecret(None, 1).ready, TerrorSpells,
      source))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals(end.tokensOn(spells), Tokens(0, 1))
    assertEquals(Vector(p1, p2, p3).map(end.boardWarbands), Vector(3, 3, 3))
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said("used.none", "The Darkest Secret was not held.",
        covers = false)))

  test("with one way to kill two it asks nothing, the actor's own board " +
      "included"):
    val done = accepted(use(staged.boardWarbands(p2, 0).ready, TerrorSpells,
      source))
    assert(backToActing(done))
    assertEquals(Look(after(done)).boardWarbands(p1), 1)
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Killed 2 warbands.", covers = false)))

  test("two warbands in two places both die without asking"):
    val done = accepted(use(staged.boardWarbands(p1, 1).boardWarbands(p2, 1)
      .ready, TerrorSpells, source))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals(Vector(p1, p2, p3).map(end.boardWarbands), Vector(0, 0, 3))

  test("it asks how two die across the region's sites and boards, and " +
      "offers nothing elsewhere"):
    val ready = staged.boardWarbands(p1, 0).bandits(peaks, 2)
      .warbandsAt(giant, p3, 1).ready
    val started = accepted(use(ready, TerrorSpells, source))
    assert(awaits(started, TerrorSpells.decisionId),
      parked.parkedDecision(started.state).toString)
    assertEquals(offered(started, p1), Some(Vector("site" -> peaks.value,
      "player" -> p2.value)))
    val done = accepted(answer(started, p1, TerrorSpells.decisionId,
      killing(DecisionOptionRef.Site(peaks) -> 1,
        DecisionOptionRef.Player(p2) -> 1)))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals(end.forces(peaks), SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(end.boardWarbands(p2), 2)
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Killed 2 warbands.", covers = false)))
    val events = started.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("with no warband in the region the line says so"):
    val done = accepted(use(staged.boardWarbands(p1, 0).boardWarbands(p2, 0)
      .ready, TerrorSpells, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said("used.empty", "No warband was in the region.",
        covers = false)))
