package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay.walker.PowerNoted

/** Power notes on the wire: every argument kind and source round trips, and a
  * note with a bad source, unit or amount is refused. */
class PowerNoteWireSuite extends munit.FunSuite:
  private def noteEvent(source: PowerSourceRef, args: NoteArg*): PowerNoted =
    PowerNoted(PowerId("test.power"), PowerNote(source, "used", args.toVector),
      covers = true)

  test("a power note round trips with every argument kind"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("93")),
      NoteArg.Player(PlayerId("p1")), NoteArg.Card(RelicId("r1")),
      NoteArg.Site(SiteId("s1")), NoteArg.Amount(3, NoteUnit.Favor),
      NoteArg.Amount(1, NoteUnit.Warband), NoteArg.Number(8),
      NoteArg.Bank(Suit.all.head),
      NoteArg.Dice(Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank)),
      NoteArg.Dice(Vector(AttackDieFace.OneSword)))
    val encoded = GameEventWire.encodeEvent("notes", 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("a power note's site and banner sources round trip"):
    Vector(noteEvent(PowerSourceRef.Site(SiteId("s1"))),
        noteEvent(PowerSourceRef.Banner(Banner.PeoplesFavor))).foreach { event =>
      val encoded = GameEventWire.encodeEvent("notes", 0, event)
        .toOption.get
      assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))
    }

  test("a power note's card list and banner arguments round trip"):
    val event = noteEvent(PowerSourceRef.Card(DenizenId("93")),
      NoteArg.Cards(Vector(DenizenId("12"), RelicId("r1"))),
      NoteArg.Cards(Vector.empty), NoteArg.Banner(Banner.PeoplesFavor),
      NoteArg.Pile(SearchSource.WorldDeck),
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Provinces)))
    val encoded = GameEventWire.encodeEvent("notes", 0, event)
      .toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("an unknown note argument is refused"):
    val event = noteEvent(PowerSourceRef.Site(SiteId("s1")), NoteArg.Number(1))
    val encoded = ujson.read(GameEventWire.encodeEvent("notes", 0,
      event).toOption.get)
    encoded("payload")("note")("args")(0)("kind") = "colour"
    assert(GameEventWire.decode(encoded).isLeft)

  test("a note with an unknown source, an unknown unit or a negative amount is refused"):
    def refused(edit: ujson.Value => Unit): Boolean =
      val event = noteEvent(PowerSourceRef.Site(SiteId("s1")),
        NoteArg.Amount(2, NoteUnit.Favor))
      val encoded = ujson.read(GameEventWire.encodeEvent("notes", 0,
        event).toOption.get)
      edit(encoded("payload")("note"))
      GameEventWire.decode(encoded).isLeft
    assert(refused(note => note("source")("kind") = "moon"))
    assert(refused(note => note("args")(0)("unit") = "gold"))
    assert(refused(note => note("args")(0)("value") = -1))
