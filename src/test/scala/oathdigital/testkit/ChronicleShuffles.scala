package oathdigital.testkit

import oathdigital.application.ChronicleRandomPort

/** The Chronicle shuffles tests hand a setup, so the order a game is dealt in
  * is the same every run. Both are stubs: canned answers, no record of calls.
  */
object ChronicleShuffles:
  /** Stub of ChronicleRandomPort: shuffle reverses its input, so ordering
    * effects are deterministic and visible. A generated game makes the head
    * of its seating the first player, so reversing a two-seat request makes
    * the second requested seat first. */
  val reversing: ChronicleRandomPort = new ChronicleRandomPort:
    def shuffle[A](values: Vector[A]): Vector[A] = values.reverse

  /** Stub of ChronicleRandomPort: shuffle returns its input unchanged, so a
    * generated game keeps the seating order it was given. */
  val unshuffled: ChronicleRandomPort = new ChronicleRandomPort:
    def shuffle[A](values: Vector[A]): Vector[A] = values
