package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** What the session gives the modifier flow: the projection and identity it
  * reads, the draft set it steps, and the four effects it causes. `preview`
  * and `send` add the game and seat themselves. Member names follow
  * `SessionControls` rather than the session's own locals, because the
  * anonymous implementation in `ServerModeUi.start` would otherwise shadow
  * the local it forwards to.
  */
private[frontend] trait FlowHost:
  def displayedProjection: Option[GameProjection]
  def currentGameId: String
  def currentPlayerId: String
  def currentDrafts: SessionDrafts
  /** Writes without a redraw; the flow redraws where it did before. */
  def replaceDrafts(value: SessionDrafts): Unit
  def redraw(): Unit
  /** Records the failure and redraws. */
  def fail(error: GameClientFailure): Unit
  def preview(request: MajorActionPreviewRequest)
      : Future[Either[GameClientFailure, MajorActionPreviewResponse]]
  /** `submitTransport`: sends under the session's identity and position. */
  def send(command: GameCommand, modifiers: Vector[ModifierInvocation]): Unit

/** The modifier flow (CONTEXT.md): previewing a major action and ordering
  * the modifiers it offers. Every `ActionControls` member ends here; the
  * flow reads the draft set from its host, applies a `FlowStep` or a
  * `FlowExit`, and redraws or sends.
  */
private[frontend] final class ModifierFlow(host: FlowHost) extends ActionControls:
  private def drafts: SessionDrafts = host.currentDrafts

  private def selectionContext(sequence: Long, action: String) =
    ModifierSelectionContext(host.currentGameId, host.currentPlayerId, sequence, action)

  private def step(value: FlowStep): Unit =
    host.replaceDrafts(drafts.step(value))
    host.redraw()

  private def exit(value: FlowExit): Unit =
    host.replaceDrafts(drafts.leave(value))
    host.redraw()

  private def submit(command: GameCommand): Unit = (command match
    case walker: GameCommand.StartWalker =>
      ModifierFlowDraft.action(walker).map(walker -> _)
    case _ => None) match
    case None => host.send(command, Vector.empty)
    case Some((walker, (action, parameters))) => host.displayedProjection.foreach { current =>
      val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
      host.preview(request).foreach:
        case Right(response) if response.modifiers.isEmpty =>
          host.send(command, Vector.empty)
        case Right(response) =>
          step(FlowStep.Ordering(ModifierFlowDraft.fromPreview(walker,
            parameters, response, drafts.modifiers.map(_.selection),
            selectionContext(current.nextSequence, action))))
        case Left(error) => host.fail(error)
    }

  private def confirmModifierSelection(): Unit = drafts.modifiers.foreach { draft =>
    val request = MajorActionPreviewRequest(draft.selection.context.sequence,
      draft.selection.context.action, draft.baseParameters,
      draft.selection.invocations)
    host.preview(request).foreach:
      case Right(_) =>
        host.replaceDrafts(drafts.leave(FlowExit.OrderingLeft))
        host.send(ModifierFlowDraft.submission(draft.command,
          draft.selection.invocations), Vector.empty)
      case Left(error) =>
        host.replaceDrafts(drafts.leave(FlowExit.Failed))
        host.fail(error)
  }

  def stage(draft: Draft): Unit =
    host.replaceDrafts(drafts.staged(draft))
    host.redraw()
  def submitCommand(command: GameCommand): Unit = submit(command)

  def toggleModifier(value: PreviewModifier): Unit = step(FlowStep.Toggle(value))
  def moveModifier(value: PreviewModifier, delta: Int): Unit =
    step(FlowStep.Move(value, delta))
  def confirmModifiers(): Unit = confirmModifierSelection()
  def backFromModifiers(): Unit = exit(FlowExit.OrderingLeft)
  def cancelModifiers(): Unit = exit(FlowExit.Cancelled)
