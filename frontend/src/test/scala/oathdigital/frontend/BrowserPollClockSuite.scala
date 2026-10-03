package oathdigital.frontend

import org.scalajs.dom
import scala.concurrent.{Future, Promise}

/** `BrowserPollClock`, held to the clock contract over real timers. Its
  * "time comes" is a wait of a few milliseconds, longer than the contract's
  * delay, for the timers to run.
  */
class BrowserPollClockSuite extends PollClockContract[BrowserPollClock]:
  protected def make() = new BrowserPollClock

  protected def timeComesFor(clock: BrowserPollClock): Future[Unit] =
    val waited = Promise[Unit]()
    dom.window.setTimeout(() => waited.success(()), 10)
    waited.future
