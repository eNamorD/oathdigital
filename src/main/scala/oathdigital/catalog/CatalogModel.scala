package oathdigital.catalog

import oathdigital.model.{
  CatalogRef,
  SiteId,
  SupplyRules,
  Tokens,
  VisionId
}

final case class DefinitionId(value: String) {
  require(value.trim.nonEmpty, "catalog definition ID must not be blank")
}

final case class PrintedComponentId(kind: String, value: String) {
  require(kind.trim.nonEmpty, "printed component ID kind must not be blank")
  require(value.trim.nonEmpty, "printed component ID must not be blank")
}

final case class RulesetMetadata(
    id: String,
    version: String,
    normativeGeneralRulesSourceId: String
)

final case class SourceProvenance(
    sourceId: String,
    file: String,
    page: Int,
    sheetSlot: Option[String]
)

final case class TranscriptionMetadata(
    method: String,
    confidence: String,
    reviewStatus: String
)

final case class ComponentMetadata(
    definitionId: DefinitionId,
    kind: String,
    name: String,
    printedComponentId: Option[PrintedComponentId],
    provenance: Vector[SourceProvenance],
    transcription: TranscriptionMetadata,
    unresolved: Vector[String]
)

final case class SetupCardDefinition(
    metadata: ComponentMetadata,
    step: Int,
    handlers: Vector[String],
    physicalRole: String
)

sealed trait PlayerBoardKind extends Product with Serializable
object PlayerBoardKind {
  case object Chancellor extends PlayerBoardKind
  case object Player extends PlayerBoardKind
}

/**
 * Only the verified Supply portion of a player board.
 *
 * `excludedReviewItems` preserves unresolved notes about other printed board
 * fields so consumers cannot mistake this partial definition for a complete
 * player-board transcription.
 */
final case class SupplyBoardDefinition(
    metadata: ComponentMetadata,
    boardKind: PlayerBoardKind,
    rules: SupplyRules,
    remainingValues: Vector[Int],
    excludedReviewItems: Vector[String]
)

final case class SiteDefinition(
    metadata: ComponentMetadata,
    id: SiteId,
    defense: Int,
    capacity: Int,
    relicSlots: Int,
    recoverDifficulty: Option[Int],
    startingResources: Tokens,
    forgeRequirements: Option[Tokens],
    powers: Vector[String]
)

final case class VisionGoal(kind: String, minimumVisionsDrawn: Int)

final case class VisionDefinition(
    metadata: ComponentMetadata,
    id: VisionId,
    handlers: Vector[String],
    goal: Option[VisionGoal]
)

final case class ExecutableCatalog(
    schemaVersion: String,
    ref: CatalogRef,
    ruleset: RulesetMetadata,
    setupCards: Vector[SetupCardDefinition],
    supplyBoards: Vector[SupplyBoardDefinition],
    sites: Vector[SiteDefinition],
    visions: Vector[VisionDefinition]
)

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
    selection: CatalogSelection,
    expectedCatalog: Option[CatalogRef] = None
)

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

  final case class WrongType(
      path: String,
      expected: String,
      actual: String
  ) extends CatalogLoadError {
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

  final case class DuplicateDefinitionId(
      path: String,
      id: DefinitionId
  ) extends CatalogLoadError {
    override val message: String = s"duplicate definition ID ${id.value}"
  }

  final case class DuplicatePrintedComponentId(
      path: String,
      id: PrintedComponentId
  ) extends CatalogLoadError {
    override val message: String =
      s"duplicate printed component ID ${id.kind}:${id.value}"
  }

  final case class UnknownSourceReference(path: String, sourceId: String)
      extends CatalogLoadError {
    override val message: String = s"unknown source reference $sourceId"
  }

  final case class IdentityPolicyMismatch(path: String, detail: String)
      extends CatalogLoadError {
    override val message: String = detail
  }

  final case class UnsupportedExecutableKind(path: String, kind: String)
      extends CatalogLoadError {
    override val message: String =
      s"component kind $kind has no executable decoder"
  }

  final case class UnresolvedRequiredFields(
      path: String,
      definitionId: DefinitionId,
      items: Vector[String]
  ) extends CatalogLoadError {
    override val message: String =
      s"${definitionId.value} has unresolved required fields: " +
        items.mkString("; ")
  }
}
