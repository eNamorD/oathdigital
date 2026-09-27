package oathdigital.gameplay.walker

import oathdigital.model.{OathViolation, ReadyGame, Shuffle}

/** Fills a declared `Shuffle`'s order from the walker's random source. Split
  * out of [[ProcedureWalker]], which is close to the project's per-file
  * bound, as [[WalkerRolls]] is. The filled operation is what the walker runs
  * and records, so replay applies the same order without asking again. A pile
  * of fewer than two cards keeps its order and asks nothing.
  */
private[walker] object WalkerShuffles:
  def ordered(shuffle: Shuffle, state: ReadyGame, dice: WalkerDice)
      : Either[OathViolation, Shuffle] =
    val cards = state.game.current.commonCards.pile(shuffle.pile)
    if cards.size < 2 then Right(shuffle.copy(order = Some(cards)))
    else dice.shuffle(cards.size).flatMap(order => Either.cond(
      order.sorted == cards.indices.toVector,
      shuffle.copy(order = Some(order.map(cards))),
      OathViolation.InvalidEventOrder(
        s"shuffle source gave $order for a pile of ${cards.size} cards")))
