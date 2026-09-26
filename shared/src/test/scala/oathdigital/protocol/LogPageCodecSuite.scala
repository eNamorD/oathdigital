package oathdigital.protocol

import oathdigital.protocol.projection._

class LogPageCodecSuite extends munit.FunSuite:
  private val page = LogPageWire("game", 4L, 9L, Vector(
    LogEntryWire(4L, 0, "turn", 0, Vector(
      LogSpanWire("player", "Red", id = Some("red")),
      LogSpanWire("text", "'s turn"))),
    LogEntryWire(6L, 1, "action", 1, Vector(
      LogSpanWire("text", "Started Travel"),
      LogSpanWire("cost", "−2 Supply", value = Some(2),
        unit = Some("Supply"))))))

  test("a page round-trips"):
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(page)), Right(page))

  test("a text span carries no id, value or unit on the wire"):
    val json = ujson.read(LogPageCodec.encode(page))
    val text = json("entries")(0)("spans")(1).obj
    assertEquals(text.keySet.toSet, Set("kind", "text"))

  test("an unexpected field is rejected at its path"):
    val json = LogPageCodec.encode(page).replace("\"ordinal\":0",
      "\"ordinal\":0,\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].extra"))

  test("malformed JSON is a decode failure, not an exception"):
    assert(LogPageCodec.decode("{").isLeft)
