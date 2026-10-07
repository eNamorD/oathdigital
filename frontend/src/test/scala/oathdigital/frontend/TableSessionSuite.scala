package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => GameCommand, MajorActionPreviewRequest,
  MajorActionPreviewResponse, ModifierInvocation}

import oathdigital.protocol.projection.{LogEntryWire, LogPageWire, LogSpanWire,
  LogTakenBackWire}
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.setTimeout

/** The table session over a client spy, a fake poll clock and a navigation
  * spy: which loads it makes, how it routes and fails, when it polls,
  * and what it shows. The jsdom suite proves the wiring; this proves the
  * session.
  */
class TableSessionSuite extends munit.FunSuite:
  private type Answer[A] = Either[GameClientFailure, A]

  /** A `load` the client received. */
  private final case class Load(gameId: String, selectedPlayerId: String)

  /** A `submit` the client received, with the position it expected. */
  private final case class Submit(expectedNextSequence: Long,
      command: GameCommand)

  /** A `preview` the client received. */
  private final case class Preview(gameId: String, selectedPlayerId: String,
      request: MajorActionPreviewRequest)

  /** A `loadLog` the client received. */
  private final case class LogLoad(gameId: String, selectedPlayerId: String,
      after: Long)

  /** A session the navigation was told to show. */
  private final case class Shown(gameId: String, playerId: String)

  /** Spy of `GameClient`: records every load, submit, preview and log load.
    * It computes no answer: each call returns a promise that the test
    * completes through `answerLoad` and its siblings, oldest first.
    */
  private final class RecordingClient extends GameClient:
    var loads = Vector.empty[Load]
    var submits = Vector.empty[Submit]
    var previews = Vector.empty[Preview]
    private var pendingLoads = Vector.empty[Promise[Answer[GameProjection]]]
    private var pendingSubmits = Vector.empty[Promise[Answer[GameProjection]]]
    private var pendingPreviews = Vector.empty[Promise[Answer[MajorActionPreviewResponse]]]
    def load(gameId: String, selectedPlayerId: String) =
      loads :+= Load(gameId, selectedPlayerId)
      val promise = Promise[Answer[GameProjection]]()
      pendingLoads :+= promise
      promise.future
    def submit(gameId: String, selectedPlayerId: String, expectedNextSequence: Long,
        command: GameCommand, orderedModifiers: Vector[ModifierInvocation]) =
      submits :+= Submit(expectedNextSequence, command)
      val promise = Promise[Answer[GameProjection]]()
      pendingSubmits :+= promise
      promise.future
    def preview(gameId: String, selectedPlayerId: String,
        request: MajorActionPreviewRequest) =
      previews :+= Preview(gameId, selectedPlayerId, request)
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
    var logs = Vector.empty[LogLoad]
    private var pendingLogs = Vector.empty[Promise[Answer[LogPageWire]]]
    def loadLog(gameId: String, selectedPlayerId: String, after: Long) =
      logs :+= LogLoad(gameId, selectedPlayerId, after)
      val promise = Promise[Answer[LogPageWire]]()
      pendingLogs :+= promise
      promise.future
    def answerLog(result: Answer[LogPageWire]): Unit =
      val promise = pendingLogs.head
      pendingLogs = pendingLogs.tail
      promise.success(result)

  /** Spy of `Navigation`: records the sessions shown, the start-overs and
    * the reloads.
    */
  private final class RecordingNavigation extends Navigation:
    var shown = Vector.empty[Shown]
    var startedOver = 0
    var reloads = 0
    def showSession(gameId: String, playerId: String): Unit =
      shown :+= Shown(gameId, playerId)
    def startOver(): Unit = startedOver += 1
    def reload(): Unit = reloads += 1

  private final class Fixture(trusted: Boolean, player: String = "red"):
    val client = new RecordingClient
    val clock = new FakePollClock
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
    TestProjections.actingProjection(gameId = "g", nextSequence = sequence,
      activePlayer = Some(active), players = players, ready = ready)
      .copy(viewerPlayerId = viewer)

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
    assertEquals(fixture.client.loads, Vector(Load("g", "red")))
    assertEquals(fixture.navigation.shown, Vector(Shown("g", "red")))
    assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connecting)
    fixture.client.answerLoad(Right(snapshot(3)))
    settle().map { _ =>
      val session = fixture.session
      assertEquals(session.viewedProjection.map(_.nextSequence), Some(3L))
      assertEquals(session.viewedDrafts.context, Some(BoardSelectionContext("g", "red", 3)))
      assertEquals(session.viewedConnection, ServerConnectionState.Connected)
      assert(session.controlsAvailable)
      // The session polls at the poller's visible interval, 5000 ms.
      assertEquals(fixture.clock.delaysAsked, Vector(5000))
      assert(fixture.clock.hasPendingTask)
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
      assertEquals(fixture.navigation.shown,
        Vector(Shown("g", "red"), Shown("g", "blue")))
      assertEquals(fixture.client.loads,
        Vector(Load("g", "red"), Load("g", "blue")))
      assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
      assert(!fixture.clock.hasPendingTask)
      fixture.client.answerLoad(Right(snapshot(1, active = "blue", ready = false)))
      settle()
    }.map { _ =>
      assertEquals(fixture.session.viewedDrafts.context,
        Some(BoardSelectionContext("g", "blue", 1)))
      assert(fixture.clock.hasPendingTask)
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
      fixture.clock.fireNext()
      val refused = GameClientFailure.HttpFailure(401, "unauthorized", "no seat")
      fixture.client.answerLoad(Left(refused))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection, None)
        assertEquals(fixture.session.shownFailure, Some(refused))
        assert(TableSession.needsSeatLink(fixture.session.trusted, refused))
        assert(!fixture.clock.hasPendingTask)
      }
    }

  test("a development 403 on a poll keeps the position on display and keeps polling"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fireNext()
      val refused = GameClientFailure.HttpFailure(403, "forbidden", "no")
      fixture.client.answerLoad(Left(refused))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(2L))
        assertEquals(fixture.session.shownFailure, Some(refused))
        assert(fixture.clock.hasPendingTask)
      }
    }

  test("a poll at the same position keeps polling, and an advancing one is displayed"):
    displayed(2).flatMap { fixture =>
      val before = fixture.redraws
      fixture.clock.fireNext()
      fixture.client.answerLoad(Right(snapshot(2)))
      settle().flatMap { _ =>
        assertEquals(fixture.redraws, before)
        assert(fixture.clock.hasPendingTask)
        fixture.clock.fireNext()
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(3L))
        assertEquals(fixture.session.viewedDrafts.context,
          Some(BoardSelectionContext("g", "red", 3)))
        assertEquals(fixture.redraws, before + 1)
        assert(fixture.clock.hasPendingTask)
      }
    }

  /** Spec, "An unparseable form": the failure moves from a blank pane to a
    * stopped session with the last good position still on screen.
    */
  test("an unsupported projection stops polling, keeps the position and asks for a reload"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fireNext()
      val unsupported = GameClientFailure.UnsupportedProjection(
        "$.walkerDecision.query.form", "unknown decision form 'choose-two'")
      fixture.client.answerLoad(Left(unsupported))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(2L))
        assertEquals(fixture.session.shownFailure, Some(unsupported))
        assert(fixture.session.clientOutOfDate)
        assert(!fixture.clock.hasPendingTask)
      }
    }

  /** The regression the derived flag prevents: an unsupported projection
    * leaves the session Connected, so a control on the retained position is
    * still live. A submit the server accepts can carry the table past the
    * park this build could not parse, and a LATCHED flag would then have
    * labelled the next unrelated failure a stale bundle.
    */
  test("recovering past the unparseable park clears the out-of-date notice"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fireNext()
      fixture.client.answerLoad(Left(GameClientFailure.UnsupportedProjection(
        "$.walkerDecision.query.form", "unknown decision form 'choose-two'")))
      settle().flatMap { _ =>
        assert(fixture.session.clientOutOfDate)
        fixture.session.reconnectSession()
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.flatMap { _ =>
        assert(!fixture.session.clientOutOfDate)
        assertEquals(fixture.session.shownFailure, None)
        fixture.clock.fireNext()
        fixture.client.answerLoad(Left(
          GameClientFailure.HttpFailure(500, "internal", "boom")))
        settle()
      }.map { _ =>
        assert(!fixture.session.clientOutOfDate)
        assertEquals(fixture.session.shownFailure.map(_.message),
          Some("HTTP 500 internal: boom"))
      }
    }

  test("an ordinary decode failure keeps polling and asks for no reload"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fireNext()
      fixture.client.answerLoad(Left(
        GameClientFailure.DecodeFailure("$.phase", "expected string")))
      settle().map { _ =>
        assert(!fixture.session.clientOutOfDate)
        assert(fixture.clock.hasPendingTask)
      }
    }

  test("a transient poll failure disconnects and stops polling"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fireNext()
      val down = GameClientFailure.NetworkFailure("down")
      fixture.client.answerLoad(Left(down))
      settle().map { _ =>
        assertEquals(fixture.session.viewedConnection, ServerConnectionState.Disconnected(down))
        assert(!fixture.clock.hasPendingTask)
      }
    }

  /** Spec, "The trusted `store` records success against `capture`". */
  test("a trusted seat takes the viewer from its first snapshot and ends connected"):
    val fixture = new Fixture(trusted = true, player = "")
    fixture.session.open(None)
    assertEquals(fixture.client.loads, Vector(Load("g", "")))
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
      fixture.clock.fireNext()
      fixture.client.answerLoad(Right(snapshot(1, viewer = Some("blue"))))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection, None)
        assertEquals(fixture.session.shownFailure, Some(GameClientFailure.HttpFailure(403,
          "seat-changed", "Open your assigned seat link.")))
        assert(!fixture.clock.hasPendingTask)
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
      assertEquals(fixture.client.submits,
        Vector(Submit(2L, GameCommand.BeginRest)))
      val before = fixture.redraws
      val stale = GameClientFailure.StalePosition("moved")
      fixture.client.answerSubmit(Left(stale))
      settle().flatMap { _ =>
        // Spec, Fix 1: the failure is drawn before the reload lands.
        assertEquals(fixture.redraws, before + 1)
        assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
        assertEquals(fixture.client.loads.size, 2)
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(3L))
        assertEquals(fixture.session.shownFailure, Some(stale))
      }
    }

  /** Spec, Fix 2. */
  test("a preview answered after the session changed never lands"):
    displayed(2).flatMap { fixture =>
      val answer = fixture.session.preview(MajorActionPreviewRequest(2L, "recover", Map.empty))
      fixture.session.reconnectSession()
      fixture.client.answerPreview(Right(MajorActionPreviewResponse(2L, "recover",
        Vector.empty, Vector.empty)))
      settle().map(_ => assert(!answer.isCompleted))
    }

  test("a preview answered under the same session lands"):
    displayed(2).flatMap { fixture =>
      val answer = fixture.session.preview(MajorActionPreviewRequest(2L, "recover", Map.empty))
      val response = MajorActionPreviewResponse(2L, "recover", Vector.empty,
        Vector.empty)
      fixture.client.answerPreview(Right(response))
      answer.map(result => assertEquals(result, Right(response)))
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

  private def page(after: Long, next: Long): LogPageWire =
    LogPageWire("g", after, next, (after until next).toVector.map(sequence =>
      LogEntryWire(sequence, 0, "action", 1,
        Vector(LogSpanWire("text", s"line $sequence")))))

  test("a displayed position fetches the log from the start and appends it"):
    displayed(3).flatMap { fixture =>
      assertEquals(fixture.client.logs, Vector(LogLoad("g", "red", 0L)))
      fixture.client.answerLog(Right(page(0, 3)))
      settle().map { _ =>
        assertEquals(fixture.session.viewedLog.map(_.sequence),
          Vector(0L, 1L, 2L))
      }
    }

  test("the log is asked for again only when the position advances"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Right(page(0, 3)))
      settle().flatMap { _ =>
        fixture.clock.fireNext()
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.flatMap { _ =>
        assertEquals(fixture.client.logs.size, 1)
        fixture.clock.fireNext()
        fixture.client.answerLoad(Right(snapshot(5)))
        settle()
      }.map { _ =>
        assertEquals(fixture.client.logs.last, LogLoad("g", "red", 3L))
      }
    }

  test("a failed log request leaves the log and is retried at the next advance"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Left(GameClientFailure.NetworkFailure("down")))
      settle().flatMap { _ =>
        assertEquals(fixture.session.viewedLog, Vector.empty)
        assertEquals(fixture.session.shownFailure, None)
        fixture.clock.fireNext()
        fixture.client.answerLoad(Right(snapshot(4)))
        settle()
      }.map { _ =>
        assertEquals(fixture.client.logs,
          Vector(LogLoad("g", "red", 0L), LogLoad("g", "red", 0L)))
      }
    }

  test("a session change resets the log and ignores the old answer"):
    displayed(3).flatMap { fixture =>
      fixture.session.loadSession("h", "blue")
      fixture.client.answerLog(Right(page(0, 3)))
      settle().map { _ =>
        assertEquals(fixture.session.viewedLog, Vector.empty)
      }
    }

  /** The whole log up to `next`, with every entry from `from` on taken back
    * by red. */
  private def pageTakenBackFrom(from: Long, next: Long): LogPageWire =
    val whole = page(0, next)
    whole.copy(entries = whole.entries.map(entry =>
      if entry.sequence < from then entry
      else entry.copy(takenBack = Some(LogTakenBackWire(from,
        Vector(LogSpanWire("text", "Red took back the following moves:")))))))

  /** A poll answered with `snapshot(sequence)` carrying `revision`. */
  private def pollAnswered(fixture: Fixture, sequence: Long,
      revision: Option[Long]): Future[Unit] =
    fixture.clock.fireNext()
    fixture.client.answerLoad(Right(snapshot(sequence)
      .copy(logRevision = revision)))
    settle()

  test("a new log revision refetches the whole log, keeping the old one on show until it lands"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Right(page(0, 3)))
      settle().flatMap(_ => pollAnswered(fixture, 4, Some(3L))).flatMap { _ =>
        assertEquals(fixture.client.logs.last, LogLoad("g", "red", 0L))
        assertEquals(fixture.session.viewedLog.map(_.sequence),
          Vector(0L, 1L, 2L))
        fixture.client.answerLog(Right(pageTakenBackFrom(2, 4)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedLog, pageTakenBackFrom(2, 4).entries)
      }
    }

  test("an unchanged log revision only appends"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Right(page(0, 3)))
      settle().flatMap(_ => pollAnswered(fixture, 5, None)).map { _ =>
        assertEquals(fixture.client.logs.last, LogLoad("g", "red", 3L))
      }
    }

  test("a log answer asked for before the revision changed lands on nothing"):
    displayed(3).flatMap { fixture =>
      fixture.client.answerLog(Right(page(0, 3)))
      settle()
        .flatMap(_ => pollAnswered(fixture, 5, None))
        .flatMap(_ => pollAnswered(fixture, 6, Some(5L)))
        .flatMap { _ =>
          assertEquals(fixture.client.logs.map(_.after), Vector(0L, 3L, 0L))
          fixture.client.answerLog(Right(page(3, 5)))
          settle()
        }.flatMap { _ =>
          assertEquals(fixture.session.viewedLog.map(_.sequence),
            Vector(0L, 1L, 2L))
          fixture.client.answerLog(Right(pageTakenBackFrom(3, 6)))
          settle()
        }.map { _ =>
          assertEquals(fixture.session.viewedLog, pageTakenBackFrom(3, 6).entries)
        }
    }
