package oathdigital.application.gamelog

import oathdigital.gameplay.powers.action.WardOfSilence
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.{atTable, format, playerLabel, texts}

/** How Ward of Silence reads in the Game Log: its line is the action's own,
  * and neither of its questions posts a "Chose" line. */
class WardOfSilenceLogSuite extends munit.FunSuite:
  private val ward = CatalogNames.denizen("Ward of Silence")

  /** p1, holding Ward of Silence and 3 faceup secrets at Ancient City, flips
    * 2 of p2's 2 faceup secrets there facedown. The log as p3 sees it. */
  private def wardFlippedTwoOfP2sSecrets: Vector[String] =
    val table = Table.start.pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .pawn(p3, "Buried Giant").adviser(p1, ward).secrets(p1, faceUp = 3)
      .secrets(p2, faceUp = 2).banditsRefilled
    val (service, act) = atTable("ward-of-silence", table)
    act.withAnswers {
      case park if park.decisionId == WardOfSilence.targetDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Player(p2))
      case park if park.decisionId == WardOfSilence.amountDecisionId =>
        ChooseAmountAnswer(2)
    }.after(MenuPlay.start(p1, ActionRef.UsePower(WardOfSilence.id),
      Vector(DecisionOptionRef.Denizen(ward))))
    texts(format(Script("ward-of-silence", service, p1), Some(p3)))

  test("its line is the action's own and names the target, with no Chose " +
      "line"):
    val log = wardFlippedTwoOfP2sSecrets
    assert(log.contains(s"Ward of Silence: Flipped 2 secrets on " +
      s"${playerLabel(p2)}'s board facedown."), log.mkString("\n"))
    assert(!log.exists(_.startsWith("Chose ")), log.mkString("\n"))
