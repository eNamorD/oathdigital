package oathdigital.gameplay.actions

import oathdigital.model.{NoteKey, PowerId}

/** The game rules that write Game Log lines (power log lines design, "Game
  * rules"): each rule's id and the keys it writes. A rule is not a power, so
  * the formatter reads this beside the two power catalogs.
  */
object RuleNotes:
  /** Card play's Homeland rule: a play of a card of the Homeland's suit to
    * that site may discard a card there first, at any capacity. */
  val homelandDiscard: PowerId = PowerId("rule.homeland-discard")

  val all: Vector[(PowerId, Vector[NoteKey])] =
    Vector(homelandDiscard -> Vector(PlacementRules.discardFirst))
