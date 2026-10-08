package oathdigital.testkit

import oathdigital.application.*

/** Stub of IdentityRepository: every operation fails the test, so a test
  * overrides exactly the operations the code under test is expected to call
  * and any other call is reported. It has no rules of its own, so it has no
  * contract suite; the overrides a test writes answer values the real
  * repository can answer (`HsqldbIdentityRepositorySuite` holds the real one
  * to those answers). */
abstract class IdentityRepositoryStub extends IdentityRepository:
  private def unused[A]: Either[IdentityFailure, A] =
    munit.Assertions.fail("unexpected identity repository operation")

  override def createUser(id: UserId, name: String, now: Long)
      : Either[IdentityFailure, Unit] = unused
  override def linkExternalIdentity(identity: ExternalIdentity, id: UserId)
      : Either[IdentityFailure, Unit] = unused
  override def findUser(identity: ExternalIdentity)
      : Either[IdentityFailure, Option[UserId]] = unused
  override def createGame(gameId: String, owner: UserId, now: Long)
      : Either[IdentityFailure, Unit] = unused
  override def addMembership(membership: GameMembership, now: Long)
      : Either[IdentityFailure, Unit] = unused
  override def findMembership(gameId: String, userId: UserId)
      : Either[IdentityFailure, Option[GameMembership]] = unused
  override def listMemberships(gameId: String)
      : Either[IdentityFailure, Vector[GameMembership]] = unused
  override def createSession(session: StoredSession)
      : Either[IdentityFailure, Unit] = unused
  override def resolveSession(digest: SessionTokenDigest, now: Long)
      : Either[IdentityFailure, StoredSession] = unused
  override def revokeSession(digest: SessionTokenDigest, now: Long)
      : Either[IdentityFailure, Unit] = unused
  override def touchSession(digest: SessionTokenDigest, seen: Long, idle: Long)
      : Either[IdentityFailure, Unit] = unused
  override def resolveTrustedSeat(digest: SeatCodeDigest)
      : Either[IdentityFailure, TrustedSeat] = unused
