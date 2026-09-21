package oathdigital.gameplay.walker

import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace,
  OathViolation, PoolKey, ReadyGame, Roll, RollOutcome}

/** Roll outcome mechanics, split out of [[ProcedureWalker]] (which is close to
  * the project's per-file bound) and shared by the live walk and by
  * [[WalkerReplay]], so a rolled outcome and a replayed one are derived and
  * accumulated by the same code rather than by two implementations agreeing.
  */
private[walker] object WalkerRolls {

  def poolCount(state: ReadyGame, pool: PoolKey): Int =
    state.game.current.rollPools.get(pool).fold(0)(_.count)

  /** Skulls and score of `faces`, all of one family: attack faces score
    * swords and count skulls, defense faces score shields (a Doubler applies
    * across every accumulated roll).
    */
  private def derive(faces: Vector[DieFace]): (Int, Int) = {
    val attack = faces.collect { case face: AttackDieFace => face }
    if (attack.nonEmpty)
      (AttackDieFace.skulls(attack), AttackDieFace.score(attack))
    else (0, DefenseDieFace.score(faces.collect {
      case face: DefenseDieFace => face
    }))
  }

  private def invalid(detail: String) =
    OathViolation.InvalidEventOrder(detail)

  /** The outcome of rolling `faces` at `roll`: the face count must equal the
    * pool's count and every face must belong to the roll's die kind.
    */
  def outcomeFor(roll: Roll, state: ReadyGame, faces: Vector[DieFace])
      : Either[OathViolation, RollOutcome] = {
    val pool = roll.pool
    val count = poolCount(state, pool)
    for {
      _ <- Either.cond(faces.size == count, (), invalid(
        s"rolled ${faces.size} dice for pool $pool but pool count is $count"))
      _ <- roll.dice.die match {
        case DiceKind.Defense => Either.cond(
          faces.forall(_.isInstanceOf[DefenseDieFace]), (), invalid(
            s"defense roll for pool $pool received a non-defense die face"))
        case DiceKind.Attack => Either.cond(
          faces.forall(_.isInstanceOf[AttackDieFace]), (), invalid(
            s"attack roll for pool $pool received a non-attack die face"))
      }
    } yield {
      val (skulls, score) = derive(faces)
      RollOutcome(pool, count, faces, skulls, score)
    }
  }

  /** The outcome a recorded roll claims. Replay has no tree, so it cannot know
    * the die kind: it checks the pool and its count, and that the faces are
    * one family.
    */
  def outcomeForRecorded(state: ReadyGame, pool: PoolKey,
      faces: Vector[DieFace]): Either[OathViolation, RollOutcome] = for {
    count <- state.game.current.rollPools.get(pool).map(_.count).toRight(
      invalid(s"recorded roll references missing pool ${pool.value}"))
    _ <- Either.cond(faces.size == count, (), invalid(
      s"recorded roll has ${faces.size} faces but pool count is $count"))
    _ <- Either.cond(faces.forall(_.isInstanceOf[DefenseDieFace]) ||
      faces.forall(_.isInstanceOf[AttackDieFace]), (), invalid(
      "recorded roll mixes attack and defense faces"))
  } yield {
    val (skulls, score) = derive(faces)
    RollOutcome(pool, count, faces, skulls, score)
  }

  /** Merges `outcome` into the pool's accumulated entry: repeated rolls of one
    * pool accumulate faces, count and skulls, and the score is re-derived from
    * every accumulated face because each Doubler multiplies shields from every
    * roll, not only its own.
    */
  def write(ready: ReadyGame, outcome: RollOutcome): ReadyGame = {
    val current = ready.game.current
    val accumulated = current.rollOutcomes.get(outcome.pool).fold(outcome) {
      previous =>
        val faces = previous.faces ++ outcome.faces
        RollOutcome(outcome.pool, previous.count + outcome.count, faces,
          previous.skulls + outcome.skulls, derive(faces)._2)
    }
    ready.copy(game = ready.game.copy(current = current.copy(rollOutcomes =
      current.rollOutcomes.updated(outcome.pool, accumulated))))
  }
}
