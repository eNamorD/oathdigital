package oathdigital.testkit

import oathdigital.application.ChronicleRandomPort

/** Stubs of ChronicleRandomPort (canned answers, no record of calls) that keep
  * a generated game's seating and card order the same every run: `reversing`
  * reverses every shuffle, `unshuffled` leaves it as given.
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
