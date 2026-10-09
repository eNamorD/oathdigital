package oathdigital.model

/** One shape violation against an operation, mirroring the code/detail of the
  * [[OperationError]] the mutation pipeline would reject with.
  */
enum OperationReasonKind { case Impossible, Invalid }

/** `waiver` is set when the refusal comes from a power on its ruler's
  * enemies that the ruler may waive (consent design, slice 3). */
final case class OperationReason(code: String, detail: String,
    kind: OperationReasonKind = OperationReasonKind.Invalid,
    waiver: Option[OperationWaiver] = None)

/** How an operation's refusal may be waived by consent (consent design,
  * slice 3). `power` names the restriction. `granter` is the player who may
  * consent, `None` when bandits, the Empire or nobody rules it. `covers` is
  * the key a grant or a refusal is remembered under. `note` is the hover
  * reason of an option its granter may be asked about. */
final case class OperationWaiver(power: PowerId, granter: Option[PlayerId],
    covers: ConsentKey, note: PowerNote)

/** Extension seam for per-query contextual restrictions beyond the static
  * allowlist. Restrictions report typed rule impossibility or invalidity.
  */
trait OperationRestriction:
  def reason(
      ready: ReadyGame,
      operation: CoreOperation
  ): Option[OperationReason]
