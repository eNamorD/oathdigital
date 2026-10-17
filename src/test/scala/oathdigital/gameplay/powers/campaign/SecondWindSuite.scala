package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.{FollowUpPlay, OathRules}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.Unwrap.accepted

/** Second Wind (card 16): an attacker's battle plan that, once the Campaign is
  * won, owes a Travel and then a Campaign, each spending no Supply
  * (follow-up actions design, rulings 9 and 11). */
class SecondWindSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Second Wind")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** p1 can conquer the origin, which bandits rule, with 5 warbands and 7
    * Supply, holding Second Wind faceup with `payments` faceup secrets and
    * favor to pay its cost that many times. The rival stands at the origin
    * too, so a Raid is legal there once p1 rules it. */
  private def holding(payments: Int = 1): Board =
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.actor, card, Orientation.FaceUp))(
      _.secrets(base.actor, faceUp = payments).favor(base.actor, payments))

  /** The rival rules the origin with 2 warbands and holds Second Wind
    * faceup, with one faceup secret and one favor to pay for it. p1 can
    * only conquer the rival there. */
  private def rivalRulesOriginHoldingSecondWind: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1).favor(base.rival, 1))

  private def secondWindOfferFor(player: PlayerId, action: ActionRef)
      : QueuedProcedure =
    QueuedProcedure(player, action, Vector.empty,
      Some(PowerSourceRef.Card(card)), QueueKind.Offer)

  /** p1 attacks with `force`; the rival picks Second Wind as the
    * defender's plan, then every window is finished. */
  private def defendedWithSecondWind(game: OathRules, b: Board,
      force: Int): Run =
    commit(game, b, force).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows

  private def owed(b: Board, action: ActionRef): QueuedProcedure =
    QueuedProcedure(b.actor, action, Vector.empty,
        Some(PowerSourceRef.Card(card)), QueueKind.Offer)

  /** What is owed once the Campaign ends, in order: the entry the driver has
    * offered, then the ones behind it. */
  private def followUps(run: Run): Vector[QueuedProcedure] =
    FollowUpPlay.offered(run.state).toVector ++
      ready(run.state).game.current.queue

  /** A Campaign with Second Wind chosen as the attacker's plan. */
  private def withSecondWind(run: Run, b: Board): Run =
    run.pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows

  test("a won Campaign queues a Travel, then a Campaign"):
    val b = holding()
    val won = withSecondWind(commit(rules(winningDice), b, 4), b)
    assertEquals(won.winner, Some(true))
    assertEquals(followUps(won),
      Vector(owed(b, ActionRef.Travel), owed(b, ActionRef.Campaign)))

  test("a lost Campaign queues nothing"):
    val b = holding()
    val lost = withSecondWind(commit(rules(losingDice), b, 0), b)
    assertEquals(lost.winner, Some(false))
    assertEquals(followUps(lost), Vector.empty)

  test("not choosing it queues nothing"):
    val b = holding()
    val won = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(won.winner, Some(true))
    assertEquals(followUps(won), Vector.empty)

  test("choosing it places a secret on the card and burns a favor"):
    val b = holding()
    val chosen = commit(rules(losingDice), b, 0)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(ready(chosen.state)).faceUpSecrets(b.actor), 0)
    assertEquals(Look(ready(chosen.state)).favor(b.actor), 0)

  test("a defender is offered it"):
    val b = rivalRulesOriginHoldingSecondWind
    val run = commit(rules(losingDice), b, 2).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(run.offered(b.rival).contains(ref))

  test("a winning defender is owed a Travel, then a Campaign"):
    val b = rivalRulesOriginHoldingSecondWind
    val held = defendedWithSecondWind(rules(losingDice), b, 2)
    assertEquals(held.winner, Some(false))
    assertEquals(followUps(held), Vector(
      secondWindOfferFor(b.rival, ActionRef.Travel),
      secondWindOfferFor(b.rival, ActionRef.Campaign)))

  test("a beaten defender is owed nothing"):
    val b = rivalRulesOriginHoldingSecondWind
    val beaten = defendedWithSecondWind(rules(winningDice), b, 5)
    assertEquals(beaten.winner, Some(true))
    assertEquals(followUps(beaten), Vector.empty)

  test("the defender pays off turn: the secret flips facedown and a " +
      "favor burns"):
    val b = rivalRulesOriginHoldingSecondWind
    val chosen = commit(rules(losingDice), b, 2).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    val after = Look(ready(chosen.state))
    assertEquals(after.faceUpSecrets(b.rival), 0)
    assertEquals(after.faceDownSecrets(b.rival),
      Look(b.ready).faceDownSecrets(b.rival) + 1)
    assertEquals(after.favor(b.rival), 0)

  test("the defender walks the Travel off turn, then is offered the " +
      "Campaign"):
    val b = rivalRulesOriginHoldingSecondWind
    val game = rules(losingDice)
    val held = defendedWithSecondWind(game, b, 2)
    val taken = accepted(FollowUpPlay.taken(game, held.state, b.rival))
    val to = Look(ready(taken.state)).pawn(b.actor)
    val travelled = accepted(game.resolveWalker(taken.state, b.rival,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(to))))
    val after = ready(travelled.state)
    assertEquals(Look(after).pawn(b.rival), to)
    assertEquals(Look(after).supply(b.rival), Look(b.ready).supply(b.rival))
    assertEquals(Look(after).active, b.actor)
    assertEquals(FollowUpPlay.offered(travelled.state),
      Some(secondWindOfferFor(b.rival, ActionRef.Campaign)))

  test("skipping the Travel still offers the Campaign"):
    val b = holding()
    val game = rules(winningDice)
    val won = withSecondWind(commit(game, b, 4), b)
    assertEquals(FollowUpPlay.offered(won.state),
      Some(owed(b, ActionRef.Travel)))
    val skipped = accepted(FollowUpPlay.skipped(game, won.state, b.actor))
    assertEquals(FollowUpPlay.offered(skipped.state),
      Some(owed(b, ActionRef.Campaign)))
    assertEquals(ready(skipped.state).game.current.queue, Vector.empty)

  test("the queued Campaign offers it again, with the secret still on the " +
      "card, and a second win queues again"):
    val b = holding(payments = 2)
    val game = rules(winningDice)
    val won = withSecondWind(commit(game, b, 4), b)
    val skipped = accepted(FollowUpPlay.skipped(game, won.state, b.actor))
    // p1 now rules the origin, so the taken Campaign raids the rival there.
    val after = ready(skipped.state)
    val taken = accepted(FollowUpPlay.taken(game, skipped.state, b.actor))
    val again = committed(game, taken, b.copy(ready = after),
      Look(after).boardWarbands(b.actor), raid = true)
    assert(awaits(again, b.actor, CampaignIds.attackerPlan))
    assert(again.offered(b.actor).contains(ref))
    val raided = withSecondWind(again, b)
    // The beaten rival's pawn is moved to the first site offered.
    val wonAgain =
      if awaits(raided, b.actor, CampaignIds.relocation) then
        raided.pick(b.actor, CampaignIds.relocation,
          raided.offered(b.actor).head).finishPlanWindows
      else raided
    assertEquals(wonAgain.winner, Some(true))
    assertEquals(followUps(wonAgain),
      Vector(owed(b, ActionRef.Travel), owed(b, ActionRef.Campaign)))
