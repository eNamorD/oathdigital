package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.Unwrap.accepted

/** Second Wind (card 16): after its holder's Campaign is won, a Travel and
  * then a Campaign are owed, each spending no Supply (follow-up actions
  * design, rulings 9 and 11). */
class SecondWindSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Second Wind")
  private val modifiers = Vector(SecondWind.id)

  /** p1 can conquer the origin, which bandits rule, with 5 warbands and 7
    * Supply, holding Second Wind faceup with `payments` secrets and favor to
    * pay its cost that many times. The rival stands at the origin too, so a
    * Raid is legal there once p1 rules it. */
  private def holding(payments: Int = 1): Board =
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.actor, card, Orientation.FaceUp))(
      _.secrets(base.actor, faceUp = payments).favor(base.actor, payments))

  private def owed(b: Board, action: ActionRef): FollowUp =
    FollowUp(b.actor, action, PowerSourceRef.Card(card))

  private def followUps(run: Run): Vector[FollowUp] =
    ready(run.state).game.current.followUps

  test("a won Campaign queues a Travel, then a Campaign"):
    val b = holding()
    val won = commit(rules(winningDice), b, 4, modifiers = modifiers)
      .finishPlanWindows
    assertEquals(won.winner, Some(true))
    assertEquals(followUps(won),
      Vector(owed(b, ActionRef.Travel), owed(b, ActionRef.Campaign)))

  test("a lost Campaign queues nothing"):
    val lost = commit(rules(losingDice), holding(), 0, modifiers = modifiers)
      .finishPlanWindows
    assertEquals(lost.winner, Some(false))
    assertEquals(followUps(lost), Vector.empty)

  test("the cost is paid at the start, win or lose"):
    val b = holding()
    val started = accepted(rules(losingDice).startWalker(
      oathdigital.model.OathState.Ready(b.ready), ActionRef.Campaign,
      b.actor, modifiers))
    assertEquals(Look(ready(started.state)).faceUpSecrets(b.actor), 0)
    assertEquals(Look(ready(started.state)).favor(b.actor), 0)

  test("skipping the Travel still offers the Campaign"):
    val b = holding()
    val game = rules(winningDice)
    val won = commit(game, b, 4, modifiers = modifiers).finishPlanWindows
    val skipped = accepted(game.skipFollowUp(won.state, b.actor))
    assertEquals(ready(skipped.state).game.current.followUps,
      Vector(owed(b, ActionRef.Campaign)))

  test("the queued Campaign cannot select it again: its secret is still on " +
      "the card"):
    val b = holding(payments = 2)
    val game = rules(winningDice)
    val won = commit(game, b, 4, modifiers = modifiers).finishPlanWindows
    val skipped = accepted(game.skipFollowUp(won.state, b.actor))
    assert(!accepted(game.offerableWalkerPowers(ready(skipped.state), b.actor,
      ActionRef.Campaign)).exists(_.id == SecondWind.id))
