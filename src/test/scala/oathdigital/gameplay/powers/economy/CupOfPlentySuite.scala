package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class CupOfPlentySuite extends munit.FunSuite:
  import SearchFixture.rules

  private val cup = CatalogNames.relic("Cup of Plenty")
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers = Vector(CupOfPlenty.id)

  /** p1 holds the Cup faceup and stands at Ancient City, which holds the
    * token-free Alchemist (Arcane). p1 has 4 favor, 2 faceup secrets and 7
    * Supply. */
  private def held: Table = Table.start
    .relic(p1, cup)
    .pawn(p1, at = "Ancient City").denizen(alchemist, at = "Ancient City")
    .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, 7)

  /** Trades for favor with Alchemist. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId])
      : EconomyActions.Completed =
    EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(alchemist))

  private def supplyAfter(ready: ReadyGame, selected: Vector[PowerId]): Int =
    Look(trade(ready, selected).result).supply(p1)

  test("trading costs the printed Supply without the Cup"):
    // A Trade costs 1 Supply: 7 - 1.
    assertEquals(supplyAfter(held.ready, Vector.empty), 7 - 1)

  test("a player with no faceup adviser trades for no Supply"):
    val ready = held.ready
    val done = trade(ready, modifiers)
    val result = done.result
    assertEquals(Look(result).supply(p1), 7)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      result)
    assertWireRoundTrips(done.journal.events)

  test("a faceup adviser of another suit does not stop the free trade"):
    // Wrestlers is Order; the traded Alchemist is Arcane.
    assertEquals(supplyAfter(held.adviser(p1, "Wrestlers").ready, modifiers), 7)

  test("a faceup adviser of the traded card's suit makes the trade cost " +
      "Supply"):
    // Magician's Code is Arcane, like Alchemist.
    assertEquals(supplyAfter(held.adviser(p1, "Magician's Code").ready,
      modifiers), 7 - 1)

  test("a facedown adviser of the traded card's suit does not count"):
    assertEquals(supplyAfter(held.adviser(p1, "Magician's Code",
      facedown = true).ready, modifiers), 7)

  test("it is a Trade modifier, and only while the relic is faceup in play"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(held.ready, ActionRef.Trade).contains(CupOfPlenty.id))
    assert(!offered(held.ready, ActionRef.Muster).contains(CupOfPlenty.id))
    val facedown = held.relic(p1, cup, facedown = true).ready
    assert(!offered(facedown, ActionRef.Trade).contains(CupOfPlenty.id))
