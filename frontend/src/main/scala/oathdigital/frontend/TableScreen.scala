package oathdigital.frontend

/** What the table screen reads: the displayed position, the failure to
  * show, the draft set, and who is looking. Read-only. The names repeat
  * neither the session's private state nor `SessionControls`' or
  * `FlowHost`'s members, so one class implements all three traits with no
  * name answering two questions.
  */
private[frontend] trait TableView:
  def trusted: Boolean
  def viewedGameId: String
  def viewedPlayerId: String
  def viewedProjection: Option[GameProjection]
  def shownFailure: Option[GameClientFailure]
  def viewedDrafts: SessionDrafts
  def viewedConnection: ServerConnectionState
  def controlsAvailable: Boolean
  def viewedRawEvents: Vector[RawEvent]
