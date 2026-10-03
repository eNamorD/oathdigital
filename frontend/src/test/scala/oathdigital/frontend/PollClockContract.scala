package oathdigital.frontend

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** What every `PollClock` promises: a task runs once when its delay is up,
  * and a task that was cancelled never runs. `FakePollClock`, which the
  * polling suites use, and `BrowserPollClock`, over real timers, both run
  * it, so the fake cannot drift from the real clock. Each test returns a
  * `Future` built on the one `timeComesFor` completes.
  */
private[frontend] abstract class PollClockContract[C <: PollClock]
    extends munit.FunSuite:
  /** A new clock with nothing scheduled. */
  protected def make(): C
  /** Lets the time of every task scheduled so far come; completes once those
    * tasks have run. */
  protected def timeComesFor(clock: C): Future[Unit]

  /** A delay of a few milliseconds, so the real clock stays quick. */
  private val delayMillis = 2

  test("a scheduled task runs once when its time comes"):
    val clock = make()
    var runs = 0
    clock.schedule(delayMillis)(() => runs += 1)
    assertEquals(runs, 0)
    timeComesFor(clock).flatMap { _ =>
      assertEquals(runs, 1)
      timeComesFor(clock)
    }.map(_ => assertEquals(runs, 1))

  test("a cancelled task never runs, while a task beside it does"):
    val clock = make()
    val ran = mutable.ArrayBuffer.empty[String]
    val cancelled = clock.schedule(delayMillis)(() => ran += "cancelled")
    clock.schedule(delayMillis)(() => ran += "kept")
    cancelled.cancel()
    timeComesFor(clock).map: _ =>
      assertEquals(ran.toVector, Vector("kept"))
