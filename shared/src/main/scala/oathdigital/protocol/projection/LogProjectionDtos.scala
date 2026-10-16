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

/** One log entry (game log design, "Output"). `(sequence, ordinal)` is its
  * stable identity: `sequence` is the journal index of the event that posted
  * it, `ordinal` its position among the entries that event posted. Depth 0
  * is a headline, depth 1 a line; only a line can carry a section.
  */
final case class LogEntry(sequence: Long, ordinal: Int, kind: LogKind,
    spans: Vector[LogSpan], section: Option[LogSection] = None,
    takenBack: Option[LogTakenBack] = None):
  def depth: Int = kind.depth

/** The taken-back group an entry belongs to (undo design, "Game Log"):
  * `group` is the sequence of the group's first entry, the same for every
  * entry of one contiguous run of voided entries, and `header` the line
  * that opens it, "p1 took back the following moves:". */
final case class LogTakenBack(group: Long, header: Vector[LogSpan])

/** The action an entry belongs to (log action sections design, "Server"):
  * `id` is the journal index of the action's first event, the same for
  * every entry of one action, and `action` its procedure key, or `minor`
  * for a minor action. A client draws one band per section. */
final case class LogSection(id: Long, action: String)

/** `key` is the kind's wire spelling, and `depth` where its entries sit: 0
  * is a headline, 1 a line. The kind decides it, so no caller can post a
  * headline at a line's depth and no wire can carry a disagreeing pair. */
enum LogKind(val key: String, val depth: Int):
  case Round extends LogKind("round", 0)
  case Turn extends LogKind("turn", 0)
  /** Wake, Act or Rest, under its turn's headline. */
  case Phase extends LogKind("phase", 0)
  /** The tag that opens an action's band: its name and the Supply it spent
    * (log action sections design, "The tag entry"). */
  case Section extends LogKind("section", 1)
  case Action extends LogKind("action", 1)
  case Decision extends LogKind("decision", 1)
  case Roll extends LogKind("roll", 1)
  case Delta extends LogKind("delta", 1)
  case Trigger extends LogKind("trigger", 1)
  case Victory extends LogKind("victory", 0)

/** One piece of an entry's text: plain words or a typed reference. `text` is
  * what a reader that ignores the kind shows. A card a viewer may not
  * identify is never a `Card`: it is `Text` naming its back.
  */
sealed trait LogSpan extends Product with Serializable:
  def text: String
object LogSpan:
  final case class Text(value: String) extends LogSpan:
    def text: String = value
  final case class Player(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Card(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Site(id: String, name: String) extends LogSpan:
    def text: String = name
  final case class Amount(value: Int, unit: String) extends LogSpan:
    def text: String = s"$value $unit"
  /** One roll of one die kind, drawn as dice: `faces` are the wire names the
    * client's die chips know, `names` the rulebook's ("Attack" and "Defend"). */
  final case class Dice(die: String, faces: Vector[String],
      names: Vector[String]) extends LogSpan:
    def text: String = names.mkString(", ")
  /** What an action spent, drawn apart from the sentence (game log design,
    * "Supply"). Supply is the only thing an action spends, so the word is
    * the case's, not a field: a second currency is a one-parameter change
    * the compiler routes to every site. */
  final case class Cost(value: Int) extends LogSpan:
    def text: String = s"−$value Supply"
  /** More cards than a line names inline, read as "6 cards": the list a
    * client opens, in order, each card's face if its viewer may identify it
    * and its back otherwise (catalog batch 2, N5). */
  final case class Cards(cards: Vector[CardDetailsProjection]) extends LogSpan:
    def text: String = s"${cards.size} cards"
