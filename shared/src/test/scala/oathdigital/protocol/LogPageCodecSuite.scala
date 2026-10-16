package oathdigital.protocol

import oathdigital.protocol.projection._

class LogPageCodecSuite extends munit.FunSuite:
  private val page = LogPage("game", 4L, 9L, Vector(
    LogEntry(4L, 0, LogKind.Turn, Vector(
      LogSpan.Player("red", "Red"), LogSpan.Text("'s turn"))),
    LogEntry(6L, 1, LogKind.Action, Vector(
      LogSpan.Text("Started Travel"), LogSpan.Cost(2)))))

  test("a page round-trips"):
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(page)), Right(page))

  test("every span case round-trips"):
    val spans = Vector[LogSpan](LogSpan.Text("words"),
      LogSpan.Player("red", "Red"), LogSpan.Card("c1", "Old Oak"),
      LogSpan.Site("s1", "Buried Giant"), LogSpan.Amount(3, "favor"),
      LogSpan.Cost(2),
      LogSpan.Dice(DieFace.OneSword, Vector(DieFace.TwoSwordsSkull)),
      LogSpan.Cards(Vector(
        CardDetailsProjection("19", "denizen", "Scryer", Some("discord")))))
    val all = LogPage("g", 0, 9,
      Vector(LogEntry(7, 0, LogKind.Action, spans)))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(all)), Right(all))

  test("a text span is the whole of its object: a kind and its words"):
    val json = ujson.read(LogPageCodec.encode(LogPage("g", 0, 9,
      Vector(LogEntry(7, 0, LogKind.Turn, Vector(LogSpan.Text("'s turn")))))))
    assertEquals(json("entries")(0)("spans")(0).obj.keySet.toSet,
      Set("kind", "text"))

  test("an entry carries no depth: its kind decides it"):
    val json = ujson.read(LogPageCodec.encode(page))
    assertEquals(json("entries")(0).obj.keySet.toSet,
      Set("sequence", "ordinal", "kind", "spans", "section", "takenBack"))

  test("a span kind this build does not know is an unknown variant"):
    val json = LogPageCodec.encode(page).replace("\"kind\":\"player\"",
      "\"kind\":\"omen\"")
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.UnknownVariant("$.entries[0].spans[0].kind",
        "unknown log span kind 'omen'")))

  test("an entry kind this build does not know is an unknown variant"):
    val json = LogPageCodec.encode(page).replace("\"kind\":\"turn\"",
      "\"kind\":\"interlude\"")
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.UnknownVariant("$.entries[0].kind",
        "unknown log entry kind 'interlude'")))

  test("a field another span case declares is rejected at its path"):
    val json = LogPageCodec.encode(page).replace("\"text\":\"'s turn\"",
      "\"text\":\"'s turn\",\"value\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].spans[1].value"))

  test("an empty card list is rejected: no line builds one"):
    val empty = LogPage("g", 0, 9, Vector(LogEntry(7, 0, LogKind.Action,
      Vector(LogSpan.Cards(Vector.empty)))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(empty)).left.map(
      _.path), Left("$.entries[0].spans[0].cards"))

  test("a section on a headline kind is rejected"):
    val json = LogPageCodec.encode(LogPage("g", 0, 9, Vector(
      LogEntry(6, 0, LogKind.Section, Vector(LogSpan.Text("Search")),
        Some(LogSection(6, "search"))))))
      .replace("\"kind\":\"section\"", "\"kind\":\"turn\"")
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.InvalidValue("$.entries[0].section",
        "only a line can carry a section")))

  test("an entry's section round-trips, and an entry without one writes null"):
    val banded = LogPage("g", 0, 9, Vector(
      LogEntry(6, 0, LogKind.Section, Vector(LogSpan.Text("Search")),
        Some(LogSection(6, "search"))),
      LogEntry(7, 0, LogKind.Turn, Vector(LogSpan.Text("Red's turn")))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(banded)),
      Right(banded))
    val json = ujson.read(LogPageCodec.encode(banded))
    assertEquals(json("entries")(0)("section"),
      ujson.Obj("id" -> ujson.Num(6), "action" -> ujson.Str("search")))
    assertEquals(json("entries")(1)("section"), ujson.Null)

  test("an entry written before sections or groups decodes with none"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":""" +
      """[{"sequence":7,"ordinal":0,"kind":"turn","spans":[]}]}"""
    assertEquals(LogPageCodec.decode(json).map(_.entries.map(entry =>
      entry.section -> entry.takenBack)), Right(Vector(None -> None)))

  test("an entry's taken-back group round-trips with its header"):
    val voided = LogPage("g", 0, 9, Vector(LogEntry(6, 0, LogKind.Action,
      Vector(LogSpan.Text("Travelled")),
      takenBack = Some(LogTakenBack(4L, Vector(
        LogSpan.Player("p1", "p1"),
        LogSpan.Text(" took back the following moves:")))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(voided)),
      Right(voided))

  test("a section with an unexpected field is rejected at its path"):
    val banded = page.copy(entries = Vector(page.entries.head.copy(
      kind = LogKind.Action, section = Some(LogSection(4, "travel")))))
    val json = LogPageCodec.encode(banded).replace("\"action\":\"travel\"",
      "\"action\":\"travel\",\"extra\":1")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].section.extra"))

  test("malformed JSON is a decode failure that names the parser's clue"):
    // ujson wraps its parse error in a trace whose own message is only the
    // JSON path; the clue is the wrapped error's.
    assertEquals(LogPageCodec.decode("{"),
      Left(ProtocolDecodeFailure.MalformedJson("$", "exhausted input")))

  test("a card list span round-trips its faces and backs"):
    val list = LogPage("g", 0, 9, Vector(LogEntry(7, 0, LogKind.Action,
      Vector(LogSpan.Cards(Vector(
        CardDetailsProjection("19", "denizen", "Scryer", Some("discord")),
        CardDetailsProjection("hidden", "vision", "Facedown vision",
          orientation = Some("face-down"), hidden = true)))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(list)), Right(list))

  test("a dice span is its faces: the die and the words come off them"):
    val roll = LogPage("g", 0, 9, Vector(LogEntry(7, 0, LogKind.Roll,
      Vector(LogSpan.Dice(DieFace.OneSword,
        Vector(DieFace.OneSword, DieFace.TwoSwordsSkull))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(roll)), Right(roll))
    val json = ujson.read(LogPageCodec.encode(roll))
    assertEquals(json("entries")(0)("spans")(0),
      ujson.Obj("kind" -> "dice", "faces" -> ujson.Arr("one-sword",
        "one-sword", "two-swords-skull")))

  test("an empty roll is rejected: no line builds one"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":[""" +
      """{"sequence":7,"ordinal":0,"kind":"roll","spans":[""" +
      """{"kind":"dice","faces":[]}]}]}"""
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].spans[0].faces"))

  test("a roll mixing the two dice is rejected"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":[""" +
      """{"sequence":7,"ordinal":0,"kind":"roll","spans":[""" +
      """{"kind":"dice","faces":["one-sword","blank"]}]}]}"""
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.InvalidValue("$.entries[0].spans[0].faces",
        "expected one die's faces")))

  test("a face this build does not know is an unknown variant"):
    val json = """{"gameId":"g","after":0,"nextSequence":9,"entries":[""" +
      """{"sequence":7,"ordinal":0,"kind":"roll","spans":[""" +
      """{"kind":"dice","faces":["three-moons"]}]}]}"""
    assertEquals(LogPageCodec.decode(json),
      Left(ProtocolDecodeFailure.UnknownVariant(
        "$.entries[0].spans[0].faces[0]", "unknown die face 'three-moons'")))

  /** The entry's own `exact` set, the one `depth` came out of: a build that
    * put it back would be caught here and nowhere else. */
  test("a field no entry declares is rejected at its path"):
    val json = LogPageCodec.encode(page).replace("\"ordinal\":0",
      "\"ordinal\":0,\"depth\":0")
    assertEquals(LogPageCodec.decode(json).left.map(_.path),
      Left("$.entries[0].depth"))
