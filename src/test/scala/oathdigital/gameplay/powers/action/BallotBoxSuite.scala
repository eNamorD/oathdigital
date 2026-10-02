package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class BallotBoxSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val box = CatalogNames.denizen("Ballot Box")
  private val source = DecisionOptionRef.Denizen(box)
  private val power = BallotBox.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  // Denizens with no power of their own: one Hearth, two Order.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val codeOfHonor = CatalogNames.denizen("Code of Honor")
  private val messenger = CatalogNames.denizen("Messenger")

  /** p1's Act at Ancient City beside Ballot Box, site-only, with 2 favor and
    * an `adviser`. */
  private def staged(adviser: DenizenId): Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, home).denizen(box, at = home)
      .favor(p1, 2).adviser(p1, adviser)

  /** A faceup Hearth adviser, which matches Ballot Box itself. */
  private def matching: Table = staged(homesteaders)

  private def mine(ready: ReadyGame): ForceKind = Look(ready).forceKind(p1)

  /** `table` with p1's bank holding only `left` warbands. */
  private def banking(table: Table, left: Int): Table =
    val ready = table.ready
    table.boardWarbands(p1, ready.banks.warbandSupply(mine(ready)) - left)

  test("a matching adviser replaces an enemy's warbands with the player's"):
    val ready = matching.warbandsAt(home, p2, 2).ready
    val t = accepted(use(ready, power, source))
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(box), Tokens(2, 0))
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 2))
    assertEquals(Look(end).boardWarbands(p1), Look(ready).boardWarbands(p1))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"Replaced 2 ${p2.value} warbands at ${home.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assertWireRoundTrips(t.events)

  test("bandits are replaced too"):
    val t = accepted(use(matching.bandits(home, 3).ready, power, source))
    val end = after(t)
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 3))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.bandits", s"Replaced 3 bandit warbands at ${home.value}.",
      covers = true)))

  test("a short supply removes the warbands it cannot replace"):
    val ready = banking(matching.warbandsAt(home, p2, 3), 1).ready
    val t = accepted(use(ready, power, source))
    val end = after(t)
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 1))
    assertEquals(NoteText.said(power, t.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Replaced 1 ${p2.value} warband at ${home.value}.", covers = true),
      NoteText.Said("removed",
        s"Removed 2 ${p2.value} warbands at ${home.value}.", covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))

  test("an empty supply removes them all, and bandits refill the site"):
    val ready = banking(matching.warbandsAt(home, p2, 2), 0).ready
    val t = accepted(use(ready, power, source))
    // Ancient City's printed bandit capacity is 3.
    assertEquals(Look(after(t)).forces(home),
      SiteForces.Occupied(ForceKind.Bandit, 3))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "removed", s"Removed 2 ${p2.value} warbands at ${home.value}.",
      covers = false)))

  test("any card at the site can match, not only Ballot Box"):
    val ready = staged(codeOfHonor).denizen(messenger, at = home)
      .warbandsAt(home, p2, 1).ready
    val t = accepted(use(ready, power, source))
    val end = after(t)
    assertEquals(Look(end).forces(home), SiteForces.Occupied(mine(end), 1))

  test("with no matching adviser nothing changes, and the line says so"):
    val ready = staged(codeOfHonor).warbandsAt(home, p2, 2).ready
    val t = accepted(use(ready, power, source))
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).forces(home), Look(ready).forces(home))
    assertEquals(Look(after(t)).tokensOn(box), Tokens(2, 0))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.unmatched",
      s"${p1.value} had no adviser matching a card at ${home.value}.",
      covers = false)))

  test("the player's own warbands are not replaced"):
    val ready = matching.warbandsAt(home, p1, 2).ready
    val t = accepted(use(ready, power, source))
    assertEquals(Look(after(t)).forces(home), Look(ready).forces(home))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no warband to replace.",
      covers = false)))
