package oathdigital.application.gamelog

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}

/** What a migrated procedure's line reads, on the branches no golden script
  * exercises, and at which kind it posts (procedures journal their own notes
  * design, slice 2). A golden file proves the branch its script plays; these
  * are the branches it does not play, written down before the rule that
  * worded them was deleted.
  *
  * Rendering takes no game state: every assertion here is over plain text,
  * players, sites, banners and numbers, and a card with no states behind it
  * renders as its back, which is the part these branches leave untested
  * elsewhere.
  */
class ProcedureNoteLinesSuite extends munit.FunSuite:
  private val words = new LogWords(catalog, LogScripts.presentation)
  private val lines = new PowerLines(words, NoteWordings.default(catalog),
    LogScripts.formatter.procedures.noteKind)

  /** `key`'s sentence, as the Game Log renders it for a procedure's note. */
  private def spoken(procedure: ProcedureRef, key: NoteKey, args: NoteArg*)(
      using munit.Location): Vector[LogSpan] =
    lines.spoken(ProcedureNotes.id(procedure), key.sourceless(args*),
      Vector.empty, None).getOrElse(
        fail(s"no template for ${procedure.key}/${key.name}"))

  /** `key`'s sentence as plain text, for a line with no card in it. */
  private def said(procedure: ProcedureRef, key: NoteKey, args: NoteArg*)(
      using munit.Location): String =
    spoken(procedure, key, args*).map(_.text).mkString

  /** The kind `procedure`'s entry declares for `key`. */
  private def kindOf(procedure: ProcedureRef, key: NoteKey): LogKind =
    LogScripts.formatter.procedures.of(procedure).noteKind(key.name)

  test("a procedure's note posts at Action unless its entry says otherwise"):
    assertEquals(kindOf(ActionRef.Muster, MusterProcedure.mustered),
      LogKind.Action)

  test("a procedure whose line is still a rule declares no kind"):
    assertEquals(LogScripts.formatter.procedures.of(ActionRef.Travel)
      .noteKind("anything"), LogKind.Action)

  test("a count of one reads singular"):
    assertEquals(said(ActionRef.Muster, MusterProcedure.mustered,
      NoteArg.Number(1)), "Mustered 1 warband with ")
