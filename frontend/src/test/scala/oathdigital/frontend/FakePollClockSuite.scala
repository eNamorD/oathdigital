package oathdigital.frontend

import scala.concurrent.Future

/** The manual clock the polling suites use, held to the clock contract.
  * Its "time comes" is firing every active task by hand.
  */
class FakePollClockSuite extends PollClockContract[FakePollClock]:
  protected def make() = new FakePollClock

  protected def timeComesFor(clock: FakePollClock): Future[Unit] =
    clock.activeDelays.foreach(_ => clock.fireNext())
    Future.successful(())
