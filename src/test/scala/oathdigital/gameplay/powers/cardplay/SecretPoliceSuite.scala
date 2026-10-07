package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

class SecretPoliceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val police = CatalogNames.denizen("Secret Police")
  private val other = Table.p2
  private val home = CatalogNames.site("Ancient City")
  private val elsewhere = CatalogNames.site("Broken Peaks")

  /** Secret Police faceup at `at`, on `table`; p1's pawn at Ancient City. */
  private def policeAt(table: Table, at: SiteId): Table =
    table.pawn(actor, home).denizen(police, at = at)

  /** A Search drawing `vision`, on the quiet table as `arrange` lays it. */
  private def search(vision: VisionId = VisionRules.Faith)(
      arrange: Table => Table): OathTransition =
    val drawing = SearchFixture.staged(Vector(vision), supply = 5)
    searched(arrange(Table.from(drawing)).unchecked, vision)

  test("an enemy at a site its ruler rules cannot play a Vision faceup"):
    // p2 rules Ancient City, where the Police stand and p1's pawn is.
    val parkedAt = search()(policeAt(_, home).warbandsAt(home, other, 2))
    assert(!offered(parkedAt).contains(PlayedAs.Faceup))
    assert(offered(parkedAt).contains(PlayedAs.Discarded))
    assert(offered(parkedAt).contains(PlayedAs.Facedown))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      PlayedAs.Faceup).isLeft)

  test("any site the ruler rules binds, not only the Police site"):
    // p2 rules Broken Peaks, where the Police stand, and Ancient City.
    assert(!offered(search()(policeAt(_, elsewhere)
      .warbandsAt(elsewhere, other, 2).warbandsAt(home, other, 2)))
      .contains(PlayedAs.Faceup))

  test("the Police site's own ruler plays freely"):
    // p1 rules Ancient City, where the Police stand.
    assert(offered(search()(policeAt(_, home).warbandsAt(home, actor, 2)))
      .contains(PlayedAs.Faceup))

  test("a pawn at a site with a different ruler is not bound"):
    // p2 rules the Police site, Broken Peaks; p1 rules Ancient City.
    assert(offered(search()(policeAt(_, elsewhere)
      .warbandsAt(elsewhere, other, 2).warbandsAt(home, actor, 2)))
      .contains(PlayedAs.Faceup))

  test("under Bandit rule every player at a Bandit site is bound"):
    assert(!offered(search()(policeAt(_, elsewhere).bandits(elsewhere, 2)
      .bandits(home, 2))).contains(PlayedAs.Faceup))

  test("the Conspiracy is forbidden too"):
    assert(!offered(search(VisionRules.Conspiracy)(policeAt(_, home)
      .warbandsAt(home, other, 2))).contains(PlayedAs.Faceup))

  test("a facedown Vision played from the advisers is bound too"):
    val ready = policeAt(Table.start, home).warbandsAt(home, other, 2).ready
    assert(!offered(fromAdvisers(ready, VisionRules.Faith))
      .contains(PlayedAs.Faceup))

  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = SecretPolice
    NoteText.said(power.id, power.noteKeys, from.events)

  test("the hidden faceup placement writes no line: its reason is on hover"):
    assertEquals(hidden(search()(policeAt(_, home)
      .warbandsAt(home, other, 2))), Vector.empty)

  test("a player the Police do not bind reads no line"):
    assertEquals(hidden(search()(policeAt(_, home)
      .warbandsAt(home, actor, 2))), Vector.empty)
