package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Rusting Ray: a defender's plan, a secret placed, offered while its user
  * holds the Darkest Secret, that scores the attacker's hollow swords as
  * nothing. */
class RustingRaySuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Rusting Ray")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val ignored = NoteText.Said("ignored", "Hollow swords ignored.",
    covers = false)

  /** p2 rules the origin, holds the Oathkeeper title, Rusting Ray faceup and
    * one faceup secret; `darkest` says whether p2 also holds the Darkest
    * Secret. */
  private def defender(darkest: Boolean): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val armed = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1))
    if darkest then on(armed)(_.darkestSecret(Some(base.rival), secrets = 1))
    else armed

  test("the defender places a secret, off turn, and the attacker's hollow " +
      "swords score nothing"):
    val b = defender(darkest = true)
    val facedown = b.player(b.rival).board.faceDownSecrets
    val picked = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    // Paid outside the defender's turn, the secret flips facedown at once.
    assertEquals(Look(picked.state).adviserTokens(b.rival, card), Tokens.empty)
    assertEquals(Look(picked.state).player(b.rival).board.faceDownSecrets,
      facedown + 1)
    val done = picked.finishPlanWindows
    // Four hollow swords would score 2.
    assertEquals(done.result.attackScore, 0)
    assertEquals(said(RustingRay, done.events), Vector(ignored))

  test("with no hollow sword rolled it writes nothing"):
    val b = defender(darkest = true)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 4)
    assertEquals(said(RustingRay, done.events), Vector.empty)

  test("it is not offered while its user lacks the Darkest Secret"):
    val b = defender(darkest = false)
    // With no attacker plan the attacker's window is skipped; the title's
    // plan keeps the defender's open.
    val run = commit(rules(losingDice), b, 4)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))

  test("with Yew Staff the defender uses it without the Darkest Secret"):
    val base = defender(darkest = false)
    val b = on(base)(_.relic(base.rival, "Yew Staff"))
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 0)
    assertEquals(said(RustingRay, done.events), Vector(ignored))
