package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace,
  OathViolation}

/** Where an automatic `Roll` gets its faces: asked once per roll, with the die
  * kind and the pool's count. The engine never rolls; production supplies the
  * application service's dice port. Replay never asks: it applies the faces
  * recorded in the `RollPayload`.
  */
trait WalkerDice {
  def roll(kind: DiceKind, count: Int): Either[OathViolation, Vector[DieFace]]
}

object WalkerDice {
  /** The default: fails loudly, so a walk that reaches an automatic roll
    * without a source is a typed violation and never a silent roll.
    */
  val unavailable: WalkerDice = (_, _) => Left(OathViolation.InvalidEventOrder(
    "walker has no dice source for an automatic roll"))

  /** Fixed faces, for simulations: a simulation reports what a tree would do,
    * and no rule reads a placeholder face because a start only walks to its
    * first decision.
    */
  val placeholder: WalkerDice = (kind, count) => Right(Vector.fill(count)(
    kind match {
      case DiceKind.Attack => AttackDieFace.HollowSword: DieFace
      case DiceKind.Defense => DefenseDieFace.Blank: DieFace
    }))
}
