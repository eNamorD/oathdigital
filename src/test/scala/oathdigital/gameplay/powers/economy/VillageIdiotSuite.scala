package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class VillageIdiotSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val idiot = CatalogNames.denizen("Village Idiot")
  private val other = CatalogNames.denizen("Errand Boy")
  private val modifiers = Vector(VillageIdiot.id)

  /** p1's site, Ancient City, holds Errand Boy (Beast) and Village Idiot
    * (Hearth), both token-free, and the Hearth bank holds `hearth` favor.
    * p1 has the start's 1 favor, 3 warbands and 7 Supply. */
  private def idiotAtSite(hearth: Int = 3): Table = Table.start
    .denizen(other, at = Table.homeOf(p1))
    .denizen(idiot, at = Table.homeOf(p1))
    .bankFavor(Suit.Hearth, hearth)

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster, p1,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def hearth(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Hearth, 0)

  test("mustering on Village Idiot gains a favor from the Hearth bank"):
    val ready = idiotAtSite().ready
    val (transition, result) = muster(ready, modifiers, idiot)
    // The Muster places p1's only favor on the card, then Village Idiot
    // gives one back from the Hearth bank.
    assertEquals(Look(result).favor(p1), 1)
    assertEquals(hearth(result), 2)
    assertEquals(Look(result).warbands(p1), 3 + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("mustering on another card, or without the selection, gains no favor"):
    val ready = idiotAtSite().ready
    assertEquals(hearth(muster(ready, modifiers, other)._2), 3)
    assertEquals(hearth(muster(ready, Vector.empty, idiot)._2), 3)

  test("an empty Hearth bank gives nothing, and the Muster still runs"):
    val (_, result) = muster(idiotAtSite(hearth = 0).ready, modifiers, idiot)
    assertEquals(Look(result).favor(p1), 0)
    assertEquals(Look(result).warbands(p1), 3 + 1)

  test("it is a Muster modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, p1, action).toOption.get.map(_.id)
    assert(offered(idiotAtSite().ready, ActionRef.Muster)
      .contains(VillageIdiot.id))
    assert(!offered(idiotAtSite().ready, ActionRef.Trade)
      .contains(VillageIdiot.id))
    val without = Table.start.denizen(other, at = Table.homeOf(p1)).ready
    assert(!offered(without, ActionRef.Muster).contains(VillageIdiot.id))
