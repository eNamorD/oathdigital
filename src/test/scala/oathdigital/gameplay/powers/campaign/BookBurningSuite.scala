package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.{WalkerDice, WalkerStepRecorded}
import oathdigital.model._

/** Book Burning: a free attacker's plan, offered only in a Raid. If the attacker
  * wins, every secret on the defender's board is burnt but one, the facedown
  * ones first, turned faceup to be burnt.
  */
class BookBurningSuite extends munit.FunSuite:
  private val card = cardWith("denizen.book-burning")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))

  /** The other player stands at the origin with three warbands on their board
    * and the given secrets. The attacker holds Book Burning and four warbands.
    */
  private def raid(faceUp: Int, faceDown: Int): Board =
    val base = withEnemyAtOrigin(board(warbands = 4))
    withAdviser(on(base)(_.warbands(base.other, 3)
      .secrets(base.other, faceUp = faceUp, faceDown = faceDown)),
      card, Orientation.FaceUp)

  /** A Raid with Book Burning chosen, played to its end. A beaten defender's
    * pawn goes to the first site offered. */
  private def burned(b: Board, dice: WalkerDice): Run =
    val run = commit(rules(dice), b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    if awaits(run, b.actor, CampaignIds.relocation) then
      run.pick(b.actor, CampaignIds.relocation, run.offered(b.actor).head).finish
    else run

  private def secrets(run: Run, who: PlayerId): (Int, Int) =
    val held = player(run.state, who).board
    held.faceUpSecrets -> held.faceDownSecrets

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    BookBurning.id, Vector(BookBurning.burned, BookBurning.none), run.events)

  test("a won Raid burns every secret but one, the facedown ones first, and says so"):
    val b = raid(faceUp = 2, faceDown = 2)
    val done = burned(b, winning)
    assertEquals(winner(done), Some(true))
    assertEquals(secrets(done, b.other), (1, 0))
    assertEquals(lines(done), Vector(NoteText.Said("burned",
      s"Burned 3 secrets from ${b.other.value}'s board.", covers = false)))

  test("facedown secrets are turned faceup and burnt in one step, and the one left stays facedown"):
    val b = raid(faceUp = 0, faceDown = 3)
    val done = burned(b, winning)
    assertEquals(secrets(done, b.other), (0, 1))
    val flipped = done.events.collect {
      case step: WalkerStepRecorded
          if step.ops.exists(_.isInstanceOf[FlipSecrets]) => step.ops }
    assertEquals(flipped, Vector(Vector[CoreOperation](
      FlipSecrets(b.other, 2, SecretSide.FaceDown, SecretSide.FaceUp),
      Burn.secrets(2, PositionedLocation(Location.PlayArea(b.other))))))

  test("a defender with one secret loses nothing, and the line says there was none to burn"):
    val b = raid(faceUp = 1, faceDown = 0)
    val done = burned(b, winning)
    assertEquals(winner(done), Some(true))
    assertEquals(secrets(done, b.other), (1, 0))
    assertEquals(lines(done), Vector(NoteText.Said("none",
      s"${b.other.value} had no secret to burn.", covers = false)))

  test("a lost Raid burns nothing and writes nothing"):
    val b = raid(faceUp = 2, faceDown = 2)
    val done = burned(b, losing)
    assertEquals(winner(done), Some(false))
    assertEquals(secrets(done, b.other), (2, 2))
    assertEquals(lines(done), Vector.empty)

  test("choosing it costs nothing and changes no dice"):
    val b = raid(faceUp = 2, faceDown = 0)
    val run = commit(rules(winning), b, 4, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[ModifyDicePool]))

  test("it is not offered in a Conquest"):
    val b = withAdviser(withAdviser(board(), card, Orientation.FaceUp), honors,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))
