package oathdigital.model

/** Open marker for one rolled die face, in the model so faces can be stored on
  * model state ([[RollOutcome]]) without model importing the gameplay die
  * vocabulary (the `BackendArchitectureSuite` inner/outer guard forbids that).
  *
  * Deliberately NOT sealed: each concrete face family ([[DefenseDieFace]],
  * [[AttackDieFace]], any later family) extends it from its own file, so
  * walker `RollPayload`s and state outcomes store a plain `Vector[DieFace]`
  * while the families keep their own sealed typing and scoring.
  */
trait DieFace extends Product with Serializable
