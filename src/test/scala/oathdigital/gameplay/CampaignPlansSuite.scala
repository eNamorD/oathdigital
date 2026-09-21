package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignPlans, CampaignSetup}
import oathdigital.gameplay.powers.campaign.{BattlePlans, BrassArmy, Outriders, PlanContext, TitleDefensePlan, Watchdog}
import oathdigital.gameplay.powers.campaign.BattlePlan
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** What the ported plans offer, asked directly: where a plan's card must stand,
  * what it costs and what it does. Whether the user can pay, and how a plan is
  * chosen and applied, are the plan window's business (`CampaignProcedureSuite`
  * and `CampaignPlanWindowSuite`).
  */
class CampaignPlansSuite extends munit.FunSuite {
  private def setupOf(b: Board, force: Int = 2): CampaignSetup = CampaignSetup(
    b.actor, CampaignKind.Conquest, b.origin,
    CampaignSetup.conquestDefender(b.ready, b.actor).get, Vector(b.origin),
    Vector.empty, force)

  private def context(b: Board, side: CampaignPlanSide): PlanContext =
    PlanContext(b.ready, setupOf(b), side)

  private def defending(b: Board): PlanContext = PlanContext(b.ready,
    setupOf(b).copy(defender = CampaignDefender.Player(b.other)),
    CampaignPlanSide.Defender)

  private val outriders = Outriders.forCatalog(catalog).get
  private val brass = BrassArmy.forCatalog(catalog).get
  private val watchdog = Watchdog.forCatalog(catalog).get
  private val outridersCard = cardWith("denizen.outriders")
  private val brassCard = relicWith("relic.brass-army.campaign")
  private val watchdogCard = cardWith("denizen.watchdog")

  test("Outriders is an attacker's plan from any adviser of the attacker, in either orientation") {
    val up = withAdviser(board(), outridersCard, Orientation.FaceUp)
    val down = withAdviser(board(), outridersCard, Orientation.FaceDown)
    Vector(up, down).foreach { b =>
      val offer = outriders.plan(context(b, CampaignPlanSide.Attacker)).get
      assertEquals(offer.source, CampaignPlanSource.Adviser(b.actor,
        DenizenId(outridersCard)))
      assertEquals((offer.costs, offer.effects), (Vector.empty, Vector.empty))
      assertEquals(CampaignPlans.refOf(offer.source),
        DecisionOptionRef.Denizen(DenizenId(outridersCard)))
    }
    assertEquals(outriders.sides, Set[CampaignPlanSide](CampaignPlanSide.Attacker))
  }

  test("a plan is used only by the ruler of its source: a card at the origin does not count unless the attacker rules it") {
    val b = withSiteCard(board(), board().origin, outridersCard)
    assertEquals(outriders.plan(context(b, CampaignPlanSide.Attacker)), None)
    // The attacker rules a second site, and the card stands there.
    val two = board(extras = 1)
    val ruled = two.extras.head
    val staged = withSiteCard(actorRules(two, ruled), ruled, outridersCard)
    assertEquals(outriders.plan(context(staged, CampaignPlanSide.Attacker))
      .map(_.source), Some(CampaignPlanSource.SiteCard(ruled,
        DenizenId(outridersCard))))
  }

