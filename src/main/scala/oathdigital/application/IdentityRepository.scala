package oathdigital.application

final case class UserId(value: String)
final case class ExternalIdentity(provider: String, subject: String)

final case class SessionTokenDigest private (bytes: Vector[Byte])
object SessionTokenDigest {
  val Length: Int = 32

  def fromBytes(bytes: Vector[Byte]): Either[String, SessionTokenDigest] =
    if (bytes.size == Length) Right(new SessionTokenDigest(bytes))
    else Left(s"session token digest must contain exactly $Length bytes")
}

final case class CsrfTokenDigest private (bytes: Vector[Byte])
object CsrfTokenDigest {
  val Length: Int = 32

  def fromBytes(bytes: Vector[Byte]): Either[String, CsrfTokenDigest] =
    if (bytes.size == Length) Right(new CsrfTokenDigest(bytes))
    else Left(s"CSRF token digest must contain exactly $Length bytes")
}

final case class SeatCodeDigest private (bytes: Vector[Byte])
object SeatCodeDigest {
  val Length: Int = 32

  def fromBytes(bytes: Vector[Byte]): Either[String, SeatCodeDigest] =
    if (bytes.size == Length) Right(new SeatCodeDigest(bytes))
    else Left(s"seat code digest must contain exactly $Length bytes")
}

final case class TrustedSeat(gameId: String, playerId: String)

final case class StoredSession(
    digest: SessionTokenDigest,
    userId: UserId,
    createdAtMillis: Long,
    lastSeenAtMillis: Long,
    idleExpiresAtMillis: Long,
    absoluteExpiresAtMillis: Long,
    revokedAtMillis: Option[Long],
    csrfTokenDigest: Option[CsrfTokenDigest]
)

sealed trait MembershipRole extends Product with Serializable
object MembershipRole {
  case object Owner extends MembershipRole
  case object Player extends MembershipRole
  case object Spectator extends MembershipRole
}

final case class GameMembership(
    gameId: String,
    userId: UserId,
    role: MembershipRole,
    playerId: Option[String]
)

sealed trait IdentityFailure extends Product with Serializable
object IdentityFailure {
  final case class DuplicateUser(userId: UserId) extends IdentityFailure
  final case class UserNotFound(userId: UserId) extends IdentityFailure
  final case class DuplicateExternalIdentity(identity: ExternalIdentity)
      extends IdentityFailure
  final case class DuplicateGame(gameId: String) extends IdentityFailure
  final case class GameNotFound(gameId: String) extends IdentityFailure
  final case class DuplicateMembership(gameId: String, userId: UserId)
      extends IdentityFailure
  final case class PlayerSeatOccupied(gameId: String, playerId: String)
      extends IdentityFailure
  final case class InvalidMembership(message: String) extends IdentityFailure
  case object DuplicateSession extends IdentityFailure
  final case class InvalidSession(message: String) extends IdentityFailure
  case object SessionNotFound extends IdentityFailure
  case object SessionExpired extends IdentityFailure
  case object SessionRevoked extends IdentityFailure
  case object DuplicateTrustedSeat extends IdentityFailure
  case object TrustedSeatNotFound extends IdentityFailure
  final case class InvalidTrustedSeat(message: String) extends IdentityFailure
  final case class StorageFailure(message: String) extends IdentityFailure
}

trait IdentityRepository {
  def createUser(userId: UserId, displayName: String, nowMillis: Long)
      : Either[IdentityFailure, Unit]
  def linkExternalIdentity(
      identity: ExternalIdentity,
      userId: UserId
  ): Either[IdentityFailure, Unit]
  def findUser(identity: ExternalIdentity)
      : Either[IdentityFailure, Option[UserId]]
  def createGame(
      gameId: String,
      owner: UserId,
      nowMillis: Long
  ): Either[IdentityFailure, Unit]
  def addMembership(
      membership: GameMembership,
      nowMillis: Long
  ): Either[IdentityFailure, Unit]
  def findMembership(gameId: String, userId: UserId)
      : Either[IdentityFailure, Option[GameMembership]]
  /** Owner, players by player ID, then spectators; ties use user ID. */
  def listMemberships(gameId: String)
      : Either[IdentityFailure, Vector[GameMembership]]
  def createSession(session: StoredSession): Either[IdentityFailure, Unit]
  def resolveSession(
      digest: SessionTokenDigest,
      nowMillis: Long
  ): Either[IdentityFailure, StoredSession]
  def revokeSession(
      digest: SessionTokenDigest,
      revokedAtMillis: Long
  ): Either[IdentityFailure, Unit]
  def touchSession(
      digest: SessionTokenDigest,
      lastSeenAtMillis: Long,
      idleExpiresAtMillis: Long
  ): Either[IdentityFailure, Unit]
  def createTrustedSeats(
      gameId: String,
      seats: Vector[(SeatCodeDigest, String)],
      nowMillis: Long
  ): Either[IdentityFailure, Unit]
  def resolveTrustedSeat(
      digest: SeatCodeDigest
  ): Either[IdentityFailure, TrustedSeat]
}
