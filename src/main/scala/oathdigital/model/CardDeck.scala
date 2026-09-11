package oathdigital.model

/** The four card decks a card can be drawn from or buried into.
  *
  * Declared in the model rather than beside the operations that move cards,
  * because `DecisionOptionRef.Deck` names a deck and answered decisions are
  * persisted (see `Decisions.scala`). The move changed the package and
  * nothing else: same four case objects, same names, same JSON and wire
  * strings.
  */
sealed trait CardDeck extends Product with Serializable
object CardDeck {
  case object World extends CardDeck
  case object Relic extends CardDeck
  case object Edifice extends CardDeck
  case object Legacy extends CardDeck
}
