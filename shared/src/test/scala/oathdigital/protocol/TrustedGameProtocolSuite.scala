package oathdigital.protocol

class TrustedGameProtocolSuite extends munit.FunSuite {
  private val json = """{"gameId":"game-1","participants":[{"playerId":"p1","lineageId":"l1","color":"red"},{"playerId":"p2","lineageId":"l2","color":"blue"}],"firstPlayerId":"p2"}"""
  private val request = TrustedGameCreateRequest("game-1", Vector(
    BootstrapParticipantRequest("p1", "l1", "red"),
    BootstrapParticipantRequest("p2", "l2", "blue")), "p2")

  test("creation request round trips with stable actorless field order") {
    assertEquals(TrustedGameCreateRequestCodec.encode(request), json)
    assertEquals(TrustedGameCreateRequestCodec.decode(json), Right(request))
  }

  test("creation requires exact root and participant fields") {
    val base = ujson.read(json)
    Vector("gameId", "participants", "firstPlayerId").foreach { key =>
      val value = ujson.read(json).obj
      value.remove(key)
      assert(TrustedGameCreateRequestCodec.decode(ujson.write(value)).isLeft)
    }
    Vector("actor", "expectedNextSequence", "extra").foreach { key =>
      val value = ujson.read(json)
      value(key) = "p1"
      assert(TrustedGameCreateRequestCodec.decode(ujson.write(value)).isLeft)
    }
    base("participants")(0)("actor") = "p1"
    assert(TrustedGameCreateRequestCodec.decode(ujson.write(base)).isLeft)
  }

  test("invalid identifiers, duplicates, empty participants and foreign first player fail") {
    Vector("", " ", "a/b", "a?b", "a" * 129).foreach { invalid =>
      assert(TrustedGameCreateRequestCodec.decode(json.replace("game-1", invalid)).isLeft)
      assert(TrustedGameCreateRequestCodec.decode(json.replace("p1", invalid)).isLeft)
    }
    Vector(request.copy(participants = Vector.empty),
      request.copy(participants = Vector(request.participants.head, request.participants.head)),
      request.copy(firstPlayerId = "p3"),
      request.copy(participants = request.participants.updated(0,
        request.participants.head.copy(lineageId = ""))),
      request.copy(participants = request.participants.updated(0,
        request.participants.head.copy(color = "")))).foreach { invalid =>
      assert(TrustedGameCreateRequestCodec.decode(TrustedGameCreateRequestCodec.encode(invalid)).isLeft)
    }
    Vector("[]", "null", "{", json.replace("\"game-1\"", "12")).foreach { invalid =>
      assert(TrustedGameCreateRequestCodec.decode(invalid).isLeft)
    }
  }

  test("response contains only ordered game and seat links with strict decoding") {
    val response = TrustedGameCreateResponse("game-1", Vector(
      TrustedSeatLink("p2", "https://example.test/s/code2"),
      TrustedSeatLink("p1", "https://example.test/s/code1")))
    val encoded = """{"gameId":"game-1","seats":[{"playerId":"p2","url":"https://example.test/s/code2"},{"playerId":"p1","url":"https://example.test/s/code1"}]}"""
    assertEquals(TrustedGameCreateResponseCodec.encode(response), encoded)
    assertEquals(TrustedGameCreateResponseCodec.decode(encoded), Right(response))
    val extra = ujson.read(encoded)
    extra("seats")(0)("code") = "secret"
    assert(TrustedGameCreateResponseCodec.decode(ujson.write(extra)).isLeft)
    val missing = ujson.read(encoded)
    missing("seats")(0).obj.remove("url")
    assert(TrustedGameCreateResponseCodec.decode(ujson.write(missing)).isLeft)
    extra.obj("unexpected") = ujson.Bool(true)
    assert(TrustedGameCreateResponseCodec.decode(ujson.write(extra)).isLeft)
  }
}
