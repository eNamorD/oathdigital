package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.model._

/** Vow of Peace's second sentence: attackers cannot sacrifice warbands to
  * increase their attack against a faceup holder.
  */
class VowOfPeaceSuite extends munit.FunSuite:
  private val vow = cardWith("denizen.vow-of-peace")
  private val wrestlers = cardWith("denizen.wrestlers")

  private def defendedBy(b: Board, orientation: Orientation): Board =
    withAdviserFor(b, b.rival, vow, orientation)

  /** Every decision the Campaign parks on until it ends, answering the plan
    * windows with Finish and every amount with zero.
    */
  private def asked(run: Run): Vector[String] =
    parked.parkedDecision(run.state) match
      case Some(facts) if facts.decision == CampaignIds.attackerPlan ||
          facts.decision == CampaignIds.defenderPlan =>
        facts.decision +: asked(run.answer(facts.awaiting, facts.decision,
          DecisionAnswer.ChooseOneAnswer(CampaignIds.finish)))
      case Some(facts) if facts.decision == CampaignIds.sacrifice ||
          facts.decision == CampaignIds.placement =>
        facts.decision +: asked(run.answer(facts.awaiting, facts.decision,
          DecisionAnswer.ChooseAmountAnswer(0)))
      case Some(facts) => Vector(facts.decision)
      case None => Vector.empty

  test("an attacker against a faceup holder is not asked to sacrifice, and " +
      "the battle still resolves"):
    val defended = defendedBy(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      Orientation.FaceUp)
    val run = commit(rules(losing), defended, 2)
    assert(!asked(run).contains(CampaignIds.sacrifice))
    val done = ready(run.finishPlanWindows.state)
    assert(done.game.current.lastCampaignResult.nonEmpty)

  test("a Raid against a faceup holder is protected too"):
    val raided = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val run = commit(rules(losing), defendedBy(raided, Orientation.FaceUp), 2,
      raid = true)
    assert(!asked(run).contains(CampaignIds.sacrifice))

  test("an attacker against anyone else is still asked"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    assert(asked(commit(rules(losing), against, 2))
      .contains(CampaignIds.sacrifice))

  test("a facedown Vow of Peace protects no one"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val defended = defendedBy(against, Orientation.FaceDown)
    assert(asked(commit(rules(losing), defended, 2))
      .contains(CampaignIds.sacrifice))

  test("a holder who is not the defender is not protected"):
    val defended = defendedBy(board(warbands = 5, supply = 7),
      Orientation.FaceUp)
    assert(asked(commit(rules(losing), defended, 2))
      .contains(CampaignIds.sacrifice))

  test("the defender's own sacrifice is unaffected"):
    val defended = defendedBy(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      Orientation.FaceUp)
    val b = withAdviserFor(defended, defended.rival, wrestlers,
      Orientation.FaceUp)
    val run = commit(rules(losing), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(wrestlers))
    assert(picked.since(run).exists(_.isInstanceOf[Sacrifice]))

  private def notes(run: Run): Vector[PowerNoted] =
    run.finishPlanWindows.events.collect { case noted: PowerNoted => noted }

  test("the removed sacrifice decision leaves a note naming the defender"):
    val b = defendedBy(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)),
      Orientation.FaceUp)
    assertEquals(notes(commit(rules(losing), b, 2)), Vector(PowerNoted(
      VowOfPeaceContribution.id, VowOfPeaceContribution.noSacrifice(
        PowerSourceRef.Card(vow), NoteArg.Player(b.rival)),
      covers = false)))

  test("an attacker against anyone else writes no Vow of Peace note"):
    val against = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    assertEquals(notes(commit(rules(losing), against, 2)), Vector.empty)
