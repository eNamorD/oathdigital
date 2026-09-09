package oathdigital.application

import java.nio.file.Files

import oathdigital.application.AuthorizationFailure._
import oathdigital.application.ProjectionScope._
import oathdigital.model.{DenizenId, PlayerId, SiteId}
import oathdigital.persistence.HsqldbDatabaseOwner

class MembershipAuthorizationServiceSuite extends munit.FunSuite {
  private val ownerUser = UserId("owner-user")
  private val playerUser = UserId("player-user")
  private val spectatorUser = UserId("spectator-user")
  private val outsider = UserId("outsider-user")

  test("pluggable authenticator returns a provider-neutral principal") {
    val authenticator = new Authenticator[String] {
      override def authenticate(credential: String) =
        if (credential == "test-credential")
          Right(AuthenticatedUser(playerUser))
        else Left(AuthenticationFailure.InvalidCredential("invalid"))
    }

    assertEquals(
      authenticator.authenticate("test-credential"),
      Right(AuthenticatedUser(playerUser): AuthenticatedPrincipal)
    )
  }

  test("membership roles produce owner player spectator and nonmember access") {
    withService { service =>
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
  }

  test("bootstrap projection and command policies derive authority server-side") {
    withService { service =>
      assert(service.authorizeBootstrap(
        "game-1", AuthenticatedUser(ownerUser)).isRight)
      assertEquals(
        service.authorizeBootstrap("game-1", AuthenticatedUser(playerUser)),
        Left(Forbidden("bootstrap"))
      )

      assertEquals(
        service.authorizeProjection("game-1", AuthenticatedUser(ownerUser))
          .toOption.get.scope,
        PublicOnly
      )
      assertEquals(
        service.authorizeProjection("game-1", AuthenticatedUser(spectatorUser))
          .toOption.get.scope,
        PublicOnly
      )
      assertEquals(
        service.authorizeProjection("game-1", AuthenticatedUser(playerUser))
          .toOption.get.scope,
        PlayerPrivate(PlayerId("p1"))
      )

      val actor = service.authorizeCommand(
        "game-1", AuthenticatedUser(playerUser)).toOption.get
      assertEquals(
        actor.placePawn(SiteId("site-1")),
        GameCommand.PlacePawn(PlayerId("p1"), SiteId("site-1"))
      )
      assertEquals(
        actor.chooseAdviser(DenizenId("9")),
        GameCommand.ChooseAdviser(PlayerId("p1"), DenizenId("9"))
      )
      assertEquals(
        actor.travel(SiteId("site-2")),
        GameCommand.Travel(PlayerId("p1"), SiteId("site-2"))
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
  }

  test("identity storage failures remain distinct from nonmembership") {
    val failing = new IdentityRepositoryStub {
      override def findMembership(gameId: String, userId: UserId) =
        Left(IdentityFailure.StorageFailure("database unavailable"))
    }
    assertEquals(
      new MembershipAuthorizationService(failing)
        .resolve("game", AuthenticatedUser(playerUser)),
      Left(StorageFailure("database unavailable"))
    )
  }

  private def withService(
      test: MembershipAuthorizationService => Unit
  ): Unit = {
    val path = Files.createTempDirectory("oathdigital-authorization-")
      .resolve("database")
    val database = HsqldbDatabaseOwner.open(path).toOption.get
    try {
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
    } finally database.close()
  }

  private abstract class IdentityRepositoryStub extends IdentityRepository {
    private def unused[A]: Either[IdentityFailure, A] =
      fail("unexpected identity repository operation")
    override def createUser(id: UserId, name: String, now: Long) = unused
    override def linkExternalIdentity(identity: ExternalIdentity, id: UserId) =
      unused
    override def findUser(identity: ExternalIdentity) = unused
    override def createGame(gameId: String, owner: UserId, now: Long) = unused
    override def addMembership(membership: GameMembership, now: Long) = unused
    override def listMemberships(gameId: String) = unused
    override def createSession(session: StoredSession) = unused
    override def resolveSession(digest: SessionTokenDigest, now: Long) = unused
    override def revokeSession(digest: SessionTokenDigest, now: Long) = unused
    override def touchSession(digest: SessionTokenDigest, seen: Long, idle: Long) =
      unused
    override def createTrustedSeats(
        gameId: String,
        seats: Vector[(SeatCodeDigest, String)],
        now: Long
    ) = unused
    override def resolveTrustedSeat(digest: SeatCodeDigest) = unused
  }
}
