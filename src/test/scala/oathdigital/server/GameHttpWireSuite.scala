package oathdigital.server

import oathdigital.application.{GameCommand, GameIntentMapper, TreeDecision}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer, PartitionAnswer}
import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire,
  DistributeAmountWire, GameIntent}
import oathdigital.testkit.Unwrap.rejected

class GameHttpWireSuite extends munit.FunSuite:
  test("domain conversion rejects unknown protocol identifiers without throwing"):
    val failure = rejected(GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.RequestConsent("travel.destination",
        oathdigital.protocol.WalkerStartArgWire("teleport", "x"))))
    assertEquals(failure.path, "$.intent.target")

  test("walker intents bind to ResolveWalker commands for the requester"):
    // The command carries the transport-bound actor (C1): the rules layer
    // checks the requester against the parked position's owner before
    // resuming anything.
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

  test("an unknown option kind is rejected without throwing"):
    val failure = rejected(GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("recover.choice",
        DecisionAnswerWire.ChooseOneWire("teleport", "x"))))
    assertEquals(failure.path, "$.intent.payload.option")

  test("a blank relic id is refused with a typed failure"):
    val failure = rejected(GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", ""))))
    assertEquals(failure.path, "$.intent.payload.option")

  test("an unplaceable option inside a partition answer is rejected with " +
      "the placement path, not the choose-one path"):
    val failure = rejected(GameIntentMapper.bind(PlayerId("trusted"),
      GameIntent.ResolveWalker("forge.assignment",
        DecisionAnswerWire.PartitionWire(Vector(
          DecisionPlacementWire("warband", "w1", "pay-favor"))))))
    assertEquals(failure.path, "$.intent.payload.placements.option")

  test("malformed actorless requests retain typed paths"):
    assertEquals(rejected(GameHttpWire.decodeCommand("{")).path, "$")
    val missing = """{"expectedNextSequence":8,"intent":{"type":"requestConsent",""" +
      """"decisionId":"travel.destination","target":{"optionKind":"site"}}}"""
    assertEquals(rejected(GameHttpWire.decodeCommand(missing)).path,
      "$.intent.target[0].optionId")
