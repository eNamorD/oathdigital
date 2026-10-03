package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class RowdyPubSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val pub = CatalogNames.denizen("Rowdy Pub")
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers = Vector(RowdyPub.id)

  /** p1's site, Ancient City, holds Rowdy Pub (Hearth) and Alchemist, both
    * token-free. p1 has the start's 3 warbands, and its 1 favor and 7
    * Supply pay for a Muster. */
  private def pubAtSite: Table = Table.start.pawn(p1, at = "Ancient City")
    .denizen(alchemist, at = "Ancient City").denizen(pub, at = "Ancient City")
    .favor(p1, 1).boardWarbands(p1, 3).supply(p1, 7)

  /** Musters from `card`. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, selected,
      DecisionOptionRef.Denizen(card))

  test("mustering from Rowdy Pub gains one more warband"):
    val ready = pubAtSite.ready
    val done = muster(ready, modifiers, pub)
    val result = done.result
    // 3 at the start, 1 the Muster gains, 1 the Pub adds.
    assertEquals(Look(result).boardWarbands(p1), 3 + 1 + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      result)
    assertWireRoundTrips(done.journal.events)

  test("the extra warband is on top of the matching-adviser bonus"):
    // Extra Provisions is Hearth, like Rowdy Pub.
    val ready = pubAtSite.adviser(p1, "Extra Provisions").ready
    val result = muster(ready, modifiers, pub).result
    // 3 at the start, 1 the Muster gains, 1 the Pub, 1 the matching adviser.
    assertEquals(Look(result).boardWarbands(p1), 3 + 1 + 1 + 1)

  test("mustering from another card, or without the selection, gains no extra"):
    val ready = pubAtSite.ready
    // 3 at the start and the 1 the Muster gains.
    assertEquals(Look(muster(ready, modifiers, alchemist).result)
      .boardWarbands(p1), 3 + 1)
    assertEquals(Look(muster(ready, Vector.empty, pub).result)
      .boardWarbands(p1), 3 + 1)

  test("it is a Muster modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(pubAtSite.ready, ActionRef.Muster).contains(RowdyPub.id))
    assert(!offered(pubAtSite.ready, ActionRef.Trade).contains(RowdyPub.id))
    val withoutPub = Table.start.pawn(p1, at = "Ancient City")
      .denizen(alchemist, at = "Ancient City").ready
    assert(!offered(withoutPub, ActionRef.Muster).contains(RowdyPub.id))
