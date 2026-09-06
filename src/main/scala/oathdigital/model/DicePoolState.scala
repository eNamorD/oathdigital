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

/** Recorded result of one rolled pool: the faces that rode the roll command
  * plus the derived `skulls`/`score` (this slice only writes defense rolls,
  * so `skulls` is 0; powers may edit the outcome later via
  * `ModifyRollOutcome`). Lives in state next to the pool counts so later
  * resolution consumes it without re-rolling (engine never calls random
  * ports; replay uses the recorded faces).
  */
final case class RollOutcome(
    pool: PoolKey,
    count: Int,
    faces: Vector[DieFace],
    skulls: Int,
    score: Int
)
