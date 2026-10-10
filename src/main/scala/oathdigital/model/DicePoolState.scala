package oathdigital.model

/** Number of dice currently assigned to a named pool (e.g. `"recover"`,
  * `"campaign.attack"`).
  *
  * Pool counts live in game state. A `ModifyDicePool` operation adjusts the
  * count; a `Roll` leaf reads it when the application layer pre-rolls the
  * faces at the command boundary (the engine never rolls). Kept in its own
  * file as the state side of the pool vocabulary, matching
  * `CurrentGameState.rollPools` (the pool *keys* live with the model pending
  * pointer in `PendingTree.scala`, referenced by the dice-pool leaves in
  * `gameplay/operations/CoreOperations.scala`).
  */
final case class DicePoolState(count: Int)

/** Accumulated result for one named pool during an action: every roll appends
  * its faces/count and adds its derived `skulls`. This slice only writes
  * defense rolls, so `skulls` is 0; their score is re-derived from all
  * accumulated faces because a Doubler applies across rolls. Powers may edit
  * the outcome later via
  * `ModifyRollOutcome`). Lives in state next to the pool counts so later
  * resolution consumes it without re-rolling (engine never calls random
  * ports; replay uses the recorded faces).
  *
  * `throws` counts the pool's rolls, this outcome's included; a throw's
  * number is this count. `rerolled` holds the indices of the faces
  * rerolled since the pool last rolled, in index order. A roll starts a new
  * throw and leaves it empty; a `Reroll` adds its dice. A throw is the faces
  * its roll added plus these (catalog batch 7, slice 2: Silver Charm
  * rerolls earlier dice with a roll).
  */
final case class RollOutcome(
    pool: PoolKey,
    count: Int,
    faces: Vector[DieFace],
    skulls: Int,
    score: Int,
    throws: Int = 1,
    rerolled: Vector[Int] = Vector.empty
)

object RollOutcome:
  /** Skulls and score of `faces`, all of one family: attack faces score
    * swords and count skulls, defense faces score shields, a Doubler
    * applying across every face. */
  def derive(faces: Vector[DieFace]): (Int, Int) =
    val attack = faces.collect { case face: AttackDieFace => face }
    if attack.nonEmpty then
      (AttackDieFace.skulls(attack), AttackDieFace.score(attack))
    else (0, DefenseDieFace.score(faces.collect {
      case face: DefenseDieFace => face
    }))
