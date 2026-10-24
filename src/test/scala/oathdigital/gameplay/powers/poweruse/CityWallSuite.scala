package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.{CampaignFixture, OathRules, PowerAccess}
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.phases.wake.ForcedWakeProcedure
import oathdigital.gameplay.powerresolver.{ModifierPicks, PowerUse,
  PowerUseStep}
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.powers.campaign.PlanDriver
import oathdigital.gameplay.powers.travel.{TravelFixture, Tyrant}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, ParkedNode, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** City Wall (card 252): "While at this site, enemies of City Wall's ruler
  * cannot place [favor] / [secret] on cards or use non-persistent powers."
  * (catalog batch 8, slice 10, part 2). */
class CityWallSuite extends munit.FunSuite:
  private val wall = CatalogNames.denizen("City Wall")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val source = PowerSourceRef.Card(wall)

  /** p1's Act with City Wall at Ancient City, which `ruled` gives its
    * ruler (p2 by default), and p1's pawn at `pawnAt`. */
  private def wallAtAncientCity(pawnAt: SiteId = home,
      ruled: Table => Table = _.warbandsAt(home, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = pawnAt)
      .denizen(wall, at = home).denizen(wrestlers, at = woods)).ready

  private def nonPersistentUse: PowerUse = PowerUse(p1,
    PowerId("test.power"), Some(wrestlers), persistent = false,
    costsSecret = false)

  test("it refuses an enemy at its site any non-persistent power, waivable " +
      "by the ruler"):
    val refusal = CityWall.refusal(wallAtAncientCity(), nonPersistentUse)
    assertEquals(refusal.map(_.note), Some(CityWall.usePlain(source)))
    assertEquals(refusal.flatMap(_.reason.waiver).flatMap(_.granter),
      Some(p2))

  test("away from its site, or as its ruler, nothing is refused"):
    assertEquals(CityWall.refusal(wallAtAncientCity(pawnAt = woods),
      nonPersistentUse), None)
    assertEquals(CityWall.refusal(wallAtAncientCity(ruled =
      _.warbandsAt(home, p1, 1)), nonPersistentUse), None)

  test("it refuses an enemy at its site a payment onto any card"):
    val ban = CityWall.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(wrestlers), Cost(favor = 1))
    assertEquals(ban.reason(wallAtAncientCity(), pay).flatMap(_.waiver)
      .map(_.covers), Some(ConsentKey(CityWall.id, p1, "power.use",
      DecisionOptionRef.Denizen(wrestlers))))
    assertEquals(ban.reason(wallAtAncientCity(pawnAt = woods), pay), None)

  test("under bandits the placing ban names nobody, and its plain reason"):
    // A Muster's sources stand at the musterer's site, every one of which
    // City Wall refuses, so no question shows this reason beside a card
    // that may be chosen: the refusal itself is pinned.
    val ban = CityWall.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(wrestlers), Cost(favor = 1))
    val waiver = ban.reason(wallAtAncientCity(ruled = _.bandits(home, 2)),
      pay).flatMap(_.waiver)
    assertEquals(waiver.map(_.granter), Some(None))
    assertEquals(waiver.map(_.plain), Some(CityWall.placingPlain(source)))

  // ---- the paths ---------------------------------------------------------

  private val terror = CatalogNames.denizen("Roving Terror")
  private val terrorRef = DecisionOptionRef.Denizen(terror)

  private val rovingTerror = RovingTerror.forCatalog(catalog)

  /** p1's Act at Ancient City beside Roving Terror and City Wall, which p2
    * rules, with Wrestlers at Broken Peaks to replace, as
    * `SpellBreakerPhaseSuite` lays it out. */
  private def terrorInsideWall: ReadyGame =
    Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home).denizen(wall, at = home)
      .denizen(wrestlers, at = CatalogNames.site("Broken Peaks"))
      .warbandsAt(home, p2, 1).ready

  private def terrorOption(ready: ReadyGame)(using munit.Location)
      : DecisionOption = MenuPlay.menuOptionFor(catalog, ready,
    ActionRef.UsePower(rovingTerror.id), Vector(terrorRef))

  test("the phase menu asks City Wall's ruler about an ACTION used at its " +
      "site"):
    terrorOption(terrorInsideWall) match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        assertEquals(ask.power, CityWall.id)
      case other => fail(s"expected an askable option, got $other")

  test("one grant opens both the ACTION and its cost's placement"):
    val ready = terrorInsideWall
    val granted = ready.copy(consent = ConsentState(answers = Map(
      ConsentKey(CityWall.id, p1, "power.use", terrorRef) ->
        ConsentAnswer.Granted.by(p2)), turn = Some(ConsentTurn.of(ready))))
    assert(terrorOption(granted).available)
    assert(PhasePowerHarness.awaits(accepted(PhasePowerHarness.use(granted,
      rovingTerror, terrorRef)), RovingTerror.decisionId))

  /** Every production walker power, as a command with no modifiers picked
    * gets them. */
  private val everyPower: WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)

  test("a Muster at its site is askable of the ruler, and the decision " +
      "still parks"):
    // Wrestlers and City Wall itself are the sources: placing on either is
    // refused.
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(wall, at = home).denizen(wrestlers, at = home)
      .warbandsAt(home, p2, 1).favor(p1, 4).supply(p1, 7).ready
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    val pending = Outcomes.parked(ProcedureWalker.advance(ready, tree, None,
      everyPower)).tree
    val options = present(ProcedureWalker.parkedDecide(ready, tree, pending,
      everyPower), "the source decision").query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")
    assert(options.exists(_.ref == DecisionOptionRef.Denizen(wrestlers)),
      options.toString)
    assert(options.forall(_.askable), options.toString)

  test("Jinx's reroll at its site is askable, with the placing reason"):
    // p1's Recover at Fair Isle with Jinx faceup, as `JinxSuite` sets it
    // up, City Wall at Fair Isle ruled by p2, parked on the first reroll
    // choice.
    val isle = CatalogNames.site("Fair Isle")
    val jinx = CatalogNames.denizen("Jinx")
    import DefenseDieFace.{Blank, TwoShields}
    val start = Table.start.turn(p1, Phase.Act).pawn(p1, at = isle)
      .relicAt(CatalogNames.relic("Dowsing Sticks"), at = isle)
      .adviser(p1, jinx).secrets(p1, faceUp = 2)
      .denizen(wall, at = isle).warbandsAt(isle, p2, 1).ready
    val game = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = scripted(Vector(Blank, Blank), Vector(TwoShields,
        TwoShields)))
    val rolled = accepted(game.startWalker(Ready(start), ActionRef.Recover,
      p1))
    val firstReroll = RerollChoice.decisionId(RecoverProcedure.recoverPool, 1)
    val jinxOption = ParkedNode.of(rolled.state, catalog,
        WalkerPowerCatalog.default(catalog)).toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _)
            if decide.decisionId == firstReroll => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .flatMap(_.find(_.ref == RerollChoice.optionRef(jinx)))
    jinxOption match
      case Some(DecisionOption.Unavailable(_, ask: OptionReason.Askable)) =>
        assertEquals(ask.note, CityWall.placingAsked(source))
      case other => fail(s"expected an askable Jinx, got $other")

  /** p1 attacks Ancient City, which the rival rules, holding Fire Talkers
    * and Battle Honors faceup, a faceup secret and the Darkest Secret; City
    * Wall stands there when `walled`. */
  private def attackOnRivalsCity(walled: Boolean): CampaignFixture.Board =
    import CampaignFixture.{board, on, withAdviser, withSecrets}
    val talkers = withSecrets(withAdviser(withAdviser(board(warbands = 5,
      supply = 7), "Fire Talkers", Orientation.FaceUp), "Battle Honors",
      Orientation.FaceUp), 1)
    val b = on(talkers)(_.darkestSecret(Some(talkers.actor), secrets = 1)
      .warbandsAt(talkers.origin, talkers.rival, 2))
    if walled then on(b)(_.denizen(wall, at = b.origin)) else b

  private def attackerPlanAsked(b: CampaignFixture.Board): PlanDriver.Run =
    PlanDriver.commit(CampaignFixture.rules(PlanDriver.winningDice), b, 2)

  test("an enemy attacking from its site has no plan to use, so the plan " +
      "window is not asked"):
    val b = attackOnRivalsCity(walled = true)
    assert(!PlanDriver.awaits(attackerPlanAsked(b), b.actor,
      CampaignIds.attackerPlan))

  test("without it, the same attack is offered Fire Talkers"):
    val b = attackOnRivalsCity(walled = false)
    val run = attackerPlanAsked(b)
    assert(PlanDriver.awaits(run, b.actor, CampaignIds.attackerPlan))
    assert(run.offered(b.actor).contains(DecisionOptionRef.Denizen(
      CatalogNames.denizen("Fire Talkers"))))

  /** p1 holds Fire Talkers faceup, a faceup secret and the Darkest Secret,
    * with the pawn at Ancient City; the rival rules Buried Giant with 2
    * warbands and stands there. City Wall stands at `wallAt`, ruled by the
    * rival. */
  private def firesAgainstTheRivalAtBuriedGiant(wallAt: SiteId)
      : (CampaignFixture.Board, SiteId) =
    import CampaignFixture.{board, on, withAdviser, withSecrets}
    val giant = CatalogNames.site("Buried Giant")
    val talkers = withSecrets(withAdviser(board(warbands = 5, supply = 7),
      "Fire Talkers", Orientation.FaceUp), 1)
    val b = on(talkers)(_.darkestSecret(Some(talkers.actor), secrets = 1)
      .pawn(talkers.rival, at = giant).warbandsAt(giant, talkers.rival, 2)
      .denizen(wall, at = wallAt).warbandsAt(wallAt, talkers.rival, 2))
    (b, giant)

  /** p1's Campaign queued from `origin`, a Conquest committing 2. */
  private def conquestQueuedFrom(b: CampaignFixture.Board, origin: SiteId)
      : PlanDriver.Run =
    val game = CampaignFixture.rules(PlanDriver.winningDice)
    PlanDriver.committed(game, CampaignFixture.campaignQueuedFrom(game, b,
      origin, PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))), b,
      force = 2)

  test("an attacker campaigning from City Wall's site is walled in there"):
    val giant = CatalogNames.site("Buried Giant")
    val (b, origin) = firesAgainstTheRivalAtBuriedGiant(wallAt = giant)
    assert(!PlanDriver.awaits(conquestQueuedFrom(b, origin), b.actor,
      CampaignIds.attackerPlan))

  test("an attacker standing at City Wall's site but campaigning from " +
      "elsewhere is not walled in"):
    val (b, origin) = firesAgainstTheRivalAtBuriedGiant(wallAt = home)
    val run = conquestQueuedFrom(b, origin)
    assert(PlanDriver.awaits(run, b.actor, CampaignIds.attackerPlan))
    assert(run.offered(b.actor).contains(DecisionOptionRef.Denizen(
      CatalogNames.denizen("Fire Talkers"))))

  // ---- an as-if Trade site (item 13c) ------------------------------------

  private val friends = CatalogNames.denizen("Small Friends")
  private val library = CatalogNames.denizen("Map Library")
  /** A plain Beast denizen, so Small Friends may act as if at its site. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val tradeRules = PowerRules.withWalkerPowers
  private val tradeModifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)

  /** `ready` after `ActAsIf(site)` ran through the pipeline, as the walk's
    * site answer leaves it. */
  private def afterActingAsIfAt(ready: ReadyGame, site: SiteId): ReadyGame =
    accepted(OperationPipeline.run(ready, Vector(ActAsIf(site)),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  /** p1's Act holding Small Friends, with p1's pawn at `pawnAt` and City
    * Wall at Ancient City, which `ruled` gives its ruler (p2 by default).
    * Errand Boy, a Beast card, and Map Library stand at `libraryAt`. */
  private def smallFriendsAndMapLibraryAt(libraryAt: SiteId, pawnAt: SiteId,
      ruled: Table => Table = _.warbandsAt(home, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = pawnAt)
      .adviser(p1, friends).denizen(wall, at = home)
      .denizen(errandBoy, at = libraryAt).denizen(library, at = libraryAt))
      .ready

  /** p1 starts a Trade for favor, parked on the modifier decision. */
  private def tradeForFavorBegun(ready: ReadyGame): OathTransition =
    accepted(MenuPlay.started(tradeRules, Ready(ready), p1, ActionRef.Trade,
      Vector(DecisionOptionRef.Button("favor"))))

  /** `from`, then p1's answer `ref` at `decisionId`; the events run from
    * the Trade's start. */
  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(tradeRules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  /** p1's Trade for favor in `ready` with Small Friends picked and acting
    * as if at `site`, parked on the next modifier pass. */
  private def tradingAsIfAt(ready: ReadyGame, site: SiteId): OathTransition =
    answeredWith(answeredWith(tradeForFavorBegun(ready), tradeModifiers,
      DecisionOptionRef.Denizen(friends)), PowerAccess.asIfDecision,
      DecisionOptionRef.Site(site))

  /** Map Library's state and reason at the parked modifier pass. */
  private def mapLibraryOffer(at: OathTransition)
      : Option[(String, Option[String])] =
    PhasePowerHarness.queryOf(at, p1).flatMap(_.offeredOptions
      .find(_.id == library.value)
      .map(option => option.state -> option.reason))

  test("an enemy trading as if at its site is asked before a later " +
      "non-persistent modifier there"):
    val acting = tradingAsIfAt(smallFriendsAndMapLibraryAt(home,
      pawnAt = woods), home)
    assertEquals(mapLibraryOffer(acting).map(_._1), Some("askable"))

  test("where nobody may be asked, Small Friends is not offered toward its " +
      "site, since that Trade would place a secret on a card"):
    // Wrestlers at the pawn site lets the Trade start without Small Friends.
    def offered(ruled: Table => Table): Option[Vector[String]] =
      PhasePowerHarness.queryOf(tradeForFavorBegun(
        smallFriendsAndMapLibraryAt(home, pawnAt = woods,
          ruled = steps => ruled(steps).denizen(wrestlers, at = woods))), p1)
        .map(_.offeredOptions.map(_.id))
    assertEquals(offered(_.warbandsAt(home, p2, 1)),
      Some(Vector(ModifierPicks.start.key, friends.value)))
    assertEquals(offered(_.bandits(home, 2)),
      Some(Vector(ModifierPicks.start.key)))

  test("an enemy acting as if at its site is refused a payment onto any " +
      "card"):
    // Every selected modifier is non-persistent, so a pick shows the use
    // reason first; the placing ban is pinned on the payment itself.
    val ban = CityWall.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(wrestlers), Cost(favor = 1))
    val away = wallAtAncientCity(pawnAt = woods)
    assertEquals(ban.reason(away, pay), None)
    assert(ban.reason(afterActingAsIfAt(away, home), pay).nonEmpty)

  test("once the walk ends, the enemy acts from their pawn again"):
    val away = wallAtAncientCity(pawnAt = woods)
    assert(CityWall.refusal(afterActingAsIfAt(away, home),
      nonPersistentUse).nonEmpty)
    val done = Outcomes.finished(ProcedureWalker.advance(away,
      ActAsIf(home), None, everyPower))
    assertEquals(CityWall.refusal(done.treeless, nonPersistentUse), None)

  test("an enemy at its site who trades as if elsewhere is not walled in " +
      "for the rest of that Trade"):
    val ready = smallFriendsAndMapLibraryAt(woods, pawnAt = home)
    // p2 lets p1 use Small Friends at City Wall's site.
    val granted = ready.copy(consent = ConsentState().granting(Vector(
      ConsentKey(CityWall.id, p1, "power.use",
        DecisionOptionRef.Denizen(friends))), ConsentTurn.of(ready)))
    val acting = tradingAsIfAt(granted, woods)
    assertEquals(mapLibraryOffer(acting), Some("available" -> None))

  test("only the walk's actor acts from the as-if site"):
    // Bandits rule City Wall's site, so p2 is an enemy too.
    val ready = afterActingAsIfAt(Table.start.turn(p1, Phase.Act)
      .pawn(p1, at = woods).pawn(p2, at = woods).denizen(wall, at = home)
      .bandits(home, 2).ready, home)
    assert(CityWall.refusal(ready, nonPersistentUse).nonEmpty)
    assertEquals(CityWall.refusal(ready, nonPersistentUse.copy(user = p2)),
      None)

  test("a game parked after the site answer and rebuilt from its events " +
      "keeps the enemy walled in"):
    val ready = smallFriendsAndMapLibraryAt(home, pawnAt = woods)
    val acting = tradingAsIfAt(ready, home)
    val rebuilt = ActPowerHarness.replayed(tradeRules, ready, acting.events)
    assertEquals(rebuilt.game.current.walkerAsIfSite, Some(home))
    assert(CityWall.refusal(rebuilt, nonPersistentUse).nonEmpty)

  /** The actor holding Tyrant at Buried Giant, laid out by `steps`. */
  private def holdingTyrant(steps: Table => Table): ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(table =>
      steps(table.adviser(PowerFixture.actor, "Tyrant")))

  test("Tyrant arriving at its site under bandits kills nothing"):
    import TravelFixture.coast
    val ready = holdingTyrant(_.denizen(wall, at = coast).bandits(coast, 2))
    val done = Outcomes.ready(accepted(TravelFixture.travel(ready, coast))
      .state)
    assertEquals(Look(done).forces(coast),
      SiteForces.Occupied(ForceKind.Bandit, 2))

  test("Tyrant arriving at its site, ruled by an enemy, waits at its step"):
    import TravelFixture.coast
    val ready = holdingTyrant(_.denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))
    val travelled = accepted(TravelFixture.travel(ready, coast))
    assertEquals(TravelFixture.parkedDecision(travelled.state).decisionId,
      PowerUseStep.decisionId(Tyrant.id))

  test("Tyrant leaving its site kills at the destination"):
    import TravelFixture.{coast, cradlePlains}
    val ready = holdingTyrant(_.denizen(wall, at = cradlePlains)
      .warbandsAt(cradlePlains, p2, 2).warbandsAt(coast, p2, 2))
    val done = Outcomes.ready(accepted(TravelFixture.travel(ready, coast))
      .state)
    assertEquals(Look(done).forces(coast),
      SiteForces.Occupied(Look(done).forceKind(p2), 1))

  test("Hunger at its site is still due when an enemy rules it, for the " +
      "ruler to decide, and not under bandits"):
    // p2 holds Hunger faceup and Wrestlers at Broken Peaks, as
    // `HungerSuite` stages it; City Wall stands there.
    val peaks = CatalogNames.site("Broken Peaks")
    def holding(steps: Table => Table): ReadyGame =
      steps(Table.start.adviser(p2, "Hunger").adviser(p2, "Wrestlers")
        .pawn(p2, at = peaks).denizen(wall, at = peaks)).ready
    val phasePowers = PhasePowerCatalog.default(catalog)
    def forced(ready: ReadyGame) =
      ForcedWakeProcedure.due(catalog, ready, p2, phasePowers)
    assert(forced(holding(_.warbandsAt(peaks, p1, 1))).nonEmpty)
    assertEquals(forced(holding(_.bandits(peaks, 1))), Vector.empty)
