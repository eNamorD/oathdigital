package oathdigital.protocol.projection

/** The two dice a roll is made on. */
enum Die(val key: String):
  case Attack extends Die("attack")
  case Defense extends Die("defense")

/** The seven faces, with the one spelling each side needs: `key` on the
  * wire, `word` on the page, and the `die` it is printed on.
  *
  * One table for all three. The log used to carry a face's wire name and its
  * display name as two parallel arrays, and the client kept a third table of
  * display names which had drifted from the server's: the log sentence said
  * "sword" where the die chip's label said "one sword". These words are the
  * server's, which are the internally consistent set -- "sword" is parallel
  * with "two swords and a skull", "shield" with "two shields".
  *
  * The decision wire (`WalkerDecisionProjector.faceName`) still spells its
  * faces itself -- not because it may not read this enum, but because that
  * wire was out of the typed log slice's scope. A per-die set-equality test
  * in `WalkerDecisionProjectorSuite` holds it to these keys meanwhile.
  */
enum DieFace(val key: String, val word: String, val die: Die):
  case HollowSword extends DieFace("hollow-sword", "hollow sword", Die.Attack)
  case OneSword extends DieFace("one-sword", "sword", Die.Attack)
  case TwoSwordsSkull
      extends DieFace("two-swords-skull", "two swords and a skull", Die.Attack)
  case Blank extends DieFace("blank", "blank", Die.Defense)
  case OneShield extends DieFace("one-shield", "shield", Die.Defense)
  case TwoShields extends DieFace("two-shields", "two shields", Die.Defense)
  case Doubler extends DieFace("doubler", "doubler", Die.Defense)
