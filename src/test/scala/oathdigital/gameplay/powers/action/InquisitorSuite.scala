package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class InquisitorSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val inquisitor = CatalogNames.denizen("Inquisitor")
  private val source = DecisionOptionRef.Denizen(inquisitor)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Hearth denizen. */
  private val landWarden = CatalogNames.denizen("Land Warden")
  /** p2's Vision of Faith, its only adviser on the staged table. */
  private val faithSlot = DecisionOptionRef.AdviserSlot(p2, 0)

  /** p1's Act at Ancient City beside Inquisitor, with the faceup secret that
    * pays it. p3 stands at Buried Giant. */
  private def atHome: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).pawn(p3, at = "Buried Giant")
    .denizen(inquisitor, at = home).secrets(p1, faceUp = 1)

  /** `atHome`, with p2 there too, holding the Vision of Faith facedown. */
  private def staged: Table = atHome.pawn(p2, at = home)
    .adviser(p2, VisionRules.Faith, facedown = true)

  /** The walks Inquisitor started, chose p2's Vision in, and guessed in. */
  private case class Guessed(started: OathTransition, chosen: OathTransition,
      guessed: OathTransition):
    def events: Vector[OathEvent] =
      started.events ++ chosen.events ++ guessed.events

  /** Whether `viewer`'s knowledge records `vision`, as a peek or a card
    * shown to the table records a card in a pile. */
  private def knowsVision(ready: ReadyGame, viewer: PlayerId,
      vision: VisionId): Boolean =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty).contains(vision)

  private def guessP2sVision(ready: ReadyGame, vision: VisionId): Guessed =
    val started = accepted(use(ready, Inquisitor, source))
    val chosen = accepted(answer(started, p1, Inquisitor.visionDecisionId,
      pick(faithSlot)))
    Guessed(started, chosen, accepted(answer(chosen, p1,
      Inquisitor.guessDecisionId, pick(Inquisitor.guess(vision)))))

  test("it places a secret and offers the facedown Visions of players at " +
      "the site by adviser slot, the actor's own included"):
    val ready = atHome
      .adviser(p1, VisionRules.Conspiracy, facedown = true)
      .pawn(p2, at = home).adviser(p2, landWarden)
      .adviser(p2, VisionRules.Faith, facedown = true)
      .adviser(p3, VisionRules.Rebellion, facedown = true).ready
    val t = accepted(use(ready, Inquisitor, source))
    assert(awaits(t, Inquisitor.visionDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector(
      "adviser-slot" -> s"${p1.value}:0", "adviser-slot" -> s"${p2.value}:1")))
    assertEquals(Look(after(t)).tokensOn(inquisitor), Tokens(0, 1))

  test("then it offers every Vision name, the Conspiracy included"):
    val started = accepted(use(staged.ready, Inquisitor, source))
    val chosen = accepted(answer(started, p1, Inquisitor.visionDecisionId,
      pick(faithSlot)))
    assert(awaits(chosen, Inquisitor.guessDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assertEquals(queryOf(chosen, p1).map(_.offeredOptions.map(_.label)),
      Some(Inquisitor.visions.map((_, name) => name)))

  test("a right guess discards the Vision to the next region's pile, every " +
      "player knows it there, and the line names it"):
    val ready = staged.ready
    val walked = guessP2sVision(ready, VisionRules.Faith)
    assert(backToActing(walked.guessed))
    val end = Look(after(walked.guessed))
    assertEquals(end.advisers(p2), Vector.empty[CardId])
    assertEquals(end.discardAfter(home).lastOption, Some(VisionRules.Faith))
    assertEquals(Vector(p1, p2, p3).map(knowsVision(after(walked.guessed), _,
      VisionRules.Faith)), Vector(true, true, true))
    assertEquals(NoteText.said(Inquisitor, walked.guessed.events), Vector(
      NoteText.Said("used.vision-of-faith.right",
        s"Guessed Vision of Faith and discarded ${VisionRules.Faith.value} " +
          s"from ${p2.value}.", covers = true)))
    assertEquals(replayed(ready, walked.events), Right(walked.guessed.state))
    assertWireRoundTrips(walked.events)

  test("a wrong guess changes nothing, and the line says so"):
    val walked = guessP2sVision(staged.ready, VisionRules.Conquest)
    assert(backToActing(walked.guessed))
    assertEquals(Look(after(walked.guessed)).advisers(p2),
      Vector[CardId](VisionRules.Faith))
    assert(!knowsVision(after(walked.guessed), p1, VisionRules.Faith))
    assertEquals(NoteText.said(Inquisitor, walked.guessed.events), Vector(
      NoteText.Said("used.vision-of-conquest.wrong",
        s"Guessed Vision of Conquest for ${p2.value}'s Vision, wrongly.",
        covers = true)))

  test("with no facedown Vision at the site it asks nothing, and the line " +
      "says so"):
    val ready = atHome.pawn(p2, at = "Broken Peaks")
      .adviser(p3, VisionRules.Faith, facedown = true).ready
    val done = accepted(use(ready, Inquisitor, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(Inquisitor, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        "No player at the site held a facedown Vision.", covers = true)))
