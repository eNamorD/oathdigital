package oathdigital.catalog

import oathdigital.model.{CardId, CatalogRef, DenizenId, EdificeId, LegacyId,
  PowerId, RelicId, SiteId, Suit, SupplyRules, Tokens, VisionId}

opaque type DefinitionId = String
object DefinitionId:
  def apply(value: String): DefinitionId =
    require(value.trim.nonEmpty, "catalog definition ID must not be blank")
    value
  def unapply(id: DefinitionId): Some[String] = Some(id)
  extension (id: DefinitionId) def value: String = id

final case class CatalogPower(id: PowerId, persistent: Boolean, rulesText: String):
  require(rulesText.trim.nonEmpty, "power rules text must not be blank")

trait CatalogPoweredDefinition:
  def powers: Vector[CatalogPower]
  final def handlers: Vector[String] = powers.map(_.id.value)
  final def rulesText: String = powers.map(_.rulesText).mkString("\n\n")

final case class DenizenDefinition(
    id: DefinitionId,
    name: String,
    suit: Suit,
    restrictions: CardRestrictions,
    powers: Vector[CatalogPower]
) extends CatalogPoweredDefinition

enum CardRestrictions { case Unrestricted, Locked, SiteOnly, AdviserOnly, LockedAdviserOnly }

enum RelicRole { case Ordinary, GrandScepter }

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
):
  def denizen(id: DenizenId): Option[DenizenDefinition] =
    denizenById.get(id.value)
  def relic(id: RelicId): Option[RelicDefinition] = relicById.get(id.value)
  def edifice(id: EdificeId): Option[EdificeDefinition] =
    edificeById.get(id.value)
  def legacy(id: LegacyId): Option[LegacyDefinition] = legacyById.get(id.value)
  def site(id: SiteId): Option[SiteDefinition] = siteById.get(id)

  /** The card whose printed powers include `power`. */
  def denizenWithPower(power: PowerId): Option[DenizenDefinition] =
    denizenByPower.get(power)
  def relicWithPower(power: PowerId): Option[RelicDefinition] =
    relicByPower.get(power)
  /** Either face's powers count. */
  def edificeWithPower(power: PowerId): Option[EdificeDefinition] =
    edificeByPower.get(power)
  def siteWithHandler(handler: PowerId): Option[SiteDefinition] =
    siteByHandler.get(handler)

  /** A power printed on a denizen, relic, edifice face or legacy. Sites carry
    * handler IDs only, so a site handler has no printed power.
    */
  def printedPower(id: PowerId): Option[CatalogPower] = powerById.get(id)

  /** Suit of a denizen or edifice; other card kinds have none. */
  def suitOf(id: CardId): Option[Suit] =
    denizenById.get(id.value).map(_.suit)
      .orElse(edificeById.get(id.value).map(_.suit))

  // Built on first use, so a test's `copy` indexes its own components. The
  // first entry for a key wins, matching the scans these replace.
  private def firstBy[K, A](entries: Iterable[(K, A)]): Map[K, A] =
    entries.foldLeft(Map.empty[K, A]) { case (index, (key, value)) =>
      if index.contains(key) then index else index.updated(key, value)
    }

  private lazy val denizenById = firstBy(denizens.map(d => d.id.value -> d))
  private lazy val relicById = firstBy(relics.map(r => r.id.value -> r))
  private lazy val edificeById = firstBy(edifices.map(e => e.id.value -> e))
  private lazy val legacyById = firstBy(legacies.map(l => l.id.value -> l))
  private lazy val siteById = firstBy(sites.map(s => s.id -> s))
  private lazy val denizenByPower =
    firstBy(denizens.flatMap(d => d.powers.map(_.id -> d)))
  private lazy val relicByPower =
    firstBy(relics.flatMap(r => r.powers.map(_.id -> r)))
  private lazy val edificeByPower = firstBy(edifices.flatMap(e =>
    (e.intact.powers ++ e.ruined.powers).map(_.id -> e)))
  private lazy val siteByHandler =
    firstBy(sites.flatMap(s => s.handlers.map(PowerId(_) -> s)))
  private lazy val powerById = firstBy(
    (denizens.flatMap(_.powers) ++ relics.flatMap(_.powers) ++
      edifices.flatMap(e => e.intact.powers ++ e.ruined.powers) ++
      legacies.flatMap(_.powers)).map(p => p.id -> p))

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

object CatalogSelection:
  val MetadataOnly: CatalogSelection = CatalogSelection()
  val SetupFoundation: CatalogSelection =
    CatalogSelection(setupCards = true, supplyBoards = true)

final case class CatalogLoadRequest(
    selection: CatalogSelection = CatalogSelection.MetadataOnly,
    expectedCatalog: Option[CatalogRef] = None
)

// Temporary source-compatible shells for consumers being migrated to
// rules-owned setup data. CatalogLoader never constructs these definitions.
final case class SetupCardDefinition(step: Int)
final case class SupplyBoardDefinition(rules: SupplyRules)
final case class VisionDefinition(id: VisionId)

sealed trait CatalogLoadError extends Product with Serializable:
  def path: String
  def message: String

object CatalogLoadError:
  final case class InvalidJson(detail: String) extends CatalogLoadError:
    override val path: String = "$"
    override val message: String = detail

  final case class FileReadFailed(pathValue: String, detail: String)
      extends CatalogLoadError:
    override val path: String = pathValue
    override val message: String = detail

  final case class MissingField(path: String) extends CatalogLoadError:
    override val message: String = "required field is missing"

  final case class WrongType(path: String, expected: String, actual: String)
      extends CatalogLoadError:
    override val message: String = s"expected $expected, found $actual"

  final case class InvalidValue(path: String, detail: String)
      extends CatalogLoadError:
    override val message: String = detail

  final case class UnsupportedSchemaVersion(
      path: String,
      expected: String,
      actual: String
  ) extends CatalogLoadError:
    override val message: String =
      s"expected schema $expected, found $actual"

  final case class IncompatibleCatalog(
      path: String,
      expected: CatalogRef,
      actual: CatalogRef
  ) extends CatalogLoadError:
    override val message: String =
      s"expected ${expected.ruleset}@${expected.version}, " +
        s"found ${actual.ruleset}@${actual.version}"

  final case class DuplicateDefinitionId(path: String, id: DefinitionId)
      extends CatalogLoadError:
    override val message: String = s"duplicate definition ID ${id.value}"

  final case class DuplicatePowerId(
      path: String,
      id: PowerId,
      firstPath: String
  ) extends CatalogLoadError:
    override val message: String =
      s"duplicate power ID ${id.value}; first declared at $firstPath"
