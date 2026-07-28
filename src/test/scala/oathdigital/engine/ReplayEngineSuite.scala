package oathdigital.engine

private object ReplayEngineSuite {
  final case class Add(amount: Int) extends Action
}

class ReplayEngineSuite extends munit.FunSuite {
  import ReplayEngineSuite.Add

  private val addingRules = new Rules[Int] {
    override val initialState: Int = 0

    override def transition(
        state: Int,
        action: Action
    ): Either[RuleViolation, Transition[Int]] =
      action match {
        case Add(amount) =>
          Right(Transition(state + amount, Continue.Finished(Set.empty)))
        case other => Left(RuleViolation(s"unexpected action: $other"))
      }
  }

  test("replaying the same action stream reconstructs the same state") {
    val actions = Vector(
      RecordedAction(0, Add(2)),
      RecordedAction(1, Add(3)),
      RecordedAction(2, Add(-1))
    )
    val engine = new ReplayEngine(addingRules)

    val firstReplay = engine.replay(actions)
    val secondReplay = engine.replay(actions)

    assertEquals(firstReplay, Right(4))
    assertEquals(secondReplay, firstReplay)
  }

  test("replay starts from the rules' initial state") {
    val engine = new ReplayEngine(addingRules)

    assertEquals(engine.replay(Vector.empty), Right(0))
  }
}
