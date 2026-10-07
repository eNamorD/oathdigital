package oathdigital.application

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The facedown-adviser pick through the service: a board decision only its
  * player sees, and Undo in place of the old Cancel (undo design, "The
  * facedown-adviser pick"). */
class FacedownAdviserPickServiceSuite extends munit.FunSuite:
  private val conclave = CatalogNames.denizen("Wizard's Conclave")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val table = Table.start.adviser(p1, conclave, facedown = true)
    .adviser(p1, wrestlers, facedown = true).banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  test("Undo takes back the start, leaving the table as it was"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.playFacedownAdviser(p1)))
    val undone = accept(service.handle("g", started.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, table.state)

  test("Undo after the pick returns to the pick, and the start stays undoable"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
      ParkedServiceFixture.playFacedownAdviser(p1)))
    val picked = accept(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickFacedownAdviser(p1, wrestlers)))
    val undone = accept(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, started.state)
    assert(undone.undo.nonEmpty, "the start can still be taken back")

  test("the parked pick is a board decision naming each adviser to its " +
      "player only"):
    val (service, _) = table.service()
    val started = accept(service.handle("g", 0L,
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
    // p2 cannot identify the facedown advisers, and the projector omits a
    // decision whose available option it cannot present, the waiting notice
    // included.
    assertEquals(other.walkerWaiting, None)
