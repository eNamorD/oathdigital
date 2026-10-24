package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerProcedureRegistry,
  WalkerRollFeedback}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.CatalogNames

/** Master at Arms (card 249): a free attacker battle plan. Once chosen,
  * after the attack roll its user may reroll any number of the dice showing
  * hollow swords, once (catalog batch 7, slice 1). */
class MasterAtArmsSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}
  import DefenseDieFace.Blank

  private val card = CatalogNames.denizen("Master at Arms")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val jinx = CatalogNames.denizen("Jinx")
  private val jinxRef: DecisionOptionRef = RerollChoice.optionRef(jinx)
  private val rerollRef: DecisionOptionRef = RerollChoice.optionRef(card)
  private val reroll = RerollChoice.decisionId(CampaignIds.attackPool, 1)

  private def rerolled(text: String): NoteText.Said =
    NoteText.Said("rerolled", s"Rerolled $text.", covers = false)

  /** p1 attacks the bandit-ruled origin with 3 warbands, holding Master at
    * Arms faceup. */
  private def attacker: Board =
    withAdviser(board(warbands = 3, supply = 7), card, Orientation.FaceUp)

  /** The attacker chooses Master at Arms and finishes the plan windows. */
  private def chosen(b: Board, dice: Vector[DieFace]*): Run =
    commit(rules(scripted(dice*)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows

  test("its user rerolls as many hollow swords as they choose"):
    val b = attacker
    val asked = chosen(b, Vector(HollowSword, OneSword, HollowSword),
      Vector(TwoSwordsSkull), Vector(Blank, Blank))
    assert(awaits(asked, b.actor, reroll))
    val amount = asked.pick(b.actor, reroll, rerollRef)
    assert(awaits(amount, b.actor, MasterAtArms.decisionId(1)))
    val done = amount.answer(b.actor, MasterAtArms.decisionId(1),
      ChooseAmountAnswer(1)).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](TwoSwordsSkull, OneSword, HollowSword))
    assertEquals(said(MasterAtArms.forCatalog(catalog), done.events),
      Vector(rerolled("1 hollow sword")))

  test("its reroll is a button naming the hollow swords, with no cost"):
    val b = attacker
    val asked = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(Blank, Blank))
    assertEquals(asked.options(b.actor), Vector(
      DecisionOption.Button(RerollChoice.optionRef(card),
        "Reroll any [hollow-sword] with Master at Arms"),
      DecisionOption.Button(RerollChoice.keep, RerollChoice.keepLabel)))

  test("the how-many question shows the attack dice"):
    val b = attacker
    val amount = chosen(b, Vector(HollowSword, OneSword, HollowSword),
      Vector(TwoSwordsSkull), Vector(Blank, Blank))
      .pick(b.actor, reroll, rerollRef)
    assert(awaits(amount, b.actor, MasterAtArms.decisionId(1)))
    assertEquals(WalkerProcedureRegistry.rollFeedback(ActionRef.Campaign,
      catalog, ready(amount.state), b.actor, MasterAtArms.decisionId(1)),
      Some(WalkerRollFeedback(CampaignIds.attackPool)))

  test("with one hollow sword it rerolls it without asking"):
    val b = attacker
    val done = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(OneSword), Vector(Blank, Blank))
      .pick(b.actor, reroll, rerollRef).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword, OneSword))

  test("with no hollow sword nothing is asked"):
    val b = attacker
    val done = chosen(b, Vector(OneSword, OneSword, OneSword),
      Vector(Blank, Blank))
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword, OneSword))

  test("unchosen, it offers no reroll"):
    val b = attacker
    val done = commit(rules(scripted(Vector.fill(3)(HollowSword),
      Vector(Blank, Blank))), b, 3).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector.fill[AttackDieFace](3)(HollowSword))

  test("Jinx and Master at Arms both reroll one throw, Jinx first"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 1)
    val done = chosen(b, Vector.fill(3)(OneSword),
      Vector(HollowSword, OneSword, OneSword), Vector(OneSword),
      Vector(Blank, Blank))
      .pick(b.actor, reroll, jinxRef)
      .pick(b.actor, reroll, rerollRef).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector.fill[AttackDieFace](3)(OneSword))

  test("Jinx and Master at Arms both reroll one throw, Master at Arms " +
      "first"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 1)
    val withMaster = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(HollowSword), Vector(OneSword, OneSword, OneSword),
      Vector(Blank, Blank)).pick(b.actor, reroll, rerollRef)
    // Master at Arms is used: only Jinx and "Keep the roll" are left.
    assertEquals(withMaster.offered(b.actor), Vector(jinxRef,
      RerollChoice.keep))
    val done = withMaster.pick(b.actor, reroll, jinxRef).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector.fill[AttackDieFace](3)(OneSword))

  test("without a hollow sword it is shown unavailable beside Jinx"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 1)
    val asked = chosen(b, Vector.fill(3)(OneSword), Vector(Blank, Blank))
    assertEquals(asked.withheld(b.actor, rerollRef),
      Some(OptionReason.Written(MasterAtArms.noHollowSword)))

  test("Jinx without a secret is shown unavailable beside it"):
    val b = withSecrets(withAdviser(attacker, jinx, Orientation.FaceUp),
      faceUp = 0)
    val asked = chosen(b, Vector(HollowSword, OneSword, OneSword),
      Vector(Blank, Blank))
    assertEquals(asked.withheld(b.actor, jinxRef),
      Some(OptionReason.Written("Needs 1 secret; you have 0")))
