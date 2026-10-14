package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class SecretSignalSuite extends munit.FunSuite:
  private val signal = CatalogNames.denizen("Secret Signal")
  private val power = SecretSignal.forCatalog(catalog)
  private val selected = Vector(SecretSignal.id)
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** An empty site in play, for Moving Market's move. */
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val market = CatalogNames.denizen("Moving Market")
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Secret Signal, with Wrestlers and Alchemist
    * beside them and the Order and Arcane banks holding 3 favor each. */
  private def holdingSignal: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, signal).denizen(wrestlers, at = home)
    .denizen(alchemist, at = home).bankFavor(Suit.Order, 3)
    .bankFavor(Suit.Arcane, 3)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  /** Secret Signal's option at the start of a Trade for `resource`: its
    * state and reason. */
  private def signalAtStart(ready: ReadyGame, resource: String)
      : Option[(String, Option[String])] =
    PhasePowerHarness.queryOf(accepted(rules.startWalker(Ready(ready),
      ActionRef.Trade, p1,
      startArgs = Vector(DecisionOptionRef.Button(resource)))), p1)
      .flatMap(_.offeredOptions.find(_.id == signal.value))
      .map(option => (option.state, option.reason))

  test("a Trade that gains one favor gains one more from the same bank, " +
      "with its line"):
    val ready = holdingSignal.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.result)
    // 1 for the Trade, p1 holding no Order adviser, and 1 more.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Order), 1)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("gained",
      "Gained 1 more favor.", covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a Trade that gains more than one favor gains nothing more"):
    val ready = holdingSignal.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(alchemist))
    val end = Look(done.result)
    // Secret Signal is itself a faceup Arcane adviser: 2 favor.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Arcane), 1)
    assertEquals(said(done.journal.events), Vector.empty)

  test("with Moving Market's favor after the Trade's, two gains of one " +
      "gain nothing more"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, signal)
      .denizen(market, at = home).bankFavor(Suit.Nomad, 3).ready
    val started = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.Trade, p1, PowerWindow.TradeModifierSelection,
      Vector(MovingMarket.id, SecretSignal.id),
      startArgs = Vector(DecisionOptionRef.Button("favor"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(market))))
    val done = accepted(rules.resolveWalker(asked.state, p1,
      MovingMarket.decisionId,
      ChooseOneAnswer(DecisionOptionRef.SiteSlot(giant, 0))))
    val end = Look(done.state)
    // 1 from the Trade and 1 from Moving Market, both Nomad.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Nomad), 1)
    assertEquals(said(started.events ++ asked.events ++ done.events),
      Vector.empty)
    assert(Outcomes.ready(done.state).game.current.walkerDeferred.isEmpty)

  test("it is unavailable for a Trade for secrets, and available for " +
      "favor"):
    // Skilled Merchants keeps the modifier decision asking.
    val ready = holdingSignal.adviser(p1, merchants).favor(p1, 2).ready
    assertEquals(signalAtStart(ready, "secret"), Some(("unavailable",
      Some("A Trade for secrets gains no favor"))))
    assertEquals(signalAtStart(ready, "favor"), Some(("available", None)))
