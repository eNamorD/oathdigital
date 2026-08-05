package oathdigital.persistence

import java.sql.Connection

import slick.dbio.DBIO
import slick.jdbc.HsqldbProfile.api._

private[persistence] final class EventJournalSchema {
  val TargetVersion: Int = 3

  val initialize: DBIO[Unit] =
    SimpleDBIO[Unit] { context =>
      val connection = context.connection
      createVersionLedger(connection)
      val installed = readVersions(connection)
      validateInstalled(installed)
      val current = installed.lastOption.getOrElse(0)
      migrations
        .filter(_._1 > current)
        .sortBy(_._1)
        .foreach { case (version, migrate) =>
          migrate(connection)
          recordVersion(connection, version)
        }
      validateExactTarget(readVersions(connection))
    }.transactionally

  val currentVersion: DBIO[Int] =
    SimpleDBIO[Int](context =>
      readVersions(context.connection).lastOption.getOrElse(0))

  private val migrations: Vector[(Int, Connection => Unit)] =
    Vector(
      1 -> createEventJournal _,
      2 -> createIdentityFoundation _,
      3 -> addSessionCsrfDigest _
    )

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

  private def readVersions(connection: Connection): Vector[Int] = {
    val statement = connection.createStatement()
    try {
      val rows = statement.executeQuery(
        "SELECT version FROM schema_versions ORDER BY version ASC"
      )
      val versions = Vector.newBuilder[Int]
      while (rows.next()) versions += rows.getInt(1)
      versions.result()
    } finally statement.close()
  }

  private def validateInstalled(versions: Vector[Int]): Unit = {
    versions.lastOption.foreach { newest =>
      if (newest > TargetVersion)
        throw new IllegalStateException(
          s"schema version $newest is newer than supported version $TargetVersion"
        )
    }
    val expected = (1 to versions.size).toVector
    if (versions != expected)
      throw new IllegalStateException(
        s"schema version ledger must be contiguous from 1; found ${versions.mkString(",")}"
      )
  }

  private def validateExactTarget(versions: Vector[Int]): Unit = {
    val expected = (1 to TargetVersion).toVector
    if (versions != expected)
      throw new IllegalStateException(
        s"schema version ledger mismatch; expected ${expected.mkString(",")} " +
          s"but found ${versions.mkString(",")}"
      )
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

  private def createIdentityFoundation(connection: Connection): Unit = {
    val statement = connection.createStatement()
    try {
      statement.execute(
        """CREATE TABLE users (
          |  user_id VARCHAR(128) PRIMARY KEY,
          |  display_name VARCHAR(128) NOT NULL,
          |  created_at_millis BIGINT NOT NULL
          |)""".stripMargin
      )
      statement.execute(
        """CREATE TABLE external_identities (
          |  provider VARCHAR(128) NOT NULL,
          |  subject VARCHAR(255) NOT NULL,
          |  user_id VARCHAR(128) NOT NULL,
          |  CONSTRAINT external_identities_pk PRIMARY KEY (provider, subject),
          |  CONSTRAINT external_identities_user_fk FOREIGN KEY (user_id)
          |    REFERENCES users(user_id)
          |)""".stripMargin
      )
      statement.execute(
        """CREATE TABLE game_resources (
          |  game_id VARCHAR(255) PRIMARY KEY,
          |  created_at_millis BIGINT NOT NULL
          |)""".stripMargin
      )
      statement.execute(
        """CREATE TABLE game_memberships (
          |  game_id VARCHAR(255) NOT NULL,
          |  user_id VARCHAR(128) NOT NULL,
          |  membership_role VARCHAR(16) NOT NULL,
          |  player_id VARCHAR(128),
          |  created_at_millis BIGINT NOT NULL,
          |  CONSTRAINT game_memberships_pk PRIMARY KEY (game_id, user_id),
          |  CONSTRAINT game_memberships_seat_unique UNIQUE (game_id, player_id),
          |  CONSTRAINT game_memberships_game_fk FOREIGN KEY (game_id)
          |    REFERENCES game_resources(game_id) ON DELETE CASCADE,
          |  CONSTRAINT game_memberships_user_fk FOREIGN KEY (user_id)
          |    REFERENCES users(user_id),
          |  CONSTRAINT game_memberships_role CHECK (
          |    membership_role IN ('owner', 'player', 'spectator')
          |  ),
          |  CONSTRAINT game_memberships_role_seat CHECK (
          |    (membership_role = 'player' AND player_id IS NOT NULL) OR
          |    (membership_role <> 'player' AND player_id IS NULL)
          |  )
          |)""".stripMargin
      )
      statement.execute(
        """CREATE TABLE sessions (
          |  token_digest BINARY(32) PRIMARY KEY,
          |  user_id VARCHAR(128) NOT NULL,
          |  created_at_millis BIGINT NOT NULL,
          |  last_seen_at_millis BIGINT NOT NULL,
          |  idle_expires_at_millis BIGINT NOT NULL,
          |  absolute_expires_at_millis BIGINT NOT NULL,
          |  revoked_at_millis BIGINT,
          |  CONSTRAINT sessions_user_fk FOREIGN KEY (user_id)
          |    REFERENCES users(user_id),
          |  CONSTRAINT sessions_time_order CHECK (
          |    created_at_millis <= last_seen_at_millis AND
          |    last_seen_at_millis <= idle_expires_at_millis AND
          |    idle_expires_at_millis <= absolute_expires_at_millis AND
          |    created_at_millis <= absolute_expires_at_millis AND
          |    (revoked_at_millis IS NULL OR
          |      created_at_millis <= revoked_at_millis)
          |  )
          |)""".stripMargin
      )
    } finally statement.close()
  }

  private def addSessionCsrfDigest(connection: Connection): Unit = {
    val statement = connection.createStatement()
    try {
      statement.execute(
        "ALTER TABLE sessions ADD COLUMN csrf_token_digest BINARY(32)"
      )
      statement.execute(
        """UPDATE sessions SET revoked_at_millis = created_at_millis
          |WHERE revoked_at_millis IS NULL""".stripMargin
      )
    } finally statement.close()
  }
}
