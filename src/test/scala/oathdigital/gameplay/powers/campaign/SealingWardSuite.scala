package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Sealing Ward: each of its holder's relics a Raid targets adds a defense
  * die, added when the pools are gathered. A Raid's defense pool holds 2 dice
  * for the defender's pawn and 2 for each relic targeted. */
class SealingWardSuite extends munit.FunSuite:
  private val ward = CatalogNames.denizen("Sealing Ward")
  /** Prints no battle plan, so the defender is offered none. */
  private val pig = CatalogNames.relic("Oracular Pig")
  /** Another faceup relic; its only power is an ACTION. */
  private val horse = CatalogNames.relic("Brass Horse")

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(SealingWard.id, Vector(SealingWard.added), run.events)

  test("each targeted relic of its holder adds a defense die"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    // The rival holds the Ward faceup, the Oracular Pig and the Brass Horse.
    val b = on(base)(_.adviser(base.rival, ward).relic(base.rival, pig)
      .relic(base.rival, horse))
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Relic(pig), DecisionOptionRef.Relic(horse)),
      raid = true)
    // The pawn and the two relics print 2 each; the Ward adds one per relic.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assertEquals(Look(run.state).diceInPool(CampaignIds.defensePool), 8)
    assertEquals(lines(run), Vector(NoteText.Said("added",
      "Added 2 more defense dice.", covers = false)))

  test("a Raid that targets none of its holder's relics adds nothing"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    // The rival holds the Ward faceup and the Oracular Pig, which is not
    // targeted.
    val b = on(base)(_.adviser(base.rival, ward).relic(base.rival, pig))
    val run = commit(rules(winningDice), b, 4, raid = true)
    // Only the pawn's 2 dice.
    assertEquals(Look(run.state).diceInPool(CampaignIds.defensePool), 2)
    assertEquals(lines(run), Vector.empty)

  test("a facedown Sealing Ward adds nothing"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    // The rival holds the Ward facedown and the Oracular Pig, which is
    // targeted.
    val b = on(base)(_.adviser(base.rival, ward, facedown = true)
      .relic(base.rival, pig))
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(pig)), raid = true)
    // The pawn's 2 dice and the Pig's 2.
    assertEquals(Look(run.state).diceInPool(CampaignIds.defensePool), 4)
    assertEquals(lines(run), Vector.empty)
