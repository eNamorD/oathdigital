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
  * Both wires that show a roll send these faces: the log's spans and, since
  * the decision-id vocabulary decision (item 3), the decision wire's roll
  * outcome. Each reaches them through one table from the engine's own open
  * face type (`application.RolledFaces`), so neither spells a key itself and
  * no test is needed to hold two spellings together.
  *
  * The journal is the exception, and deliberately: `GameEventJsonSupport`
  * and `WalkerValueCodec` spell the keys again for the durable event
  * payloads, so that a rename made for a projection cannot change what a
  * recorded roll means.
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

object DieFace:
  /** The faces by their wire key. A decoder reads a face per rolled die, so
    * it looks the key up here rather than scanning `values`, which allocates
    * the array again on every call. */
  val byKey: Map[String, DieFace] = values.map(face => face.key -> face).toMap
