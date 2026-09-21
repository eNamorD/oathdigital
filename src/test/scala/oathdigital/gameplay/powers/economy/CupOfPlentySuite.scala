package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class CupOfPlentySuite extends munit.FunSuite {
  import EconomyFixture._
  import SearchFixture.rules

  private val cup = RelicId("R07")
  private val modifiers = Vector(CupOfPlenty.id)
  /** A suit that is not the traded card's, for an adviser that does not match. */
  private val otherSuit = catalog.denizens.find(d => d.suit != plain.suit).get
  private val otherId = DenizenId(otherSuit.id.value)

  private def held(advisers: Vector[AdviserState]): ReadyGame =
    PowerFixture.withRelic(act(advisers = advisers), cup)

  /** Trades for favor with the plain card at the site, returning the result. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId])
      : (OathTransition, ReadyGame) = {
    val started = rules.startWalker(Ready(ready), ActionRef.Trade,
      PowerFixture.actor, selected, Vector(DecisionOptionRef.Button("favor")))
      .toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(plainId)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)
  }

  private def supply(ready: ReadyGame): Int = player(ready).board.supply.supply

  test("the Cup is a registered selected Trade modifier") {
    val power = CupOfPlenty.forCatalog(catalog).get
    assertEquals(power.cardId, cup)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Trade))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("trading costs the printed Supply without the Cup") {
    assertEquals(supply(trade(held(Vector.empty), Vector.empty)._2), 6)
  }

  test("a player with no faceup adviser trades for no Supply") {
    val ready = held(Vector.empty)
    val (transition, result) = trade(ready, modifiers)
    assertEquals(supply(result), 7)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))
  }

  test("a faceup adviser of another suit does not stop the free trade") {
    val adviser = DenizenState(otherId, Orientation.FaceUp, Tokens.empty)
    assertEquals(supply(trade(held(Vector(adviser)), modifiers)._2), 7)
  }

  test("a faceup adviser of the traded card's suit makes the trade cost Supply") {
    assertEquals(supply(trade(held(Vector(matchingAdviser)), modifiers)._2), 6)
  }

  test("a facedown adviser of the traded card's suit does not count") {
    val facedown = DenizenState(matchingId, Orientation.FaceDown, Tokens.empty)
    assertEquals(supply(trade(held(Vector(facedown)), modifiers)._2), 7)
  }

  test("it is a Trade modifier, and only while the relic is faceup in play") {
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, PowerFixture.actor, action)
        .toOption.get.map(_.id)
    assert(offered(held(Vector.empty), ActionRef.Trade).contains(CupOfPlenty.id))
    assert(!offered(held(Vector.empty), ActionRef.Muster).contains(CupOfPlenty.id))
    val facedown = PowerFixture.withRelic(act(), cup, Orientation.FaceDown)
    assert(!offered(facedown, ActionRef.Trade).contains(CupOfPlenty.id))
  }

  private def player(ready: ReadyGame): PlayerState =
    EconomyFixture.player(ready)
}
