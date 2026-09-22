package oathdigital.application

/**
 * The application-layer source of randomness for Chronicle setup: every
 * resolved order is recorded in the start event, so the engine and replay
 * stay RNG-free (2026-09-21 Chronicle design, "Randomness").
 */
trait ChronicleRandomPort {
  def shuffle[A](values: Vector[A]): Vector[A]
}
object ChronicleRandomPort {
  val random: ChronicleRandomPort = new ChronicleRandomPort {
    private val rng = new scala.util.Random()
    def shuffle[A](values: Vector[A]): Vector[A] = rng.shuffle(values)
  }
}

/**
 * How a deck's cards are ordered before dealing. `implemented` marks the
 * cards that should reach the top -- read from the reviewed power catalog,
 * never a hand-kept list.
 */
trait ShufflePolicy {
  def order[A](cards: Vector[A], implemented: A => Boolean,
      random: ChronicleRandomPort): Vector[A]
}
object ShufflePolicy {
  /**
   * The alpha policy (2026-09-21 Chronicle design, "Randomness"): implemented
   * cards are shuffled and placed on top, the rest shuffled below. Regional
   * discards, starting hands and the first packet are dealt from the top, so
   * they draw implemented cards first. A uniform policy replaces this once
   * the catalog is complete.
   */
  val implementedFirst: ShufflePolicy = new ShufflePolicy {
    def order[A](cards: Vector[A], implemented: A => Boolean,
        random: ChronicleRandomPort): Vector[A] = {
      val (impl, rest) = cards.partition(implemented)
      random.shuffle(impl) ++ random.shuffle(rest)
    }
  }
}
