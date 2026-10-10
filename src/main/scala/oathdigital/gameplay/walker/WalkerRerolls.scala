package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DiceKind, OathViolation, ReadyGame,
  Reroll}

/** Fills a declared `Reroll`'s faces from the walker's dice source. Split out
  * of [[ProcedureWalker]], which is close to the project's per-file bound,
  * as [[WalkerShuffles]] is. The filled operation is what the walker runs and
  * records, so replay applies the same faces without rolling again. The die
  * kind is read from the faces being replaced. A reroll of no dice, or of a
  * pool with no recorded roll, is handed back unfilled, and applying it
  * fails.
  */
private[walker] object WalkerRerolls:
  def filled(reroll: Reroll, state: ReadyGame, dice: WalkerDice)
      : Either[OathViolation, Reroll] =
    state.game.current.rollOutcomes.get(reroll.pool)
      .flatMap(outcome => reroll.indices.headOption
        .flatMap(outcome.faces.lift)) match
      case None => Right(reroll)
      case Some(face) =>
        val kind = face match
          case _: AttackDieFace => DiceKind.Attack
          case _ => DiceKind.Defense
        dice.roll(kind, reroll.indices.size).flatMap(faces => Either.cond(
          faces.size == reroll.indices.size,
          reroll.copy(faces = Some(faces)),
          OathViolation.InvalidEventOrder(s"dice source gave ${faces.size} " +
            s"faces for a reroll of ${reroll.indices.size}")))
