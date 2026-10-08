package oathdigital.application

import oathdigital.testkit.ChronicleShuffles

/** What every `ChronicleRandomPort` promises (the trait's doc): a shuffle
  * returns the same values in some order. The real random port and the shared
  * stubs of `ChronicleShuffles` run it, so a stub cannot reorder a deck by
  * dropping or doubling a card. */
trait ChronicleRandomPortContract extends munit.FunSuite:
  protected def ports: Vector[(String, ChronicleRandomPort)]

  ports.foreach { (name, port) =>
    test(s"$name: a shuffle returns the same values in some order"):
      val values = Vector(1, 2, 3, 4, 5, 6, 7, 8, 2)
      assertEquals(port.shuffle(values).sorted, values.sorted)

    test(s"$name: an empty or one-value shuffle is unchanged"):
      assertEquals(port.shuffle(Vector.empty[Int]), Vector.empty)
      assertEquals(port.shuffle(Vector("only")), Vector("only"))
  }

class ChronicleRandomPortSuite extends ChronicleRandomPortContract:
  protected def ports = Vector(
    "random" -> ChronicleRandomPort.random,
    "reversing" -> ChronicleShuffles.reversing,
    "unshuffled" -> ChronicleShuffles.unshuffled)

/** What every `ShufflePolicy` promises (the trait's doc): the order holds the
  * same cards, whatever the policy puts on top. The one production policy runs
  * it under the real port and under both shared stubs. */
class ShufflePolicySuite extends munit.FunSuite:
  private val ports = Vector(
    "random" -> ChronicleRandomPort.random,
    "reversing" -> ChronicleShuffles.reversing,
    "unshuffled" -> ChronicleShuffles.unshuffled)

  ports.foreach { (name, port) =>
    test(s"implemented-first under the $name port keeps every card"):
      val cards = Vector(1, 2, 3, 4, 5, 6, 7, 8)
      val ordered = ShufflePolicy.implementedFirst.order(cards,
        Set(2, 4, 6), port)
      assertEquals(ordered.sorted, cards)

    test(s"implemented-first under the $name port puts implemented cards " +
        "ahead of the rest"):
      val ordered = ShufflePolicy.implementedFirst.order(
        Vector(1, 2, 3, 4, 5, 6, 7, 8), Set(2, 4, 6), port)
      assertEquals(ordered.take(3).toSet, Set(2, 4, 6))
  }
