package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}

class VowOfWanderingSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val beastkin = CatalogNames.denizen("Vow of Beastkin")
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen to Muster on. It matches no Nomad adviser. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Errand Boy, with 1 favor and 7 Supply to pay
    * a Muster, and no adviser yet. */
  private def besideErrandBoy: Table = Table.start.pawn(p1, at = home)
    .denizen(errandBoy, at = home).favor(p1, 1).supply(p1, 7)

  private def musteredOnErrandBoy(ready: ReadyGame)
      : EconomyActions.Completed = EconomyActions.muster(rules, ready, p1,
    Vector.empty, DecisionOptionRef.Denizen(errandBoy))

  private def gainedLine(n: Int): NoteText.Said = NoteText.Said("gained",
    s"Gained $n more ${if n == 1 then "warband" else "warbands"}.",
    covers = false)

  private def wanderingSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfWanderingMuster.id, VowOfWanderingMuster.noteKeys,
      events)

  test("every Muster gains two more warbands, and the line says so"):
    val ready = besideErrandBoy.adviser(p1, vow).ready
    val done = musteredOnErrandBoy(ready)
    // 1, plus none for a matching adviser, plus the Vow's 2 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(2)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("with Vow of Beastkin, each vow's line tells its own part"):
    val ready = besideErrandBoy.adviser(p1, beastkin).adviser(p1, vow).ready
    val done = musteredOnErrandBoy(ready)
    // 1, plus 1 for Vow of Beastkin, a Beast adviser, plus 1 and 2 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 5)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(2)))
    assertEquals(NoteText.said(VowOfBeastkin.id,
      VowOfBeastkin.forCatalog(catalog).noteKeys, done.journal.events),
      Vector(gainedLine(1)))

  test("a short bank gives what it holds, and the line says what came"):
    val ready = besideErrandBoy.adviser(p1, vow).warbandsInBank(p1, 2).ready
    val done = musteredOnErrandBoy(ready)
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 2)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(1)))

  test("another player's Vow, or a facedown one, changes nothing"):
    val rivalHolds = besideErrandBoy.adviser(p2, vow).ready
    val facedown = besideErrandBoy.adviser(p1, vow, facedown = true).ready
    Vector(rivalHolds, facedown).foreach { ready =>
      val done = musteredOnErrandBoy(ready)
      assertEquals(Look(done.result).boardWarbands(p1),
        Look(ready).boardWarbands(p1) + 1)
      assertEquals(wanderingSaid(done.journal.events), Vector.empty)
    }
