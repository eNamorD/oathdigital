package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class RowdyPubSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val pub = CatalogNames.denizen("Rowdy Pub")
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers = Vector(RowdyPub.id)

  /** p1's site, Ancient City, holds Rowdy Pub (Hearth) and Alchemist, both
    * token-free. p1 has the start's 3 warbands, and its 1 favor and 7
    * Supply pay for a Muster. */
  private def pubAtSite: Table = Table.start
    .denizen(alchemist, at = Table.homeOf(p1))
    .denizen(pub, at = Table.homeOf(p1))

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

  test("mustering from Rowdy Pub gains one more warband"):
    val ready = pubAtSite.ready
    val (transition, result) = muster(ready, modifiers, pub)
    assertEquals(Look(result).warbands(p1), 3 + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("the extra warband is on top of the matching-adviser bonus"):
    // Extra Provisions is Hearth, like Rowdy Pub.
    val ready = pubAtSite.adviser(p1, "Extra Provisions").ready
    val (_, result) = muster(ready, modifiers, pub)
    assertEquals(Look(result).warbands(p1), 3 + 3)

  test("mustering from another card, or without the selection, gains no extra"):
    val ready = pubAtSite.ready
    assertEquals(Look(muster(ready, modifiers, alchemist)._2).warbands(p1), 3 + 1)
    assertEquals(Look(muster(ready, Vector.empty, pub)._2).warbands(p1), 3 + 1)

  test("it is a Muster modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, p1, action).toOption.get.map(_.id)
    assert(offered(pubAtSite.ready, ActionRef.Muster).contains(RowdyPub.id))
    assert(!offered(pubAtSite.ready, ActionRef.Trade).contains(RowdyPub.id))
    val withoutPub = Table.start.denizen(alchemist, at = Table.homeOf(p1)).ready
    assert(!offered(withoutPub, ActionRef.Muster).contains(RowdyPub.id))
