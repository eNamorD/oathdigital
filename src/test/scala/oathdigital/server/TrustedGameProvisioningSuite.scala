package oathdigital.server

import java.nio.file.{Files, Path}
import java.sql.{Connection, DriverManager}
import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, plan}
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.protocol._

class TrustedGameProvisioningSuite extends munit.FunSuite {
  private val request = TrustedGameCreateRequest("trusted-game", Vector(
    BootstrapParticipantRequest("p1", "l1", "red"),
    BootstrapParticipantRequest("p2", "l2", "blue")))
  private val codes = Vector("AAAAAAAAAAAAAAAAAAAAAA", "AQEBAQEBAQEBAQEBAQEBAQ",
    "AgICAgICAgICAgICAgICAg", "AwMDAwMDAwMDAwMDAwMDAw").map(SeatCode.parse(_).toOption.get)

  private def withDatabase(body: (HsqldbDatabaseOwner, Connection) => Unit): Unit = {
    val path: Path = Files.createTempDirectory("trusted-provisioning-").resolve("games")
    val owner = HsqldbDatabaseOwner.open(path).toOption.get
    val connection = DriverManager.getConnection(s"jdbc:hsqldb:file:$path", "SA", "")
    try body(owner, connection)
    finally { connection.close(); owner.close() }
  }

  private def provision(owner: HsqldbDatabaseOwner, generate: () => SeatCode) =
    new TrustedGameProvisioning(new GameApplicationService(catalog, owner.eventStreams),
      new DevelopmentFirstGamePlanFactory(catalog), owner.trustedGames, generate, () => 1234L)

  private def rows(connection: Connection, gameId: String): Vector[Int] =
    Vector("game_resources", "trusted_seats", "event_streams", "event_entries").map { table =>
      val statement = connection.prepareStatement(s"SELECT COUNT(*) FROM $table WHERE game_id = ?")
      try {
        statement.setString(1, gameId)
        val result = statement.executeQuery()
        result.next()
        result.getInt(1)
      } finally statement.close()
    }

  test("creates ordered links, digest-only seats and replayable journal in one commit") {
    withDatabase { (owner, connection) =>
      val generated = codes.iterator
      val response = provision(owner, () => generated.next()).create(request, "https://games.example.test")
      assertEquals(response, Right(TrustedGameCreateResponse("trusted-game", Vector(
        TrustedSeatLink("p1", "https://games.example.test/s/AAAAAAAAAAAAAAAAAAAAAA"),
        TrustedSeatLink("p2", "https://games.example.test/s/AQEBAQEBAQEBAQEBAQEBAQ")))))
      assertEquals(rows(connection, request.gameId), Vector(1, 2, 1, 1))
      codes.take(2).zipWithIndex.foreach { case (code, index) =>
        assertEquals(owner.identities.resolveTrustedSeat(code.digest),
          Right(TrustedSeat(request.gameId, s"p${index + 1}")))
      }
      val statement = connection.createStatement()
      try {
        val result = statement.executeQuery("SELECT token_digest, created_at_millis FROM trusted_seats ORDER BY player_id")
        var index = 0
        while (result.next()) {
          assertEquals(result.getBytes(1).toVector, codes(index).digest.bytes)
          assertEquals(result.getLong(2), 1234L)
          index += 1
        }
      } finally statement.close()
      val loaded = new GameApplicationService(catalog, owner.eventStreams).load(request.gameId)
      assertEquals(loaded.toOption.flatten.map(_.nextSequence), Some(1L))
      val journal = owner.eventStreams.load(request.gameId).toOption.flatten.get.records.mkString
      codes.foreach(code => assert(!journal.contains(code.raw)))
    }
  }

  test("duplicate game and persistent digest collision return generic failures without partial rows") {
    withDatabase { (owner, connection) =>
      val generated = codes.iterator
      assert(provision(owner, () => generated.next()).create(request, "http://localhost:8080").isRight)
      val before = rows(connection, request.gameId)
      val duplicates = codes.drop(2).iterator
      assertEquals(provision(owner, () => duplicates.next()).create(request, "http://localhost:8080"),
        Left(TrustedGameFailure.DuplicateGame))
      assertEquals(rows(connection, request.gameId), before)
      val collisionCodes = Vector(codes(2), codes.head).iterator
      val collision = provision(owner, () => collisionCodes.next()).create(
        request.copy(gameId = "collision"), "http://localhost:8080")
      assertEquals(collision, Left(TrustedGameFailure.CodeCollision))
      assertEquals(rows(connection, "collision"), Vector(0, 0, 0, 0))
      codes.foreach(code => assert(!collision.toString.contains(code.raw)))
    }
  }

