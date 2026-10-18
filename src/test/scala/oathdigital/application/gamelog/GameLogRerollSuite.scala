package oathdigital.application.gamelog

import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.walker.{ChoicePayload, RollPayload,
  WalkerCompleted, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.CatalogNames
import LogScripts._

/** The lines of a reroll: the roll as rolled, then the throw restated after
  * each reroll, and nothing for the reroll decision (catalog batch 7,
  * slice 1). */
class GameLogRerollSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, TwoShields}

  private val pool = RecoverProcedure.recoverPool
  private val decision = RerollChoice.decisionId(pool, 1)
  private val jinx = DecisionOptionRef.Denizen(CatalogNames.denizen("Jinx"))

  /** The state the appended steps start from: the Recover pool's two dice
    * show two blanks, so the reroll applies to them. */
  private def blanksRolled(state: OathState): OathState = state match
    case OathState.Ready(ready) => OathState.Ready(ready.updateCurrent(
      current => current.copy(
        rollPools = current.rollPools.updated(pool, DicePoolState(2)),
        rollOutcomes = current.rollOutcomes.updated(pool, RollOutcome(pool,
          2, Vector(Blank, Blank), 0, 0)))))
    case other => other

  private def rolled(by: PlayerId, answer: DecisionOptionRef)
      : Vector[OathEvent] = Vector(
    WalkerStepRecorded("roll", RollPayload(pool, Vector(Blank, Blank),
      automatic = true), Vector.empty, Vector.empty),
    WalkerStepRecorded("pick", ChoicePayload(decision,
      ChooseOneAnswer(answer), by), Vector.empty, Vector.empty))

  private def rerollStep: OathEvent = WalkerStepRecorded("reroll",
    WalkerStepPayload.DeltaRecorded,
    Vector(Reroll(pool, Vector(0, 1), Some(Vector(TwoShields, TwoShields)))),
    Vector.empty)

  test("a reroll restates the throw, and its decision writes no line"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    val shown = texts(formatAppended(script,
      rolled(script.actor, jinx) :+ rerollStep :+
        WalkerCompleted(ActionRef.Recover),
      stateBefore = blanksRolled).filter(_.sequence >= steps.size))
    val rolls = shown.filter(_.startsWith("Rolled "))
    assertEquals(rolls.size, 2, shown)
    assertNotEquals(rolls(0), rolls(1))
    assert(!shown.exists(_.toLowerCase.contains("chose")), shown)

  test("keeping the roll writes no line"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    val shown = texts(formatAppended(script,
      rolled(script.actor, RerollChoice.keep) :+
        WalkerCompleted(ActionRef.Recover)).filter(_.sequence >= steps.size))
    assertEquals(shown.count(_.startsWith("Rolled ")), 1, shown)
    assert(!shown.exists(_.contains("Keep the roll")), shown)
