package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.protocol.projection.LogSpan
import LogSpan.Text

class LogWordsSuite extends munit.FunSuite:
  test("a card's back names its kind"):
    assertEquals(LogWords.backOf(VisionId("vision:vision-of-faith")), "Vision")
    assertEquals(LogWords.backOf(RelicId("relic:x")), "Relic")
    assertEquals(LogWords.backOf(EdificeId("edifice:x")), "Edifice")
    assertEquals(LogWords.backOf(LegacyId("legacy:x")), "Legacy")
    assertEquals(LogWords.backOf(DenizenId("denizen:x")), "Denizen")

  test("a kind's plural follows its spelling"):
    assertEquals(LogWords.plural("Legacy"), "Legacies")
    assertEquals(LogWords.plural("Relic"), "Relics")

  test("items join with commas and a final 'and'"):
    def items(names: String*) = names.toVector.map(name => Vector[LogSpan](Text(name)))
    def joined(names: String*) = LogWords.join(items(names*)).map(_.text).mkString
    assertEquals(joined(), "")
    assertEquals(joined("A"), "A")
    assertEquals(joined("A", "B"), "A and B")
    assertEquals(joined("A", "B", "C"), "A, B and C")

  test("a cost names the one currency an action spends"):
    assertEquals(LogSpan.Cost(2).text, "−2 Supply")

  test("a roll is its faces; a mixed or empty pool is no span"):
    assertEquals(LogWords.dice(Vector(AttackDieFace.OneSword,
      AttackDieFace.TwoSwordsSkull)).map(_.text),
      Some("sword, two swords and a skull"))
    assertEquals(LogWords.dice(Vector(DefenseDieFace.OneShield))
      .map(_.die), Some(oathdigital.protocol.projection.Die.Defense))
    assertEquals(LogWords.dice(Vector(AttackDieFace.OneSword,
      DefenseDieFace.Blank)), None)
    assertEquals(LogWords.dice(Vector.empty), None)
