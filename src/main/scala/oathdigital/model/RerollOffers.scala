package oathdigital.model

/** One reroll a power offers on a throw: the card that offers it, what using
  * it pays, and what it does (catalog batch 7, slice 1).
  *
  * `label` is the button's text after its cost, naming the card and what it
  * rerolls: "Reroll all with Jinx". It may hold glyph tokens.
  *
  * `payment` is the power's cost, built by the power, which knows its card's
  * suit; it is run before `steps`. `steps` reroll dice, with any decision
  * and note the power needs; a `Reroll` among them must be a leaf, never
  * inside a `BuildOps`, so the walker fills its faces. `withheld` is why the
  * reroll stands but cannot be used now: it is shown unavailable with that
  * reason.
  *
  * The offer is rebuilt at every fold, so its steps must not depend on what
  * using it changes.
  */
final case class RerollOffer(card: DenizenId, label: String,
    payment: Option[PayCost],
    steps: Vector[Operation], withheld: Option[OptionReason] = None)

/** A reroll offer with the power that made it. */
final case class OfferedReroll(power: PowerId, offer: RerollOffer)
