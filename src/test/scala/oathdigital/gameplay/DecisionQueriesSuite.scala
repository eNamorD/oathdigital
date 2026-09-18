package oathdigital.gameplay

import munit.FunSuite
import oathdigital.gameplay.walker.DecisionQueries
import oathdigital.model._

class DecisionQueriesSuite extends FunSuite {
  private val first = DecisionOption.Button(DecisionOptionRef.Button("first"), "First")
  private val second = DecisionOption.Button(DecisionOptionRef.Button("second"), "Second")
  private val options = Vector(first, second)

  test("partition rejects placements above a section maximum") {
    val query = DecisionQuery.Partition(Vector(
      DecisionSection("keep", "Keep", 1, Some(1)),
      DecisionSection("discard", "Discard", 0)), options)
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(first.ref, "keep"),
      DecisionPlacement(second.ref, "keep")))
    assert(DecisionQueries.accepts("cards", query, answer).isLeft)
  }

  test("partition rejects a declared maximum below minimum") {
    val query = DecisionQuery.Partition(Vector(
      DecisionSection("keep", "Keep", 1, Some(0)),
      DecisionSection("discard", "Discard", 0)), options)
    assert(DecisionQueries.wellFormed("cards", query).isLeft)
  }

  test("partition rejects maxima that leave only one possible section") {
    val query = DecisionQuery.Partition(Vector(
      DecisionSection("blocked", "Blocked", 0, Some(0)),
      DecisionSection("only", "Only", 0, Some(2))), options)
    assert(DecisionQueries.wellFormed("cards", query).isLeft)
  }

  test("unbounded partition still accepts existing allocations") {
    val query = DecisionQuery.Partition(Vector(
      DecisionSection("favor", "Favor", 1),
      DecisionSection("secret", "Secret", 0)), options)
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(first.ref, "favor"),
      DecisionPlacement(second.ref, "secret")))
    assert(DecisionQueries.wellFormed("forge", query).isRight)
    assert(DecisionQueries.accepts("forge", query, answer).isRight)
  }
}
