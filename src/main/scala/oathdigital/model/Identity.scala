package oathdigital.model

private[model] object IdentityValidation {
  def nonBlank(label: String, value: String): Unit =
    require(value.trim.nonEmpty, s"$label must not be blank")
}

final case class PlayerId(value: String) {
  IdentityValidation.nonBlank("player ID", value)
}

final case class LineageId(value: String) {
  IdentityValidation.nonBlank("lineage ID", value)
}

final case class DecisionId(value: String) {
  IdentityValidation.nonBlank("decision ID", value)
}

final case class PowerId(value: String) {
  require(value.matches(PowerId.pattern), s"invalid stable power ID $value")
}

object PowerId {
  private val pattern = "[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+"

  /** Safe parse for untrusted (e.g. wire) input: `None` rather than throwing
   * when `value` does not satisfy the stable power ID shape. */
  def fromValue(value: String): Option[PowerId] =
    if (value.matches(pattern)) Some(PowerId(value)) else None
}

final case class CatalogRef(ruleset: String, version: String) {
  IdentityValidation.nonBlank("ruleset", ruleset)
  IdentityValidation.nonBlank("catalog version", version)
}

sealed trait ComponentId extends Product with Serializable {
  def value: String
  def kind: String
}

sealed trait CardId extends ComponentId
sealed trait WorldCardId extends CardId

final case class DenizenId(value: String) extends WorldCardId {
  IdentityValidation.nonBlank("denizen ID", value)
  override val kind: String = "denizen"
}

final case class VisionId(value: String) extends WorldCardId {
  IdentityValidation.nonBlank("Vision ID", value)
  override val kind: String = "vision"
}

final case class RelicId(value: String) extends CardId {
  IdentityValidation.nonBlank("relic ID", value)
  override val kind: String = "relic"
}

object RelicId {
  /** Safe parse for untrusted (e.g. wire) input: `None` rather than throwing
   * when `value` is blank. Mirrors `PowerId.fromValue`. */
  def fromValue(value: String): Option[RelicId] =
    if (value.trim.nonEmpty) Some(RelicId(value)) else None
}

final case class EdificeId(value: String) extends CardId {
  IdentityValidation.nonBlank("edifice ID", value)
  override val kind: String = "edifice"
}

final case class LegacyId(value: String) extends CardId {
  IdentityValidation.nonBlank("legacy ID", value)
  override val kind: String = "legacy"
}

final case class SiteId(value: String) extends ComponentId {
  IdentityValidation.nonBlank("site ID", value)
  override val kind: String = "site"
}
