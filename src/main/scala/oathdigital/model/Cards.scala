package oathdigital.model

enum Orientation { case FaceUp, FaceDown }

enum EdificeSide { case Intact, Ruined }

sealed trait CardState extends Product with Serializable:
  def id: CardId

sealed trait AdviserState extends CardState
sealed trait SiteDenizenState extends CardState:
  def tokens: Tokens

final case class DenizenState(
    id: DenizenId,
    orientation: Orientation,
    tokens: Tokens
) extends AdviserState
    with SiteDenizenState

final case class VisionState(
    id: VisionId,
    orientation: Orientation
) extends AdviserState

/**
 * Edifices are special denizens. They can occupy a site's denizen slots, but
 * keep their intact/ruined side instead of an ordinary faceup/facedown flag.
 */
final case class EdificeState(
    id: EdificeId,
    side: EdificeSide,
    tokens: Tokens
) extends SiteDenizenState

final case class RelicState(
    id: RelicId,
    orientation: Orientation,
    tokens: Tokens
) extends CardState

final case class LegacyState(id: LegacyId, active: Boolean) extends CardState

enum Region(val key: String):
  case Cradle extends Region("cradle")
  case Provinces extends Region("provinces")
  case Hinterland extends Region("hinterland")
object Region:
  val all: Vector[Region] = Vector(Cradle, Provinces, Hinterland)

final case class CardZones(
    worldDeck: Vector[WorldCardId],
    relicDeck: Vector[RelicId],
    edificeDeck: Vector[EdificeId],
    legacyDeck: Vector[LegacyId],
    regionalDiscards: Map[Region, Vector[WorldCardId]]
):
  def discard(region: Region): Vector[WorldCardId] =
    regionalDiscards.getOrElse(region, Vector.empty)

  /** The cards of a pile Search draws from, in stored order: the world deck
    * top first, a discard pile top last. */
  def pile(source: SearchSource): Vector[WorldCardId] = source match
    case SearchSource.WorldDeck => worldDeck
    case SearchSource.RegionalDiscard(region) => discard(region)

  /** These zones with `source` holding `cards`, in stored order. */
  def withPile(source: SearchSource, cards: Vector[WorldCardId]): CardZones =
    source match
      case SearchSource.WorldDeck => copy(worldDeck = cards)
      case SearchSource.RegionalDiscard(region) =>
        copy(regionalDiscards = regionalDiscards.updated(region, cards))

/** Stable, container-qualified target for a denizen printed at a site. */
final case class SiteDenizenTarget(siteId: SiteId, denizenId: DenizenId):
  def stableKey: String = s"site:${siteId.value}:denizen:${denizenId.value}"
