package oathdigital.gameplay.operations

import oathdigital.model._

/** The `Shuffle` write: a pile Search draws from takes the order the walker
  * generated. The order must hold exactly the pile's cards, so a recorded
  * shuffle can reorder a pile but never add, drop or replace a card.
  */
private[operations] object PileOperations:
  import OperationError.InvalidDescription

  def shuffle(ready: ReadyGame, pile: SearchSource,
      order: Option[Vector[WorldCardId]]): Either[OperationError, ReadyGame] =
    val held = ready.game.current.commonCards.pile(pile)
    val name = SearchSource.name(pile)
    order match
      case None => Left(InvalidDescription(
        s"a shuffle of the $name has no order"))
      case Some(cards) if cards.size != held.size || cards.diff(held).nonEmpty =>
        Left(InvalidDescription(
          s"a shuffle of the $name must hold exactly its cards"))
      case Some(cards) => Right(ready.updateCurrent(current => current.copy(
        commonCards = current.commonCards.withPile(pile, cards))))
