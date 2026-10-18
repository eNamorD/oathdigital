package oathdigital.gameplay.powers

import java.nio.file.{Files, Path, Paths}
import oathdigital.gameplay.actions.{ProcedureNotes, RuleNotes}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.{ActionRef, NoteKey, NotePart, PowerId,
  ProcedureRef}

/** Every power's declared note keys and their wordings, exactly. Note keys
  * are journaled in `LogNoted` events and the log looks a saved game's
  * wording up by key, so a renamed or dropped key orphans the notes of
  * saved games. This fixture is the one place that says what the whole
  * catalog declares, and the one proof that moving a declaration moves
  * neither its name nor its sentence.
  *
  * `NOTE_KEYS_SNAPSHOT=write` rewrites the file instead of comparing; a
  * rewritten snapshot is reviewed line by line before it is committed.
  */
class DeclaredNoteKeysSuite extends munit.FunSuite:
  private val file: Path = Paths.get("src/test/resources/notes/declared-keys.txt")
  private val writing = sys.env.get("NOTE_KEYS_SNAPSHOT").contains("write")

  /** One entry per catalog listing. Six powers are both a contributing
    * power and a phase power, so they are listed twice. */
  private val listings: Vector[(PowerId, Vector[NoteKey])] =
    WalkerPowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++
    PhasePowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++ RuleNotes.all

  /** One entry per power, from the union the Game Log and the walker read,
    * so this fixture records what they see and not a second derivation of
    * it. A power listed in both catalogs has one declared set there. */
  private val declared: Vector[(PowerId, Vector[NoteKey])] =
    DeclaredNotes.of(catalog).keys.toVector

  /** A key's sentence, as the parts that render it: text as itself, an
    * argument as `{index}`, a plural as `{index?one|many}`. A key's name
    * alone would not say whether its wording survived being moved. */
  private def wording(template: Vector[NotePart]): String =
    template.map {
      case NotePart.Text(words) => words
      case NotePart.Arg(index) => s"{$index}"
      case NotePart.Plural(index, one, many) => s"{$index?$one|$many}"
    }.mkString

  /** One line per declared key: its power, its name and its wording, sorted,
    * so where a key sits in its object's body cannot change the snapshot.
    * Most powers in the catalogs note nothing and take `noteKeys`' empty
    * default; they contribute no line, so a power's last key going missing
    * takes its last line with it. */
  private def rendered: String =
    declared.flatMap((id, keys) => keys.map(key =>
      s"${id.value} ${key.name} = ${wording(key.template)}"))
      .sorted.mkString("", "\n", "\n")

  test("every power declares exactly the notes the snapshot records"):
    if writing then
      Files.createDirectories(file.getParent)
      Files.writeString(file, rendered)
    else
      assert(Files.exists(file), s"missing snapshot $file; run with " +
        "NOTE_KEYS_SNAPSHOT=write and review it")
      assertNoDiff(rendered, Files.readString(file))

  test("a power in both catalogs declares the same notes in each"):
    val twice = listings.groupBy(_._1).filter(_._2.sizeIs > 1)
    assertEquals(twice.keys.toVector.map(_.value).sorted, Vector(
      "denizen.assassin", "denizen.insomnia", "denizen.pied-piper",
      "denizen.silver-tongue", "denizen.vow-of-obedience",
      "denizen.vow-of-poverty"))
    twice.foreach: (id, entries) =>
      val each = entries.map(_._2.map(key =>
        s"${key.name} = ${wording(key.template)}").sorted).distinct
      assertEquals(each.size, 1,
        s"${id.value} declares different notes in each catalog: " +
          each.map(_.mkString("[", ", ", "]")).mkString(" vs "))

  test("every procedure reaches the declared union under its derived id"):
    val muster = ProcedureNotes.id(ActionRef.Muster)
    assertEquals(muster, PowerId("procedure.muster"))
    assert(ProcedureNotes.writes(muster),
      "the registry knows an id it built itself")
    assert(!ProcedureNotes.writes(PowerId("denizen.wolves")),
      "a power is not a procedure")
    val union = DeclaredNotes.of(catalog)
    ProcedureRef.all.foreach: procedure =>
      assert(union.knows(ProcedureNotes.id(procedure)),
        s"the union covers ${procedure.key}")
