package oathdigital.model

class NoteSetSuite extends munit.FunSuite:
  private val sentence = Vector(NotePart.Text("Took it."))

  test("a key is declared by being created"):
    val notes = NoteSet(PowerId("test.one"))
    val took = notes.key("took", sentence)
    assertEquals(notes.declared, Vector(took))
    assertEquals(took.name, "took")
    assertEquals(took.template, sentence)

  test("an included set's keys are declared too, after the set's own"):
    val shared = NoteSet.unowned("shared")
    val discarded = shared.key("discarded", Vector(NotePart.Text("Gone.")))
    val notes = NoteSet(PowerId("test.two"))
    val took = notes.key("took", sentence)
    notes.include(shared)
    assertEquals(notes.declared, Vector(took, discarded))

  test("including a set twice declares its keys once"):
    val shared = NoteSet.unowned("shared")
    shared.key("discarded", Vector(NotePart.Text("Gone.")))
    val notes = NoteSet(PowerId("test.three"))
    notes.include(shared)
    notes.include(shared)
    assertEquals(notes.declared.map(_.name), Vector("discarded"))

  test("two sets may include a third, and its key arrives once"):
    val shared = NoteSet.unowned("shared")
    val discarded = shared.key("discarded", Vector(NotePart.Text("Gone.")))
    val left = NoteSet.unowned("left")
    left.include(shared)
    val right = NoteSet.unowned("right")
    right.include(shared)
    val notes = NoteSet(PowerId("test.diamond"))
    notes.include(left)
    notes.include(right)
    assertEquals(notes.declared, Vector(discarded))

  test("a set refuses to include one that includes it"):
    val first = NoteSet(PowerId("test.first"))
    val second = NoteSet(PowerId("test.second"))
    first.include(second)
    val thrown = intercept[IllegalArgumentException](second.include(first))
    assert(thrown.getMessage.contains("test.first"), thrown.getMessage)
    assert(thrown.getMessage.contains("test.second"), thrown.getMessage)

  test("a set refuses to include itself"):
    val notes = NoteSet(PowerId("test.self"))
    intercept[IllegalArgumentException](notes.include(notes))

  test("a borrowed key that clashes with an owned one is refused, not picked"):
    val shared = NoteSet.unowned("shared")
    shared.key("discarded", Vector(NotePart.Text("Gone.")))
    val notes = NoteSet(PowerId("test.clash"))
    notes.key("discarded", Vector(NotePart.Text("Discarded at end.")))
    notes.include(shared)
    val thrown = intercept[IllegalArgumentException](notes.declared)
    assert(thrown.getMessage.contains("test.clash"), thrown.getMessage)
    assert(thrown.getMessage.contains("discarded"), thrown.getMessage)

  test("a set rejects two keys of the same name"):
    val notes = NoteSet(PowerId("test.four"))
    notes.key("took", sentence)
    val thrown = intercept[IllegalArgumentException](notes.key("took", sentence))
    assert(thrown.getMessage.contains("test.four"), thrown.getMessage)
    assert(thrown.getMessage.contains("took"), thrown.getMessage)
