package oathdigital.model

/** One shape violation against an operation, mirroring the code/detail of the
  * [[OperationError]] the mutation pipeline would reject with.
  */
sealed trait OperationReasonKind
object OperationReasonKind {
  case object Impossible extends OperationReasonKind
  case object Invalid extends OperationReasonKind
}

final case class OperationReason(code: String, detail: String,
    kind: OperationReasonKind = OperationReasonKind.Invalid)

/** Extension seam for per-query contextual restrictions beyond the static
  * allowlist. Restrictions report typed rule impossibility or invalidity.
  */
trait OperationRestriction {
  def reason(
      ready: ReadyGame,
      operation: CoreOperation
  ): Option[OperationReason]
}
