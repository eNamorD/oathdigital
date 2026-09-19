package oathdigital.model

sealed trait Orientation extends Product with Serializable
object Orientation {
  case object FaceUp extends Orientation
  case object FaceDown extends Orientation
}

sealed trait EdificeSide extends Product with Serializable
object EdificeSide {
  case object Intact extends EdificeSide
  case object Ruined extends EdificeSide
}

sealed trait CardState extends Product with Serializable {
  def id: CardId
}

sealed trait AdviserState extends CardState
sealed trait SiteDenizenState extends CardState {
  def tokens: Tokens
}

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

sealed trait Region extends Product with Serializable {
  def key: String
}
object Region {
  case object Cradle extends Region {
    override val key: String = "cradle"
  }
  case object Provinces extends Region {
    override val key: String = "provinces"
  }
  case object Hinterland extends Region {
    override val key: String = "hinterland"
  }

  val all: Vector[Region] = Vector(Cradle, Provinces, Hinterland)
}

final case class CardZones(
    worldDeck: Vector[WorldCardId],
    relicDeck: Vector[RelicId],
    edificeDeck: Vector[EdificeId],
    legacyDeck: Vector[LegacyId],
    regionalDiscards: Map[Region, Vector[WorldCardId]]
) {
  def discard(region: Region): Vector[WorldCardId] =
    regionalDiscards.getOrElse(region, Vector.empty)
}
