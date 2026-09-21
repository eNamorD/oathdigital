package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{CardStaging, MusterPowers, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class RowdyPubSuite extends munit.FunSuite {
  import EconomyFixture._
  import SearchFixture.rules

  private val pub = DenizenId("144")
  private val modifiers = Vector(RowdyPub.id)

  /** The actor's site holds Rowdy Pub and the plain card, both token-free. */
  private def pubAtSite(advisers: Vector[AdviserState] = Vector.empty)
      : ReadyGame = {
    val ready = CardStaging.without(act(advisers = advisers), pub)
    val siteId = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      siteId, c.map.sites(siteId).copy(denizens = Vector(
        DenizenState(plainId, Orientation.FaceUp, Tokens.empty),
        DenizenState(pub, Orientation.FaceUp, Tokens.empty)))))))
  }

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId): (OathTransition, ReadyGame) = {
    val started = rules.startWalker(Ready(ready), ActionRef.Muster,
      PowerFixture.actor, selected).toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)
  }

  private def warbands(ready: ReadyGame): Int =
    EconomyFixture.player(ready).board.warbands

  test("Rowdy Pub is a registered selected Muster modifier") {
    val power = RowdyPub.forCatalog(catalog).get
    assertEquals(power.cardId, pub)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("mustering from Rowdy Pub gains one more warband") {
    val ready = pubAtSite()
    val (transition, result) = muster(ready, modifiers, pub)
    assertEquals(warbands(result), warbands(ready) + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))
  }

  test("the extra warband is on top of the matching-adviser bonus") {
    val ready = pubAtSite()
    val economy = MusterPowers.powers.map(_.id).toSet
    val hearthAdviser = catalog.denizens.find(d => d.suit == Suit.Hearth &&
      d.id.value != pub.value && !d.powers.exists(p => economy(p.id))).get
    val adviser = DenizenState(DenizenId(hearthAdviser.id.value),
      Orientation.FaceUp, Tokens.empty)
    val withAdviser = pubAtSite(Vector(adviser))
    val (_, result) = muster(withAdviser, modifiers, pub)
    assertEquals(warbands(result), warbands(ready) + 3)
  }

  test("mustering from another card, or without the selection, gains no extra") {
    val ready = pubAtSite()
    assertEquals(warbands(muster(ready, modifiers, plainId)._2),
      warbands(ready) + 1)
    assertEquals(warbands(muster(ready, Vector.empty, pub)._2),
      warbands(ready) + 1)
  }

  test("it is a Muster modifier, offered when the card is in reach") {
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(pubAtSite(),
      PowerFixture.actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(RowdyPub.id))
    assert(!offered(ActionRef.Trade).contains(RowdyPub.id))
    assert(!rules.offerableWalkerPowers(act(), PowerFixture.actor,
      ActionRef.Muster).toOption.get.map(_.id).contains(RowdyPub.id))
  }
}
