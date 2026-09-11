package oathdigital.model

/** Open decision payload carried by a walker `Decide` leaf and stored in
  * `PendingTree.answered`.
  *
  * The payload must be MODEL-safe: answered decisions are persisted on
  * `CurrentGameState.walkerPending` between commands (the legacy
  * `PendingProcedure` precedent stores model payloads the same way), so the
  * family and every concrete case live in the model, never importing
  * gameplay. Concrete payloads are declared next to the action they belong to
  * (or in this file when they are plain data); the engine stays generic over
  * payloads.
  *
  * Deliberately NOT sealed here: a power or action declares its own payload
  * case wherever it lives (same-file-sealed restriction on the family root is
  * why the root stays open).
  */
trait DecisionPayload extends Product with Serializable

object DecisionPayload {
  /** Recover per-roll choice, resolved at the `"recover.choice"` decision:
    * continue rolling (another 1-supply payment) or stop and abandon without
    * a relic
    * (Stop is only legal while the recovery has not yet succeeded).
    */
  sealed trait RecoverChoice extends Product with Serializable
  object RecoverChoice {
    case object Continue extends RecoverChoice
    case object Stop extends RecoverChoice
  }

  /** Answer to the per-roll choice decision. */
  final case class RecoverChoicePayload(choice: RecoverChoice)
      extends DecisionPayload

  /** Answer to the success-only `"recover.relic"` decision: which facedown
    * relic at the site the actor takes into their play area facedown.
    */
  final case class RecoverRelicPayload(relicId: RelicId)
      extends DecisionPayload
}
