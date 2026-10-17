package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay.walker.PowerNoted
import oathdigital.testkit.Unwrap.accepted

/** Power notes on the wire: every argument kind and source round trips, and a
  * note with a bad source, unit or amount is refused. */
class PowerNoteWireSuite extends munit.FunSuite:
  private def noteEvent(source: PowerSourceRef, args: NoteArg*): PowerNoted =
    PowerNoted(PowerId("test.power"),
      PowerNote(Some(source), "used", args.toVector), covers = true)

  test("a power note round trips with every argument kind"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("denizen")),
      NoteArg.Player(PlayerId("p1")), NoteArg.Card(RelicId("r1")),
      NoteArg.Site(SiteId("s1")), NoteArg.Amount(3, NoteUnit.Favor),
      NoteArg.Amount(1, NoteUnit.Warband), NoteArg.Number(8),
      NoteArg.Bank(Suit.all.head),
      NoteArg.Dice(Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank)),
      NoteArg.Dice(Vector(AttackDieFace.OneSword)))
    val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("a power note's site and banner sources round trip"):
    Vector(noteEvent(PowerSourceRef.Site(SiteId("s1"))),
        noteEvent(PowerSourceRef.Banner(Banner.PeoplesFavor))).foreach { event =>
      val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }

  test("a power note's card list and banner arguments round trip"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("denizen")),
      NoteArg.Cards(Vector(DenizenId("other-denizen"), RelicId("r1"))),
      NoteArg.Cards(Vector.empty), NoteArg.Banner(Banner.PeoplesFavor),
      NoteArg.Pile(SearchSource.WorldDeck),
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Provinces)),
      NoteArg.RegionName(Region.Hinterland), NoteArg.SuitName(Suit.Nomad),
      NoteArg.Goal(OathkeeperGoal.ThePeople))
    val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("an unknown note argument is refused"):
    val event = noteEvent(PowerSourceRef.Site(SiteId("s1")), NoteArg.Number(1))
    val encoded = ujson.read(accepted(
      GameEventWire.encodeEvent("notes", 0, event)))
    encoded("payload")("note")("args")(0)("kind") = "colour"
    assertEquals(GameEventWire.decode(encoded), Left(WireError.InvalidValue(
      "$.payload.note.args[0].kind", "unknown note argument 'colour'")))

  test("a note with an unknown source, an unknown unit or a negative " +
      "amount is refused"):
    def assertRefused(label: String, edit: ujson.Value => Unit,
        refusal: WireError): Unit =
      val event = noteEvent(PowerSourceRef.Site(SiteId("s1")),
        NoteArg.Amount(2, NoteUnit.Favor))
      val encoded = ujson.read(accepted(
        GameEventWire.encodeEvent("notes", 0, event)))
      edit(encoded("payload")("note"))
      val decoded = GameEventWire.decode(encoded)
      assertEquals(decoded, Left(refusal), label)
    assertRefused("an unknown source kind",
      note => note("source")("kind") = "moon",
      WireError.InvalidValue("$.payload.note.source.kind",
        "unknown note source 'moon'"))
    assertRefused("an unknown unit", note => note("args")(0)("unit") = "gold",
      WireError.InvalidValue("$.payload.note.args[0].unit", "unknown note unit"))
    assertRefused("a negative amount",
      note => note("args")(0)("value") = -1,
      WireError.InvalidValue("$.payload.note.args[0].value",
        "must not be negative"))

  test("a note with no source round trips and writes no source field"):
    val event = PowerNoted(PowerId("procedure.muster"),
      PowerNote(None, "mustered", Vector(NoteArg.Number(2))), covers = false)
    val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assert(!ujson.read(encoded)("payload")("note").obj.contains("source"),
      s"a sourceless note writes no source field: $encoded")

  test("a note recorded with a source still decodes as one"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("denizen")))
    val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    assertEquals(event.note.source,
      Some(PowerSourceRef.Card(DenizenId("denizen"))))

  test("the players, clauses and placements arguments round trip"):
    val event = noteEvent(PowerSourceRef.Site(SiteId("s1")),
      NoteArg.Players(Vector(PlayerId("p1"), PlayerId("p2"))),
      NoteArg.Players(Vector.empty),
      NoteArg.Clauses(Vector(NoteClause.LostWarbands(2),
        NoteClause.BurnedFavor(1),
        NoteClause.Discarded(Vector(DenizenId("denizen"), RelicId("r1"))),
        NoteClause.SetAside(Vector(RelicId("r2"))),
        NoteClause.SentTo(SiteId("s2")))),
      NoteArg.Placements(Vector(2 -> SiteId("s1"), 1 -> SiteId("s2"))))
    val encoded = accepted(GameEventWire.encodeEvent("notes", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("an unknown note clause is refused"):
    val event = noteEvent(PowerSourceRef.Site(SiteId("s1")),
      NoteArg.Clauses(Vector(NoteClause.BurnedFavor(1))))
    val encoded = ujson.read(accepted(
      GameEventWire.encodeEvent("notes", 0, event)))
    encoded("payload")("note")("args")(0)("clauses")(0)("kind") = "weather"
    assertEquals(GameEventWire.decode(encoded), Left(WireError.InvalidValue(
      "$.payload.note.args[0].clauses[0].kind", "unknown note clause 'weather'")))

  test("a negative clause or placement count is refused"):
    def refused(arg: NoteArg, edit: ujson.Value => Unit,
        path: String): Unit =
      val encoded = ujson.read(accepted(GameEventWire.encodeEvent("notes", 0,
        noteEvent(PowerSourceRef.Site(SiteId("s1")), arg))))
      edit(encoded("payload")("note")("args")(0))
      assertEquals(GameEventWire.decode(encoded), Left(
        WireError.InvalidValue(path, "must not be negative")))
    refused(NoteArg.Clauses(Vector(NoteClause.LostWarbands(2))),
      arg => arg("clauses")(0)("count") = -1,
      "$.payload.note.args[0].clauses[0].count")
    refused(NoteArg.Placements(Vector(2 -> SiteId("s1"))),
      arg => arg("placements")(0)("count") = -1,
      "$.payload.note.args[0].placements[0].count")
