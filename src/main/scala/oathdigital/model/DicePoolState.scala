package oathdigital.model

/** Number of dice currently assigned to a named pool (e.g. `"recover"`,
  * `"campaign.attack"`).
  *
  * Pool counts live in game state. A `ModifyDicePool` operation adjusts the
  * count; a `Roll` leaf reads it when the application layer pre-rolls the
  * faces at the command boundary (the engine never rolls). Kept in its own
  * file as the state side of the pool vocabulary, matching
  * `CurrentGameState.rollPools` (the pool *keys* live with the operations in
  * `CoreOperations.scala`).
  */
final case class DicePoolState(count: Int)
