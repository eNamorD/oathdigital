package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RuleNotes
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.model.{NoteKey, PowerId}

/** Every note key the catalog declares, by power (derive note keys design,
  * "One declared set union"). The walker reads it to reject a note no power
  * declared, and the Game Log's `NoteWordings` builds its templates from it,
  * so both read one union instead of each rebuilding its own.
  */
final class DeclaredNotes private (val keys: Map[PowerId, Vector[NoteKey]]):
  def declares(power: PowerId, key: String): Boolean =
    keys.get(power).exists(_.exists(_.name == key))

object DeclaredNotes:
  /** A power listed in both catalogs keeps the keys of both, as the Game
    * Log's wordings do by keying on power and key name. Six powers are
    * listed twice and declare the same keys in each, so the listings are
    * concatenated and then taken by name: without that, every one of their
    * key names would appear twice. */
  def of(catalog: ExecutableCatalog): DeclaredNotes =
    of(WalkerPowerCatalog.contributing(catalog), catalog)

  /** The union over `walkers` and `catalog`'s phase powers and game rules.
    * The walker powers are taken as given, so that `WalkerPowerCatalog` can
    * build the union for the `WalkerPowers` it is building. */
  private[powers] def of(walkers: Vector[ContributingPower],
      catalog: ExecutableCatalog): DeclaredNotes = new DeclaredNotes(
    (walkers.map(power => power.id -> power.noteKeys) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        power.id -> power.noteKeys) ++ RuleNotes.all)
      .groupMapReduce(_._1)(_._2)(_ ++ _)
      .view.mapValues(_.distinctBy(_.name)).toMap)
