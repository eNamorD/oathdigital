package oathdigital.frontend

import munit.Assertions.fail
import scala.collection.mutable

/** Fake of `PollClock`: keeps every task it was asked to schedule and runs
  * one only when a test calls `fireNext`, so a test steps time by hand. A
  * task that was cancelled or has run no longer counts as active.
  * `PollClockContract` holds it to what the real clock promises.
  */
private[frontend] final class FakePollClock extends PollClock:
  private final class Entry(
      val delay: Int,
      val task: () => Unit,
      var cancelled: Boolean
  )
  private val entries = mutable.ArrayBuffer.empty[Entry]

  override def schedule(delayMillis: Int)(task: () => Unit) =
    val entry = new Entry(delayMillis, task, cancelled = false)
    entries += entry
    new PollCancellation:
      override def cancel(): Unit = entry.cancelled = true

  /** The delay of every task scheduled so far, cancelled or run included. */
  def delaysAsked: Vector[Int] = entries.map(_.delay).toVector

  /** The delay of each task that is neither cancelled nor run, oldest first. */
  def activeDelays: Vector[Int] =
    entries.filterNot(_.cancelled).map(_.delay).toVector

  /** Whether any task is neither cancelled nor run. */
  def hasPendingTask: Boolean = entries.exists(!_.cancelled)

  /** The position of the task scheduled last, for `fireEvenIfCancelled`. */
  def latestIndex: Int = entries.size - 1

  /** Runs the oldest active task, once; fails when there is none. */
  def fireNext(): Unit =
    val entry = entries.find(!_.cancelled).getOrElse(
      fail("no active timer")
    )
    entry.cancelled = true
    entry.task()

  /** Runs the task at `index` whether or not it was cancelled or has run. */
  def fireEvenIfCancelled(index: Int): Unit = entries(index).task()
