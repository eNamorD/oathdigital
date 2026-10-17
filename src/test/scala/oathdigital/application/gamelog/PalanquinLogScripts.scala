package oathdigital.application.gamelog

import oathdigital.gameplay.powers.action.{Palanquin, PalanquinCard}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2}
import LogScripts.atTable

/** Journaled Palanquin uses, for the Game Log suites (catalog batch 8,
  * slice 15). Kept beside `LogScripts`, which is at its size limit. */
object PalanquinLogScripts:
  /** p1 holds Palanquin and 1 favor at Ancient City, where p2 stands too;
    * p1 sends p2 to Buried Giant. */
  def palanquinSendsP2ToBuriedGiant(using munit.Location): Script =
    val table = Table.start.pawn(p2, "Ancient City")
      .adviser(p1, PalanquinCard.id).favor(p1, 1).banditsRefilled
    val (service, act) = atTable("palanquin", table)
    act.withAnswers {
      case park if park.decisionId == Palanquin.targetDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Player(p2))
      case park if park.decisionId == Palanquin.destinationDecisionId =>
        ChooseOneAnswer(DecisionOptionRef.Site(
          CatalogNames.site("Buried Giant")))
    }.after(MenuPlay.start(p1, ActionRef.UsePower(Palanquin.id),
      Vector(DecisionOptionRef.Denizen(PalanquinCard.id))))
    Script("palanquin", service, p1)
