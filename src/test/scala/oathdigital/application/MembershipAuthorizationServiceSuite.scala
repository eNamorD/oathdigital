package oathdigital.application

import java.nio.file.Files

import oathdigital.application.AuthorizationFailure._
import oathdigital.application.ProjectionScope._
import oathdigital.model.{PlayerId, RelicId}
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.testkit.IdentityRepositoryStub
import oathdigital.testkit.Unwrap.{accepted, rejected}

class MembershipAuthorizationServiceSuite extends munit.FunSuite:
  private val ownerUser = UserId("owner-user")
  private val playerUser = UserId("player-user")
  private val spectatorUser = UserId("spectator-user")
  private val outsider = UserId("outsider-user")

  test("membership roles produce owner player spectator and nonmember access"):
    withGameOneMembers { service =>
      assertEquals(
        service.resolve("game-1", AuthenticatedUser(ownerUser)),
        Right(GameAccessContext.Owner("game-1", ownerUser))
      )
      assertEquals(
        service.resolve("game-1", AuthenticatedUser(playerUser)),
        Right(GameAccessContext.Player(
          "game-1",
          playerUser,
          PlayerId("p1")
        ))
      )
      assertEquals(
        service.resolve("game-1", AuthenticatedUser(spectatorUser)),
        Right(GameAccessContext.Spectator("game-1", spectatorUser))
      )
      assertEquals(
        service.resolve("game-1", AuthenticatedUser(outsider)),
        Left(NotMember("game-1", outsider))
      )
      assertEquals(
        service.resolve("game-2", AuthenticatedUser(playerUser)),
        Left(NotMember("game-2", playerUser))
      )
    }

  test("bootstrap projection and command policies derive authority server-side"):
    withGameOneMembers { service =>
      assert(service.authorizeBootstrap(
        "game-1", AuthenticatedUser(ownerUser)).isRight)
      assertEquals(
        service.authorizeBootstrap("game-1", AuthenticatedUser(playerUser)),
        Left(Forbidden("bootstrap"))
      )

      assertEquals(
        accepted(service.authorizeProjection("game-1",
          AuthenticatedUser(ownerUser))).scope,
        PublicOnly
      )
      assertEquals(
        accepted(service.authorizeProjection("game-1",
          AuthenticatedUser(spectatorUser))).scope,
        PublicOnly
      )
      assertEquals(
        accepted(service.authorizeProjection("game-1",
          AuthenticatedUser(playerUser))).scope,
        PlayerPrivate(PlayerId("p1"))
      )

      val actor = accepted(service.authorizeCommand(
        "game-1", AuthenticatedUser(playerUser)))
      assertEquals(
        actor.beginRest,
        GameCommand.BeginRest(PlayerId("p1"))
      )
      assertEquals(
        actor.revealOwnedRelic(RelicId("r1")),
        GameCommand.RevealOwnedRelic(PlayerId("p1"), RelicId("r1"))
      )
      assertEquals(
        service.authorizeCommand("game-1", AuthenticatedUser(ownerUser)),
        Left(Forbidden("command"))
      )
      assertEquals(
        service.authorizeCommand("game-1", AuthenticatedUser(spectatorUser)),
        Left(Forbidden("command"))
      )
    }

  test("identity storage failures remain distinct from nonmembership"):
    /** Stub of IdentityRepository: findMembership returns StorageFailure. */
    val failing = new IdentityRepositoryStub:
      override def findMembership(gameId: String, userId: UserId): Either[IdentityFailure, Option[GameMembership]] =
        Left(IdentityFailure.StorageFailure("database unavailable"))
    assertEquals(
      new MembershipAuthorizationService(failing)
        .resolve("game", AuthenticatedUser(playerUser)),
      Left(StorageFailure("database unavailable"))
    )

  List(MembershipRole.Owner, MembershipRole.Spectator).foreach: role =>
    test(s"a $role membership that occupies a player seat is corrupt"):
      /** Stub of IdentityRepository: findMembership returns a membership
        * of `role` that holds seat p1. */
      val seated = new IdentityRepositoryStub:
        override def findMembership(gameId: String, userId: UserId): Either[IdentityFailure, Option[GameMembership]] =
          Right(Some(GameMembership(gameId, userId, role, Some("p1"))))
      assertEquals(
        rejected(new MembershipAuthorizationService(seated)
          .resolve("game", AuthenticatedUser(UserId("seated-user")))),
        CorruptMembership("non-player membership occupies a player seat")
      )

  /** A fresh identity database with four users: ownerUser owns game-1,
    * where playerUser sits as p1 and spectatorUser watches; outsider owns
    * game-2 and belongs to game-1 in no way. */
  private def withGameOneMembers(
      test: MembershipAuthorizationService => Unit
  ): Unit =
    val path = Files.createTempDirectory("oathdigital-authorization-")
      .resolve("database")
    val database = accepted(HsqldbDatabaseOwner.open(path))
    try
      val identities = database.identities
      Vector(ownerUser, playerUser, spectatorUser, outsider)
        .foreach(user => identities.createUser(user, user.value, 0L))
      identities.createGame("game-1", ownerUser, 1L)
      identities.createGame("game-2", outsider, 1L)
      identities.addMembership(
        GameMembership(
          "game-1",
          playerUser,
          MembershipRole.Player,
          Some("p1")
        ),
        2L
      )
      identities.addMembership(
        GameMembership(
          "game-1",
          spectatorUser,
          MembershipRole.Spectator,
          None
        ),
        2L
      )
      test(new MembershipAuthorizationService(identities))
    finally database.close()
