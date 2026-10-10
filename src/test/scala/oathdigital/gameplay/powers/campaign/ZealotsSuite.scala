package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Zealots: one favor placed; while the defending force is larger when it
  * is chosen, each sacrificed warband adds three. */
class ZealotsSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, TwoSwordsSkull}
  import DefenseDieFace.Blank

  private val card = CatalogNames.denizen("Zealots")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val source = PowerSourceRef.Card(card)
  // Ancient City's two blank defense dice and its two Bandits defend with 2.
  private val blanks = Vector(Blank, Blank)

  /** p1 attacks the origin, which 2 Bandits rule, with `warbands`
    * warbands, holding Zealots faceup and 1 favor. */
  private def attacker(warbands: Int): Board =
    val b = withAdviser(board(warbands = warbands, supply = 7), card,
      Orientation.FaceUp)
    on(b)(_.favor(b.actor, 1))

  /** The parked sacrifice question's heading. */
  private def sacrificeHeading(run: Run, b: Board): Option[String] =
    run.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount => amount.heading
      case _ => None

  /** Whether `run` recorded a sacrifice worth. */
  private def recordsWorth(run: Run): Boolean = run.ops.exists {
    case ModifyDicePool(pool, _, _) => pool == CampaignIds.sacrificeWorth
    case _ => false
  }

  test("with a larger defending force each sacrificed warband adds three"):
    val b = attacker(warbands = 1)
    val game = rules(dice(Vector(HollowSword), blanks))
    val asked = commit(game, b, 1)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(asked.state).adviserTokens(b.actor, card),
      Tokens(1, 0))
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(sacrificeHeading(asked, b),
      Some("Sacrifice up to 1 warband for three attack each"))
    val done = asked.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(done.result.sacrificeWorth, 3)
    // Half a sword scores nothing; the sacrifice alone beats the 2.
    assertEquals(done.result.attackTotal, 3)
    assertEquals(done.winner, Some(true))
    assertEquals(said(Zealots, done.events), Vector.empty)

  test("without a larger defending force it warns, and a sacrifice adds " +
      "one"):
    val b = attacker(warbands = 3)
    val run = commit(rules(dice(Vector.fill(3)(HollowSword), blanks)), b, 3)
    val warning = run.options(b.actor).collectFirst {
      case shown: DecisionOption.Warned if shown.ref == ref => shown.reason }
    assertEquals(warning, Some(OptionReason.Noted(Zealots.id,
      Zealots.noEffect(source))))
    assertEquals(NoteText.sentence(Zealots.noteKeys, Zealots.noEffect(source)),
      "Has no effect unless defending force is larger than yours")
    val done = run.pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
    assertEquals(done.result.sacrificeWorth, 1)
    // Three halves make one sword, and the sacrifice one more: a tie,
    // which the defender wins.
    assertEquals(done.result.attackTotal, 2)
    assertEquals(done.winner, Some(false))
    assert(!recordsWorth(done))

  test("a skull lost after it is chosen does not bring it into force"):
    // Two warbands against two Bandits is not larger. A skull then kills
    // one of the two, which does not make Zealots hold.
    val b = attacker(warbands = 2)
    val game = rules(dice(Vector(TwoSwordsSkull, HollowSword), blanks))
    val done = commit(game, b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(1))
    assertEquals(done.result.skullLosses, 1)
    assertEquals(done.result.sacrificeWorth, 1)
    assertEquals(done.result.attackTotal, 2 + 1)
