package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.campaign.SneakAttackCard
import oathdigital.gameplay.walker.FollowUpOffer
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer,
  ChooseOneAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Dice, MenuPlay, Park,
  Step, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, raidAnswers}

/** Journaled walks by a player who is not the active player, for the Game
  * Log suites (catalog batch 8, slice 14). Kept beside `LogScripts`, which
  * is at its size limit. */
object OffTurnLogScripts:
  /** p1 and p2 at Ancient City, which two bandits rule; p2 holds Sneak
    * Attack faceup. p3 stands at Buried Giant. */
  private def sneakAttackTable(using munit.Location): Table =
    Table.start.pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant").bandits("Ancient City", 2)
      .adviser(p2, SneakAttackCard.id).banditsRefilled

  private def menuCampaign: Step = MenuPlay.start(p1, ActionRef.Campaign)

  /** No warband is sacrificed, so blank dice lose every Campaign. */
  private val noSacrifice: Answers = {
    case park if park.decisionId == CampaignIds.sacrifice =>
      ChooseAmountAnswer(0)
  }

  /** Every follow-up offer answered with `answer`, and no sacrifice. */
  private def everyOffer(answer: DecisionOptionRef): Answers = noSacrifice
    .orElse {
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(answer)
    }

  /** p1 raids p2 at Ancient City and loses, so p2 stays there; p2 then
    * takes Sneak Attack and raids p1 there, off turn. */
  def sneakAttackTaken(using munit.Location): Script =
    val (service, act) = atTable("sneak-attack", sneakAttackTable,
      Dice.blanks)
    raidAnswers(act, everyOffer(FollowUpOffer.take)).after(menuCampaign)
    Script("sneak-attack", service, p1)

  /** The same, with p2 skipping the offer. */
  def sneakAttackSkipped(using munit.Location): Script =
    val (service, act) = atTable("sneak-attack-skipped", sneakAttackTable,
      Dice.blanks)
    raidAnswers(act, everyOffer(FollowUpOffer.skip)).after(menuCampaign)
    Script("sneak-attack-skipped", service, p1)

  /** Whether the parked follow-up offer is for a Travel. */
  private def offersTravel(park: Park): Boolean =
    park.ready.game.current.walkerEntry.exists(_.procedure == ActionRef.Travel)

  /** Whether the parked plan window still offers `card`. */
  private def stillOffers(park: Park, card: DenizenId): Boolean =
    park.decide.query match
      case one: DecisionQuery.ChooseOne => one.options.exists(option =>
        option.available && option.ref == DecisionOptionRef.Denizen(card))
      case _ => false

  /** p2 rules Ancient City with 2 warbands and holds Second Wind faceup,
    * with a faceup secret and a favor; p1 conquers there with blank dice
    * and loses. p2 picks Second Wind, travels to Buried Giant off turn,
    * then skips the Campaign. */
  def secondWindDefenderTravels(using munit.Location): Script =
    val secondWind = CatalogNames.denizen("Second Wind")
    val table = Table.start.pawn(p1, "Ancient City")
      .pawn(p3, "Buried Giant").warbandsAt("Ancient City", p2, 2)
      .adviser(p2, secondWind).secrets(p2, faceUp = 1).favor(p2, 1)
      .banditsRefilled
    val (service, act) = atTable("second-wind-defender", table, Dice.blanks)
    raidAnswers(act, noSacrifice.orElse {
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(DecisionOptionRef.Button("conquest"))
      case park if park.decisionId == CampaignIds.defenderPlan &&
          stillOffers(park, secondWind) =>
        ChooseOneAnswer(DecisionOptionRef.Denizen(secondWind))
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(
          if offersTravel(park) then FollowUpOffer.take
          else FollowUpOffer.skip)
      case park if park.decisionId == TravelProcedure.destinationDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Site(
          CatalogNames.site("Buried Giant")))
    }).after(menuCampaign)
    Script("second-wind-defender", service, p1)
