package oathdigital.persistence

import java.sql.Connection

import slick.dbio.DBIO
import slick.jdbc.HsqldbProfile.api._

private[persistence] final class EventJournalSchema {
  val TargetVersion: Int = 1

  val initialize: DBIO[Unit] =
    SimpleDBIO[Unit] { context =>
      val connection = context.connection
      createVersionLedger(connection)
      val current = readVersion(connection)
      migrations
        .filter(_._1 > current)
        .sortBy(_._1)
        .foreach { case (version, migrate) =>
          migrate(connection)
          recordVersion(connection, version)
        }
    }.transactionally

  val currentVersion: DBIO[Int] =
    SimpleDBIO[Int](context => readVersion(context.connection))

  private val migrations: Vector[(Int, Connection => Unit)] =
    Vector(1 -> createEventJournal _)

  private def createVersionLedger(connection: Connection): Unit = {
    val statement = connection.createStatement()
    try statement.execute(
      """CREATE TABLE IF NOT EXISTS schema_versions (
        |  version INTEGER PRIMARY KEY,
        |  applied_at_epoch_millis BIGINT NOT NULL
        |)""".stripMargin
    )
    finally statement.close()
  }

  private def readVersion(connection: Connection): Int = {
    val statement = connection.createStatement()
    try {
      val rows = statement.executeQuery(
        "SELECT COALESCE(MAX(version), 0) FROM schema_versions"
      )
      rows.next()
      rows.getInt(1)
    } finally statement.close()
  }

  private def recordVersion(connection: Connection, version: Int): Unit = {
    val statement = connection.prepareStatement(
      """INSERT INTO schema_versions (version, applied_at_epoch_millis)
        |VALUES (?, ?)""".stripMargin
    )
    try {
      statement.setInt(1, version)
      statement.setLong(2, System.currentTimeMillis())
      statement.executeUpdate()
      ()
    } finally statement.close()
  }

  private def createEventJournal(connection: Connection): Unit = {
    val statement = connection.createStatement()
    try {
      statement.execute(
        """CREATE TABLE event_streams (
          |  game_id VARCHAR(255) PRIMARY KEY,
          |  next_sequence BIGINT NOT NULL,
          |  CONSTRAINT event_streams_non_negative
          |    CHECK (next_sequence >= 0)
          |)""".stripMargin
      )
      statement.execute(
        """CREATE TABLE event_entries (
          |  game_id VARCHAR(255) NOT NULL,
          |  sequence BIGINT NOT NULL,
          |  envelope_json CLOB NOT NULL,
          |  CONSTRAINT event_entries_non_negative CHECK (sequence >= 0),
          |  CONSTRAINT event_entries_pk PRIMARY KEY (game_id, sequence),
          |  CONSTRAINT event_entries_stream_fk FOREIGN KEY (game_id)
          |    REFERENCES event_streams(game_id) ON DELETE CASCADE
          |)""".stripMargin
      )
    } finally statement.close()
  }
}
