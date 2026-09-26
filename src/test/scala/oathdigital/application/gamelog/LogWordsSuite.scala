package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.LogSpanWire
import LogSpan.Text

class LogWordsSuite extends munit.FunSuite:
  test("a card's back names its kind"):
    assertEquals(LogWords.backOf(VisionId("vision:vision-of-faith")), "Vision")
    assertEquals(LogWords.backOf(RelicId("relic:x")), "Relic")
    assertEquals(LogWords.backOf(EdificeId("edifice:x")), "Edifice")
    assertEquals(LogWords.backOf(LegacyId("legacy:x")), "Legacy")
    assertEquals(LogWords.backOf(DenizenId("denizen:x")), "Denizen")
    assertEquals(LogWords.plural("Legacy"), "Legacies")
    assertEquals(LogWords.plural("Relic"), "Relics")

  test("items join with commas and a final 'and'"):
    def items(names: String*) = names.toVector.map(name => Vector[LogSpan](Text(name)))
    def joined(names: String*) = LogWords.join(items(names*)).map(_.text).mkString
    assertEquals(joined(), "")
    assertEquals(joined("A"), "A")
    assertEquals(joined("A", "B"), "A and B")
    assertEquals(joined("A", "B", "C"), "A, B and C")

  test("every span kind has its wire form; only typed spans carry ids and amounts"):
    assertEquals(GameLogProjector.span(Text("x")), LogSpanWire("text", "x"))
    assertEquals(GameLogProjector.span(LogSpan.Player("red", "Red")),
      LogSpanWire("player", "Red", id = Some("red")))
    assertEquals(GameLogProjector.span(LogSpan.Card("c", "Card")),
      LogSpanWire("card", "Card", id = Some("c")))
    assertEquals(GameLogProjector.span(LogSpan.Site("s", "Site")),
      LogSpanWire("site", "Site", id = Some("s")))
    assertEquals(GameLogProjector.span(LogSpan.Amount(3, "favor")),
      LogSpanWire("amount", "3 favor", value = Some(3), unit = Some("favor")))
    assertEquals(GameLogProjector.span(LogSpan.Cost(2, "Supply")),
      LogSpanWire("cost", "−2 Supply", value = Some(2), unit = Some("Supply")))
