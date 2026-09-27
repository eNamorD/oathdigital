package oathdigital.protocol.projection

/** One span of a log entry on the wire. `kind` is `text`, `player`, `card`,
  * `site`, `amount`, `cost`, `dice` or `cards`; `text` is always present, so
  * a client that ignores kinds still shows a sentence. A card shown by its
  * back is a `text` span: no field carries a hidden card's id. A `dice`
  * span's `id` holds its face wire names separated by spaces, and its `unit`
  * names the die, `attack` or `defense`. A `cards` span's `cards` are the
  * cards it lists, in order, each a face or a back (a hidden card projects
  * as `hidden`). */
final case class LogSpanWire(kind: String, text: String,
    id: Option[String] = None, value: Option[Int] = None,
    unit: Option[String] = None,
    cards: Vector[CardDetailsProjection] = Vector.empty)

/** One log entry; `(sequence, ordinal)` is its stable key. */
final case class LogEntryWire(sequence: Long, ordinal: Int, kind: String,
    depth: Int, spans: Vector[LogSpanWire])

/** Every entry with `sequence >= after`, formatted for one seat, and the
  * cursor to ask with next time. */
final case class LogPageWire(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntryWire])
