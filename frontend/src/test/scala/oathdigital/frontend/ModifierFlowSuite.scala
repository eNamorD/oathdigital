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
  private val travel = BoardTargetAction("travel", "Choose a destination",
    minimum = 1, maximum = 1, autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  // A Site ref's stable key is site:<id>, and this site's id is site:woods.
  private val woodsKey = BoardTargetRef.Site("site:woods").stableKey
  private val advisers = Vector("a1", "a2").map(id => MinorAdviser(
    CardDetails(id, "denizen", s"Old Oak $id", orientation = Some("face-down"))))
  private val minor = MinorActionsState(advisers, canPeekSiteRelics = false,
    Vector.empty, None, 0, 0)
  private val projection = actingProjection(nextSequence = 9L,
    players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
    .copy(boardTargetActions = Vector(travel), minorActions = Some(minor))
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val invocation = ModifierInvocation("adviser", "a", None, "h.a")
  private val recover = GameCommand.StartWalker("recover", Vector.empty)
  private def response(action: String, modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, action, modifiers, Vector.empty, Vector.empty)
  /** The drafts a session holds for `projection`: empty, then reconciled
    * with it. That sets the context and the board-target draft for Travel
    * (the one action `projection` offers, active because it auto-activates),
    * and no other draft.
    */
  private def draftsForProjection: SessionDrafts =
    SessionDrafts.empty.reconcile(context, projection)
  /** A flow draft the flow would have opened for `action`, with `modifier`
    * offered and selected.
    */
  private def draftWithModifierSelected(action: String,
      command: Option[GameCommand.StartWalker], actionKind: Option[String],
      stage: ModifierFlowStage): ModifierFlowDraft =
    val fresh = ModifierFlowDraft.fromPreview(command = command,
      actionKind = actionKind, parameters = Map.empty,
      response = response(action, Vector(modifier)), previous = None,
      context = ModifierSelectionContext("game", "red", 9, action))
    fresh.copy(selection = fresh.selection.toggle(modifier), stage = stage)

  /** Spy of `FlowHost`: records redraws, failures, sent commands and preview
    * requests. It computes no preview: `answer` completes the oldest one the
    * test is waiting on.
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

  /** The board-target draft `host` holds; fails showing its drafts. */
  private def boardTargetsOf(host: Host): BoardTargetSelectionState =
    host.currentDrafts.boardTargets.getOrElse(
      fail(s"no board-target draft in ${host.currentDrafts}"))

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

  test("a major action whose preview offers modifiers enters Ordering and carries the previous selection"):
    val previous = draftWithModifierSelected("recover", Some(recover), None,
      ModifierFlowStage.Ordering)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(previous)))
    ui.submitCommand(recover)
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      val opened = modifiersOf(host)
      assertEquals(opened.stage, ModifierFlowStage.Ordering)
      assertEquals(opened.command, Some(recover))
      assertEquals(opened.actionKind, None)
      assertEquals(opened.selection.selected, Vector(modifier))
      assertEquals(host.redraws, 1)
      assertEquals(host.sent, Vector.empty)
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
    val moved = draftsForProjection.copy(boardTargets =
      draftsForProjection.boardTargets.map(_.copy(selectedKeys = Set(woodsKey))))
    host.currentDrafts = moved
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      assertEquals(host.currentDrafts.boardTargets, moved.boardTargets)
      assertEquals(host.currentDrafts.modifiers.map(_.stage),
        Some(ModifierFlowStage.Ordering))
    }

  test("with no projection displayed, neither entry previews or sends"):
    val FlowUnderTest(host, ui) = flow()
    host.displayedProjection = None
    ui.submitCommand(recover)
    ui.beginTargetedMajorAction("travel")
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.sent, Vector.empty)
    assertEquals(host.currentDrafts, draftsForProjection)

  test("a targeted travel restarts the flow, then enters Ordering when modifiers are offered"):
    val stale = draftWithModifierSelected("recover", Some(recover), None,
      ModifierFlowStage.Ordering)
    val FlowUnderTest(host, ui) = flow(draftsForProjection.copy(
      modifiers = Some(stale)))
    ui.beginTargetedMajorAction("travel")
    // Restarted, before the preview lands.
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "travel", Map.empty)))
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      val opened = modifiersOf(host)
      assertEquals(opened.stage, ModifierFlowStage.Ordering)
      assertEquals(opened.command, None)
      assertEquals(opened.actionKind, Some("travel"))
      assertEquals(host.redraws, 1)
    }

  test("a failed targeted preview fails and leaves the restarted drafts"):
    val FlowUnderTest(host, ui) = flow()
    ui.beginTargetedMajorAction("travel")
    val restarted = host.currentDrafts
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.currentDrafts, restarted)
      assertEquals(host.currentDrafts.boardTargets,
        draftsForProjection.boardTargets.map(_.cancel))
      assertEquals(host.failures, Vector(error))
      assertEquals(host.redraws, 1)
    }

  test("a targeted travel with no modifiers enters Targets on the board"):
    val FlowUnderTest(host, ui) = flow()
    ui.beginTargetedMajorAction("travel")
    host.answer(Right(response("travel", Vector.empty))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Targets))
      assertEquals(host.currentDrafts.boardTargets.flatMap(_.activeActionKind), Some("travel"))
      assertEquals(host.currentDrafts.boardTargets.map(_.actions.map(_.actionKind)),
        Some(Vector("travel")))
      assertEquals(host.redraws, 1)
    }

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

  test("confirming with a command leaves Ordering and sends the folded submission without a redraw"):
    val ordering = draftWithModifierSelected("recover", Some(recover), None,
      ModifierFlowStage.Ordering)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(ordering)))
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
    val ordering = draftWithModifierSelected("travel", None, Some("travel"),
      ModifierFlowStage.Ordering)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(ordering)))
    ui.confirmModifiers()
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Targets))
      assertEquals(host.currentDrafts.modifiers.map(_.selection.selected), Some(Vector(modifier)))
      assertEquals(host.currentDrafts.boardTargets.flatMap(_.activeActionKind), Some("travel"))
      assertEquals(host.sent, Vector.empty)
      assertEquals(host.redraws, 1)
    }

  test("a failed confirm preview leaves Failed and fails"):
    val ordering = draftWithModifierSelected("recover", Some(recover), None,
      ModifierFlowStage.Ordering)
    val FlowUnderTest(host, ui) = flow(draftsForProjection.copy(
      modifiers = Some(ordering)))
    ui.confirmModifiers()
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.currentDrafts.boardTargets,
        draftsForProjection.boardTargets)
      assertEquals(host.failures, Vector(error))
      assertEquals(host.redraws, 1)
    }

  test("confirming with nothing in flight does nothing"):
    val FlowUnderTest(host, ui) = flow()
    ui.confirmModifiers()
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  test("a target command in Targets leaves Completed and sends the folded submission"):
    val targets = draftWithModifierSelected("travel", None, Some("travel"),
      ModifierFlowStage.Targets)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(targets)))
    val command = GameCommand.StartWalker("travel", Vector.empty)
    ui.submitTargetCommand(command)
    assertEquals(host.sent, Vector(
      GameCommand.StartWalker("travel", Vector("h.a")) -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.boardTargets, None)
    assertEquals(host.previews, Vector.empty)

  test("a target command outside Targets behaves as a plain submit"):
    val FlowUnderTest(host, ui) = flow()
    // Take Wealth offers no modifiers, so the command goes out unchanged.
    val takeWealth = GameCommand.StartWalker("take-wealth", Vector.empty)
    ui.submitTargetCommand(takeWealth)
    assertEquals(host.sent, Vector(takeWealth -> Vector.empty))
    assertEquals(host.previews, Vector.empty)

  test("both cancel entries restore the board targets from Targets"):
    val restored = BoardTargetSelectionState.restore(context, Vector(travel))
    val targets = draftWithModifierSelected("travel", None, Some("travel"),
      ModifierFlowStage.Targets)
    val start = draftsForProjection.copy(modifiers = Some(targets),
      boardTargets = draftsForProjection.boardTargets
        .map(_.copy(selectedKeys = Set(woodsKey))))
    val FlowUnderTest(first, ui) = flow(start)
    ui.cancelModifiers()
    assertEquals(first.currentDrafts.modifiers, None)
    assertEquals(first.currentDrafts.boardTargets, Some(restored))
    assertEquals(first.redraws, 1)
    val FlowUnderTest(second, again) = flow(start)
    again.cancelTargetAction()
    assertEquals(second.currentDrafts, first.currentDrafts)
    assertEquals(second.redraws, 1)

  test("backing out of Ordering leaves only the flow draft"):
    val ordering = draftWithModifierSelected("recover", Some(recover), None,
      ModifierFlowStage.Ordering)
    val FlowUnderTest(host, ui) = flow(draftsForProjection.copy(
      modifiers = Some(ordering)))
    ui.backFromModifiers()
    assertEquals(host.currentDrafts, draftsForProjection)
    assertEquals(host.redraws, 1)

  test("backing out of Targets returns to Ordering, and is a no-op with no flow"):
    val targets = draftWithModifierSelected("travel", None, Some("travel"),
      ModifierFlowStage.Targets)
    val FlowUnderTest(host, ui) = flow(draftsForProjection.copy(
      modifiers = Some(targets)))
    ui.backFromTargets()
    assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierFlowStage.Ordering))
    assertEquals(host.currentDrafts.boardTargets, None)
    assertEquals(host.redraws, 1)
    val FlowUnderTest(idle, none) = flow()
    none.backFromTargets()
    assertEquals(idle.currentDrafts, draftsForProjection)
    assertEquals(idle.redraws, 0)

  test("toggle and move apply their step and redraw"):
    val second = PreviewModifier("adviser:p:denizen:b", "h.b", "B")
    val ordering = ModifierFlowDraft.fromPreview(command = Some(recover),
      actionKind = None, parameters = Map.empty,
      response = response("recover", Vector(modifier, second)),
      previous = None,
      context = ModifierSelectionContext("game", "red", 9, "recover"))
    val FlowUnderTest(host, ui) = flow(draftsForProjection.copy(
      modifiers = Some(ordering)))
    def selected = host.currentDrafts.modifiers.map(_.selection.selected)
    ui.toggleModifier(modifier)
    ui.toggleModifier(second)
    assertEquals(selected, Some(Vector(modifier, second)))
    ui.moveModifier(second, -1)
    assertEquals(selected, Some(Vector(second, modifier)))
    ui.moveModifier(second, 1)
    assertEquals(selected, Some(Vector(modifier, second)))
    // Two toggles and two moves.
    assertEquals(host.redraws, 2 + 2)

  test("stage replaces one slot and redraws"):
    val FlowUnderTest(host, ui) = flow()
    val activated = boardTargetsOf(host).copy(selectedKeys = Set(woodsKey))
    ui.stage(Draft.BoardTargets(activated))
    assertEquals(host.currentDrafts.boardTargets, Some(activated))
    assertEquals(host.redraws, 1)

  test("a board selection update stages the targets and a submit completes the command"):
    val targets = draftWithModifierSelected("travel", None, Some("travel"),
      ModifierFlowStage.Targets)
    val FlowUnderTest(host, ui) = flow(
      draftsForProjection.copy(modifiers = Some(targets)))
    val activated = boardTargetsOf(host).copy(selectedKeys = Set(woodsKey))
    ui.handleSelection(BoardSelectionResult.Updated(activated))
    assertEquals(host.currentDrafts.boardTargets, Some(activated))
    assertEquals(host.redraws, 1)
    ui.handleSelection(BoardSelectionResult.Submit(travel,
      Vector(BoardTargetRef.Site("site:woods"))))
    assertEquals(host.sent, Vector(GameCommand.StartWalker("travel", Vector("h.a"),
      Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:woods"))) -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
