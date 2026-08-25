package oathdigital.protocol

class CommandProtocolSuite extends munit.FunSuite {
  test("actorless command envelopes round trip identically") {
    val request = ActorlessCommandRequest(12,
      ujson.Obj("type" -> "travel", "destinationSiteId" -> "S2"))
    assertEquals(ActorlessCommandCodec.decode(ActorlessCommandCodec.encode(request)),
      Right(request))
  }

  test("actor injection and non-exact envelopes have typed failures") {
    val injected = """{"expectedNextSequence":0,"intent":{"type":"endWake","playerId":"spoof"}}"""
    assertEquals(ActorlessCommandCodec.decode(injected).left.toOption.get.path,
      "$.intent.playerId")
    val extra = """{"expectedNextSequence":0,"intent":{"type":"endWake"},"actor":"spoof"}"""
    assertEquals(ActorlessCommandCodec.decode(extra).left.toOption.get.path,
      "$.actor")
  }
}
