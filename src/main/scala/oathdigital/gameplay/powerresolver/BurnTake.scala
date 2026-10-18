package oathdigital.gameplay.powerresolver

import oathdigital.model.{Piece, PlayerId, PositionedLocation, LogNote}

/** Favor or secrets a player burns: how many, where from, and who burns
  * them (catalog batch 8, "A burn's actor"). A cost's burnt portion is its
  * payer's, from their board. */
final case class Burnt(resource: Piece.Counted, from: PositionedLocation,
    by: PlayerId)

/** A power's claim on a burn: `taker` takes the tokens instead of the bank.
  * `line` words what was taken, given the burner, the count taken and the
  * active player. */
final case class BurnTake(taker: PlayerId,
    line: (PlayerId, Int, PlayerId) => LogNote)
