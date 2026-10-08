package oathdigital.protocol

import oathdigital.model.PlayerColor
import oathdigital.protocol.ProtocolDecodeFailure.*

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
    assertEquals(TrustedGameCreateRequestCodec.decode(ujson.write(missing)),
      Left(MissingField("$.participants")))
    // The server generates the game ID, so a request naming one is refused.
    Vector("gameId", "actor", "expectedNextSequence", "firstPlayerId", "extra").foreach { key =>
      val value = ujson.read(json)
      value(key) = "p1"
      assertEquals(TrustedGameCreateRequestCodec.decode(ujson.write(value)),
        Left(UnexpectedField(s"$$.$key")), key)
    }
    base("participants")(0)("actor") = "p1"
    assertEquals(TrustedGameCreateRequestCodec.decode(ujson.write(base)),
      Left(UnexpectedField("$.participants[0].actor")))
    // The server derives the lineage from the color, so a request naming one is refused.
    val lineage = ujson.read(json)
    lineage("participants")(0)("lineageId") = "l1"
    assertEquals(TrustedGameCreateRequestCodec.decode(ujson.write(lineage)).left.toOption
      .map(_.path), Some("$.participants[0].lineageId"))

  test("invalid player ids are refused"):
    // An id holds at most 128 characters, starts with a letter or a digit
    // and has no path or query characters such as "/" or "?".
    Vector("" -> "expected a non-blank string",
      " " -> "expected a non-blank string", "a/b" -> "invalid identifier",
      "a?b" -> "invalid identifier", ("a" * 129) -> "invalid identifier"
    ).foreach { (invalid, message) =>
      assertEquals(TrustedGameCreateRequestCodec.decode(
        json.replace("p1", invalid)),
        Left(InvalidValue("$.participants[0].playerId", message)), invalid)
    }

  test("duplicate and empty participants are refused"):
    val duplicatedParticipants =
      Vector(request.participants.head, request.participants.head)
    Vector(request.copy(participants = Vector.empty),
      request.copy(participants = duplicatedParticipants)).foreach { invalid =>
      assertEquals(TrustedGameCreateRequestCodec.decode(
        TrustedGameCreateRequestCodec.encode(invalid)),
        Left(InvalidValue("$.participants",
          "requires unique player IDs and at least one participant")))
    }

  test("an unknown colour is refused at its path"):
    // A colour outside the closed set is refused where it enters.
    Vector("", "green").foreach { invalid =>
      assertEquals(TrustedGameCreateRequestCodec.decode(
        json.replace("\"red\"", s"\"$invalid\"")).left.toOption.map(_.path),
        Some("$.participants[0].color"))
    }

  test("malformed JSON, a non-object root and a numeric id are refused"):
    Vector(
      "[]" -> ExpectedObject("$"),
      "null" -> ExpectedObject("$"),
      "{" -> MalformedJson("$", "malformed JSON"),
      json.replace("\"p1\"", "12") ->
        InvalidValue("$.participants[0].playerId", "expected a non-blank string")
    ).foreach { (invalid, refusal) =>
      assertEquals(TrustedGameCreateRequestCodec.decode(invalid),
        Left(refusal), invalid)
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
    assertEquals(TrustedGameCreateResponseCodec.decode(ujson.write(extra)),
      Left(UnexpectedField("$.seats[0].code")))

  test("a missing url is refused"):
    val missing = ujson.read(seatResponseJson)
    missing("seats")(0).obj.remove("url")
    assertEquals(TrustedGameCreateResponseCodec.decode(ujson.write(missing)),
      Left(MissingField("$.seats[0].url")))

  test("an extra root field is refused"):
    val extra = ujson.read(seatResponseJson)
    extra.obj("unexpected") = ujson.Bool(true)
    assertEquals(TrustedGameCreateResponseCodec.decode(ujson.write(extra)),
      Left(UnexpectedField("$.unexpected")))
