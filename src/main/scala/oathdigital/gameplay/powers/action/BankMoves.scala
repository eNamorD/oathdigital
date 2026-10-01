package oathdigital.gameplay.powers.action

import oathdigital.model._

/** A move of favor from one favor bank to another, for Levelers and Memory
  * of Home: the move, and the line that reads it back. */
private[action] object BankMoves:
  def move(amount: Int, from: Suit, to: Suit): Move = Move(Piece.Favor(amount),
    PositionedLocation(Location.FavorBank(from)),
    PositionedLocation(Location.FavorBank(to)))

  /** "Moved {n favor} from {the Beast bank} to {the Arcane bank}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2), NotePart.Text(".")))

  /** The bank that lost favor in the step before the note, how much, and
    * the bank that gained it. Nothing moved writes nothing. */
  def movedNote(card: PowerSourceRef)(states: NoteStates): Option[PowerNote] =
    for
      (before, after) <- states.previous
      from <- Suit.all.find(suit => stock(after, suit) < stock(before, suit))
      to <- Suit.all.find(suit => stock(after, suit) > stock(before, suit))
    yield moved(card, NoteArg.Amount(stock(before, from) - stock(after, from),
      NoteUnit.Favor), NoteArg.Bank(from), NoteArg.Bank(to))

  private def stock(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)
