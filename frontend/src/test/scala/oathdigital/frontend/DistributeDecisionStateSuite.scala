package oathdigital.frontend

/** The stepper interaction behind a parked distribution, with no idea what
  * it distributes: slots are opaque ids with bounds, and confirmation is
  * exactly `nothing left to place`.
  */
class DistributeDecisionStateSuite extends munit.FunSuite {
  private val slots = Vector(
    DistributeSlotBounds("arcane", 0, 2),
    DistributeSlotBounds("discord", 0, 2),
    DistributeSlotBounds("nomad", 1, 6))
  private val atMinimums = DistributeDecisionState.opened(slots, 5, None)

  test("a draft with no suggestions opens at the minimums") {
    assertEquals(slots.map(s => atMinimums.amount(s.item)), Vector(0, 0, 1))
    assertEquals(atMinimums.remaining, 4)
    assert(!atMinimums.canConfirm)
  }

  test("a draft with suggestions opens at them") {
    val suggested = DistributeDecisionState.opened(slots, 5,
      Some(Vector(2, 2, 1)))
    assertEquals(slots.map(s => suggested.amount(s.item)), Vector(2, 2, 1))
    assert(suggested.canConfirm)
  }

  test("increment stops at the slot maximum and decrement at its minimum") {
    val raised = atMinimums.increment("arcane").increment("arcane")
      .increment("arcane")
    assertEquals(raised.amount("arcane"), 2)
    assertEquals(atMinimums.decrement("nomad").amount("nomad"), 1)
    assertEquals(atMinimums.increment("missing"), atMinimums)
  }

  test("increment stops when nothing remains") {
    val spent = atMinimums.fill("nomad")
    assertEquals(spent.remaining, 0)
    assertEquals(spent.increment("arcane"), spent)
  }

  test("fill is limited by what remains") {
    val partly = atMinimums.fill("nomad")
    assertEquals(partly.amount("nomad"), 5)
    assertEquals(partly.remaining, 0)
  }

  test("fill is limited by the slot maximum") {
    val capped = atMinimums.fill("arcane")
    assertEquals(capped.amount("arcane"), 2)
    assertEquals(capped.remaining, 2)
  }

  test("fill with nothing remaining changes nothing") {
    val spent = atMinimums.fill("nomad")
    assertEquals(spent.fill("discord"), spent)
  }

  test("drain lowers a slot to its minimum") {
    val drained = atMinimums.fill("nomad").drain("nomad")
    assertEquals(drained.amount("nomad"), 1)
    assertEquals(drained.remaining, 4)
  }

  test("confirmation is enabled exactly when nothing remains") {
    assert(!atMinimums.increment("arcane").canConfirm)
    assert(atMinimums.fill("arcane").fill("discord").canConfirm)
  }
}
