package oathdigital.persistence

import java.nio.file.Path
import java.sql.{Connection, SQLException}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.control.NonFatal

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.HsqldbProfile.api._

import oathdigital.application.{
  EventStreamRepository,
  ExpectedStream,
  RepositoryAppendResult,
  RepositoryFailure,
  StoredEventStream
}

object HsqldbEventStreamRepository {
  def open(
      databasePath: Path
  ): Either[RepositoryFailure, HsqldbEventStreamRepository] =
    validatePath(databasePath).flatMap(openValidated)

  private def validatePath(
      databasePath: Path
  ): Either[RepositoryFailure, Path] = {
    val normalized = databasePath.toAbsolutePath.normalize
    val value = normalized.toString
    if (value.exists(character =>
      character == ';' || character == '\n' ||
        character == '\r' || character == '\u0000'))
      Left(RepositoryFailure.InvalidConfiguration(
        "database path contains an unsafe HSQLDB URL delimiter"
      ))
    else Right(normalized)
  }

  private def openValidated(
      normalized: Path
  ): Either[RepositoryFailure, HsqldbEventStreamRepository] = {
    val config = new HikariConfig()
    config.setJdbcUrl(s"jdbc:hsqldb:file:$normalized")
    config.setDriverClassName("org.hsqldb.jdbc.JDBCDriver")
    config.setUsername("SA")
    config.setPassword("")
    config.setMaximumPoolSize(4)
    config.setMinimumIdle(1)
    config.setPoolName("oathdigital-event-journal")
    config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10))

    var dataSource: HikariDataSource = null
    try {
      dataSource = new HikariDataSource(config)
      val database = Database.forDataSource(dataSource, Some(4))
      val repository =
        new HsqldbEventStreamRepository(database, dataSource)
      repository.initializeSchema() match {
        case Right(_) => Right(repository)
        case Left(failure) =>
          repository.close()
          Left(failure)
      }
    } catch {
      case NonFatal(error) =>
        if (dataSource != null) dataSource.close()
        Left(storageFailure("open database", error))
    }
  }

  private[persistence] def storageFailure(
      operation: String,
      error: Throwable
  ): RepositoryFailure.StorageFailure =
    RepositoryFailure.StorageFailure(
      s"$operation failed: ${Option(error.getMessage).getOrElse(
        error.getClass.getSimpleName
      )}"
    )
}

/**
 * File-backed HSQLDB journal for opaque versioned event-envelope JSON.
 *
 * All public operations are synchronous because the storage-neutral
 * application boundary is synchronous. Slick still owns JDBC execution and
 * transaction scheduling underneath this adapter.
 */
