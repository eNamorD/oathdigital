package oathdigital.engine

import oathdigital.testkit.RunningTotalEvolution

class EventReplayEngineSuite extends munit.FunSuite:
  private val engine = new EventReplayEngine(RunningTotalEvolution)
  private def recorded(events: Int*) = events.toVector.zipWithIndex.map {
    case (event, index) => RecordedEvent(index.toLong, event) }

  test("scan pairs every event with the state before and after it"):
    assertEquals(engine.scan(recorded(2, 3, 5)), Right(Vector(
      ReplayStep(RecordedEvent(0L, 2), 0, 2),
      ReplayStep(RecordedEvent(1L, 3), 2, 5),
      ReplayStep(RecordedEvent(2L, 5), 5, 10))))

  test("replay is the last step's after, or the initial state when empty"):
    assertEquals(engine.replay(recorded(2, 3, 5)), Right(10))
    assertEquals(engine.replay(recorded()), Right(0))
    assertEquals(engine.scan(recorded()), Right(Vector.empty))

  test("scan and replay report the same corrupt event"):
    val corrupt = recorded(2, -1, 5)
    assertEquals(engine.scan(corrupt), Left(EventReplayFailure(1L, "negative -1")))
    assertEquals(engine.replay(corrupt), Left(EventReplayFailure(1L, "negative -1")))
