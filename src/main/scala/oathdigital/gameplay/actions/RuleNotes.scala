package oathdigital.gameplay.actions

import oathdigital.model.{NoteKey, PowerId}

/** The game rules that write Game Log lines (power log lines design, "Game
  * rules"): each rule's id and the keys it writes. A rule is not a power, so
  * the formatter reads this beside the two power catalogs.
  */
object RuleNotes:
  /** Card play's Homeland rule: a full Homeland takes a card of its suit
    * after a discard. */
  val homelandDiscard: PowerId = PowerId("rule.homeland-discard")

  val all: Vector[(PowerId, Vector[NoteKey])] =
    Vector(homelandDiscard -> Vector(PlacementRules.discardFirst))
