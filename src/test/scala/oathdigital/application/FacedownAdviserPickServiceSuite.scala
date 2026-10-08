package oathdigital.application

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.protocol.projection.{DecisionQueryProjection,
  WalkerWaitingProjection}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** The facedown-adviser pick through the service: a board decision only its
  * player sees (undo design, "The facedown-adviser pick"). Undo of the pick
  * is covered by `UndoServiceSuite`. */
class FacedownAdviserPickServiceSuite extends munit.FunSuite:
  private val conclave = CatalogNames.denizen("Wizard's Conclave")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val table = Table.start.adviser(p1, conclave, facedown = true)
    .adviser(p1, wrestlers, facedown = true).banditsRefilled
  test("the parked pick is a board decision naming each adviser to its " +
      "player only"):
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.playFacedownAdviser(p1)))
    val projector = new GameProjector(catalog)
    val own = projector.project("g", started.loaded, p1).walkerDecision
    assertEquals(own.map(decision => (decision.action, decision.decisionId,
      decision.presentation)), Some(("play-facedown-adviser",
      CardPlayProcedure.facedownAdviserDecisionId, "board")))
    val options = own.flatMap(_.query).collect {
      case one: DecisionQueryProjection.ChooseOne => one.options
    }.getOrElse(Vector.empty)
    assertEquals(options.map(option => (option.kind, option.id,
      option.card.exists(!_.hidden))).toSet, Set(
      ("denizen", conclave.value, true), ("denizen", wrestlers.value, true)))
    val other = projector.project("g", started.loaded, p2)
    assertEquals(other.walkerDecision, None)
    // p2 cannot identify the facedown advisers, yet still learns whom the
    // table waits on and what p1 is asked.
    assertEquals(other.walkerWaiting, Some(WalkerWaitingProjection(p1.value,
      Some("Choose a facedown adviser to play"))))
