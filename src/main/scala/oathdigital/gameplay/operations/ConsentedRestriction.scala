package oathdigital.gameplay.operations

import oathdigital.model._

/** `inner`, lifted where consent was given (consent design, slice 3). A
  * refusal that names a waiver is dropped once a grant for what it covers is
  * remembered this turn, or when its power and requester are in `together`:
  * a grant for another operation of the same batch lifts it too
  * ([[OperationConsent.together]]). This is the operation path's one ledger
  * check: `OperationRestrictions.active` wraps every printed restriction in
  * it, so the walker's steps, its screens and card play's judgements agree.
  */
final case class ConsentedRestriction(inner: OperationRestriction,
    together: Set[(PowerId, PlayerId)] = Set.empty)
    extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    inner.reason(ready, operation).filterNot(_.waiver.exists(waiver =>
      together.contains(waiver.power -> waiver.covers.requester) ||
        ready.consent.remembered(waiver.covers, ConsentTurn.of(ready))
          .contains(ConsentAnswer.Granted)))
