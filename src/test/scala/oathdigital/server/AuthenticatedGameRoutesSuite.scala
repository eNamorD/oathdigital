package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import java.nio.file.Files

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest => AkkaRequest}

import oathdigital.application._
import oathdigital.application.MembershipRole._
import oathdigital.gameplay.OathRules
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.serialization.GameEventWire
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState

class AuthenticatedGameRoutesSuite extends munit.FunSuite {
  test("authenticated Negotiation lets a non-active member author decisions and rejects outsiders") {
    implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](
      Behaviors.empty, "authenticated-negotiation-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val database = HsqldbDatabaseOwner.open(Files.createTempDirectory(
      "auth-negotiation-route-").resolve("database")).toOption.get
    val identities = database.identities
    val repository = new InMemoryEventStreamRepository
    val gameId = "auth-negotiation-game"
    val (setupState, setupEvents) = execute(new FirstGameSetupRules(catalog))
    val OathState.Ready(ready) = setupState: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val other = ready.game.current.players.find(_.player != actor).get
    val rules = new OathRules(catalog)
    val act = rules.startWalker(setupState,
      oathdigital.model.PhaseTransitionRef.EndWake, actor).toOption.get
    val traveled = rules.startWalker(act.state,
      oathdigital.model.ActionRef.Travel, actor, Vector.empty,
      Vector(oathdigital.model.DecisionOptionRef.Site(
        other.pawnSite.get))).toOption.get
    val allEvents = setupEvents ++ act.events ++ traveled.events
    repository.seed(gameId, allEvents.zipWithIndex.map { case (event, index) =>
      ujson.write(GameEventWire.encodeEvent(gameId, catalog.ref, index.toLong, event)
        .toOption.get)
    })
    val ownerUser = UserId("negotiation-owner")
    val actorUser = UserId("negotiation-actor")
    val otherUser = UserId("negotiation-other")
    val outsider = UserId("negotiation-outsider")
    Vector(ownerUser, actorUser, otherUser, outsider).foreach(user =>
      identities.createUser(user, user.value, 0L))
    identities.createGame(gameId, ownerUser, 0L)
    identities.addMembership(GameMembership(gameId, actorUser, Player,
      Some(actor.value)), 0L)
    identities.addMembership(GameMembership(gameId, otherUser, Player,
      Some(other.player.value)), 0L)
    val csrfDigest = CsrfTokenDigest.fromBytes(SensitiveTokenDigest.sha256("c" * 43))
      .toOption.get
    val authenticator = new HttpSessionAuthenticator {
      override def authenticate(request: AkkaRequest) = Future.successful(
        request.headers.find(_.name == "X-Test-User").map(header =>
          Right(AuthenticatedHttpSession(AuthenticatedUser(UserId(header.value)),
            csrfDigest))).getOrElse(Left(AuthenticationFailure.MissingCredential)))
    }
    val gateway = new AuthenticatedGameGateway(new GameApplicationService(catalog,
      repository), new GameProjector(catalog),
      new MembershipAuthorizationService(identities), identities,
      new GeneratedFirstGamePlanFactory(catalog))
    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(
      new AuthenticatedGameRoutes(authenticator,
        new SameOriginCsrfProtection("http://127.0.0.1"), gateway, blocking).route),
      10.seconds)
    val base = s"http://127.0.0.1:${binding.localAddress.getPort}" +
      s"/api/authenticated/first-games/$gameId"
    val client = HttpClient.newHttpClient()
    try {
      def sequenceOf(response: java.net.http.HttpResponse[String]): Long =
        ujson.read(response.body())("nextSequence").num.toLong
      var sequence = allEvents.size.toLong
      def send(user: UserId, intent: ujson.Obj) = post(client, base + "/commands",
        user.value, ujson.write(ujson.Obj("expectedNextSequence" ->
          ujson.Num(sequence.toDouble), "intent" -> intent)))
      def answer(decision: String, payload: ujson.Obj) = ujson.Obj(
        "type" -> "resolveWalker", "decisionId" -> decision, "payload" -> payload)
      val begin = send(actorUser, ujson.Obj("type" -> "startWalker",
        "action" -> "negotiation", "modifiers" -> ujson.Arr(),
        "startArgs" -> ujson.Arr()))
      assertEquals(begin.statusCode(), 200, begin.body()); sequence = sequenceOf(begin)
      if (ujson.read(begin.body())("walkerDecision")("decisionId").str ==
          "negotiation.negotiators") {
        val chosen = send(actorUser, answer("negotiation.negotiators",
          ujson.Obj("kind" -> "choose-many", "options" -> ujson.Arr(ujson.Obj(
            "optionKind" -> "player", "optionId" -> other.player.value)))))
        assertEquals(chosen.statusCode(), 200, chosen.body()); sequence = sequenceOf(chosen)
      }
      val outsiderAttempt = send(outsider, answer("negotiation.deal",
        ujson.Obj("kind" -> "decline-deal")))
      assertEquals(outsiderAttempt.statusCode(), 403)
      val replace = send(otherUser, answer("negotiation.deal", ujson.Obj(
        "kind" -> "propose-terms", "terms" -> ujson.Obj("transfers" ->
          ujson.Arr(ujson.Obj("recipientPlayerId" -> actor.value, "favor" -> 1,
            "relicIds" -> ujson.Arr())), "disclosures" -> ujson.Arr()))))
      assertEquals(replace.statusCode(), 200, replace.body()); sequence = sequenceOf(replace)
      val accept = send(otherUser, answer("negotiation.deal",
        ujson.Obj("kind" -> "accept-deal")))
      assertEquals(accept.statusCode(), 200, accept.body()); sequence = sequenceOf(accept)
      val decline = send(otherUser, answer("negotiation.deal",
        ujson.Obj("kind" -> "decline-deal")))
      assertEquals(decline.statusCode(), 200, decline.body())
      assert(ujson.read(decline.body())("walkerDecision").isNull)
    
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate(); Await.result(system.whenTerminated, 10.seconds)
      database.close()
    }
  }

  test("authenticated routes derive projection scope and command actor") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "authenticated-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val database = HsqldbDatabaseOwner.open(
      Files.createTempDirectory("auth-route-").resolve("database")
    ).toOption.get
    val identities = database.identities
    val owner = UserId("owner")
    val p2User = UserId("user-p2")
    val p3User = UserId("user-p3")
    val spectator = UserId("spectator")
    val outsider = UserId("outsider")
    Vector(owner, p2User, p3User, spectator, outsider)
      .foreach(user => identities.createUser(user, user.value, 0L))
    identities.createGame("auth-game", owner, 0L)
    identities.addMembership(
      GameMembership("auth-game", p2User, Player, Some("p2")), 0L)
    identities.addMembership(
      GameMembership("auth-game", p3User, Player, Some("p3")), 0L)
    identities.addMembership(
      GameMembership("auth-game", spectator, Spectator, None), 0L)

    val events = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, events)
    service.handle("auth-game", 0L, GameCommand.Begin(plan))
    val csrfToken = "c" * 43
    val csrfDigest = CsrfTokenDigest.fromBytes(
      SensitiveTokenDigest.sha256(csrfToken)
    ).toOption.get
    val authenticator = new HttpSessionAuthenticator {
      override def authenticate(request: AkkaRequest) =
        Future.successful(request.headers.find(_.name == "X-Test-User") match {
          case Some(header) => Right(AuthenticatedHttpSession(
            AuthenticatedUser(UserId(header.value)), csrfDigest))
          case None => Left(AuthenticationFailure.MissingCredential)
        })
    }
    val gateway = new AuthenticatedGameGateway(
      service,
      new GameProjector(catalog),
      new MembershipAuthorizationService(identities),
      identities,
      new GeneratedFirstGamePlanFactory(catalog)
    )
    val binding = Await.result(
      Http().newServerAt("127.0.0.1", 0).bind(
        new AuthenticatedGameRoutes(
          authenticator,
          new SameOriginCsrfProtection("http://127.0.0.1"),
          gateway,
          blocking
        ).route
      ),
      10.seconds
    )
    val base = s"http://127.0.0.1:${binding.localAddress.getPort}" +
      "/api/authenticated/first-games/auth-game"
    val client = HttpClient.newHttpClient()

    try {
      assertEquals(get(client, base, None).statusCode(), 401)
      assertEquals(get(client, base, Some(outsider.value)).statusCode(), 403)
      assertEquals(get(client, base + "?playerId=p2", Some(p2User.value))
        .statusCode(), 400)

      // Forge is a walker action (batch-1 Task 3), so the three guarantees
      // this block always made are asserted on `startWalker`/`resolveWalker`
      // instead of the deleted `beginForge`/`completeForge` intents: the
      // actor is derived from the session and never carried on the wire, a
      // spoofed one is rejected outright, and no unknown field rides an
      // assignment payload.
      val actorDerivedForge = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj("expectedNextSequence" -> 1,
          "intent" -> ujson.Obj("type" -> "startWalker", "action" -> "forge",
            "modifiers" -> ujson.Arr()))))
      assertEquals(actorDerivedForge.statusCode(), 422, actorDerivedForge.body())
      val spoofedForgeActor = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj("expectedNextSequence" -> 1,
          "intent" -> ujson.Obj("type" -> "startWalker", "action" -> "forge",
            "modifiers" -> ujson.Arr(), "playerId" -> "p3"))))
      assertEquals(spoofedForgeActor.statusCode(), 400)
      val spoofedRelic = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj("expectedNextSequence" -> 1,
          "intent" -> ujson.Obj("type" -> "resolveWalker",
            "decisionId" -> "forge.assignment",
            "payload" -> ujson.Obj("kind" -> "partition",
              "placements" -> ujson.Arr(),
              "relicId" -> "relic:spoofed")))))
      assertEquals(spoofedRelic.statusCode(), 400, spoofedRelic.body())
      val actorDerivedMinor = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj("expectedNextSequence" -> 1,
          "intent" -> ujson.Obj("type" -> "peekSiteRelics"))))
      assertEquals(actorDerivedMinor.statusCode(), 422, actorDerivedMinor.body())
      val spoofedMinorActor = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj("expectedNextSequence" -> 1,
          "intent" -> ujson.Obj("type" -> "peekSiteRelics", "playerId" -> "p3"))))
      assertEquals(spoofedMinorActor.statusCode(), 400)

      val pawn = post(client, base + "/commands", p2User.value,
        intentBody(1L, "placePawn", "siteId", sites.head.value))
      assertEquals(pawn.statusCode(), 200, pawn.body())
      assert(ujson.read(pawn.body())("pendingCardDecision")("cards").arr.nonEmpty)
      assertNoHiddenPlan(pawn.body())

      Vector(owner, spectator, p3User).foreach { user =>
        val projection = get(client, base, Some(user.value))
        assertEquals(projection.statusCode(), 200)
        assert(ujson.read(projection.body())("pendingCardDecision").isNull)
        assertNoHiddenPlan(projection.body())
      }

      val impersonation = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj(
          "expectedNextSequence" -> 2,
          "intent" -> ujson.Obj(
            "type" -> "resolveCardDecision",
            "playerId" -> "p3",
            "decisionId" -> "setup-adviser-0-p2",
            "resolution" -> ujson.Obj("kind" -> "starting-adviser",
              "adviserId" -> plan.denizenOrder(9).value)
          )
        )))
      assertEquals(impersonation.statusCode(), 400)
      Vector(owner, spectator).foreach { user =>
        assertEquals(post(client, base + "/commands", user.value,
          intentBody(2L, "placePawn", "siteId", "S1")
        ).statusCode(), 403)
      }

      val stale = post(client, base + "/commands", p2User.value,
        decisionIntentBody(1L, plan.denizenOrder(9).value))
      assertEquals(stale.statusCode(), 409, stale.body())
      val accepted = post(client, base + "/commands", p2User.value,
        decisionIntentBody(2L, plan.denizenOrder(9).value))
      assertEquals(accepted.statusCode(), 200)

      database.close()
      val storage = get(client, base, Some(owner.value))
      assertEquals(storage.statusCode(), 500)
      assertEquals(
        ujson.read(storage.body())("error").str,
        "internal-error"
      )
      assert(!storage.body().toLowerCase.contains("closed"))
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
      database.close()
    }
  }

  private def assertNoHiddenPlan(body: String): Unit = {
    assert(!ujson.read(body).obj.contains("viewerPlayerId"))
    assert(!body.contains("worldDeckOrder"))
    assert(!body.contains("relicOrder"))
    assert(!body.contains("denizenOrder"))
  }

  private def intentBody(
      expected: Long,
      intentType: String,
      field: String,
      value: String
  ): String = ujson.write(ujson.Obj(
    "expectedNextSequence" -> ujson.Num(expected.toDouble),
    "intent" -> ujson.Obj("type" -> intentType, field -> value)
  ))

  private def get(client: HttpClient, url: String, user: Option[String]) = {
    val builder = HttpRequest.newBuilder(URI.create(url)).GET()
    user.foreach(value => builder.header("X-Test-User", value))
    client.send(builder.build(), JavaResponse.BodyHandlers.ofString())
  }

  private def decisionIntentBody(sequence: Long, adviserId: String): String =
    ujson.write(ujson.Obj("expectedNextSequence" -> ujson.Num(sequence.toDouble),
      "intent" -> ujson.Obj("type" -> "resolveCardDecision",
        "decisionId" -> "setup-adviser-0-p2",
        "resolution" -> ujson.Obj("kind" -> "starting-adviser",
          "adviserId" -> adviserId))))

  private def post(client: HttpClient, url: String, user: String, body: String) =
    client.send(HttpRequest.newBuilder(URI.create(url))
      .header("X-Test-User", user)
      .header("Origin", "http://127.0.0.1")
      .header("X-CSRF-Token", "c" * 43)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      JavaResponse.BodyHandlers.ofString())
}
