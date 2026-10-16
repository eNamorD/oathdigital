package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.{CampaignFixture, OathRules}
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.phases.wake.ForcedWakeProcedure
import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.powers.campaign.PlanDriver
import oathdigital.gameplay.powers.travel.TravelFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes,
  ParkedNode, PhasePowerHarness, Table}
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
        ConsentAnswer.Granted), turn = Some(ConsentTurn.of(ready))))
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
      .flatMap(_.find(_.ref == DecisionOptionRef.Denizen(jinx)))
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

  /** The actor holding Tyrant at Buried Giant, laid out by `steps`. */
  private def holdingTyrant(steps: Table => Table): ReadyGame =
    TravelFixture.on(TravelFixture.board(supply = 7))(table =>
      steps(table.adviser(PowerFixture.actor, "Tyrant")))

  test("Tyrant arriving at its site, ruled by an enemy, kills nothing"):
    import TravelFixture.coast
    val ready = holdingTyrant(_.denizen(wall, at = coast)
      .warbandsAt(coast, p2, 2))
    val done = Outcomes.ready(accepted(TravelFixture.travel(ready, coast))
      .state)
    assertEquals(Look(done).forces(coast),
      SiteForces.Occupied(Look(done).forceKind(p2), 2))

  test("Tyrant leaving its site kills at the destination"):
    import TravelFixture.{coast, cradlePlains}
    val ready = holdingTyrant(_.denizen(wall, at = cradlePlains)
      .warbandsAt(cradlePlains, p2, 2).warbandsAt(coast, p2, 2))
    val done = Outcomes.ready(accepted(TravelFixture.travel(ready, coast))
      .state)
    assertEquals(Look(done).forces(coast),
      SiteForces.Occupied(Look(done).forceKind(p2), 1))

  test("Hunger is not forced on its holder at its site, ruled by an enemy"):
    // p2 holds Hunger faceup and Wrestlers at Broken Peaks, as
    // `HungerSuite` stages it; City Wall stands there, ruled by p1.
    val peaks = CatalogNames.site("Broken Peaks")
    def holding(steps: Table => Table): ReadyGame =
      steps(Table.start.adviser(p2, "Hunger").adviser(p2, "Wrestlers")
        .pawn(p2, at = peaks)).ready
    val phasePowers = PhasePowerCatalog.default(catalog)
    def forced(ready: ReadyGame) =
      ForcedWakeProcedure.due(catalog, ready, p2, phasePowers)
    assert(forced(holding(identity)).nonEmpty)
    assertEquals(forced(holding(_.denizen(wall, at = peaks)
      .warbandsAt(peaks, p1, 1))), Vector.empty)
