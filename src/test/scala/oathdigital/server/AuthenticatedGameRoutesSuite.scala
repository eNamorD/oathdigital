package oathdigital.server

import java.net.http.HttpResponse
import java.nio.file.Files

import oathdigital.application._
import oathdigital.application.MembershipRole._
import oathdigital.catalog.SiteOnly
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.DenizenId
import oathdigital.testkit.{CatalogNames, HttpTestServer,
  TestUserHeaderAuthenticator}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class AuthenticatedGameRoutesSuite extends munit.FunSuite:
  /** The authenticated routes over `service` and the memberships in
    * `identities`. */
  private def serve(name: String, service: GameApplicationService,
      identities: IdentityRepository): HttpTestServer =
    val gateway = new AuthenticatedGameGateway(service,
      new GameProjector(catalog),
      new MembershipAuthorizationService(identities), identities,
      new GeneratedFirstGamePlanFactory(catalog))
    HttpTestServer.start(name)(blocking => new AuthenticatedGameRoutes(
      TestUserHeaderAuthenticator,
      new SameOriginCsrfProtection(HttpTestServer.origin),
      gateway, blocking).route)

  private def openDatabase(label: String): HsqldbDatabaseOwner =
    accepted(HsqldbDatabaseOwner.open(
      Files.createTempDirectory(label).resolve("database")))

  test("authenticated Negotiation lets a non-active member author decisions " +
      "and rejects outsiders"):
    val database = openDatabase("auth-negotiation-route-")
    val identities = database.identities
    val repository = new InMemoryEventStreamRepository
    val gameId = "auth-negotiation-game"
    // Replays the real setup: the route test needs a Negotiation reached by
    // real play, as a client would reach it.
    // initialSituation's pawns: p2, the first player, at Ancient City and
    // p1 alone at Buried Giant. p2 travels to p1.
    val traveled = initialSituation().endingWake(p2)
      .parkedAfter(ParkedServiceFixture.startTravel(p2))
      .parkedAfter(ParkedServiceFixture.pickTravelDestination(p2,
        CatalogNames.site("Buried Giant")))
    traveled.seedInto(repository, gameId)
    val ownerUser = UserId("negotiation-owner")
    val actorUser = UserId("negotiation-actor")
    val otherUser = UserId("negotiation-other")
    val outsider = UserId("negotiation-outsider")
    Vector(ownerUser, actorUser, otherUser, outsider).foreach(user =>
      identities.createUser(user, user.value, 0L))
    identities.createGame(gameId, ownerUser, 0L)
    identities.addMembership(GameMembership(gameId, actorUser, Player,
      Some(p2.value)), 0L)
    identities.addMembership(GameMembership(gameId, otherUser, Player,
      Some(p1.value)), 0L)
    val server = serve("authenticated-negotiation-route-test",
      new GameApplicationService(catalog, repository), identities)
    val commands = s"/api/authenticated/first-games/$gameId/commands"
    try
      def sequenceOf(response: HttpResponse[String]): Long =
        ujson.read(response.body())("nextSequence").num.toLong
      var sequence = traveled.nextSequence
      def send(user: UserId, intent: ujson.Obj) = server.post(commands,
        ujson.write(ujson.Obj("expectedNextSequence" ->
          ujson.Num(sequence.toDouble), "intent" -> intent)),
        Some(user.value))
      def answer(decision: String, payload: ujson.Obj) = ujson.Obj(
        "type" -> "resolveWalker", "decisionId" -> decision, "payload" -> payload)
      // The Act menu is parked: Negotiation is picked from it.
      val begin = send(actorUser, answer("phase.menu", ujson.Obj(
        "kind" -> "choose-one", "optionKind" -> "button",
        "optionId" -> "negotiation")))
      assertEquals(begin.statusCode(), 200, begin.body()); sequence = sequenceOf(begin)
      // p1 is the only other player at Buried Giant, so there are no
      // negotiators to choose: the walk parks on the deal.
      assertEquals(ujson.read(begin.body())("walkerDecision")("decisionId").str,
        "negotiation.deal")
      val outsiderAttempt = send(outsider, answer("negotiation.deal",
        ujson.Obj("kind" -> "decline-deal")))
      assertEquals(outsiderAttempt.statusCode(), 403)
      val replace = send(otherUser, answer("negotiation.deal", ujson.Obj(
        "kind" -> "propose-terms", "terms" -> ujson.Obj("transfers" ->
          ujson.Arr(ujson.Obj("recipientPlayerId" -> p2.value, "favor" -> 1,
            "relicIds" -> ujson.Arr())), "disclosures" -> ujson.Arr()))))
      assertEquals(replace.statusCode(), 200, replace.body()); sequence = sequenceOf(replace)
      val accept = send(otherUser, answer("negotiation.deal",
        ujson.Obj("kind" -> "accept-deal")))
      assertEquals(accept.statusCode(), 200, accept.body()); sequence = sequenceOf(accept)
      val decline = send(otherUser, answer("negotiation.deal",
        ujson.Obj("kind" -> "decline-deal")))
      assertEquals(decline.statusCode(), 200, decline.body())
      assert(ujson.read(decline.body())("walkerDecision").isNull)
    finally
      server.close()
      database.close()

  private val owner = UserId("owner")
  private val p2User = UserId("user-p2")
  private val p3User = UserId("user-p3")
  private val spectator = UserId("spectator")
  private val outsider = UserId("outsider")

  private val gamePath = "/api/authenticated/first-games/auth-game"
  private val commandsPath = gamePath + "/commands"

  /** "auth-game", begun with p2 first and parked on p2's Setup pawn
    * placement: `owner` owns it, user-p2 and user-p3 hold seats p2 and p3,
    * `spectator` watches, and `outsider` belongs to none of it.
    * `afterBegin` is its next sequence. */
  private final case class BegunAuthGame(server: HttpTestServer,
      database: HsqldbDatabaseOwner, afterBegin: Long)

  /** `body`'s result; closes `database` and rethrows if `body` fails, since
    * munit runs a fixture's teardown only after its setup succeeds. */
  private def closingOnFailure[A](database: HsqldbDatabaseOwner)(
      body: => A): A =
    try body
    catch
      case error: Throwable =>
        database.close()
        throw error

  private val begunAuthGame = FunFixture[BegunAuthGame](
    setup = _ =>
      val database = openDatabase("auth-route-")
      closingOnFailure(database):
        val identities = database.identities
        Vector(owner, p2User, p3User, spectator, outsider)
          .foreach(user => identities.createUser(user, user.value, 0L))
        identities.createGame("auth-game", owner, 0L)
        identities.addMembership(
          GameMembership("auth-game", p2User, Player, Some("p2")), 0L)
        identities.addMembership(
          GameMembership("auth-game", p3User, Player, Some("p3")), 0L)
        identities.addMembership(
          GameMembership("auth-game", spectator, Spectator, None), 0L)
        val service = new GameApplicationService(catalog,
          new InMemoryEventStreamRepository)
        val begun = accepted(service.handle("auth-game", 0L,
          GameCommand.Begin(chronicle, orders.copy(firstPlayer = p2))))
        BegunAuthGame(serve("authenticated-route-test", service, identities),
          database, begun.nextSequence),
    teardown = begun =>
      begun.server.close()
      begun.database.close())

  begunAuthGame.test("a projection needs a member's session and takes no " +
      "playerId"):
    case BegunAuthGame(server, _, _) =>
      assertEquals(server.get(gamePath).statusCode(), 401)
      assertEquals(server.get(gamePath, Some(outsider.value)).statusCode(), 403)
      assertEquals(server.get(gamePath + "?playerId=p2", Some(p2User.value))
        .statusCode(), 400)

  begunAuthGame.test("actorless commands reach the rules and a playerId on " +
      "the wire is refused"):
    case BegunAuthGame(server, _, afterBegin) =>
      def command(intent: ujson.Obj) = server.post(commandsPath,
        ujson.write(ujson.Obj(
          "expectedNextSequence" -> ujson.Num(afterBegin.toDouble),
          "intent" -> intent)), Some(p2User.value))
      // Forge is a walker action (batch-1 Task 3), so the three guarantees
      // this block always made are asserted on `resolveWalker` instead of
      // the deleted `beginForge`/`completeForge` intents: the actor is
      // derived from the session and never carried on the wire, a spoofed
      // one is rejected outright, and no unknown field rides an assignment
      // payload. 422: the game is in Setup, so the derived actor has no
      // Forge assignment to answer. 400: the wire refuses a playerId field
      // and an unknown payload field.
      val assignment = ujson.Obj("kind" -> "partition",
        "placements" -> ujson.Arr())
      val actorDerivedForge = command(ujson.Obj("type" -> "resolveWalker",
        "decisionId" -> "forge.assignment", "payload" -> assignment))
      assertEquals(actorDerivedForge.statusCode(), 422, actorDerivedForge.body())
      val spoofedForgeActor = command(ujson.Obj("type" -> "resolveWalker",
        "decisionId" -> "forge.assignment", "payload" -> assignment,
        "playerId" -> "p3"))
      assertEquals(spoofedForgeActor.statusCode(), 400)
      val spoofedRelic = command(ujson.Obj("type" -> "resolveWalker",
        "decisionId" -> "forge.assignment",
        "payload" -> ujson.Obj("kind" -> "partition",
          "placements" -> ujson.Arr(),
          "relicId" -> "relic:spoofed")))
      assertEquals(spoofedRelic.statusCode(), 400, spoofedRelic.body())

  begunAuthGame.test("p2 walks Setup through the authenticated routes, " +
      "nobody else sees or answers its decisions, and a stale position " +
      "conflicts"):
    case BegunAuthGame(server, _, afterBegin) =>
      // p2 is dealt Wizard's Conclave, Errand Boy and Nature Worship, keeps
      // Wizard's Conclave, and discards the others.
      val kept = CatalogNames.denizen("Wizard's Conclave")
      val discarded = Vector("Errand Boy", "Nature Worship")
        .map(CatalogNames.denizen(_))
      assertEquals(freshReady.game.current.temporaryHands(p2)
        .collect { case id: DenizenId => id }, kept +: discarded)
      assert(!catalog.denizen(kept).exists(_.isInstanceOf[SiteOnly]))

      val pawnDecision = SetupProcedure.pawnDecisionId(p2)
      val pawn = server.post(commandsPath,
        resolveWalkerBody(afterBegin, pawnDecision, "site",
          CatalogNames.site("Ancient City").value), Some(p2User.value))
      assertEquals(pawn.statusCode(), 200, pawn.body())
      assertEquals(ujson.read(pawn.body())("walkerDecision")("decisionId").str,
        SetupProcedure.adviserDecisionId(p2))
      assert(ujson.read(pawn.body())("walkerDecision")("query")("options")
        .arr.nonEmpty)
      assertNoHiddenPlan(pawn.body())
      val afterPawn = ujson.read(pawn.body())("nextSequence").num.toLong

      Vector(owner, spectator, p3User).foreach { user =>
        val projection = server.get(gamePath, Some(user.value))
        assertEquals(projection.statusCode(), 200)
        assert(ujson.read(projection.body())("walkerDecision").isNull)
        assertNoHiddenPlan(projection.body())
      }

      val adviserDecision = SetupProcedure.adviserDecisionId(p2)
      val impersonation = server.post(commandsPath,
        ujson.write(ujson.Obj(
          "expectedNextSequence" -> ujson.Num(afterPawn.toDouble),
          "intent" -> ujson.Obj(
            "type" -> "resolveWalker",
            "playerId" -> "p3",
            "decisionId" -> adviserDecision,
            "payload" -> ujson.Obj("kind" -> "choose-one",
              "optionKind" -> "denizen", "optionId" -> kept.value)
          )
        )), Some(p2User.value))
      assertEquals(impersonation.statusCode(), 400)
      Vector(owner, spectator).foreach { user =>
        assertEquals(server.post(commandsPath,
          resolveWalkerBody(afterPawn, adviserDecision, "denizen", kept.value),
          Some(user.value)).statusCode(), 403)
      }

      val stale = server.post(commandsPath,
        resolveWalkerBody(afterBegin, adviserDecision, "denizen", kept.value),
        Some(p2User.value))
      assertEquals(stale.statusCode(), 409, stale.body())
      val keptResponse = server.post(commandsPath,
        resolveWalkerKeepPartitionBody(afterPawn, adviserDecision, "denizen",
          kept.value, discarded.map(_.value)), Some(p2User.value))
      assertEquals(keptResponse.statusCode(), 200)
      // Reveal Cards follows, since Wizard's Conclave is not site-only. Only
      // its owner is offered the card; everyone else sees no decision.
      val reveal = ujson.read(keptResponse.body())("walkerDecision")
      assertEquals(reveal("decisionId").str,
        SetupProcedure.revealDecisionId(p2))
      assertEquals(reveal("query")("options").arr.map(option =>
        (option("kind").str, option("id").str, option("label").str)).toVector,
        Vector(("adviser-slot", s"${p2.value}:0", "Wizard's Conclave")))
      Vector(owner, spectator, p3User).foreach { user =>
        val projection = server.get(gamePath, Some(user.value))
        assert(ujson.read(projection.body())("walkerDecision").isNull)
        assert(!projection.body().contains(kept.value), user)
      }

  begunAuthGame.test("a storage failure answers 500 without its details"):
    case BegunAuthGame(server, database, _) =>
      database.close()
      val storage = server.get(gamePath, Some(owner.value))
      assertEquals(storage.statusCode(), 500)
      assertEquals(
        ujson.read(storage.body())("error").str,
        "internal-error"
      )
      assert(!storage.body().toLowerCase.contains("closed"))

  private def assertNoHiddenPlan(body: String): Unit =
    assert(!ujson.read(body).obj.contains("viewerPlayerId"))
    assert(!body.contains("worldDeckOrder"))
    assert(!body.contains("relicOrder"))
    assert(!body.contains("denizenOrder"))

  private def resolveWalkerBody(sequence: Long, decisionId: String,
      optionKind: String, optionId: String): String =
    ujson.write(ujson.Obj("expectedNextSequence" -> ujson.Num(sequence.toDouble),
      "intent" -> ujson.Obj("type" -> "resolveWalker",
        "decisionId" -> decisionId,
        "payload" -> ujson.Obj("kind" -> "choose-one",
          "optionKind" -> optionKind, "optionId" -> optionId))))

  private def resolveWalkerKeepPartitionBody(sequence: Long, decisionId: String,
      optionKind: String, keptId: String, discardedIds: Vector[String]): String =
    ujson.write(ujson.Obj("expectedNextSequence" -> ujson.Num(sequence.toDouble),
      "intent" -> ujson.Obj("type" -> "resolveWalker",
        "decisionId" -> decisionId,
        "payload" -> ujson.Obj("kind" -> "partition",
          "placements" -> ujson.Arr.from(
            ujson.Obj("optionKind" -> optionKind, "optionId" -> keptId,
              "sectionKey" -> "keep") +:
            discardedIds.map(id => ujson.Obj(
              "optionKind" -> optionKind, "optionId" -> id,
              "sectionKey" -> "discard")))))))
