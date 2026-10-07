package oathdigital.application.gamelog

import oathdigital.protocol.projection.CardDetailsProjection

/** One line of the game log (spec, "Output"). `(sequence, ordinal)` is its
  * stable identity: `sequence` is the journal index of the event that posted
  * it, `ordinal` its position among the entries that event posted. Depth 0
  * is a headline, depth 1 a line; only a line can carry a section.
  */
final case class LogEntry(sequence: Long, ordinal: Int, kind: LogKind,
    depth: Int, spans: Vector[LogSpan], section: Option[LogSection] = None,
    takenBack: Option[LogTakenBack] = None)

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

/** `key` is the kind's wire spelling. */
enum LogKind(val key: String):
  case Round extends LogKind("round")
  case Turn extends LogKind("turn")
  /** Wake, Act or Rest, under its turn's headline. */
  case Phase extends LogKind("phase")
  /** The tag that opens an action's band: its name and the Supply it spent
    * (log action sections design, "The tag entry"). */
  case Section extends LogKind("section")
  case Action extends LogKind("action")
  case Decision extends LogKind("decision")
  case Roll extends LogKind("roll")
  case Delta extends LogKind("delta")
  case Trigger extends LogKind("trigger")
  case Victory extends LogKind("victory")

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
  /** What an action spent, drawn apart from the sentence (spec, "Supply"). */
  final case class Cost(value: Int, unit: String) extends LogSpan:
    def text: String = s"−$value $unit"
  /** More cards than a line names inline, read as "6 cards": the list a
    * client opens, in order, each card's face if its viewer may identify it
    * and its back otherwise (catalog batch 2, N5). */
  final case class Cards(cards: Vector[CardDetailsProjection]) extends LogSpan:
    def text: String = s"${cards.size} cards"

/** An entry before it has a key: what one event posts, in order. */
private[gamelog] final case class Posted(kind: LogKind, depth: Int,
    spans: Vector[LogSpan], section: Option[LogSection] = None)
private[gamelog] object Posted:
  def headline(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 0, spans)
  def line(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 1, spans)
