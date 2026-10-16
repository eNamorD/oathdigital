package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.powers.poweruse.CityWall
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Wild Allies (card 198): "ACTION: Campaign at any site with a Beast
  * card. Act as if your pawn is there. Spend no Supply and add your
  * warbands there to your force." (catalog batch 8, slice 13). */
class WildAlliesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Wild Allies")
  private val source = DecisionOptionRef.Denizen(card)
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val home = CatalogNames.site("Ancient City")
  /** In the Provinces, away from p1's Cradle, with no pawn. */
  private val wilds = CatalogNames.site("Deep Woods")
  /** A third site with no pawn. */
  private val lonely = CatalogNames.site("Desolate Shore")
  private val rules = ActPowerHarness.rulesWithPrintedRestrictions()

  /** p1 holds Wild Allies faceup with a secret to pay; Errand Boy, a
    * Beast, stands at the wilds, with p2's pawn and 2 of p1's warbands
    * there. */
  private def alliesWithABeastSiteHoldingTheRival: Table = Table.start
    .adviser(p1, card).denizen(errandBoy, at = wilds).pawn(p2, at = wilds)
    .warbandsAt(wilds, p1, 2)

  private def menuOption(ready: ReadyGame): DecisionOption =
    MenuPlay.menuOptionFor(catalog, ready, ActionRef.UsePower(WildAllies.id),
      Vector(source))

  /** The options of the site question the use is parked on. */
  private def siteOptions(t: OathTransition): Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog, WalkerPowerCatalog.default(catalog),
        PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        assertEquals(decide.decisionId, WildAllies.siteDecisionId)
        decide.query match
          case one: DecisionQuery.ChooseOne => one.options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  /** The use answered with the wilds. */
  private def campaignAtTheWilds(ready: ReadyGame): (OathTransition,
      OathTransition) =
    val asked = accepted(ActPowerHarness.use(rules, ready, WildAllies.id,
      source))
    (asked, accepted(ActPowerHarness.answer(rules, asked.state,
      WildAllies.siteDecisionId, DecisionOptionRef.Site(wilds))))

  test("with no Beast card at any site the ACTION is unavailable, with " +
      "the reason"):
    val ready = Table.start.adviser(p1, card).ready
    assertEquals(menuOption(ready), DecisionOption.Unavailable(
      DecisionOption.Denizen(source), OptionReason.Written(
        "No site with a Beast card where a Campaign could start")))

  test("the site question lists the Beast sites in map order, and one " +
      "with nothing to attack is unavailable with why"):
    val ready = alliesWithABeastSiteHoldingTheRival
      .denizen(CatalogNames.denizen("Wolves"), at = lonely).ready
    val options = siteOptions(accepted(ActPowerHarness.use(rules, ready,
      WildAllies.id, source)))
    val map = ready.game.current.map
    assertEquals(options.map(_.ref), map.inPlay.filter(site =>
      site == wilds || site == lonely).map(DecisionOptionRef.Site(_)))
    assertEquals(options.find(_.ref == DecisionOptionRef.Site(lonely)),
      Some(DecisionOption.Unavailable(DecisionOption.Site(
        DecisionOptionRef.Site(lonely)), OptionReason.Written(
        "Nothing to conquer and no enemy pawn to raid"))))
    assert(options.find(_.ref == DecisionOptionRef.Site(wilds))
      .exists(_.available))

  test("the Campaign runs from the chosen site for no Supply, its " +
      "warbands there join the force, and the pawn stays"):
    val (_, queued) = campaignAtTheWilds(
      alliesWithABeastSiteHoldingTheRival.ready)
    val live = Outcomes.ready(queued.state)
    assertEquals(live.game.current.walkerProcedure, Some(ActionRef.Campaign))
    assertEquals(CampaignSetup.originOf(live, p1), Some(wilds))
    assertEquals(CampaignSetup.atOrigin(live, p1), 2)
    assertEquals(Look(live).pawn(p1), home)
    assertEquals(Look(live).supply(p1), 7)
    assertEquals(Look(live).adviserTokens(p1, card), Tokens(0, 1))
    ParkedNode.of(queued.state, catalog,
        WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        assertEquals(decide.decisionId, CampaignIds.force)
        decide.query match
          case amount: DecisionQuery.ChooseAmount =>
            assertEquals(amount.max, 5)
          case other => fail(s"expected the force question, got $other")
      case other => fail(s"expected the force question, got $other")

  test("an enemy standing at City Wall's site cannot use it, with City " +
      "Wall's reason"):
    // City Wall at Ancient City, ruled by p2; p1, an enemy, stands there.
    val ready = alliesWithABeastSiteHoldingTheRival
      .denizen(CatalogNames.denizen("City Wall"), at = home)
      .warbandsAt(home, p2, 1).ready
    menuOption(ready) match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        assertEquals(ask.power, CityWall.id)
      case other => fail(s"expected an askable option, got $other")

  test("using it writes the site it campaigns at"):
    val (_, queued) = campaignAtTheWilds(
      alliesWithABeastSiteHoldingTheRival.ready)
    assertEquals(NoteText.said(WildAllies.id, WildAllies.noteKeys,
      queued.events), Vector(NoteText.Said(NoteKey.Used,
      s"Campaign at ${wilds.value}.", covers = false)))

  test("a replayed use reaches the same state"):
    val ready = alliesWithABeastSiteHoldingTheRival.ready
    val (asked, queued) = campaignAtTheWilds(ready)
    assertEquals(ActPowerHarness.replayed(rules, MenuPlay.withoutMenu(ready),
      asked.events ++ queued.events), Outcomes.ready(queued.state))
