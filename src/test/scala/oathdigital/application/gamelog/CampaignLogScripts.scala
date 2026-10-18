package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer,
  ChooseManyAnswer, ChooseOneAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Dice, MenuPlay, Park,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, raidAnswers}

/** The Campaign branches the older goldens never play: a won Raid's
  * spoils, and a Conquest victory's placement with the bandits' loss.
  * Kept beside `LogScripts`, which is at its size limit (procedures
  * journal their own notes design, slice 4).
  */
object CampaignLogScripts:
  private val relic = CatalogNames.relic("Wine of Welcome")

  /** p1 Raids p2, who shares Ancient City, holds Wine of Welcome faceup,
    * holds the People's Favor, has a facedown adviser and two favor. p1
    * targets the relic and the banner as well as the pawn, commits three
    * warbands and sacrifices them all, so the attack is six against p2's
    * three board warbands and blank defense dice: p1 wins and takes both.
    */
  def raidTakingRelicAndBanner(using munit.Location): Script =
    val (service, act) = atTable("raid-spoils", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 3)
      .relic(p2, relic)
      .peoplesFavor(Some(p2), favor = 2)
      .adviser(p2, "Birdsong", facedown = true)
      .favor(p2, 2)
      .banditsRefilled, Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.targets =>
        ChooseManyAnswer(Vector(DecisionOptionRef.Relic(relic),
          DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("raid-spoils", service, p1)

  /** p1 Conquests Ancient City, which the bandits rule with three
    * warbands, with four board warbands and no sacrifice: four swords
    * against three, so p1 wins, the bandits lose all three, and p1 places
    * two of the four survivors on the site.
    */
  def conquestPlacingWarbands(using munit.Location): Script =
    val (service, act) = atTable("conquest-placed", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 4)
      .banditsRefilled, Dice.raid)
    val answers: Answers = {
      case Park(Decide(CampaignIds.force, _,
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
      case park if park.decisionId == CampaignIds.sacrifice =>
        ChooseAmountAnswer(0)
      case park if park.decisionId == CampaignIds.placement =>
        ChooseAmountAnswer(2)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    }
    act.withAnswers(answers).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("conquest-placed", service, p1)

  /** The scripts this file adds to the golden registry. */
  val named: Vector[(String, () => Script)] = Vector(
    "raid-spoils" -> (() => raidTakingRelicAndBanner),
    "conquest-placed" -> (() => conquestPlacingWarbands),
    "captains-raid" -> (() => CaptainsLogScripts.captainsRaidFromARuledSite),
    "captains-raid-lost" ->
      (() => CaptainsLogScripts.captainsRaidLostWithASplit))
