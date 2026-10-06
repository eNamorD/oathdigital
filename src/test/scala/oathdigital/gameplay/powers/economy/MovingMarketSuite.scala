package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class MovingMarketSuite extends munit.FunSuite:
  private val market = CatalogNames.denizen("Moving Market")
  private val power = MovingMarket.forCatalog(catalog)
  private val modifiers = Vector(MovingMarket.id)
  /** p1's pawn site, and two other sites in play, each with room for one
    * card. */
  private val home = CatalogNames.site("Ancient City")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Beast denizen, filling Broken Peaks. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen to Trade with instead. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  /** p1 at Ancient City beside the token-free Moving Market, with the
    * start's 1 favor and 1 faceup secret, and the Nomad bank holding `nomad`
    * favor. Errand Boy fills Broken Peaks; Buried Giant is empty. */
  private def staged(nomad: Int = 3): Table = Table.start
    .pawn(p1, at = home).denizen(market, at = home)
    .denizen(errandBoy, at = peaks).bankFavor(Suit.Nomad, nomad)

  /** p1 Trades for favor with Moving Market, having selected it, and is
    * asked where to move it. */
  private def tradedWithMarket(ready: ReadyGame): OathTransition =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      modifiers, Vector(DecisionOptionRef.Button("favor"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(market))))
    asked.copy(events = started.events ++ asked.events)

  /** `asked`, answered with `ref`; the events run from the Trade's start. */
  private def answered(asked: OathTransition, ref: DecisionOptionRef)
      : OathTransition =
    val done = accepted(rules.resolveWalker(asked.state, p1,
      MovingMarket.decisionId, ChooseOneAnswer(ref)))
    done.copy(events = asked.events ++ done.events)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("after trading with it, one question offers every empty slot at the " +
      "other sites, then Skip"):
    val asked = tradedWithMarket(staged().ready)
    assert(parked.parkedDecision(asked.state)
      .exists(_.decision == MovingMarket.decisionId),
      parked.parkedDecision(asked.state).toString)
    val options = PhasePowerHarness.offered(asked, p1).getOrElse(Vector.empty)
    assert(options.contains("site-slot" -> s"${giant.value}:0"), options)
    assert(!options.exists((_, id) =>
      id.startsWith(home.value) || id.startsWith(peaks.value)), options)
    assertEquals(options.lastOption, Some("button" -> "skip"))

  test("a chosen slot moves it there with its tokens, and it gains 1 favor " +
      "from the Nomad bank"):
    val ready = staged().ready
    val done = answered(tradedWithMarket(ready),
      DecisionOptionRef.SiteSlot(giant, 0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(done.state)
    assertEquals(end.cardsAt(giant), Vector[CardId](market))
    assertEquals(end.cardsAt(home), Vector.empty[CardId])
    // The Trade placed p1's secret on it, and the secret travels with it.
    assertEquals(end.tokensOn(market), Tokens(0, 1))
    // 1 favor from the Trade and 1 from Moving Market, both Nomad.
    assertEquals(end.favor(p1), 1 + 2)
    assertEquals(end.bankFavor(Suit.Nomad), 3 - 2)
    assertEquals(said(done.events), Vector(NoteText.Said("moved",
      s"Moved it to ${giant.value} and gained 1 favor from the Nomad bank.",
      covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      Outcomes.ready(done.state))
    assertWireRoundTrips(done.events)

  test("Skip leaves it where it stands, gains nothing and writes nothing"):
    val done = answered(tradedWithMarket(staged().ready), MovingMarket.skip)
    val end = Look(done.state)
    assertEquals(end.cardsAt(home), Vector[CardId](market))
    assertEquals(end.favor(p1), 1 + 1)
    assertEquals(said(done.events), Vector.empty)

  test("with the Nomad bank emptied by the Trade it still moves, and the " +
      "line names only the move"):
    val done = answered(tradedWithMarket(staged(nomad = 1).ready),
      DecisionOptionRef.SiteSlot(giant, 0))
    val end = Look(done.state)
    assertEquals(end.cardsAt(giant), Vector[CardId](market))
    assertEquals(end.favor(p1), 1 + 1)
    assertEquals(said(done.events), Vector(NoteText.Said("moved.only",
      s"Moved it to ${giant.value}.", covers = false)))

  test("trading with another card, or without selecting it, asks nothing"):
    val ready = staged().denizen(wrestlers, at = home).ready
    val elsewhere = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val unselected = EconomyActions.trade(rules, ready, p1, Vector.empty,
      "favor", DecisionOptionRef.Denizen(market))
    Vector(elsewhere, unselected).foreach { done =>
      assertEquals(parked.parkedDecision(Ready(done.result)), None)
      assert(Look(done.result).cardsAt(home).contains(market))
      assertEquals(said(done.journal.events), Vector.empty)
    }

  test("it is a Trade modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(staged().ready, ActionRef.Trade).contains(MovingMarket.id))
    assert(!offered(staged().ready, ActionRef.Muster)
      .contains(MovingMarket.id))
    val away = Table.start.denizen(market, at = giant).ready
    assert(!offered(away, ActionRef.Trade).contains(MovingMarket.id))
