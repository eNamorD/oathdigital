package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The session's two effects on the page's address. */
private[frontend] trait Navigation:
  /** Shows the current game and seat in the address bar. */
  def showSession(gameId: String, playerId: String): Unit
  /** Leaves the table for the development start page. */
  def startOver(): Unit

/** The table session (CONTEXT.md): one viewer's live connection to one game
  * from one seat. It holds the displayed position, the failure on show and
  * the draft set; loads, polls and submits under the session identity; and
  * is the modifier flow's host, the development toolbar's session and the
  * screen's view. `repaint` draws the screen; the session calls it through
  * `redraw()` wherever `ServerModeUi` used to render.
  */
private[frontend] final class TableSession(
    client: GameClient,
    val trusted: Boolean,
    initialGameId: String,
    initialPlayerId: String,
    clock: PollClock,
    navigation: Navigation,
    repaint: () => Unit
) extends SessionControls, FlowHost, TableView:
  private var projection = Option.empty[GameProjection]
  private var failure = Option.empty[GameClientFailure]
  private var selectedPlayer = initialPlayerId
  private var gameId = initialGameId
  private val coordinator = new ServerSessionCoordinator(gameId, selectedPlayer)
  private val polling = new SnapshotPollingCoordinator(clock, poll)
  private var drafts = SessionDrafts.empty
  private var rawEvents = Vector.empty[RawEvent]
  private var rawHistorySequence = Option.empty[Long]

  private def showSession(): Unit =
    if !trusted then navigation.showSession(gameId, selectedPlayer)

  private def invalidTrustedViewer(value: GameProjection): Boolean =
    trusted && !value.viewerPlayerId.exists(player =>
      value.players.exists(_.playerId == player) &&
        (selectedPlayer.isEmpty || selectedPlayer == player))

  private def store(
      request: ServerRequestIdentity,
      value: GameProjection,
      notice: Option[GameClientFailure]
  ): Unit =
    val routed = if !trusted then coordinator.route(request, value, notice)
    else value.viewerPlayerId match
      case Some(player) if !invalidTrustedViewer(value) =>
        if selectedPlayer.isEmpty then
          selectedPlayer = player
          coordinator.switchSession(gameId, selectedPlayer)
        // Against `capture`, not `request`: on the first load of an empty
        // seat, `switchSession` just above has moved `capture` to a new
        // generation, `request` would no longer be accepted, and the
        // connection would stay Connecting with every control disabled. On
        // every other path the two are equal; `accept` checked them.
        coordinator.recordSnapshotSuccess(coordinator.capture)
        Some(ProjectionRoute.Display(value, notice))
      case _ =>
        polling.stop()
        coordinator.switchSession(gameId, selectedPlayer)
        projection = None
        failure = Some(GameClientFailure.HttpFailure(403, "seat-changed",
          "Open your assigned seat link."))
        redraw()
        None
    routed.foreach:
      case ProjectionRoute.Display(displayed, retainedNotice) =>
        drafts = drafts.reconcile(
          BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
          displayed)
        projection = Some(displayed)
        failure = retainedNotice
        redraw()
        polling.resume(coordinator.capture)
        if !trusted && !rawHistorySequence.contains(displayed.nextSequence) then
          rawHistorySequence = Some(displayed.nextSequence)
          client match
            case development: HttpGameClient => development.loadRawEventHistory(gameId).foreach:
              case Right(events) => rawEvents = events; redraw()
              case Left(_) => ()
            case _ => ()
      case ProjectionRoute.ReloadForActivePlayer(
            displayed,
            nextRequest,
            retainedNotice
          ) =>
        drafts = SessionDrafts.empty
        polling.stop()
        projection = Some(displayed)
        failure = retainedNotice
        selectedPlayer = nextRequest.playerId
        showSession()
        redraw()
        client.load(gameId, selectedPlayer).foreach { result =>
          accept(nextRequest, result, retainedNotice)
        }

  private def accept(
      request: ServerRequestIdentity,
      result: Either[GameClientFailure, GameProjection],
      notice: Option[GameClientFailure] = None
  ): Unit =
    if coordinator.accepts(request) then result match
      case Right(value) => store(request, value, notice)
      case Left(error) =>
        coordinator.recordFailure(request, error)
        if GameClientFailure.isTransient(error) ||
            TableSession.needsSeatLink(trusted, error) then
          polling.stop()
        if TableSession.needsSeatLink(trusted, error) then
          coordinator.switchSession(gameId, selectedPlayer)
          projection = None
        failure = Some(error)
        redraw()

  private def loadExisting(id: String, playerId: String): Unit =
    if trusted then return
    polling.stop()
    gameId = id.trim
    projection = None
    drafts = SessionDrafts.empty
    rawEvents = Vector.empty
    rawHistorySequence = None
    failure = None
    selectedPlayer = playerId.trim match
      case "" => "red-exile"
      case value => value
    val request = coordinator.switchSession(gameId, selectedPlayer)
    showSession()
    redraw()
    client.load(gameId, selectedPlayer).foreach(accept(request, _))

  private def newGame(): Unit =
    if trusted then return
    polling.stop()
    navigation.startOver()

  private def reconnect(): Unit =
    polling.stop()
    failure = None
    val request = coordinator.reconnect()
    showSession()
    redraw()
    client.load(gameId, selectedPlayer).foreach(accept(request, _))

  private def poll(request: ServerRequestIdentity): Unit =
    client.load(request.gameId, request.playerId).foreach:
      case Right(snapshot) if invalidTrustedViewer(snapshot) =>
        val accepted = polling.complete(request, continuePolling = false)
        if accepted then accept(request, Right(snapshot))
      case Right(snapshot) =>
        val advances = projection.forall(current =>
          coordinator.snapshotAdvances(
            request,
            current.nextSequence,
            snapshot.nextSequence
          )
        )
        if advances then
          val accepted = polling.complete(request, continuePolling = false)
          if accepted then accept(request, Right(snapshot))
        else
          val accepted = polling.complete(request, continuePolling = true)
          if accepted then coordinator.recordSnapshotSuccess(request)
      case Left(error) =>
        val transient = GameClientFailure.isTransient(error)
        val accepted = polling.complete(request, continuePolling = !transient)
        if accepted then accept(request, Left(error))

  private def submitTransport(command: GameCommand,
      modifiers: Vector[ModifierInvocation]): Unit =
    projection.foreach { current =>
      val request = coordinator.capture
      client
        .submit(gameId, selectedPlayer, current.nextSequence, command, modifiers)
        .foreach:
          case Left(stale: GameClientFailure.StalePosition)
              if coordinator.accepts(request) =>
            drafts = SessionDrafts.empty
            failure = Some(stale)
            redraw()
            client.load(gameId, selectedPlayer).foreach:
              refreshed => accept(request, refreshed, Some(stale))
          case other => accept(request, other)
    }

  /** Forwards the page's visibility to the poller. */
  def visibilityChanged(hidden: Boolean): Unit = polling.visibilityChanged(hidden)

  /** The bootstrap: the first render, then the trusted seat's load, the
    * queried game, or a new game.
    */
  def open(queryGameId: Option[String]): Unit =
    redraw()
    if trusted then client.load(gameId, selectedPlayer).foreach(accept(coordinator.capture, _))
    else queryGameId match
      case Some(existing) => loadExisting(existing, selectedPlayer)
      case None => newGame()

  // SessionControls (the development toolbar).
  def currentGameId: String = gameId
  def currentPlayerId: String = selectedPlayer
  def displayedProjection: Option[GameProjection] = projection
  def connectionState: ServerConnectionState = coordinator.connectionState
  def loadSession(id: String, playerId: String): Unit = loadExisting(id, playerId)
  def reconnectSession(): Unit = reconnect()
  def createGame(): Unit = newGame()

  // FlowHost (the modifier flow). The three identity members are above.
  def currentDrafts: SessionDrafts = drafts
  def replaceDrafts(value: SessionDrafts): Unit = drafts = value
  def redraw(): Unit = repaint()
  def fail(error: GameClientFailure): Unit =
    failure = Some(error)
    redraw()
  def preview(request: MajorActionPreviewRequest) =
    client.preview(gameId, selectedPlayer, request)
  def send(command: GameCommand, modifiers: Vector[ModifierInvocation]): Unit =
    submitTransport(command, modifiers)

  // TableView (the screen).
  def viewedGameId: String = gameId
  def viewedPlayerId: String = selectedPlayer
  def viewedProjection: Option[GameProjection] = projection
  def shownFailure: Option[GameClientFailure] = failure
  def viewedDrafts: SessionDrafts = drafts
  def viewedConnection: ServerConnectionState = coordinator.connectionState
  def controlsAvailable: Boolean =
    coordinator.connectionState == ServerConnectionState.Connected
  def viewedRawEvents: Vector[RawEvent] = rawEvents

private[frontend] object TableSession:
  /** A trusted seat whose cookie no longer opens the game: the viewer must
    * reopen their seat link. Development sessions never need one.
    */
  def needsSeatLink(trusted: Boolean, error: GameClientFailure): Boolean =
    error match
      case GameClientFailure.HttpFailure(401 | 403, _, _) if trusted => true
      case _ => false
