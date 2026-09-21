package oathdigital.persistence

import java.sql.{Connection, SQLException}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.HsqldbProfile.api._
import oathdigital.application.{SeatCodeDigest, TrustedGameStore, TrustedGameStoreFailure}

private object HsqldbTrustedGameStore {
  final case class ExpectedFailure(failure: TrustedGameStoreFailure)
      extends RuntimeException(null, null, false, false)
}

final class HsqldbTrustedGameStore private[persistence] (database: Database)
    extends TrustedGameStore {
  import TrustedGameStoreFailure._
  import HsqldbTrustedGameStore.ExpectedFailure

  override def create(gameId: String, seats: Vector[(SeatCodeDigest, String)],
      preparedRecords: Vector[String], nowMillis: Long)
      : Either[TrustedGameStoreFailure, Unit] = {
    if (seats.isEmpty || preparedRecords.isEmpty ||
        seats.exists { case (_, player) => player == null || player.trim.isEmpty } ||
        seats.map(_._2).distinct.size != seats.size)
      return Left(InvalidInput)
    if (seats.map(_._1).distinct.size != seats.size) return Left(CodeCollision)

    val transaction = SimpleDBIO[Unit] { context =>
      val connection = context.connection
      classifyUnique(DuplicateGame) {
        execute(connection, "INSERT INTO game_resources (game_id, created_at_millis) VALUES (?, ?)") { s =>
          s.setString(1, gameId); s.setLong(2, nowMillis)
        }
      }
      seats.foreach { case (digest, player) => classifyUnique(CodeCollision) {
        execute(connection, "INSERT INTO trusted_seats (token_digest, game_id, player_id, created_at_millis) VALUES (?, ?, ?, ?)") { s =>
          s.setBytes(1, digest.bytes.toArray); s.setString(2, gameId)
          s.setString(3, player); s.setLong(4, nowMillis)
        }
      } }
      classifyUnique(DuplicateGame) {
        execute(connection, "INSERT INTO event_streams (game_id, next_sequence) VALUES (?, ?)") { s =>
          s.setString(1, gameId); s.setLong(2, preparedRecords.size.toLong)
        }
      }
      preparedRecords.zipWithIndex.foreach { case (record, sequence) =>
        execute(connection, "INSERT INTO event_entries (game_id, sequence, envelope_json) VALUES (?, ?, ?)") { s =>
          s.setString(1, gameId); s.setLong(2, sequence.toLong); s.setString(3, record)
        }
      }
    }.transactionally
    try { Await.result(database.run(transaction), Duration.Inf); Right(()) }
    catch {
      case ExpectedFailure(failure) => Left(failure)
      case NonFatal(_) => Left(StorageFailure)
    }
  }

  private def execute(connection: Connection, sql: String)(
      bind: java.sql.PreparedStatement => Unit): Unit = {
    val statement = connection.prepareStatement(sql)
    try { bind(statement); statement.executeUpdate(); () }
    finally statement.close()
  }

  // Throw inside the transaction so expected conflicts also roll back every row.
  private def classifyUnique(failure: TrustedGameStoreFailure)(operation: => Unit): Unit =
    try operation
    catch {
      case error: SQLException if error.getSQLState == "23505" =>
        throw ExpectedFailure(failure)
    }
}
