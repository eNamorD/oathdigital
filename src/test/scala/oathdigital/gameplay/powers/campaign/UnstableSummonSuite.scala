package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Unstable Summon: a free attacker's plan that burns X secrets from the
  * Darkest Secret its user holds for X attack dice. It is the first plan
  * that asks an amount (catalog batch 7, slice 3). */
class UnstableSummonSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Unstable Summon")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val onBanner =
    PositionedLocation(Location.OnBanner(Banner.DarkestSecret))

  private def lines(run: Run): Vector[NoteText.Said] =
    said(UnstableSummon, run.events)

  private def burned(text: String): NoteText.Said = NoteText.Said("burned",
    s"Burned $text.", covers = false)

  /** p1 attacks the bandit-ruled origin holding Unstable Summon faceup; the
    * Darkest Secret, with `bannerSecrets` on it, is held by p1, or by p2
    * when `attackerHoldsBanner` is false. */
  private def attackerWithSummon(bannerSecrets: Int,
      attackerHoldsBanner: Boolean = true): Board =
    val base = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    on(base)(_.darkestSecret(Some(
      if attackerHoldsBanner then base.actor else base.rival), bannerSecrets))

  test("the attacker burns every secret on the Darkest Secret for as many " +
      "attack dice, for nothing"):
    val b = attackerWithSummon(bannerSecrets = 3)
    val run = commit(rules(winningDice), b, 2)
    assert(run.offered(b.actor).contains(ref))
    assert(!run.options(b.actor).exists {
      case priced: DecisionOption.Priced => priced.ref == ref
      case _ => false
    })
    val asked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(awaits(asked, b.actor, UnstableSummon.decisionId))
    assertEquals(asked.query(b.actor), DecisionQuery.ChooseAmount(1, 3,
      Some(UnstableSummon.heading), UnstableSummon.confirm))
    val done = asked.answer(b.actor, UnstableSummon.decisionId,
      ChooseAmountAnswer(3))
    assertEquals(Look(done.state).secretsOnDarkestSecret, 0)
    // A burn is recorded as the move it is made of.
    assert(done.since(asked).contains(Burn.secrets(3, onBanner, None).move))
    assert(done.since(asked).contains(
      ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!done.since(run).exists(_.isInstanceOf[PayCost]))
    assertEquals(lines(done), Vector(burned(
      "3 secrets from the Darkest Secret for 3 attack dice")))

  test("X may be as low as one, leaving the rest on the banner"):
    val b = attackerWithSummon(bannerSecrets = 3)
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, UnstableSummon.decisionId, ChooseAmountAnswer(1))
    assertEquals(Look(done.state).secretsOnDarkestSecret, 2)
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assertEquals(lines(done), Vector(burned(
      "1 secret from the Darkest Secret for 1 attack die")))

  test("without the Darkest Secret, or with no secret on it, it is shown " +
      "unavailable"):
    // Longbows keeps the attacker's plan decision asked.
    for b <- Vector(attackerWithSummon(3, attackerHoldsBanner = false),
        attackerWithSummon(0)) do
      val asked = withAdviser(b, "Longbows", Orientation.FaceUp)
      assertEquals(commit(rules(winningDice), asked, 2).withheld(b.actor,
        ref), Some(OptionReason.Written(UnstableSummon.noBanner)))
