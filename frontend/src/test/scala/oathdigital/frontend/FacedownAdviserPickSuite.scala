package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one}
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent, WalkerStartArgWire}
import oathdigital.testkit.Unwrap.as
import org.scalajs.dom

/** "Play facedown adviser" lights the player's facedown advisers on their
  * board before the walker starts; a click picks one, and the panel plays
  * it by name or cancels.
  */
class FacedownAdviserPickSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private def facedown(id: String, name: String) = CardDetails(id, "denizen",
    name, suit = Some("order"), orientation = Some("face-down"))
  private val oak = facedown("a1", "Old Oak")
  private val bandits = facedown("a2", "Bandits")
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val table = TestProjections.actingProjection(players = Vector(red))
    .copy(playerBoards = Vector(PlayerBoard("red", warbands = 3, favor = 0,
      faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
      totalSecrets = 1, supply = 7, pawnSiteId = None,
      advisers = Vector(oak, bandits), relics = Vector.empty,
      revealedVision = None)))
  private val draft = FacedownAdviserDraft(context,
    Vector(MinorAdviser(oak), MinorAdviser(bandits)), None)

  private def boards(value: FacedownAdviserDraft,
      ui: RecordingControls): dom.Element =
    WorldBoardRenderer.playerBoards(table, "red",
      BoardOptions.facedown(value, canControl = true, ui))

  private def panel(value: FacedownAdviserDraft,
      ui: RecordingControls): dom.Element =
    FacedownAdviserRenderer.render(value, canControl = true, ui)

  test("the player's facedown advisers are lit on their board, and a " +
      "click picks one"):
    val ui = new RecordingControls()
    val root = boards(draft, ui)
    assertEquals(all(root, ".board-cards .board-option")
      .map(_.getAttribute("data-option-ref")),
      Vector("denizen:a1", "denizen:a2"))
    click(one(root, """[data-option-ref="denizen:a2"]"""))
    assertEquals(ui.chosenFacedown, Vector("a2"))

  test("the picked adviser is selected, and the panel plays it by name"):
    val ui = new RecordingControls()
    val picked = draft.choose("a2")
    assert(one(boards(picked, ui), """[data-option-ref="denizen:a2"]""")
      .classList.contains("board-option-selected"))
    val start = as[dom.html.Button](one(panel(picked, ui),
      ".facedown-adviser-start"))
    assertEquals(start.textContent, "Play Bandits")
    start.click()
    assertEquals(ui.targetCommands, Vector(Intent.StartWalker(
      "play-facedown-adviser", Vector.empty,
      Vector(WalkerStartArgWire("denizen", "a2")))))

  test("with no pick the panel asks for one on the board, and Cancel " +
      "leaves"):
    val ui = new RecordingControls()
    val shown = panel(draft, ui)
    assertEquals(all(shown, ".facedown-adviser-start"), Vector.empty)
    assertEquals(one(shown, ".decision-instruction").textContent,
      "Choose a facedown adviser on your board.")
    assertEquals(all(shown, ".card-face"), Vector.empty)
    as[dom.html.Button](one(shown, ".modifier-cancel")).click()
    assertEquals(ui.cancelledTargets, 1)

  test("a lone facedown adviser is picked already"):
    val minor = MinorActionsState(Vector(MinorAdviser(oak)),
      canPeekSiteRelics = false, facedownRelics = Vector.empty, siteId = None,
      maxBoardToSite = 0, maxSiteToBoard = 0)
    assertEquals(FacedownAdviserDraft.initial(context, minor)
      .flatMap(_.selectedCardId), Some("a1"))
