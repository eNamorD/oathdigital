package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, Birdsong}
import oathdigital.gameplay.powers.search.NewsFromAfar
import oathdigital.gameplay.powers.travel.{Tents, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, ParkedNode, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** A menu option counts the modifiers its player could select: an action
  * that only a modifier makes affordable is still available, and a modifier
  * the player cannot pay for unlocks nothing. */
class ModifierMenuOptionsSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  /** Why `viewer` may not start `action` at `ready`; fails the test if the
    * start is accepted. */
  private def refusal(ready: ReadyGame, viewer: PlayerId, action: ActionRef)(
      using munit.Location): OathViolation =
    rejected(rules.startWalker(OathState.Ready(ready), action, viewer))

  /** The refusal of a start that spends `cost` Supply from an empty track. */
  private def noSupplyFor(cost: Int): OathViolation =
    OathViolation.CoreOperationRejected("insufficient-supply",
      s"a supply spend of $cost exceeds the 0 available")

  // ---- Travel ----

  import TravelFixture.board
  import oathdigital.gameplay.powers.PowerFixture.actor

  /** The actor at the Cradle's plains with no Supply, holding `favor`. */
  private def broke(favor: Int, tents: Boolean): ReadyGame =
    val table = Table.from(board(supply = 0)).favor(actor, favor)
    (if tents then table.adviser(actor, CatalogNames.denizen("Tents"))
      else table).unchecked

  test("Travel is available at 0 Supply when Tents would make it free; the " +
      "start asks for the modifier, and picking it reaches the destination"):
    val ready = broke(favor = 1, tents = true)
    assert(MenuPlay.availableInMenu(catalog, ready, ActionRef.Travel))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Travel, actor))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, travelModifiers)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Travel"),
        OptionReason.Written("Needs 1 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, actor,
      travelModifiers, ModifierPlay.pick(Tents.id)))
    assertEquals(parked(picked.state).decisionId,
      TravelProcedure.destinationDecisionId)
    // Without Tents nothing can be picked, and the start is refused.
    assertEquals(refusal(broke(favor = 1, tents = false), actor,
      ActionRef.Travel), noSupplyFor(1))

  private val travelModifiers =
    ModifierPicks.decisionId(PowerWindow.TravelModifierSelection)

  test("without a modifier that waives it, Travel at 0 Supply is not " +
      "available"):
    assert(!MenuPlay.availableInMenu(catalog,
      broke(favor = 1, tents = false), ActionRef.Travel))

  test("Tents with no favor to place on it leaves no Travel at 0 Supply"):
    assert(!MenuPlay.availableInMenu(catalog,
      broke(favor = 0, tents = true), ActionRef.Travel))

  // ---- Muster ----

  /** p1 at Ancient City, which holds Errand Boy, a beast card to muster on,
    * with 4 favor and no Supply. */
  private def beastMuster(playmates: Boolean): ReadyGame =
    val table = Table.start.pawn(p1, at = "Ancient City")
      .denizen(CatalogNames.denizen("Errand Boy"), at = "Ancient City")
      .favor(p1, 4).supply(p1, 0)
    (if playmates then table.adviser(p1,
      CatalogNames.denizen("Animal Playmates")) else table).ready

  test("Muster is available at 0 Supply when Animal Playmates waives it; the " +
      "start asks for the modifier, and picking it reaches the source"):
    val ready = beastMuster(playmates = true)
    assert(MenuPlay.availableInMenu(catalog, ready, ActionRef.Muster))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Muster, p1))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, musterModifiers)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Muster"),
        OptionReason.Written("Needs 1 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, p1,
      musterModifiers, ModifierPlay.pick(AnimalPlaymates.id)))
    assertEquals(parked(picked.state).decisionId, MusterProcedure.decisionId)

  private val musterModifiers =
    ModifierPicks.decisionId(PowerWindow.MusterModifierSelection)

  test("without the waiver, Muster at 0 Supply is not available"):
    assert(!MenuPlay.availableInMenu(catalog, beastMuster(playmates = false),
      ActionRef.Muster))

  // ---- Trade ----

  /** p1 at Ancient City, which holds Errand Boy, a beast card to trade with,
    * with 4 favor, 2 faceup secrets and no Supply. */
  private def beastTrade(birdsong: Boolean): ReadyGame =
    val table = Table.start.pawn(p1, at = "Ancient City")
      .denizen(CatalogNames.denizen("Errand Boy"), at = "Ancient City")
      .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, 0)
    (if birdsong then table.adviser(p1, CatalogNames.denizen("Birdsong"))
      else table).ready

  test("Trade is available at 0 Supply when Birdsong waives it; the start " +
      "asks for the modifier, and picking it reaches the source"):
    val ready = beastTrade(birdsong = true)
    assert(MenuPlay.availableInMenu(catalog, ready, ActionRef.Trade,
      TradeProcedure.selection(TradeResource.Favor)))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Trade, p1, startArgs = Vector(DecisionOptionRef.Button(
        "favor"))))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, tradeModifiers)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Trade"),
        OptionReason.Written("Needs 1 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, p1,
      tradeModifiers, ModifierPlay.pick(Birdsong.id)))
    assertEquals(parked(picked.state).decisionId, TradeProcedure.decisionId)

  private val tradeModifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)

  test("without the waiver, Trade at 0 Supply is not available"):
    assert(!MenuPlay.availableInMenu(catalog, beastTrade(birdsong = false),
      ActionRef.Trade, TradeProcedure.selection(TradeResource.Favor)))

  // ---- Search ----

  /** p1 with no Supply, holding `favor`, and News from Afar faceup when
    * `news`. */
  private def brokeSearcher(news: Boolean, favor: Int): ReadyGame =
    val table = Table.start.supply(p1, 0).favor(p1, favor)
    (if news then table.adviser(p1, CatalogNames.denizen("News from Afar"))
      else table).ready

  test("Search is available when News from Afar would make it free; the " +
      "start asks for the modifier, and picking it reaches the source"):
    val ready = brokeSearcher(news = true, favor = 2)
    assert(MenuPlay.availableInMenu(catalog, ready, ActionRef.Search))
    val started = accepted(rules.startWalker(OathState.Ready(ready),
      ActionRef.Search, p1))
    val modifiers = parked(started.state)
    assertEquals(modifiers.decisionId, search)
    assertEquals(modifiers.query match
      case DecisionQuery.ChooseOne(options, _, _) => options.headOption
      case _ => None,
      Some(DecisionOption.Unavailable(
        DecisionOption.Button(ModifierPicks.start, "Start Search"),
        OptionReason.Written("Needs 2 Supply; you have 0"))))
    val picked = accepted(rules.resolveWalker(started.state, p1, search,
      ModifierPlay.pick(NewsFromAfar.id)))
    assertEquals(parked(picked.state).decisionId,
      SearchProcedure.sourceDecisionId)

  private val search =
    ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)

  /** The decision the walk is parked on, as the projector rebuilds it. */
  private def parked(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  test("News from Afar with too little favor to pay leaves no Search " +
      "available"):
    assert(!MenuPlay.availableInMenu(catalog,
      brokeSearcher(news = true, favor = 1), ActionRef.Search))
    assert(!MenuPlay.availableInMenu(catalog,
      brokeSearcher(news = false, favor = 2), ActionRef.Search))
