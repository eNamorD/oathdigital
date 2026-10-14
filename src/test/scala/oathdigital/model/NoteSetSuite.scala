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

  test("a set rejects two keys of the same name"):
    val notes = NoteSet(PowerId("test.four"))
    notes.key("took", sentence)
    val thrown = intercept[IllegalArgumentException](notes.key("took", sentence))
    assert(thrown.getMessage.contains("test.four"), thrown.getMessage)
    assert(thrown.getMessage.contains("took"), thrown.getMessage)
