package oathdigital.frontend

import oathdigital.protocol.projection.{LogEntryWire, LogSectionWire,
  LogSpanWire, LogTakenBackWire}
import org.scalajs.dom
import scala.scalajs.js

/** Builders for the log lines and the content box the log suites draw. */
private[frontend] object TestLog:
  /** A log entry with every field named at the call. */
  def entry(sequence: Long, kind: String, depth: Int,
      spans: Vector[LogSpanWire], section: Option[LogSectionWire] = None,
      takenBack: Option[LogTakenBackWire] = None): LogEntryWire =
    LogEntryWire(sequence = sequence, ordinal = 0, kind = kind, depth = depth,
      spans = spans, section = section, takenBack = takenBack)

  /** A log entry that reads `text` as one plain span. */
  def textEntry(sequence: Long, kind: String, depth: Int, text: String,
      section: Option[LogSectionWire] = None,
      takenBack: Option[LogTakenBackWire] = None): LogEntryWire =
    entry(sequence, kind, depth, Vector(LogSpanWire("text", text)), section,
      takenBack)

  /** A content box jsdom gives a size: `height` tall, `scrolled` from the
    * top, holding `total` pixels. `scrollTop` is a plain writable property
    * that jsdom does not clamp, so scrolling to the end sets it to `total`
    * (the box's `scrollHeight`), not to `total - height`.
    */
  def sizedBox(total: Int, height: Int, scrolled: Double): dom.html.Element =
    val content = dom.document.createElement("div")
      .asInstanceOf[dom.html.Element]
    def define(name: String, value: js.Any, writable: Boolean) =
      js.Object.defineProperty(content, name, js.Dynamic.literal(value = value,
        writable = writable, configurable = true)
        .asInstanceOf[js.PropertyDescriptor])
    define("scrollHeight", total, writable = false)
    define("clientHeight", height, writable = false)
    define("scrollTop", scrolled, writable = true)
    content
