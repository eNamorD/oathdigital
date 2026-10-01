package oathdigital.model

private[model] object IdentityValidation:
  def nonBlank(label: String, value: String): Unit =
    require(value.trim.nonEmpty, s"$label must not be blank")

opaque type PlayerId = String
object PlayerId:
  def apply(value: String): PlayerId =
    IdentityValidation.nonBlank("player ID", value)
    value
  def unapply(id: PlayerId): Some[String] = Some(id)
  extension (id: PlayerId) def value: String = id

opaque type LineageId = String
object LineageId:
  def apply(value: String): LineageId =
    IdentityValidation.nonBlank("lineage ID", value)
    value
  def unapply(id: LineageId): Some[String] = Some(id)
  extension (id: LineageId) def value: String = id

opaque type DecisionId = String
object DecisionId:
  def apply(value: String): DecisionId =
    IdentityValidation.nonBlank("decision ID", value)
    value
  def unapply(id: DecisionId): Some[String] = Some(id)
  extension (id: DecisionId) def value: String = id

opaque type PowerId = String
object PowerId:
  private val pattern = "[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+"

  def apply(value: String): PowerId =
    require(value.matches(pattern), s"invalid stable power ID $value")
    value
  def unapply(id: PowerId): Some[String] = Some(id)
  extension (id: PowerId) def value: String = id

  /** Safe parse for untrusted (e.g. wire) input: `None` rather than throwing
   * when `value` does not satisfy the stable power ID shape. */
  def fromValue(value: String): Option[PowerId] =
    if value.matches(pattern) then Some(value) else None

sealed trait ComponentId extends Product with Serializable:
  def value: String
  def kind: String

sealed trait CardId extends ComponentId
sealed trait WorldCardId extends CardId

final case class DenizenId(value: String) extends WorldCardId:
  IdentityValidation.nonBlank("denizen ID", value)
  override val kind: String = "denizen"

final case class VisionId(value: String) extends WorldCardId:
  IdentityValidation.nonBlank("Vision ID", value)
  override val kind: String = "vision"

final case class RelicId(value: String) extends CardId:
  IdentityValidation.nonBlank("relic ID", value)
  override val kind: String = "relic"

object RelicId:
  /** Safe parse for untrusted (e.g. wire) input: `None` rather than throwing
   * when `value` is blank. Mirrors `PowerId.fromValue`. */
  def fromValue(value: String): Option[RelicId] =
    if value.trim.nonEmpty then Some(RelicId(value)) else None

final case class EdificeId(value: String) extends CardId:
  IdentityValidation.nonBlank("edifice ID", value)
  override val kind: String = "edifice"

final case class LegacyId(value: String) extends CardId:
  IdentityValidation.nonBlank("legacy ID", value)
  override val kind: String = "legacy"

final case class SiteId(value: String) extends ComponentId:
  IdentityValidation.nonBlank("site ID", value)
  override val kind: String = "site"
