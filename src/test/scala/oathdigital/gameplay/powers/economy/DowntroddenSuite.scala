package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class DowntroddenSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val downtrodden = CatalogNames.denizen("Downtrodden")
  // A plain Beast card with no Muster power, mustered on in every test.
  private val beast = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(Downtrodden.id)

  /** p1 holds Downtrodden (Discord) as a faceup adviser and stands at
    * Ancient City with Errand Boy. Every favor bank holds 3, except as
    * `banks` sets. p1 has the start's 1 favor, 3 warbands and 7 Supply. */
  private def board(banks: (Suit, Int)*): ReadyGame =
    val even = Suit.all.foldLeft(Table.start.adviser(p1, downtrodden)
      .denizen(beast, at = Table.homeOf(p1)))((table, suit) =>
      table.bankFavor(suit, 3))
    banks.foldLeft(even) { case (table, (suit, n)) => table.bankFavor(suit, n) }
      .ready

  /** Musters from Errand Boy and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId])
      : (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster, p1,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(beast)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  test("mustering on a card whose bank strictly holds the least favor gains " +
      "two more warbands"):
    val ready = board(Suit.Beast -> 1)
    val (transition, result) = muster(ready, modifiers)
    assertEquals(Look(result).warbands(p1), 3 + 1 + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("a tie for least gives nothing, empty banks included"):
    assertEquals(Look(muster(board(Suit.Beast -> 1, Suit.Order -> 1),
      modifiers)._2).warbands(p1), 3 + 1)
    assertEquals(Look(muster(board(Suit.Beast -> 0, Suit.Order -> 0),
      modifiers)._2).warbands(p1), 3 + 1)

  test("a card whose bank is not the least gives nothing"):
    assertEquals(Look(muster(board(Suit.Order -> 1), modifiers)._2)
      .warbands(p1), 3 + 1)

  test("without the selection the least bank gives nothing"):
    assertEquals(Look(muster(board(Suit.Beast -> 1), Vector.empty)._2)
      .warbands(p1), 3 + 1)

  test("it is a Muster modifier only"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(board(),
      p1, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(Downtrodden.id))
    assert(!offered(ActionRef.Trade).contains(Downtrodden.id))

  test("Village Idiot's favor comes after the check: a tie for least stays " +
      "a tie when both modifiers are selected"):
    val idiot = CatalogNames.denizen("Village Idiot")
    // Every bank holds 2, so Hearth, Village Idiot's suit, ties for least.
    // Village Idiot then takes 1 from the Hearth bank, which must not make
    // the bank the strict least before Downtrodden reads it.
    val ready = Suit.all.foldLeft(Table.start.adviser(p1, downtrodden)
      .denizen(idiot, at = Table.homeOf(p1)))((table, suit) =>
      table.bankFavor(suit, 2)).ready
    // The order the player selects them in must not matter.
    Vector(Downtrodden.id, VillageIdiot.id).permutations.foreach: selected =>
      val started = rules.startWalker(Ready(ready), ActionRef.Muster, p1,
        selected).toOption.get
      val result = rules.resolveWalker(started.state, p1,
        MusterProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(idiot)))
        .toOption.get.state.asInstanceOf[Ready].value
      assertEquals(result.banks.favor.getOrElse(Suit.Hearth, 0), 1)
      assertEquals(Look(result).warbands(p1), 3 + 1)
