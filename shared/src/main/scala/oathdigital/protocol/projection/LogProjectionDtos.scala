package oathdigital.protocol.projection

/** One span of a log entry on the wire. `kind` is `text`, `player`, `card`,
  * `site`, `amount`, `cost` or `dice`; `text` is always present, so a client
  * that ignores kinds still shows a sentence. A card shown by its back is a
  * `text` span: no field carries a hidden card's id. A `dice` span's `id`
  * holds its face wire names separated by spaces, and its `unit` names the
  * die, `attack` or `defense`. */
final case class LogSpanWire(kind: String, text: String,
    id: Option[String] = None, value: Option[Int] = None,
    unit: Option[String] = None)

/** One log entry; `(sequence, ordinal)` is its stable key. */
final case class LogEntryWire(sequence: Long, ordinal: Int, kind: String,
    depth: Int, spans: Vector[LogSpanWire])

/** Every entry with `sequence >= after`, formatted for one seat, and the
  * cursor to ask with next time. */
final case class LogPageWire(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntryWire])
