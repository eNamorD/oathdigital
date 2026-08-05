package oathdigital.persistence

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import com.zaxxer.hikari.HikariDataSource
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.HsqldbProfile.api._

import oathdigital.application.RepositoryFailure
import oathdigital.application._

/** Owns the sole datasource, schema lifecycle, and HSQLDB shutdown. */
final class HsqldbDatabaseOwner private (
    database: Database,
    source: HikariDataSource
) extends AutoCloseable {
  private val schema = new EventJournalSchema
  private val closed = new AtomicBoolean(false)
  private val shutdowns = new AtomicInteger(0)

  val eventStreams: HsqldbEventStreamRepository =
    new HsqldbEventStreamRepository(database)
  val identities: HsqldbIdentityRepository =
    new HsqldbIdentityRepository(database)

  def initializeSchema(): Either[RepositoryFailure, Unit] =
    run("initialize schema")(schema.initialize)

  def schemaVersion: Either[RepositoryFailure, Int] =
    run("read schema version")(schema.currentVersion)

  private[persistence] def shutdownCount: Int = shutdowns.get()

  private def run[A](operation: String)(action: DBIO[A]) =
    try Right(Await.result(database.run(action), Duration.Inf))
    catch {
      case NonFatal(error) =>
        Left(HsqldbEventStreamRepository.storageFailure(operation, error))
    }

  override def close(): Unit =
    if (closed.compareAndSet(false, true)) {
      try Await.result(database.run(SimpleDBIO { context =>
        val statement = context.connection.createStatement()
        try {
          statement.execute("SHUTDOWN")
          shutdowns.incrementAndGet()
          ()
        } finally statement.close()
      }), Duration.Inf)
      catch { case NonFatal(_) => () }
      finally {
        database.close()
        source.close()
      }
    }
}

object HsqldbDatabaseOwner {
  private val ReopenAttempts = 2
  private val ReopenBackoffMillis = 50L
  private val ReopenDeadlineNanos = TimeUnit.SECONDS.toNanos(25L)

  private[persistence] sealed trait OpenAttemptFailure
  private[persistence] final case class ConnectionFailure(error: Throwable)
      extends OpenAttemptFailure
  private[persistence] final case class InitializationFailure(
      error: RepositoryFailure
  ) extends OpenAttemptFailure

  def open(path: Path): Either[RepositoryFailure, HsqldbDatabaseOwner] =
    validatePath(path).flatMap { validated =>
      val deadline = System.nanoTime() + ReopenDeadlineNanos
      retryTransientLock(
        () => openAttempt(validated),
        ReopenAttempts,
        deadline,
        System.nanoTime _,
        millis => Thread.sleep(millis),
        isTransientLockHeartbeat
      ).left.map {
        case ConnectionFailure(error) =>
          HsqldbEventStreamRepository.storageFailure("open database", error)
        case InitializationFailure(error) => error
      }
    }

  private def validatePath(path: Path): Either[RepositoryFailure, Path] = {
    val normalized = path.toAbsolutePath.normalize
    if (normalized.toString.exists(character =>
      character == ';' || character == '\n' || character == '\r' ||
        character == '\u0000'))
      Left(RepositoryFailure.InvalidConfiguration(
        "database path contains an unsafe HSQLDB URL delimiter"
      ))
    else Right(normalized)
  }

  private def openAttempt(
      path: Path
  ): Either[OpenAttemptFailure, HsqldbDatabaseOwner] = {
    val source = new HikariDataSource()
    try {
      source.setJdbcUrl(s"jdbc:hsqldb:file:$path")
      source.setDriverClassName("org.hsqldb.jdbc.JDBCDriver")
      source.setUsername("SA")
      source.setPassword("")
      source.setMaximumPoolSize(4)
      source.setMinimumIdle(1)
      source.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10))
      source.setPoolName("oathdigital-database")
      val probe = source.getConnection
      probe.close()
      val owner = new HsqldbDatabaseOwner(
        Database.forDataSource(source, Some(4)),
        source
      )
      owner.initializeSchema() match {
        case Right(_) => Right(owner)
        case Left(error) =>
          owner.close()
          Left(InitializationFailure(error))
      }
    } catch {
      case NonFatal(error) =>
        try source.close()
        catch { case NonFatal(_) => () }
        Left(ConnectionFailure(error))
    }
  }

  private[persistence] def retryTransientLock[A](
      attempt: () => Either[OpenAttemptFailure, A],
      maxAttempts: Int,
      deadlineNanos: Long,
      nanoTime: () => Long,
      sleep: Long => Unit,
      retryable: Throwable => Boolean
  ): Either[OpenAttemptFailure, A] = {
    var attempts = 0
    var result: Either[OpenAttemptFailure, A] = null
    do {
      attempts += 1
      result = attempt()
      result match {
        case Left(ConnectionFailure(error))
            if attempts < maxAttempts && nanoTime() < deadlineNanos &&
              retryable(error) =>
          sleep(ReopenBackoffMillis)
        case _ => return result
      }
    } while (attempts < maxAttempts && nanoTime() < deadlineNanos)
    result
  }

  private[persistence] def isTransientLockHeartbeat(error: Throwable): Boolean = {
    val chain = Iterator.iterate(Option(error))(_.flatMap(value =>
      Option(value.getCause))).takeWhile(_.nonEmpty).flatten.toVector
    isTransientLockHeartbeatChain(chain.map(value =>
      value.getClass.getName -> Option(value.getMessage).getOrElse("")))
  }

  private[persistence] def isTransientLockHeartbeatChain(
      chain: Vector[(String, String)]
  ): Boolean =
    chain.exists(_._1 ==
      "org.hsqldb.persist.LockFile$LockHeldExternallyException") &&
      chain.exists { case (_, message) =>
        message.contains("lockFile:") && message.contains("checkHeartbeat")
      }
}

