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

  test("a dice span round-trips with its faces and die"):
    val dice = LogPageWire("g", 0, 9, Vector(LogEntryWire(7, 0, "roll", 1,
      Vector(LogSpanWire("text", "Rolled "), LogSpanWire("dice",
        "sword, two swords and a skull", id = Some("one-sword two-swords-skull"),
        unit = Some("attack"))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(dice)), Right(dice))

  test("a text span carries no id, value or unit on the wire"):
    val oneSpan = LogPageWire("g", 0, 9, Vector(LogEntryWire(7, 0, "turn", 0,
      Vector(LogSpanWire("text", "'s turn")))))
    val json = ujson.read(LogPageCodec.encode(oneSpan))
    val text = json("entries")(0)("spans")(0).obj
    assertEquals(text.keySet.toSet, Set("kind", "text"))

  test("an unexpected field is rejected at its path"):
    // Entry 0 has ordinal 0 and entry 1 has ordinal 1, so the replace hits
    // only entry 0 and the path is entries[0].
    val json = LogPageCodec.encode(page).replace("\"ordinal\":0",
      "\"ordinal\":0,\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].extra"))

  test("an entry's section round-trips, and an entry without one writes null"):
    val banded = LogPageWire("g", 0, 9, Vector(
      LogEntryWire(6, 0, "section", 1, Vector(LogSpanWire("text", "Search")),
        Some(LogSectionWire(6, "search"))),
      LogEntryWire(7, 0, "turn", 0, Vector(LogSpanWire("text", "Red's turn")))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(banded)), Right(banded))
    val json = ujson.read(LogPageCodec.encode(banded))
    assertEquals(json("entries")(0)("section"),
      ujson.Obj("id" -> ujson.Num(6), "action" -> ujson.Str("search")))
    assertEquals(json("entries")(1)("section"), ujson.Null)

  test("an entry written before sections decodes with none"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":""" +
      """[{"sequence":7,"ordinal":0,"kind":"turn","depth":0,"spans":[]}]}"""
    assertEquals(LogPageCodec.decode(json).map(_.entries.map(_.section)),
      Right(Vector(None)))

  test("a section with an unexpected field is rejected at its path"):
    val banded = page.copy(entries = Vector(page.entries.head.copy(
      section = Some(LogSectionWire(4, "travel")))))
    val json = LogPageCodec.encode(banded).replace("\"action\":\"travel\"",
      "\"action\":\"travel\",\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].section.extra"))

  test("malformed JSON is a decode failure, not an exception"):
    assert(LogPageCodec.decode("{").isLeft)

  test("a card list span round-trips its faces and backs"):
    val list = LogPageWire("g", 0, 9, Vector(LogEntryWire(7, 0, "action", 1,
      Vector(LogSpanWire("cards", "6 cards", cards = Vector(
        CardDetailsProjection("19", "denizen", "Scryer", Some("discord")),
        CardDetailsProjection("hidden", "vision", "Facedown vision",
          orientation = Some("face-down"), hidden = true)))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(list)), Right(list))
