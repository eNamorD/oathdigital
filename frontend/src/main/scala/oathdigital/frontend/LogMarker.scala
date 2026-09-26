package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js
import scala.util.Try

/** Where the log's last-looked marker lives (spec, "Divider"). A seam so the
  * suites can supply a store: the jsdom harness makes the real one throw.
  */
private[frontend] trait LogStore:
  def read(key: String): Option[String]
  def write(key: String, value: String): Unit

private[frontend] object LogStore:
  /** The browser's `localStorage`, or nothing when the browser refuses it,
    * by throwing or by having none. A refused store means no divider, never a
    * failure.
    */
  def browser: Option[LogStore] =
    Try(dom.window.localStorage).toOption
      .filter(storage => !js.isUndefined(storage) && storage != null).map(storage =>
      new LogStore:
        def read(key: String): Option[String] =
          Try(Option(storage.getItem(key))).toOption.flatten
        def write(key: String, value: String): Unit =
          Try(storage.setItem(key, value)).getOrElse(()))

/** The reader's place in one seat's log: the sequence of the last entry held
  * once a list has stayed at the end for a second. A seat with no id is an
  * observer and keeps no place.
  */
private[frontend] final class LogMarker(store: Option[LogStore],
    schedule: (() => Unit) => (() => Unit) = LogMarker.afterASecond):
  private var key = Option.empty[String]
  private var latest = Option.empty[Long]
  private var cancel = Option.empty[() => Unit]

  /** Starts on a seat's log and returns the sequence stored for it. */
  def open(gameId: String, seatId: String): Option[Long] =
    stop()
    latest = None
    key = Option.when(seatId.nonEmpty && store.nonEmpty)(LogMarker.key(gameId, seatId))
    for
      name <- key
      value <- store.flatMap(_.read(name))
      sequence <- value.toLongOption
    yield sequence

  /** Called whenever a list moves or grows. The second starts when a list
    * reaches the end and is not restarted by later entries; leaving the end
    * cancels it.
    */
  def observe(atEnd: Boolean, last: Option[Long]): Unit =
    latest = last
    if !atEnd || last.isEmpty then stop()
    else if cancel.isEmpty && key.nonEmpty then
      cancel = Some(schedule(() => { cancel = None; write() }))

  /** On unload: a mark still waiting for its second is written now. */
  def flush(): Unit =
    if cancel.nonEmpty then
      stop()
      write()

  private def stop(): Unit =
    cancel.foreach(_())
    cancel = None

  private def write(): Unit =
    for name <- key; target <- store; sequence <- latest do
      target.write(name, sequence.toString)

private[frontend] object LogMarker:
  def key(gameId: String, seatId: String): String = s"oath.log.seen.$gameId.$seatId"

  def afterASecond(run: () => Unit): () => Unit =
    val handle = dom.window.setTimeout(() => run(), 1000)
    () => dom.window.clearTimeout(handle)
