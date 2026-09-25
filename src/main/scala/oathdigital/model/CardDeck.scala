package oathdigital.model

/** The four card decks a card can be drawn from or buried into.
  *
  * Declared in the model rather than beside the operations that move cards,
  * because `DecisionOptionRef.Deck` names a deck and answered decisions are
  * persisted (see `Decisions.scala`). The move changed the package and
  * nothing else: same four case objects, same names, same JSON and wire
  * strings.
  *
  * `key` is the stable wire spelling, frozen: it is the `Location.Deck`
  * journal tag and the identity half of a `DecisionOptionRef.Deck` on the
  * command wire.
  */
enum CardDeck(val key: String) {
  case World extends CardDeck("world")
  case Relic extends CardDeck("relic")
  case Edifice extends CardDeck("edifice")
  case Legacy extends CardDeck("legacy")
}
object CardDeck {
  val all: Vector[CardDeck] = Vector(World, Relic, Edifice, Legacy)

  /** Safe parse for untrusted (wire) input, mirroring `Suit.fromKey`. */
  def fromKey(value: String): Option[CardDeck] = all.find(_.key == value)
}
