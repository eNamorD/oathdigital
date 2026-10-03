package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class TheOldOakSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val oak = CatalogNames.denizen("The Old Oak")
  // Beast cards with no Trade or Muster power: an adviser, and another card
  // at the site.
  private val adviser = CatalogNames.denizen("Errand Boy")
  private val otherBeast = CatalogNames.denizen("Fae Merchant")
  private val modifiers = Vector(TheOldOak.id)

  /** p1 holds Errand Boy as an adviser, faceup unless `facedown`, and stands
    * at Ancient City with The Old Oak and another Beast card. p1 has 2 favor,
    * which a Trade for secrets costs, and the start's 1 faceup secret. */
  private def board(facedown: Boolean = false): ReadyGame = Table.start
    .favor(p1, 2).secrets(p1, faceUp = 1)
    .adviser(p1, adviser, facedown = facedown)
    .pawn(p1, at = "Ancient City").denizen(oak, at = "Ancient City")
    .denizen(otherBeast, at = "Ancient City").ready

  private def secrets(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)

  /** Trades for `resource` with `card`. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId, resource: String = "secret")
      : EconomyActions.Completed =
    EconomyActions.trade(rules, ready, p1, selected, resource,
      DecisionOptionRef.Denizen(card))

  // Below, the first 1 is p1's starting faceup secret; a Trade for secrets
  // gains 1 per matching adviser, and The Old Oak 1 more.

  test("trading with The Old Oak for secrets with a faceup beast adviser " +
      "gains one more secret"):
    val ready = board()
    val done = trade(ready, modifiers, oak)
    val result = done.result
    assertEquals(secrets(result), 1 + 1 + 1)
    assertEquals(secrets(trade(ready, Vector.empty, oak).result), 1 + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      result)
    assertWireRoundTrips(done.journal.events)

  test("without a faceup beast adviser it gains nothing"):
    val traded = trade(board(facedown = true), modifiers, oak)
    assertEquals(secrets(traded.result), 1)

  test("trading with another card, or for favor, gains no extra secret"):
    assertEquals(secrets(trade(board(), modifiers, otherBeast).result), 1 + 1)
    // A Trade for favor pays the faceup secret and gains favor only.
    assertEquals(secrets(trade(board(), modifiers, oak, "favor").result), 0)

  test("it is a Trade modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(board(), ActionRef.Trade).contains(TheOldOak.id))
    assert(!offered(board(), ActionRef.Muster).contains(TheOldOak.id))
    val without = Table.start.adviser(p1, adviser).ready
    assert(!offered(without, ActionRef.Trade).contains(TheOldOak.id))

  test("several faceup beast advisers still add only one secret to the " +
      "base gain, which is one per matching adviser"):
    val third = CatalogNames.denizen("Second Chance")
    val ready = Table.start.favor(p1, 2).secrets(p1, faceUp = 1)
      .adviser(p1, adviser).adviser(p1, third)
      .pawn(p1, at = "Ancient City").denizen(oak, at = "Ancient City").ready
    assertEquals(secrets(trade(ready, Vector.empty, oak).result), 1 + 2)
    assertEquals(secrets(trade(ready, modifiers, oak).result), 1 + 2 + 1)
