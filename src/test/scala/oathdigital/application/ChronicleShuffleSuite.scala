package oathdigital.application

class ChronicleShuffleSuite extends munit.FunSuite {
  /** Reverses the input so ordering effects are deterministic and visible. */
  private val reversing: ChronicleRandomPort = new ChronicleRandomPort {
    def shuffle[A](values: Vector[A]): Vector[A] = values.reverse
  }

  test("implemented-first puts every implemented card ahead of every other") {
    val cards = Vector(1, 2, 3, 4, 5, 6)
    val implemented = Set(2, 4)
    val ordered = ShufflePolicy.implementedFirst.order(cards, implemented, reversing)
    assertEquals(ordered, Vector(4, 2, 6, 5, 3, 1))
  }

  test("an empty implemented set shuffles everything as the remainder") {
    val cards = Vector("a", "b", "c")
    val ordered = ShufflePolicy.implementedFirst.order(cards, Set.empty[String], reversing)
    assertEquals(ordered, Vector("c", "b", "a"))
  }

  test("the random port shuffles without changing membership") {
    val values = Vector(1, 2, 3, 4, 5)
    assertEquals(ChronicleRandomPort.random.shuffle(values).sorted, values)
  }
}
