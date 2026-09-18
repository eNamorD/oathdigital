package oathdigital.server

import oathdigital.application.{GameCommand, GameIntentMapper, StartPayload,
  TreeDecision}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer, PartitionAnswer}
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  DecisionAnswerWire, DecisionPlacementWire, DistributeAmountWire, GameIntent}

class GameHttpWireSuite extends munit.FunSuite {
  test("the usePower intent binds to a UsePower command for the requester") {
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      oathdigital.protocol.GameIntent.UsePower("denizen.silver-tongue",
        oathdigital.protocol.WalkerStartArgWire("denizen", "92"))),
      Right(GameCommand.UsePower(PlayerId("actor-1"),
        PowerId("denizen.silver-tongue"),
        DecisionOptionRef.Denizen(DenizenId("92")))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      oathdigital.protocol.GameIntent.UsePower("Not A Power",
        oathdigital.protocol.WalkerStartArgWire("denizen", "92"))).left.toOption
      .map(_.path), Some("$.intent.powerId"))
    assert(GameIntentMapper.bind(PlayerId("actor-1"),
      oathdigital.protocol.GameIntent.UsePower("denizen.silver-tongue",
        oathdigital.protocol.WalkerStartArgWire("warband", "w1"))).isLeft)
  }
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
      GameIntent.PlacePawn("site:a")),
      Right(GameCommand.PlacePawn(PlayerId("dev-selected"), SiteId("site:a"))))
    assertEquals(GameIntentMapper.bind(PlayerId("member-seat"), GameIntent.EndWake),
      Right(GameCommand.EndWake(PlayerId("member-seat"))))
  }

  test("domain conversion rejects unknown protocol identifiers without throwing") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.Trade(oathdigital.protocol.EconomyTarget("denizen", "d1"),
        "injected-resource")).left.toOption.get
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
    // die faces into. It DOES carry the transport-bound actor (C1): the
    // rules layer checks the requester against the parked position's owner
    // before resuming anything.
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.RollWalker("recover.pool")),
      Right(GameCommand.RollWalker(PlayerId("actor-1"), PoolKey("recover.pool"))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.choice",
        DecisionAnswerWire.ChooseOneWire("button", "continue"))),
      Right(GameCommand.ResolveWalker(PlayerId("actor-1"),
        TreeDecision("recover.choice",
          ChooseOneAnswer(DecisionOptionRef.Button("continue"))))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", "relic-1"))),
      Right(GameCommand.ResolveWalker(PlayerId("actor-1"),
        TreeDecision("recover.relic",
          ChooseOneAnswer(DecisionOptionRef.Relic(RelicId("relic-1")))))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("forge.assignment",
        DecisionAnswerWire.PartitionWire(Vector(
          DecisionPlacementWire("denizen", "d1", "pay-favor"),
          DecisionPlacementWire("denizen", "d2", "pay-secret"))))),
      Right(GameCommand.ResolveWalker(PlayerId("actor-1"),
        TreeDecision("forge.assignment", PartitionAnswer(Vector(
          DecisionPlacement(DecisionOptionRef.Denizen(DenizenId("d1")),
            "pay-favor"),
          DecisionPlacement(DecisionOptionRef.Denizen(DenizenId("d2")),
            "pay-secret")))))))
    assertEquals(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("rest.distribution",
        DecisionAnswerWire.DistributeWire(Vector(
          DistributeAmountWire("favor-bank", "arcane", 0),
          DistributeAmountWire("favor-bank", "nomad", 3))))),
      Right(GameCommand.ResolveWalker(PlayerId("actor-1"),
        TreeDecision("rest.distribution", DistributeAnswer(Vector(
          DistributeAmount(DecisionOptionRef.FavorBank(Suit.Arcane), 0),
          DistributeAmount(DecisionOptionRef.FavorBank(Suit.Nomad), 3)))))))

    // An unknown option kind, and a blank id an identifier would throw on,
    // are both typed mapping failures rather than exceptions.
    assert(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.relic",
        DecisionAnswerWire.ChooseOneWire("warband", "w1"))).isLeft)
    assert(GameIntentMapper.bind(PlayerId("actor-1"),
      GameIntent.ResolveWalker("recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", "   "))).isLeft)
  }

  test("an unknown walker action string is rejected without throwing") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.StartWalker("teleport", Vector.empty)).left.toOption.get
    assertEquals(failure.path, "$.intent.action")
    assert(failure.message.contains("unknown action"))
  }

  test("a malformed walker modifier id is rejected with a typed failure, " +
      "not an exception") {
    val result = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.StartWalker("recover", Vector("Recover")))
    val failure = result match {
      case Left(f) => f
      case Right(command) => fail(s"expected a typed rejection, got $command")
    }
    assertEquals(failure.path, "$.intent.modifiers[0]")
  }

  test("an unknown option kind is rejected without throwing") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("recover.choice",
        DecisionAnswerWire.ChooseOneWire("teleport", "x"))).left.toOption.get
    assertEquals(failure.path, "$.intent.payload.option")
  }

  test("a blank relic id is rejected with a typed failure, not an " +
      "IllegalArgumentException escaping the mapper (finding I7)") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", ""))).left.toOption.get
    assertEquals(failure.path, "$.intent.payload.option")
  }

  test("an unplaceable option inside a partition answer is rejected with " +
      "the placement path, not the choose-one path") {
    val failure = GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("forge.assignment",
        DecisionAnswerWire.PartitionWire(Vector(
          DecisionPlacementWire("warband", "w1", "pay-favor")))))
      .left.toOption.get
    assertEquals(failure.path, "$.intent.payload.placements.option")
  }

  test("malformed actorless requests retain typed paths") {
    assertEquals(GameHttpWire.decodeCommand("{").left.toOption.get.path, "$")
    val missing = """{"expectedNextSequence":8,"intent":{"type":"startWalker",""" +
      """"action":"travel","modifiers":[],"startArgs":[{"optionKind":"site"}]}}"""
    assertEquals(GameHttpWire.decodeCommand(missing).left.toOption.get.path,
      "$.intent.startArgs[0].optionId")
  }

  test("development bootstrap remains configuration-only") {
    val json =
      """{"expectedNextSequence":0,"participants":[{"playerId":"p1","lineageId":"l1","color":"red"}],"firstPlayer":"p1"}"""
    val request = GameHttpWire.decodeBootstrap(json).toOption.get
    assertEquals(request.expectedNextSequence, 0L)
    assertEquals(request.participants.map(_.playerId), Vector("p1"))
  }
}
