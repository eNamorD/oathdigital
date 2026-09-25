package oathdigital.model

enum Role(val isImperial: Boolean) {
  case Exile extends Role(false)
  case Citizen extends Role(true)
  case Chancellor extends Role(true)
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

sealed trait SiteRuler extends Product with Serializable
object SiteRuler {
  case object Unruled extends SiteRuler
  case object Bandits extends SiteRuler
  case object Empire extends SiteRuler
  final case class Player(playerId: PlayerId) extends SiteRuler
}

sealed trait SiteRuleError extends Product with Serializable
object SiteRuleError {
  final case class UnknownLineage(lineage: LineageId) extends SiteRuleError
  final case class DuplicateCurrentLineage(
      lineage: LineageId,
      players: Vector[PlayerId]
  ) extends SiteRuleError
}

/** Direct interpretation of the physical warbands stored at a site. */
object SiteRule {
  import SiteRuleError._
  import SiteRuler._

  def ruler(forces: SiteForces, players: Vector[PlayerState])
      : Either[SiteRuleError, SiteRuler] = forces match {
    case SiteForces.Empty => Right(Unruled)
    case SiteForces.Occupied(ForceKind.Bandit, _) => Right(Bandits)
    case SiteForces.Occupied(ForceKind.Imperial, _) => Right(Empire)
    case SiteForces.Occupied(ForceKind.Exile(lineage), _) =>
      players.filter(_.lineage == lineage).map(_.player) match {
        case Vector(player) => Right(Player(player))
        case Vector() => Left(UnknownLineage(lineage))
        case duplicates => Left(DuplicateCurrentLineage(lineage, duplicates))
      }
  }

  def ruledBy(forces: SiteForces, players: Vector[PlayerState], player: PlayerId)
      : Either[SiteRuleError, Boolean] =
    ruler(forces, players).map(_ == Player(player))

  def sameRuler(left: SiteForces, right: SiteForces,
      players: Vector[PlayerState]): Either[SiteRuleError, Boolean] =
    for {
      leftRuler <- ruler(left, players)
      rightRuler <- ruler(right, players)
    } yield leftRuler != Unruled && leftRuler == rightRuler

  def enemies(left: SiteRuler, right: SiteRuler): Boolean =
    left != Unruled && right != Unruled && left != right
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
      relics: Vector[RelicState],
      edifice: Option[EdificeId] = None
  ) extends AtlasEntry

  case object EmpireDivider extends AtlasEntry
}

/**
 * One ordered Atlas sequence. `entries.head` is the Recent end and
 * `entries.last` is the Forgotten end.
 */
final case class AtlasRemoval(
    removed: Vector[AtlasEntry],
    remaining: AtlasState
)

final case class AtlasState(entries: Vector[AtlasEntry]) {
  def addRecent(entry: AtlasEntry): AtlasState =
    copy(entries = entry +: entries)

  /** Prepends entries while preserving their supplied front-to-back order. */
  def addRecent(additions: Vector[AtlasEntry]): AtlasState =
    copy(entries = additions ++ entries)

  /**
   * Removes up to `numSites` stored sites from the Recent end.
   *
   * Any Empire divider encountered before the requested number of sites is
   * reached is removed and included in the result.
   */
  def removeRecent(numSites: Int): AtlasRemoval = {
    val (removed, remaining) = takeSites(entries, numSites)
    AtlasRemoval(removed, AtlasState(remaining))
  }

  /**
   * Removes up to `numSites` stored sites from the Forgotten end.
   *
   * Removed entries are returned in removal order, from back to front.
   * Any Empire divider encountered is included in the result.
   */
  def removeForgotten(numSites: Int): AtlasRemoval = {
    val (removedReversed, remainingReversed) =
      takeSites(entries.reverse, numSites)
    AtlasRemoval(
      removedReversed,
      AtlasState(remainingReversed.reverse)
    )
  }

  def mostRecent: Option[AtlasEntry] = entries.headOption
  def mostForgotten: Option[AtlasEntry] = entries.lastOption

  private def takeSites(
      source: Vector[AtlasEntry],
      numSites: Int
  ): (Vector[AtlasEntry], Vector[AtlasEntry]) = {
    require(numSites >= 0, "number of Atlas sites must be non-negative")

    var removedSites = 0
    var position = 0
    while (position < source.size && removedSites < numSites) {
      source(position) match {
        case _: AtlasEntry.StoredSite => removedSites += 1
        case AtlasEntry.EmpireDivider => ()
      }
      position += 1
    }
    source.splitAt(position)
  }
}

enum PeoplesFavorFace { case Mob, GrandCouncil }

enum DarkestSecretFace { case WanderingFlame, Festival }

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

enum Banner(val key: String) {
  case PeoplesFavor extends Banner("peoples-favor")
  case DarkestSecret extends Banner("darkest-secret")
}
object Banner {
  val all: Vector[Banner] = Vector(PeoplesFavor, DarkestSecret)
  def fromKey(key: String): Option[Banner] = all.find(_.key == key)
}

enum FoundationNumber(val value: Int) {
  case I extends FoundationNumber(1)
  case II extends FoundationNumber(2)
  case III extends FoundationNumber(3)
  case IV extends FoundationNumber(4)
  case V extends FoundationNumber(5)
  case VI extends FoundationNumber(6)
}
object FoundationNumber {
  val all: Vector[FoundationNumber] = Vector(I, II, III, IV, V, VI)
}

enum FoundationFace { case Normal, Altered }

final case class FoundationState(
    face: FoundationFace,
    alterationSources: Set[LegacyId]
)

enum OathkeeperGoal(val key: String) {
  case Supremacy extends OathkeeperGoal("supremacy")
  case Protection extends OathkeeperGoal("protection")
  case ThePeople extends OathkeeperGoal("the-people")
  case Devotion extends OathkeeperGoal("devotion")
}
object OathkeeperGoal {
  val all: Vector[OathkeeperGoal] =
    Vector(Supremacy, Protection, ThePeople, Devotion)
}

enum TitleSide { case Oathkeeper, Usurper }

final case class OathkeeperState(
    holder: Option[PlayerId],
    side: TitleSide
)
