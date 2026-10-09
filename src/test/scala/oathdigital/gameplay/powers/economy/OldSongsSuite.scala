package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ModifierOffers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class OldSongsSuite extends munit.FunSuite:
  private val songs = CatalogNames.denizen("Old Songs")
  private val selected = Vector(OldSongs.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Arcane denizen, holding one secret. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  /** A plain Order denizen, holding one secret and one favor. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Beast denizen, holding nothing. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Old Songs faceup, with the start's 1 faceup
    * secret, beside Alchemist (1 secret), Wrestlers (1 secret and 1 favor)
    * and Errand Boy (nothing). The Arcane bank holds 3 favor. */
  private def staged: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, songs).denizen(alchemist, at = home)
    .denizen(wrestlers, at = home).denizen(errandBoy, at = home)
    .tokens(alchemist, secrets = 1).tokens(wrestlers, favor = 1, secrets = 1)
    .bankFavor(Suit.Arcane, 3)

  private def tradeStarted(ready: ReadyGame, modifiers: Vector[PowerId])
      : OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1, modifiers,
      Vector(DecisionOptionRef.Button("favor"))))

  private def states(started: OathTransition)
      : Option[Vector[(String, String)]] =
    PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state)))

  private def modifiers(ready: ReadyGame, action: ActionRef)
      : Vector[PowerId] =
    accepted(ModifierOffers.offerable(WalkerPowerCatalog.default(catalog),
      ready, p1, action)).map(_.id)

  test("selected, a card with exactly one secret and no favor is a Trade " +
      "source, and one with favor too is not"):
    assertEquals(states(tradeStarted(staged.ready, selected)), Some(Vector(
      alchemist.value -> "available", wrestlers.value -> "unavailable",
      errandBoy.value -> "available")))

  test("trading with it places the secret beside the one already there"):
    val ready = staged.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(alchemist))
    val after = Look(done.result)
    assertEquals(after.tokensOn(alchemist), Tokens(0, 2))
    // 1: Old Songs is Hearth, not Arcane.
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(after.bankFavor(Suit.Arcane), 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("without it, the card with one secret is shown unavailable and " +
      "refused"):
    val started = tradeStarted(staged.ready, Vector.empty)
    assertEquals(states(started), Some(Vector(
      alchemist.value -> "unavailable", wrestlers.value -> "unavailable",
      errandBoy.value -> "available")))
    assertEquals(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))).left.toOption,
      Some(Refusals.notOffered(TradeProcedure.decisionId)))

  test("it is a Trade modifier only"):
    val ready = staged.ready
    assert(modifiers(ready, ActionRef.Trade).contains(OldSongs.id))
    assert(!modifiers(ready, ActionRef.Muster).contains(OldSongs.id))
