package oathdigital.frontend

import oathdigital.protocol.{GameIntent => Intent, PreviewModifier}

/** Spy of `ActionControls`: records what was staged and submitted instead
  * of touching a session, shared by every panel render suite. `stage`,
  * `submitCommand`, `cancelTargetAction` and `submitTargetCommand` record;
  * every other method is a no-op stub. `drafts` is the draft set the stages
  * so far leave, from `empty`, so a suite re-renders a panel from exactly
  * what the panel staged.
  */
private[frontend] class RecordingControls extends ActionControls:
  var staged: Vector[Draft] = Vector.empty
  var submitted: Vector[Intent] = Vector.empty
  var targetCommands: Vector[Intent.StartWalker] = Vector.empty
  var cancelledTargets: Int = 0

  def drafts: SessionDrafts = staged.foldLeft(SessionDrafts.empty)(_.staged(_))
  def stage(draft: Draft): Unit = staged :+= draft
  def submitCommand(command: Intent): Unit = submitted :+= command
  def handleSelection(result: BoardSelectionResult): Unit = ()

  def toggleModifier(value: PreviewModifier): Unit = ()
  def moveModifier(value: PreviewModifier, delta: Int): Unit = ()
  def confirmModifiers(): Unit = ()
  def backFromModifiers(): Unit = ()
  def cancelModifiers(): Unit = ()
  def beginTargetedMajorAction(actionKind: String): Unit = ()
  def backFromTargets(): Unit = ()
  def cancelTargetAction(): Unit = cancelledTargets += 1
  def submitTargetCommand(command: Intent.StartWalker): Unit =
    targetCommands :+= command
