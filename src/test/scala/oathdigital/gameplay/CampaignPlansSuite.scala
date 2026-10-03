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
class CampaignPlansSuite extends munit.FunSuite:
  private def setupOf(b: Board, force: Int = 2): CampaignSetup = CampaignSetup(
    b.actor, CampaignKind.Conquest, b.origin,
    CampaignSetup.conquestDefender(b.ready, b.actor).get, Vector(b.origin),
    Vector.empty, force)

  private def context(b: Board, side: CampaignPlanSide): PlanContext =
    PlanContext(b.ready, setupOf(b), side)

  private def defending(b: Board): PlanContext = PlanContext(b.ready,
    setupOf(b).copy(defender = CampaignDefender.Player(b.rival)),
    CampaignPlanSide.Defender)

  private val outriders = Outriders
  private val brass = BrassArmy
  private val watchdog = Watchdog
  private val outridersCard = cardWith("denizen.outriders")
  private val brassCard = relicWith("relic.brass-army.campaign")
  private val watchdogCard = cardWith("denizen.watchdog")

  test("Outriders is an attacker's plan from any adviser of the attacker, in either orientation"):
    val up = withAdviser(board(warbands = 5, supply = 7), outridersCard,
      Orientation.FaceUp)
    val down = withAdviser(board(warbands = 5, supply = 7), outridersCard,
      Orientation.FaceDown)
    Vector(up, down).foreach { b =>
      val offer = outriders.plan(context(b, CampaignPlanSide.Attacker)).get
      assertEquals(offer.source, CampaignPlanSource.Adviser(b.actor,
        outridersCard))
      assertEquals((offer.costs, offer.effects), (Vector.empty, Vector.empty))
      assertEquals(CampaignPlans.refOf(offer.source),
        DecisionOptionRef.Denizen(outridersCard))
    }
    assertEquals(outriders.sides, Set[CampaignPlanSide](CampaignPlanSide.Attacker))

  test("a plan is used only by the ruler of its source: a card at the origin does not count unless the attacker rules it"):
    val b = withSiteCard(board(warbands = 5, supply = 7),
      board(warbands = 5, supply = 7).origin, outridersCard)
    assertEquals(outriders.plan(context(b, CampaignPlanSide.Attacker)), None)
    // The attacker rules a second site, and the card stands there.
    val two = board(extras = 1, warbands = 5, supply = 7)
    val ruled = two.extras.head
    val staged = withSiteCard(actorRules(two, ruled), ruled, outridersCard)
    assertEquals(outriders.plan(context(staged, CampaignPlanSide.Attacker))
      .map(_.source), Some(CampaignPlanSource.SiteCard(ruled,
        outridersCard)))

  test("a card held by another player is not offered"):
    val b = board(warbands = 5, supply = 7)
    val elsewhere = b.copy(ready = withAdviser(b, outridersCard,
      Orientation.FaceUp).ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if p.player == b.actor then
        p.copy(advisers = Vector.empty) else p.copy(advisers = Vector(
        DenizenState(outridersCard, Orientation.FaceUp,
          Tokens.empty)))))))
    assertEquals(outriders.plan(context(elsewhere, CampaignPlanSide.Attacker)),
      None)

  test("Brass Army costs a secret placed onto a faceup relic, occupied or not, and adds four attack dice"):
    val offer = brass.plan(context(withSecrets(withRelic(board(warbands = 5, supply = 7), brassCard), 1),
      CampaignPlanSide.Attacker)).get
    assertEquals(offer.costs, Vector[CampaignPlanCost](CampaignPlanCost.Secret(1)))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddAttackDice(4)))
    // Whether the attacker can pay is not the offer's business.
    val occupied = withRelic(board(warbands = 5, supply = 7), brassCard)
    assert(brass.plan(context(withSecrets(occupied, 0),
      CampaignPlanSide.Attacker)).nonEmpty)
    val holding = occupied.copy(ready = occupied.ready.updateCurrent(current =>
      current.copy(players = current.players.map(p => p.copy(relics =
        p.relics.map(r => r.copy(tokens = Tokens(1, 1))))))))
    assert(brass.plan(context(holding, CampaignPlanSide.Attacker)).nonEmpty)
    val bare = board(warbands = 5, supply = 7)
    assertEquals(brass.plan(context(bare, CampaignPlanSide.Attacker)), None)

  test("Brass Army needs the relic faceup"):
    val b = withRelic(board(warbands = 5, supply = 7), brassCard)
    val down = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => p.copy(relics = p.relics.map(r =>
        r.copy(orientation = Orientation.FaceDown)))))))
    assertEquals(brass.plan(context(down, CampaignPlanSide.Attacker)), None)

  test("Watchdog adds a defense die when a target is in the Cradle, for a ruler of its card"):
    val base = board(warbands = 5, supply = 7)
    assert(base.ready.game.current.map.regionOf(base.origin).contains(Region.Cradle),
      "the fixture's origin must be in the Cradle")
    val bandits = withSiteCard(base, base.origin, watchdogCard)
    val offer = watchdog.plan(context(bandits, CampaignPlanSide.Defender)).get
    assertEquals(offer.source, CampaignPlanSource.SiteCard(base.origin,
      watchdogCard))
    assertEquals(offer.effects, Vector[CampaignPlanEffect](
      CampaignPlanEffect.AddDefenseDice(1)))
    // A Raid targets no site.
    val raid = PlanContext(bandits.ready, setupOf(bandits).copy(
      kind = CampaignKind.Raid, targetSites = Vector.empty),
      CampaignPlanSide.Defender)
    assertEquals(watchdog.plan(raid), None)

  test("a player defender uses Watchdog from an adviser, and a card at a site the attacker's enemy does not rule is not theirs"):
    val b = rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7))
    val held = b.copy(ready = withAdviser(b, watchdogCard, Orientation.FaceUp)
      .ready.updateCurrent(current => current.copy(players = current.players.map(
        p => if p.player == b.rival then p.copy(advisers = Vector(DenizenState(
          watchdogCard, Orientation.FaceUp, Tokens.empty)))
        else p.copy(advisers = Vector.empty)))))
    assertEquals(watchdog.plan(defending(held)).map(_.source),
      Some(CampaignPlanSource.Adviser(b.rival, watchdogCard)))
    val siteCard = withSiteCard(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      b.origin, watchdogCard)
    assertEquals(watchdog.plan(defending(siteCard)).map(_.source),
      Some(CampaignPlanSource.SiteCard(b.origin, watchdogCard)))
    // The card stands at a site the attacker's pawn is at but the defender does not rule.
    val unruled = withSiteCard(board(warbands = 5, supply = 7), b.origin,
      watchdogCard)
    assertEquals(watchdog.plan(defending(unruled)), None)

  test("the title adds one defense die to an Oathkeeper and two to a Usurper, and only for a defender who holds it"):
    val b = rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7))
    def titled(side: TitleSide): Option[CampaignPlanOffer] =
      val state = b.copy(ready = b.ready.updateCurrent(c => c.copy(title =
        c.title.copy(side = side))))
      TitleDefensePlan.plan.plan(defending(state))
    assertEquals(titled(TitleSide.Oathkeeper).get.effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(1)))
    assertEquals(titled(TitleSide.Usurper).get.effects,
      Vector[CampaignPlanEffect](CampaignPlanEffect.AddDefenseDice(2)))
    assertEquals(titled(TitleSide.Oathkeeper).get.source,
      CampaignPlanSource.Title(b.rival))
    assertEquals(CampaignPlans.refOf(titled(TitleSide.Oathkeeper).get.source),
      DecisionOptionRef.Button("title"))
    assertEquals(titled(TitleSide.Oathkeeper).get.label,
      "Oathkeeper title: add 1 defense die")
    assertEquals(TitleDefensePlan.plan.plan(context(b, CampaignPlanSide.Attacker)),
      None)
    assertEquals(TitleDefensePlan.plan.plan(context(board(warbands = 5, supply = 7), CampaignPlanSide.Defender)),
      None)

  test("plans are listed with the title first, then advisers, relics, and cards at sites, then by key"):
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

  test("a card names its option and the title names it by button"):
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

  test("a plan's badge names the side or sides that may use it"):
    import oathdigital.model.CampaignPlanSide._
    assertEquals(CampaignPlans.badgeOf(Set(Attacker)), Some("Attack Plan"))
    assertEquals(CampaignPlans.badgeOf(Set(Defender)), Some("Defense Plan"))
    assertEquals(CampaignPlans.badgeOf(Set(Attacker, Defender)),
      Some("Battle Plan"))
    assertEquals(CampaignPlans.badgeOf(Set.empty), None)

  test("the option a plan offers carries that badge"):
    val offered = OfferedPlan(PowerId("relic.sticky-fire"),
      CampaignPlanOffer(CampaignPlanSource.Relic(PlayerId("red"),
        RelicId("relic:sticky-fire")), "Sticky Fire", Vector.empty,
        Vector.empty, Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)))
    assertEquals(CampaignPlans.optionOf(offered), DecisionOption.Badged(
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId("relic:sticky-fire"))),
      "Battle Plan"))

  test("the four ported plans register through one object, and are automatic whatever the catalog flag"):
    val plans = BattlePlans.forCatalog(catalog)
    assertEquals(plans.size, 4)
    plans.foreach(plan => assertEquals(plan.resolution, PowerResolution.Automatic))
    assert(plans.forall(_.isInstanceOf[BattlePlan]))
    assertEquals(plans.map(_.id.value).toSet, Set("title.oathkeeper-defense",
      "denizen.outriders", "relic.brass-army.campaign", "denizen.watchdog"))

  test("a bandit's applied-plan marker reads back to the plan's source"):
    Vector[DecisionOptionRef](DecisionOptionRef.Denizen(DenizenId("56")),
      DecisionOptionRef.Edifice(EdificeId("edifice:homeland-arcane")),
      DecisionOptionRef.Relic(RelicId("relic:circlet")),
      DecisionOptionRef.Button("title")).foreach { ref =>
      assertEquals(CampaignPlans.markedRef(CampaignPlans.appliedMarker(ref)),
        Some(ref))
    }
    assertEquals(CampaignPlans.markedRef(PoolKey("campaign.attack")), None)