  test("a card held by another player is not offered") {
    val b = board()
    val elsewhere = b.copy(ready = withAdviser(b, outridersCard,
      Orientation.FaceUp).ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if (p.player == b.actor)
        p.copy(advisers = Vector.empty) else p.copy(advisers = Vector(
        DenizenState(DenizenId(outridersCard), Orientation.FaceUp,
          Tokens.empty)))))))
    assertEquals(outriders.plan(context(elsewhere, CampaignPlanSide.Attacker)),
      None)
  }

  test("Brass Army costs a secret placed onto a faceup relic, occupied or not, and adds four attack dice") {
    val offer = brass.plan(context(withSecrets(withRelic(board(), brassCard), 1),
      CampaignPlanSide.Attacker)).get
    assertEquals(offer.costs, Vector[CampaignPlanCost](CampaignPlanCost.Secret(1)))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddAttackDice(4)))
    // Whether the attacker can pay is not the offer's business.
    assert(brass.plan(context(withSecrets(withRelic(board(), brassCard), 0),
      CampaignPlanSide.Attacker)).nonEmpty)
    val occupied = withRelic(board(), brassCard)
    val holding = occupied.copy(ready = occupied.ready.updateCurrent(current =>
      current.copy(players = current.players.map(p => p.copy(relics =
        p.relics.map(r => r.copy(tokens = Tokens(1, 1))))))))
    assert(brass.plan(context(holding, CampaignPlanSide.Attacker)).nonEmpty)
    assertEquals(brass.plan(context(board(), CampaignPlanSide.Attacker)), None)
  }

  test("Brass Army needs the relic faceup") {
    val b = withRelic(board(), brassCard)
    val down = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => p.copy(relics = p.relics.map(r =>
        r.copy(orientation = Orientation.FaceDown)))))))
    assertEquals(brass.plan(context(down, CampaignPlanSide.Attacker)), None)
  }

  test("Watchdog adds a defense die when a target is in the Cradle, for a ruler of its card") {
    val base = board()
    assert(base.ready.game.current.map.regionOf(base.origin).contains(Region.Cradle),
      "the fixture's origin must be in the Cradle")
    val bandits = withSiteCard(base, base.origin, watchdogCard)
    val offer = watchdog.plan(context(bandits, CampaignPlanSide.Defender)).get
    assertEquals(offer.source, CampaignPlanSource.SiteCard(base.origin,
      DenizenId(watchdogCard)))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddDefenseDice(1)))
    // A Raid targets no site.
    val raid = PlanContext(bandits.ready, setupOf(bandits).copy(
      kind = CampaignKind.Raid, targetSites = Vector.empty),
      CampaignPlanSide.Defender)
    assertEquals(watchdog.plan(raid), None)
  }

  test("a player defender uses Watchdog from an adviser, and a card at a site the attacker's enemy does not rule is not theirs") {
    val b = againstPlayer(board())
    val held = b.copy(ready = withAdviser(b, watchdogCard, Orientation.FaceUp)
      .ready.updateCurrent(current => current.copy(players = current.players.map(
        p => if (p.player == b.other) p.copy(advisers = Vector(DenizenState(
          DenizenId(watchdogCard), Orientation.FaceUp, Tokens.empty)))
        else p.copy(advisers = Vector.empty)))))
    assertEquals(watchdog.plan(defending(held)).map(_.source),
      Some(CampaignPlanSource.Adviser(b.other, DenizenId(watchdogCard))))
    val siteCard = withSiteCard(againstPlayer(board()), b.origin, watchdogCard)
    assertEquals(watchdog.plan(defending(siteCard)).map(_.source),
      Some(CampaignPlanSource.SiteCard(b.origin, DenizenId(watchdogCard))))
    // The card stands at a site the attacker's pawn is at but the defender does not rule.
    val unruled = withSiteCard(board(), b.origin, watchdogCard)
    assertEquals(watchdog.plan(defending(unruled)), None)
  }

  test("the title adds one defense die to an Oathkeeper and two to a Usurper, and only for a defender who holds it") {
    val b = againstPlayer(board())
    def titled(side: TitleSide): Option[CampaignPlanOffer] = {
      val state = b.copy(ready = b.ready.updateCurrent(c => c.copy(title =
        c.title.copy(side = side))))
      TitleDefensePlan.plan.plan(defending(state))
    }
    assertEquals(titled(TitleSide.Oathkeeper).get.effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(1)))
    assertEquals(titled(TitleSide.Usurper).get.effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(2)))
    assertEquals(titled(TitleSide.Oathkeeper).get.source,
      CampaignPlanSource.Title(b.other))
    assertEquals(CampaignPlans.refOf(titled(TitleSide.Oathkeeper).get.source),
      DecisionOptionRef.Button("title"))
    assertEquals(titled(TitleSide.Oathkeeper).get.label,
      "Oathkeeper title: add 1 defense die")
    assertEquals(TitleDefensePlan.plan.plan(context(b, CampaignPlanSide.Attacker)),
      None)
    assertEquals(TitleDefensePlan.plan.plan(context(board(), CampaignPlanSide.Defender)),
      None)
  }

  test("plans are listed with the title first, then advisers, relics, and cards at sites, then by key") {
    val adviser = OfferedPlan(PowerId("test.a"), CampaignPlanOffer(
      CampaignPlanSource.Adviser(PlayerId("p"), DenizenId("d")), "", Vector.empty,
      Vector.empty))
    val relic = OfferedPlan(PowerId("test.b"), CampaignPlanOffer(
      CampaignPlanSource.Relic(PlayerId("p"), RelicId("r")), "", Vector.empty,
      Vector.empty))
    val site = OfferedPlan(PowerId("test.c"), CampaignPlanOffer(
      CampaignPlanSource.SiteCard(SiteId("s"), DenizenId("d")), "", Vector.empty,
      Vector.empty))
    val edifice = OfferedPlan(PowerId("test.d"), CampaignPlanOffer(
      CampaignPlanSource.SiteEdifice(SiteId("s"), EdificeId("e")), "",
      Vector.empty, Vector.empty))
    val title = OfferedPlan(PowerId("test.e"), CampaignPlanOffer(
      CampaignPlanSource.Title(PlayerId("p")), "t", Vector.empty, Vector.empty))
    assertEquals(CampaignPlans.sorted(Vector(site, edifice, relic, adviser, title))
      .map(_.power.value), Vector("test.e", "test.a", "test.b", "test.c", "test.d"))
  }

  test("a card names its option and the title names it by button") {
    val card = OfferedPlan(PowerId("test.a"), CampaignPlanOffer(
      CampaignPlanSource.SiteEdifice(SiteId("s"), EdificeId("e")), "", Vector.empty,
      Vector.empty))
    assertEquals(CampaignPlans.optionOf(card),
      DecisionOption.Edifice(DecisionOptionRef.Edifice(EdificeId("e"))))
    val title = OfferedPlan(PowerId("test.t"), CampaignPlanOffer(
      CampaignPlanSource.Title(PlayerId("p")), "the words", Vector.empty,
      Vector.empty))
    assertEquals(CampaignPlans.optionOf(title), DecisionOption.Button(
      DecisionOptionRef.Button("title"), "the words"))
  }

  test("the four ported plans register through one object, and are automatic whatever the catalog flag") {
    val plans = BattlePlans.forCatalog(catalog)
    assertEquals(plans.size, 4)
    plans.foreach(plan => assertEquals(plan.resolution, PowerResolution.Automatic))
    assert(plans.forall(_.isInstanceOf[BattlePlan]))
    assertEquals(plans.map(_.id.value).toSet, Set("title.oathkeeper-defense",
      "denizen.outriders", "relic.brass-army.campaign", "denizen.watchdog"))
  }
}
