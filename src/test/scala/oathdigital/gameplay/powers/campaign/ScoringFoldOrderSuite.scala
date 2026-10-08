package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.model.AttackDieFace.{OneSword, TwoSwordsSkull}
import oathdigital.model.DefenseDieFace.{OneShield, TwoShields}
import oathdigital.testkit.CatalogNames

/** `inner` with its priority replaced, so its transforms fold elsewhere. */
private final class Reprioritised(inner: ContributingPower,
    override val priority: Int) extends ContributingPower:
  def id: PowerId = inner.id
  def source: RuleSourceRef = inner.source
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    inner.contributions
  override def noteKeys: Vector[NoteKey] = inner.noteKeys
  override def resolution: PowerResolution = inner.resolution

/** The scorers make the rescoring plans write the same total whatever order
  * their transforms fold in (catalog batch 4 design, "Verify at plan time",
  * "Fold order"). Transforms fold by priority first, so the same Campaign is
  * run again with every scoring plan's priority set to fold them in reverse
  * id order. Every plan in `AttackScore.changes` and `DefenseScore.changes`
  * writes its total through the scorer's `rescore`, so one mixed Campaign
  * per side checks the order independence they all rely on. */
class ScoringFoldOrderSuite extends munit.FunSuite:

  /** The production rules, but every plan the scorers know folds in the
    * reverse of its id order: the first id alphabetically folds last. */
  private def reversedRules(roll: WalkerDice): OathRules =
    val default = WalkerPowerCatalog.default(catalog)
    val scoring = (AttackScore.changes.map(_.ref) ++
      DefenseScore.changes.map(_.ref)).toSet
    val plans = default.powers.collect {
      case plan: BattlePlan if scoring.contains(plan.cardRef) => plan.id.value }
    assertEquals(plans.size, scoring.size,
      "each card the scorers know has one battle plan")
    val rank = plans.sorted.zipWithIndex.toMap
    val powers = default.powers.map(power => rank.get(power.id.value)
      .fold(power)(at => Reprioritised(power, plans.size - at)))
    new OathRules(catalog, walkerPowerCatalog = default.copy(powers = powers),
      walkerDice = roll)

  private def refOf(name: String): DecisionOptionRef =
    DecisionOptionRef.Denizen(CatalogNames.denizen(name))

  test("Lancers, Fearsome General and Outriders score the same attack in " +
      "either fold order"):
    val b = Vector("Lancers", "Fearsome General", "Outriders").foldLeft(
      board(warbands = 5, supply = 7))((held, name) =>
      withAdviser(held, name, Orientation.FaceUp))
    val roll = dice(attack = Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    def scored(game: OathRules): (Int, Int) =
      val done = Vector("Lancers", "Fearsome General", "Outriders")
        .foldLeft(commit(game, b, 3))((run, name) =>
          run.pick(b.actor, CampaignIds.attackerPlan, refOf(name)))
        .finishPlanWindows
      (done.result.attackScore, done.result.skullLosses)
    // Doubled and ignored: four skulls of four swords, and two swords.
    assertEquals(scored(rules(roll)), (18, 0))
    assertEquals(scored(reversedRules(roll)), (18, 0))

  test("Rain Boots, War Tortoise, Bag of Siegeworks and Garrison Armory " +
      "score the same defense in either fold order"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val attacker = withSecrets(withRelic(Vector("Rain Boots", "War Tortoise")
      .foldLeft(base)((held, name) =>
        withAdviser(held, name, Orientation.FaceUp)), "Bag of Siegeworks"), 1)
    val b = on(withAdviserFor(attacker, base.rival, "Garrison Armory",
      Orientation.FaceUp))(_.favor(base.actor, 1).favor(base.rival, 1))
    val roll = dice(attack = Vector.fill(5)(OneSword),
      defense = Vector(OneShield, TwoShields))
    val attackerPlans = Vector(refOf("Rain Boots"), refOf("War Tortoise"),
      DecisionOptionRef.Relic(CatalogNames.relic("Bag of Siegeworks")))
    def scored(game: OathRules): Int =
      val chosen = attackerPlans.foldLeft(commit(game, b, 5))((run, plan) =>
        run.pick(b.actor, CampaignIds.attackerPlan, plan))
      val attackDone =
        if awaits(chosen, b.actor, CampaignIds.attackerPlan) then
          chosen.pick(b.actor, CampaignIds.attackerPlan, CampaignIds.finish)
        else chosen
      attackDone.pick(b.rival, CampaignIds.defenderPlan,
        refOf("Garrison Armory")).finishPlanWindows.result.defenseScore
    // Both faces are ignored; the two warbands are added twice.
    assertEquals(scored(rules(roll)), 4)
    assertEquals(scored(reversedRules(roll)), 4)
