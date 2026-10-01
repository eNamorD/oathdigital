package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TheOldOakSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val oak = CatalogNames.denizen("The Old Oak")
  // A plain Beast adviser, and another plain Beast card at the site.
  private val adviser = CatalogNames.denizen("Errand Boy")
  private val otherBeast = SearchFixture.denizensOf(Suit.Beast)
    .filterNot(_ == adviser).head
  private val modifiers = Vector(TheOldOak.id)

  /** p1 holds Errand Boy as an adviser, faceup unless `facedown`, and stands
    * at Ancient City with The Old Oak and another Beast card. p1 has 2 favor,
    * which a Trade for secrets costs, and the start's 1 faceup secret. */
  private def board(facedown: Boolean = false): ReadyGame = Table.start
    .favor(p1, 2).adviser(p1, adviser, facedown = facedown)
    .denizen(oak, at = Table.homeOf(p1))
    .denizen(otherBeast, at = Table.homeOf(p1)).ready

  private def secrets(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)

  /** Trades for `resource` with `card` and returns the result and the whole
    * journal. */
  private def trade(ready: ReadyGame, selected: Vector[PowerId],
      card: DenizenId, resource: String = "secret")
      : (OathTransition, ReadyGame) =
    val started = rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      selected, Vector(DecisionOptionRef.Button(resource))).toOption.get
    val done = rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)))
      .toOption.get
    (started.copy(events = started.events ++ done.events),
      done.state.asInstanceOf[Ready].value)

  test("trading with The Old Oak for secrets with a faceup beast adviser " +
      "gains one more secret"):
    val ready = board()
    val (transition, result) = trade(ready, modifiers, oak)
    assertEquals(secrets(result), 1 + 1 + 1)
    assertEquals(secrets(trade(ready, Vector.empty, oak)._2), 1 + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, transition.events),
      result)
    assert(PaidActionHarness.wireRoundTrips(transition.events))

  test("without a faceup beast adviser it gains nothing"):
    assertEquals(secrets(trade(board(facedown = true), modifiers, oak)._2), 1)

  test("trading with another card, or for favor, gains no extra secret"):
    assertEquals(secrets(trade(board(), modifiers, otherBeast)._2), 1 + 1)
    // A Trade for favor pays the faceup secret and gains favor only.
    assertEquals(secrets(trade(board(), modifiers, oak, "favor")._2), 0)

  test("it is a Trade modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      rules.offerableWalkerPowers(ready, p1, action).toOption.get.map(_.id)
    assert(offered(board(), ActionRef.Trade).contains(TheOldOak.id))
    assert(!offered(board(), ActionRef.Muster).contains(TheOldOak.id))
    val without = Table.start.adviser(p1, adviser).ready
    assert(!offered(without, ActionRef.Trade).contains(TheOldOak.id))
