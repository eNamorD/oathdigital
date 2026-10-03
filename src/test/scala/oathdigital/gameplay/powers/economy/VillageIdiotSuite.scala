package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class VillageIdiotSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val idiot = CatalogNames.denizen("Village Idiot")
  private val other = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(VillageIdiot.id)

  /** p1's site, Ancient City, holds Errand Boy (Beast) and Village Idiot
    * (Hearth), both token-free, and the Hearth bank holds `hearth` favor.
    * p1 has the start's 1 favor, 3 warbands and 7 Supply. */
  private def idiotAtSite(hearth: Int = 3): Table = Table.start
    .pawn(p1, at = "Ancient City").denizen(other, at = "Ancient City")
    .denizen(idiot, at = "Ancient City")
    .favor(p1, 1).boardWarbands(p1, 3).supply(p1, 7)
    .bankFavor(Suit.Hearth, hearth)

  /** Musters from `card`. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, selected,
      DecisionOptionRef.Denizen(card))

  private def hearth(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Hearth, 0)

  test("mustering on Village Idiot gains a favor from the Hearth bank"):
    val ready = idiotAtSite().ready
    val done = muster(ready, modifiers, idiot)
    val result = done.result
    // The Muster places p1's only favor on the card, then Village Idiot
    // gives one back from the Hearth bank.
    assertEquals(Look(result).favor(p1), 1)
    assertEquals(hearth(result), 2)
    assertEquals(Look(result).boardWarbands(p1), 3 + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      result)
    assertWireRoundTrips(done.journal.events)

  test("mustering on another card, or without the selection, gains no favor"):
    val ready = idiotAtSite().ready
    assertEquals(hearth(muster(ready, modifiers, other).result), 3)
    assertEquals(hearth(muster(ready, Vector.empty, idiot).result), 3)

  test("an empty Hearth bank gives nothing, and the Muster still runs"):
    val result = muster(idiotAtSite(hearth = 0).ready, modifiers, idiot).result
    assertEquals(Look(result).favor(p1), 0)
    assertEquals(Look(result).boardWarbands(p1), 3 + 1)

  test("it is a Muster modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(idiotAtSite().ready, ActionRef.Muster)
      .contains(VillageIdiot.id))
    assert(!offered(idiotAtSite().ready, ActionRef.Trade)
      .contains(VillageIdiot.id))
    val without = Table.start.pawn(p1, at = "Ancient City")
      .denizen(other, at = "Ancient City").ready
    assert(!offered(without, ActionRef.Muster).contains(VillageIdiot.id))