  test("duplicate generated codes retry per seat with an eight-attempt terminal limit") {
    withDatabase { (owner, connection) =>
      val generated = Vector(codes.head, codes.head, codes(1)).iterator
      assert(provision(owner, () => generated.next()).create(request, "https://games.test").isRight)
      var count = 0
      val failed = provision(owner, () => { count += 1; codes(2) }).create(
        request.copy(gameId = "exhausted"), "https://games.test")
      assertEquals(failed, Left(TrustedGameFailure.CodeCollision))
      assertEquals(count, 9)
      assertEquals(rows(connection, "exhausted"), Vector(0, 0, 0, 0))
    }
  }

  test("event insertion failure rolls back game resource, seats and stream") {
    withDatabase { (owner, connection) =>
      val statement = connection.createStatement()
      try statement.execute("ALTER TABLE event_entries ADD CONSTRAINT fail_bootstrap CHECK (sequence > 0)")
      finally statement.close()
      val generated = codes.iterator
      val failed = provision(owner, () => generated.next()).create(request, "https://games.test")
      assertEquals(failed, Left(TrustedGameFailure.StorageFailure))
      assertEquals(rows(connection, request.gameId), Vector(0, 0, 0, 0))
      codes.foreach(code => assert(!failed.toString.contains(code.raw)))
    }
  }

  test("invalid bootstrap semantics and origins never persist anything or generate codes") {
    withDatabase { (owner, connection) =>
      val service = provision(owner, () => fail("must validate before code generation"))
      Vector(request.copy(gameId = "bad/game"), request.copy(participants = Vector.empty),
        request.copy(participants = request.participants.updated(1,
          request.participants(1).copy(color = "red"))),
        request.copy(participants = request.participants.updated(1,
          request.participants(1).copy(lineageId = "l1")))).foreach { invalid =>
        assertEquals(service.create(invalid, "https://games.test"), Left(TrustedGameFailure.InvalidRequest))
        assertEquals(rows(connection, invalid.gameId), Vector(0, 0, 0, 0))
      }
      Vector("https://user:secret@games.test", "https://games.test/path", "https://games.test?q=secret",
        "ftp://games.test", "https://games.test/#secret").foreach { origin =>
        assertEquals(service.create(request, origin), Left(TrustedGameFailure.InvalidRequest))
      }
      assertEquals(rows(connection, request.gameId), Vector(0, 0, 0, 0))
    }
  }

  test("code generator exceptions are generic and cannot leak credentials") {
    withDatabase { (owner, connection) =>
      val failed = provision(owner, () => throw new IllegalStateException(codes.head.raw))
        .create(request, "https://games.test")
      assertEquals(failed, Left(TrustedGameFailure.StorageFailure))
      assert(!failed.toString.contains(codes.head.raw))
      assertEquals(rows(connection, request.gameId), Vector(0, 0, 0, 0))
    }
  }

  test("bootstrap preparation performs no repository IO and matches ordinary handle") {
    val untouched = new EventStreamRepository {
      def load(id: String) = fail("preparation must not read storage")
      def append(id: String, expected: ExpectedStream, records: Vector[String]) =
        fail("preparation must not write storage")
    }
    val prepared = new GameApplicationService(catalog, untouched).prepareBootstrap("prepared", plan).toOption.get
    val journal = new InMemoryEventStreamRepository
    val accepted = new GameApplicationService(catalog, journal)
      .handle("prepared", 0L, GameCommand.Begin(plan)).toOption.get
    assertEquals(prepared.state, accepted.state)
    assertEquals(prepared.events, accepted.events)
    assertEquals(prepared.continue, accepted.continue)
    assertEquals(prepared.records, journal.load("prepared").toOption.flatten.get.records)
  }

  test("store rejects invalid seat and record input without throwing or persisting rows") {
    withDatabase { (owner, connection) =>
      assertEquals(owner.trustedGames.create("invalid", Vector(codes.head.digest -> null),
        Vector("record"), 0L), Left(TrustedGameStoreFailure.InvalidInput))
      assertEquals(owner.trustedGames.create("invalid", Vector(codes.head.digest -> "p1"),
        Vector.empty, 0L), Left(TrustedGameStoreFailure.InvalidInput))
      assertEquals(rows(connection, "invalid"), Vector(0, 0, 0, 0))
    }
  }

  test("store preserves record order and rolls back even after an earlier event row was inserted") {
    withDatabase { (owner, connection) =>
      assertEquals(owner.trustedGames.create("ordered", Vector(codes.head.digest -> "p1"),
        Vector("first", "second", "third"), 0L), Right(()))
      assertEquals(owner.eventStreams.load("ordered").toOption.flatten,
        Some(StoredEventStream("ordered", Vector("first", "second", "third"))))
      val statement = connection.createStatement()
      try statement.execute("ALTER TABLE event_entries ADD CONSTRAINT fail_second CHECK (game_id <> 'partial' OR sequence = 0)")
      finally statement.close()
      assertEquals(owner.trustedGames.create("partial", Vector(codes(1).digest -> "p1"),
        Vector("first", "second"), 0L), Left(TrustedGameStoreFailure.StorageFailure))
      assertEquals(rows(connection, "partial"), Vector(0, 0, 0, 0))
    }
  }
}
