package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace,
  OathViolation}

/** Where the walker's randomness comes from: an automatic `Roll`'s faces,
  * asked once per roll with the die kind and the pool's count, and a
  * `Shuffle`'s order. The engine never rolls or shuffles; production
  * supplies the application service's port. Replay never asks: it applies
  * the faces recorded in the `RollPayload` and the order recorded in the
  * `Shuffle`.
  */
trait WalkerDice:
  def roll(kind: DiceKind, count: Int): Either[OathViolation, Vector[DieFace]]

  /** A new order for a pile of `count` cards: a permutation of
    * `0 until count`, where the new pile's card `i` is the old pile's card
    * `order(i)`. A source that cannot shuffle fails loudly, as one that
    * cannot roll does. The walker asks only for a pile of two or more.
    */
  def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
    Left(OathViolation.InvalidEventOrder(
      "walker has no shuffle source for a Shuffle"))

object WalkerDice:
  /** The default: fails loudly, so a walk that reaches an automatic roll
    * without a source is a typed violation and never a silent roll.
    */
  val unavailable: WalkerDice = (_, _) => Left(OathViolation.InvalidEventOrder(
    "walker has no dice source for an automatic roll"))

  /** Fixed faces and an unchanged pile order, for simulations: a simulation
    * reports what a tree would do, and no rule reads a placeholder face or
    * order because a start only walks to its first decision.
    */
  val placeholder: WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] =
      Right(Vector.fill(count)(kind match {
        case DiceKind.Attack => AttackDieFace.HollowSword: DieFace
        case DiceKind.Defense => DefenseDieFace.Blank: DieFace
      }))
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right((0 until count).toVector)
