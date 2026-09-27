package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{CardStaging, MusterPowers, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class BirdsongSuite extends munit.FunSuite:
  import EconomyFixture._
  import SearchFixture.rules

  private val birdsong = DenizenId("176")
  private val modifiers = Vector(Birdsong.id)
  private val economy = MusterPowers.powers.map(_.id).toSet

  private def denizenOf(suit: Suit): DenizenId = DenizenId(catalog.denizens
    .find(d => d.suit == suit && d.id.value != birdsong.value &&
      !d.powers.exists(p => economy(p.id))).get.id.value)

  /** The actor holds Birdsong as an adviser; their site holds the plain card
    * and `source`, token-free. Both cards leave wherever the first game dealt
    * them, so neither is duplicated.
    */
  private def at(source: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    val ready = CardStaging.without(PowerFixture.asAdviser(
      CardStaging.without(act(), birdsong), birdsong, orientation), source)
    val site = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      site, c.map.sites(site).copy(denizens = Vector(plainId, source).distinct
        .map(id => DenizenState(id, Orientation.FaceUp, Tokens.empty)))))))

  /** Trades for favor with `card`, returning the result and the journal. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Trade,
      PowerFixture.actor, selected, Vector(DecisionOptionRef.Button("favor")))
      .toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def supply(ready: ReadyGame): Int =
    EconomyFixture.player(ready).board.supply.supply

  test("Birdsong is a registered free selected Trade modifier"):
    val power = Birdsong.forCatalog(catalog).get
    assertEquals(power.cardId, birdsong)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Trade))
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("trading with a beast card spends no Supply"):
    val beast = denizenOf(Suit.Beast)
    val ready = at(beast)
    val (transition, result) = trade(ready, modifiers, beast)
    assertEquals(supply(result), 7)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("trading with a nomad card spends no Supply"):
    val nomad = denizenOf(Suit.Nomad)
    assertEquals(supply(trade(at(nomad), modifiers, nomad)._2), 7)

  test("trading with a card of another suit pays the Supply"):
    val order = denizenOf(Suit.Order)
    assertEquals(supply(trade(at(order), modifiers, order)._2), 6)

  test("without the selection a beast card pays the Supply"):
    val beast = denizenOf(Suit.Beast)
    assertEquals(supply(trade(at(beast), Vector.empty, beast)._2), 6)

  test("it may be selected whatever the site holds, and only for a Trade"):
    val ready = at(denizenOf(Suit.Order))
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(ready,
      PowerFixture.actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Trade).contains(Birdsong.id))
    assert(!offered(ActionRef.Muster).contains(Birdsong.id))

  test("a facedown Birdsong is not offered"):
    val ready = at(denizenOf(Suit.Beast), Orientation.FaceDown)
    assert(!rules.offerableWalkerPowers(ready, PowerFixture.actor,
      ActionRef.Trade).toOption.get.map(_.id).contains(Birdsong.id))
