package oathdigital.model

/** What one power says about its own effect, for the Game Log (power log
  * lines design, section 1). `source` is the card, site or banner the line
  * starts with. `key` picks the power's template. `args` are typed
  * references, never text, so a card still passes the log's knowledge rule.
  */
final case class PowerNote(source: PowerSourceRef, key: String,
    args: Vector[NoteArg])

sealed trait NoteArg extends Product with Serializable
object NoteArg:
  final case class Player(id: PlayerId) extends NoteArg
  final case class Card(id: CardId) extends NoteArg
  final case class Site(id: SiteId) extends NoteArg
  /** An amount with its unit, "3 favor". It is always what happened, never
    * the number the card prints. */
  final case class Amount(value: Int, unit: NoteUnit) extends NoteArg
  /** A bare number, "Total: 8". */
  final case class Number(value: Int) extends NoteArg
  /** "the Order bank". */
  final case class Bank(suit: Suit) extends NoteArg
  final case class Dice(faces: Vector[DieFace]) extends NoteArg
  /** Several cards as one phrase, "Tinker, 2 Denizens and a Vision". Each
    * card still passes the log's knowledge rule. */
  final case class Cards(ids: Vector[CardId]) extends NoteArg
  final case class Banner(banner: oathdigital.model.Banner) extends NoteArg

/** `key` is the wire spelling. */
enum NoteUnit(val key: String, val one: String, val many: String):
  case Favor extends NoteUnit("favor", "favor", "favor")
  case Secret extends NoteUnit("secret", "secret", "secrets")
  case Supply extends NoteUnit("supply", "Supply", "Supply")
  case Warband extends NoteUnit("warband", "warband", "warbands")
  def word(value: Int): String = if value == 1 then one else many
object NoteUnit:
  def fromKey(key: String): Option[NoteUnit] = values.find(_.key == key)

/** One piece of a note's sentence. `Plural` picks `one` when the argument at
  * `index` is an amount or number of exactly 1, and `many` otherwise. */
enum NotePart:
  case Text(words: String)
  case Arg(index: Int)
  case Plural(index: Int, one: String, many: String)

/** One line a power can write: its key and the sentence the log renders
  * after "{source}: ". A power declares its keys and builds its notes
  * through them, so a key never lacks a template. */
final case class NoteKey(name: String, template: Vector[NotePart]):
  def apply(source: PowerSourceRef, args: NoteArg*): PowerNote =
    PowerNote(source, name, args.toVector)
object NoteKey:
  /** A phase power's own line, which replaces "Used {card}". */
  val Used: String = "used"
  /** A phase power's own line, or a variant of it for another outcome, such
    * as nothing to target: `used`, or `used.{variant}`. Either replaces
    * "Used {card}" and is its action's line. */
  def isUse(name: String): Boolean = name == Used || name.startsWith(s"$Used.")

/** What a note may read when the walker reaches it. `previous` is the states
  * before and after the leaf this command ran last, if any, so a note that
  * restates a step reads the applied amount instead of repeating the
  * operation's cap logic. A leaf that ran and changed nothing gives the same
  * state twice, so a note after it reads no change. `answered` holds the
  * action's decisions so far. */
final case class NoteStates(now: ReadyGame,
    previous: Option[(ReadyGame, ReadyGame)], answered: Vector[Answered])

/** A power's line, placed in the tree where its effect happens. The walker
  * journals it as `PowerNoted` when it reaches this node, unless `build`
  * returns `None`. It changes no state and is never part of a recorded
  * batch. `covers` drops the generic detail lines of the step before it
  * (power log lines design, "Covering"). */
final case class Note(power: PowerId, build: NoteStates => Option[PowerNote],
    covers: Boolean = false) extends Operation:
  val children: Vector[Operation] = Vector.empty
