package oathdigital.gameplay.powers

import java.nio.file.{Files, Path, Paths}
import oathdigital.gameplay.actions.RuleNotes
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.{NoteKey, PowerId}

/** Every power's declared note keys, exactly. Note keys are journaled in
  * `PowerNoted` events and the log looks a saved game's wording up by key,
  * so a renamed or dropped key orphans the notes of saved games. This
  * fixture is the one place that says what the whole catalog declares.
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

  /** One entry per power, as the wordings table sees it: keyed by power and
    * key name, so a power listed in both catalogs has one declared set. */
  private val declared: Vector[(PowerId, Vector[NoteKey])] =
    listings.groupMapReduce(_._1)(_._2)(_ ++ _).toVector
      .map((id, keys) => id -> keys.distinctBy(_.name))

  /** One line per power that declares a key: its id, then its key names,
    * both sorted, so where a key sits in its object's body cannot change
    * the snapshot. Most powers in the catalogs note nothing and take
    * `noteKeys`' empty default; they are left out, so a power's last key
    * going missing removes its line. */
  private def rendered: String =
    declared.filter(_._2.nonEmpty).map((id, keys) =>
      s"${id.value}: ${keys.map(_.name).sorted.mkString(", ")}")
      .sorted.mkString("", "\n", "\n")

  test("every power declares exactly the keys the snapshot records"):
    if writing then
      Files.createDirectories(file.getParent)
      Files.writeString(file, rendered)
    else
      assert(Files.exists(file), s"missing snapshot $file; run with " +
        "NOTE_KEYS_SNAPSHOT=write and review it")
      assertNoDiff(rendered, Files.readString(file))

  test("a power in both catalogs declares the same keys in each"):
    val twice = listings.groupBy(_._1).filter(_._2.sizeIs > 1)
    assertEquals(twice.keys.toVector.map(_.value).sorted, Vector(
      "denizen.assassin", "denizen.insomnia", "denizen.pied-piper",
      "denizen.silver-tongue", "denizen.vow-of-obedience",
      "denizen.vow-of-poverty"))
    twice.foreach((id, entries) => assertEquals(
      entries.map(_._2.map(_.name).sorted).distinct.size, 1, id.value))
