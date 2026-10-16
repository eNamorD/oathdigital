package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.{PowerUse, PowerUseRefusal,
  PowerUseRestriction}
import oathdigital.model._

/** The power-use filter (catalog batch 8, slice 10): the refusals a use
  * meets, with consent applied, and how an option for a refused use is
  * shown. Every path that uses a power reads it: the phase menu and the
  * start check, the modifier picker, the battle-plan and reroll offers, and
  * the automatic powers (part 2). */
object PowerUseRules:
  /** The decision slot of a power-use grant's key. A grant covers the
    * card's use by its requester for the turn, on whichever path uses it,
    * so the key names the use instead of a decision. */
  val useKey: String = "power.use"

  /** The target of a grant's key: the card, or the power's own button
    * when it has no card or its card has no option ref. */
  def target(use: PowerUse): DecisionOptionRef = use.card match
    case Some(id: DenizenId) => DecisionOptionRef.Denizen(id)
    case Some(id: RelicId) => DecisionOptionRef.Relic(id)
    case Some(id: EdificeId) => DecisionOptionRef.Edifice(id)
    case _ => DecisionOptionRef.Button(use.power.value)

  /** Every refusal `restrictions` give `use`, less those a grant
    * remembered this turn lifts. */
  def refusals(ready: ReadyGame, use: PowerUse,
      restrictions: Vector[PowerUseRestriction]): Vector[PowerUseRefusal] =
    restrictions.flatMap(_.refusal(ready, use)).filterNot(refusal =>
      refusal.reason.waiver.exists(waiver => ready.consent.remembered(
        waiver.covers, ConsentTurn.of(ready))
        .contains(ConsentAnswer.Granted)))

  /** How an option for a use `refusals` stop is shown: askable of its
    * granters when `consent` allows it and consent may lift every refusal
    * (ruling 8), else unavailable with the first refusal's reason. */
  def shown(ready: ReadyGame, actor: PlayerId,
      refusals: Vector[PowerUseRefusal], consent: Boolean): OptionReason =
    require(refusals.nonEmpty, "an option with no refusal is not refused")
    val first = refusals.head
    Option.when(consent)(OperationConsent.ask(ready, actor,
      refusals.map(_.reason))).flatten
      .getOrElse(OptionReason.Noted(first.power, first.note))
