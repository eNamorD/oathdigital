package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.VowOfUnion
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Dice, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, raidAnswers, texts}

/** How Vow of Union reads in the Game Log: an off-turn holder is named,
  * neither of its questions posts a "Chose" line, and a Campaign it lets
  * end early says why. */
class VowOfUnionLogSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Union")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val buriedGiant = CatalogNames.site("Buried Giant")

  /** Vow of Union's answers: the holder moves every warband at `site` to
    * their board, which then holds `after`. */
  private def holderMovesAllToBoard(holder: PlayerId, site: SiteId,
      after: Int): Answers = {
    case park if park.decisionId == VowOfUnion.directionDecisionId =>
      ChooseOneAnswer(VowOfUnion.toBoard)
    case park if park.decisionId == VowOfUnion.arrangeDecisionId =>
      DistributeAnswer(Vector(
        DistributeAmount(DecisionOptionRef.Player(holder), after),
        DistributeAmount(DecisionOptionRef.Site(site), 0)))
  }

  /** p1 campaigns from Ancient City, where p2 stands. p3, at Buried Giant
    * with Vow of Union, rules it with 2 warbands and moves both to their
    * board, which then holds 5. `kind` answers p1's kind question, Raid
    * unless it says otherwise. The log as p2 sees it. */
  private def raidAfterHolderMovedTwo(kind: Answers = PartialFunction.empty)
      : Vector[String] =
    val table = Table.start.pawn(p1, ancientCity).pawn(p2, ancientCity)
      .pawn(p3, buriedGiant).adviser(p3, vow).warbandsAt(buriedGiant, p3, 2)
      .banditsRefilled
    val (service, act) = atTable("vow-of-union", table, Dice.raid)
    raidAnswers(act, kind.orElse(holderMovesAllToBoard(p3, buriedGiant, 5)))
      .after(MenuPlay.start(p1, ActionRef.Campaign))
    texts(format(Script("vow-of-union", service, p1), Some(p2)))

  test("its line names the off-turn holder, with no Chose line for either " +
      "question"):
    val log = raidAfterHolderMovedTwo()
    assert(log.contains(s"Vow of Union: ${playerLabel(p3)} moved 2 " +
      "warbands from sites to their board."), log.mkString("\n"))
    assert(!log.exists(_.startsWith(s"${playerLabel(p3)} chose")),
      log.mkString("\n"))

  test("a skipped Campaign says so, with no Chose line for the Skip"):
    val log = raidAfterHolderMovedTwo {
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(CampaignIds.skip)
    }
    assert(log.exists(_.startsWith("Campaign")), log.mkString("\n"))
    assert(log.contains("Skipped the Campaign."), log.mkString("\n"))
    assert(!log.exists(_.contains("Skip Campaign")), log.mkString("\n"))

  test("a Campaign left with nothing to attack says so"):
    // p2, at Broken Peaks with Vow of Union, rules Ancient City, p1's site,
    // and empties it: no Conquest is left, and no Raid was legal.
    val table = Table.start.pawn(p1, ancientCity).pawn(p2, "Broken Peaks")
      .pawn(p3, buriedGiant).adviser(p2, vow).warbandsAt(ancientCity, p2, 2)
      .banditsRefilled
    val (service, act) = atTable("vow-of-union-ended", table, Dice.raid)
    act.withAnswers(holderMovesAllToBoard(p2, ancientCity, 5))
      .after(MenuPlay.start(p1, ActionRef.Campaign))
    val log = texts(format(Script("vow-of-union-ended", service, p1),
      Some(p3)))
    assert(log.exists(_.startsWith("Campaign")), log.mkString("\n"))
    assert(log.contains("Nothing left to attack: the Campaign ended."),
      log.mkString("\n"))
