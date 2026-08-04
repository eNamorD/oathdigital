package oathdigital.persistence

import java.sql.{Connection, SQLException}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.control.NoStackTrace

import slick.jdbc.JdbcBackend.Database
import slick.jdbc.HsqldbProfile.api._

import oathdigital.application._

object HsqldbIdentityRepository {
  private final case class ExpectedFailureControl(failure: IdentityFailure)
      extends RuntimeException with NoStackTrace

  private def storage(operation: String, error: Throwable) =
    IdentityFailure.StorageFailure(
      s"$operation failed: ${Option(error.getMessage).getOrElse(
        error.getClass.getSimpleName)}"
    )
}

final class HsqldbIdentityRepository private[persistence] (
    database: Database
) extends IdentityRepository {
  import IdentityFailure._
  import MembershipRole._
  import HsqldbIdentityRepository.ExpectedFailureControl

  override def createUser(
      userId: UserId,
      displayName: String,
      nowMillis: Long
  ): Either[IdentityFailure, Unit] =
    runExpected("create user") { connection =>
      if (exists(connection, "users", "user_id", userId.value))
        Left(DuplicateUser(userId))
      else {
        val statement = connection.prepareStatement(
          "INSERT INTO users (user_id, display_name, created_at_millis) VALUES (?, ?, ?)"
        )
        try {
          statement.setString(1, userId.value)
          statement.setString(2, displayName)
          statement.setLong(3, nowMillis)
          try {
            statement.executeUpdate()
            Right(())
          } catch {
            case error: SQLException if constraintViolation(error) =>
              Left(DuplicateUser(userId))
          }
        } finally statement.close()
      }
    }

  override def linkExternalIdentity(
      identity: ExternalIdentity,
      userId: UserId
  ): Either[IdentityFailure, Unit] =
    runExpected("link external identity") { connection =>
      if (!exists(connection, "users", "user_id", userId.value))
        Left(UserNotFound(userId))
      else if (externalExists(connection, identity))
        Left(DuplicateExternalIdentity(identity))
      else {
        val statement = connection.prepareStatement(
          "INSERT INTO external_identities (provider, subject, user_id) VALUES (?, ?, ?)"
        )
        try {
          statement.setString(1, identity.provider)
          statement.setString(2, identity.subject)
          statement.setString(3, userId.value)
          try {
            statement.executeUpdate()
            Right(())
          } catch {
            case error: SQLException if constraintViolation(error) =>
              Left(DuplicateExternalIdentity(identity))
          }
        } finally statement.close()
      }
    }

  override def findUser(
      identity: ExternalIdentity
  ): Either[IdentityFailure, Option[UserId]] =
    runExpected("find external identity") { connection =>
      val statement = connection.prepareStatement(
        "SELECT user_id FROM external_identities WHERE provider = ? AND subject = ?"
      )
      try {
        statement.setString(1, identity.provider)
        statement.setString(2, identity.subject)
        val row = statement.executeQuery()
        Right(if (row.next()) Some(UserId(row.getString(1))) else None)
      } finally statement.close()
    }

  override def createGame(
      gameId: String,
      owner: UserId,
      nowMillis: Long
  ): Either[IdentityFailure, Unit] =
    createGameWithBeforeOwnerMembership(gameId, owner, nowMillis)(
      _ => Right(())
    )

  private[persistence] def createGameWithBeforeOwnerMembership(
      gameId: String,
      owner: UserId,
      nowMillis: Long
  )(beforeOwnerMembership: Connection => Either[IdentityFailure, Unit])
      : Either[IdentityFailure, Unit] =
    runExpected("create game resource") { connection =>
      if (!exists(connection, "users", "user_id", owner.value))
        Left(UserNotFound(owner))
      else if (exists(connection, "game_resources", "game_id", gameId))
        Left(DuplicateGame(gameId))
      else {
        try {
          insertGame(connection, gameId, nowMillis)
          beforeOwnerMembership(connection).map { _ =>
            insertMembership(
              connection,
              GameMembership(gameId, owner, Owner, None),
              nowMillis
            )
          }
        } catch {
          case error: SQLException if constraintViolation(error) =>
            Left(DuplicateGame(gameId))
        }
      }
    }

  override def addMembership(
      membership: GameMembership,
      nowMillis: Long
  ): Either[IdentityFailure, Unit] =
    validateMembership(membership).flatMap { _ =>
      runExpected("add game membership") { connection =>
        if (!exists(connection, "game_resources", "game_id", membership.gameId))
          Left(GameNotFound(membership.gameId))
        else if (!exists(connection, "users", "user_id", membership.userId.value))
          Left(UserNotFound(membership.userId))
        else if (membershipExists(connection, membership.gameId, membership.userId))
          Left(DuplicateMembership(membership.gameId, membership.userId))
        else membership.playerId match {
          case Some(playerId) if seatExists(connection, membership.gameId, playerId) =>
            Left(PlayerSeatOccupied(membership.gameId, playerId))
          case _ =>
            try {
              insertMembership(connection, membership, nowMillis)
              Right(())
            } catch {
              case error: SQLException if constraintViolation(error) =>
                if (membershipExists(
                  connection,
                  membership.gameId,
                  membership.userId
                )) Left(DuplicateMembership(
                  membership.gameId,
                  membership.userId
                ))
                else membership.playerId match {
                  case Some(playerId) =>
                    Left(PlayerSeatOccupied(membership.gameId, playerId))
                  case None =>
                    Left(StorageFailure("membership constraint rejected"))
                }
            }
        }
      }
    }

  override def findMembership(
      gameId: String,
      userId: UserId
  ): Either[IdentityFailure, Option[GameMembership]] =
    runExpected("find game membership") { connection =>
      val statement = connection.prepareStatement(
        """SELECT membership_role, player_id FROM game_memberships
          |WHERE game_id = ? AND user_id = ?""".stripMargin
      )
      try {
        statement.setString(1, gameId)
        statement.setString(2, userId.value)
        val row = statement.executeQuery()
        if (!row.next()) Right(None)
        else {
          val playerId = Option(row.getString(2))
          Right(Some(GameMembership(
            gameId,
            userId,
            parseRole(row.getString(1)),
            playerId
          )))
        }
      } finally statement.close()
    }

  override def createSession(
      session: StoredSession
  ): Either[IdentityFailure, Unit] =
    validateSession(session).flatMap { _ =>
      runExpected("create session") { connection =>
        if (!exists(connection, "users", "user_id", session.userId.value))
          Left(UserNotFound(session.userId))
        else if (sessionExists(connection, session.digest))
          Left(DuplicateSession)
        else {
          val statement = connection.prepareStatement(
            """INSERT INTO sessions (
              |token_digest, user_id, created_at_millis, last_seen_at_millis,
              |idle_expires_at_millis, absolute_expires_at_millis, revoked_at_millis)
              |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
          )
          try {
            statement.setBytes(1, session.digest.bytes.toArray)
            statement.setString(2, session.userId.value)
            statement.setLong(3, session.createdAtMillis)
            statement.setLong(4, session.lastSeenAtMillis)
            statement.setLong(5, session.idleExpiresAtMillis)
            statement.setLong(6, session.absoluteExpiresAtMillis)
            session.revokedAtMillis.fold(statement.setNull(7, java.sql.Types.BIGINT))(
              statement.setLong(7, _))
            try {
              statement.executeUpdate()
              Right(())
            } catch {
              case error: SQLException if constraintViolation(error) =>
                Left(DuplicateSession)
            }
          } finally statement.close()
        }
      }
    }

  override def resolveSession(
      digest: SessionTokenDigest,
      nowMillis: Long
  ): Either[IdentityFailure, StoredSession] =
    runExpected("resolve session") { connection =>
      selectSession(connection, digest) match {
        case None => Left(SessionNotFound)
        case Some(session) if session.revokedAtMillis.nonEmpty =>
          Left(SessionRevoked)
        case Some(session)
            if nowMillis >= session.idleExpiresAtMillis ||
              nowMillis >= session.absoluteExpiresAtMillis =>
          Left(SessionExpired)
        case Some(session) => Right(session)
      }
    }

  override def revokeSession(
      digest: SessionTokenDigest,
      revokedAtMillis: Long
  ): Either[IdentityFailure, Unit] =
    updateSession("revoke session", digest,
      "UPDATE sessions SET revoked_at_millis = ? WHERE token_digest = ?") {
      statement => statement.setLong(1, revokedAtMillis)
    }

  override def touchSession(
      digest: SessionTokenDigest,
      lastSeenAtMillis: Long,
      idleExpiresAtMillis: Long
  ): Either[IdentityFailure, Unit] =
    if (idleExpiresAtMillis < lastSeenAtMillis)
      Left(InvalidSession("idle expiry must not precede last seen time"))
    else runExpected("touch session") { connection =>
      selectSession(connection, digest) match {
        case None => Left(SessionNotFound)
        case Some(session) if session.revokedAtMillis.nonEmpty =>
          Left(SessionRevoked)
        case Some(session) if lastSeenAtMillis < session.lastSeenAtMillis =>
          Left(InvalidSession("last seen time cannot move backwards"))
        case Some(session)
            if lastSeenAtMillis >= session.idleExpiresAtMillis ||
              lastSeenAtMillis >= session.absoluteExpiresAtMillis =>
          Left(SessionExpired)
        case Some(session)
            if idleExpiresAtMillis > session.absoluteExpiresAtMillis =>
          Left(InvalidSession("idle expiry exceeds absolute expiry"))
        case Some(_) =>
          val statement = connection.prepareStatement(
            """UPDATE sessions SET last_seen_at_millis = ?,
              |idle_expires_at_millis = ? WHERE token_digest = ?""".stripMargin
          )
          try {
            statement.setLong(1, lastSeenAtMillis)
            statement.setLong(2, idleExpiresAtMillis)
            statement.setBytes(3, digest.bytes.toArray)
            statement.executeUpdate()
            Right(())
          } finally statement.close()
      }
    }

  private def updateSession(
      operation: String,
      digest: SessionTokenDigest,
      sql: String
  )(setValues: java.sql.PreparedStatement => Unit)
      : Either[IdentityFailure, Unit] =
    runExpected(operation) { connection =>
      val statement = connection.prepareStatement(sql)
      try {
        setValues(statement)
        val digestIndex = statement.getParameterMetaData.getParameterCount
        statement.setBytes(digestIndex, digest.bytes.toArray)
        if (statement.executeUpdate() == 0) Left(SessionNotFound)
        else Right(())
      } finally statement.close()
    }

  private def validateMembership(membership: GameMembership) =
    membership.role match {
      case Player if membership.playerId.exists(_.trim.nonEmpty) => Right(())
      case Player => Left(InvalidMembership("player membership requires playerId"))
      case _ if membership.playerId.nonEmpty =>
        Left(InvalidMembership("owner and spectator memberships cannot occupy a player seat"))
      case _ => Right(())
    }

  private def validateSession(session: StoredSession) =
    if (session.createdAtMillis > session.lastSeenAtMillis)
      Left(InvalidSession("last seen time precedes creation"))
    else if (session.lastSeenAtMillis > session.idleExpiresAtMillis)
      Left(InvalidSession("idle expiry precedes last seen time"))
    else if (session.idleExpiresAtMillis > session.absoluteExpiresAtMillis)
      Left(InvalidSession("idle expiry exceeds absolute expiry"))
    else if (session.createdAtMillis > session.absoluteExpiresAtMillis)
      Left(InvalidSession("absolute expiry precedes creation"))
    else if (session.revokedAtMillis.exists(_ < session.createdAtMillis))
      Left(InvalidSession("revocation precedes creation"))
    else Right(())

  private def insertGame(connection: Connection, gameId: String, now: Long): Unit = {
    val statement = connection.prepareStatement(
      "INSERT INTO game_resources (game_id, created_at_millis) VALUES (?, ?)"
    )
    try {
      statement.setString(1, gameId)
      statement.setLong(2, now)
      statement.executeUpdate()
    } finally statement.close()
  }

  private def insertMembership(
      connection: Connection,
      membership: GameMembership,
      now: Long
  ): Unit = {
    val statement = connection.prepareStatement(
      """INSERT INTO game_memberships
        |(game_id, user_id, membership_role, player_id, created_at_millis)
        |VALUES (?, ?, ?, ?, ?)""".stripMargin
    )
    try {
      statement.setString(1, membership.gameId)
      statement.setString(2, membership.userId.value)
      statement.setString(3, roleName(membership.role))
      membership.playerId.fold(statement.setNull(4, java.sql.Types.VARCHAR))(
        statement.setString(4, _))
      statement.setLong(5, now)
      statement.executeUpdate()
    } finally statement.close()
  }

  private def selectSession(
      connection: Connection,
      digest: SessionTokenDigest
  ): Option[StoredSession] = {
    val statement = connection.prepareStatement(
      """SELECT user_id, created_at_millis, last_seen_at_millis,
        |idle_expires_at_millis, absolute_expires_at_millis, revoked_at_millis
        |FROM sessions WHERE token_digest = ?""".stripMargin
    )
    try {
      statement.setBytes(1, digest.bytes.toArray)
      val row = statement.executeQuery()
      if (!row.next()) None
      else {
        val revokedValue = row.getLong(6)
        val revoked = if (row.wasNull()) None else Some(revokedValue)
        Some(StoredSession(
          digest,
          UserId(row.getString(1)),
          row.getLong(2),
          row.getLong(3),
          row.getLong(4),
          row.getLong(5),
          revoked
        ))
      }
    } finally statement.close()
  }

  private def exists(
      connection: Connection,
      table: String,
      column: String,
      value: String
  ): Boolean = {
    val statement = connection.prepareStatement(
      s"SELECT 1 FROM $table WHERE $column = ?"
    )
    try {
      statement.setString(1, value)
      statement.executeQuery().next()
    } finally statement.close()
  }

  private def externalExists(connection: Connection, identity: ExternalIdentity) = {
    val statement = connection.prepareStatement(
      "SELECT 1 FROM external_identities WHERE provider = ? AND subject = ?"
    )
    try {
      statement.setString(1, identity.provider)
      statement.setString(2, identity.subject)
      statement.executeQuery().next()
    } finally statement.close()
  }

  private def membershipExists(connection: Connection, gameId: String, userId: UserId) =
    pairExists(connection, "game_memberships", "game_id", gameId, "user_id", userId.value)

  private def seatExists(connection: Connection, gameId: String, playerId: String) =
    pairExists(connection, "game_memberships", "game_id", gameId, "player_id", playerId)

  private def pairExists(connection: Connection, table: String, firstColumn: String,
      first: String, secondColumn: String, second: String): Boolean = {
    val statement = connection.prepareStatement(
      s"SELECT 1 FROM $table WHERE $firstColumn = ? AND $secondColumn = ?"
    )
    try {
      statement.setString(1, first)
      statement.setString(2, second)
      statement.executeQuery().next()
    } finally statement.close()
  }

  private def sessionExists(connection: Connection, digest: SessionTokenDigest) = {
    val statement = connection.prepareStatement(
      "SELECT 1 FROM sessions WHERE token_digest = ?"
    )
    try {
      statement.setBytes(1, digest.bytes.toArray)
      statement.executeQuery().next()
    } finally statement.close()
  }

  private def roleName(role: MembershipRole): String = role match {
    case Owner => "owner"
    case Player => "player"
    case Spectator => "spectator"
  }

  private def constraintViolation(error: SQLException): Boolean =
    Option(error.getSQLState).exists(_.startsWith("23"))

  private def parseRole(value: String): MembershipRole = value match {
    case "owner" => Owner
    case "player" => Player
    case "spectator" => Spectator
    case other => throw new IllegalStateException(s"unknown membership role '$other'")
  }

  private def runExpected[A](operation: String)(
      action: Connection => Either[IdentityFailure, A]
  ): Either[IdentityFailure, A] = {
    val transactional = SimpleDBIO[A] { context =>
      action(context.connection) match {
        case Right(value) => value
        case Left(failure) => throw ExpectedFailureControl(failure)
      }
    }.transactionally
    try Right(Await.result(database.run(transactional), Duration.Inf))
    catch {
      case ExpectedFailureControl(failure) => Left(failure)
      case NonFatal(error) =>
        Left(HsqldbIdentityRepository.storage(operation, error))
    }
  }

}
