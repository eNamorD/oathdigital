package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

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

  private val home = "Ancient City"

  /** p1 holds Animal Playmates as an adviser and stands at Ancient City,
    * which holds Alchemist. p1 has 4 favor and 7 Supply. */
  private def advised(facedown: Boolean = false): Table = Table.start
    .adviser(p1, playmates, facedown = facedown)
    .pawn(p1, at = home).denizen("Alchemist", at = home)
    .favor(p1, 4).supply(p1, 7)

  private def muster(ready: ReadyGame, selected: Vector[PowerId],
      card: DecisionOptionRef): EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, selected, card)

  test("mustering on a beast denizen spends no Supply and otherwise costs " +
      "the same"):
    val ready = advised().denizen(beast, at = home).ready
    val mustered = muster(ready, modifiers, DecisionOptionRef.Denizen(beast))
    val result = mustered.result
    assertEquals(Look(result).supply(p1), 7)
    assertEquals(Look(result).tokensOn(beast), Tokens(1, 0))
    // The selection itself is free: favor and secrets match the same Muster
    // without it.
    val plain = muster(ready, Vector.empty, DecisionOptionRef.Denizen(beast))
      .result
    assertEquals(Look(result).favor(p1), Look(plain).favor(p1))
    assertEquals(Look(result).faceUpSecrets(p1), Look(plain).faceUpSecrets(p1))
    val events = mustered.journal.events
    assertEquals(ActPowerHarness.replayed(rules, ready, events), result)
    assertWireRoundTrips(events)

  test("mustering on a beast edifice spends no Supply"):
    val ready = advised().edifice(beastEdifice, EdificeSide.Intact,
      at = home).ready
    val result = muster(ready, modifiers,
      DecisionOptionRef.Edifice(beastEdifice)).result
    assertEquals(Look(result).supply(p1), 7)

  test("mustering on a card of another suit pays the Supply"):
    val result = muster(advised().denizen(order, at = home).ready,
      modifiers, DecisionOptionRef.Denizen(order)).result
    // A Muster costs 1 Supply: 7 - 1.
    assertEquals(Look(result).supply(p1), 7 - 1)

  test("without the selection a beast card pays the Supply"):
    val result = muster(advised().denizen(beast, at = home).ready,
      Vector.empty, DecisionOptionRef.Denizen(beast)).result
    // A Muster costs 1 Supply: 7 - 1.
    assertEquals(Look(result).supply(p1), 7 - 1)

  test("it may be selected whatever the site holds, and only for a Muster"):
    val ready = advised().denizen(order, at = home).ready
    val offered = (action: ActionRef) => accepted(
      rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(ActionRef.Muster).contains(AnimalPlaymates.id))
    assert(!offered(ActionRef.Trade).contains(AnimalPlaymates.id))
