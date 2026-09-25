package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => GameCommand, MajorActionPreviewRequest,
  MajorActionPreviewResponse, ModifierInvocation, PreviewModifier}

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The modifier flow over a fake host: which preview each entry sends, which
  * step or exit each answer applies, what is sent, and what is redrawn.
  */
class ModifierFlowSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 9)
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private val advisers = Vector("a1", "a2").map(id => MinorAdviser(
    CardDetails(id, "denizen", s"Old Oak $id", orientation = Some("face-down")),
    Vector.empty))
  private val minor = MinorActionsState(advisers, canPeekSiteRelics = false,
    Vector.empty, None, 0, 0)
  private val projection = GameProjection("game", 9L, "act", Some("red"),
    Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
    Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
    boardTargetActions = Vector(travel), minorActions = Some(minor))
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val invocation = ModifierInvocation("adviser", "a", None, "h.a")
  private val recover = GameCommand.StartWalker("recover", Vector.empty)
  private def response(action: String, modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, action, modifiers, Vector.empty, Vector.empty)
  private def bound: SessionDrafts = SessionDrafts.empty.reconcile(context, projection)
  /** A flow draft the flow would have opened for `action`, with `modifier`
    * offered and (when `chosen`) selected.
    */
  private def draft(action: String, command: Option[GameCommand],
      actionKind: Option[String], stage: ModifierFlowStage,
      chosen: Boolean = true): ModifierFlowDraft =
    val fresh = ModifierFlowDraft.fromPreview(command, actionKind, Map.empty,
      response(action, Vector(modifier)), None,
      ModifierSelectionContext("game", "red", 9, action))
    val selected = if chosen then fresh.selection.toggle(modifier) else fresh.selection
    fresh.copy(selection = selected, stage = stage)

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

  private def flow(drafts: SessionDrafts = bound): (Host, ModifierFlow) =
    val host = new Host(drafts)
    host -> new ModifierFlow(host)

  test("a command with no preview action is sent as it is"):
    val (host, ui) = flow()
    ui.submitCommand(GameCommand.BeginRest)
    assertEquals(host.sent, Vector(GameCommand.BeginRest -> Vector.empty))
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  test("a major action whose preview offers no modifiers is sent directly"):
    val (host, ui) = flow()
    ui.submitCommand(recover)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "recover", Map.empty)))
    host.answer(Right(response("recover", Vector.empty))).map { _ =>
      assertEquals(host.sent, Vector(recover -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.redraws, 0)
    }

  test("a major action whose preview offers modifiers enters Ordering and carries the previous selection"):
    val previous = draft("recover", Some(recover), None, ModifierFlowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(previous)))
    ui.submitCommand(recover)
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      val opened = host.currentDrafts.modifiers.get
      assertEquals(opened.stage, ModifierFlowStage.Ordering)
      assertEquals(opened.command, Some(recover))
      assertEquals(opened.actionKind, None)
      assertEquals(opened.selection.selected, Vector(modifier))
      assertEquals(host.redraws, 1)
      assertEquals(host.sent, Vector.empty)
    }

  test("a preview failure on submit fails without touching the drafts"):
    val (host, ui) = flow()
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
    val (host, ui) = flow()
    ui.submitCommand(recover)
    val pick = FacedownAdviserDraft.initial(context, minor).map(_.choose("a2"))
    host.currentDrafts = bound.copy(facedownAdviser = pick)
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      assertEquals(host.currentDrafts.facedownAdviser, pick)
      assertEquals(host.currentDrafts.modifiers.map(_.stage),
        Some(ModifierFlowStage.Ordering))
    }

  test("with no projection displayed, neither entry previews or sends"):
    val (host, ui) = flow()
    host.displayedProjection = None
    ui.submitCommand(recover)
    ui.beginTargetedMajorAction("travel")
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.sent, Vector.empty)
    assertEquals(host.currentDrafts, bound)

  test("a targeted travel restarts the flow, then enters Ordering when modifiers are offered"):
    val stale = draft("recover", Some(recover), None, ModifierFlowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(stale),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.beginTargetedMajorAction("travel")
    // Restarted, before the preview lands.
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.facedownAdviser, None)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "travel", Map.empty)))
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      val opened = host.currentDrafts.modifiers.get
      assertEquals(opened.stage, ModifierFlowStage.Ordering)
      assertEquals(opened.command, None)
      assertEquals(opened.actionKind, Some("travel"))
      assertEquals(host.redraws, 1)
    }

  test("a failed targeted preview fails and leaves the restarted drafts"):
    val (host, ui) = flow(bound.copy(
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.beginTargetedMajorAction("travel")
    val restarted = host.currentDrafts
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.currentDrafts, restarted)
      assertEquals(host.currentDrafts.facedownAdviser, None)
      assertEquals(host.currentDrafts.boardTargets, bound.boardTargets.map(_.cancel))
      assertEquals(host.failures, Vector(error))
      assertEquals(host.redraws, 1)
    }

  test("a targeted travel with no modifiers enters Targets on the board"):
    val (host, ui) = flow()
    ui.beginTargetedMajorAction("travel")
    host.answer(Right(response("travel", Vector.empty))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Targets))
      assertEquals(host.currentDrafts.boardTargets.flatMap(_.activeActionKind), Some("travel"))
      assertEquals(host.currentDrafts.boardTargets.map(_.actions.map(_.actionKind)),
        Some(Vector("travel")))
      assertEquals(host.redraws, 1)
    }

  test("a targeted facedown adviser enters Targets with the pick and no board targets"):
    val (host, ui) = flow()
    ui.beginTargetedMajorAction("play-facedown-adviser")
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "search",
      Map("procedure" -> "facedown-adviser"))))
    host.answer(Right(response("search", Vector.empty))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Targets))
      assertEquals(host.currentDrafts.facedownAdviser.map(_.advisers.map(_.card.cardId)),
        Some(Vector("a1", "a2")))
      assertEquals(host.currentDrafts.boardTargets, None)
      assertEquals(host.redraws, 1)
    }

  test("confirming with a command leaves Ordering and sends the folded submission without a redraw"):
    val ordering = draft("recover", Some(recover), None, ModifierFlowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering)))
    ui.confirmModifiers()
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "recover",
      Map.empty, Vector(invocation))))
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      assertEquals(host.sent, Vector(
        GameCommand.StartWalker("recover", Vector("h.a")) -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.redraws, 0)
    }

  test("confirming without a command enters Targets"):
    val ordering = draft("travel", None, Some("travel"), ModifierFlowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering)))
    ui.confirmModifiers()
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Targets))
      assertEquals(host.currentDrafts.modifiers.map(_.selection.selected), Some(Vector(modifier)))
      assertEquals(host.currentDrafts.boardTargets.flatMap(_.activeActionKind), Some("travel"))
      assertEquals(host.sent, Vector.empty)
      assertEquals(host.redraws, 1)
    }

  test("a failed confirm preview leaves Failed and fails"):
    val ordering = draft("recover", Some(recover), None, ModifierFlowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.confirmModifiers()
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.currentDrafts.facedownAdviser, None)
      assertEquals(host.currentDrafts.boardTargets, bound.boardTargets)
      assertEquals(host.failures, Vector(error))
      assertEquals(host.redraws, 1)
    }

  test("confirming with nothing in flight does nothing"):
    val (host, ui) = flow()
    ui.confirmModifiers()
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  test("a target command in Targets leaves Completed and sends the folded submission"):
    val targets = draft("travel", None, Some("travel"), ModifierFlowStage.Targets)
    val (host, ui) = flow(bound.copy(modifiers = Some(targets)))
    val command = GameCommand.StartWalker("travel", Vector.empty)
    ui.submitTargetCommand(command)
    assertEquals(host.sent, Vector(
      GameCommand.StartWalker("travel", Vector("h.a")) -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.boardTargets, None)
    assertEquals(host.previews, Vector.empty)

  test("a target command outside Targets behaves as a plain submit"):
    val (host, ui) = flow()
    ui.submitTargetCommand(GameCommand.BeginRest)
    assertEquals(host.sent, Vector(GameCommand.BeginRest -> Vector.empty))
    assertEquals(host.previews, Vector.empty)

  test("cancelling from either stage leaves Cancelled with the restored board targets"):
    val restored = BoardTargetSelectionState.restore(context, Vector(travel))
    val targets = draft("travel", None, Some("travel"), ModifierFlowStage.Targets)
    val start = bound.copy(modifiers = Some(targets),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor),
      boardTargets = bound.boardTargets.map(_.copy(selectedKeys = Set("site:site:woods"))))
    val (first, ui) = flow(start)
    ui.cancelModifiers()
    assertEquals(first.currentDrafts.modifiers, None)
    assertEquals(first.currentDrafts.facedownAdviser, None)
    assertEquals(first.currentDrafts.boardTargets, Some(restored))
    assertEquals(first.redraws, 1)
    val (second, again) = flow(start)
    again.cancelTargetAction()
    assertEquals(second.currentDrafts, first.currentDrafts)
    assertEquals(second.redraws, 1)

  test("backing out of Ordering leaves only the flow draft"):
    val ordering = draft("recover", Some(recover), None, ModifierFlowStage.Ordering)
    val pick = FacedownAdviserDraft.initial(context, minor)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering), facedownAdviser = pick))
    ui.backFromModifiers()
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.facedownAdviser, pick)
    assertEquals(host.redraws, 1)

  test("backing out of Targets returns to Ordering, and is a no-op with no flow"):
    val targets = draft("travel", None, Some("travel"), ModifierFlowStage.Targets)
    val (host, ui) = flow(bound.copy(modifiers = Some(targets),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.backFromTargets()
    assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Ordering))
    assertEquals(host.currentDrafts.facedownAdviser, None)
    assertEquals(host.currentDrafts.boardTargets, None)
    assertEquals(host.redraws, 1)
    val (idle, none) = flow()
    none.backFromTargets()
    assertEquals(idle.currentDrafts, bound)
    assertEquals(idle.redraws, 0)

  test("toggle, move and choose apply their step and redraw"):
    val second = PreviewModifier("adviser:p:denizen:b", "h.b", "B")
    val ordering = ModifierFlowDraft.fromPreview(Some(recover), None, Map.empty,
      response("recover", Vector(modifier, second)), None,
      ModifierSelectionContext("game", "red", 9, "recover"))
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    def selected = host.currentDrafts.modifiers.map(_.selection.selected)
    ui.toggleModifier(modifier)
    ui.toggleModifier(second)
    assertEquals(selected, Some(Vector(modifier, second)))
    ui.moveModifier(second, -1)
    assertEquals(selected, Some(Vector(second, modifier)))
    ui.moveModifier(second, 1)
    assertEquals(selected, Some(Vector(modifier, second)))
    ui.chooseFacedownAdviser("a2")
    assertEquals(host.currentDrafts.facedownAdviser.flatMap(_.selectedCardId), Some("a2"))
    assertEquals(host.redraws, 5)

  test("stage replaces one slot and redraws"):
    val (host, ui) = flow()
    val activated = host.currentDrafts.boardTargets.get.copy(selectedKeys = Set("site:site:woods"))
    ui.stage(Draft.BoardTargets(activated))
    assertEquals(host.currentDrafts.boardTargets, Some(activated))
    assertEquals(host.redraws, 1)

  test("a board selection update stages the targets and a submit completes the command"):
    val targets = draft("travel", None, Some("travel"), ModifierFlowStage.Targets)
    val (host, ui) = flow(bound.copy(modifiers = Some(targets)))
    val activated = host.currentDrafts.boardTargets.get.copy(selectedKeys = Set("site:site:woods"))
    ui.handleSelection(BoardSelectionResult.Updated(activated))
    assertEquals(host.currentDrafts.boardTargets, Some(activated))
    assertEquals(host.redraws, 1)
    ui.handleSelection(BoardSelectionResult.Submit(travel,
      Vector(BoardTargetRef.Site("site:woods"))))
    assertEquals(host.sent, Vector(GameCommand.StartWalker("travel", Vector("h.a"),
      Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:woods"))) -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
