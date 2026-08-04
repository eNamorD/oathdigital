package oathdigital.frontend

import org.scalajs.dom

trait PollCancellation {
  def cancel(): Unit
}

trait PollClock {
  def schedule(delayMillis: Int)(task: () => Unit): PollCancellation
}

final class BrowserPollClock extends PollClock {
  override def schedule(delayMillis: Int)(task: () => Unit) = {
    val handle = dom.window.setTimeout(() => task(), delayMillis)
    new PollCancellation {
      override def cancel(): Unit = dom.window.clearTimeout(handle)
    }
  }
}

/**
 * Schedules one player-scoped snapshot request at a time. Transport and
 * projection handling remain outside this class so it can later drive deltas
 * or push-triggered catch-up without changing session authority.
 */
final class SnapshotPollingCoordinator(
    clock: PollClock,
    requestPoll: ServerRequestIdentity => Unit,
    visibleIntervalMillis: Int = 5000,
    hiddenIntervalMillis: Int = 30000
) {
  require(visibleIntervalMillis > 0)
  require(hiddenIntervalMillis >= visibleIntervalMillis)

  private var current = Option.empty[ServerRequestIdentity]
  private var scheduled = Option.empty[PollCancellation]
  private var inFlight = false
  private var hidden = false
  private var running = false
  private var timerGeneration = 0L

  def resume(request: ServerRequestIdentity): Unit = {
    if (!running || !current.contains(request)) {
      invalidateTimer()
      current = Some(request)
      inFlight = false
      running = true
      scheduleNext()
    }
  }

  def stop(): Unit = {
    invalidateTimer()
    running = false
    inFlight = false
  }

  def visibilityChanged(isHidden: Boolean): Unit = {
    hidden = isHidden
    if (running && !inFlight) {
      invalidateTimer()
      if (hidden) scheduleNext() else beginPoll()
    }
  }

  def complete(
      request: ServerRequestIdentity,
      continuePolling: Boolean
  ): Boolean =
    if (!running || !inFlight || !current.contains(request)) false
    else {
      inFlight = false
      if (continuePolling) scheduleNext()
      else running = false
      true
    }

  def isRunning: Boolean = running
  def hasInFlightPoll: Boolean = inFlight

  private def scheduleNext(): Unit =
    if (running && !inFlight && scheduled.isEmpty)
      {
        val expectedTimerGeneration = timerGeneration
        scheduled = Some(clock.schedule(
        if (hidden) hiddenIntervalMillis else visibleIntervalMillis
        )(() =>
          if (expectedTimerGeneration == timerGeneration) beginPoll()
        ))
      }

  private def beginPoll(): Unit = {
    scheduled = None
    if (running && !inFlight) current.foreach { request =>
      inFlight = true
      requestPoll(request)
    }
  }

  private def invalidateTimer(): Unit = {
    timerGeneration += 1
    scheduled.foreach(_.cancel())
    scheduled = None
  }
}
