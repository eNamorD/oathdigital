package oathdigital.frontend

import oathdigital.protocol.GameIntent => Intent

/** Spy of `TableControls`: records what was staged and submitted instead
  * of touching a session, shared by every panel render suite. `drafts` is
  * the draft set the stages so far leave, from `empty`, so a suite
  * re-renders a panel from exactly what the panel staged and asserts on that
  * state, not on how often `stage` ran. `submitted` is the one place a call
  * is the whole effect: a command leaves the client and nothing observable
  * comes back, so a suite asserts the command itself.
  */
private[frontend] class RecordingControls extends TableControls:
  var staged: Vector[Draft] = Vector.empty
  var submitted: Vector[Intent] = Vector.empty

  def drafts: SessionDrafts = staged.foldLeft(SessionDrafts.empty)(_.staged(_))
  def stage(draft: Draft): Unit = staged :+= draft
  def submitCommand(command: Intent): Unit = submitted :+= command
