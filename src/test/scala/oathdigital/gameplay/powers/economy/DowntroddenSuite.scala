package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class DowntroddenSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val downtrodden = CatalogNames.denizen("Downtrodden")
  // A plain Beast card with no Muster power, mustered on in every test.
  private val beast = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(Downtrodden.id)

  /** `table` with every favor bank holding `each`, except as `banks` sets. */
  private def banksHolding(table: Table, each: Int, banks: (Suit, Int)*)
      : Table =
    val even = Suit.all.foldLeft(table)((t, suit) => t.bankFavor(suit, each))
    banks.foldLeft(even) { case (t, (suit, n)) => t.bankFavor(suit, n) }

  /** p1 holds Downtrodden (Discord) as a faceup adviser and stands at
    * Ancient City with Errand Boy. Every favor bank holds 3, except as
    * `banks` sets. p1 has the start's 1 favor, 3 warbands and 7 Supply. */
  private def board(banks: (Suit, Int)*): ReadyGame =
    banksHolding(Table.start.adviser(p1, downtrodden)
      .pawn(p1, at = "Ancient City").denizen(beast, at = "Ancient City")
      .boardWarbands(p1, 3).supply(p1, 7), 3, banks*).ready

  /** Musters from Errand Boy. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId])
      : EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, selected,
      DecisionOptionRef.Denizen(beast))

  test("mustering on a card whose bank strictly holds the least favor gains " +
      "two more warbands"):
    val ready = board(Suit.Beast -> 1)
    val done = muster(ready, modifiers)
    val result = done.result
    // 3 at the start, 1 the Muster gains, 2 Downtrodden adds.
    assertEquals(Look(result).boardWarbands(p1), 3 + 1 + 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      result)
    assertWireRoundTrips(done.journal.events)

  // Without Downtrodden's 2: 3 at the start and the 1 the Muster gains.
  test("a tie for least gives nothing, empty banks included"):
    assertEquals(Look(muster(board(Suit.Beast -> 1, Suit.Order -> 1),
      modifiers).result).boardWarbands(p1), 3 + 1)
    assertEquals(Look(muster(board(Suit.Beast -> 0, Suit.Order -> 0),
      modifiers).result).boardWarbands(p1), 3 + 1)

  test("a card whose bank is not the least gives nothing"):
    assertEquals(Look(muster(board(Suit.Order -> 1), modifiers).result)
      .boardWarbands(p1), 3 + 1)

  test("without the selection the least bank gives nothing"):
    assertEquals(Look(muster(board(Suit.Beast -> 1), Vector.empty).result)
      .boardWarbands(p1), 3 + 1)

  test("it is a Muster modifier only"):
    val offered = (action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(board(), p1, action)).map(_.id)
    assert(offered(ActionRef.Muster).contains(Downtrodden.id))
    assert(!offered(ActionRef.Trade).contains(Downtrodden.id))

  test("Village Idiot's favor comes after the check: a tie for least stays " +
      "a tie when both modifiers are selected"):
    val idiot = CatalogNames.denizen("Village Idiot")
    // Every bank holds 2, so Hearth, Village Idiot's suit, ties for least.
    // Village Idiot then takes 1 from the Hearth bank, which must not make
    // the bank the strict least before Downtrodden reads it.
    val ready = banksHolding(Table.start.adviser(p1, downtrodden)
      .pawn(p1, at = "Ancient City").denizen(idiot, at = "Ancient City"), 2)
      .ready
    // The order the player selects them in must not matter.
    Vector(Downtrodden.id, VillageIdiot.id).permutations.foreach: selected =>
      val result = EconomyActions.muster(rules, ready, p1, selected,
        DecisionOptionRef.Denizen(idiot)).result
      assertEquals(result.banks.favor.getOrElse(Suit.Hearth, 0), 1)
      assertEquals(Look(result).boardWarbands(p1), 3 + 1)
