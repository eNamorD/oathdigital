package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.EconomyFixture
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{CardStaging, MusterPowers, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class AnimalPlaymatesSuite extends munit.FunSuite:
  import EconomyFixture._
  import SearchFixture.rules

  private val playmates = DenizenId("40")
  private val modifiers = Vector(AnimalPlaymates.id)
  private val economy = MusterPowers.powers.map(_.id).toSet

  /** A token-free denizen of `suit` with no Muster or Trade power. */
  private def denizenOf(suit: Suit): DenizenId = DenizenId(catalog.denizens
    .find(d => d.suit == suit && d.id.value != playmates.value &&
      !d.powers.exists(p => economy(p.id))).get.id.value)

  /** A beast edifice whose intact face has no Muster or Trade power. */
  private val beastEdifice: EdificeId = EdificeId(catalog.edifices
    .find(e => e.suit == Suit.Beast &&
      !e.intact.powers.exists(p => economy(p.id))).get.id.value)

  /** The actor holds Animal Playmates as a faceup adviser; their site holds
    * the plain card and `source`.
    */
  private def at(source: SiteDenizenState,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    val ready = CardStaging.without(act(advisers = Vector(
      DenizenState(playmates, orientation, Tokens.empty))), source.id)
    val site = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      site, c.map.sites(site).copy(denizens = (Vector[SiteDenizenState](
        DenizenState(plainId, Orientation.FaceUp, Tokens.empty), source))
        .distinctBy(_.id))))))

  private def denizen(id: DenizenId): SiteDenizenState =
    DenizenState(id, Orientation.FaceUp, Tokens.empty)

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DecisionOptionRef): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster,
      PowerFixture.actor, selected).toOption.get
    val done = rules.resolveWalker(started.state, PowerFixture.actor,
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(card))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  private def supply(ready: ReadyGame): Int =
    EconomyFixture.player(ready).board.supply.supply

  test("Animal Playmates is a registered free selected Muster modifier"):
    val power = AnimalPlaymates.forCatalog(catalog).get
    assertEquals(power.cardId, playmates)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.cost, Cost.free)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("mustering on a beast denizen spends no Supply"):
    val beast = denizenOf(Suit.Beast)
    val ready = at(denizen(beast))
    val (transition, result) = muster(ready, modifiers,
      DecisionOptionRef.Denizen(beast))
    assertEquals(supply(result), 7)
    assertEquals(PaidActionHarness.tokensOn(result, beast), Tokens(1, 0))
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("mustering on a beast edifice spends no Supply"):
    val ready = at(EdificeState(beastEdifice, EdificeSide.Intact, Tokens.empty))
    val (_, result) = muster(ready, modifiers,
      DecisionOptionRef.Edifice(beastEdifice))
    assertEquals(supply(result), 7)

  test("mustering on a card of another suit pays the Supply"):
    val order = denizenOf(Suit.Order)
    val (_, result) = muster(at(denizen(order)), modifiers,
      DecisionOptionRef.Denizen(order))
    assertEquals(supply(result), 6)

  test("without the selection a beast card pays the Supply"):
    val beast = denizenOf(Suit.Beast)
    val (_, result) = muster(at(denizen(beast)), Vector.empty,
      DecisionOptionRef.Denizen(beast))
    assertEquals(supply(result), 6)

  test("it may be selected whatever the site holds, and only for a Muster"):
    val ready = at(denizen(denizenOf(Suit.Order)))
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(ready,
      PowerFixture.actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(AnimalPlaymates.id))
    assert(!offered(ActionRef.Trade).contains(AnimalPlaymates.id))

  test("a facedown Animal Playmates is not offered"):
    val ready = at(denizen(denizenOf(Suit.Beast)), Orientation.FaceDown)
    assert(!rules.offerableWalkerPowers(ready, PowerFixture.actor,
      ActionRef.Muster).toOption.get.map(_.id).contains(AnimalPlaymates.id))
