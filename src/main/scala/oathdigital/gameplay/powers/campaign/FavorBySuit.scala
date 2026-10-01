package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The favor a plan gains one of per card, from each card's suit bank (Tribute
  * Spoils, Military Parade). Only a faceup card has a suit, except an edifice,
  * which has its suit on either face, so the callers pass only such cards. The
  * gains are best effort: a bank with less favor gives what it holds.
  */
object FavorBySuit:
  /** How many of `cards` belong to each suit, in suit order, leaving out the
    * suits with none. A card whose suit the catalog does not know counts
    * nothing. */
  def counts(catalog: ExecutableCatalog, cards: Vector[CardId])
      : Vector[(Suit, Int)] =
    val suits = cards.flatMap(card => catalog.suitOf(card))
    Suit.all.map(suit => suit -> suits.count(_ == suit)).filter(_._2 > 0)

  /** One gain per suit for `user`. A bandit defender has no board, so its favor
    * moves from the bank to the shared bank, as Battle Honors' does. */
  def gains(user: Option[PlayerId], counts: Vector[(Suit, Int)])
      : Vector[Operation] = counts.map { case (suit, count) =>
    user.fold[Operation](Move(Piece.Favor(count),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.SharedBank)))(Gain.Favor(_, suit, count))
  }
