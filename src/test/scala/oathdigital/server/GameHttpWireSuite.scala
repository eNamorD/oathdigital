package oathdigital.server

import oathdigital.application.{GameCommand, GameIntentMapper, StartPayload,
  TreeDecision}
import oathdigital.gameplay.WakeResource
import oathdigital.model._
import oathdigital.model.DecisionPayload.{RecoverChoice, RecoverChoicePayload,
  RecoverRelicPayload}
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  DecisionPayloadWire, GameIntent}

class GameHttpWireSuite extends munit.FunSuite {
  test("development and authenticated transports decode the same actorless intent") {
    val json = ActorlessCommandCodec.encode(ActorlessCommandRequest(
      8L,
      GameIntent.PlacePawn("site:ancient-city")
    ))
    val development = GameHttpWire.decodeCommand(json).toOption.get
    val authenticated = AuthenticatedGameHttpWire.decodeCommand(json).toOption.get
    assertEquals(development.expectedNextSequence, authenticated.expectedNextSequence)
    assertEquals(development.intent, authenticated.intent)
    assertEquals(development.intent, GameIntent.PlacePawn("site:ancient-city"))
  }

  test("actor injection is rejected by both transports") {
    val json =
      """{"expectedNextSequence":8,"intent":{"type":"endWake","playerId":"spoof"}}"""
    val development = GameHttpWire.decodeCommand(json).left.toOption.get
    val authenticated = AuthenticatedGameHttpWire.decodeCommand(json).left.toOption.get
    assertEquals(development, authenticated)
    assertEquals(development.path, "$.intent.playerId")
  }

  test("one mapper binds the transport-selected actor") {
    assertEquals(GameIntentMapper.bind(PlayerId("dev-selected"),
      GameIntent.TakeWealth("favor")),
      Right(GameCommand.TakeWealth(PlayerId("dev-selected"), WakeResource.Favor)))
    assertEquals(GameIntentMapper.bind(PlayerId("member-seat"), GameIntent.EndWake),
      Right(GameCommand.EndWake(PlayerId("member-seat"))))
    val rest = GameIntent.ResolveRestPower("rest-1", Vector(
      oathdigital.protocol.RestFavorAllocation(
        oathdigital.protocol.RestFavorSource("relic-slot", "site:a", "0"), 2)),
      "hearth")
    assertEquals(GameIntentMapper.bind(PlayerId("off-turn-owner"), rest), Right(
      GameCommand.ResolveRestPower(PlayerId("off-turn-owner"),
        DecisionId("rest-1"), Vector(FavorAllocation(
          SiteFavorSource.Relic(SiteId("site:a"), 0), 2)),
        Suit.Hearth)))
  }

  test("domain conversion rejects unknown protocol identifiers without throwing") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.TakeWealth("injected-resource")).left.toOption.get
    assertEquals(failure.path, "$.intent.resource")
  }

  test("walker intents map onto Task 4's StartWalker/RollWalker/ResolveWalker " +
      "commands, and RollWalker never carries client-supplied faces") {
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.StartWalker("recover", Vector("denizen.catacombs"))),
      Right(GameCommand.StartWalker(ActionRef.Recover,
        StartPayload(PlayerId("actor-1"), Vector(PowerId("denizen.catacombs"))))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.StartWalker("recover", Vector.empty)),
      Right(GameCommand.StartWalker(ActionRef.Recover,
        StartPayload(PlayerId("actor-1")))))
    // RollWalker takes only a pool key -- there is no field on the wire
    // intent, the codec, or GameCommand.RollWalker for a client to place
    // die faces into.
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.RollWalker("recover.pool")),
      Right(GameCommand.RollWalker(PoolKey("recover.pool"))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.choice",
        DecisionPayloadWire.RecoverChoiceWire("continue"))),
      Right(GameCommand.ResolveWalker(TreeDecision("recover.choice",
        RecoverChoicePayload(RecoverChoice.Continue)))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.choice",
        DecisionPayloadWire.RecoverChoiceWire("stop"))),
      Right(GameCommand.ResolveWalker(TreeDecision("recover.choice",
        RecoverChoicePayload(RecoverChoice.Stop)))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.relic",
        DecisionPayloadWire.RecoverRelicWire("relic-1"))),
      Right(GameCommand.ResolveWalker(TreeDecision("recover.relic",
        RecoverRelicPayload(RelicId("relic-1"))))))
  }

  test("an unknown walker action string is rejected without throwing") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.StartWalker("teleport", Vector.empty)).left.toOption.get
    assertEquals(failure.path, "$.intent.action")
    assert(failure.message.contains("unknown action"))
  }

  test("an unknown Recover choice value is rejected without throwing") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("recover.choice",
        DecisionPayloadWire.RecoverChoiceWire("teleport"))).left.toOption.get
    assertEquals(failure.path, "$.intent.payload.choice")
  }

  test("malformed actorless requests retain typed paths") {
    assertEquals(GameHttpWire.decodeCommand("{").left.toOption.get.path, "$")
    val missing = """{"expectedNextSequence":8,"intent":{"type":"travel"}}"""
    assertEquals(GameHttpWire.decodeCommand(missing).left.toOption.get.path,
      "$.intent.destinationSiteId")
  }

  test("development bootstrap remains configuration-only") {
    val json =
      """{"expectedNextSequence":0,"participants":[{"playerId":"p1","lineageId":"l1","color":"red"}],"firstPlayer":"p1"}"""
    val request = GameHttpWire.decodeBootstrap(json).toOption.get
    assertEquals(request.expectedNextSequence, 0L)
    assertEquals(request.participants.map(_.playerId), Vector("p1"))
  }
}
