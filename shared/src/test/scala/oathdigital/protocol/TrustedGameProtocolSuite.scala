package oathdigital.protocol

import oathdigital.model.PlayerColor

class TrustedGameProtocolSuite extends munit.FunSuite:
  private val json = """{"participants":[{"playerId":"p1","color":"red"},{"playerId":"p2","color":"blue"}]}"""
  private val request = TrustedGameCreateRequest(Vector(
    BootstrapParticipantRequest("p1", PlayerColor.Red),
    BootstrapParticipantRequest("p2", PlayerColor.Blue)))

  test("creation request round trips with stable actorless field order"):
    assertEquals(TrustedGameCreateRequestCodec.encode(request), json)
    assertEquals(TrustedGameCreateRequestCodec.decode(json), Right(request))

  test("creation requires exact root and participant fields"):
    val base = ujson.read(json)
    val missing = ujson.read(json).obj
    missing.remove("participants")
    assert(TrustedGameCreateRequestCodec.decode(ujson.write(missing)).isLeft)
    // The server generates the game ID, so a request naming one is refused.
    Vector("gameId", "actor", "expectedNextSequence", "firstPlayerId", "extra").foreach { key =>
      val value = ujson.read(json)
      value(key) = "p1"
      assert(TrustedGameCreateRequestCodec.decode(ujson.write(value)).isLeft)
    }
    base("participants")(0)("actor") = "p1"
    assert(TrustedGameCreateRequestCodec.decode(ujson.write(base)).isLeft)
    // The server derives the lineage from the color, so a request naming one is refused.
    val lineage = ujson.read(json)
    lineage("participants")(0)("lineageId") = "l1"
    assertEquals(TrustedGameCreateRequestCodec.decode(ujson.write(lineage)).left.toOption
      .map(_.path), Some("$.participants[0].lineageId"))

  test("invalid player ids are refused"):
    // An id holds at most 128 characters, starts with a letter or a digit
    // and has no path or query characters such as "/" or "?".
    Vector("", " ", "a/b", "a?b", "a" * 129).foreach { invalid =>
      assert(TrustedGameCreateRequestCodec.decode(
        json.replace("p1", invalid)).isLeft)
    }

  test("duplicate and empty participants are refused"):
    val twice = Vector(request.participants.head, request.participants.head)
    Vector(request.copy(participants = Vector.empty),
      request.copy(participants = twice)).foreach { invalid =>
      assert(TrustedGameCreateRequestCodec.decode(
        TrustedGameCreateRequestCodec.encode(invalid)).isLeft)
    }

  test("an unknown colour is refused at its path"):
    // A colour outside the closed set is refused where it enters.
    Vector("", "green").foreach { invalid =>
      assertEquals(TrustedGameCreateRequestCodec.decode(
        json.replace("\"red\"", s"\"$invalid\"")).left.toOption.map(_.path),
        Some("$.participants[0].color"))
    }

  test("malformed JSON, a non-object root and a numeric id are refused"):
    Vector("[]", "null", "{", json.replace("\"p1\"", "12")).foreach {
      invalid =>
      assert(TrustedGameCreateRequestCodec.decode(invalid).isLeft)
    }

  private val seatResponse = TrustedGameCreateResponse("game-1", Vector(
    TrustedSeatLink("p2", "https://example.test/s/code2"),
    TrustedSeatLink("p1", "https://example.test/s/code1")))
  private val seatResponseJson =
    """{"gameId":"game-1","seats":[""" +
      """{"playerId":"p2","url":"https://example.test/s/code2"},""" +
      """{"playerId":"p1","url":"https://example.test/s/code1"}]}"""

  test("response writes ordered game and seat links and decodes them back"):
    assertEquals(TrustedGameCreateResponseCodec.encode(seatResponse),
      seatResponseJson)
    assertEquals(TrustedGameCreateResponseCodec.decode(seatResponseJson),
      Right(seatResponse))

  test("an extra seat field is refused"):
    val extra = ujson.read(seatResponseJson)
    extra("seats")(0)("code") = "secret"
    assert(TrustedGameCreateResponseCodec.decode(ujson.write(extra)).isLeft)

  test("a missing url is refused"):
    val missing = ujson.read(seatResponseJson)
    missing("seats")(0).obj.remove("url")
    assert(TrustedGameCreateResponseCodec.decode(ujson.write(missing)).isLeft)

  test("an extra root field is refused"):
    val extra = ujson.read(seatResponseJson)
    extra.obj("unexpected") = ujson.Bool(true)
    assert(TrustedGameCreateResponseCodec.decode(ujson.write(extra)).isLeft)
