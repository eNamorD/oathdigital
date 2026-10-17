package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.{FollowUpPlay, OathRules}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.FollowUpOffer
import oathdigital.model._
import oathdigital.model.DecisionAnswer.DistributeAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, ParkedNode}
import oathdigital.testkit.Unwrap.accepted

/** Sneak Attack (card 98): after another player's Campaign, its holder may
  * campaign against that player for no Supply, off turn, before the
  * Campaign's other follow-ups (catalog batch 8, slice 14, rulings 6 to
  * 9). */
class SneakAttackSuite extends munit.FunSuite:
  private val sneak = SneakAttackCard.id
  private val source = PowerSourceRef.Card(sneak)
  private val secondWind = CatalogNames.denizen("Second Wind")

  /** p1 at the bandit-ruled origin with the rival, who holds Sneak Attack
    * faceup. */
  private def rivalHoldsSneakAttackAtTheOrigin: Board =
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    withAdviserFor(b, b.rival, sneak, Orientation.FaceUp)

  /** The offer `holder` is owed against `attacker`. */
  private def sneakOffer(holder: PlayerId, attacker: PlayerId)
      : QueuedProcedure =
    QueuedProcedure(holder, ActionRef.Campaign,
      Vector(DecisionOptionRef.Player(attacker)), Some(source),
      QueueKind.Offer)

  private def p1SecondWindOffer(b: Board, action: ActionRef): QueuedProcedure =
    QueuedProcedure(b.actor, action, Vector.empty,
      Some(PowerSourceRef.Card(secondWind)), QueueKind.Offer)

  /** The parked follow-up offer's options: Take, then Skip. */
  private def offerOptions(state: OathState): Vector[DecisionOption] =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog))
      .toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _)
            if decide.decisionId == FollowUpOffer.decisionId => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)

  /** The rival's Sneak Attack Campaign against p1, taken after p1's won
    * Conquest of the origin's bandits: a Raid of p1 with 3 warbands. Its
    * events run from p1's start. */
  private def rivalSneakAttacksAfterP1sConquest(game: OathRules, b: Board)
      : Run =
    val done = commit(game, b, 4).finishPlanWindows
    val taken = accepted(FollowUpPlay.taken(game, done.state, b.rival))
    val holder = b.copy(actor = b.rival, ready = ready(done.state))
    val sneaked = committed(game, taken, holder, force = 3, raid = true)
      .finishPlanWindows
    sneaked.copy(events = done.events ++ sneaked.events)

  test("after another player's Campaign its holder is offered a Campaign " +
      "against them"):
    val b = rivalHoldsSneakAttackAtTheOrigin
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.result.attacker, b.actor)
    assertEquals(FollowUpPlay.offered(done.state),
      Some(sneakOffer(b.rival, b.actor)))

  test("the taken Campaign is the holder's, off turn, against the earlier " +
      "attacker, for no Supply"):
    val b = rivalHoldsSneakAttackAtTheOrigin
    val sneaked = rivalSneakAttacksAfterP1sConquest(rules(winningDice), b)
    val result = sneaked.result
    assertEquals(result.attacker, b.rival)
    assertEquals(result.defender, CampaignDefender.Player(b.actor))
    assertEquals(Look(ready(sneaked.state)).supply(b.rival),
      Look(b.ready).supply(b.rival))
    assertEquals(Look(ready(sneaked.state)).active, b.actor)

  test("it is offered before the Campaign's Second Wind follow-ups, which " +
      "follow whether it is taken or skipped"):
    val base = rivalHoldsSneakAttackAtTheOrigin
    val b = on(withAdviserFor(base, base.actor, secondWind,
      Orientation.FaceUp))(_.secrets(base.actor, faceUp = 1)
      .favor(base.actor, 1))
    val game = rules(winningDice)
    val won = commit(game, b, 4)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(secondWind)).finishPlanWindows
    assertEquals(FollowUpPlay.offered(won.state),
      Some(sneakOffer(b.rival, b.actor)))
    assertEquals(ready(won.state).game.current.queue,
      Vector(p1SecondWindOffer(b, ActionRef.Travel),
        p1SecondWindOffer(b, ActionRef.Campaign)))
    val skipped = accepted(FollowUpPlay.skipped(game, won.state, b.rival))
    assertEquals(FollowUpPlay.offered(skipped.state),
      Some(p1SecondWindOffer(b, ActionRef.Travel)))
    // Taken, the Sneak Attack Campaign runs first, and Second Wind's offers
    // wait behind it.
    val taken = accepted(FollowUpPlay.taken(game, won.state, b.rival))
    assertEquals(ready(taken.state).game.current.walkActor, b.rival)
    assertEquals(ready(taken.state).game.current.queue,
      Vector(p1SecondWindOffer(b, ActionRef.Travel),
        p1SecondWindOffer(b, ActionRef.Campaign)))

  test("no offer when the holder cannot campaign against the attacker"):
    // The rival stands apart from p1, at a site nobody rules.
    val base = board(warbands = 4, supply = 7)
    val b = withAdviserFor(base, base.rival, sneak, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state), None)
    assertEquals(ready(done.state).game.current.queue, Vector.empty)

  test("no offer after the holder's own Campaign"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = withAdviserFor(base, base.actor, sneak, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.result.attacker, b.actor)
    assertEquals(FollowUpPlay.offered(done.state), None)

  test("no offer when the holder's Sneak Attack is facedown"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = withAdviserFor(base, base.rival, sneak, Orientation.FaceDown)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state), None)

  test("a holder under Vow of Peace sees Take unavailable, with its " +
      "reason"):
    val base = rivalHoldsSneakAttackAtTheOrigin
    val b = withAdviserFor(base, base.rival,
      CatalogNames.denizen("Vow of Peace"), Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(FollowUpPlay.offered(done.state),
      Some(sneakOffer(b.rival, b.actor)))
    val take = offerOptions(done.state).collectFirst {
      case shown: DecisionOption.Unavailable if shown.ref ==
          FollowUpOffer.take => shown.reason }
    assertEquals(take, Some(OptionReason.Written(
      "Vow of Peace prevents its ruler from campaigning")))

  test("a defender's Second Wind Campaign triggers it too"):
    // The rival rules the origin and holds Second Wind; p3, the bystander,
    // holds Sneak Attack where the rival's and p3's pawns start. p1 cannot
    // reach p3, so p3 is offered nothing after p1's Campaign. The rival wins
    // as defender, skips the Travel and raids p3, off turn; p3 is then
    // offered a Campaign against the rival.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(withAdviserFor(base, base.rival, secondWind,
        Orientation.FaceUp), base.bystander, sneak, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1).favor(base.rival, 1))
    val game = rules(losingDice)
    val held = commit(game, b, 2).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(secondWind)).finishPlanWindows
    assertEquals(held.winner, Some(false))
    assertEquals(FollowUpPlay.offered(held.state).map(_.source),
      Some(Some(PowerSourceRef.Card(secondWind))))
    val noTravel = accepted(FollowUpPlay.skipped(game, held.state, b.rival))
    val toCampaign = accepted(FollowUpPlay.taken(game, noTravel.state,
      b.rival))
    val rival = b.copy(actor = b.rival, ready = ready(noTravel.state))
    val raided = committed(game, toCampaign, rival, force = 2, raid = true)
      .finishPlanWindows
    assertEquals(raided.result.attacker, b.rival)
    assertEquals(FollowUpPlay.offered(raided.state),
      Some(sneakOffer(b.bystander, b.rival)))

  test("no offer after a Campaign its attacker skips"):
    // p3 holds Vow of Union and rules Broken Peaks, in their region; the
    // rival holds Sneak Attack at the origin. p3 moves Broken Peaks'
    // warbands to their board, and p1 then skips the Campaign. The walk
    // ends before the root's trailing Branch, so nothing is offered, even
    // though an earlier Campaign of p1's left its result behind.
    val brokenPeaks = CatalogNames.site("Broken Peaks")
    val base = rivalHoldsSneakAttackAtTheOrigin
    val game = rules(winningDice)
    val earlier = commit(game, base, 4).finishPlanWindows.result
    assertEquals(earlier.attacker, base.actor)
    val b = on(withAdviserFor(base, base.bystander,
        CatalogNames.denizen("Vow of Union"), Orientation.FaceUp))(
      _.warbandsAt(brokenPeaks, base.bystander, 2).update(_.updateCurrent(
        _.copy(lastCampaignResult = Some(earlier)))))
    val started = game.startWalker(OathState.Ready(b.ready),
      ActionRef.Campaign, b.actor).fold(e => fail(s"no start: $e"), identity)
    val moved = Run(game, started, started.events)
      .pick(b.bystander, VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
      .answer(b.bystander, VowOfUnion.arrangeDecisionId, DistributeAnswer(
        Vector(DistributeAmount(DecisionOptionRef.Player(b.bystander), 5),
          DistributeAmount(DecisionOptionRef.Site(brokenPeaks), 0))))
    val skipped = moved.pick(b.actor, CampaignIds.kind, CampaignIds.skip)
    assertEquals(FollowUpPlay.offered(skipped.state), None)
    assertEquals(ready(skipped.state).game.current.lastCampaignResult,
      Some(earlier))

  test("a replayed Sneak Attack Campaign reaches the same state"):
    val b = rivalHoldsSneakAttackAtTheOrigin
    val game = rules(winningDice)
    val sneaked = rivalSneakAttacksAfterP1sConquest(game, b)
    assertEquals(ActPowerHarness.replayed(game, b.ready, sneaked.events),
      ready(sneaked.state))
