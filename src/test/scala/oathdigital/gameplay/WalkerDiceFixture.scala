package oathdigital.gameplay

import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** Dice for the walks that roll by themselves. A tree with an automatic
  * `Roll` asks its dice source mid-command, so a test that drives such a
  * tree has to say what the dice show before the walk starts rather than
  * handing faces to a parked roll afterwards.
  */
object WalkerDiceFixture {
  /** Nothing scored, whatever the pool holds: a Recover roll that fails and
    * leaves the player the continue-or-stop choice.
    */
  val blanks: WalkerDice = (kind, count) => Right(Vector.fill(count)(
    kind match {
      case DiceKind.Attack => AttackDieFace.HollowSword: DieFace
      case DiceKind.Defense => DefenseDieFace.Blank: DieFace
    }))

  /** Two shields on every defense die, which carries one Recover roll past
    * any difficulty the fixtures use.
    */
  val shields: WalkerDice = (kind, count) => Right(Vector.fill(count)(
    kind match {
      case DiceKind.Attack => AttackDieFace.TwoSwordsSkull: DieFace
      case DiceKind.Defense => DefenseDieFace.TwoShields: DieFace
    }))

  /** Faces handed out one roll at a time, so a test can script a failed roll
    * and then a successful one. Running out is a violation rather than a
    * repeat of the last roll, so a walk that rolls more often than the test
    * meant fails instead of passing on borrowed faces.
    */
  def scripted(rolls: Vector[DieFace]*): WalkerDice = {
    val queue = scala.collection.mutable.Queue(rolls: _*)
    (_, _) =>
      if (queue.isEmpty) Left(OathViolation.InvalidEventOrder(
        "the scripted dice source ran out of rolls"))
      else Right(queue.dequeue())
  }
}
