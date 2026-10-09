package oathdigital.gameplay.actions

import oathdigital.gameplay.operations.HallOfMinisters
import oathdigital.model.{NoteKey, PowerId}

/** The game rules that write Game Log lines (power log lines design, "Game
  * rules"): each rule's id and the keys it writes. A rule is not a power, so
  * the formatter reads this beside the two power catalogs.
  */
object RuleNotes:
  /** Card play's Homeland rule: a play of a card of the Homeland's suit to
    * that site may discard a card there first, at any capacity. */
  val homelandDiscard: PowerId = PowerId("rule.homeland-discard")

  /** The game rules' keys, and the Hall of Ministers' consent note: an
    * operation restriction is not a power either (consent slice 3). */
  val all: Vector[(PowerId, Vector[NoteKey])] =
    Vector(homelandDiscard -> Vector(CardPlayRules.discardFirst),
      HallOfMinisters.id -> Vector(HallOfMinisters.noDiscardConsent))
