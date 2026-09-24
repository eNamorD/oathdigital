package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{Board, actorRules, againstPlayer, board, cardWith, relicWith, rules, withAdviser, withEnemyAtOrigin, withRelic, withSecrets, withSiteCard}
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, RollPayload,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** Campaign through the rules, as a client drives it. */
class CampaignProcedureSuite extends munit.FunSuite {
  private val r = rules(CampaignFixture.anyDice)

  private def start(b: Board) =
    r.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)

  private def answer(state: OathState, actor: PlayerId, id: String,
      answer: DecisionAnswer) = r.resolveWalker(state, actor, id, answer)

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def button(key: String) =
    ChooseOneAnswer(DecisionOptionRef.Button(key))

  private def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  private def parkedDecision(b: Board, transition: OathTransition): Decide = {
    val pending = ready(transition.state).game.current.walkerPending.get
    val tree = CampaignProcedure.rebuild(catalog, ready(transition.state),
      b.actor, Vector.empty).toOption.get
    ProcedureWalker.openDecisions(ready(transition.state), tree, pending,
      WalkerPowerCatalog.default(catalog)).head
  }

  private def supply(state: OathState, id: PlayerId): Int =
    ready(state).game.current.players.find(_.player == id).get
      .board.supply.supply

  test("with one legal kind, no extra target and force to commit, the start parks on the force") {
    val b = board()
    val started = start(b).getOrElse(fail("Campaign must start"))
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.force)))
    // Committing everything is what an attacker almost always wants, so the
    // question suggests the whole force and lets them dial it down.
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseAmount(0, 5,
      Some("Commit warbands to the Campaign: 0 to 5, each adds one attack die"),
      "Commit force", suggested = Some(5)))
    assertEquals(supply(started.state, b.actor), 5)
  }

  test("extra same-ruler sites are offered as optional targets, in map order") {
    val b = board(extras = 2)
    val started = start(b).getOrElse(fail("Campaign must start"))
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.targets)))
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseMany(0, 2,
      b.extras.map(site => DecisionOption.Site(DecisionOptionRef.Site(site))),
      Some("Also target these sites ruled by the same defender")))
    val chosen = answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.last)))).toOption.get
    assertEquals(chosen.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.force)))
  }

  test("the empty selection is a valid targets answer") {
    val b = board(extras = 1)
    val started = start(b).toOption.get
    assert(answer(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector.empty)).isRight)
  }

  test("a pawn shared with an enemy offers both kinds; a lone enemy is the Raid defender") {
    val shared = withEnemyAtOrigin(board())
    val b = shared.copy(ready = shared.ready.updateCurrent(current =>
      current.copy(players = current.players.map(p =>
        if (p.player == shared.other) p.copy(relics = Vector(RelicState(
          RelicId("r-raid"), Orientation.FaceUp, Tokens.empty))) else p))))
    val started = start(b).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.kind)))
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("conquest"), "Conquest"),
      DecisionOption.Button(DecisionOptionRef.Button("raid"), "Raid")),
      Some("Choose a Campaign")))
    val raid = answer(started.state, b.actor, CampaignIds.kind, button("raid"))
      .getOrElse(fail("the kind must be accepted"))
    // One enemy pawn: the defender is implied, so the Raid targets come next.
    assertEquals(raid.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.targets)))
  }

  test("with neither a ruled pawn site nor an enemy pawn, no Campaign starts or is offered") {
    val b = board()
    val unruled = b.ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(b.origin,
        current.map.sites(b.origin).copy(forces = SiteForces.Empty)))))
    val stuck = b.copy(ready = unruled)
    assert(start(stuck).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    assert(!CampaignProcedure.startable(catalog, stuck.ready, stuck.actor,
      WalkerPowers.empty))
    assert(CampaignProcedure.startable(catalog, b.ready, b.actor, WalkerPowers.empty))
  }

  test("a Campaign the actor cannot pay for is not offered and does not start") {
    val b = board(supply = 1)
    assert(start(b).isLeft)
    assert(!CampaignProcedure.startable(catalog, b.ready, b.actor,
      WalkerPowers.empty))
  }

  test("committing force gathers the attack pool and the printed defense pool") {
    val b = board()
    val started = start(b).toOption.get
    val done = answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(3)).getOrElse(fail("the force must be accepted"))
    val printed = catalog.sites.find(_.id == b.origin).get.defense
    val gathered = ops(done.events).collect { case pool: ModifyDicePool => pool }
    assertEquals(gathered, Vector(ModifyDicePool(CampaignIds.attackPool, 3)) ++
      Option.when(printed > 0)(ModifyDicePool(CampaignIds.defensePool, printed)))
    // The rolls are automatic, so the walk goes on to the sacrifice.
    assertEquals(done.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.sacrifice)))
  }

  test("zero force is legal and gathers no attack pool") {
    val b = board(warbands = 0)
    val started = start(b).toOption.get
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseAmount(0, 0,
      Some("Commit warbands to the Campaign: 0 to 0, each adds one attack die"),
      "Commit force", suggested = Some(0)))
    val done = answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(0)).toOption.get
    assert(!ops(done.events).exists {
      case ModifyDicePool(pool, _, _) => pool == CampaignIds.attackPool
      case _ => false
    })
  }

  test("more force than the board holds is rejected") {
    val b = board(warbands = 2)
    val started = start(b).toOption.get
    assert(answer(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(3)).isLeft)
  }

  test("no first-game gate: an altered Foundation or a Citizen still campaigns") {
    val b = board()
    val campaign = b.ready.game.campaign
    val lineage = campaign.lineages(b.player(b.actor).lineage)
    // A Citizen's warbands are Imperial, so the bank must define that supply.
    val citizen = b.ready.copy(
      game = b.ready.game.copy(campaign = campaign.copy(lineages =
        campaign.lineages.updated(lineage.id, lineage.copy(role = Role.Citizen)))),
      banks = b.ready.banks.copy(warbandSupply =
        b.ready.banks.warbandSupply.updated(ForceKind.Imperial, 15)))
    assert(start(b.copy(ready = citizen)).isRight)
    val altered = b.ready.copy(game = b.ready.game.copy(campaign =
      campaign.copy(foundations = campaign.foundations.map { case (k, f) =>
        k -> f.copy(face = FoundationFace.Altered) })))
    assert(start(b.copy(ready = altered)).isRight)
  }

  test("a held Campaign power the engine does not run does not block the start") {
    val b = board()
    val bag = catalog.relics.find(_.handlers.contains("relic.bag-of-siegeworks")).get
    val held = RelicId(bag.id.value)
    // The relic leaves the deck and any site, so the card index stays valid.
    val holding = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if (p.player == b.actor) p.copy(
        relics = Vector(RelicState(held, Orientation.FaceUp, Tokens.empty)))
      else p),
      commonCards = current.commonCards.copy(relicDeck =
        current.commonCards.relicDeck.filterNot(_ == held)),
      map = current.map.copy(sites = current.map.sites.map { case (id, site) =>
        id -> site.copy(relics = site.relics.filterNot(_.id == held)) })))
    // What gets recorded is whatever the reviewed catalog lists for the
    // Campaign modifier window as an unimplemented automatic rule; Bag of
    // Siegeworks is a player-selected plan, which the catalog does not list.
    assert(start(b.copy(ready = holding)).isRight)
  }

  test("a faceup Vow of Peace stops the start, through the walker power catalog") {
    val b = board()
    val vow = catalog.denizens.find(_.handlers.contains("denizen.vow-of-peace")).get
    val holding = b.ready.updateCurrent(current => current.copy(players =
      current.players.map(p => if (p.player == b.actor) p.copy(advisers = Vector(
        DenizenState(DenizenId(vow.id.value), Orientation.FaceUp, Tokens.empty)))
      else p)))
    val withPowers = rules(powers = true)
    assertEquals(withPowers.startWalker(Ready(holding), ActionRef.Campaign, b.actor),
      Left(OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning")))
  }

  private val outriders = cardWith("denizen.outriders")
  private val brass = relicWith("relic.brass-army.campaign")
  private def planPick(ref: DecisionOptionRef) = ChooseOneAnswer(ref)
  private val finish = ChooseOneAnswer(CampaignIds.finish)

  private def atPlans(b: Board, force: Int = 2): OathTransition = {
    val started = start(b).toOption.get
    answer(started.state, b.actor, CampaignIds.force, ChooseAmountAnswer(force))
      .getOrElse(fail("the force must be accepted"))
  }

  test("an attacker plan is offered after the force, with Finish, and a pick applies its effects") {
    val b = withSecrets(withRelic(board(), brass), 2)
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Priced(
        DecisionOption.Badged(DecisionOption.Relic(DecisionOptionRef.Relic(
          RelicId(brass))), "Attack Plan"),
        OptionPrice(secrets = 1)),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val picked = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Relic(RelicId(brass)))).toOption.get
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assert(ops(picked.events).contains(PayCost(b.actor,
      Location.OnCard(RelicId(brass)), Cost(secret = 1), intoOccupied = true)))
    // Nothing else can be chosen, so the window finishes by itself and the
    // walk goes on to the sacrifice.
    assertEquals(picked.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.sacrifice)))
  }

  test("two plans are chosen one at a time, each source once, and Finish ends the window") {
    val b = withSecrets(withRelic(withAdviser(board(), outriders,
      Orientation.FaceUp), brass), 1)
    val plans = atPlans(b)
    val first = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assertEquals(first.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    assertEquals(parkedDecision(b, first).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Priced(
        DecisionOption.Badged(DecisionOption.Relic(DecisionOptionRef.Relic(
          RelicId(brass))), "Attack Plan"),
        OptionPrice(secrets = 1)),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Choose a battle plan, or finish")))
    val done = answer(first.state, b.actor, CampaignIds.attackerPlan, finish).toOption.get
    assertEquals(ops(done.events).collect { case pool: ModifyDicePool => pool }
      .filter(_.pool == CampaignIds.attackPool), Vector.empty)
  }

  test("a plan already chosen is rejected when chosen again") {
    val b = withSecrets(withRelic(withAdviser(board(), outriders,
      Orientation.FaceUp), brass), 1)
    val first = answer(atPlans(b).state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(answer(first.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).isLeft)
  }

  test("a facedown Outriders is revealed when chosen") {
    val b = withAdviser(board(), outriders, Orientation.FaceDown)
    val done = answer(atPlans(b).state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(ops(done.events).contains(Move(Piece.Card(DenizenId(outriders)),
      PositionedLocation(Location.PlayArea(b.actor)),
      PositionedLocation(Location.PlayArea(b.actor)),
      resultingOrientation = Some(Orientation.FaceUp))))
  }

  test("with no plan available the attacker window is skipped") {
    val b = board()
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.sacrifice)))
  }

  test("a player defender owns the defender window and the attacker cannot answer it") {
    val b = againstPlayer(board())
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.other,
      DecisionId(CampaignIds.defenderPlan)))
    assertEquals(parkedDecision(b, plans).query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Badged(DecisionOption.Button(DecisionOptionRef.Button("title"),
        "Oathkeeper title: add 1 defense die"), "Defense Plan"),
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans")),
      Some("Defender: choose a battle plan, or finish")))
    assert(answer(plans.state, b.actor, CampaignIds.defenderPlan, finish).isLeft)
    val picked = answer(plans.state, b.other, CampaignIds.defenderPlan,
      planPick(DecisionOptionRef.Button("title"))).toOption.get
    assert(ops(picked.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
  }

  test("a bandit defender applies its cost-free plans by itself") {
    val watchdog = cardWith("denizen.watchdog")
    val base = board()
    assert(base.ready.game.current.map.regionOf(base.origin).contains(Region.Cradle),
      "the fixture's origin must be in the Cradle for Watchdog")
    val b = withSiteCard(base, base.origin, watchdog)
    val done = atPlans(b)
    assert(ops(done.events).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    assertEquals(done.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.sacrifice)))
  }

  // ---- battle -----------------------------------------------------------
  private def printed(b: Board): Int =
    catalog.sites.find(_.id == b.origin).get.defense
  private def blanks(b: Board) = Vector.fill(printed(b))(DefenseDieFace.Blank)
  private def sword(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)

  private def committed(game: OathRules, b: Board, force: Int): OathTransition = {
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .getOrElse(fail("Campaign must start"))
    game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(force)).getOrElse(fail("the force must be accepted"))
  }

  private def result(state: OathState): CampaignResult =
    ready(state).game.current.lastCampaignResult.get
  private def site(state: OathState, id: SiteId): SiteForces =
    ready(state).game.current.map.sites(id).forces
  private def boardWarbands(state: OathState, id: PlayerId): Int =
    ready(state).game.current.players.find(_.player == id).get.board.warbands
  private def isAutomaticRoll(pool: PoolKey)(event: OathEvent): Boolean =
    event match {
      case step: WalkerStepRecorded => step.payload match {
        case RollPayload(`pool`, _, true) => true
        case _ => false
      }
      case _ => false
    }

  test("a Conquest victory rolls both dice by itself, records the result and places the survivors") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val start = committed(game, b, 4)
    assert(start.events.exists(isAutomaticRoll(CampaignIds.attackPool)))
    assertEquals(start.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.sacrifice)))
    val sacrificed = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).getOrElse(fail("the sacrifice must be accepted"))
    assertEquals(sacrificed.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.placement)))
    assertEquals(result(sacrificed.state), CampaignResult(b.actor,
      CampaignKind.Conquest, CampaignDefender.Bandits, Vector(b.origin),
      Vector.empty, force = 4, attackFaces = sword(4), attackScore = 4,
      skullLosses = 0, sacrificed = 0, defenseFaces = blanks(b), defenseScore = 2,
      attackerWins = true))
    // The board is untouched until the losses: the bandits are gone, the
    // committed force is still on the board, and nothing is placed yet.
    assertEquals(boardWarbands(sacrificed.state, b.actor), 5)
    val placed = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      ChooseAmountAnswer(3)).getOrElse(fail("the placement must be accepted"))
    val lineage = b.player(b.actor).lineage
    assertEquals(site(placed.state, b.origin),
      SiteForces.Occupied(ForceKind.Exile(lineage), 3))
    assertEquals(boardWarbands(placed.state, b.actor), 2)
    assertEquals(placed.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(ready(placed.state).game.current.walkerPending, None)
    assertEquals(ready(placed.state).game.current.rollPools, Map.empty[PoolKey, DicePoolState])
  }

  test("the recorded events replay to the same state as the live walk") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor)
      .toOption.get
    val forced = game.resolveWalker(started.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(4)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor,
      CampaignIds.sacrifice, ChooseAmountAnswer(0)).toOption.get
    val placed = game.resolveWalker(sacrificed.state, b.actor,
      CampaignIds.placement, ChooseAmountAnswer(3)).toOption.get
    val events = started.events ++ forced.events ++ sacrificed.events ++
      placed.events
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready))) {
      case (Right(state), event) => game.evolve(state, event)
      case (failure, _) => failure
    }
    assertEquals(replayed, Right(placed.state))
  }

  test("a defeat kills the skull and sacrifice losses and half the survivors, and the bandits stay") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val done = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    assertEquals(result(done.state).attackerWins, false)
    assertEquals(boardWarbands(done.state, b.actor), 4)
    assertEquals(site(done.state, b.origin), SiteForces.Occupied(ForceKind.Bandit, 2))
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
  }

  test("a sacrifice adds one attack per warband and can turn a defeat into a victory") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(2), blanks(b)))
    val start = committed(game, b, 2)
    val won = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1)).toOption.get
    assertEquals(result(won.state).sacrificed, 1)
    assertEquals(result(won.state).attackerWins, true)
    assertEquals(won.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.placement)))
  }

  test("the sacrifice decision is bounded by the force the skulls left, and shows the roll") {
    val b = board()
    val faces = Vector(AttackDieFace.TwoSwordsSkull, AttackDieFace.OneSword)
    val game = rules(CampaignFixture.dice(faces, blanks(b)))
    val start = committed(game, b, 2)
    assertEquals(parkedDecision(b, start).query, DecisionQuery.ChooseAmount(0, 1,
      Some(CampaignBattle.sacrificeHeading(1)), "Sacrifice"))
  }

  test("zero force asks no sacrifice, and the attacker loses with nothing to kill") {
    val b = board(warbands = 0)
    val game = rules(CampaignFixture.dice(Vector.empty, blanks(b)))
    val done = committed(game, b, 0)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(result(done.state).force, 0)
    assertEquals(result(done.state).attackerWins, false)
    assertEquals(boardWarbands(done.state, b.actor), 0)
  }

  test("a player defender keeps half the killed force, returned to its board") {
    val b = againstPlayer(board())
    val game = rules(CampaignFixture.dice(sword(4), blanks(b)))
    val before = boardWarbands(Ready(b.ready), b.other)
    val start = committed(game, b, 4)
    // The defender may choose plans first; finish that window.
    val afterPlans = game.resolveWalker(start.state, b.other,
      CampaignIds.defenderPlan, ChooseOneAnswer(CampaignIds.finish)).toOption.get
    val sacrificed = game.resolveWalker(afterPlans.state, b.actor,
      CampaignIds.sacrifice, ChooseAmountAnswer(0)).toOption.get
    assertEquals(result(sacrificed.state).defender, CampaignDefender.Player(b.other))
    assertEquals(boardWarbands(sacrificed.state, b.other), before + 1)
  }

  test("several targets are placed with one distribution, and the rest stay on the board") {
    val b = board(extras = 1)
    val game = rules(CampaignFixture.dice(sword(5),
      Vector.fill(printed(b) + catalog.sites.find(_.id == b.extras.head).get.defense)(
        DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val targeted = game.resolveWalker(started.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Site(b.extras.head)))).toOption.get
    val forced = game.resolveWalker(targeted.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(5)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    assertEquals(parkedDecision(b, sacrificed).query, DecisionQuery.Distribute(
      Vector(b.origin, b.extras.head).map(site => DistributeSlot(
        DecisionOptionRef.Site(site), 0, 5, None)), 0, 5,
      Some("Place up to 5 surviving warbands across the conquered sites; the " +
        "rest stay on your board"), "Place warbands"))
    val placed = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Site(b.origin), 2),
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 1)))).toOption.get
    val lineage = b.player(b.actor).lineage
    assertEquals(site(placed.state, b.origin),
      SiteForces.Occupied(ForceKind.Exile(lineage), 2))
    assertEquals(site(placed.state, b.extras.head),
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))
    assertEquals(boardWarbands(placed.state, b.actor), 2)
    // More than the survivors is rejected.
    assert(game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Site(b.origin), 4),
        DistributeAmount(DecisionOptionRef.Site(b.extras.head), 2)))).isLeft)
  }

  test("a site card is a plan only for the site's ruler, and is never revealed") {
    val outriders = cardWith("denizen.outriders")
    val two = board(extras = 1)
    val ruled = two.extras.head
    val b = withSiteCard(actorRules(two, ruled), ruled, outriders)
    val plans = atPlans(b)
    assertEquals(plans.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.attackerPlan)))
    val done = answer(plans.state, b.actor, CampaignIds.attackerPlan,
      planPick(DecisionOptionRef.Denizen(DenizenId(outriders)))).toOption.get
    assert(!ops(done.events).exists(_.isInstanceOf[Reveal]))
    // The same card at the origin, which the bandits rule, is not the attacker's.
    val atOrigin = withSiteCard(board(), board().origin, outriders)
    assertEquals(atPlans(atOrigin).continue, OathContinue
      .AwaitingCampaignDecision(atOrigin.actor, DecisionId(CampaignIds.sacrifice)))
  }

  test("a victory with nothing placed refills the bandits at the action boundary") {
    val b = board()
    val game = rules(CampaignFixture.dice(sword(3), blanks(b)))
    val start = committed(game, b, 3)
    val sacrificed = game.resolveWalker(start.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(0)).toOption.get
    val done = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.placement,
      ChooseAmountAnswer(0)).toOption.get
    assert(done.events.exists(_.isInstanceOf[OathEvent.BanditsRefilled]))
    val capacity = catalog.sites.find(_.id == b.origin).get.capacity
    assertEquals(ready(done.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Bandit, capacity))
    val refill = done.events.collectFirst {
      case event: OathEvent.BanditsRefilled => event }.get
    assert(game.evolve(sacrificed.state, refill.copy(sites =
      Vector(b.origin -> 99))).isLeft)
  }
}
