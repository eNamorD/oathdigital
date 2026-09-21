package oathdigital.application

import oathdigital.model._

/** The consequences of answering a decision option, worded for display, read
  * off the operations the walk recorded for it. It words only what a player
  * weighs when choosing (Supply spent and what is gained), in the order the
  * operations ran, and only what actually ran: a gain that shrank to nothing
  * was skipped and so is not mentioned.
  *
  * The walker records the leaf a `Gain` expands into, a `Move` from a bank to
  * the player's play area, so that is what is read; a `Gain` itself is worded
  * the same way for a caller that holds the requested operation.
  */
private[application] object OperationDetails {
  def of(operations: Vector[CoreOperation]): Vector[String] = operations.collect {
    case SpendSupply(_, amount, _) => s"$amount Supply"
    case GainSupply(_, amount) => s"+$amount Supply"
    case Gain.Warbands(_, _, amount) => s"+$amount warbands"
    case Gain.Favor(_, _, amount) => s"+$amount favor"
    case Gain.Secrets(_, amount) => s"+$amount secrets"
    case Move(piece, from, to, _) if fromBank(from.location) &&
        toPlayArea(to.location) => piece match {
      case Piece.Warbands(_, amount) => s"+$amount warbands"
      case Piece.Favor(amount) => s"+$amount favor"
      case Piece.Secrets(amount) => s"+$amount secrets"
      case _ => ""
    }
  }.filter(_.nonEmpty)

  private def fromBank(location: Location): Boolean = location match {
    case _: Location.FavorBank | Location.SharedBank |
        _: Location.WarbandBank => true
    case _ => false
  }

  private def toPlayArea(location: Location): Boolean = location match {
    case _: Location.PlayArea => true
    case _ => false
  }
}
