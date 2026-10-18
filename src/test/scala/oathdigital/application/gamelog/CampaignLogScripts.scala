package oathdigital.application.gamelog

import oathdigital.application.CampaignDicePort
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.StickyFire
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

  private val rampart = CatalogNames.edifice("Towering Rampart")
  private val outriders = CatalogNames.denizen("Outriders")

  /** Whether the parked decision still lists `ref`. A plan already chosen
    * is filtered out of the next pass, so this answers a free plan exactly
    * once without a marker on its card. */
  private def offers(park: Park, ref: DecisionOptionRef): Boolean =
    park.decide.query match
      case one: DecisionQuery.ChooseOne => one.options.exists(_.ref == ref)
      case _ => false

  /** Attack dice that all roll a two-swords-and-skull face, defense dice
    * blank: the roll Outriders is for. */
  private val skulls: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.TwoSwordsSkull)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** A Conquest of Ancient City, which the bandits rule and where an
    * intact Towering Rampart stands: a free defender's plan, which a
    * bandit defender applies without being asked. */
  def banditPlanApplied(using munit.Location): Script =
    val (service, act) = atTable("bandit-plan", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 4)
      .edifice("Towering Rampart", EdificeSide.Intact, "Ancient City")
      .banditsRefilled, Dice.raid)
    val answers: Answers = {
      case Park(Decide(CampaignIds.force, _,
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
      case park if park.decisionId == CampaignIds.sacrifice ||
          park.decisionId == CampaignIds.placement =>
        ChooseAmountAnswer(0)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    }
    act.withAnswers(answers).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("bandit-plan", service, p1)

  /** The `raid` table with an intact Towering Rampart at Ancient City,
    * where p2's pawn stands and p2 rules: the edifice is the ruler's, so
    * p2, the defender, is offered it and chooses it. */
  def defenderChoseAPlan(using munit.Location): Script =
    val (service, act) = atTable("defender-plan", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 3)
      .warbandsAt("Ancient City", p2, 2)
      .edifice("Towering Rampart", EdificeSide.Intact, "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .banditsRefilled, Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.defenderPlan &&
          offers(park, DecisionOptionRef.Edifice(rampart)) =>
        ChooseOneAnswer(DecisionOptionRef.Edifice(rampart))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("defender-plan", service, p1)

  /** p1 Raids p2 with Outriders faceup and rolls a skull on every attack
    * die: the plan writes the attack again without the skull cap, so one
    * total is told, after the plan's own line. */
  def raidUnderOutriders(using munit.Location): Script =
    val (service, act) = atTable("outriders", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 1)
      .adviser(p1, outriders)
      .adviser(p2, "Birdsong", facedown = true)
      .banditsRefilled, skulls)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.attackerPlan &&
          offers(park, DecisionOptionRef.Denizen(outriders)) =>
        ChooseOneAnswer(DecisionOptionRef.Denizen(outriders))
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("outriders", service, p1)

  private val stickyFire = CatalogNames.relic("Sticky Fire")

  /** p1 Raids p2, holding Sticky Fire faceup, and burns what the Raid's
    * own half left. p2's five board warbands lose two to the Raid and
    * three to the burn, so the two lines count different numbers: the
    * Campaign's loss line reads the leaf above it and no further.
    */
  def raidBurningWhatIsLeft(using munit.Location): Script =
    val (service, act) = atTable("sticky-fire", Table.start
      .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant")
      .boardWarbands(p1, 3).boardWarbands(p2, 5)
      .relic(p1, stickyFire)
      .favor(p1, 1)
      .banditsRefilled, Dice.raid)
    raidAnswers(act, {
      case park if park.decisionId == CampaignIds.attackerPlan &&
          offers(park, DecisionOptionRef.Relic(stickyFire)) =>
        ChooseOneAnswer(DecisionOptionRef.Relic(stickyFire))
      case park if park.decisionId == StickyFire.decisionId =>
        ChooseOneAnswer(StickyFire.yes)
    }).after(MenuPlay.start(p1, ActionRef.Campaign))
    Script("sticky-fire", service, p1)

  /** The scripts this file adds to the golden registry. */
  val named: Vector[(String, () => Script)] = Vector(
    "raid-spoils" -> (() => raidTakingRelicAndBanner),
    "conquest-placed" -> (() => conquestPlacingWarbands),
    "captains-raid" -> (() => CaptainsLogScripts.captainsRaidFromARuledSite),
    "captains-raid-lost" ->
      (() => CaptainsLogScripts.captainsRaidLostWithASplit))
