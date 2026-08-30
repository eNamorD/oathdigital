package oathdigital.catalog

import oathdigital.model.{CatalogRef, PowerId, SiteId, SupplyRules, Tokens, VisionId}

final case class DefinitionId(value: String) {
  require(value.trim.nonEmpty, "catalog definition ID must not be blank")
}

final case class Suit(value: String) {
  require(Suit.values.contains(value), s"unsupported suit $value")
}

object Suit {
  val values: Set[String] =
    Set("arcane", "beast", "discord", "hearth", "nomad", "order")

  val Arcane: Suit = Suit("arcane")
  val Beast: Suit = Suit("beast")
  val Discord: Suit = Suit("discord")
  val Hearth: Suit = Suit("hearth")
  val Nomad: Suit = Suit("nomad")
  val Order: Suit = Suit("order")

}

final case class CatalogPower(id: PowerId, persistent: Boolean, rulesText: String) {
  require(rulesText.trim.nonEmpty, "power rules text must not be blank")
}
object CatalogPower {
  def apply(id: String, persistent: Boolean, rulesText: String): CatalogPower =
    CatalogPower(PowerId(id), persistent, rulesText)
}

trait CatalogPoweredDefinition {
  def powers: Vector[CatalogPower]
  final def handlers: Vector[String] = powers.map(_.id.value)
  final def rulesText: String = powers.map(_.rulesText).mkString("\n\n")
}

final case class DenizenDefinition(
    id: DefinitionId,
    name: String,
    suit: Suit,
    restrictions: CardRestrictions,
    powers: Vector[CatalogPower]
) extends CatalogPoweredDefinition

sealed trait CardRestrictions extends Product with Serializable
object CardRestrictions {
  case object Unrestricted extends CardRestrictions
  case object Locked extends CardRestrictions
  case object SiteOnly extends CardRestrictions
  case object AdviserOnly extends CardRestrictions
  case object LockedAdviserOnly extends CardRestrictions
}

sealed trait RelicRole extends Product with Serializable
object RelicRole {
  case object Ordinary extends RelicRole
  case object GrandScepter extends RelicRole
}

final case class RelicDefinition(
    id: DefinitionId,
    name: String,
    role: RelicRole,
    value: Int,
    defense: Int,
    powers: Vector[CatalogPower]
) extends CatalogPoweredDefinition

final case class EdificeFaceDefinition(
    name: String,
    restrictions: CardRestrictions,
    powers: Vector[CatalogPower]
) extends CatalogPoweredDefinition

final case class EdificeDefinition(
    id: DefinitionId,
    suit: Suit,
    intact: EdificeFaceDefinition,
    ruined: EdificeFaceDefinition
)

final case class LegacyDefinition(
    id: DefinitionId,
    name: String,
    powers: Vector[CatalogPower]
) extends CatalogPoweredDefinition

final case class SiteDefinition(
    id: SiteId,
    name: String,
    defense: Int,
    capacity: Int,
    relicSlots: Int,
    recoverDifficulty: Option[Int],
    startingResources: Tokens,
    forgeRequirements: Option[Tokens],
    handlers: Vector[String]
)

/**
 * Complete runtime component catalog.
 *
 * The final three vectors remain as empty compatibility projections while
 * setup cards, player boards, and visions move into rules-owned code.
 */
final case class ExecutableCatalog(
    schemaVersion: String,
    ref: CatalogRef,
    denizens: Vector[DenizenDefinition],
    relics: Vector[RelicDefinition],
    edifices: Vector[EdificeDefinition],
    legacies: Vector[LegacyDefinition],
    sites: Vector[SiteDefinition],
    setupCards: Vector[SetupCardDefinition] = Vector.empty,
    supplyBoards: Vector[SupplyBoardDefinition] = Vector.empty,
    visions: Vector[VisionDefinition] = Vector.empty
)

/**
 * Compatibility request shape. Runtime catalogs are now loaded atomically,
 * so selection flags are intentionally ignored by CatalogLoader.
 */
final case class CatalogSelection(
    setupCards: Boolean = false,
    supplyBoards: Boolean = false,
    sites: Boolean = false,
    visions: Boolean = false
)

object CatalogSelection {
  val MetadataOnly: CatalogSelection = CatalogSelection()
  val SetupFoundation: CatalogSelection =
    CatalogSelection(setupCards = true, supplyBoards = true)
}

final case class CatalogLoadRequest(
    selection: CatalogSelection = CatalogSelection.MetadataOnly,
    expectedCatalog: Option[CatalogRef] = None
)

// Temporary source-compatible shells for consumers being migrated to
// rules-owned setup data. CatalogLoader never constructs these definitions.
final case class SetupCardDefinition(step: Int)
final case class SupplyBoardDefinition(rules: SupplyRules)
final case class VisionDefinition(id: VisionId)

sealed trait CatalogLoadError extends Product with Serializable {
  def path: String
  def message: String
}

object CatalogLoadError {
  final case class InvalidJson(detail: String) extends CatalogLoadError {
    override val path: String = "$"
    override val message: String = detail
  }

  final case class FileReadFailed(pathValue: String, detail: String)
      extends CatalogLoadError {
    override val path: String = pathValue
    override val message: String = detail
  }

  final case class MissingField(path: String) extends CatalogLoadError {
    override val message: String = "required field is missing"
  }

  final case class WrongType(path: String, expected: String, actual: String)
      extends CatalogLoadError {
    override val message: String = s"expected $expected, found $actual"
  }

  final case class InvalidValue(path: String, detail: String)
      extends CatalogLoadError {
    override val message: String = detail
  }

  final case class UnsupportedSchemaVersion(
      path: String,
      expected: String,
      actual: String
  ) extends CatalogLoadError {
    override val message: String =
      s"expected schema $expected, found $actual"
  }

  final case class IncompatibleCatalog(
      path: String,
      expected: CatalogRef,
      actual: CatalogRef
  ) extends CatalogLoadError {
    override val message: String =
      s"expected ${expected.ruleset}@${expected.version}, " +
        s"found ${actual.ruleset}@${actual.version}"
  }

  final case class DuplicateDefinitionId(path: String, id: DefinitionId)
      extends CatalogLoadError {
    override val message: String = s"duplicate definition ID ${id.value}"
  }

  final case class DuplicatePowerId(
      path: String,
      id: PowerId,
      firstPath: String
  ) extends CatalogLoadError {
    override val message: String =
      s"duplicate power ID ${id.value}; first declared at $firstPath"
  }
}
