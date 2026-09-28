package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class AnimalPlaymatesSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val playmates = CatalogNames.denizen("Animal Playmates")
  private val modifiers = Vector(AnimalPlaymates.id)
  // Plain cards the tests muster on: none has a Muster or Trade power.
  private val beast = CatalogNames.denizen("Errand Boy")
  private val order = CatalogNames.denizen("Wrestlers")
  /** A Beast edifice whose intact face has no Muster or Trade power (its
    * power is about challenging banners). */
  private val beastEdifice = CatalogNames.edifice("School of Vines")

  /** p1 holds Animal Playmates as an adviser and stands at Ancient City,
    * which holds Alchemist. p1 has 4 favor and the start's 7 Supply. */
  private def advised(facedown: Boolean = false): Table = Table.start
    .adviser(p1, playmates, facedown = facedown)
    .denizen("Alchemist", at = Table.homeOf(p1))
    .favor(p1, 4)

  /** Musters from `card` and returns the result and the whole journal. */
  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DecisionOptionRef): (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Muster, p1,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, p1, MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(card)).toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  test("mustering on a beast denizen spends no Supply"):
    val ready = advised().denizen(beast, at = Table.homeOf(p1)).ready
    val (transition, result) = muster(ready, modifiers,
      DecisionOptionRef.Denizen(beast))
    assertEquals(Look(result).supply(p1), 7)
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    // The selection itself is free: favor and secrets match the same Muster
    // without it.
    val (_, plain) = muster(ready, Vector.empty, DecisionOptionRef.Denizen(beast))
    assertEquals(Look(result).favor(p1), Look(plain).favor(p1))
    assertEquals(Look(result).faceUpSecrets(p1), Look(plain).faceUpSecrets(p1))
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("mustering on a beast edifice spends no Supply"):
    val ready = advised().edifice(beastEdifice, EdificeSide.Intact,
      at = Table.homeOf(p1)).ready
    val (_, result) = muster(ready, modifiers,
      DecisionOptionRef.Edifice(beastEdifice))
    assertEquals(Look(result).supply(p1), 7)

  test("mustering on a card of another suit pays the Supply"):
    val (_, result) = muster(advised().denizen(order, at = Table.homeOf(p1)).ready,
      modifiers, DecisionOptionRef.Denizen(order))
    assertEquals(Look(result).supply(p1), 6)

  test("without the selection a beast card pays the Supply"):
    val (_, result) = muster(advised().denizen(beast, at = Table.homeOf(p1)).ready,
      Vector.empty, DecisionOptionRef.Denizen(beast))
    assertEquals(Look(result).supply(p1), 6)

  test("it may be selected whatever the site holds, and only for a Muster"):
    val ready = advised().denizen(order, at = Table.homeOf(p1)).ready
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(ready,
      p1, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(AnimalPlaymates.id))
    assert(!offered(ActionRef.Trade).contains(AnimalPlaymates.id))

  test("a facedown Animal Playmates is not offered"):
    val ready = advised(facedown = true).denizen(beast, at = Table.homeOf(p1)).ready
    assert(!rules.offerableWalkerPowers(ready, p1,
      ActionRef.Muster).toOption.get.map(_.id).contains(AnimalPlaymates.id))
