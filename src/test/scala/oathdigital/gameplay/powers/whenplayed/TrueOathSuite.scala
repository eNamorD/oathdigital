package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, OathkeeperGoalChoice}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** True Oath: with three other Beast cards ruled, its player may change the
  * Oathkeeper goal. */
class TrueOathSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[TrueOath]
  private val oath = power.cardId
  private val played = hook(oath)

  /** True Oath and the Beast advisers Animal Host and Threatening Roar,
    * p1's, under Supremacy. Wolves, the third other Beast card, stands at
    * p1's site, which p1 rules, or, when `wolvesRuled` is false, at a site
    * nobody rules. */
  private def oathBesideBeasts(wolvesRuled: Boolean = true): ReadyGame =
    Table.start.pawn(p1, homeSite).adviser(p1, oath)
      .adviser(p1, "Animal Host").adviser(p1, "Threatening Roar")
      .warbandsAt(homeSite, p1, 1)
      .denizen("Wolves", at = if wolvesRuled then homeSite else nearSite)
      .oathkeeperGoal(OathkeeperGoal.Supremacy).ready

  private def goal(ready: ReadyGame): OathkeeperGoal =
    ready.game.campaign.oathkeeperGoal

  test("with three other Beast cards it offers the other goals and Keep, " +
      "and sets the one chosen"):
    val ready = oathBesideBeasts()
    val walked = walkAnswering(ready, power, played,
      chooseOne(OathkeeperGoalChoice.ref(OathkeeperGoal.Devotion)))
    assertEquals(walked.asked.map(_.decisionId), Vector(TrueOath.decisionId))
    assertEquals(chooseOneOptions(walked.asked.head),
      OathkeeperGoal.all.map { g =>
        val shown = DecisionOption.Button(OathkeeperGoalChoice.ref(g),
          OathkeeperGoalChoice.label(g))
        if g == OathkeeperGoal.Supremacy then DecisionOption.Unavailable(
          shown, OptionReason.Written(OathkeeperGoalChoice.currentGoal))
        else shown
      } :+ DecisionOption.Button(TrueOath.keep, "Keep the current goal"))
    assertEquals(goal(walked.end), OathkeeperGoal.Devotion)
    assertJournalRebuilds(ready, walked.events, walked.end)
    assertEquals(said(power, walked.events), Vector(NoteText.Said(
      "changed", "Changed the Oathkeeper goal to Devotion.",
      covers = false)))

  test("Keep leaves the goal, and the line says so"):
    val walked = walkAnswering(oathBesideBeasts(), power, played,
      chooseOne(TrueOath.keep))
    assertEquals(goal(walked.end), OathkeeperGoal.Supremacy)
    assertEquals(said(power, walked.events), Vector(NoteText.Said("kept",
      "Kept the Oathkeeper goal.", covers = false)))

  test("with two other Beast cards, True Oath itself not counted, it asks " +
      "nothing"):
    val walked = walkAnswering(oathBesideBeasts(wolvesRuled = false), power,
      played)
    assertEquals(walked.asked, Vector.empty)
    assertEquals(goal(walked.end), OathkeeperGoal.Supremacy)
    assertEquals(said(power, walked.events), Vector(NoteText.Said("unmet",
      "Ruled fewer than three other Beast cards.", covers = false)))
