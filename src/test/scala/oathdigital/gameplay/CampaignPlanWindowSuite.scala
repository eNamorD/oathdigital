package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlanApplication, CampaignProcedure}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, Transform}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.campaign.{BattlePlan, PlanContext, PlanUse}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerDice, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** The plan window itself, with plans of the suite's own: what a plan may cost,
  * who pays and when, which plans are offered after each pick, and how a plan
  * that asks a question of its own is resumed. The ported plans are covered by
  * `CampaignPlansSuite` and `CampaignProcedureSuite`.
  */
class CampaignPlanWindowSuite extends munit.FunSuite {
  private val orderCard = cardWith("denizen.outriders")
  private val hearthCard = cardWith("denizen.watchdog")

  /** A plan that costs and does what the test says, from a denizen of its user. */
  private final case class Plan(name: String, card: String,
      planSides: Set[CampaignPlanSide],
      costs: Vector[CampaignPlanCost] = Vector.empty,
      effects: Vector[CampaignPlanEffect] = Vector.empty,
      afterwards: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty)
      extends BattlePlan {
    def id: PowerId = PowerId(s"test.plan.$name")
    def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
    def sides: Set[CampaignPlanSide] = planSides
    def plan(context: PlanContext): Option[CampaignPlanOffer] =
      context.denizen(DenizenId(card)).map(CampaignPlanOffer(_, name, costs,
        effects))
    override def later: Map[PowerWindow, PlanUse => Vector[Operation]] =
      afterwards
  }

