package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.DeclaredNotes
import oathdigital.gameplay.powers.campaign.{WarningSignals,
  WarningSignalsCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.{NoteArg, PlayerId, PowerNote, PowerSourceRef}

/** A note whose key no power declares has no wording, so the Game Log would
  * render nothing for it. The walker refuses to journal it instead (derive
  * note keys design, "The walker check"). */
class UndeclaredNoteSuite extends munit.FunSuite:
  private val declared = Some(DeclaredNotes.of(catalog))
  private val power = WarningSignals.id
  private val source = PowerSourceRef.Card(WarningSignalsCard.id)

  test("a declared key is journaled"):
    val note = PowerNote(source, "redistributed",
      Vector(NoteArg.Player(PlayerId("red"))))
    assertEquals(
      NoteCheck.journal(declared, power, note, covers = false).map(_.note),
      Some(note))

  test("an undeclared key is refused, and the failure names both"):
    val note = PowerNote(source, "nonesuch", Vector.empty)
    val thrown = intercept[AssertionError](
      NoteCheck.journal(declared, power, note, covers = false))
    assert(thrown.getMessage.contains("denizen.warning-signals"),
      thrown.getMessage)
    assert(thrown.getMessage.contains("nonesuch"), thrown.getMessage)

  test("without the strict property the note is dropped, not thrown"):
    val note = PowerNote(source, "nonesuch", Vector.empty)
    assertEquals(
      NoteCheck.journal(declared, power, note, covers = false, strict = false),
      None)

  test("powers with no catalog behind them declare nothing and are not checked"):
    val note = PowerNote(source, "nonesuch", Vector.empty)
    assertEquals(
      NoteCheck.journal(None, power, note, covers = false).map(_.note),
      Some(note))

  test("the test build sets the strict property"):
    assert(NoteCheck.strictByDefault,
      "build.sbt must set -Doathdigital.notes.strict=true for the test " +
        "scope, or an undeclared note passes every suite silently")
