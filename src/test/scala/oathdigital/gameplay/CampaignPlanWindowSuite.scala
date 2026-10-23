package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds,
  CampaignPlanApplication, CampaignProcedure}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.campaign.{BattlePlan, PlanContext,
  PlanDriver, PlanUse}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, RecordedOps}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.{accepted, present}

/** The plan window itself, with plans of the suite's own: what a plan may cost,
  * who pays and when, which plans are offered after each pick, and how a plan
  * that asks a question of its own is resumed. The ported plans are covered by
  * `CampaignPlansSuite` and `CampaignProcedureSuite`.
  */
class CampaignPlanWindowSuite extends munit.FunSuite:
  // The suite's plans sit on these cards; their printed plans play no part.
  private val outriders = CatalogNames.denizen("Outriders")
  private val watchdog = CatalogNames.denizen("Watchdog")

  /** Stub of BattlePlan: offers the costs and effects the test names, from a
    * denizen of its user.
    */
  private final case class Plan(name: String, card: DenizenId,
      planSides: Set[CampaignPlanSide],
      costs: Vector[CampaignPlanCost] = Vector.empty,
      effects: Vector[CampaignPlanEffect] = Vector.empty,
      afterwards: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty)
      extends BattlePlan:
    def id: PowerId = PowerId(s"test.plan.$name")
    def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(card)
    def sides: Set[CampaignPlanSide] = planSides
    def plan(context: PlanContext): Option[CampaignPlanOffer] =
      context.denizen(card).map(CampaignPlanOffer(_, name, costs,
        effects))
    override def later: Map[PowerWindow, PlanUse => Vector[Operation]] =
      afterwards

  private val attacker = Set[CampaignPlanSide](CampaignPlanSide.Attacker)
  private val defender = Set[CampaignPlanSide](CampaignPlanSide.Defender)

  /** The Campaign the actor started, and the transition that committed its
    * force. */
  private final case class Committed(started: OathTransition,
      forced: OathTransition)

  /** Starts the actor's Campaign, chooses a Raid when `raid`, and commits
    * `force` warbands; each step fails with the violation. */
  private def startedAndCommitted(g: OathRules, b: Board, force: Int = 2,
      raid: Boolean = false): Committed =
    val started = accepted(g.startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor))
    val kind =
      if raid then accepted(g.resolveWalker(started.state, b.actor,
        CampaignIds.kind, ChooseOneAnswer(DecisionOptionRef.Button("raid"))))
      else started
    Committed(started, accepted(g.resolveWalker(kind.state, b.actor,
      CampaignIds.force, ChooseAmountAnswer(force))))

  private def pick(g: OathRules, from: OathTransition, who: PlayerId,
      id: String, ref: DecisionOptionRef): OathTransition =
    accepted(g.resolveWalker(from.state, who, id, ChooseOneAnswer(ref)))

  private def parked(plans: Vector[ContributingPower], b: Board,
      from: OathTransition): Decide =
    val current = ready(from.state)
    val tree = accepted(CampaignProcedure.rebuild(catalog, current, b.actor,
      Vector.empty))
    val pending = present(current.game.current.walkerPending,
      "a parked walker decision")
    present(ProcedureWalker.openDecisions(current, tree, pending,
      WalkerPowers(plans)).headOption, "an open decision")

  private def optionsOf(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")

  /** Whether `state` is parked on `id`, awaiting `who`, rebuilt with exactly
    * `plans` as the walker powers -- the same vector `rulesWith` built the
    * state's own `OathRules` with. A per-test power vector, so this
    * constructs its own `ParkedDecisionAssertions` rather than sharing one.
    */
  private def awaits(state: OathState, plans: Vector[ContributingPower],
      who: PlayerId, id: String): Boolean =
    new ParkedDecisionAssertions(catalog, WalkerPowers(plans))
      .parkedDecision(state).exists(facts =>
        facts.decision == id && facts.awaiting == who)

  private def denizen(card: DenizenId): DecisionOptionRef =
    DecisionOptionRef.Denizen(card)
  private val finishOption =
    DecisionOption.Button(CampaignIds.finish, "Finish battle plans")

  // ---- costs --------------------------------------------------------------

  /** p1 holds Outriders with 1 favor on it, and 2 favor of their own. */
  private def outridersHolding: Board =
    val base = board(warbands = 5, supply = 7)
    on(withAdviserFor(base, base.actor, outriders, Orientation.FaceUp,
      Tokens(1, 0)))(_.favor(base.actor, 2))

  /** A plan on Outriders that costs 1 favor and adds 3 attack dice. */
  private val favorPlan = Plan("pay", outriders, attacker,
    Vector(CampaignPlanCost.Favor(1)),
    Vector(CampaignPlanEffect.AddAttackDice(3)))

  test("a favor cost is placed onto a card that already holds resources, " +
      "and the option states the price"):
    val b = outridersHolding
    val plan = favorPlan
    val g = rulesWith(Vector(plan))
    val forced = startedAndCommitted(g, b).forced
    assert(
      awaits(forced.state, Vector(plan), b.actor, CampaignIds.attackerPlan))
    assertEquals(optionsOf(parked(Vector(plan), b, forced)), Vector(
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(outriders)), "Attack Plan"),
        OptionPrice(favor = 1)),
      finishOption))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    assertEquals(Look(picked.state).tokensOn(outriders), Tokens(2, 0))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 1)
    assert(
      RecordedOps.all(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(RecordedOps.all(picked.events).contains(PayCost(b.actor,
      Location.OnCard(outriders), Cost(favor = 1), intoOccupied = true,
      matchingBank = catalog.suitOf(outriders))))

  test("a paid plan's recorded steps replay to the same state and survive " +
      "the journal wire"):
    val b = outridersHolding
    val g = rulesWith(Vector(favorPlan))
    val committed = startedAndCommitted(g, b)
    val picked = pick(g, committed.forced, b.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    val events = committed.started.events ++ committed.forced.events ++
      picked.events
    assertEquals(ActPowerHarness.replayed(g, b.ready, events),
      ready(picked.state))
    assertWireRoundTrips(events)

  test("burnt costs leave the board, rest on no card, and the option states " +
      "them apart"):
    val base = board(warbands = 5, supply = 7)
    val b = on(withAdviserFor(base, base.actor, outriders, Orientation.FaceUp))(
      _.favor(base.actor, 1).secrets(base.actor, faceUp = 1))
    val plan = Plan("burn", outriders, attacker, Vector(
      CampaignPlanCost.FavorBurnt(1), CampaignPlanCost.SecretBurnt(1)),
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val g = rulesWith(Vector(plan))
    val forced = startedAndCommitted(g, b).forced
    assertEquals(optionsOf(parked(Vector(plan), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(outriders)), "Attack Plan"),
        OptionPrice(favorBurnt = 1, secretsBurnt = 1)))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    val after = Look(picked.state).player(b.actor)
    assertEquals((after.board.favor, after.board.faceUpSecrets), (0, 0))
    assertEquals(Look(picked.state).tokensOn(outriders), Tokens.empty)
    assert(RecordedOps.all(picked.events).contains(PayCost(b.actor,
      Location.OnCard(outriders), Cost(favorBurnt = 1, secretBurnt = 1),
      intoOccupied = true, matchingBank = catalog.suitOf(outriders))))

  test("a plan the user cannot pay is not offered, and a window with nothing " +
      "to offer is skipped"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviserFor(base, base.actor, outriders, Orientation.FaceUp)
    val plan = Plan("poor", outriders, attacker,
      Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddAttackDice(3)))
    val forced = startedAndCommitted(rulesWith(Vector(plan)),
      on(b)(_.favor(b.actor, 0))).forced
    assert(awaits(forced.state, Vector(plan), b.actor, CampaignIds.sacrifice))

  test("the options are rebuilt after each pick: a second plan the payment " +
      "made unaffordable is gone"):
    val base = board(warbands = 5, supply = 7)
    val b = on(withAdviserFor(withAdviserFor(base, base.actor, outriders,
      Orientation.FaceUp), base.actor, watchdog, Orientation.FaceUp))(
      _.secrets(base.actor, faceUp = 1))
    val first = Plan("first", outriders, attacker,
      Vector(CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val second = Plan("second", watchdog, attacker,
      Vector(CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val plans = Vector[ContributingPower](first, second)
    val g = rulesWith(plans)
    val forced = startedAndCommitted(g, b).forced
    // The two plans and Finish.
    val offered = optionsOf(parked(plans, b, forced))
    assertEquals(offered.size, 3)
    assertEquals(offered.map(_.ref).toSet, Set(denizen(outriders),
      denizen(watchdog), CampaignIds.finish))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    assert(awaits(picked.state, plans, b.actor, CampaignIds.sacrifice))

  test("a defender's plan is paid at once: favor goes to the card's suit " +
      "bank and a secret turns facedown"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(base, base.rival, outriders, Orientation.FaceUp))(
      _.favor(base.rival, 2).secrets(base.rival, faceUp = 1, faceDown = 0))
    val plan = Plan("guard", outriders, defender, Vector(
      CampaignPlanCost.Favor(1), CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val g = rulesWith(Vector(plan))
    val committed = startedAndCommitted(g, b)
    val forced = committed.forced
    assert(
      awaits(forced.state, Vector(plan), b.rival, CampaignIds.defenderPlan))
    assertEquals(optionsOf(parked(Vector(plan), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(outriders)), "Defense Plan"),
        OptionPrice(favor = 1, secrets = 1)))
    val suit = catalog.suitOf(outriders).get
    val bank = ready(forced.state).banks.favor.getOrElse(suit, 0)
    val picked = pick(g, forced, b.rival, CampaignIds.defenderPlan,
      denizen(outriders))
    assertEquals(Look(picked.state).tokensOn(outriders), Tokens.empty)
    assertEquals(ready(picked.state).banks.favor.getOrElse(suit, 0), bank + 1)
    val after = Look(picked.state).player(b.rival).board
    assertEquals((after.favor, after.faceUpSecrets, after.faceDownSecrets),
      (1, 0, 1))
    assert(
      RecordedOps.all(picked.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // The recorded payment is the requested one, and replay settles it again.
    assertEquals(ActPowerHarness.replayed(g, b.ready,
      committed.started.events ++ forced.events ++ picked.events),
      ready(picked.state))

  test("a defender plan with a cost the defender cannot pay is not offered"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    // The rival keeps only 1 faceup secret, so burning 2 is beyond them.
    val b = on(withAdviserFor(base, base.rival, outriders, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1))
    val plan = Plan("guard", outriders, defender,
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val forced = startedAndCommitted(rulesWith(Vector(plan)), b).forced
    assert(awaits(forced.state, Vector(plan), b.actor, CampaignIds.sacrifice))

  // ---- a warband sacrifice ------------------------------------------------

  private def defenderHolding(b: Board, warbands: Int): Board =
    val against = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(b))
    on(withAdviserFor(against, against.rival, outriders, Orientation.FaceUp))(
      _.boardWarbands(against.rival, warbands))

  private val sacrificing = Plan("sacrifice", outriders, defender,
    Vector(CampaignPlanCost.SacrificeWarband),
    Vector(CampaignPlanEffect.AddDefenseDice(1)))

  test("a Raid defender sacrifices a warband from the board"):
    val b = defenderHolding(board(warbands = 4, supply = 7), 3)
    val g = rulesWith(Vector(sacrificing))
    val forced = startedAndCommitted(g, b, raid = true).forced
    assert(awaits(forced.state, Vector(sacrificing), b.rival,
      CampaignIds.defenderPlan))
    assertEquals(optionsOf(parked(Vector(sacrificing), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(outriders)), "Defense Plan"),
        OptionPrice(warbands = 1)))
    val picked = pick(g, forced, b.rival, CampaignIds.defenderPlan,
      denizen(outriders))
    assertEquals(Look(picked.state).player(b.rival).board.warbands, 2)
    assertEquals(RecordedOps.all(picked.events).collect { case sacrifice: Sacrifice =>
      sacrifice }, Vector(Sacrifice(b.rival,
        Piece.Warbands(ForceKind.Exile(b.player(b.rival).lineage), 1),
        PositionedLocation(Location.PlayArea(b.rival)))))

  test("a Raid defender with no warband on the board cannot pay the " +
      "sacrifice, so the plan is not offered"):
    val b = defenderHolding(board(warbands = 4, supply = 7), 0)
    val g = rulesWith(Vector(sacrificing))
    val forced = startedAndCommitted(g, b, raid = true).forced
    assert(
      awaits(forced.state, Vector(sacrificing), b.actor, CampaignIds.sacrifice))

  test("a Conquest defender sacrifices from the one target site it rules"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(against, against.rival, outriders,
      Orientation.FaceUp)
    val g = rulesWith(Vector(sacrificing))
    val forced = startedAndCommitted(g, b).forced
    assert(awaits(forced.state, Vector(sacrificing), b.rival,
      CampaignIds.defenderPlan))
    val picked = pick(g, forced, b.rival, CampaignIds.defenderPlan,
      denizen(outriders))
    // The rival's 2 warbands rule the origin; the plan sacrifices one.
    assertEquals(ready(picked.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 2 - 1))

  test("with several target sites the defender chooses which pays, and the " +
      "walk resumes inside the plan"):
    val two = rivalRulesOriginAndHoldsOathkeeper(
      board(extras = 1, warbands = 5, supply = 7))
    val extra = two.extras.head
    val lineage = two.player(two.rival).lineage
    // The rival rules the extra site with 2 warbands too.
    val staged = withAdviserFor(on(two)(_.warbandsAt(extra, two.rival, 2)),
      two.rival, outriders, Orientation.FaceUp)
    val g = rulesWith(Vector(sacrificing))
    val started = accepted(g.startWalker(Ready(staged.ready),
      ActionRef.Campaign, staged.actor))
    val targeted = accepted(g.resolveWalker(started.state, staged.actor,
      CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(extra)))))
    val forced = accepted(g.resolveWalker(targeted.state, staged.actor,
      CampaignIds.force, ChooseAmountAnswer(2)))
    val picked = pick(g, forced, staged.rival, CampaignIds.defenderPlan,
      denizen(outriders))
    // The plan asks which force pays before it kills anything.
    assert(awaits(picked.state, Vector(sacrificing), staged.rival,
      CampaignIds.planSacrifice))
    assertEquals(ready(picked.state).game.current.map.sites(extra).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    val paid = accepted(g.resolveWalker(picked.state, staged.rival,
      CampaignIds.planSacrifice,
      ChooseOneAnswer(DecisionOptionRef.Site(extra))))
    assertEquals(ready(paid.state).game.current.map.sites(extra).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    assertEquals(ready(paid.state).game.current.map.sites(staged.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    assert(
      RecordedOps.all(paid.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // Nothing else is offered, so the window ends and the attacker sacrifices.
    assert(awaits(paid.state, Vector(sacrificing), staged.actor,
      CampaignIds.sacrifice))

  // ---- effects ------------------------------------------------------------

  test("a defender plan that removes attack dice takes what the pool holds, " +
      "and none from an empty pool"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, outriders, Orientation.FaceUp)
    val plan = Plan("remove", outriders, defender, effects =
      Vector(CampaignPlanEffect.RemoveAttackDice(3)))
    val g = rulesWith(Vector(plan))
    val forced = startedAndCommitted(g, b, force = 2).forced
    val picked = pick(g, forced, b.rival, CampaignIds.defenderPlan,
      denizen(outriders))
    assert(
      RecordedOps.all(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val none = startedAndCommitted(g, b, force = 0).forced
    val nothing = pick(g, none, b.rival, CampaignIds.defenderPlan,
      denizen(outriders))
    assertEquals(RecordedOps.all(nothing.events).collect { case pool: ModifyDicePool => pool },
      Vector.empty)

  test("a plan's own operations run after its payment"):
    val base = board(warbands = 5, supply = 4)
    val b = withAdviserFor(base, base.actor, outriders, Orientation.FaceUp)
    val plan = Plan("run", outriders, attacker, effects =
      Vector(CampaignPlanEffect.Run(Vector(GainSupply(b.actor, 1)))))
    val g = rulesWith(Vector(plan))
    val forced = startedAndCommitted(g, b).forced
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    // 4 Supply, less the Campaign's 2, plus the 1 the plan gains.
    assertEquals(Look(picked.state).player(b.actor).board.supply.supply,
      4 - 2 + 1)

  // ---- other powers change what a plan costs ------------------------------

  /** Stub of ContributingPower: adds one secret, placed on its card, to every
    * attacker plan. */
  private final case class Surcharge() extends ContributingPower:
    def id: PowerId = PowerId("test.surcharge")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignPlanApplication -> Vector[Contribution](
        Transform((ctx, children) => ctx.operation match {
          case application: CampaignPlanApplication
              if application.side == CampaignPlanSide.Attacker =>
            application.source match {
              case CampaignPlanSource.Adviser(user, card) => children :+
                BuildOps((_, _) => Right(Vector[CoreOperation](
                  Costs.onCard(user, card, Cost(secret = 1), catalog,
                    intoOccupied = true))))
              case _ => children
            }
          case _ => children
        })))

  test("a power that adds to a plan's cost changes what is offered, and the " +
      "option's price"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviserFor(base, base.actor, outriders, Orientation.FaceUp)
    val plan = Plan("free", outriders, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val plain = Vector[ContributingPower](plan)
    val taxed = Vector[ContributingPower](plan, Surcharge())
    // Without the surcharge the free plan is offered, free.
    val freely = startedAndCommitted(rulesWith(plain),
      on(b)(_.secrets(b.actor, faceUp = 0))).forced
    assertEquals(optionsOf(parked(plain, b, freely)).head,
      DecisionOption.Badged(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(outriders)),
        "Attack Plan"))
    // With it, and no secret to pay, it is not offered at all.
    val broke = startedAndCommitted(rulesWith(taxed),
      on(b)(_.secrets(b.actor, faceUp = 0))).forced
    assert(awaits(broke.state, taxed, b.actor, CampaignIds.sacrifice))
    // With a secret it is offered, priced, and choosing it pays the secret.
    val rich = on(b)(_.secrets(b.actor, faceUp = 1))
    val g = rulesWith(taxed)
    val forced = startedAndCommitted(g, rich).forced
    assertEquals(optionsOf(parked(taxed, rich, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(outriders)), "Attack Plan"),
        OptionPrice(secrets = 1)))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    assertEquals(Look(picked.state).tokensOn(outriders), Tokens(0, 1))
    assertEquals(Look(picked.state).player(b.actor).board.faceUpSecrets, 0)

  // ---- later windows ------------------------------------------------------

  test("what a used plan adds at a later window runs only when the plan was " +
      "chosen"):
    val base = board(warbands = 5, supply = 4)
    val b = withAdviserFor(base, base.actor, outriders, Orientation.FaceUp)
    val plan = Plan("later", outriders, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)), afterwards = Map(
        PowerWindow.CampaignActionEligibility -> (use =>
          Vector[Operation](GainSupply(use.actor, 1)))))
    val plans = Vector[ContributingPower](plan)
    def finished(chooses: Boolean): Int =
      val g = rulesWith(plans, PlanDriver.winningDice)
      val forced = startedAndCommitted(g, b, force = 4).forced
      val next =
        if chooses then pick(g, forced, b.actor, CampaignIds.attackerPlan,
          denizen(outriders))
        else pick(g, forced, b.actor, CampaignIds.attackerPlan,
          CampaignIds.finish)
      val sacrificed = accepted(g.resolveWalker(next.state, b.actor,
        CampaignIds.sacrifice, ChooseAmountAnswer(0)))
      val placed = accepted(g.resolveWalker(sacrificed.state, b.actor,
        CampaignIds.placement, ChooseAmountAnswer(0)))
      Look(placed.state).player(b.actor).board.supply.supply
    // 4 Supply, less the Campaign's 2, plus the 1 the plan gains when chosen.
    assertEquals(finished(chooses = true), 4 - 2 + 1)
    assertEquals(finished(chooses = false), 4 - 2)

  // ---- a bandit defender --------------------------------------------------

  test("a bandit defender applies every cost-free plan of a site it rules, " +
      "and none that costs"):
    val two = board(extras = 1, warbands = 5, supply = 7)
    val staged = withSiteCard(withSiteCard(two, two.origin, outriders),
      two.extras.head, watchdog)
    val free = Plan("free", outriders, defender, effects =
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val costly = Plan("costly", watchdog, defender,
      Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(5)))
    val g = rulesWith(Vector(free, costly))
    val funded = on(staged)(_.favor(staged.actor, 3))
    val started = accepted(g.startWalker(Ready(funded.ready),
      ActionRef.Campaign, funded.actor))
    val targeted = accepted(g.resolveWalker(started.state, funded.actor,
      CampaignIds.targets, ChooseManyAnswer(Vector.empty)))
    val forced = accepted(g.resolveWalker(targeted.state, funded.actor,
      CampaignIds.force, ChooseAmountAnswer(2)))
    val pools = RecordedOps.all(forced.events).collect {
      case pool @ ModifyDicePool(CampaignIds.defensePool, _, _) => pool }
    assertEquals(pools.filter(_.delta == 1).size, 1)
    assertEquals(pools.filter(_.delta == 5), Vector.empty)
    assertEquals(RecordedOps.all(forced.events).collect { case pay: PayCost => pay },
      Vector.empty)
    assert(awaits(forced.state, Vector(free, costly), staged.actor,
      CampaignIds.sacrifice))

  test("Finish ends the window and a chosen source is not offered again"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviserFor(base, base.actor, outriders, Orientation.FaceUp)
    val plan = Plan("once", outriders, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val g = rulesWith(Vector(plan))
    val forced = startedAndCommitted(g, b).forced
    val finished = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      CampaignIds.finish)
    assert(awaits(finished.state, Vector(plan), b.actor, CampaignIds.sacrifice))
    assertEquals(RecordedOps.all(finished.events).collect { case pool: ModifyDicePool => pool },
      Vector.empty)
    // With a second plan left the window stays open, and the source already
    // chosen is no longer offered.
    val second = Plan("second", watchdog, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val both = withAdviserFor(b, b.actor, watchdog, Orientation.FaceUp)
    val g2 = rulesWith(Vector(plan, second))
    val forced2 = startedAndCommitted(g2, both).forced
    val taken = pick(g2, forced2, both.actor, CampaignIds.attackerPlan,
      denizen(outriders))
    val left = optionsOf(parked(Vector(plan, second), both, taken))
    assertEquals(left.map(_.ref).toSet,
      Set(denizen(watchdog), CampaignIds.finish))
