package oathdigital.protocol.projection

/** Whom an askable option would ask, and about which powers, for the
  * client to word its confirmation (consent design, "Projection"). Player
  * ids: the client words their display names. `granterPlayerIds` and
  * `powers` are the required asks; `alternatives` the asks any one of
  * which will do (catalog batch 8, slice 15). */
final case class ConsentAskProjection(granterPlayerIds: Vector[String],
    powers: Vector[String],
    alternatives: Vector[ConsentAlternativeProjection] = Vector.empty)

/** One alternative ask: its granter, its powers, and whether the granter
  * is asked to pay rather than to waive. */
final case class ConsentAlternativeProjection(granterPlayerId: String,
    powers: Vector[String], paying: Boolean)

/** The requester's own view of their waiting request: who has not yet
  * answered. `either` when any one of them will do. */
final case class ConsentWaitingProjection(granterPlayerIds: Vector[String],
    either: Boolean = false)

/** An asked granter's prompt: who asks, about which of the granter's own
  * powers, for which option. */
final case class ConsentPromptProjection(requesterPlayerId: String,
    powers: Vector[String], target: String)

/** A waivable power the viewer rules, with their setting for each other
  * player: `never`, `case-by-case` or `always`. Only ever the viewer's own
  * (ruling 16). `cardId` names the card that prints it, when a card does, so
  * the card can open the grid (consent slice 3). */
final case class ConsentPreferencesProjection(powerId: String, power: String,
    rows: Vector[ConsentPreferenceRowProjection],
    cardId: Option[String] = None)
final case class ConsentPreferenceRowProjection(playerId: String,
    setting: String)
