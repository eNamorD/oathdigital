package oathdigital.engine

private object RuleViolationSuite {
  final case class Add(amount: Int) extends Action
}

class RuleViolationSuite extends munit.FunSuite {
  import RuleViolationSuite.Add

  test("replay reports the failing record index and original rule violation") {
    val violation = RuleViolation("total cannot exceed five")
    var transitionCount = 0
    val rules = new Rules[Int] {
      override val initialState: Int = 0

      override def transition(
          state: Int,
          action: Action
      ): Either[RuleViolation, Transition[Int]] = {
        transitionCount += 1
        action match {
          case Add(amount) if state + amount > 5 => Left(violation)
          case Add(amount) =>
            Right(Transition(state + amount, Continue.Finished(Set.empty)))
          case other => Left(RuleViolation(s"unexpected action: $other"))
        }
      }
    }
    val actions = Vector(
      RecordedAction(0, Add(3)),
      RecordedAction(1, Add(4)),
      RecordedAction(2, Add(-2))
    )

    val result = new ReplayEngine(rules).replay(actions)

    assertEquals(result, Left(ReplayFailure(1, violation)))
    assertEquals(transitionCount, 2)
  }
}
