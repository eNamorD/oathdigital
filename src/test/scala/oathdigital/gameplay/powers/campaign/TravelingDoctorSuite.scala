package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Traveling Doctor: Billowing Fog's rule, free, and discarded after a
  * Campaign its user lost. A bandit defender applies it and gains nothing
  * from it. */
class TravelingDoctorSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Traveling Doctor")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(TravelingDoctor.id,
      Vector(SparedForce.spared, PlanDiscard.discarded), run.events)

  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  test("a defeated attacker loses no survivor, and the Doctor is discarded"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(base, card, Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).player(b.actor).board.warbands, 5)
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("spared",
      s"${b.actor.value}'s force was spared.", covers = false), discarded))

  test("a victorious user keeps the Doctor"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(base, card, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(Look(done.state).player(b.actor).advisers.exists(_.id == card))
    assertEquals(lines(done), Vector.empty)

  test("a bandit defender applies it, loses every targeted warband, and " +
      "discards it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(done.ops.contains(Kill(Piece.Warbands(ForceKind.Bandit, 2),
      PositionedLocation(Location.Site(b.origin)))))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(discarded))
