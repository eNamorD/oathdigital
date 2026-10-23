package oathdigital.gameplay.powerresolver

import oathdigital.catalog.PrintedPower
import oathdigital.model.{CardId, OperationReason, PlayerId, PowerId,
  LogNote, ReadyGame}

/** One use of a power, as the power-use restrictions judge it (catalog
  * batch 8, slice 10): who uses it, the card it is printed on, whether it
  * is printed persistent, and whether using it costs a secret. `consent` is
  * whether a refusal may be asked of a ruler: never inside a Campaign
  * (consent design, ruling 2). `places` is whether its cost places favor
  * or secrets on its card, which the placement bans of part 2 read; the
  * payment settles to the bank off its user's turn, so a ban reads it only
  * on that turn.
  */
final case class PowerUse(user: PlayerId, power: PowerId,
    card: Option[CardId], persistent: Boolean, costsSecret: Boolean,
    consent: Boolean = true, places: Boolean = false)

object PowerUse:
  /** A use of `printed` from `card`. It costs a secret when its printed
    * cost has a secret or a burnt secret, or when `secret` says it pays one
    * another way: an X secret cost, or a tax on battle plans. It places on
    * the card when its printed cost has favor or secrets, or when `places`
    * says its payment does. */
  def of(user: PlayerId, printed: PrintedPower, card: CardId,
      secret: Boolean = false, consent: Boolean = true,
      places: Boolean = false): PowerUse =
    PowerUse(user, printed.id, Some(card), printed.persistent,
      secret || printed.cost.secret > 0 || printed.cost.secretBurnt > 0,
      consent,
      places || printed.cost.favor > 0 || printed.cost.secret > 0)

  /** An automatic rule's use (catalog batch 8, slice 10, part 2): printed
    * non-persistent, costing nothing. `PowerKindsCatalogSuite` checks each
    * registered power's declared resolution against its catalog flag. */
  def automatic(user: PlayerId, power: PowerId, card: CardId): PowerUse =
    PowerUse(user, power, Some(card), persistent = false,
      costsSecret = false)

/** Why a restriction refuses a use. `power` is the restriction's and
  * `note` the reason shown when nobody may be asked. `reason` is the
  * refusal on the consent path: its waiver, when it has one, names who may
  * lift it and the key a grant is remembered under. */
final case class PowerUseRefusal(power: PowerId, note: LogNote,
    reason: OperationReason)

/** A rule that forbids some uses of powers (Spell Breaker, Forest Council,
  * City Wall, Giant Python). A walker power declares it as
  * `powerUseRestrictions`; the engine reads it without naming it. `waiver`
  * is how its ruler may waive it, for the consent grid, the preferences and
  * the consent log (`ConsentPowers`); `None` when nothing waives it. */
trait PowerUseRestriction:
  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal]
  def waiver: Option[Waiver] = None
