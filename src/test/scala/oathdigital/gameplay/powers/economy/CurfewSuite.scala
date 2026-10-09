package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{NoteText, PayOrAsk, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Curfew's ruler toll on a Trade. The toll is shared with Forced Labor
  * (`RulerToll`); its bandit case is pinned here only. */
class CurfewSuite extends munit.FunSuite:
  private val curfew = CatalogNames.denizen("Curfew")
  private val power = Curfew.forCatalog(catalog)
  /** p1's pawn site, where p1 trades. */
  private val home = CatalogNames.site("Ancient City")
  /** Where Curfew stands. */
  private val peaks = CatalogNames.site("Broken Peaks")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val automatic = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** p1 at Ancient City beside Wrestlers, with the start's 1 favor and 1
    * faceup secret, Curfew at Broken Peaks, and the Order bank holding 3
    * favor. Nobody rules either site yet. */
  private def curfewTable: Table = Table.start.pawn(p1, at = home)
    .denizen(wrestlers, at = home).denizen(curfew, at = peaks)
    .bankFavor(Suit.Order, 3)

  /** p2 rules Broken Peaks, so Curfew is p2's, and Ancient City, so p2
    * rules Wrestlers. */
  private def rivalRulesBoth: Table =
    curfewTable.warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1)

  private def tradedForFavorWithWrestlers(ready: ReadyGame)
      : EconomyActions.Completed = EconomyActions.trade(rules, ready, p1,
    Vector.empty, "favor", DecisionOptionRef.Denizen(wrestlers))

  /** Whether a Trade for favor with Wrestlers survives the preview a start
    * runs. */
  private def wrestlersOffered(ready: ReadyGame): Boolean =
    TradeProcedure.startOptions(catalog, ready, p1, TradeResource.Favor,
      automatic).exists(previewed => previewed.outcome.isRight &&
        previewed.option.ref == DecisionOptionRef.Denizen(wrestlers))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  /** The Trade for favor with Wrestlers, parked on Curfew's step, with the
    * events of its start and its source pick. */
  private def tradedToStep(ready: ReadyGame)(using munit.Location)
      : OathTransition =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      Vector.empty, Vector(DecisionOptionRef.Button("favor"))))
    val picked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(wrestlers))))
    picked.copy(events = started.events ++ picked.events)

  /** The Trade for favor with Wrestlers, with Curfew's step answered with
    * `choice`: the events of both, and the state after. */
  private def tradedAnswering(ready: ReadyGame, choice: DecisionOptionRef)(
      using munit.Location): (Vector[OathEvent], ReadyGame) =
    val stepped = tradedToStep(ready)
    val done = accepted(rules.resolveWalker(stepped.state, p1,
      Curfew.decisionId, DecisionAnswer.ChooseOneAnswer(choice)))
    (stepped.events ++ done.events, Outcomes.ready(done.state))

  test("an enemy trading with a card the ruler rules gives the ruler 1 favor " +
      "on top of the Trade's cost"):
    val ready = rivalRulesBoth.ready
    val (events, result) = tradedAnswering(ready, PayOrAsk.pay)
    val end = Look(result)
    // The start's 1 favor went to p2, then the Trade gained 1 Order favor.
    assertEquals(end.favor(p1), 1)
    assertEquals(end.favor(p2), Look(ready).favor(p2) + 1)
    assertEquals(end.bankFavor(Suit.Order), 3 - 1)
    assertEquals(said(events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${p2.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, events), result)
    assertWireRoundTrips(events)

  test("a trader who cannot pay is offered the card only to ask its ruler; " +
      "under bandits, not at all"):
    assert(wrestlersOffered(rivalRulesBoth.favor(p1, 0).ready))
    assert(!wrestlersOffered(curfewTable.bandits(peaks, 1).bandits(home, 1)
      .favor(p1, 0).ready))

  test("when bandits rule, the favor is burnt"):
    val ready = curfewTable.bandits(peaks, 1).bandits(home, 1).ready
    val done = tradedForFavorWithWrestlers(ready)
    assertEquals(Look(done.result).favor(p1), 1)
    assertEquals(Look(done.result).favor(p2), Look(ready).favor(p2))
    assertEquals(said(done.journal.events), Vector(NoteText.Said("burned",
      "Burned 1 favor.", covers = false)))

  test("the ruler trades without paying, and so does an enemy trading with " +
      "a card the ruler does not rule"):
    val rulerTrades = curfewTable.warbandsAt(peaks, p1, 1)
      .warbandsAt(home, p1, 1).ready
    val unruledCard = curfewTable.warbandsAt(peaks, p2, 1).ready
    Vector(rulerTrades, unruledCard).foreach { ready =>
      val done = tradedForFavorWithWrestlers(ready)
      // Only the Trade's own favor is gained, and nothing is paid.
      assertEquals(Look(done.result).favor(p1), Look(ready).favor(p1) + 1)
      assertEquals(said(done.journal.events), Vector.empty)
    }

  test("an enemy of a player ruler may ask to trade without paying; once " +
      "granted, it gives nothing"):
    val ready = rivalRulesBoth.ready
    val free = Curfew.free(DecisionOptionRef.Denizen(wrestlers))
    val asked = present(PlacementPicks.parkedOptions(
      tradedToStep(ready).state, automatic)
      .find(_.ref == free), "the free option")
    assertEquals(asked, DecisionOption.Unavailable(
      DecisionOption.Button(free, "Trade without paying"),
      OptionReason.Askable(Curfew.id, Curfew.noFreeTradeConsent(
        PowerSourceRef.Card(CurfewCard.id)),
        Vector(ConsentAsk(p2, Vector(Curfew.id))))))
    val granted = ready.copy(consent = ConsentState().granting(Vector(
      ConsentKey(Curfew.id, p1, Curfew.decisionId, free)),
      ConsentTurn.of(ready)))
    val (events, result) = tradedAnswering(granted, free)
    assertEquals(Look(result).favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(said(events), Vector(NoteText.Said("waived",
      s"Gave nothing, with ${p2.value}'s consent.", covers = false)))

  test("a Trade whose own cost takes the favor shows paying unavailable, " +
      "with what it needs"):
    // A Trade for secrets takes 2 favor before Curfew's toll.
    val ready = rivalRulesBoth.favor(p1, 2).ready
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      Vector.empty, Vector(DecisionOptionRef.Button("secret"))))
    val picked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(wrestlers))))
    assertEquals(PlacementPicks.parkedOptions(picked.state, automatic)
      .find(_.ref == PayOrAsk.pay), Some(DecisionOption.Unavailable(
        DecisionOption.Button(PayOrAsk.pay, s"Give 1 favor to ${p2.value}"),
        OptionReason.Written("Needs 3 favor; you have 2"))))