/** Explicit standalone owner used by focused journal tests and tools. */
final class OwnedHsqldbEventStreamRepository private (
    val owner: HsqldbDatabaseOwner
) extends EventStreamRepository with AutoCloseable {
  private val adapter = owner.eventStreams
  override def load(gameId: String) = adapter.load(gameId)
  override def append(gameId: String, expected: ExpectedStream,
      records: Vector[String]) = adapter.append(gameId, expected, records)
  def initializeSchema() = owner.initializeSchema()
  def schemaVersion = owner.schemaVersion
  override def close(): Unit = owner.close()
}

object OwnedHsqldbEventStreamRepository {
  def open(path: Path): Either[RepositoryFailure, OwnedHsqldbEventStreamRepository] =
    HsqldbDatabaseOwner.open(path).map(new OwnedHsqldbEventStreamRepository(_))
}

/** Explicit standalone owner used by focused identity tests and tools. */
final class OwnedHsqldbIdentityRepository private (
    val owner: HsqldbDatabaseOwner
) extends IdentityRepository with AutoCloseable {
  private val adapter = owner.identities
  override def createUser(id: UserId, name: String, now: Long) =
    adapter.createUser(id, name, now)
  override def linkExternalIdentity(identity: ExternalIdentity, userId: UserId) =
    adapter.linkExternalIdentity(identity, userId)
  override def findUser(identity: ExternalIdentity) = adapter.findUser(identity)
  override def createGame(gameId: String, ownerId: UserId, now: Long) =
    adapter.createGame(gameId, ownerId, now)
  override def addMembership(membership: GameMembership, now: Long) =
    adapter.addMembership(membership, now)
  override def findMembership(gameId: String, userId: UserId) =
    adapter.findMembership(gameId, userId)
  override def listMemberships(gameId: String) =
    adapter.listMemberships(gameId)
  override def createSession(session: StoredSession) =
    adapter.createSession(session)
  override def resolveSession(digest: SessionTokenDigest, now: Long) =
    adapter.resolveSession(digest, now)
  override def revokeSession(digest: SessionTokenDigest, now: Long) =
    adapter.revokeSession(digest, now)
  override def touchSession(digest: SessionTokenDigest, seen: Long, idle: Long) =
    adapter.touchSession(digest, seen, idle)
  private[persistence] def createGameWithBeforeOwnerMembership(
      gameId: String,
      ownerId: UserId,
      now: Long
  )(before: java.sql.Connection => Either[IdentityFailure, Unit]) =
    adapter.createGameWithBeforeOwnerMembership(gameId, ownerId, now)(before)
  private[persistence] def sessionColumnNames = adapter.sessionColumnNames
  def initializeSchema(): Either[IdentityFailure, Unit] =
    owner.initializeSchema().left.map(failure =>
      IdentityFailure.StorageFailure(failure.toString))
  def schemaVersion: Either[IdentityFailure, Int] =
    owner.schemaVersion.left.map(failure =>
      IdentityFailure.StorageFailure(failure.toString))
  override def close(): Unit = owner.close()
}

object OwnedHsqldbIdentityRepository {
  def open(path: Path): Either[IdentityFailure, OwnedHsqldbIdentityRepository] =
    HsqldbDatabaseOwner.open(path)
      .left.map(failure => IdentityFailure.StorageFailure(failure.toString))
      .map(new OwnedHsqldbIdentityRepository(_))
}
