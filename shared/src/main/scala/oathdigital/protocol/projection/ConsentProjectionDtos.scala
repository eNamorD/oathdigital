package oathdigital.protocol.projection

/** Whom an askable option would ask, and about which powers, for the
  * client to word its confirmation (consent design, "Projection"). Player
  * ids: the client words their display names. `granterPlayerIds` and
  * `powers` are the required asks to ignore a power; `payers` the required
  * asks to pay; `alternatives` the asks any one of which will do (catalog
  * batch 8, slice 15). */
final case class ConsentAskProjection(granterPlayerIds: Vector[String],
    powers: Vector[String],
    alternatives: Vector[ConsentAlternativeProjection] = Vector.empty,
    payers: Vector[ConsentAlternativeProjection] = Vector.empty)

/** One ask on its own: its granter, its powers, and whether the granter
  * is asked to pay rather than to waive. */
final case class ConsentAlternativeProjection(granterPlayerId: String,
    powers: Vector[String], paying: Boolean)

/** The requester's own view of their waiting request: who has not yet
  * answered. `either` when any one of them will do. `use` is the card
  * whose automatic power the rulers decide on (catalog batch 8, item
  * 10b). */
final case class ConsentWaitingProjection(granterPlayerIds: Vector[String],
    either: Boolean = false, use: Option[String] = None)

/** An asked granter's prompt: who asks, about which of the granter's own
  * powers, for which option. `paying` when the granter is asked to pay
  * and travel there rather than to waive (catalog batch 8, slice 15).
  * `use` is the card whose automatic power the ruler decides on (catalog
  * batch 8, item 10b). */
final case class ConsentPromptProjection(requesterPlayerId: String,
    powers: Vector[String], target: String, paying: Boolean = false,
    use: Option[String] = None)

/** A waivable power the viewer rules, with their setting for each other
  * player: `never`, `case-by-case` or `always`. Only ever the viewer's own
  * (ruling 16). `cardId` names the card that prints it, when a card does, so
  * the card can open the grid (consent slice 3). */
final case class ConsentPreferencesProjection(powerId: String, power: String,
    rows: Vector[ConsentPreferenceRowProjection],
    cardId: Option[String] = None)
final case class ConsentPreferenceRowProjection(playerId: String,
    setting: String)
