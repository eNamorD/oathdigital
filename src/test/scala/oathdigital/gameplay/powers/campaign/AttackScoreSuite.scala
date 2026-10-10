package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.CampaignBattle
import oathdigital.model.{AttackDieFace, CampaignPlanSide, DecisionOptionRef}
import oathdigital.model.AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}
import oathdigital.testkit.CatalogNames

/** The attack scorer's steps (catalog batch 4 rulings, "The attack score"):
  * face values, Lancers' doubling, the skull cap, War Tortoise, the total.
  * Each plan's rules come from `AttackScore.changes`, the list a Campaign
  * reads, so a card mapped to the wrong rules or the wrong side fails here. */
class AttackScoreSuite extends munit.FunSuite:
  private final case class Plan(side: CampaignPlanSide, ref: DecisionOptionRef)
  private def attacker(name: String)(using munit.Location): Plan =
    Plan(CampaignPlanSide.Attacker,
      DecisionOptionRef.Denizen(CatalogNames.denizen(name)))
  private def defender(name: String)(using munit.Location): Plan =
    Plan(CampaignPlanSide.Defender,
      DecisionOptionRef.Denizen(CatalogNames.denizen(name)))

  private def relicDefender(name: String)(using munit.Location): Plan =
    Plan(CampaignPlanSide.Defender,
      DecisionOptionRef.Relic(CatalogNames.relic(name)))

  /** The rules once `plans` are chosen, folded from `AttackScore.changes` as
    * a Campaign folds them; fails if the scorer does not know a plan. */
  private def chosen(plans: Plan*)(using munit.Location): AttackRules =
    val changes = AttackScore.changes.filter(change =>
      plans.contains(Plan(change.side, change.ref)))
    assertEquals(changes.map(change => Plan(change.side, change.ref)).toSet,
      plans.toSet, "the attack scorer knows each plan")
    changes.foldLeft(AttackRules())((rules, change) => change.change(rules))

  private def assertScores(faces: Vector[AttackDieFace], force: Int,
      rules: AttackRules)(score: Int, lost: Int)(using munit.Location): Unit =
    assertEquals(AttackScore.of(faces, force, rules), (score, lost))

  test("with no plan chosen it scores as the printed rules do"):
    val rolls = Vector(Vector(OneSword, HollowSword, HollowSword, HollowSword),
      Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), Vector(TwoSwordsSkull),
      Vector.empty)
    for faces <- rolls; force <- 0 to 3 do
      assertEquals(AttackScore.of(faces, force, chosen()),
        CampaignBattle.attackResult(faces, force, ignoreSkulls = false))

  test("a hollow sword is worth half, a whole sword with Fae Battalion, and " +
      "nothing with the defender's Rusting Ray"):
    val faces = Vector.fill(3)(HollowSword)
    assertScores(faces, 3, chosen())(score = 1, lost = 0)
    assertScores(faces, 3, chosen(attacker("Fae Battalion")))(score = 3,
      lost = 0)
    assertScores(faces, 3, chosen(defender("Rusting Ray")))(score = 0,
      lost = 0)

  test("Lancers doubles every face, skulls included, before the skull cap"):
    // Two hollow swords make one sword, two swords, and two payable skulls
    // of two swords each.
    assertScores(Vector(HollowSword, OneSword, TwoSwordsSkull), 5,
      chosen(attacker("Lancers")))(score = 1 + 2 + 4, lost = 2)

  test("the rulings' example: Lancers and Fearsome General with a force of 3"):
    // Doubled: four skulls and two swords. Three skulls are payable and score
    // 12, the fourth scores 0, the swords add 2.
    assertScores(Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), 3,
      chosen(attacker("Lancers"), attacker("Fearsome General")))(score = 14,
      lost = 3)

  test("an unpayable skull scores nothing, with Fearsome General too"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull)
    assertScores(faces, 1, chosen())(score = 2, lost = 1)
    assertScores(faces, 1, chosen(attacker("Fearsome General")))(score = 4,
      lost = 1)

  test("with Outriders, The Great Levy, Kindred Warriors or Rangers a skull " +
      "scores its swords and kills nothing, four with Fearsome General"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull)
    for name <- Vector("Outriders", "The Great Levy", "Kindred Warriors",
        "Rangers") do
      assertScores(faces, 1, chosen(attacker(name)))(score = 4, lost = 0)
    assertScores(faces, 1,
      chosen(attacker("Outriders"), attacker("Fearsome General")))(score = 8,
      lost = 0)

  test("the defender's War Tortoise scores every skull face as nothing, " +
      "Fearsome General's included, and payable skulls still kill"):
    assertScores(Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword), 1,
      chosen(defender("War Tortoise"), attacker("Fearsome General")))(
      score = 1, lost = 1)
    assertScores(Vector(TwoSwordsSkull, OneSword), 2,
      chosen(defender("War Tortoise"), attacker("Outriders")))(score = 1,
      lost = 0)

  test("with the defender's Weeping Banner each skull kills two, capped by " +
      "the force, and scores only when both are paid"):
    val faces = Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword)
    val banner = relicDefender("Weeping Banner")
    assertScores(faces, 4, chosen(banner))(score = 1 + 4, lost = 4)
    // An odd force: the last warband dies and its skull scores nothing.
    assertScores(faces, 3, chosen(banner))(score = 1 + 2, lost = 3)
    assertScores(faces, 3, chosen(banner, attacker("Fearsome General")))(
      score = 1 + 4, lost = 3)
    assertScores(faces, 3, chosen(banner, attacker("Outriders")))(
      score = 1 + 4, lost = 0)
