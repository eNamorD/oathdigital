package oathdigital.gameplay.walker

import oathdigital.gameplay.powers.DeclaredNotes
import oathdigital.model.{PowerId, PowerNote}

/** Journals a note only if its power declares its key (derive note keys
  * design, decisions 5 and 6). A note with no declared key has no wording,
  * so the Game Log renders nothing for it and the player's log is quietly
  * short of a line.
  *
  * In tests that is a programming error and fails here, at the note, so any
  * scripted journal that reaches the note fails whether or not its suite
  * asserts the line's text. In production the note is dropped and the error
  * is logged: the Log pane is player-facing, and one missing line must not
  * take the whole pane down with it.
  *
  * A power the union has never heard of is a test's own invention, so what
  * it declares is that test's business and its notes pass. That is what
  * lets a harness driving both catalog powers and synthetic ones carry the
  * real union: the catalog powers are checked, the inventions are not.
  *
  * Which of the two happens is `strict`, whose default is the system
  * property `oathdigital.notes.strict`. `build.sbt` sets that property for
  * the test scope and nothing sets it in production, so the player-facing
  * behaviour is the default rather than something a release must remember
  * to ask for. `UndeclaredNoteSuite` asserts the test scope has it.
  */
private[walker] object NoteCheck:
  private val logger =
    org.slf4j.LoggerFactory.getLogger("oathdigital.gameplay.walker.NoteCheck")

  /** Whether an undeclared note fails rather than being dropped. */
  def strictByDefault: Boolean =
    sys.props.get("oathdigital.notes.strict").contains("true")

  def journal(declared: Option[DeclaredNotes], power: PowerId,
      note: PowerNote, covers: Boolean, strict: Boolean = strictByDefault)
      : Option[PowerNoted] =
    if declared.forall(union =>
        !union.knows(power) || union.declares(power, note.key)) then
      Some(PowerNoted(power, note, covers))
    else
      val message = s"${power.value} wrote the undeclared note ${note.key} " +
        s"from ${note.source}"
      if strict then throw new AssertionError(message)
      logger.error(message)
      None
