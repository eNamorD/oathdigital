package oathdigital.presentation

/** Stable, serialization-friendly identity for a presented game object. */
final case class ViewId(value: String) {
  require(value.trim.nonEmpty, "view ID must not be blank")
}

/** Text exposed visually and to assistive technology. */
final case class AccessibleLabel(value: String) {
  require(value.trim.nonEmpty, "accessible label must not be blank")
}

/**
 * A non-owning reference understood by a platform-specific image loader.
 *
 * Core game and presentation code never assumes that the reference resolves.
 */
final case class ImageRef(value: String) {
  require(value.trim.nonEmpty, "image reference must not be blank")
}

/**
 * Image-independent representation of an object.
 *
 * `symbol` should be short enough to display inside a token or card silhouette.
 * `text` is the deterministic visible substitute for an unavailable image.
 */
final case class FallbackVisual(symbol: String, text: String) {
  require(symbol.trim.nonEmpty, "fallback symbol must not be blank")
  require(text.trim.nonEmpty, "fallback text must not be blank")
}

sealed trait PresentedEntity extends Product with Serializable {
  def id: ViewId
  def label: AccessibleLabel
  def image: Option[ImageRef]
  def fallback: FallbackVisual
}

final case class CardView(
    id: ViewId,
    label: AccessibleLabel,
    image: Option[ImageRef],
    fallback: FallbackVisual,
    rulesText: String
) extends PresentedEntity

final case class SiteView(
    id: ViewId,
    label: AccessibleLabel,
    image: Option[ImageRef],
    fallback: FallbackVisual
) extends PresentedEntity

final case class PieceView(
    id: ViewId,
    label: AccessibleLabel,
    image: Option[ImageRef],
    fallback: FallbackVisual,
    siteId: Option[ViewId]
) extends PresentedEntity

final case class ActionView(
    id: ViewId,
    label: AccessibleLabel,
    image: Option[ImageRef],
    fallback: FallbackVisual,
    enabled: Boolean
) extends PresentedEntity

/** Complete, renderer-independent board snapshot. */
final case class BoardView(
    id: ViewId,
    label: AccessibleLabel,
    sites: Vector[SiteView],
    cards: Vector[CardView],
    pieces: Vector[PieceView],
    actions: Vector[ActionView]
) {
  /** Stable IDs must be unique across every entity in one snapshot. */
  def duplicateIds: Set[ViewId] = {
    val ids = (sites.iterator ++ cards.iterator ++ pieces.iterator ++ actions.iterator).map(_.id).toVector
    ids.groupBy(identity).collect { case (duplicateId, occurrences) if occurrences.size > 1 => duplicateId }.toSet
  }
}

/** Result reported by an image loader; renderers need not expose loader details. */
sealed trait ImageLoadResult extends Product with Serializable
object ImageLoadResult {
  case object NotRequested extends ImageLoadResult
  final case class Loaded(reference: ImageRef) extends ImageLoadResult
  final case class Failed(reference: ImageRef) extends ImageLoadResult
}

/** A deterministic instruction that any terminal, web, or native renderer can consume. */
sealed trait VisualInstruction extends Product with Serializable {
  def accessibleLabel: AccessibleLabel
}
object VisualInstruction {
  final case class Image(reference: ImageRef, accessibleLabel: AccessibleLabel) extends VisualInstruction
  final case class Placeholder(
      symbol: String,
      text: String,
      accessibleLabel: AccessibleLabel
  ) extends VisualInstruction
}

object VisualResolver {
  import ImageLoadResult._
  import VisualInstruction._

  /**
   * Uses an image only when the entity requests that exact reference and the
   * loader confirms it. Every absent, stale, or failed result uses the same
   * entity-owned placeholder.
   */
  def resolve(entity: PresentedEntity, loadResult: ImageLoadResult): VisualInstruction =
    (entity.image, loadResult) match {
      case (Some(expected), Loaded(actual)) if expected == actual =>
        Image(expected, entity.label)
      case _ =>
        Placeholder(entity.fallback.symbol, entity.fallback.text, entity.label)
    }
}
