package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

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
    .denizen(alchemist, at = Table.homeOf(p1))
    .favor(p1, 4).secrets(p1, faceUp = 2)

  /** Trades for favor with Alchemist, returning the result. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId])
      : (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      selected, Vector(DecisionOptionRef.Button("favor"))).toOption.get
    val done = rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def supplyAfter(ready: ReadyGame, selected: Vector[PowerId]): Int =
    Look(trade(ready, selected)._2).supply(p1)

  test("trading costs the printed Supply without the Cup"):
    assertEquals(supplyAfter(held.ready, Vector.empty), 6)

  test("a player with no faceup adviser trades for no Supply"):
    val ready = held.ready
    val (transition, result) = trade(ready, modifiers)
    assertEquals(Look(result).supply(p1), 7)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("a faceup adviser of another suit does not stop the free trade"):
    // Wrestlers is Order; the traded Alchemist is Arcane.
    assertEquals(supplyAfter(held.adviser(p1, "Wrestlers").ready, modifiers), 7)

  test("a faceup adviser of the traded card's suit makes the trade cost Supply"):
    // Magician's Code is Arcane, like Alchemist.
    assertEquals(supplyAfter(held.adviser(p1, "Magician's Code").ready,
      modifiers), 6)

  test("a facedown adviser of the traded card's suit does not count"):
    assertEquals(supplyAfter(held.adviser(p1, "Magician's Code",
      facedown = true).ready, modifiers), 7)

  test("it is a Trade modifier, and only while the relic is faceup in play"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, p1, action).toOption.get.map(_.id)
    assert(offered(held.ready, ActionRef.Trade).contains(CupOfPlenty.id))
    assert(!offered(held.ready, ActionRef.Muster).contains(CupOfPlenty.id))
    val facedown = held.relic(p1, cup, facedown = true).ready
    assert(!offered(facedown, ActionRef.Trade).contains(CupOfPlenty.id))
