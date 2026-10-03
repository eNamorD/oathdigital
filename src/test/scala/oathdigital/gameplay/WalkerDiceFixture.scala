package oathdigital.gameplay

import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** Dice for the walks that roll by themselves. A tree with an automatic
  * `Roll` asks its dice source mid-command, so a test that drives such a
  * tree has to say what the dice show before the walk starts rather than
  * handing faces to a parked roll afterwards.
  */
object WalkerDiceFixture:
  /** Stub of WalkerDice: nothing scored, whatever the pool holds (every
    * attack die a hollow sword, every defense die blank). A Recover roll
    * fails and leaves the player the continue-or-stop choice; a Campaign
    * attack is lost.
    */
  val blanks: WalkerDice = (kind, count) => Right(Vector.fill(count)(
    kind match {
      case DiceKind.Attack => AttackDieFace.HollowSword: DieFace
      case DiceKind.Defense => DefenseDieFace.Blank: DieFace
    }))

  /** Stub of WalkerDice: two shields on every defense die, which carries one
    * Recover roll past any difficulty the fixtures use.
    */
  val shields: WalkerDice = (kind, count) => Right(Vector.fill(count)(
    kind match {
      case DiceKind.Attack => AttackDieFace.TwoSwordsSkull: DieFace
      case DiceKind.Defense => DefenseDieFace.TwoShields: DieFace
    }))

  /** Stub of WalkerDice: every attack die a sword; the defense dice are
    * `faces`, in turn, starting over when the pool is larger than `faces`.
    */
  def swordsCyclingDefense(faces: DefenseDieFace*): WalkerDice =
    (kind, count) => Right(kind match
      case DiceKind.Attack =>
        Vector.fill(count)(AttackDieFace.OneSword: DieFace)
      case DiceKind.Defense =>
        Vector.tabulate(count)(index => faces(index % faces.size): DieFace))

  /** Stub of WalkerDice: every attack die a sword; the first defense die a
    * single shield and the rest two shields, so a pool of three holds one
    * single shield and two double shields.
    */
  val swordsSingleThenDoubleShields: WalkerDice = (kind, count) =>
    Right(kind match
      case DiceKind.Attack =>
        Vector.fill(count)(AttackDieFace.OneSword: DieFace)
      case DiceKind.Defense => Vector.tabulate(count)(index =>
        (if index == 0 then DefenseDieFace.OneShield
         else DefenseDieFace.TwoShields): DieFace))

  /** Stub of WalkerDice: the first attack die two swords and a skull, the
    * rest one sword; every defense die blank.
    */
  val skullThenSwords: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.tabulate(count)(index =>
      (if index == 0 then AttackDieFace.TwoSwordsSkull
       else AttackDieFace.OneSword): DieFace)
    case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace))

  /** Stub of WalkerDice: dice that return exactly these faces, and fail
    * loudly on a wrong count. */
  def dice(attack: Vector[AttackDieFace] = Vector.empty,
      defense: Vector[DefenseDieFace] = Vector.empty): WalkerDice =
    (kind, count) => kind match
      case DiceKind.Attack => Either.cond(attack.size == count, attack,
        OathViolation.InvalidEventOrder(
          s"test dice: ${attack.size} attack faces for a pool of $count"))
      case DiceKind.Defense => Either.cond(defense.size == count, defense,
        OathViolation.InvalidEventOrder(
          s"test dice: ${defense.size} defense faces for a pool of $count"))

  /** Stub of WalkerDice: faces handed out one roll at a time, so a test can
    * script a failed roll and then a successful one. Running out is a
    * violation rather than a repeat of the last roll, so a walk that rolls
    * more often than the test meant fails instead of passing on borrowed
    * faces.
    */
  def scripted(rolls: Vector[DieFace]*): WalkerDice =
    val queue = scala.collection.mutable.Queue(rolls*)
    (_, _) =>
      if queue.isEmpty then Left(OathViolation.InvalidEventOrder(
        "the scripted dice source ran out of rolls"))
      else Right(queue.dequeue())

  /** Stub of WalkerDice: shuffle answers `order(count)` for a pile of `count`
    * cards, and every roll fails, so a walk that rolls instead of shuffling
    * is refused.
    */
  def shufflingBy(order: Int => Vector[Int]): WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] =
      Left(OathViolation.InvalidEventOrder("this dice source never rolls"))
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right(order(count))
