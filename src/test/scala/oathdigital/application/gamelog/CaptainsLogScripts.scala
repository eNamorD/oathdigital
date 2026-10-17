package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.action.{Captains, CaptainsCard}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Dice, MenuPlay, Step,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, raidAnswers}

/** Journaled Captains Campaigns, for the Game Log suites (catalog batch 8,
  * slice 13). Kept beside `LogScripts`, which is at its size limit. */
object CaptainsLogScripts:
  /** Captains at Ancient City, where p1 stands and rules with 1 warband;
    * p1 also rules Deep Woods with `atWoods` warbands, where p2 stands, and
    * holds `onBoard` on the board. p3 stands at Buried Giant. */
  private def captainsTable(onBoard: Int, atWoods: Int)(
      using munit.Location): Table =
    Table.start.pawn(p1, "Ancient City").pawn(p3, "Buried Giant")
      .denizen(CaptainsCard.id, "Ancient City")
      .warbandsAt("Ancient City", p1, 1).warbandsAt("Deep Woods", p1, atWoods)
      .boardWarbands(p1, onBoard).pawn(p2, "Deep Woods").banditsRefilled

  private def captainsAtTheWoods: Step = MenuPlay.start(p1,
    ActionRef.UsePower(Captains.id),
    Vector(DecisionOptionRef.Denizen(CaptainsCard.id)))

  private val woodsAnswer: Answers = {
    case park if park.decisionId == Captains.siteDecisionId =>
      ChooseOneAnswer(DecisionOptionRef.Site(CatalogNames.site("Deep Woods")))
  }

  /** p1 uses Captains to Raid p2 at Deep Woods, a site p1 rules away from
    * p1's pawn, and wins. */
  def captainsRaidFromARuledSite(using munit.Location): Script =
    val (service, act) = atTable("captains-raid",
      captainsTable(onBoard = 3, atWoods = 2), Dice.raid)
    raidAnswers(act, woodsAnswer).after(captainsAtTheWoods)
    Script("captains-raid", service, p1)

  /** The same Raid lost: 1 warband on p1's board and 3 at Deep Woods are
    * committed with hollow swords and no sacrifice, so 2 die, and p1 takes
    * 1 of them from Deep Woods. */
  def captainsRaidLostWithASplit(using munit.Location): Script =
    val (service, act) = atTable("captains-raid-lost",
      captainsTable(onBoard = 1, atWoods = 3), Dice.blanks)
    raidAnswers(act, woodsAnswer.orElse {
      case park if park.decisionId == CampaignIds.sacrifice =>
        ChooseAmountAnswer(0)
      case park if park.decisionId == CampaignIds.originDeaths =>
        ChooseAmountAnswer(1)
    }).after(captainsAtTheWoods)
    Script("captains-raid-lost", service, p1)
