package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => GameCommand, MajorActionPreviewRequest,
  MajorActionPreviewResponse, ModifierInvocation}

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.setTimeout

/** The table session over a fake client, a manual poll clock and a recording
  * navigation: which loads it makes, how it routes and fails, when it polls,
  * and what it shows. The jsdom suite proves the wiring; this proves the
  * session.
  */
class TableSessionSuite extends munit.FunSuite:
  private type Answer[A] = Either[GameClientFailure, A]

  /** Records every call and answers each through a promise, oldest first. */
  private final class FakeClient extends GameClient:
    var loads = Vector.empty[(String, String)]
    var submits = Vector.empty[(Long, GameCommand)]
    var previews = Vector.empty[(String, String, MajorActionPreviewRequest)]
    private var pendingLoads = Vector.empty[Promise[Answer[GameProjection]]]
    private var pendingSubmits = Vector.empty[Promise[Answer[GameProjection]]]
    private var pendingPreviews = Vector.empty[Promise[Answer[MajorActionPreviewResponse]]]
    def load(gameId: String, selectedPlayerId: String) =
      loads :+= (gameId -> selectedPlayerId)
      val promise = Promise[Answer[GameProjection]]()
      pendingLoads :+= promise
      promise.future
    def submit(gameId: String, selectedPlayerId: String, expectedNextSequence: Long,
        command: GameCommand, orderedModifiers: Vector[ModifierInvocation]) =
      submits :+= (expectedNextSequence -> command)
      val promise = Promise[Answer[GameProjection]]()
      pendingSubmits :+= promise
      promise.future
    def preview(gameId: String, selectedPlayerId: String,
        request: MajorActionPreviewRequest) =
      previews :+= ((gameId, selectedPlayerId, request))
      val promise = Promise[Answer[MajorActionPreviewResponse]]()
      pendingPreviews :+= promise
      promise.future
    def answerLoad(result: Answer[GameProjection]): Unit =
      val promise = pendingLoads.head
      pendingLoads = pendingLoads.tail
      promise.success(result)
    def answerSubmit(result: Answer[GameProjection]): Unit =
      val promise = pendingSubmits.head
      pendingSubmits = pendingSubmits.tail
      promise.success(result)
    def answerPreview(result: Answer[MajorActionPreviewResponse]): Unit =
      val promise = pendingPreviews.head
      pendingPreviews = pendingPreviews.tail
      promise.success(result)

  /** Holds the latest scheduled poll; `fire` runs it as if its delay passed. */
  private final class ManualClock extends PollClock:
    var delays = Vector.empty[Int]
    private var latest = Option.empty[() => Unit]
    def schedule(delayMillis: Int)(task: () => Unit) =
      delays :+= delayMillis
      latest = Some(task)
      new PollCancellation:
        def cancel(): Unit = if latest.contains(task) then latest = None
    def pending: Boolean = latest.nonEmpty
    def fire(): Unit =
      val task = latest.get
      latest = None
      task()

  private final class RecordingNavigation extends Navigation:
    var shown = Vector.empty[(String, String)]
    var startedOver = 0
    def showSession(gameId: String, playerId: String): Unit = shown :+= (gameId -> playerId)
    def startOver(): Unit = startedOver += 1

  private final class Fixture(trusted: Boolean, player: String = "red"):
    val client = new FakeClient
    val clock = new ManualClock
    val navigation = new RecordingNavigation
    var redraws = 0
    val session = new TableSession(client, trusted, "g", player, clock,
      navigation, () => redraws += 1)

  /** Every queued callback has run: a timer fires after the microtask queue
    * drains, and the queue execution context runs on microtasks.
    */
  private def settle(): Future[Unit] =
    val done = Promise[Unit]()
    setTimeout(0)(done.success(()))
    done.future

  private val players = Vector(
    GamePlayer("red", "Red", "Exile", PlayerColor.Red),
    GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue))

  private def snapshot(sequence: Long, active: String = "red", ready: Boolean = true,
      viewer: Option[String] = None): GameProjection =
    GameProjection("g", sequence, "act", Some(active), players, Vector.empty,
      Vector.empty, Vector.empty, ready = ready, completed = false,
      viewerPlayerId = viewer)

  /** A development session showing `snapshot(sequence)` as red, polling. */
  private def displayed(sequence: Long): Future[Fixture] =
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    fixture.client.answerLoad(Right(snapshot(sequence)))
    settle().map(_ => fixture)

  /** A trusted seat that has taken red from its first snapshot, polling. */
  private def seated(): Future[Fixture] =
    val fixture = new Fixture(trusted = true, player = "")
    fixture.session.open(None)
    fixture.client.answerLoad(Right(snapshot(1, viewer = Some("red"))))
    settle().map(_ => fixture)

  test("a development session opens the queried game, displays it and polls"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    assertEquals(fixture.client.loads, Vector("g" -> "red"))
    assertEquals(fixture.navigation.shown, Vector("g" -> "red"))
    assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connecting)
    fixture.client.answerLoad(Right(snapshot(3)))
    settle().map { _ =>
      val session = fixture.session
      assertEquals(session.viewedProjection.map(_.nextSequence), Some(3L))
      assertEquals(session.viewedDrafts.context, Some(BoardSelectionContext("g", "red", 3)))
      assertEquals(session.viewedConnection, ServerConnectionState.Connected)
      assert(session.controlsAvailable)
      assertEquals(fixture.clock.delays, Vector(5000))
      assert(fixture.clock.pending)
      // open, loadExisting, and the displayed snapshot.
      assertEquals(fixture.redraws, 3)
    }

  test("a development session with no queried game starts over"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(None)
    assertEquals(fixture.navigation.startedOver, 1)
    assertEquals(fixture.client.loads, Vector.empty)

  test("a snapshot waiting on another seat before the game is ready reloads as that seat"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    fixture.client.answerLoad(Right(snapshot(1, active = "blue", ready = false)))
    settle().flatMap { _ =>
      assertEquals(fixture.session.currentPlayerId, "blue")
      assertEquals(fixture.navigation.shown, Vector("g" -> "red", "g" -> "blue"))
      assertEquals(fixture.client.loads, Vector("g" -> "red", "g" -> "blue"))
      assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
      assert(!fixture.clock.pending)
      fixture.client.answerLoad(Right(snapshot(1, active = "blue", ready = false)))
      settle()
    }.map { _ =>
      assertEquals(fixture.session.viewedDrafts.context,
        Some(BoardSelectionContext("g", "blue", 1)))
      assert(fixture.clock.pending)
    }

  test("a transient load failure disconnects, and reconnect loads again"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    val down = GameClientFailure.NetworkFailure("down")
    fixture.client.answerLoad(Left(down))
    settle().flatMap { _ =>
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Disconnected(down))
      assertEquals(fixture.session.shownFailure, Some(down))
      assert(!fixture.session.controlsAvailable)
      fixture.session.reconnectSession()
      assertEquals(fixture.session.shownFailure, None)
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connecting)
      assertEquals(fixture.client.loads.size, 2)
      fixture.client.answerLoad(Right(snapshot(1)))
      settle()
    }.map { _ =>
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connected)
    }

  test("a trusted seat refused by a poll clears the projection and needs its seat link"):
    seated().flatMap { fixture =>
      fixture.clock.fire()
      val refused = GameClientFailure.HttpFailure(401, "unauthorized", "no seat")
      fixture.client.answerLoad(Left(refused))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection, None)
        assertEquals(fixture.session.shownFailure, Some(refused))
        assert(TableSession.needsSeatLink(fixture.session.trusted, refused))
        assert(!fixture.clock.pending)
      }
    }

  test("a development 403 on a poll keeps the position on display and keeps polling"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fire()
      val refused = GameClientFailure.HttpFailure(403, "forbidden", "no")
      fixture.client.answerLoad(Left(refused))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(2L))
        assertEquals(fixture.session.shownFailure, Some(refused))
        assert(fixture.clock.pending)
      }
    }

  test("a poll at the same position keeps polling, and an advancing one is displayed"):
    displayed(2).flatMap { fixture =>
      val before = fixture.redraws
      fixture.clock.fire()
      fixture.client.answerLoad(Right(snapshot(2)))
      settle().flatMap { _ =>
        assertEquals(fixture.redraws, before)
        assert(fixture.clock.pending)
        fixture.clock.fire()
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(3L))
        assertEquals(fixture.session.viewedDrafts.context,
          Some(BoardSelectionContext("g", "red", 3)))
        assertEquals(fixture.redraws, before + 1)
        assert(fixture.clock.pending)
      }
    }

  test("a transient poll failure disconnects and stops polling"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fire()
      val down = GameClientFailure.NetworkFailure("down")
      fixture.client.answerLoad(Left(down))
      settle().map { _ =>
        assertEquals(fixture.session.viewedConnection, ServerConnectionState.Disconnected(down))
        assert(!fixture.clock.pending)
      }
    }

  /** Spec, "The trusted `store` records success against `capture`". */
  test("a trusted seat takes the viewer from its first snapshot and ends connected"):
    val fixture = new Fixture(trusted = true, player = "")
    fixture.session.open(None)
    assertEquals(fixture.client.loads, Vector("g" -> ""))
    fixture.client.answerLoad(Right(snapshot(1, viewer = Some("red"))))
    settle().map { _ =>
      assertEquals(fixture.session.currentPlayerId, "red")
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connected)
      assert(fixture.session.controlsAvailable)
      assertEquals(fixture.session.viewedDrafts.context,
        Some(BoardSelectionContext("g", "red", 1)))
      assertEquals(fixture.navigation.shown, Vector.empty)
    }

  test("a trusted snapshot for another viewer stops the session and asks for the seat link"):
    seated().flatMap { fixture =>
      fixture.clock.fire()
      fixture.client.answerLoad(Right(snapshot(1, viewer = Some("blue"))))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection, None)
        assertEquals(fixture.session.shownFailure, Some(GameClientFailure.HttpFailure(403,
          "seat-changed", "Open your assigned seat link.")))
        assert(!fixture.clock.pending)
      }
    }

  test("a trusted seat ignores the development calls and never shows its address"):
    seated().map { fixture =>
      fixture.session.loadSession("other", "blue")
      fixture.session.createGame()
      assertEquals(fixture.session.currentGameId, "g")
      assertEquals(fixture.session.currentPlayerId, "red")
      assertEquals(fixture.client.loads.size, 1)
      assertEquals(fixture.navigation.shown, Vector.empty)
      assertEquals(fixture.navigation.startedOver, 0)
    }

  test("a stale-position submit empties the drafts, reloads, and keeps the notice"):
    displayed(2).flatMap { fixture =>
      fixture.session.send(GameCommand.BeginRest, Vector.empty)
      assertEquals(fixture.client.submits, Vector(2L -> GameCommand.BeginRest))
      val stale = GameClientFailure.StalePosition("moved")
      fixture.client.answerSubmit(Left(stale))
      settle().flatMap { _ =>
        assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
        assertEquals(fixture.client.loads.size, 2)
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(3L))
        assertEquals(fixture.session.shownFailure, Some(stale))
      }
    }

  test("the flow's preview carries the session's game and seat"):
    displayed(2).map { fixture =>
      val request = MajorActionPreviewRequest(2L, "recover", Map.empty)
      fixture.session.preview(request)
      assertEquals(fixture.client.previews, Vector(("g", "red", request)))
    }

  test("fail shows the failure and redraws; replaceDrafts does not redraw"):
    displayed(2).map { fixture =>
      val before = fixture.redraws
      fixture.session.replaceDrafts(SessionDrafts.empty)
      assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
      assertEquals(fixture.redraws, before)
      val down = GameClientFailure.NetworkFailure("down")
      fixture.session.fail(down)
      assertEquals(fixture.session.shownFailure, Some(down))
      assertEquals(fixture.redraws, before + 1)
    }

  test("only a trusted 401 or 403 needs the seat link"):
    val http = (status: Int) => GameClientFailure.HttpFailure(status, "code", "detail")
    assert(TableSession.needsSeatLink(trusted = true, http(401)))
    assert(TableSession.needsSeatLink(trusted = true, http(403)))
    assert(!TableSession.needsSeatLink(trusted = false, http(403)))
    assert(!TableSession.needsSeatLink(trusted = true, http(500)))
    assert(!TableSession.needsSeatLink(trusted = true,
      GameClientFailure.NetworkFailure("down")))
