package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{Board, actorRules, board, on,
  rivalRulesOriginAndHoldsOathkeeper, rules, withAdviser, withEnemyAtOrigin,
  withRelic, withRelicFor, withSecrets, withSiteCard}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignProcedure, CampaignSetup}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  RollPayload, WalkerPowers, WalkerProcedureRegistry, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** Campaign through the rules, as a client drives it. */
class CampaignProcedureSuite extends munit.FunSuite:
  private val r = rules(WalkerDiceFixture.blanks)

  /** `r` is built with the default walker power catalog, so this reads the
    * park the same way.
    */
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private def start(b: Board) =
    r.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)

  private def answer(state: OathState, actor: PlayerId, id: String,
      answer: DecisionAnswer) = r.resolveWalker(state, actor, id, answer)

  private def ready(state: OathState): ReadyGame = Outcomes.ready(state)

  private def button(key: String) =
    ChooseOneAnswer(DecisionOptionRef.Button(key))

  private def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    PhasePowerHarness.ops(events)

  /** The attacker's `answer` to `id`, resolved by `game` from `from`; fails
    * with the violation. The attacker is every board's actor, p1. */
  private def resolved(game: OathRules, from: OathTransition, id: String,
      answer: DecisionAnswer): OathTransition =
    accepted(game.resolveWalker(from.state, Table.p1, id, answer))

  /** The parked `Decide`, for the tests that check its `query`. The
    * procedure comes from the shared module, not assumed, and the tree is
    * rebuilt the way that module rebuilds it -- no more hand-rolling
    * `CampaignProcedure.rebuild` directly.
    */
  private def parkedDecision(b: Board, transition: OathTransition): Decide =
    val current = ready(transition.state)
    val facts = present(parked.parkedDecision(transition.state),
      s"a park in $transition")
    val pending = present(current.game.current.walkerPending,
      "a parked walker decision")
    val tree = accepted(WalkerProcedureRegistry.rebuild(facts.procedure,
      catalog, current, b.actor, Vector.empty))
    ProcedureWalker.openDecisions(current, tree, pending,
      WalkerPowerCatalog.default(catalog)).head

  test("with one legal kind, no extra target and force to commit, the start " +
      "parks on the force"):
    val b = board(warbands = 5, supply = 7)
    val started = accepted(start(b))
    parked.assertParked(started.state, ActionRef.Campaign, CampaignIds.force,
      b.actor)
    // Committing everything is what an attacker almost always wants, so the
    // question suggests the whole force (p1's 5 warbands) and lets them dial
    // it down.
    assertEquals(parkedDecision(b, started).query,
      DecisionQuery.ChooseAmount(0, 5, Some("Commit warbands to the " +
        "Campaign: 0 to 5, each adds one attack die"), "Commit force",
        suggested = Some(5)))

  test("starting a Campaign spends 2 Supply"):
    val b = board(warbands = 5, supply = 7)
    val started = accepted(start(b))
    assertEquals(Look(started.state).supply(b.actor), 7 - 2)

  test("extra same-ruler sites are offered as optional targets, in map order"):
    val b = board(extras = 2, warbands = 5, supply = 7)
    val started = accepted(start(b))
    parked.assertParked(started.state, ActionRef.Campaign, CampaignIds.targets,
      b.actor)
    val query = as[DecisionQuery.ChooseMany](parkedDecision(b, started).query)
    assertEquals(query.options.filter(_.available),
      b.extras.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))))
    assertEquals((query.min, query.max, query.heading), (0, 2,
      Some("Also target these sites ruled by the same defender")))
    val chosen = accepted(answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.last)))))
    parked.assertParked(chosen.state, ActionRef.Campaign, CampaignIds.force,
      b.actor)

  test("the empty selection is a valid targets answer"):
    val b = board(extras = 1, warbands = 5, supply = 7)
    val started = accepted(start(b))
    val answered = accepted(answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector.empty)))
    parked.assertParked(answered.state, ActionRef.Campaign, CampaignIds.force,
      b.actor)

  test("a pawn shared with an enemy offers both kinds; a lone enemy is the " +
      "Raid defender"):
    val shared = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    // The rival holds a faceup relic, so a Raid has something to take.
    val b = withRelicFor(shared, shared.rival, "Cursed Cauldron")
    val started = accepted(start(b))
    parked.assertParked(started.state, ActionRef.Campaign, CampaignIds.kind,
      b.actor)
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseOne(
      Vector(
        DecisionOption.Button(DecisionOptionRef.Button("conquest"), "Conquest"),
        DecisionOption.Button(DecisionOptionRef.Button("raid"), "Raid")),
      Some("Choose a Campaign")))
    val raid = accepted(
      answer(started.state, b.actor, CampaignIds.kind, button("raid")))
    // One enemy pawn: the defender is implied, so the Raid targets come next.
    parked.assertParked(raid.state, ActionRef.Campaign, CampaignIds.targets,
      b.actor)

  test("with neither a ruled pawn site nor an enemy pawn, no Campaign starts " +
      "or is offered"):
    val b = board(warbands = 5, supply = 7)
    // The origin's bandits go, so nobody rules p1's site.
    val stuck = on(b)(_.bandits(b.origin, 0))
    assert(start(stuck).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    assert(!CampaignProcedure.startable(catalog, stuck.ready, stuck.actor,
      WalkerPowers.empty))
    assert(CampaignProcedure.startable(catalog, b.ready, b.actor,
      WalkerPowers.empty))

  test("a Campaign the actor cannot pay for is not offered and does not start"):
    val b = board(supply = 1, warbands = 5)
    assertEquals(start(b).left.toOption,
      Some(OathViolation.CoreOperationRejected("insufficient-supply",
        "a supply spend of 2 exceeds the 1 available")))
    assert(!CampaignProcedure.startable(catalog, b.ready, b.actor,
      WalkerPowers.empty))

  test("committing force pins the kind and defender and gathers the attack " +
      "pool and the printed defense pool"):
    val b = board(warbands = 5, supply = 7)
    val started = accepted(start(b))
    val done = accepted(answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(3)))
    val printed = catalog.sites.find(_.id == b.origin).get.defense
    val gathered = ops(done.events).collect {
      case pool: ModifyDicePool => pool }
    val defense = Option.when(printed > 0)(
      ModifyDicePool(CampaignIds.defensePool, printed))
    assertEquals(gathered, Vector(
      ModifyDicePool(PoolKey("campaign.setup.conquest.bandits"), 1),
      ModifyDicePool(CampaignIds.attackPool, 3)) ++ defense)
    assertEquals(CampaignSetup.pinned(ready(done.state)),
      Some(CampaignKind.Conquest -> CampaignDefender.Bandits))
    // The rolls are automatic, so the walk goes on to the sacrifice.
    parked.assertParked(done.state, ActionRef.Campaign, CampaignIds.sacrifice,
      b.actor)

  test("zero force is legal and gathers no attack pool"):
    val b = board(warbands = 0, supply = 7)
    val started = accepted(start(b))
    assertEquals(parkedDecision(b, started).query,
      DecisionQuery.ChooseAmount(0, 0, Some("Commit warbands to the " +
        "Campaign: 0 to 0, each adds one attack die"), "Commit force",
        suggested = Some(0)))
    val done = accepted(answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(0)))
    assert(!ops(done.events).exists {
      case ModifyDicePool(pool, _, _) => pool == CampaignIds.attackPool
      case _ => false
    })

  test("more force than the board holds is rejected"):
    val b = board(warbands = 2, supply = 7)
    val started = accepted(start(b))
    val tooMany = answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(3))
    assertEquals(tooMany.left.toOption, Some(OathViolation.InvalidEventOrder(
      "decision campaign.force amount 3 is outside 0..2")))

  test("a held battle-plan relic does not block the start: its plan is " +
      "chosen at the plan step"):
    val b = board(warbands = 5, supply = 7)
    val holding = withRelic(b, "Bag of Siegeworks")
    // Bag of Siegeworks is a battle plan, offered at the plan step, so
    // holding it changes nothing at the start.
    parked.assertParked(accepted(start(holding)).state,
      ActionRef.Campaign, CampaignIds.force, b.actor)

  test("a faceup Vow of Peace stops the start, through the walker power " +
      "catalog"):
    val b = board(warbands = 5, supply = 7)
    val holding = withAdviser(b, "Vow of Peace", Orientation.FaceUp)
    val withPowers = rules()
    assertEquals(
      withPowers.startWalker(Ready(holding.ready), ActionRef.Campaign, b.actor),
      Left(OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning")))

  private val outriders = CatalogNames.denizen("Outriders")
  private val brass = CatalogNames.relic("Brass Army")
  private def planPick(ref: DecisionOptionRef) = ChooseOneAnswer(ref)
  private val finish = ChooseOneAnswer(CampaignIds.finish)

  private def atPlans(b: Board, force: Int = 2): OathTransition =
    val started = accepted(start(b))
    accepted(answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(force)))

  test("an attacker plan is offered after the force, with Finish, and a pick " +
      "applies its effects"):
    val b = withSecrets(withRelic(board(warbands = 5, supply = 7), brass), 2)
    val plans = atPlans(b)
    parked.assertParked(plans.state, ActionRef.Campaign,
      CampaignIds.attackerPlan, b.actor)
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Priced(
        DecisionOption.Badged(DecisionOption.Relic(DecisionOptionRef.Relic(
          brass)), "Attack Plan"),
        OptionPrice(secrets = 1)),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val picked = accepted(answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Relic(brass))))
    assert(
      ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(brass), Cost(secret = 1), intoOccupied = true)))
    // Nothing else can be chosen, so the window finishes by itself and the
    // walk goes on to the sacrifice.
    parked.assertParked(picked.state, ActionRef.Campaign, CampaignIds.sacrifice,
      b.actor)

  test("two plans are chosen one at a time, each source once, and Finish " +
      "ends the window"):
    val advised = withAdviser(board(warbands = 5, supply = 7), outriders,
      Orientation.FaceUp)
    val b = withSecrets(withRelic(advised, brass), 1)
    val plans = atPlans(b)
    val first = accepted(answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(outriders))))
    parked.assertParked(first.state, ActionRef.Campaign,
      CampaignIds.attackerPlan, b.actor)
    assertEquals(parkedDecision(b, first).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Priced(
        DecisionOption.Badged(DecisionOption.Relic(DecisionOptionRef.Relic(
          brass)), "Attack Plan"),
        OptionPrice(secrets = 1)),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val done = accepted(
      answer(first.state, b.actor, CampaignIds.attackerPlan, finish))
    assertEquals(ops(done.events).collect { case pool: ModifyDicePool => pool }
      .filter(_.pool == CampaignIds.attackPool), Vector.empty)

  test("a plan already chosen is rejected when chosen again"):
    val advised = withAdviser(board(warbands = 5, supply = 7), outriders,
      Orientation.FaceUp)
    val b = withSecrets(withRelic(advised, brass), 1)
    val first = accepted(answer(atPlans(b).state, b.actor,
      CampaignIds.attackerPlan, planPick(DecisionOptionRef.Denizen(outriders))))
    assertEquals(answer(first.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(outriders))).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "decision campaign.attacker-plan does not offer the selected option")))

  test("a facedown Outriders is revealed when chosen"):
    val b = withAdviser(board(warbands = 5, supply = 7), outriders,
      Orientation.FaceDown)
    val done = accepted(answer(atPlans(b).state, b.actor,
      CampaignIds.attackerPlan, planPick(DecisionOptionRef.Denizen(outriders))))
    assert(ops(done.events).contains(Move(Piece.Card(outriders),
      PositionedLocation(Location.PlayArea(b.actor)),
      PositionedLocation(Location.PlayArea(b.actor)),
      resultingOrientation = Some(Orientation.FaceUp))))

  test("a player defender owns the defender window and the attacker cannot " +
      "answer it"):
    val b = rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7))
    val plans = atPlans(b)
    parked.assertParked(plans.state, ActionRef.Campaign,
      CampaignIds.defenderPlan, b.rival)
    val title = DecisionOption.Button(DecisionOptionRef.Button("title"),
      "Oathkeeper title: add 1 defense die")
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Badged(title, "Defense Plan"),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Defender: choose a battle plan, or finish")))
    assert(
      answer(plans.state, b.actor, CampaignIds.defenderPlan, finish).isLeft)
    val picked = accepted(answer(plans.state, b.rival, CampaignIds.defenderPlan,
      planPick(DecisionOptionRef.Button("title"))))
    assert(
      ops(picked.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))

  test("a bandit defender applies its cost-free plans by itself"):
    val watchdog = CatalogNames.denizen("Watchdog")
    val base = board(warbands = 5, supply = 7)
    assert(
      base.ready.game.current.map.regionOf(base.origin).contains(Region.Cradle),
      "the fixture's origin must be in the Cradle for Watchdog")
    val b = withSiteCard(base, base.origin, watchdog)
    val done = atPlans(b)
    assert(
      ops(done.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    parked.assertParked(done.state, ActionRef.Campaign, CampaignIds.sacrifice,
      b.actor)

  // ---- battle -----------------------------------------------------------
  private def printed(b: Board): Int =
    catalog.sites.find(_.id == b.origin).get.defense
  private def blanks(b: Board) = Vector.fill(printed(b))(DefenseDieFace.Blank)
  private def sword(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)

  private def committed(game: OathRules, b: Board, force: Int): OathTransition =
    val started = accepted(
      game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor))
    resolved(game, started, CampaignIds.force, ChooseAmountAnswer(force))

  private def result(state: OathState): CampaignResult =
    present(ready(state).game.current.lastCampaignResult, "a Campaign result")
  private def isAutomaticRoll(pool: PoolKey)(event: OathEvent): Boolean =
    event match
      case step: WalkerStepRecorded => step.payload match
        case RollPayload(`pool`, _, true) => true
        case _ => false
      case _ => false

  test("a Conquest victory rolls both dice by itself, records the result and " +
      "places the survivors"):
    val b = board(warbands = 5, supply = 7)
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val start = committed(game, b, 4)
    assert(start.events.exists(isAutomaticRoll(CampaignIds.attackPool)))
    parked.assertParked(start.state, ActionRef.Campaign, CampaignIds.sacrifice,
      b.actor)
    val sacrificed = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    parked.assertParked(sacrificed.state, ActionRef.Campaign,
      CampaignIds.placement, b.actor)
    assertEquals(result(sacrificed.state), CampaignResult(b.actor,
      CampaignKind.Conquest, CampaignDefender.Bandits, Vector(b.origin),
      Vector.empty, force = 4, attackFaces = sword(4), attackScore = 4,
      skullLosses = 0, sacrificed = 0, defenseFaces = blanks(b),
      defenseScore = 2, attackerWins = true))
    // The defense score is the origin's 2 bandits; the blank dice add nothing.
    // The board is untouched until the losses: the bandits are gone, the
    // committed force is still on p1's board of 5, and nothing is placed yet.
    assertEquals(Look(sacrificed.state).boardWarbands(b.actor), 5)
    val placed = resolved(game, sacrificed, CampaignIds.placement,
      ChooseAmountAnswer(3))
    val lineage = b.player(b.actor).lineage
    assertEquals(Look(placed.state).forces(b.origin),
      SiteForces.Occupied(ForceKind.Exile(lineage), 3))
    assertEquals(Look(placed.state).boardWarbands(b.actor), 2)
    parked.assertResumed(placed.state, Phase.Act, b.actor)
    assertEquals(ready(placed.state).game.current.walkerPending, None)
    assertEquals(ready(placed.state).game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])

  test("the recorded events replay to the same state as the live walk"):
    val b = board(warbands = 5, supply = 7)
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val started = accepted(
      game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor))
    val forced = resolved(game, started, CampaignIds.force,
      ChooseAmountAnswer(4))
    val sacrificed = resolved(game, forced, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    val placed = resolved(game, sacrificed, CampaignIds.placement,
      ChooseAmountAnswer(3))
    val events = started.events ++ forced.events ++ sacrificed.events ++
      placed.events
    assertEquals(ActPowerHarness.replayed(game, b.ready, events),
      ready(placed.state))

  test("a defeat with no skull and no sacrifice kills half the survivors, " +
      "and the bandits stay"):
    val b = board(warbands = 5, supply = 7)
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val done = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    assertEquals(result(done.state).attackerWins, false)
    assertEquals(Look(done.state).boardWarbands(b.actor), 4)
    assertEquals(Look(done.state).forces(b.origin),
      SiteForces.Occupied(ForceKind.Bandit, 2))
    parked.assertResumed(done.state, Phase.Act, b.actor)

  test("a sacrifice adds one attack per warband and can turn a defeat into a " +
      "victory"):
    val b = board(warbands = 5, supply = 7)
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val won = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(result(won.state).sacrificed, 1)
    assertEquals(result(won.state).attackerWins, true)
    parked.assertParked(won.state, ActionRef.Campaign, CampaignIds.placement,
      b.actor)

  test("the sacrifice decision is bounded by the force the skulls left, and " +
      "shows the roll"):
    val b = board(warbands = 5, supply = 7)
    val faces = Vector(AttackDieFace.TwoSwordsSkull, AttackDieFace.OneSword)
    val game = rules(CampaignFixture.dice(faces, blanks(b)))
    val start = committed(game, b, 2)
    assertEquals(parkedDecision(b, start).query,
      DecisionQuery.ChooseAmount(0, 1, Some(CampaignBattle.sacrificeHeading(1)),
        "Sacrifice"))

  test("zero force asks no sacrifice, and the attacker loses with nothing to " +
      "kill"):
    val b = board(warbands = 0, supply = 7)
    val game = rules(CampaignFixture.dice(Vector.empty, blanks(b)))
    val done = committed(game, b, 0)
    parked.assertResumed(done.state, Phase.Act, b.actor)
    assertEquals(result(done.state).force, 0)
    assertEquals(result(done.state).attackerWins, false)
    assertEquals(Look(done.state).boardWarbands(b.actor), 0)

  test("a player defender keeps half the killed force, returned to its board"):
    val b = rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7))
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val before = Look(b.ready).boardWarbands(b.rival)
    val start = committed(game, b, 4)
    // The defender may choose plans first; finish that window.
    val afterPlans = accepted(game.resolveWalker(start.state, b.rival,
      CampaignIds.defenderPlan, ChooseOneAnswer(CampaignIds.finish)))
    val sacrificed = resolved(game, afterPlans, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    assertEquals(result(sacrificed.state).defender,
      CampaignDefender.Player(b.rival))
    // The rival's 2 warbands at the origin are killed; half return.
    assertEquals(Look(sacrificed.state).boardWarbands(b.rival),
      before + (2 - 2 / 2))

  test("several targets are placed with one distribution, and the rest stay " +
      "on the board"):
    val b = board(extras = 1, warbands = 5, supply = 7)
    val extraDefense = catalog.sites.find(_.id == b.extras.head).get.defense
    val game = rules(CampaignFixture.dice(sword(5),
      Vector.fill(printed(b) + extraDefense)(DefenseDieFace.Blank)))
    val started = accepted(
      game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor))
    val targeted = resolved(game, started, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.head))))
    val forced = resolved(game, targeted, CampaignIds.force,
      ChooseAmountAnswer(5))
    val sacrificed = resolved(game, forced, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    assertEquals(parkedDecision(b, sacrificed).query, DecisionQuery.Distribute(
      Vector(b.origin, b.extras.head).map(site => DistributeSlot(
        DecisionOptionRef.Site(site), 0, 5, None)), 0, 5,
      Some("Place up to 5 surviving warbands across the conquered sites; the " +
        "rest stay on your board"), "Place warbands"))
    val placed = resolved(game, sacrificed, CampaignIds.placement,
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Site(b.origin), 2),
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 1))))
    val lineage = b.player(b.actor).lineage
    assertEquals(Look(placed.state).forces(b.origin),
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    assertEquals(Look(placed.state).forces(b.extras.head),
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    assertEquals(Look(placed.state).boardWarbands(b.actor), 2)
    // More than the survivors is rejected.
    assert(game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Site(b.origin), 4),
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 2)))).isLeft)

  test("a site card is a plan only for the site's ruler, and is never " +
      "revealed"):
    val two = board(extras = 1, warbands = 5, supply = 7)
    val ruled = two.extras.head
    val b = withSiteCard(actorRules(two, ruled), ruled, outriders)
    val plans = atPlans(b)
    parked.assertParked(plans.state, ActionRef.Campaign,
      CampaignIds.attackerPlan, b.actor)
    val done = accepted(answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(outriders))))
    assert(!ops(done.events).exists(_.isInstanceOf[Reveal]))
    // The same card at the origin, which the bandits rule, is not the
    // attacker's.
    val bare = board(warbands = 5, supply = 7)
    val atOrigin = withSiteCard(bare, bare.origin, outriders)
    parked.assertParked(atPlans(atOrigin).state, ActionRef.Campaign,
      CampaignIds.sacrifice, atOrigin.actor)

  test("a victory with nothing placed refills the bandits at the action " +
      "boundary"):
    val b = board(warbands = 5, supply = 7)
    val game = rules(CampaignFixture.dice(sword(3), blanks(b)))
    val start = committed(game, b, 3)
    val sacrificed = resolved(game, start, CampaignIds.sacrifice,
      ChooseAmountAnswer(0))
    val done = resolved(game, sacrificed, CampaignIds.placement,
      ChooseAmountAnswer(0))
    assert(done.events.exists(_.isInstanceOf[OathEvent.BanditsRefilled]))
    val capacity = catalog.sites.find(_.id == b.origin).get.capacity
    assertEquals(ready(done.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Bandit, capacity))
    val refill = done.events.collectFirst {
      case event: OathEvent.BanditsRefilled => event }.get
    assert(game.evolve(sacrificed.state, refill.copy(sites =
      Vector(b.origin -> 99))).isLeft)

  test("a Conquest's targets lock the origin and tally its defense and force"):
    val b = board(extras = 1, warbands = 5, supply = 7)
    val query = as[DecisionQuery.ChooseMany](
      parkedDecision(b, accepted(start(b))).query)
    val origin = DecisionOptionRef.Site(b.origin)
    val extra = DecisionOptionRef.Site(b.extras.head)
    def printed(site: SiteId) = catalog.site(site).fold(0)(_.defense)
    assertEquals(query.locked, Vector(DecisionOption.Site(origin)))
    assertEquals(query.summary, Some(DecisionSummary("Target", "site",
      "sites")))
    // Two bandits stand at each ruled site.
    assertEquals(query.tallies, Vector(
      DecisionTally("Defense dice (before plans and powers)", "defense",
        Map(origin -> printed(b.origin), extra -> printed(b.extras.head))),
      DecisionTally("Defending force", "warbands",
        Map(origin -> 2, extra -> 2))))

  test("a Conquest shows every other site as not on offer, and why"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val inPlay = base.ready.game.current.map.inPlay
    val mine = inPlay.find(site => site != base.origin &&
      !base.extras.contains(site)).get
    val b = on(base)(_.warbandsAt(mine, base.actor, 1))
    val query = as[DecisionQuery.ChooseMany](
      parkedDecision(b, accepted(start(b))).query)
    val reasons = query.options.collect {
      case DecisionOption.Unavailable(DecisionOption.Site(ref),
          OptionReason.Written(text)) => ref.id -> text }.toMap
    assertEquals(reasons.keySet,
      inPlay.filterNot(site => site == b.origin || b.extras.contains(site))
        .toSet)
    assertEquals(reasons(mine), "You rule this site")
    assert(reasons.removed(mine).values.forall(_ == "Not ruled by the defender"))

  test("a Raid locks the defender's pawn, and shows what it may not take"):
    val shared = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val cauldron = CatalogNames.relic("Cursed Cauldron")
    val sticks = CatalogNames.relic("Dowsing Sticks")
    // The rival holds Cursed Cauldron faceup and three warbands; the
    // bystander holds Dowsing Sticks faceup; the actor holds the Darkest
    // Secret.
    val b = on(shared)(_.relic(shared.rival, cauldron)
      .relic(shared.bystander, sticks).boardWarbands(shared.rival, 3)
      .darkestSecret(Some(shared.actor), secrets = 1))
    val started = accepted(start(b))
    val raid = accepted(answer(started.state, b.actor, CampaignIds.kind,
      button("raid")))
    val query = as[DecisionQuery.ChooseMany](parkedDecision(b, raid).query)
    val pawn = DecisionOptionRef.Player(b.rival)
    val relic = DecisionOptionRef.Relic(cauldron)
    assertEquals(query.locked, Vector(DecisionOption.Player(pawn)))
    assertEquals(query.options.filter(_.available),
      Vector(DecisionOption.Relic(relic)))
    assertEquals(query.summary, Some(DecisionSummary("Raid", "target",
      "targets")))
    assertEquals(query.tallies.map(_.perOption), Vector(
      Map(pawn -> 2, relic -> catalog.relics.find(_.id.value == cauldron.value)
        .fold(0)(_.defense)),
      Map(pawn -> 3, relic -> 0)))
    assertEquals(query.options.collect {
      case DecisionOption.Unavailable(inner, OptionReason.Written(text)) =>
        inner.ref -> text }, Vector(
      DecisionOptionRef.Relic(sticks) -> "Not held by the defender",
      DecisionOptionRef.Banner(Banner.PeoplesFavor) ->
        "Not held by the defender",
      DecisionOptionRef.Banner(Banner.DarkestSecret) -> "You hold this banner"))
