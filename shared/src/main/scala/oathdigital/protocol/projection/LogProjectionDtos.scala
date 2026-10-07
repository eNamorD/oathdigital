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

/** The action an entry belongs to: `id` is the same for every entry of one
  * action, `action` its procedure key (`search`, `use-power:<id>`) or
  * `minor`. A client draws one band per run of entries sharing an `id`. */
final case class LogSectionWire(id: Long, action: String)

/** One log entry; `(sequence, ordinal)` is its stable key. A `section`
  * entry is the tag that opens its section's band. */
final case class LogEntryWire(sequence: Long, ordinal: Int, kind: String,
    depth: Int, spans: Vector[LogSpanWire],
    section: Option[LogSectionWire] = None,
    takenBack: Option[LogTakenBackWire] = None)

/** The taken-back group an entry belongs to: every entry of one group
  * shares `group`, and `header` is the line a client shows above the
  * collapsed group. */
final case class LogTakenBackWire(group: Long, header: Vector[LogSpanWire])

/** Every entry with `sequence >= after`, formatted for one seat, and the
  * cursor to ask with next time. */
final case class LogPageWire(gameId: String, after: Long, nextSequence: Long,
    entries: Vector[LogEntryWire])
