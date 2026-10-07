package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Situation, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Real batches that undo never takes back (undo design, "Barriers"). */
class UndoBarrierPlaySuite extends munit.FunSuite:
  private def nothingToUndo(service: GameApplicationService, gameId: String,
      at: Long, player: PlayerId)(using munit.Location): Unit =
    assertEquals(service.handle(gameId, at, GameCommand.Undo(player)),
      Left(GameApplicationError.CommandRejected(OathViolation.NothingToUndo)))

  test("a Recover's start rolls, so it cannot be taken back"):
    val park = ParkedServiceFixture.recoverChoicePark("recover")
    nothingToUndo(park.game.service, "recover",
      park.game.accepted.nextSequence, park.actor)

  test("a Search's start draws, so it cannot be taken back"):
    val table = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .banditsRefilled
    val (service, repository) = table.service()
    val act = table.situation(Situation.journaled(service, catalog,
      repository, "search"))
    val searched = act.parkedAfter(GameCommand.StartWalker(ActionRef.Search,
      StartPayload(p1, Vector.empty,
        Vector(DecisionOptionRef.Button("search:world")))))
    nothingToUndo(service, "search", searched.nextSequence, p1)

  test("peeking at a site's relics cannot be taken back"):
    val table = ParkedServiceFixture.recoverTable.banditsRefilled
    val (service, repository) = table.service()
    val act = table.situation(Situation.journaled(service, catalog,
      repository, "peek"))
    val peeked = act.after(GameCommand.PeekSiteRelics(p1))
    nothingToUndo(service, "peek", peeked.nextSequence, p1)

  test("finishing Rest hands the turn over, so neither player can undo"):
    val table = Table.start.banditsRefilled
    val (service, repository) = table.service()
    val act = table.situation(Situation.journaled(service, catalog,
      repository, "rest"))
    val rested = act.after(GameCommand.BeginRest(p1))
    val passed =
      if rested.ready.game.current.turn.activePlayer == p1 then
        rested.after(GameCommand.FinishRest(p1))
      else rested
    assertNotEquals(passed.ready.game.current.turn.activePlayer, p1)
    nothingToUndo(service, "rest", passed.nextSequence, p1)
    nothingToUndo(service, "rest", passed.nextSequence, p2)
