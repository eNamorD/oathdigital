package oathdigital.gameplay.walker

import oathdigital.model.{DecisionAnswer, DecisionOptionRef, DecisionQuery,
  DenizenId, OathViolation, PlayerId}

/** The `Inspect` decision (catalog batch 2, N5): cards to look at and one
  * Done button, checked with hand-built queries as `DecisionQuerySuite`
  * checks the other shapes. */
class InspectQuerySuite extends munit.FunSuite:
  private val decisionId = "power.scryer.inspect"
  private val anyone = PlayerId("player-red")
  private val cards = Vector(DenizenId("1"), DenizenId("2"))
  private val inspect = DecisionQuery.Inspect(cards,
    heading = Some("Scryer: the Cradle discard pile"))

  private def wellFormed(query: DecisionQuery) =
    DecisionQueries.wellFormed(decisionId, query)
  private def accepts(answer: DecisionAnswer) =
    DecisionQueries.accepts(decisionId, inspect, answer, anyone)
  private def invalid(detail: String): Either[OathViolation, Unit] =
    Left(OathViolation.InvalidEventOrder(s"decision $decisionId $detail"))

  test("an inspect query with cards, a heading and a done label is well formed"):
    assertEquals(wellFormed(inspect), Right(()))

  test("an inspect query with no cards, a repeated card or blank copy is malformed"):
    assertEquals(wellFormed(inspect.copy(cards = Vector.empty)),
      invalid("declares no cards"))
    assertEquals(wellFormed(inspect.copy(cards = cards :+ cards.head)),
      invalid("declares duplicate cards"))
    assertEquals(wellFormed(inspect.copy(heading = None)),
      invalid("declares no heading"))
    assertEquals(wellFormed(inspect.copy(doneLabel = " ")),
      invalid("declares a blank done label"))

  test("its one answer is its Done button"):
    assertEquals(accepts(
      DecisionAnswer.ChooseOneAnswer(DecisionQuery.Inspect.Done)), Right(()))

  test("any other answer is refused"):
    assertEquals(accepts(DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("stop"))), invalid("expects its Done button"))
    assertEquals(accepts(DecisionAnswer.ChooseAmountAnswer(1)),
      invalid("expects its Done button"))
