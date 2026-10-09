package oathdigital.gameplay.operations

import oathdigital.model._

/** Consent on the operation path (consent design, slice 3): the refusals an
  * option's plan meets, and the ask they allow. */
object OperationConsent:
  /** Every refusal `restrictions` give `operations`, in plan order. */
  def refusals(ready: ReadyGame, operations: Vector[CoreOperation],
      restrictions: Vector[OperationRestriction]): Vector[OperationReason] =
    operations.flatMap(operation =>
      restrictions.flatMap(_.reason(ready, operation)))

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
