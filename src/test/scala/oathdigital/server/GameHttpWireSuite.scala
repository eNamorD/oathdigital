package oathdigital.server

import oathdigital.application.{GameCommand, GameIntent, GameIntentMapper}
import oathdigital.gameplay.WakeResource
import oathdigital.model.{PlayerId, SiteId}

class GameHttpWireSuite extends munit.FunSuite {
  test("development and authenticated transports decode the same actorless intent") {
    val json =
      """{"expectedNextSequence":8,"intent":{"type":"travel","destinationSiteId":"site:b"}}"""
    val development = GameHttpWire.decodeCommand(json).toOption.get
    val authenticated = AuthenticatedGameHttpWire.decodeCommand(json).toOption.get
    assertEquals(development.expectedNextSequence, authenticated.expectedNextSequence)
    assertEquals(development.intent, authenticated.intent)
    assertEquals(development.intent, GameIntent.Travel(SiteId("site:b")))
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
      GameIntent.TakeWealth(WakeResource.Favor)),
      GameCommand.TakeWealth(PlayerId("dev-selected"), WakeResource.Favor))
    assertEquals(GameIntentMapper.bind(PlayerId("member-seat"), GameIntent.EndWake),
      GameCommand.EndWake(PlayerId("member-seat")))
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
    assertEquals(request.config.participants.map(_.playerId), Vector(PlayerId("p1")))
  }
}
