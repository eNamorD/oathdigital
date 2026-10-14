package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{DeclaredNotes, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.model.{NoteKey, NotePart, PowerId}

/** Every power's note templates, by power and key (power log lines design,
  * "Wording"), the name a power's notes are written under when it names
  * one, and the decisions whose answer a power's notes tell. A note whose
  * power or key is missing here renders nothing. */
private[application] final case class NoteWordings(
    templates: Map[(PowerId, String), Vector[NotePart]],
    sources: Map[PowerId, String] = Map.empty,
    narrated: Set[String] = Set.empty):
  def template(power: PowerId, key: String): Option[Vector[NotePart]] =
    templates.get((power, key))
  /** The name `power`'s notes are written under in place of their source. */
  def source(power: PowerId): Option[String] = sources.get(power)
  /** `other`'s entries win where both have one. */
  def ++(other: NoteWordings): NoteWordings =
    NoteWordings(templates ++ other.templates, sources ++ other.sources,
      narrated ++ other.narrated)

private[application] object NoteWordings:
  def of(power: PowerId, keys: Vector[NoteKey],
      source: Option[String] = None,
      narrated: Set[String] = Set.empty): NoteWordings =
    NoteWordings(keys.map(key => (power, key.name) -> key.template).toMap,
      source.map(power -> _).toMap, narrated)

  /** The walker powers', the phase powers' and the game rules' keys. The
    * keys come from the one declared-set union, which already holds the
    * game rules' and gives a power listed in both catalogs one set; only
    * the note source and the narrated decisions are still read off each
    * power, because neither belongs to a key. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    val walkers = WalkerPowerCatalog.default(catalog)
    // The catalog built the union already; building a second one here would
    // walk both power catalogs again for the same answer.
    val declared = walkers.declared.getOrElse(DeclaredNotes.of(catalog))
    val templates = declared.keys.toVector.flatMap((power, keys) =>
      keys.map(key => (power, key.name) -> key.template)).toMap
    val named = walkers.powers.map(power =>
      of(power.id, Vector.empty, power.noteSource,
        power.narratedDecisions)) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        of(power.id, Vector.empty, power.noteSource,
          power.narratedDecisions))
    named.foldLeft(NoteWordings(templates))(_ ++ _)
