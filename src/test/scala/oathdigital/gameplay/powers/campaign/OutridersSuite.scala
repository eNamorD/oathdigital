package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Outriders' line (power log lines design, "Altered procedures"): "Skulls
  * ignored.", written only when the attack rolled a skull to ignore.
  */
class OutridersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.outriders")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val power = Outriders

  /** The attacker holds Outriders, Campaigns with two warbands, chooses it,
    * and rolls `attack`. */
  private def rolled(attack: Vector[AttackDieFace]): Run =
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val defense = Vector.fill(catalog.sites.find(_.id == b.origin).get.defense)(
      DefenseDieFace.Blank)
    commit(rules(dice(attack, defense)), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish

  private def said(run: Run): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, run.events)

  test("an attack roll with a skull writes that skulls were ignored"):
    assertEquals(said(rolled(Vector(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.OneSword))), Vector(NoteText.Said("ignored",
      "Skulls ignored.", covers = false)))

  test("a roll with no skull writes nothing"):
    assertEquals(said(rolled(Vector.fill(2)(AttackDieFace.OneSword))),
      Vector.empty)
