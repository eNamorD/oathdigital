package oathdigital.gameplay.operations

import oathdigital.model._

/** The `Reroll` and `SetDieFaces` writes: rerolled faces replace recorded
  * ones in place, and set faces join them. */
private[gameplay] object RollOperations:
  import OperationError.InvalidDescription

  /** The skulls of the replaced faces come off what the outcome records and
    * the skulls of the new faces go on, so a power's earlier change to the
    * skulls stands, never below none. The score is worked out again from
    * every face, since a Doubler multiplies across the whole pool, as
    * `WalkerRolls.write` does.
    */
  def reroll(ready: ReadyGame, pool: PoolKey, indices: Vector[Int],
      faces: Option[Vector[DieFace]]): Either[OperationError, ReadyGame] =
    def invalid(detail: String) =
      Left(InvalidDescription(s"a reroll of ${pool.value} $detail"))
    ready.game.current.rollOutcomes.get(pool) match
      case None => invalid("has no recorded roll")
      case Some(outcome) => faces match
        case None => invalid("has no faces")
        case Some(rolled) if rolled.size != indices.size =>
          invalid("has a face count unlike its dice")
        case Some(_) if indices.distinct.size != indices.size ||
            indices.exists(index => index < 0 ||
              index >= outcome.faces.size) =>
          invalid("names a die the roll does not have")
        case Some(rolled) if !sameFamily(indices.map(outcome.faces) ++
            rolled) => invalid("mixes attack and defense faces")
        case Some(rolled) =>
          val replaced = indices.zip(rolled).foldLeft(outcome.faces) {
            case (all, (index, face)) => all.updated(index, face) }
          val skulls = math.max(0, outcome.skulls -
            RollOutcome.derive(indices.map(outcome.faces))._1 +
            RollOutcome.derive(rolled)._1)
          Right(ready.updateCurrent(current => current.copy(
            rollOutcomes = current.rollOutcomes.updated(pool,
              outcome.copy(faces = replaced, skulls = skulls,
                score = RollOutcome.derive(replaced)._2,
                rerolled = (outcome.rerolled ++ indices).distinct.sorted)))))

  /** Set faces join the pool's outcome; a pool that has not rolled starts
    * one with no throw counted, so the roll after it is the first throw.
    * The skulls of the set faces are added to what the outcome records, and
    * the score is worked out again from every face, as `reroll` does. The
    * pool holds as many dice fewer.
    */
  def setFaces(ready: ReadyGame, pool: PoolKey, faces: Vector[DieFace])
      : Either[OperationError, ReadyGame] =
    val current = ready.game.current
    val held = current.rollPools.get(pool).fold(0)(_.count)
    val before = current.rollOutcomes.get(pool)
    val all = before.fold(Vector.empty[DieFace])(_.faces) ++ faces
    val (skulls, _) = RollOutcome.derive(faces)
    if faces.isEmpty || faces.size > held then Left(InvalidDescription(
      s"setting ${faces.size} dice of ${pool.value}, which holds $held"))
    else if !sameFamily(all) then Left(InvalidDescription(
      s"setting dice of ${pool.value} mixes attack and defense faces"))
    else
      val score = RollOutcome.derive(all)._2
      val outcome = before.fold(RollOutcome(pool, faces.size, faces, skulls,
        score, throws = 0))(previous => previous.copy(
          count = previous.count + faces.size, faces = all,
          skulls = previous.skulls + skulls, score = score))
      Right(ready.updateCurrent(state => state.copy(
        rollPools = state.rollPools.updated(pool,
          DicePoolState(held - faces.size)),
        rollOutcomes = state.rollOutcomes.updated(pool, outcome))))

  private def sameFamily(faces: Vector[DieFace]): Boolean =
    faces.forall(_.isInstanceOf[AttackDieFace]) ||
      faces.forall(_.isInstanceOf[DefenseDieFace])
