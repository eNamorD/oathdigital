package oathdigital.gameplay.powers

import oathdigital.model._
import oathdigital.testkit.Table.p1

class PowerAnswersSuite extends munit.FunSuite:
  private def chose(decision: String, key: String): Answered =
    Answered(decision, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button(key)), p1)

  /** A loop asked "a" twice, with "b" between. */
  private val looped = Vector(chose("a", "first"), chose("b", "between"),
    chose("a", "second"))

  test("last reads a loop's latest answer, where one reads its first"):
    assertEquals(PowerAnswers.one(PendingTree(Vector("0"), looped), "a"),
      Some(DecisionOptionRef.Button("first")))
    assertEquals(PowerAnswers.last(looped, "a"),
      Some(DecisionOptionRef.Button("second")))

  test("last over several decisions reads whichever was answered last, and " +
      "nothing when none was"):
    assertEquals(PowerAnswers.last(looped.init, "a", "b"),
      Some(DecisionOptionRef.Button("between")))
    assertEquals(PowerAnswers.last(looped, "c"), None)
