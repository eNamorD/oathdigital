package oathdigital.application.gamelog

import oathdigital.application.ParkedServiceFixture
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.recover.SilverCharm
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}
import oathdigital.protocol.projection.{LogKind}
import oathdigital.testkit.{Answers, Dice, MenuPlay}
import oathdigital.testkit.Table.p1
import LogScripts._

/** Silver Charm's lines: its own line in place of the roll's, then the
  * throw restated with the rerolled earlier die, and nothing for the
  * decision (catalog batch 7, slice 2). */
class GameLogSilverCharmSuite extends munit.FunSuite:
  private val restated = "Rolled two shields, two shields, blank"

  /** p1 recovers at Broken Peaks, difficulty 4, holding Silver Charm. The
    * first roll shows a blank and a shield. On the second payment Silver
    * Charm rerolls the blank, which comes up two shields, beside two new
    * dice that show two shields and a blank: 5 in all. Silver Charm is the
    * only modifier, so its pick starts the Recover by itself; the relic
    * choice is answered by default. It lives here, not in `LogScripts`,
    * which is at the test files' line limit. */
  private def recoverSilverCharm(using munit.Location): Script =
    val (service, act) = atTable("recover-silver-charm",
      ParkedServiceFixture.recoverTable.relic(p1, "Silver Charm")
        .banditsRefilled,
      new Dice.ScriptedDefense(Vector(
        Vector(DefenseDieFace.Blank, DefenseDieFace.OneShield),
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.Blank),
        Vector(DefenseDieFace.TwoShields))))
    val continuingThenRerollingTheFirstDie: Answers = {
      case park if park.decisionId == RecoverProcedure.choiceDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))
      case park if park.decisionId == SilverCharm.decisionId =>
        ChooseManyAnswer(Vector(SilverCharm.die(0)))
    }
    act.withAnswers(ModifierPlay.answers(
        PowerWindow.RecoverModifierSelection, Vector(SilverCharm.id))
        .orElse(continuingThenRerollingTheFirstDie))
      .after(MenuPlay.start(p1, ActionRef.Recover))
    Script("recover-silver-charm", service, p1)

  test("Silver Charm's line replaces the roll's, and the throw restated " +
      "names the rerolled earlier die"):
    val entries = format(recoverSilverCharm, None)
    val shown = texts(entries)
    // The first roll's line stands; the second roll's is covered.
    assertEquals(entries.filter(_.kind == LogKind.Roll).map(text),
      Vector("Rolled blank, shield", restated), shown.toString)
    val line = shown.indexOf("Silver Charm: Rerolled blank.")
    assert(line >= 0, shown.toString)
    assert(line < shown.indexOf(restated), shown.toString)

  test("the choice of dice writes no line of its own"):
    val shown = texts(format(recoverSilverCharm, None))
    assert(!shown.exists(_.contains("Die ")), shown.toString)
