package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.{WalkerDice, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Book Burning: a free attacker's plan, offered only in a Raid. If the
  * attacker wins, every secret on the defender's board is burnt but one, the
  * facedown ones first, turned faceup to be burnt. */
class BookBurningSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Book Burning")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val honors = CatalogNames.denizen("Battle Honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(honors)

  /** The other player stands at the origin with three warbands on their board
    * and the given secrets. The attacker holds Book Burning and four warbands.
    */
  private def raid(faceUp: Int, faceDown: Int): Board =
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    withAdviser(on(base)(_.boardWarbands(base.rival, 3)
      .secrets(base.rival, faceUp = faceUp, faceDown = faceDown)),
      card, Orientation.FaceUp)

  /** A Raid committing 4 warbands with Book Burning chosen, played to its
    * end: every plan window finished, and a beaten defender's pawn moved to
    * the first site offered. */
  private def playedRaidWithBookBurning(b: Board, dice: WalkerDice): Run =
    val run = commit(rules(dice), b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    if awaits(run, b.actor, CampaignIds.relocation) then
      run.pick(b.actor, CampaignIds.relocation, run.offered(b.actor).head)
        .finishPlanWindows
    else run

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    BookBurning.id, Vector(BookBurning.burned, BookBurning.none), run.events)

  test("a won Raid burns every secret but one, the facedown ones first, and " +
      "says so"):
    val b = raid(faceUp = 2, faceDown = 2)
    val done = playedRaidWithBookBurning(b, winningDice)
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 0)
    assertEquals(lines(done), Vector(NoteText.Said("burned",
      s"Burned 3 secrets from ${b.rival.value}'s board.", covers = false)))

  test("facedown secrets are burnt by name, and the one left stays " +
      "facedown"):
    val b = raid(faceUp = 0, faceDown = 3)
    val done = playedRaidWithBookBurning(b, winningDice)
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 0)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 1)
    val burnt = done.events.collect {
      case step: WalkerStepRecorded if step.ops.exists {
          case burn: Burn => burn.resource.isInstanceOf[Piece.Secrets]
          case _ => false } => step.ops }
    assertEquals(burnt, Vector(Vector[CoreOperation](
      Burn.secrets(2, PositionedLocation(Location.PlayArea(b.rival),
        side = Some(SecretSide.FaceDown))))))

  test("a defender with one secret loses nothing, and the line says there " +
      "was none to burn"):
    val b = raid(faceUp = 1, faceDown = 0)
    val done = playedRaidWithBookBurning(b, winningDice)
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 0)
    assertEquals(lines(done), Vector(NoteText.Said("none",
      s"${b.rival.value} had no secret to burn.", covers = false)))

  test("a lost Raid burns nothing and writes nothing"):
    val b = raid(faceUp = 2, faceDown = 2)
    val done = playedRaidWithBookBurning(b, losingDice)
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 2)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 2)
    assertEquals(lines(done), Vector.empty)

  test("choosing it costs nothing and changes no dice"):
    val b = raid(faceUp = 2, faceDown = 0)
    val run = commit(rules(winningDice), b, 4, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[ModifyDicePool]))

  test("it is not offered in a Conquest"):
    val advised = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val b = withAdviser(advised, honors, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))