  /** Every attack die a sword and every defense die blank, whatever the pool. */
  private val swords: WalkerDice = (kind, count) => Right(kind match {
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
    case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace)
  })

  private val attacker = Set[CampaignPlanSide](CampaignPlanSide.Attacker)
  private val defender = Set[CampaignPlanSide](CampaignPlanSide.Defender)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  private def player(state: OathState, id: PlayerId): PlayerState =
    ready(state).game.current.players.find(_.player == id).get

  private def adviserTokens(state: OathState, id: PlayerId, card: String)
      : Tokens = player(state, id).advisers.collectFirst {
    case held: DenizenState if held.id.value == card => held.tokens
  }.get

  private def with_(b: Board, id: PlayerId)(f: PlayerBoardState => PlayerBoardState)
      : Board = replacePlayer(b, id)(p => p.copy(board = f(p.board)))

  private def committed(g: OathRules, b: Board, force: Int = 2)
      : (OathTransition, OathTransition) = {
    val started = g.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .getOrElse(fail("Campaign must start"))
    started -> g.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(force)).fold(e => fail(s"the force must be accepted: $e"), identity)
  }

  private def pick(g: OathRules, from: OathTransition, who: PlayerId, id: String,
      ref: DecisionOptionRef): OathTransition = g.resolveWalker(from.state, who,
    id, ChooseOneAnswer(ref)).fold(e => fail(s"the plan must be accepted: $e"), identity)

  private def parked(plans: Vector[ContributingPower], b: Board,
      from: OathTransition): Decide = {
    val current = ready(from.state)
    val tree = CampaignProcedure.rebuild(catalog, current, b.actor,
      Vector.empty).toOption.get
    ProcedureWalker.openDecisions(current, tree,
      current.game.current.walkerPending.get, WalkerPowers(plans)).head
  }

  private def optionsOf(decide: Decide): Vector[DecisionOption] =
    decide.query match {
      case DecisionQuery.ChooseOne(options, _) => options
      case other => fail(s"expected a choose-one, got $other")
    }

  private def awaits(who: PlayerId, id: String) =
    OathContinue.AwaitingCampaignDecision(who, DecisionId(id))

  private def denizen(card: String): DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(card))
  private val finishOption =
    DecisionOption.Button(CampaignIds.finish, "Finish battle plans")

  // ---- costs --------------------------------------------------------------

  test("a favor cost is placed onto a card that already holds resources, and the option states the price") {
    val base = board()
    val b = with_(withAdviserFor(base, base.actor, orderCard,
      Orientation.FaceUp, Tokens(1, 0)), base.actor)(_.copy(favor = 2))
    val plan = Plan("pay", orderCard, attacker, Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddAttackDice(3)))
    val g = rulesWith(Vector(plan))
    val (started, forced) = committed(g, b)
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.attackerPlan))
    assertEquals(optionsOf(parked(Vector(plan), b, forced)), Vector(
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), "Attack Plan"),
        OptionPrice(favor = 1)),
      finishOption))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(adviserTokens(picked.state, b.actor, orderCard), Tokens(2, 0))
    assertEquals(player(picked.state, b.actor).board.favor, 1)
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(DenizenId(orderCard)), Cost(favor = 1), intoOccupied = true,
      matchingBank = catalog.suitOf(DenizenId(orderCard)))))
    // The recorded steps replay to the same state, and survive the journal wire.
    val events = started.events ++ forced.events ++ picked.events
    assertEquals(PaidActionHarness.replayed(g, b.ready, events),
      ready(picked.state))
    assert(PaidActionHarness.wireRoundTrips(events))
  }

  test("burnt costs leave play to the shared bank, and the option states them apart") {
    val base = board()
    val b = with_(withAdviserFor(base, base.actor, orderCard,
      Orientation.FaceUp), base.actor)(_.copy(favor = 1, faceUpSecrets = 1))
    val plan = Plan("burn", orderCard, attacker, Vector(
      CampaignPlanCost.FavorBurnt(1), CampaignPlanCost.SecretBurnt(1)),
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b)
    assertEquals(optionsOf(parked(Vector(plan), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), "Attack Plan"),
        OptionPrice(favorBurnt = 1, secretsBurnt = 1)))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    val after = player(picked.state, b.actor)
    assertEquals((after.board.favor, after.board.faceUpSecrets), (0, 0))
    assertEquals(adviserTokens(picked.state, b.actor, orderCard), Tokens.empty)
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(DenizenId(orderCard)), Cost(favorBurnt = 1, secretBurnt = 1),
      intoOccupied = true, matchingBank = catalog.suitOf(DenizenId(orderCard)))))
  }

  test("a plan the user cannot pay is not offered, and a window with nothing to offer is skipped") {
    val base = board()
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("poor", orderCard, attacker, Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddAttackDice(3)))
    val (_, forced) = committed(rulesWith(Vector(plan)), with_(b, b.actor)(
      _.copy(favor = 0)))
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  test("the options are rebuilt after each pick: a second plan the payment made unaffordable is gone") {
    val base = board()
    val b = with_(withAdviserFor(withAdviserFor(base, base.actor, orderCard,
      Orientation.FaceUp), base.actor, hearthCard, Orientation.FaceUp),
      base.actor)(_.copy(faceUpSecrets = 1))
    val first = Plan("first", orderCard, attacker,
      Vector(CampaignPlanCost.Secret(1)), Vector(CampaignPlanEffect.AddAttackDice(1)))
    val second = Plan("second", hearthCard, attacker,
      Vector(CampaignPlanCost.Secret(1)), Vector(CampaignPlanEffect.AddAttackDice(1)))
    val plans = Vector[ContributingPower](first, second)
    val g = rulesWith(plans)
    val (_, forced) = committed(g, b)
    assertEquals(optionsOf(parked(plans, b, forced)).size, 3)
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(picked.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  test("a defender's plan is paid at once: favor goes to the card's suit bank and a secret turns facedown") {
    val base = againstPlayer(board())
    val b = with_(withAdviserFor(base, base.other, orderCard, Orientation.FaceUp),
      base.other)(_.copy(favor = 2, faceUpSecrets = 1, faceDownSecrets = 0))
    val plan = Plan("guard", orderCard, defender, Vector(
      CampaignPlanCost.Favor(1), CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val g = rulesWith(Vector(plan))
    val (started, forced) = committed(g, b)
    assertEquals(forced.continue, awaits(b.other, CampaignIds.defenderPlan))
    assertEquals(optionsOf(parked(Vector(plan), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), "Defense Plan"),
        OptionPrice(favor = 1, secrets = 1)))
    val suit = catalog.suitOf(DenizenId(orderCard)).get
    val bank = ready(forced.state).banks.favor.getOrElse(suit, 0)
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assertEquals(adviserTokens(picked.state, b.other, orderCard), Tokens.empty)
    assertEquals(ready(picked.state).banks.favor.getOrElse(suit, 0), bank + 1)
    val after = player(picked.state, b.other).board
    assertEquals((after.favor, after.faceUpSecrets, after.faceDownSecrets),
      (1, 0, 1))
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // The recorded payment is the requested one, and replay settles it again.
    assertEquals(PaidActionHarness.replayed(g, b.ready,
      started.events ++ forced.events ++ picked.events), ready(picked.state))
  }

  test("a defender plan with a cost the defender cannot pay is not offered") {
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, orderCard, Orientation.FaceUp)
    val plan = Plan("guard", orderCard, defender,
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val (_, forced) = committed(rulesWith(Vector(plan)), b)
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  // ---- a warband sacrifice ------------------------------------------------

  private def defenderHolding(b: Board, warbands: Int): Board = {
    val against = withEnemyAtOrigin(againstPlayer(b))
    with_(withAdviserFor(against, against.other, orderCard, Orientation.FaceUp),
      against.other)(_.copy(warbands = warbands))
  }

  private val sacrificing = Plan("sacrifice", orderCard, defender,
    Vector(CampaignPlanCost.SacrificeWarband),
    Vector(CampaignPlanEffect.AddDefenseDice(1)))

  test("a Raid defender sacrifices a warband from the board") {
    val b = defenderHolding(board(warbands = 4), 3)
    val g = rulesWith(Vector(sacrificing))
    val started = g.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = g.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(DecisionOptionRef.Button("raid"))).toOption.get
    val forced = g.resolveWalker(kind.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    assertEquals(forced.continue, awaits(b.other, CampaignIds.defenderPlan))
    assertEquals(optionsOf(parked(Vector(sacrificing), b, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), "Defense Plan"),
        OptionPrice(warbands = 1)))
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assertEquals(player(picked.state, b.other).board.warbands, 2)
    assert(ops(picked.events).exists(_.isInstanceOf[Sacrifice]))
  }

  test("a Raid defender with no warband on the board cannot pay the sacrifice, so the plan is not offered") {
    val b = defenderHolding(board(warbands = 4), 0)
    val g = rulesWith(Vector(sacrificing))
    val started = g.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = g.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(DecisionOptionRef.Button("raid"))).toOption.get
    val forced = g.resolveWalker(kind.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    assertEquals(forced.continue, awaits(b.actor, CampaignIds.sacrifice))
  }

  test("a Conquest defender sacrifices from the one target site it rules") {
    val b = withAdviserFor(againstPlayer(board()), againstPlayer(board()).other,
      orderCard, Orientation.FaceUp)
    val g = rulesWith(Vector(sacrificing))
    val (_, forced) = committed(g, b)
    assertEquals(forced.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assertEquals(ready(picked.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), 1))
  }

  test("with several target sites the defender chooses which pays, and the walk resumes inside the plan") {
    val two = againstPlayer(board(extras = 1))
    val extra = two.extras.head
    val lineage = two.player(two.other).lineage
    val staged = withAdviserFor(two.copy(ready = two.ready.updateCurrent(
      current => current.copy(map = current.map.copy(sites =
        current.map.sites.updated(extra, current.map.sites(extra).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(lineage), 2))))))), two.other,
      orderCard, Orientation.FaceUp)
    val g = rulesWith(Vector(sacrificing))
    val started = g.startWalker(Ready(staged.ready), ActionRef.Campaign,
      staged.actor).toOption.get
    val targeted = g.resolveWalker(started.state, staged.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(extra)))).toOption.get
    val forced = g.resolveWalker(targeted.state, staged.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    val picked = pick(g, forced, staged.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    // The plan asks which force pays before it kills anything.
    assertEquals(picked.continue, awaits(staged.other, CampaignIds.planSacrifice))
    assertEquals(ready(picked.state).game.current.map.sites(extra).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    val paid = g.resolveWalker(picked.state, staged.other,
      CampaignIds.planSacrifice, ChooseOneAnswer(DecisionOptionRef.Site(extra)))
      .getOrElse(fail("the site must be accepted"))
    assertEquals(ready(paid.state).game.current.map.sites(extra).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    assertEquals(ready(paid.state).game.current.map.sites(staged.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    assert(ops(paid.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // Nothing else is offered, so the window ends and the attacker sacrifices.
    assertEquals(paid.continue, awaits(staged.actor, CampaignIds.sacrifice))
  }

  // ---- effects ------------------------------------------------------------

  test("a defender plan that removes attack dice takes what the pool holds, and none from an empty pool") {
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, orderCard, Orientation.FaceUp)
    val plan = Plan("remove", orderCard, defender, effects =
      Vector(CampaignPlanEffect.RemoveAttackDice(3)))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b, force = 2)
    val picked = pick(g, forced, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val (_, none) = committed(g, b, force = 0)
    val nothing = pick(g, none, b.other, CampaignIds.defenderPlan,
      denizen(orderCard))
    assert(!ops(nothing.events).exists(_.isInstanceOf[ModifyDicePool]))
  }

  test("a plan's own operations run after its payment") {
    val b = withAdviserFor(board(supply = 4), board().actor, orderCard,
      Orientation.FaceUp)
    val plan = Plan("run", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.Run(Vector(GainSupply(b.actor, 1)))))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b)
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(player(picked.state, b.actor).board.supply.supply, 3)
  }

  // ---- other powers change what a plan costs ------------------------------

  /** Every attacker plan costs one more secret, placed on its card. */
  private final case class Surcharge() extends ContributingPower {
    def id: PowerId = PowerId("test.surcharge")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignPlanApplication -> Vector[Contribution](
        Transform((ctx, children) => ctx.operation match {
          case application: CampaignPlanApplication
              if application.side == CampaignPlanSide.Attacker =>
            application.source match {
              case CampaignPlanSource.Adviser(user, card) => children :+
                BuildOps((_, _) => Right(Vector[CoreOperation](Costs.onCard(user,
                  card, Cost(secret = 1), catalog, intoOccupied = true))))
              case _ => children
            }
          case _ => children
        })))
  }

  test("a power that adds to a plan's cost changes what is offered, and the option's price") {
    val base = board()
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("free", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val plain = Vector[ContributingPower](plan)
    val taxed = Vector[ContributingPower](plan, Surcharge())
    // Without the surcharge the free plan is offered, free.
    val (_, freely) = committed(rulesWith(plain), with_(b, b.actor)(
      _.copy(faceUpSecrets = 0)))
    assertEquals(optionsOf(parked(plain, b, freely)).head, DecisionOption.Badged(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(DenizenId(orderCard))),
      "Attack Plan"))
    // With it, and no secret to pay, it is not offered at all.
    val (_, broke) = committed(rulesWith(taxed), with_(b, b.actor)(
      _.copy(faceUpSecrets = 0)))
    assertEquals(broke.continue, awaits(b.actor, CampaignIds.sacrifice))
    // With a secret it is offered, priced, and choosing it pays the secret.
    val rich = with_(b, b.actor)(_.copy(faceUpSecrets = 1))
    val g = rulesWith(taxed)
    val (_, forced) = committed(g, rich)
    assertEquals(optionsOf(parked(taxed, rich, forced)).head,
      DecisionOption.Priced(DecisionOption.Badged(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(DenizenId(orderCard))), "Attack Plan"),
        OptionPrice(secrets = 1)))
    val picked = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assertEquals(adviserTokens(picked.state, b.actor, orderCard), Tokens(0, 1))
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)
  }

  // ---- later windows ------------------------------------------------------

  test("what a used plan adds at a later window runs only when the plan was chosen") {
    val base = board(supply = 4)
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("later", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)), afterwards = Map(
        PowerWindow.CampaignActionEligibility -> (use =>
          Vector[Operation](GainSupply(use.actor, 1)))))
    val plans = Vector[ContributingPower](plan)
    def finished(chooses: Boolean): Int = {
      val g = rulesWith(plans, swords)
      val (_, forced) = committed(g, b, force = 4)
      val next =
        if (chooses) pick(g, forced, b.actor, CampaignIds.attackerPlan,
          denizen(orderCard))
        else pick(g, forced, b.actor, CampaignIds.attackerPlan,
          CampaignIds.finish)
      val sacrificed = g.resolveWalker(next.state, b.actor, CampaignIds.sacrifice,
        ChooseAmountAnswer(0)).getOrElse(fail("no sacrifice"))
      val placed = g.resolveWalker(sacrificed.state, b.actor,
        CampaignIds.placement, ChooseAmountAnswer(0)).getOrElse(fail("no placement"))
      player(placed.state, b.actor).board.supply.supply
    }
    assertEquals(finished(chooses = true), 3)
    assertEquals(finished(chooses = false), 2)
  }

  // ---- a bandit defender --------------------------------------------------

  test("a bandit defender applies every cost-free plan of a site it rules, and none that costs") {
    val two = board(extras = 1)
    val staged = withSiteCard(withSiteCard(two, two.origin, orderCard),
      two.extras.head, hearthCard)
    val free = Plan("free", orderCard, defender, effects =
      Vector(CampaignPlanEffect.AddDefenseDice(1)))
    val costly = Plan("costly", hearthCard, defender,
      Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(5)))
    val g = rulesWith(Vector(free, costly))
    val funded = with_(staged, staged.actor)(_.copy(favor = 3))
    val started = g.startWalker(Ready(funded.ready), ActionRef.Campaign,
      funded.actor).toOption.get
    val targeted = g.resolveWalker(started.state, funded.actor,
      CampaignIds.targets, ChooseManyAnswer(Vector.empty)).toOption.get
    val forced = g.resolveWalker(targeted.state, funded.actor, CampaignIds.force,
      ChooseAmountAnswer(2)).toOption.get
    val pools = ops(forced.events).collect {
      case pool @ ModifyDicePool(CampaignIds.defensePool, _, _) => pool }
    assertEquals(pools.filter(_.delta == 1).size, 1)
    assert(!pools.exists(_.delta == 5))
    assert(!ops(forced.events).exists(_.isInstanceOf[PayCost]))
    assertEquals(forced.continue, awaits(staged.actor, CampaignIds.sacrifice))
  }

  test("Finish ends the window and a chosen source is not offered again") {
    val base = board()
    val b = withAdviserFor(base, base.actor, orderCard, Orientation.FaceUp)
    val plan = Plan("once", orderCard, attacker, effects =
      Vector(CampaignPlanEffect.AddAttackDice(1)))
    val g = rulesWith(Vector(plan))
    val (_, forced) = committed(g, b)
    val finished = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      CampaignIds.finish)
    assertEquals(finished.continue, awaits(b.actor, CampaignIds.sacrifice))
    assert(!ops(finished.events).exists(_.isInstanceOf[ModifyDicePool]))
    val taken = pick(g, forced, b.actor, CampaignIds.attackerPlan,
      denizen(orderCard))
    assert(g.resolveWalker(taken.state, b.actor, CampaignIds.attackerPlan,
      ChooseOneAnswer(denizen(orderCard))).isLeft)
  }
}
