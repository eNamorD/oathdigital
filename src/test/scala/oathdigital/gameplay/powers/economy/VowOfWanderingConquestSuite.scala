package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{Run, awaits, commit,
  parked, winningDice}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.testkit.{CatalogNames, Look}

/** Vow of Wandering after a Conquest victory: the placement question is not
  * asked, and the survivors stay on the board. */
class VowOfWanderingConquestSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val game = CampaignFixture.rules(winningDice)

  /** `run` with every plan window finished and nothing sacrificed, until the
    * Campaign ends or asks something else. Unlike `finishPlanWindows`, it
    * never answers the placement. */
  private def throughPlans(run: Run): Run =
    parked.parkedDecision(run.state) match
      case Some(facts) if facts.decision == CampaignIds.attackerPlan ||
          facts.decision == CampaignIds.defenderPlan =>
        throughPlans(run.answer(facts.awaiting, facts.decision,
          ChooseOneAnswer(CampaignIds.finish)))
      case Some(facts) if facts.decision == CampaignIds.sacrifice =>
        throughPlans(run.answer(facts.awaiting, facts.decision,
          ChooseAmountAnswer(0)))
      case _ => run

  test("the holder is not asked to place the survivors, who stay on the " +
      "board"):
    val b = CampaignFixture.withAdviser(
      CampaignFixture.board(warbands = 5, supply = 7), vow,
      Orientation.FaceUp)
    val done = throughPlans(commit(game, b, 4))
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).player(b.actor).board.warbands, 5)
    assertEquals(NoteText.said(VowOfWandering.id, VowOfWandering.noteKeys,
      done.events), Vector(NoteText.Said("kept",
        "Kept 4 surviving warbands on the board.", covers = false)))

  test("without the Vow, the same Conquest asks for the placement"):
    val b = CampaignFixture.board(warbands = 5, supply = 7)
    assert(awaits(throughPlans(commit(game, b, 4)), b.actor,
      CampaignIds.placement))
