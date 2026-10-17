package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.{DeclaredNotes, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.{WarningSignals,
  WarningSignalsCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.model.{ActionRef, NoteArg, PlayerId, PowerId, PowerNote,
  PowerSourceRef}

/** A note whose key no power declares has no wording, so the Game Log would
  * render nothing for it. The walker refuses to journal it instead (derive
  * note keys design, "The walker check"). */
class UndeclaredNoteSuite extends munit.FunSuite:
  private val declared = Some(DeclaredNotes.of(catalog))
  private val power = WarningSignals.id
  private val source = PowerSourceRef.Card(WarningSignalsCard.id)

  test("a declared key is journaled"):
    val note = PowerNote(Some(source), "redistributed",
      Vector(NoteArg.Player(PlayerId("red"))))
    assertEquals(
      NoteCheck.journal(declared, power, note, covers = false).map(_.note),
      Some(note))

  test("an undeclared key is refused, and the failure names both"):
    val note = PowerNote(Some(source), "nonesuch", Vector.empty)
    val thrown = intercept[AssertionError](
      NoteCheck.journal(declared, power, note, covers = false))
    assert(thrown.getMessage.contains("denizen.warning-signals"),
      thrown.getMessage)
    assert(thrown.getMessage.contains("nonesuch"), thrown.getMessage)

  test("without the strict property the note is dropped, not thrown"):
    val note = PowerNote(Some(source), "nonesuch", Vector.empty)
    assertEquals(
      NoteCheck.journal(declared, power, note, covers = false, strict = false),
      None)

  test("a power the catalogs never heard of writes what it likes"):
    val invented = PowerId("denizen.nonesuch")
    val note = PowerNote(Some(source), "whatever", Vector.empty)
    assert(!DeclaredNotes.of(catalog).knows(invented))
    assertEquals(
      NoteCheck.journal(declared, invented, note, covers = false).map(_.note),
      Some(note))

  test("a catalog power that declares nothing is still checked"):
    val quiet = WalkerPowerCatalog.default(catalog).powers
      .find(_.noteKeys.isEmpty).map(_.id)
      .getOrElse(fail("no catalog power declares an empty key set"))
    assert(DeclaredNotes.of(catalog).knows(quiet))
    intercept[AssertionError](NoteCheck.journal(declared, quiet,
      PowerNote(Some(source), "whatever", Vector.empty), covers = false))

  test("powers with no catalog behind them declare nothing and are not checked"):
    val note = PowerNote(Some(source), "nonesuch", Vector.empty)
    assertEquals(
      NoteCheck.journal(None, power, note, covers = false).map(_.note),
      Some(note))

  test("the test build sets the strict property"):
    assert(NoteCheck.strictByDefault,
      "build.sbt must set -Doathdigital.notes.strict=true for the test " +
        "scope, or an undeclared note passes every suite silently")

  test("a procedure is held to its declared keys like a power"):
    val muster = ProcedureNotes.id(ActionRef.Muster)
    assert(DeclaredNotes.of(catalog).knows(muster),
      "a procedure is covered by the union")
    val declaredKey = PowerNote(None, MusterProcedure.mustered.name,
      Vector.empty)
    assertEquals(NoteCheck.journal(declared, muster, declaredKey,
      covers = false).map(_.note), Some(declaredKey))
    val thrown = intercept[AssertionError](NoteCheck.journal(declared, muster,
      PowerNote(None, "invented", Vector.empty), covers = false))
    assert(thrown.getMessage.contains("procedure.muster"), thrown.getMessage)
    assert(thrown.getMessage.contains("invented"), thrown.getMessage)

  test("a procedure that has not migrated writes no note at all"):
    val campaign = ProcedureNotes.id(ActionRef.Campaign)
    assert(DeclaredNotes.of(catalog).knows(campaign),
      "an unmigrated procedure is covered by the union, so it is refused " +
        "rather than unrecognised")
    val thrown = intercept[AssertionError](NoteCheck.journal(declared, campaign,
      PowerNote(None, "campaigned", Vector.empty), covers = false))
    assert(thrown.getMessage.contains("procedure.campaign"), thrown.getMessage)
