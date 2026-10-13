package oathdigital.application.gamelog

import oathdigital.gameplay.powers.OathkeeperGoalChoice
import oathdigital.gameplay.powers.action.TheRedSeer
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, texts}

/** How The Red Seer reads in the Game Log: its line names the new goal, its
  * question posts no "Chose" line, and the title follows the new goal when
  * the action ends. */
class TheRedSeerLogSuite extends munit.FunSuite:
  private val seer = CatalogNames.denizen("The Red Seer")

  /** p1 holds The Red Seer, the Darkest Secret, 3 faceup secrets and the
    * Oathkeeper title, and rules Ancient City under Supremacy; p2 holds
    * Lost Tapestry. p1 changes the goal to Protection, which p2 leads. The
    * log as p3 sees it. */
  private def seerChangedTheGoalToProtection: Vector[String] =
    val table = Table.start.pawn(p1, "Ancient City")
      .pawn(p2, "Buried Giant").pawn(p3, "Buried Giant")
      .adviser(p1, seer).secrets(p1, faceUp = 3)
      .darkestSecret(Some(p1), 1).oathkeeper(Some(p1))
      .oathkeeperGoal(OathkeeperGoal.Supremacy)
      .warbandsAt("Ancient City", p1, 1).relic(p2, "Lost Tapestry")
      .banditsRefilled
    val (service, act) = atTable("the-red-seer", table)
    act.withAnswers {
      case park if park.decisionId == TheRedSeer.decisionId =>
        ChooseOneAnswer(OathkeeperGoalChoice.ref(OathkeeperGoal.Protection))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(TheRedSeer.id),
      Vector(DecisionOptionRef.Denizen(seer))))
    texts(format(Script("the-red-seer", service, p1), Some(p3)))

  test("its line names the new goal, with no Chose line, and the title " +
      "passes to the new goal's leader"):
    val log = seerChangedTheGoalToProtection
    assert(log.contains("The Red Seer: Changed the Oathkeeper goal to " +
      "Protection."), log.mkString("\n"))
    assert(!log.exists(_.startsWith("Chose ")), log.mkString("\n"))
    assert(log.contains(s"Oathkeeper passed to ${playerLabel(p2)}"),
      log.mkString("\n"))
