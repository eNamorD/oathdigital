package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => GameCommand, MajorActionPreviewRequest,
  MajorActionPreviewResponse, ModifierInvocation, PreviewModifier}
import TestProjections.actingProjection

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The modifier flow over a host spy: which preview each entry sends, which
  * step or exit each answer applies, what is sent, and what is redrawn.
  */
class ModifierFlowSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 9)
  private val advisers = Vector("a1", "a2").map(id => MinorAdviser(
    CardDetails(id, "denizen", s"Old Oak $id", orientation = Some("face-down"))))
  private val minor = MinorActionsState(advisers, canPeekSiteRelics = false,
    Vector.empty, None, 0, 0)
  private val projection = actingProjection(nextSequence = 9L,
    players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
    .copy(minorActions = Some(minor))
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val invocation = ModifierInvocation("adviser", "a", None, "h.a")
  private val recover = GameCommand.StartWalker("recover", Vector.empty)
  private val travel = GameCommand.StartWalker("travel", Vector.empty)
  private def response(action: String, modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, action, modifiers, Vector.empty)
  /** The drafts a session holds for `projection`: empty, then reconciled
    * with it. That sets the context and no other draft.
    */
  private def draftsForProjection: SessionDrafts =
    SessionDrafts.empty.reconcile(context, projection)
  /** A flow draft the flow would have opened for `command`, with `modifier`
    * offered and selected.
    */
  private def draftWithModifierSelected(action: String,
      command: GameCommand.StartWalker): ModifierFlowDraft =
    val fresh = ModifierFlowDraft.fromPreview(command = command,
      parameters = Map.empty,
      response = response(action, Vector(modifier)), previous = None,
      context = ModifierSelectionContext("game", "red", 9, action))
    fresh.copy(selection = fresh.selection.toggle(modifier))

  /** Spy of `FlowHost`: records redraws, failures, sent commands and preview
    * requests. It computes no preview: `answer` completes the oldest one the
    * test is waiting on. The drafts the flow writes are real state
    * (`currentDrafts`), and a test asserts on them first. The other
    * recordings stand for calls with no state behind them: `sent` and
    * `previews` are requests that leave the client, and `redraws` counts the
    * host's only way to show the change, so a test asserts them only when
    * the call is the effect it names.
    */
  private final class Host(var currentDrafts: SessionDrafts) extends FlowHost:
    var displayedProjection: Option[GameProjection] = Some(projection)
    val currentGameId = "game"
    val currentPlayerId = "red"
    var redraws = 0
    var failures = Vector.empty[GameClientFailure]
    var sent = Vector.empty[(GameCommand, Vector[ModifierInvocation])]
    var previews = Vector.empty[MajorActionPreviewRequest]
    private var pending = Vector.empty[Promise[Either[GameClientFailure, MajorActionPreviewResponse]]]
    def replaceDrafts(value: SessionDrafts) = currentDrafts = value
    def redraw() = redraws += 1
    def fail(error: GameClientFailure) = { failures :+= error; redraws += 1 }
    def preview(request: MajorActionPreviewRequest) =
      previews :+= request
      val promise = Promise[Either[GameClientFailure, MajorActionPreviewResponse]]()
      pending :+= promise
      promise.future
    def send(command: GameCommand, modifiers: Vector[ModifierInvocation]) =
      sent :+= (command -> modifiers)
    /** Lands the oldest unanswered preview; the returned future completes
      * after the flow's own callback has run.
      */
    def answer(result: Either[GameClientFailure, MajorActionPreviewResponse]): Future[Unit] =
      val promise = pending.head
      pending = pending.tail
      promise.success(result)
      promise.future.map(_ => ())

  private final case class FlowUnderTest(host: Host, flow: ModifierFlow)

  private def flow(drafts: SessionDrafts = draftsForProjection)
      : FlowUnderTest =
    val host = new Host(drafts)
    FlowUnderTest(host, new ModifierFlow(host))

  /** The modifier flow draft `host` holds; fails showing its drafts. */
  private def modifiersOf(host: Host): ModifierFlowDraft =
    host.currentDrafts.modifiers.getOrElse(
      fail(s"no modifier flow draft in ${host.currentDrafts}"))

  test("a command with no preview action is sent as it is"):
    val FlowUnderTest(host, ui) = flow()
    ui.submitCommand(GameCommand.BeginRest)
    assertEquals(host.sent, Vector(GameCommand.BeginRest -> Vector.empty))
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  test("a major action whose preview offers no modifiers is sent directly"):
    val FlowUnderTest(host, ui) = flow()
    ui.submitCommand(recover)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "recover", Map.empty)))
    host.answer(Right(response("recover", Vector.empty))).map { _ =>
      assertEquals(host.sent, Vector(recover -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.redraws, 0)
    }

  test("a major action whose preview offers modifiers opens the flow and carries the previous selection"):
    val previous = draftWithModifierSelected("recover", recover)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(previous)))
    ui.submitCommand(recover)
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      val opened = modifiersOf(host)
      assertEquals(opened.command, recover)
      assertEquals(opened.selection.selected, Vector(modifier))
      assertEquals(host.redraws, 1)
      assertEquals(host.sent, Vector.empty)
    }

  test("a Travel start previews as travel and orders the modifiers it offers"):
    val FlowUnderTest(host, ui) = flow()
    ui.submitCommand(travel)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "travel", Map.empty)))
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      assertEquals(modifiersOf(host).command, travel)
      assertEquals(host.sent, Vector.empty)
      assertEquals(host.redraws, 1)
    }

  test("a Travel start with no modifiers goes out at once, naming no destination"):
    val FlowUnderTest(host, ui) = flow()
    ui.submitCommand(travel)
    host.answer(Right(response("travel", Vector.empty))).map { _ =>
      assertEquals(host.sent, Vector(travel -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
    }

  test("a preview failure on submit fails without touching the drafts"):
    val FlowUnderTest(host, ui) = flow()
    val before = host.currentDrafts
    ui.submitCommand(recover)
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.failures, Vector(error))
      assertEquals(host.currentDrafts, before)
      assertEquals(host.sent, Vector.empty)
      assertEquals(host.redraws, 1)
    }

  test("the drafts are read when the preview lands, not when it is sent"):
    val FlowUnderTest(host, ui) = flow()
    ui.submitCommand(recover)
    val moved = draftsForProjection.copy(modifiers = None)
    host.currentDrafts = moved
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      assertEquals(host.currentDrafts.partition, moved.partition)
      assertEquals(modifiersOf(host).preview,
        response("recover", Vector(modifier)))
      assertEquals(modifiersOf(host).command, recover)
    }

  test("with no projection displayed, nothing previews or sends"):
    val FlowUnderTest(host, ui) = flow()
    host.displayedProjection = None
    ui.submitCommand(recover)
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.sent, Vector.empty)
    assertEquals(host.currentDrafts, draftsForProjection)

  test("a facedown-adviser start previews as Search and goes out with no card"):
    val FlowUnderTest(host, ui) = flow()
    val start = GameCommand.StartWalker("play-facedown-adviser", Vector.empty)
    ui.submitCommand(start)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "search",
      Map("procedure" -> "facedown-adviser"))))
    host.answer(Right(response("search", Vector.empty))).map { _ =>
      assertEquals(host.sent, Vector(start -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
    }

  test("confirming leaves the flow and sends the folded submission without a redraw"):
    val ordering = draftWithModifierSelected("travel", travel)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(ordering)))
    ui.confirmModifiers()
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "travel",
      Map.empty, Vector(invocation))))
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      assertEquals(host.sent, Vector(
        GameCommand.StartWalker("travel", Vector("h.a")) -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.redraws, 0)
    }

  test("a failed confirm preview leaves the flow and fails"):
    val ordering = draftWithModifierSelected("recover", recover)
    val FlowUnderTest(host, ui) = flow(draftsForProjection.copy(
      modifiers = Some(ordering)))
    ui.confirmModifiers()
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.failures, Vector(error))
      assertEquals(host.redraws, 1)
    }

  test("confirming with nothing in flight does nothing"):
    val FlowUnderTest(host, ui) = flow()
    ui.confirmModifiers()
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  private def draftsWithRecoverOrdering: SessionDrafts =
    draftsForProjection.copy(modifiers =
      Some(draftWithModifierSelected("recover", recover)))

  test("cancelling leaves only the flow draft"):
    val FlowUnderTest(host, ui) = flow(draftsWithRecoverOrdering)
    ui.cancelModifiers()
    assertEquals(host.currentDrafts, draftsForProjection)
    assertEquals(host.redraws, 1)

  test("backing out leaves only the flow draft"):
    val FlowUnderTest(host, ui) = flow(draftsWithRecoverOrdering)
    ui.backFromModifiers()
    assertEquals(host.currentDrafts, draftsForProjection)
    assertEquals(host.redraws, 1)

  private val second = PreviewModifier("adviser:p:denizen:b", "h.b", "B")

  /** The flow of a Recover preview that offers `modifier` and `second`, with
    * the first `chosen` of them already selected in that order.
    */
  private def orderingOffering(chosen: PreviewModifier*): FlowUnderTest =
    val offered = ModifierFlowDraft.fromPreview(command = recover,
      parameters = Map.empty,
      response = response("recover", Vector(modifier, second)),
      previous = None,
      context = ModifierSelectionContext("game", "red", 9, "recover"))
    val ordering = offered.copy(selection =
      chosen.foldLeft(offered.selection)(_.toggle(_)))
    flow(draftsForProjection.copy(modifiers = Some(ordering)))

  private def selected(host: Host): Option[Vector[PreviewModifier]] =
    host.currentDrafts.modifiers.map(_.selection.selected)

  test("toggle selects modifiers in click order and redraws each time"):
    val FlowUnderTest(host, ui) = orderingOffering()
    ui.toggleModifier(modifier)
    ui.toggleModifier(second)
    assertEquals(selected(host), Some(Vector(modifier, second)))
    assertEquals(host.redraws, 2)

  test("move reorders the selection and redraws each time"):
    val FlowUnderTest(host, ui) = orderingOffering(modifier, second)
    ui.moveModifier(second, -1)
    assertEquals(selected(host), Some(Vector(second, modifier)))
    ui.moveModifier(second, 1)
    assertEquals(selected(host), Some(Vector(modifier, second)))
    assertEquals(host.redraws, 2)

  test("stage replaces one slot and redraws"):
    val FlowUnderTest(host, ui) = flow()
    val board = BoardSelectionDraft(context, "forge-9", ForgeFixture.query,
      Vector("site:site:a"))
    ui.stage(Draft.Board(Some(board)))
    assertEquals(host.currentDrafts.board, Some(board))
    assertEquals(host.redraws, 1)
