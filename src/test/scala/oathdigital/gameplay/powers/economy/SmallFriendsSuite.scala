package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class SmallFriendsSuite extends munit.FunSuite:
  private val friends = CatalogNames.denizen("Small Friends")
  private val power = SmallFriends.forCatalog(catalog)
  private val map = Table.start.ready.game.current.map
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** A site in play in another region. */
  private val away = Table.siteInAnotherRegionThan(home)
  /** A third site in play. */
  private val third = map.inPlay.find(site =>
    site != home && site != away).get
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val oldOak = CatalogNames.denizen("The Old Oak")
  private val market = CatalogNames.denizen("Moving Market")
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val modifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Small Friends, with Wrestlers beside them,
    * Errand Boy at a site in another region, and the Beast bank holding 3
    * favor. */
  private def withBeastCardAway: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, friends).denizen(wrestlers, at = home)
    .denizen(errandBoy, at = away).bankFavor(Suit.Beast, 3)

  /** p1 starts a Trade for `resource`, parked on the modifier decision. */
  private def tradeBegun(ready: ReadyGame, resource: String = "favor")
      : OathTransition = accepted(MenuPlay.started(rules, Ready(ready), p1,
    ActionRef.Trade, Vector(DecisionOptionRef.Button(resource))))

  /** `from`, then p1's answer `ref` at `decisionId`; the events run from
    * the Trade's start. */
  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  private def friendsPicked(from: OathTransition): OathTransition =
    answeredWith(from, modifiers, DecisionOptionRef.Denizen(friends))

  /** `from` with Small Friends picked and acting as if at `site`. */
  private def actingAt(from: OathTransition, site: SiteId): OathTransition =
    answeredWith(friendsPicked(from), PowerAccess.asIfDecision,
      DecisionOptionRef.Site(site))

  /** Each option's id, state and reason, at the parked decision. */
  private def optionStates(at: OathTransition)
      : Option[Vector[(String, String, Option[String])]] =
    PhasePowerHarness.queryOf(at, p1).map(_.offeredOptions.map(option =>
      (option.id, option.state, option.reason)))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("picked, it asks which site, offering only the other sites with a " +
      "Beast card"):
    // Alchemist, at the third site, is no Beast card.
    val asked = friendsPicked(tradeBegun(withBeastCardAway
      .denizen(alchemist, at = third).ready))
    assertEquals(PhasePowerHarness.offered(asked, p1),
      Some(Vector("site" -> away.value)))

  test("acting at the chosen site, the Trade draws only on its cards, and " +
      "the line names it"):
    val ready = withBeastCardAway.ready
    // No other modifier to pick, so the Trade starts by itself.
    val acting = actingAt(tradeBegun(ready), away)
    assertEquals(Outcomes.ready(acting.state).game.current.walkerAsIfSite,
      Some(away))
    assertEquals(PhasePowerHarness.offered(acting, p1),
      Some(Vector("denizen" -> errandBoy.value)))
    val done = answeredWith(acting, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(errandBoy))
    val end = Look(done.state)
    assertEquals(Outcomes.ready(done.state).game.current.walkerAsIfSite,
      None)
    assertEquals(end.tokensOn(errandBoy), Tokens(0, 1))
    // 1, and 1 more for Small Friends, a faceup Beast adviser.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Beast), 1)
    assertEquals(said(done.events), Vector(NoteText.Said("acted",
      s"Acted as if at ${away.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      Outcomes.ready(done.state))
    assertWireRoundTrips(done.events)

  test("Start stays available after the site, though nothing near the " +
      "pawn can be traded with"):
    // Skilled Merchants keeps the modifier decision asking.
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .adviser(p1, merchants).denizen(errandBoy, at = away).ready
    val acting = actingAt(tradeBegun(ready), away)
    assertEquals(optionStates(acting).map(_.take(1)),
      Some(Vector(("start", "available", None))))

  test("a Trade modifier at the chosen site becomes selectable, and " +
      "applies there"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .denizen(oldOak, at = away).favor(p1, 2).ready
    val begun = tradeBegun(ready, "secret")
    // From Ancient City, The Old Oak is out of reach.
    assert(!optionStates(begun).exists(_.exists(_._1 == oldOak.value)))
    val acting = actingAt(begun, away)
    assert(optionStates(acting).exists(_.contains(
      (oldOak.value, "available", None))))
    val picked = answeredWith(acting, modifiers,
      DecisionOptionRef.Denizen(oldOak))
    val done = answeredWith(picked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(oldOak))
    // 1 secret for Small Friends, a faceup Beast adviser, and 1 more for
    // The Old Oak.
    assertEquals(Look(done.state).faceUpSecrets(p1),
      Look(ready).faceUpSecrets(p1) + 2)

  test("with a modifier picked at the pawn site, it is unavailable"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .denizen(market, at = home).denizen(errandBoy, at = away).ready
    val picked = answeredWith(tradeBegun(ready), modifiers,
      DecisionOptionRef.Denizen(market))
    assert(optionStates(picked).exists(_.contains((friends.value,
      "unavailable", Some("A modifier you picked is at your pawn site")))))

  test("it is unavailable when no other site has a Beast card"):
    // Errand Boy stands at the pawn site, which does not count.
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .adviser(p1, merchants).denizen(errandBoy, at = home).ready
    assert(optionStates(tradeBegun(ready)).exists(_.contains((friends.value,
      "unavailable", Some("No other site has a Beast card")))))
