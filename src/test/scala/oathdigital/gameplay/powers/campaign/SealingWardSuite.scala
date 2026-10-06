package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Sealing Ward: each of its holder's relics a Raid targets adds a defense
  * die, added when the pools are gathered. */
class SealingWardSuite extends munit.FunSuite:
  private val ward = CatalogNames.denizen("Sealing Ward")
  /** A second faceup relic for the rival; its only power is an ACTION. */
  private val horse = CatalogNames.relic("Brass Horse")

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(SealingWard.id, Vector(SealingWard.added), run.events)

  private def defenseDice(run: Run): Int = ready(run.state).game.current
    .rollPools.get(CampaignIds.defensePool).fold(0)(_.count)

  /** The Raid board, with the rival holding Sealing Ward on `orientation`
    * and Brass Horse faceup. */
  private def wardedRaidBoard(orientation: Orientation): RaidBoard =
    val raid = raidBoard()
    raid.copy(board = withRelicFor(withAdviserFor(raid.board,
      raid.board.rival, ward, orientation), raid.board.rival, horse))

  test("each targeted relic of its holder adds a defense die"):
    val RaidBoard(b, relic) = wardedRaidBoard(Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Relic(relic), DecisionOptionRef.Relic(horse)),
      raid = true)
    // The pawn and the two relics print 2 each; the Ward adds one per relic.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assertEquals(defenseDice(run), 8)
    assertEquals(lines(run), Vector(NoteText.Said("added",
      "Added 2 more defense dice.", covers = false)))

  test("a Raid that targets none of its holder's relics adds nothing"):
    val RaidBoard(b, _) = wardedRaidBoard(Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4, raid = true)
    assertEquals(defenseDice(run), 2)
    assertEquals(lines(run), Vector.empty)

  test("a facedown Sealing Ward adds nothing"):
    val RaidBoard(b, relic) = wardedRaidBoard(Orientation.FaceDown)
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(relic)), raid = true)
    assertEquals(defenseDice(run), 4)
    assertEquals(lines(run), Vector.empty)
