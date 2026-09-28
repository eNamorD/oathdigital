package oathdigital.server

import oathdigital.model.{PlayerColor, TriggeredProcedureRef}

import java.nio.file.{Files, Path}
import java.sql.{Connection, DriverManager}
import oathdigital.application._
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, chronicle,
  orders, participants}
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.protocol._

class TrustedGameProvisioningSuite extends munit.FunSuite:
  /** The parked decision, as this suite rebuilds it: the same catalog and
    * power catalogs `GameApplicationService` builds its rules with
    * (`GameApplicationService.scala:87-90`).
    */
  private val parkedAssertions = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  private val gameId = "trusted-game"
  private val request = TrustedGameCreateRequest(Vector(
    BootstrapParticipantRequest("p1", PlayerColor.Red),
    BootstrapParticipantRequest("p2", PlayerColor.Blue)))
  private val codes = Vector("AAAAAAAAAAAAAAAAAAAAAA", "AQEBAQEBAQEBAQEBAQEBAQ",
    "AgICAgICAgICAgICAgICAg", "AwMDAwMDAwMDAwMDAwMDAw").map(SeatCode.parse(_).toOption.get)

  private def withDatabase(body: (HsqldbDatabaseOwner, Connection) => Unit): Unit =
    val path: Path = Files.createTempDirectory("trusted-provisioning-").resolve("games")
    val owner = HsqldbDatabaseOwner.open(path).toOption.get
    val connection = DriverManager.getConnection(s"jdbc:hsqldb:file:$path", "SA", "")
    try body(owner, connection)
    finally { connection.close(); owner.close() }

  private def provision(owner: HsqldbDatabaseOwner, generate: () => SeatCode,
      gameIds: () => String = () => gameId) =
    new TrustedGameProvisioning(new GameApplicationService(catalog, owner.eventStreams),
      new GeneratedFirstGamePlanFactory(catalog), owner.trustedGames, generate, () => 1234L,
      gameIds)

  private def rows(connection: Connection, gameId: String): Vector[Int] =
    Vector("game_resources", "trusted_seats", "event_streams", "event_entries").map { table =>
      val statement = connection.prepareStatement(s"SELECT COUNT(*) FROM $table WHERE game_id = ?")
      try
        statement.setString(1, gameId)
        val result = statement.executeQuery()
        result.next()
        result.getInt(1)
      finally statement.close()
    }

  test("creates ordered links, digest-only seats and replayable journal in one commit"):
    withDatabase { (owner, connection) =>
      val generated = codes.iterator
      val response = provision(owner, () => generated.next()).create(request, "https://games.example.test")
      assertEquals(response, Right(TrustedGameCreateResponse("trusted-game", Vector(
        TrustedSeatLink("p1", "https://games.example.test/s/AAAAAAAAAAAAAAAAAAAAAA"),
        TrustedSeatLink("p2", "https://games.example.test/s/AQEBAQEBAQEBAQEBAQEBAQ")))))
      // GameStarted plus the WalkerParked fact from Setup's immediate first
      // park (2026-09-21 Chronicle design, slice 2): two event entries for
      // one bootstrap, not the legacy one.
      assertEquals(rows(connection, gameId), Vector(1, 2, 1, 2))
      codes.take(2).zipWithIndex.foreach { case (code, index) =>
        assertEquals(owner.identities.resolveTrustedSeat(code.digest),
          Right(TrustedSeat(gameId, s"p${index + 1}")))
      }
      val statement = connection.createStatement()
      try
        val result = statement.executeQuery("SELECT token_digest, created_at_millis FROM trusted_seats ORDER BY player_id")
        var index = 0
        while result.next() do
          assertEquals(result.getBytes(1).toVector, codes(index).digest.bytes)
          assertEquals(result.getLong(2), 1234L)
          index += 1
      finally statement.close()
      val loaded = new GameApplicationService(catalog, owner.eventStreams).load(gameId)
      assertEquals(loaded.toOption.flatten.map(_.nextSequence), Some(2L))
      val journal = owner.eventStreams.load(gameId).toOption.flatten.get.records.mkString
      codes.foreach(code => assert(!journal.contains(code.raw)))
    }

  test("a taken game ID is drawn again, keeping the seat codes"):
    withDatabase { (owner, connection) =>
      val first = codes.iterator
      assert(provision(owner, () => first.next()).create(request, "https://games.test").isRight)
      val ids = Iterator(gameId, "fresh")
      val second = codes.drop(2).iterator
      val response = provision(owner, () => second.next(), () => ids.next())
        .create(request, "https://games.test")
      assertEquals(response.map(_.gameId), Right("fresh"))
      assertEquals(response.map(_.seats.map(_.url)), Right(codes.drop(2).map(code =>
        s"https://games.test/s/${code.raw}")))
      assertEquals(rows(connection, "fresh"), Vector(1, 2, 1, 2))
    }

  test("generated game IDs are distinct and follow the identifier rule"):
    val ids = Vector.fill(50)(TrustedGameProvisioning.generateGameId())
    assert(ids.forall(_.matches("game-[a-z2-7]{12}")), ids)
    assertEquals(ids.distinct.size, ids.size)

  test("eight taken game IDs and persistent digest collision return generic failures without partial rows"):
    withDatabase { (owner, connection) =>
      val generated = codes.iterator
      assert(provision(owner, () => generated.next()).create(request, "http://localhost:8080").isRight)
      val before = rows(connection, gameId)
      val duplicates = codes.drop(2).iterator
      var draws = 0
      assertEquals(provision(owner, () => duplicates.next(), () => { draws += 1; gameId })
        .create(request, "http://localhost:8080"), Left(TrustedGameFailure.DuplicateGame))
      assertEquals(draws, TrustedGameProvisioning.MaxGameIdAttempts)
      assertEquals(rows(connection, gameId), before)
      val collisionCodes = Vector(codes(2), codes.head).iterator
      val collision = provision(owner, () => collisionCodes.next(), () => "collision").create(
        request, "http://localhost:8080")
      assertEquals(collision, Left(TrustedGameFailure.CodeCollision))
      assertEquals(rows(connection, "collision"), Vector(0, 0, 0, 0))
      codes.foreach(code => assert(!collision.toString.contains(code.raw)))
    }

  test("duplicate generated codes retry per seat with an eight-attempt terminal limit"):
    withDatabase { (owner, connection) =>
      val generated = Vector(codes.head, codes.head, codes(1)).iterator
      assert(provision(owner, () => generated.next()).create(request, "https://games.test").isRight)
      var count = 0
      val failed = provision(owner, () => { count += 1; codes(2) }, () => "exhausted").create(
        request, "https://games.test")
      assertEquals(failed, Left(TrustedGameFailure.CodeCollision))
      assertEquals(count, 9)
      assertEquals(rows(connection, "exhausted"), Vector(0, 0, 0, 0))
    }

  test("event insertion failure rolls back game resource, seats and stream"):
    withDatabase { (owner, connection) =>
      val statement = connection.createStatement()
      try statement.execute("ALTER TABLE event_entries ADD CONSTRAINT fail_bootstrap CHECK (sequence > 0)")
      finally statement.close()
      val generated = codes.iterator
      val failed = provision(owner, () => generated.next()).create(request, "https://games.test")
      assertEquals(failed, Left(TrustedGameFailure.StorageFailure))
      assertEquals(rows(connection, gameId), Vector(0, 0, 0, 0))
      codes.foreach(code => assert(!failed.toString.contains(code.raw)))
    }

  test("invalid bootstrap semantics and origins never persist anything or generate codes"):
    withDatabase { (owner, connection) =>
      val service = provision(owner, () => fail("must validate before code generation"))
      Vector(request.copy(participants = Vector.empty),
        request.copy(participants = request.participants.updated(1,
          request.participants(1).copy(color = PlayerColor.Red)))).foreach { invalid =>
        assertEquals(service.create(invalid, "https://games.test"), Left(TrustedGameFailure.InvalidRequest))
        assertEquals(rows(connection, gameId), Vector(0, 0, 0, 0))
      }
      Vector("https://user:secret@games.test", "https://games.test/path", "https://games.test?q=secret",
        "ftp://games.test", "https://games.test/#secret").foreach { origin =>
        assertEquals(service.create(request, origin), Left(TrustedGameFailure.InvalidRequest))
      }
      assertEquals(rows(connection, gameId), Vector(0, 0, 0, 0))
    }

  test("code generator exceptions are generic and cannot leak credentials"):
    withDatabase { (owner, connection) =>
      val failed = provision(owner, () => throw new IllegalStateException(codes.head.raw))
        .create(request, "https://games.test")
      assertEquals(failed, Left(TrustedGameFailure.StorageFailure))
      assert(!failed.toString.contains(codes.head.raw))
      assertEquals(rows(connection, gameId), Vector(0, 0, 0, 0))
    }

  test("bootstrap preparation performs no repository IO and matches ordinary handle"):
    val untouched = new EventStreamRepository:
      def load(id: String): Either[RepositoryFailure, Option[StoredEventStream]] = fail("preparation must not read storage")
      def append(id: String, expected: ExpectedStream, records: Vector[String]): Either[RepositoryFailure, RepositoryAppendResult] =
        fail("preparation must not write storage")
    val config = FirstGameBootstrapConfig(participants, orders.firstPlayer)
    val dealt = ChronicleFirstGamePlan.dealOrder(chronicle, config)
    val prepared = new GameApplicationService(catalog, untouched)
      .prepareBootstrap("prepared", chronicle, config).toOption.get
    val journal = new InMemoryEventStreamRepository
    val accepted = new GameApplicationService(catalog, journal)
      .handle("prepared", 0L, GameCommand.Begin(chronicle, dealt)).toOption.get
    assertEquals(prepared.state, accepted.state)
    assertEquals(prepared.events, accepted.events)
    parkedAssertions.assertParked(accepted.state, TriggeredProcedureRef.Setup,
      SetupProcedure.pawnDecisionId(orders.firstPlayer), orders.firstPlayer)
    assertEquals(prepared.records, journal.load("prepared").toOption.flatten.get.records)

  test("store rejects invalid seat and record input without throwing or persisting rows"):
    withDatabase { (owner, connection) =>
      assertEquals(owner.trustedGames.create("invalid", Vector(codes.head.digest -> null),
        Vector("record"), 0L), Left(TrustedGameStoreFailure.InvalidInput))
      assertEquals(owner.trustedGames.create("invalid", Vector(codes.head.digest -> "p1"),
        Vector.empty, 0L), Left(TrustedGameStoreFailure.InvalidInput))
      assertEquals(rows(connection, "invalid"), Vector(0, 0, 0, 0))
    }

  test("store preserves record order and rolls back even after an earlier event row was inserted"):
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
