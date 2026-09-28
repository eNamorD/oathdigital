package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RuleNotes
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
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

  /** The walker powers', the phase powers' and the game rules' keys. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    (WalkerPowerCatalog.default(catalog).powers.map(power =>
      of(power.id, power.noteKeys, power.noteSource,
        power.narratedDecisions)) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        of(power.id, power.noteKeys, power.noteSource,
          power.narratedDecisions)) ++
      RuleNotes.all.map((id, keys) => of(id, keys)))
      .foldLeft(NoteWordings(Map.empty))(_ ++ _)
