package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PowerRules, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class VowOfPovertySuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Poverty")
  private val home = CatalogNames.site("Ancient City")
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val market = CatalogNames.denizen("Moving Market")
  private val rules = PowerRules.withWalkerAndPhasePowers
  private val parked = PowerRules.parkedUnderWalkerAndPhasePowers
  private val automatic = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** Whether p1 could Trade for `resource` with some card at the site now,
    * by the preview a start runs. */
  private def tradeOffered(ready: ReadyGame, resource: TradeResource)
      : Boolean = TradeProcedure.startOptions(catalog, ready, p1, resource,
    automatic).exists(_.outcome.isRight)

  test("its holder is not offered Trade for favor, and may still Trade for " +
      "secrets"):
    // 2 favor pays a Trade for secrets; the start's secret pays one for favor.
    val withoutVow = Table.start.denizen(wrestlers, at = home).favor(p1, 2)
      .bankFavor(Suit.Order, 3)
    val held = withoutVow.adviser(p1, vow).ready
    assert(tradeOffered(withoutVow.ready, TradeResource.Favor))
    assert(!tradeOffered(held, TradeResource.Favor))
    assert(tradeOffered(held, TradeResource.Secret))
    val traded = for
      started <- rules.startWalker(Ready(held), ActionRef.Trade, p1,
        Vector.empty, Vector(DecisionOptionRef.Button("favor")))
      done <- rules.resolveWalker(started.state, p1, TradeProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Denizen(wrestlers)))
    yield done
    assert(traded.isLeft, traded.toString)

  test("Moving Market's favor is not from Trade, so its holder still gains " +
      "it"):
    val ready = Table.start.denizen(market, at = home).adviser(p1, vow)
      .favor(p1, 2).bankFavor(Suit.Nomad, 3).ready
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      Vector(MovingMarket.id), Vector(DecisionOptionRef.Button("secret"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(market))))
    val moved = accepted(rules.resolveWalker(asked.state, p1,
      MovingMarket.decisionId,
      ChooseOneAnswer(DecisionOptionRef.SiteSlot(giant, 0))))
    // The Trade spent the 2 favor, and Moving Market gave 1.
    assertEquals(Look(Outcomes.ready(moved.state)).favor(p1), 1)

  // ---- REST: with no favor, take 2 favor from any one favor bank ----

  private val use = ActionRef.UsePower(VowOfPoverty.id)
  private val source = DecisionOptionRef.Denizen(vow)

  /** p1's Rest with no favor, holding a faceup Vow, with exactly these banks
    * holding favor. */
  private def restingWithNoFavor(stocked: (Suit, Int)*): Table =
    Table.start.turn(p1, Phase.Rest).adviser(p1, vow).favor(p1, 0)
      .banksHoldingOnly(stocked*)

  private def usedRest(ready: ReadyGame): OathTransition = accepted(
    rules.startWalker(Ready(ready), use, p1, Vector.empty, Vector(source)))

  private def usable(ready: ReadyGame): Boolean = PhasePowerProcedure.usable(
    catalog, ready, p1, PhasePowerCatalog.default(catalog), WalkerPowers.empty)
    .exists(_.power.id == VowOfPoverty.id)

  test("REST: with no favor, its holder takes 2 favor from the bank they " +
      "choose, in a line covering the generic one"):
    val ready = restingWithNoFavor(Suit.Arcane -> 3, Suit.Order -> 3).ready
    val choice = VowOfPoverty.choiceDecisionId(ready, p1)
    val started = usedRest(ready)
    parked.assertParked(started.state, use, choice, p1)
    val taken = accepted(rules.resolveWalker(started.state, p1, choice,
      ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order))))
    val after = Outcomes.ready(taken.state)
    assertEquals(Look(after).bankFavor(Suit.Order), 3 - 2)
    assertEquals(Look(after).bankFavor(Suit.Arcane), 3)
    assertEquals(Look(after).favor(p1), 2)
    assertEquals(NoteText.said(VowOfPoverty, taken.events),
      Vector(NoteText.Said(NoteKey.Used, "Took 2 favor from the Order bank.",
        covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      started.events ++ taken.events), after)

  test("REST: a bank holding 1 gives 1 without asking, and a holder with " +
      "favor is not offered it"):
    val after = Outcomes.ready(usedRest(
      restingWithNoFavor(Suit.Hearth -> 1).ready).state)
    assertEquals(Look(after).favor(p1), 1)
    assertEquals(Look(after).bankFavor(Suit.Hearth), 0)
    val stocked = restingWithNoFavor(Suit.Hearth -> 3)
    assert(usable(stocked.ready))
    assert(!usable(stocked.favor(p1, 1).ready))
