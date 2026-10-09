package oathdigital.gameplay.operations

import oathdigital.model._

/** `inner`, lifted where consent was given (consent design, slice 3). A
  * refusal that names a waiver is dropped once a grant for what it covers is
  * remembered this turn. This is the operation path's one ledger check:
  * `OperationRestrictions.active` wraps every printed restriction in it, so
  * the walker's steps, its screens and card play's judgements agree.
  */
final case class ConsentedRestriction(inner: OperationRestriction)
    extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    inner.reason(ready, operation).filterNot(_.waiver.exists(waiver =>
      ready.consent.remembered(waiver.covers, ConsentTurn.of(ready))
        .contains(ConsentAnswer.Granted)))
