package oathdigital.protocol.projection

/** Whom an askable option would ask, and about which powers, for the
  * client to word its confirmation (consent design, "Projection"). Player
  * ids: the client words their display names. */
final case class ConsentAskProjection(granterPlayerIds: Vector[String],
    powers: Vector[String])

/** The requester's own view of their waiting request: who has not yet
  * answered. */
final case class ConsentWaitingProjection(granterPlayerIds: Vector[String])

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
