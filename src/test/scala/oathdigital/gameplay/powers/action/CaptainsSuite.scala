package oathdigital.gameplay.powers.action

import oathdigital.gameplay.{OathRules, PowerAccess}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.PlanDriver
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Captains (card 115): "ACTION: Campaign at any site you rule. Act as if
  * your pawn is there. Spend no Supply and add your warbands there to your
  * force." (catalog batch 8, slice 13). */
class CaptainsSuite extends munit.FunSuite:
  private val card = CaptainsCard.id
  private val source = DecisionOptionRef.Denizen(card)
  private val home = CatalogNames.site("Ancient City")
  /** In the Provinces, away from p1's Cradle, with no pawn. */
  private val wilds = CatalogNames.site("Deep Woods")

  /** Captains at Ancient City, which p1 rules with 1 warband and where p1
    * pays a favor; p1 also rules the wilds with `atWilds` warbands, where
    * p2's pawn stands, and holds `onBoard` on the board. */
  private def captainsAtHomeWithTheRivalAtARuledSite(onBoard: Int = 3,
      atWilds: Int = 2): Table = Table.start
    .denizen(card, at = home).warbandsAt(home, p1, 1).favor(p1, 1)
    .warbandsAt(wilds, p1, atWilds).boardWarbands(p1, onBoard)
    .pawn(p2, at = wilds)

  private def menuOption(ready: ReadyGame): DecisionOption =
    MenuPlay.menuOptionFor(catalog, ready, ActionRef.UsePower(Captains.id),
      Vector(source))

  /** The decision `state` is parked on, rebuilt with every production
    * power. */
  private def parkedDecide(state: OathState): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog),
        PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  /** The use answered with the wilds, as a Campaign run. */
  private def campaignAtTheWilds(rules: OathRules,
      ready: ReadyGame): (OathTransition, OathTransition) =
    val asked = accepted(ActPowerHarness.use(rules, ready, Captains.id,
      source))
    (asked, accepted(ActPowerHarness.answer(rules, asked.state,
      Captains.siteDecisionId, DecisionOptionRef.Site(wilds))))

  test("the site question lists the sites the user rules, their own pawn " +
      "site included"):
    val ready = captainsAtHomeWithTheRivalAtARuledSite().ready
    assertEquals(PowerAccess.ruledSites(ready, p1), Set(home, wilds))
    val asked = accepted(ActPowerHarness.use(
      ActPowerHarness.rulesWithPrintedRestrictions(), ready, Captains.id,
      source))
    val decide = parkedDecide(asked.state)
    assertEquals(decide.decisionId, Captains.siteDecisionId)
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        assertEquals(one.options.map(_.ref), ready.game.current.map.inPlay
          .filter(site => site == home || site == wilds)
          .map(DecisionOptionRef.Site(_)))
        assertEquals(one.options.find(_.ref == DecisionOptionRef.Site(home)),
          Some(DecisionOption.Unavailable(DecisionOption.Site(
            DecisionOptionRef.Site(home)), OptionReason.Written(
            "Nothing to conquer and no enemy pawn to raid"))))
        assert(one.options.find(_.ref == DecisionOptionRef.Site(wilds))
          .exists(_.available))
      case other => fail(s"expected a choose-one, got $other")

  test("ruling no site where a Campaign could start, the ACTION is " +
      "unavailable with the reason"):
    val ready = captainsAtHomeWithTheRivalAtARuledSite()
      .pawn(p2, at = CatalogNames.site("Broken Peaks")).ready
    assertEquals(menuOption(ready), DecisionOption.Unavailable(
      DecisionOption.Denizen(source), OptionReason.Written(
        "No site you rule where a Campaign could start")))

  test("the Campaign from a ruled site adds its warbands, spends no Supply " +
      "and pays the favor"):
    val rules = ActPowerHarness.rulesWithPrintedRestrictions()
    val (_, queued) = campaignAtTheWilds(rules,
      captainsAtHomeWithTheRivalAtARuledSite().ready)
    val live = Outcomes.ready(queued.state)
    assertEquals(CampaignSetup.originOf(live, p1), Some(wilds))
    assertEquals(CampaignSetup.atOrigin(live, p1), 2)
    assertEquals(Look(live).supply(p1), 7)
    assertEquals(Look(live).favor(p1), 0)
    assertEquals(Look(live).tokensOn(card), Tokens(1, 0))

  test("a defeat with a real choice splits the dead between board and " +
      "site"):
    // Four committed, two hollow swords against the pawn's two blank dice
    // and p2's three board warbands: a defeat, so two die.
    val rules = ActPowerHarness.rulesWithPrintedRestrictions(
      PlanDriver.losingDice)
    val (_, queued) = campaignAtTheWilds(rules,
      captainsAtHomeWithTheRivalAtARuledSite(onBoard = 1, atWilds = 3).ready)
    val fought = PlanDriver.Run(rules, queued, queued.events)
      .answer(p1, CampaignIds.force, ChooseAmountAnswer(4)).finishPlanWindows
    assert(PlanDriver.awaits(fought, p1, CampaignIds.originDeaths))
    val done = fought.answer(p1, CampaignIds.originDeaths,
      ChooseAmountAnswer(1))
    assertEquals(Look(done.state).boardWarbands(p1), 0)
    assertEquals(Look(done.state).forces(wilds), SiteForces.Occupied(
      Look(done.state).forceKind(p1), 2))

  test("a replayed use reaches the same state"):
    val rules = ActPowerHarness.rulesWithPrintedRestrictions()
    val ready = captainsAtHomeWithTheRivalAtARuledSite().ready
    val (asked, queued) = campaignAtTheWilds(rules, ready)
    assertEquals(ActPowerHarness.replayed(rules, MenuPlay.withoutMenu(ready),
      asked.events ++ queued.events), Outcomes.ready(queued.state))