final class HsqldbEventStreamRepository private (
    database: Database,
    dataSource: HikariDataSource
) extends EventStreamRepository
    with AutoCloseable {
  import HsqldbEventStreamRepository._
  import RepositoryAppendResult._

  private val schema = new EventJournalSchema
  private val closed = new AtomicBoolean(false)

  def initializeSchema(): Either[RepositoryFailure, Unit] =
    run("initialize schema")(schema.initialize)

  def schemaVersion: Either[RepositoryFailure, Int] =
    run("read schema version")(schema.currentVersion)

  override def load(
      gameId: String
  ): Either[RepositoryFailure, Option[StoredEventStream]] =
    run("load event stream")(
      SimpleDBIO { context =>
        val stream = context.connection.prepareStatement(
          "SELECT game_id FROM event_streams WHERE game_id = ?"
        )
        try {
          stream.setString(1, gameId)
          val streamRows = stream.executeQuery()
          if (!streamRows.next()) None
          else {
            val storedGameId = streamRows.getString(1)
            val entries = context.connection.prepareStatement(
              """SELECT envelope_json
                |FROM event_entries
                |WHERE game_id = ?
                |ORDER BY sequence ASC""".stripMargin
            )
            try {
              entries.setString(1, storedGameId)
              val rows = entries.executeQuery()
              val records = Vector.newBuilder[String]
              while (rows.next()) records += rows.getString(1)
              Some(StoredEventStream(storedGameId, records.result()))
            } finally entries.close()
          }
        } finally stream.close()
      }
    )

  override def append(
      gameId: String,
      expected: ExpectedStream,
      records: Vector[String]
  ): Either[RepositoryFailure, RepositoryAppendResult] =
    run("append event stream")(
      SimpleDBIO { context =>
        appendTransaction(context.connection, gameId, expected, records)
      }.transactionally
    )

  private def appendTransaction(
      connection: Connection,
      gameId: String,
      expected: ExpectedStream,
      records: Vector[String]
  ): RepositoryAppendResult = {
    val actual = lockNextSequence(connection, gameId)
    expected match {
      case ExpectedStream.MustNotExist =>
        actual match {
          case Some(_) => StreamAlreadyExists
          case None =>
            try {
              insertStream(connection, gameId)
            } catch {
              case error: SQLException if isConstraintViolation(error) =>
                return StreamAlreadyExists
            }
            insertEntries(connection, gameId, 0L, records)
            updateNextSequence(connection, gameId, records.size.toLong)
            Appended(0L, records.size)
        }
      case ExpectedStream.AtNextSequence(wanted) =>
        actual match {
          case None => StreamNotFound
          case Some(current) if current != wanted =>
            SequenceConflict(wanted, current)
          case Some(current) =>
            insertEntries(connection, gameId, current, records)
            updateNextSequence(
              connection,
              gameId,
              current + records.size.toLong
            )
            Appended(current, records.size)
        }
    }
  }

  private def lockNextSequence(
      connection: Connection,
      gameId: String
  ): Option[Long] = {
    val statement = connection.prepareStatement(
      """SELECT next_sequence
        |FROM event_streams
        |WHERE game_id = ?
        |FOR UPDATE""".stripMargin
    )
    try {
      statement.setString(1, gameId)
      val rows = statement.executeQuery()
      if (rows.next()) Some(rows.getLong(1)) else None
    } finally statement.close()
  }

  private def insertStream(connection: Connection, gameId: String): Unit = {
    val statement = connection.prepareStatement(
      "INSERT INTO event_streams (game_id, next_sequence) VALUES (?, 0)"
    )
    try {
      statement.setString(1, gameId)
      statement.executeUpdate()
      ()
    } finally statement.close()
  }

  private def insertEntries(
      connection: Connection,
      gameId: String,
      firstSequence: Long,
      records: Vector[String]
  ): Unit = {
    val statement = connection.prepareStatement(
      """INSERT INTO event_entries (game_id, sequence, envelope_json)
        |VALUES (?, ?, ?)""".stripMargin
    )
    try {
      records.zipWithIndex.foreach { case (record, offset) =>
        statement.setString(1, gameId)
        statement.setLong(2, firstSequence + offset)
        statement.setString(3, record)
        statement.addBatch()
      }
      if (records.nonEmpty) statement.executeBatch()
      ()
    } finally statement.close()
  }

  private def updateNextSequence(
      connection: Connection,
      gameId: String,
      nextSequence: Long
  ): Unit = {
    val statement = connection.prepareStatement(
      "UPDATE event_streams SET next_sequence = ? WHERE game_id = ?"
    )
    try {
      statement.setLong(1, nextSequence)
      statement.setString(2, gameId)
      if (statement.executeUpdate() != 1)
        throw new SQLException(s"stream '$gameId' disappeared during append")
      ()
    } finally statement.close()
  }

  private def isConstraintViolation(error: SQLException): Boolean =
    Option(error.getSQLState).exists(_.startsWith("23"))

  private def run[A](
      operation: String
  )(action: slick.dbio.DBIO[A]): Either[RepositoryFailure, A] =
    try Right(Await.result(database.run(action), Duration.Inf))
    catch {
      case NonFatal(error) => Left(storageFailure(operation, error))
    }

  override def close(): Unit = {
    if (closed.compareAndSet(false, true)) {
      try Await.result(database.run(
        SimpleDBIO[Unit] { context =>
          val statement = context.connection.createStatement()
          try {
            statement.execute("SHUTDOWN")
            ()
          } finally statement.close()
        }
      ), Duration.Inf)
      catch {
        case NonFatal(_) => ()
      } finally {
        database.close()
        dataSource.close()
      }
    }
  }
}
