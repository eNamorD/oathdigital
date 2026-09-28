package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BirdsongSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val birdsong = CatalogNames.denizen("Birdsong")
  private val modifiers = Vector(Birdsong.id)
  // Plain cards of each suit the tests trade with: none has a Muster or
  // Trade power of its own.
  private val beast = CatalogNames.denizen("Errand Boy")
  private val nomad = CatalogNames.denizen("Rain Boots")
  private val order = CatalogNames.denizen("Wrestlers")

  /** p1 holds Birdsong as an adviser; p1's site, Ancient City, holds
    * Alchemist and `source`, token-free. p1 has 4 favor, 2 faceup secrets
    * and 7 Supply. */
  private def at(source: DenizenId, facedown: Boolean = false): ReadyGame =
    Table.start
      .adviser(p1, birdsong, facedown = facedown)
      .denizen("Alchemist", at = Table.homeOf(p1))
      .denizen(source, at = Table.homeOf(p1))
      .favor(p1, 4).secrets(p1, faceUp = 2)
      .ready

  /** Trades for favor with `card`, returning the result and the journal. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      selected, Vector(DecisionOptionRef.Button("favor"))).toOption.get
    val done = rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  test("Birdsong is a registered free selected Trade modifier"):
    val power = Birdsong.forCatalog(catalog).get
    assertEquals(power.cardId, birdsong)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Trade))
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("trading with a beast card spends no Supply"):
    val ready = at(beast)
    val (transition, result) = trade(ready, modifiers, beast)
    assertEquals(Look(result).supply(p1), 7)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("trading with a nomad card spends no Supply"):
    assertEquals(Look(trade(at(nomad), modifiers, nomad)._2).supply(p1), 7)

  test("trading with a card of another suit pays the Supply"):
    assertEquals(Look(trade(at(order), modifiers, order)._2).supply(p1), 6)

  test("without the selection a beast card pays the Supply"):
    assertEquals(Look(trade(at(beast), Vector.empty, beast)._2).supply(p1), 6)

  test("it may be selected whatever the site holds, and only for a Trade"):
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(at(order),
      p1, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Trade).contains(Birdsong.id))
    assert(!offered(ActionRef.Muster).contains(Birdsong.id))

  test("a facedown Birdsong is not offered"):
    assert(!rules.offerableWalkerPowers(at(beast, facedown = true), p1,
      ActionRef.Trade).toOption.get.map(_.id).contains(Birdsong.id))
