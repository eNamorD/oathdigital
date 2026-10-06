package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Herald: after another player campaigns against a player, its holder
  * gains a favor from a bank they choose. */
class HeraldSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Herald")

  private def favorOf(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.favor

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Herald.id, Vector(Herald.gained), run.events)

  private def gained(who: PlayerId, suit: Suit): NoteText.Said =
    NoteText.Said("gained", s"${who.value} gained 1 favor from the $suit bank.",
      covers = true)

  /** p1 can conquer the origin, which the rival rules; the player `holder`
    * picks holds Herald on `orientation`; only `stocks` hold favor. */
  private def conquestWithHerald(holder: Board => PlayerId,
      orientation: Orientation, stocks: (Suit, Int)*): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, holder(base), card, orientation))(
      _.banksHoldingOnly(stocks*))

  test("after another player's Campaign against a player, a third player " +
      "holding it chooses the bank"):
    val b = conquestWithHerald(_.bystander, Orientation.FaceUp,
      Suit.Order -> 2, Suit.Hearth -> 3)
    val asked = commit(rules(winningDice), b, 4).finishPlanWindows
    // It asks once the Campaign's result is recorded.
    assertEquals(asked.winner, Some(true))
    assert(awaits(asked, b.bystander, Herald.decisionId))
    assertEquals(asked.offered(b.actor).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.FavorBank(Suit.Order),
      DecisionOptionRef.FavorBank(Suit.Hearth)))
    val done = asked.pick(b.bystander, Herald.decisionId,
      DecisionOptionRef.FavorBank(Suit.Order))
    assertEquals(favorOf(done.state, b.bystander),
      favorOf(OathState.Ready(b.ready), b.bystander) + 1)
    assertEquals(ready(done.state).banks.favor(Suit.Order), 1)
    assertEquals(lines(done), Vector(gained(b.bystander, Suit.Order)))

  test("the defender holding it gains too, from the one stocked bank, " +
      "without being asked"):
    val b = conquestWithHerald(_.rival, Orientation.FaceUp, Suit.Order -> 2)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(parked.parkedDecision(done.state), None)
    assertEquals(favorOf(done.state, b.rival),
      favorOf(OathState.Ready(b.ready), b.rival) + 1)
    assertEquals(lines(done), Vector(gained(b.rival, Suit.Order)))

  test("a Campaign against bandits gives nothing"):
    val base = board(warbands = 5, supply = 7)
    val b = on(withAdviserFor(base, base.bystander, card, Orientation.FaceUp))(
      _.banksHoldingOnly(Suit.Order -> 2))
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(favorOf(done.state, b.bystander),
      favorOf(OathState.Ready(b.ready), b.bystander))
    assertEquals(lines(done), Vector.empty)

  test("the attacker's own Herald gives nothing"):
    val b = conquestWithHerald(_.actor, Orientation.FaceUp, Suit.Order -> 2)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(favorOf(done.state, b.actor),
      favorOf(OathState.Ready(b.ready), b.actor))
    assertEquals(lines(done), Vector.empty)

  test("a facedown Herald, or empty banks, give nothing"):
    val hidden = conquestWithHerald(_.bystander, Orientation.FaceDown,
      Suit.Order -> 2)
    assertEquals(lines(commit(rules(winningDice), hidden, 4)
      .finishPlanWindows), Vector.empty)
    val empty = conquestWithHerald(_.bystander, Orientation.FaceUp)
    val done = commit(rules(winningDice), empty, 4).finishPlanWindows
    assertEquals(parked.parkedDecision(done.state), None)
    assertEquals(lines(done), Vector.empty)
