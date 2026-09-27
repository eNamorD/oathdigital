package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model.{NoteKey, NotePart, PowerId}

/** Every power's note templates, by power and key (power log lines design,
  * "Wording"). A note whose power or key is missing here renders nothing. */
private[application] final case class NoteWordings(
    templates: Map[(PowerId, String), Vector[NotePart]]):
  def template(power: PowerId, key: String): Option[Vector[NotePart]] =
    templates.get((power, key))
  /** `other`'s templates win where both have one. */
  def ++(other: NoteWordings): NoteWordings =
    NoteWordings(templates ++ other.templates)

private[application] object NoteWordings:
  def of(power: PowerId, keys: Vector[NoteKey]): NoteWordings =
    NoteWordings(keys.map(key => (power, key.name) -> key.template).toMap)

  /** The walker powers' and the phase powers' keys. */
  def default(catalog: ExecutableCatalog): NoteWordings =
    (WalkerPowerCatalog.default(catalog).powers.map(power =>
      of(power.id, power.noteKeys)) ++
      PhasePowerCatalog.default(catalog).powers.map(power =>
        of(power.id, power.noteKeys)))
      .foldLeft(NoteWordings(Map.empty))(_ ++ _)
