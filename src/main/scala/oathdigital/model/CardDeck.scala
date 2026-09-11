package oathdigital.model

/** The four card decks a card can be drawn from or buried into.
  *
  * Declared in the model rather than beside the operations that move cards,
  * because `DecisionOptionRef.Deck` names a deck and answered decisions are
  * persisted (see `Decisions.scala`). The move changed the package and
  * nothing else: same four case objects, same names, same JSON and wire
  * strings.
  */
sealed trait CardDeck extends Product with Serializable {
  /** Stable wire spelling, frozen: it is the `Location.Deck` journal tag and
    * the identity half of a `DecisionOptionRef.Deck` on the command wire.
    */
  def key: String
}
object CardDeck {
  case object World extends CardDeck { val key = "world" }
  case object Relic extends CardDeck { val key = "relic" }
  case object Edifice extends CardDeck { val key = "edifice" }
  case object Legacy extends CardDeck { val key = "legacy" }

  val all: Vector[CardDeck] = Vector(World, Relic, Edifice, Legacy)

  /** Safe parse for untrusted (wire) input, mirroring `Suit.all.find`. */
  def fromKey(value: String): Option[CardDeck] = all.find(_.key == value)
}
