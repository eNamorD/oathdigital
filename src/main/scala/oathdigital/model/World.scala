package oathdigital.model

sealed trait Role extends Product with Serializable {
  def isImperial: Boolean
}
object Role {
  case object Exile extends Role {
    override val isImperial: Boolean = false
  }
  case object Citizen extends Role {
    override val isImperial: Boolean = true
  }
  case object Chancellor extends Role {
    override val isImperial: Boolean = true
  }
}

sealed trait ForceKind extends Product with Serializable
object ForceKind {
  final case class Exile(lineage: LineageId) extends ForceKind
  case object Imperial extends ForceKind
  case object Bandit extends ForceKind
}

sealed trait SiteForces extends Product with Serializable
object SiteForces {
  case object Empty extends SiteForces

  final case class Occupied(kind: ForceKind, count: Int) extends SiteForces {
    require(count > 0, "occupied site forces must be positive")
  }
}

final case class SiteState(
    forces: SiteForces,
    denizens: Vector[SiteDenizenState],
    relics: Vector[RelicState],
    tokens: Tokens
)

final case class MapState(
    cradle: Vector[SiteId],
    provinces: Vector[SiteId],
    hinterland: Vector[SiteId],
    sites: Map[SiteId, SiteState]
) {
  def inPlay: Vector[SiteId] = cradle ++ provinces ++ hinterland

  def regionOf(site: SiteId): Option[Region] =
    if (cradle.contains(site)) Some(Region.Cradle)
    else if (provinces.contains(site)) Some(Region.Provinces)
    else if (hinterland.contains(site)) Some(Region.Hinterland)
    else None

  def hinterward: Vector[SiteId] = inPlay
  def cradleward: Vector[SiteId] = inPlay.reverse
}

sealed trait AtlasEntry extends Product with Serializable
object AtlasEntry {
  final case class StoredSite(
      id: SiteId,
      denizens: Vector[SiteDenizenState],
      relics: Vector[RelicState]
  ) extends AtlasEntry

  case object EmpireDivider extends AtlasEntry
}

/**
 * One ordered Atlas sequence. `entries.head` is the Recent end and
 * `entries.last` is the Forgotten end.
 */
final case class AtlasState(entries: Vector[AtlasEntry]) {
  def addRecent(entry: AtlasEntry): AtlasState =
    copy(entries = entry +: entries)

  def addForgotten(entry: AtlasEntry): AtlasState =
    copy(entries = entries :+ entry)

  def mostRecent: Option[AtlasEntry] = entries.headOption
  def mostForgotten: Option[AtlasEntry] = entries.lastOption
}

sealed trait PeoplesFavorFace extends Product with Serializable
object PeoplesFavorFace {
  case object Mob extends PeoplesFavorFace
  case object GrandCouncil extends PeoplesFavorFace
}

sealed trait DarkestSecretFace extends Product with Serializable
object DarkestSecretFace {
  case object WanderingFlame extends DarkestSecretFace
  case object Festival extends DarkestSecretFace
}

final case class PeoplesFavorState(
    active: PeoplesFavorFace,
    holder: Option[PlayerId],
    favor: Int
) {
  require(favor >= 0, "People's Favor resources must be non-negative")
}

final case class DarkestSecretState(
    active: DarkestSecretFace,
    holder: Option[PlayerId],
    secrets: Int
) {
  require(secrets >= 0, "Darkest Secret resources must be non-negative")
}

final case class BannersState(
    peoplesFavor: PeoplesFavorState,
    darkestSecret: DarkestSecretState
)

sealed trait FoundationNumber extends Product with Serializable {
  def value: Int
}
object FoundationNumber {
  case object I extends FoundationNumber { override val value: Int = 1 }
  case object II extends FoundationNumber { override val value: Int = 2 }
  case object III extends FoundationNumber { override val value: Int = 3 }
  case object IV extends FoundationNumber { override val value: Int = 4 }
  case object V extends FoundationNumber { override val value: Int = 5 }
  case object VI extends FoundationNumber { override val value: Int = 6 }

  val all: Vector[FoundationNumber] = Vector(I, II, III, IV, V, VI)
}

sealed trait FoundationFace extends Product with Serializable
object FoundationFace {
  case object Normal extends FoundationFace
  case object Altered extends FoundationFace
}

final case class FoundationState(
    face: FoundationFace,
    alterationSources: Set[LegacyId]
)

sealed trait OathkeeperGoal extends Product with Serializable
object OathkeeperGoal {
  case object Supremacy extends OathkeeperGoal
  case object Protection extends OathkeeperGoal
  case object ThePeople extends OathkeeperGoal
  case object Devotion extends OathkeeperGoal
}

sealed trait TitleSide extends Product with Serializable
object TitleSide {
  case object Oathkeeper extends TitleSide
  case object Usurper extends TitleSide
}

final case class OathkeeperState(
    holder: Option[PlayerId],
    side: TitleSide
)
