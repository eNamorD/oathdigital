package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.protocol.projection.StepOfferProjection
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

/** Who is offered the Undo and the Redo, and what they say (undo design,
  * "Controls" and "Projection"). */
class UndoProjectionSuite extends munit.FunSuite:
  private val table = Table.start.pawn(p3, "Buried Giant").banditsRefilled
  private val projector = new GameProjector(catalog)
  /** p1 Travels to where p3 stands; the pick is the batch to offer. */
  private def moved(service: GameApplicationService): GameAccepted =
    ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3).picked
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  test("only the issuer is offered the Undo, named by the batch's first line"):
    val (service, _) = table.service()
    val moved = this.moved(service)
    // The batch opens with the Travel tag ("Travel", 2 Supply); the note
    // skips it for the line that says what changed.
    assertEquals(projector.project("g", moved.loaded, p1).undo,
      Some(StepOfferProjection("Undo: Travelled to Buried Giant")))
    assertEquals(projector.project("g", moved.loaded, p2).undo, None)
    assertEquals(projector.projectPublic("g", moved.loaded).undo, None)

  test("a batch with no line of its own is offered as the last step"):
    val loaded = LoadedGame(table.state, 3L,
      Some(UndoStep(p1, 0L, 3L, None)))
    assertEquals(projector.project("g", loaded, p1).undo,
      Some(StepOfferProjection("Undo your last step")))

  test("every viewer sees the latest taken-back marker as the log revision"):
    val (service, _) = table.service()
    val moved = this.moved(service)
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(projector.project("g", undone.loaded, p2).logRevision,
      Some(moved.nextSequence))
    assertEquals(projector.projectPublic("g", undone.loaded).logRevision,
      Some(moved.nextSequence))

  test("only the issuer is offered the Redo, named as its Undo was"):
    val (service, _) = table.service()
    val moved = this.moved(service)
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(projector.project("g", undone.loaded, p1).redo,
      Some(StepOfferProjection("Redo: Travelled to Buried Giant")))
    assertEquals(projector.project("g", undone.loaded, p2).redo, None)
    assertEquals(projector.projectPublic("g", undone.loaded).redo, None)

  test("a taken-back batch with no line of its own is offered as the last step"):
    val loaded = LoadedGame(table.state, 4L,
      redo = Some(UndoStep(p1, 0L, 3L, None)))
    assertEquals(projector.project("g", loaded, p1).redo,
      Some(StepOfferProjection("Redo your last step")))

  test("a redo moves every viewer's log revision to its marker"):
    val (service, _) = table.service()
    val moved = this.moved(service)
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accept(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(projector.project("g", redone.loaded, p2).logRevision,
      Some(undone.nextSequence))
