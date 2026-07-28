package oathdigital.engine

private object InMemoryJournalSuite {
  final case class Move(name: String) extends Action
}

class InMemoryJournalSuite extends munit.FunSuite {
  import InMemoryJournalSuite.Move

  test("successful appends assign consecutive indexes and update size") {
    val journal = new InMemoryJournal

    assertEquals(
      journal.append(0, Move("first")),
      AppendResult.Appended(RecordedAction(0, Move("first")))
    )
    assertEquals(
      journal.append(1, Move("second")),
      AppendResult.Appended(RecordedAction(1, Move("second")))
    )
    assertEquals(journal.size, 2L)
  }

  test("stale and future expected indexes conflict without changing the journal") {
    val journal = new InMemoryJournal
    journal.append(0, Move("accepted"))

    assertEquals(journal.append(0, Move("stale")), AppendResult.Conflict(0, 1))
    assertEquals(journal.append(3, Move("future")), AppendResult.Conflict(3, 1))
    assertEquals(journal.size, 1L)
    assertEquals(journal.read(0), Vector(RecordedAction(0, Move("accepted"))))
  }

  test("reads preserve append order and begin at the requested index") {
    val journal = new InMemoryJournal
    val moves = Vector(Move("first"), Move("second"), Move("third"))
    moves.zipWithIndex.foreach { case (move, index) =>
      journal.append(index.toLong, move)
    }

    assertEquals(
      journal.read(1),
      Vector(RecordedAction(1, moves(1)), RecordedAction(2, moves(2)))
    )
    assertEquals(journal.read(3), Vector.empty)
  }

  test("reads reject negative indexes") {
    val journal = new InMemoryJournal

    intercept[IllegalArgumentException](journal.read(-1))
  }
}
