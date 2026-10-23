package oathdigital.frontend

import oathdigital.frontend.Dom.{all, click, one}
import oathdigital.frontend.TestBoards.*
import oathdigital.model.PlayerColor
import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent}

/** "Play facedown adviser" starts the walker with no card. The walk's first
  * decision is the pick, answered on the player's board like any board
  * decision (undo design, "The facedown-adviser pick").
  */
class FacedownAdviserPickSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private def facedown(id: String, name: String) = CardDetails(id, "denizen",
    name, suit = Some("order"), orientation = Some("face-down"))
  private val oak = facedown("a1", "Old Oak")
  private val bandits = facedown("a2", "Bandits")
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private def option(card: CardDetails) =
    DecisionOptionState("denizen", card.cardId, card.name, card = Some(card))

  /** The pick as the server parks it, over `cards`. */
  private def pick(cards: CardDetails*) = WalkerDecisionState(
    "play-facedown-adviser", BoardSelectionDraft.facedownAdviserDecisionId,
    query = Some(DecisionQueryState.ChooseOne(
      cards.toVector.map(option), Some("Choose a facedown adviser to play"))),
    presentation = "board")

  /** Red's Act table with `cards` facedown on her board and `parked`
    * waiting on her. */
  private def table(parked: Option[WalkerDecisionState],
      cards: CardDetails*): GameProjection =
    TestProjections.actingProjection(players = Vector(red)).copy(
      playerBoards = Vector(quietBoard("red").withAdvisers(cards*)),
      walkerDecision = parked)

  test("the parked pick lights the facedown advisers, and a click stages one"):
    val ui = new RecordingControls()
    val parked = table(Some(pick(oak, bandits)), oak, bandits)
    val surface = ParkedDecision.route(parked,
        ServerUiSupport.viewerPresentation(parked, "red")).surface match
      case Some(board: ParkedDecision.Surface.BoardDecision) => board
      case other => fail(s"expected a board decision, got $other")
    val drafts = SessionDrafts.empty.reconcile(context, parked)
    val boards = WorldBoardRenderer.playerBoards(parked, "red",
      BoardOptions.of(surface, drafts, canControl = true, ui,
        parked))
    assertEquals(all(boards, ".board-cards .board-option")
      .map(_.getAttribute("data-option-ref")),
      Vector("denizen:a1", "denizen:a2"))
    click(one(boards, """[data-option-ref="denizen:a2"]"""))
    assertEquals(ui.drafts.board.map(_.keys), Some(Vector("denizen:a2")))

  test("with one facedown adviser, a fresh position opens with it picked"):
    val draft = SessionDrafts.empty.reconcile(context,
      table(Some(pick(oak)), oak)).board
    assertEquals(draft.map(_.keys), Some(Vector("denizen:a1")))
    assertEquals(draft.flatMap(_.command), Some(Intent.ResolveWalker(
      BoardSelectionDraft.facedownAdviserDecisionId,
      DecisionAnswerWire.ChooseOneWire("denizen", "a1"))))

  test("a cleared pick stays cleared at its position, and two advisers " +
      "open with none picked"):
    val lone = table(Some(pick(oak)), oak)
    val cleared = SessionDrafts.empty.reconcile(context, lone)
      .staged(Draft.Board(None))
    assertEquals(cleared.reconcile(context, lone).board, None)
    assertEquals(SessionDrafts.empty.reconcile(context,
      table(Some(pick(oak, bandits)), oak, bandits)).board, None)
