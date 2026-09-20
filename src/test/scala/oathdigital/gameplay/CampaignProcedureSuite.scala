package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{Board, board, rules, withEnemyAtOrigin}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** Campaign through the rules, as a client drives it. */
class CampaignProcedureSuite extends munit.FunSuite {
  private val r = rules()

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
      WalkerPowers.empty).head
  }

  private def supply(state: OathState, id: PlayerId): Int =
    ready(state).game.current.players.find(_.player == id).get
      .board.supply.supply

  test("with one legal kind, no extra target and force to commit, the start parks on the force") {
    val b = board()
    val started = start(b).getOrElse(fail("Campaign must start"))
    assertEquals(started.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.force)))
    assertEquals(ready(started.state).game.current.pending, None)
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseAmount(0, 5,
      Some("Commit warbands to the Campaign: 0 to 5, each adds one attack die"),
      "Commit force"))
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
    assert(done.events.exists(_.isInstanceOf[WalkerCompleted]))
  }

  test("zero force is legal and gathers no attack pool") {
    val b = board(warbands = 0)
    val started = start(b).toOption.get
    assertEquals(parkedDecision(b, started).query, DecisionQuery.ChooseAmount(0, 0,
      Some("Commit warbands to the Campaign: 0 to 0, each adds one attack die"),
      "Commit force"))
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
}
