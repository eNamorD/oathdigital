package oathdigital.gameplay.operations

import oathdigital.model._

/** Consent on the operation path (consent design, slice 3): the refusals an
  * option's plan meets, and the ask they allow. */
object OperationConsent:
  /** Every refusal `restrictions` give `operations`, one batch, in plan
    * order. */
  def refusals(ready: ReadyGame, operations: Vector[CoreOperation],
      restrictions: Vector[OperationRestriction]): Vector[OperationReason] =
    val lifted = together(ready, operations, restrictions)
    operations.flatMap(operation =>
      lifted.flatMap(_.reason(ready, operation)))

  /** `restrictions` for one batch of `operations`, which happen at the same
    * time. Enforcement is all-or-nothing (the Hall of Ministers ruling): a
    * grant remembered for any operation of the batch lifts its power for
    * every operation of the batch by the same requester. */
  def together(ready: ReadyGame, operations: Vector[CoreOperation],
      restrictions: Vector[OperationRestriction])
      : Vector[OperationRestriction] =
    val now = ConsentTurn.of(ready)
    restrictions.map {
      case consented: ConsentedRestriction =>
        consented.copy(together = operations
          .flatMap(consented.inner.reason(ready, _)).flatMap(_.waiver)
          .filter(waiver => ready.consent.remembered(waiver.covers, now)
            .contains(ConsentAnswer.Granted))
          .map(waiver => waiver.power -> waiver.covers.requester).toSet)
      case other => other
    }

  /** The ask for an option whose plan only `refusals` stop. It exists when
    * every refusal is waivable by a player other than `actor` and nothing it
    * covers was refused this turn (ruling 8). Its reason is the first
    * refusal's note; it asks each granter about all of their powers, and it
    * covers what each refusal names. */
  def ask(ready: ReadyGame, actor: PlayerId,
      refusals: Vector[OperationReason]): Option[OptionReason.Askable] =
    val waivers = refusals.flatMap(_.waiver)
    val granters = waivers.flatMap(_.granter).filter(_ != actor)
    val refused = waivers.exists(waiver => ready.consent.remembered(
      waiver.covers, ConsentTurn.of(ready)).contains(ConsentAnswer.Refused))
    if refusals.isEmpty || waivers.size < refusals.size ||
        granters.size < waivers.size || refused then None
    else waivers.headOption.map { first =>
      val owed = waivers.map(_.power).zip(granters)
      OptionReason.Askable(first.power, first.note,
        granters.distinct.map(granter => ConsentAsk(granter,
          owed.collect { case (power, `granter`) => power }.distinct)),
        waivers.map(_.covers).distinct)
    }

  /** How an option whose plan only `refusals` stop is shown: askable when
    * [[ask]] finds an ask, else unavailable with the plain reason of the
    * first refusal nobody may be asked about, when every refusal is one a
    * grant would lift (catalog batch 8, slice 10). `None` when some refusal
    * has no waiver. */
  def shown(ready: ReadyGame, actor: PlayerId,
      refusals: Vector[OperationReason]): Option[OptionReason] =
    val waivers = refusals.flatMap(_.waiver)
    val now = ConsentTurn.of(ready)
    def unaskable(waiver: OperationWaiver): Boolean =
      !waiver.granter.exists(_ != actor) || ready.consent.remembered(
        waiver.covers, now).contains(ConsentAnswer.Refused)
    if refusals.isEmpty || waivers.size < refusals.size then None
    else ask(ready, actor, refusals).orElse(waivers.find(unaskable)
      .orElse(waivers.headOption).map(blocker =>
        OptionReason.Noted(blocker.power, blocker.plain)))
