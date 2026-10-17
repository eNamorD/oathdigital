package oathdigital.model

/** What one power, game rule or procedure says about its own effect, for
  * the Game Log (power log lines design, section 1; procedures journal their
  * own notes design, ruling 1). `source` is the card, site or banner the line
  * starts with, and is absent for a procedure's own line, which starts with
  * its sentence. `key` picks the writer's template. `args` are typed
  * references, never text, so a card still passes the log's knowledge rule.
  */
final case class PowerNote(source: Option[PowerSourceRef], key: String,
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
  /** "world deck" or "Cradle discard pile". The template supplies the
    * article. */
  final case class Pile(pile: SearchSource) extends NoteArg
  /** A region by its name, "Cradle". */
  final case class RegionName(region: Region) extends NoteArg
  /** A suit by its name, "Hearth" (Spinning Bee's declared suit). */
  final case class SuitName(suit: Suit) extends NoteArg
  /** An Oathkeeper goal in a sentence, "Protection" or "the People". */
  final case class Goal(goal: OathkeeperGoal) extends NoteArg
  /** Several players as one phrase, "Blue and Red". */
  final case class Players(ids: Vector[PlayerId]) extends NoteArg
  /** Several clauses as one phrase, "lost 2 warbands, burned 1 favor and
    * was sent to the Mine". The joining is the Game Log's. */
  final case class Clauses(parts: Vector[NoteClause]) extends NoteArg
  /** Warbands placed by site, "2 warbands on the Mine and 1 warband on the
    * Standing Stones". */
  final case class Placements(entries: Vector[(Int, SiteId)]) extends NoteArg

/** One clause of a joined note sentence (procedures journal their own notes
  * design, "Campaign"). Closed, because each case is something the rules do
  * and the Game Log has one way of saying each. */
sealed trait NoteClause extends Product with Serializable
object NoteClause:
  final case class LostWarbands(count: Int) extends NoteClause
  final case class BurnedFavor(count: Int) extends NoteClause
  final case class Discarded(ids: Vector[CardId]) extends NoteClause
  final case class SetAside(ids: Vector[CardId]) extends NoteClause
  final case class SentTo(site: SiteId) extends NoteClause

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
final case class NoteKey private[model] (name: String,
    template: Vector[NotePart]):
  def apply(source: PowerSourceRef, args: NoteArg*): PowerNote =
    PowerNote(Some(source), name, args.toVector)
  /** A line that starts with its sentence rather than a source: a
    * procedure's own. */
  def sourceless(args: NoteArg*): PowerNote =
    PowerNote(None, name, args.toVector)
object NoteKey:
  /** A phase power's own line, which replaces "Used {card}". */
  val Used: String = "used"
  /** A phase power's own line, or a variant of it for another outcome, such
    * as nothing to target: `used`, or `used.{variant}`. Either replaces
    * "Used {card}" and is its action's line. */
  def isUse(name: String): Boolean = name == Used || name.startsWith(s"$Used.")

/** The keys one object declares (derive note keys design, "Mechanism"). A
  * key is created through the set that declares it, so defining a key and
  * declaring it are one act and the two cannot disagree.
  *
  * `declared` is complete only once the owning object's initialiser has run,
  * so: read it through a `def`, never a `val`; declare keys as plain `val`s
  * in the object that owns the set, never `lazy val`s, whose key is absent
  * from `declared` until something forces them; and declare no key in a
  * trait body, which initialises once per mixin. A set left partly filled
  * is a `DeclaredNoteKeysSuite` failure, not a missing line in a player's
  * log.
  */
final class NoteSet private (private val label: String):
  private val own = scala.collection.mutable.ArrayBuffer.empty[NoteKey]
  private val borrowed = scala.collection.mutable.ArrayBuffer.empty[NoteKey]
  private val included = scala.collection.mutable.ArrayBuffer.empty[NoteSet]

  /** Declares a key and returns it. */
  def key(name: String, template: Vector[NotePart]): NoteKey =
    require(!own.exists(_.name == name), s"$label declares $name twice")
    val declaredKey = NoteKey(name, template)
    own += declaredKey
    declaredKey

  /** Declares one key another object owns, and returns it, for a power that
    * writes that one line and not the rest of the owner's set. Prefer this
    * to `include`: a power declares what it writes, so the check that
    * refuses an undeclared note stays as tight as the power is. */
  def borrow(key: NoteKey): NoteKey =
    if !borrowed.contains(key) then borrowed += key
    key

  /** Declares every key `other` owns, for a power that writes any of them. */
  def include(other: NoteSet): Unit =
    require(!other.reaches(this), s"$label and ${other.label} include each " +
      "other, so neither could say what it declares")
    if !included.contains(other) then included += other

  /** Every key this object owns or borrows, its own first. Two sets may
    * include a third, so one key arriving by two routes is one key; two
    * different keys of one name are a conflict, and picking a winner
    * silently is how a borrowed line goes missing. */
  def declared: Vector[NoteKey] =
    val all = own.toVector ++ borrowed.toVector ++
      included.toVector.flatMap(_.declared)
    all.groupBy(_.name).foreach: (name, keys) =>
      require(keys.distinct.sizeIs == 1, s"$label declares $name as " +
        keys.distinct.map(_.template).mkString(" and as "))
    all.distinct

  /** Whether `other` is this set, or anything this set includes. */
  private def reaches(other: NoteSet): Boolean =
    this.eq(other) || included.exists(_.reaches(other))

object NoteSet:
  def apply(power: PowerId): NoteSet = new NoteSet(power.value)
  /** A set for a helper that owns keys no single power owns, such as the
    * end-of-Campaign discard line. `label` names it in failures only. */
  def unowned(label: String): NoteSet = new NoteSet(label)

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
