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

  test("peeking at a site's relics cannot be taken back"):
    val table = Table.start
      .pawn(p1, "Broken Peaks")
      .relicAt("Sticky Fire", "Broken Peaks")
      .banditsRefilled
    val (service, repository) = table.service()
    val act = table.situation(Situation.journaled(service, catalog,
      repository, "peek"))
    val peeked = act.after(GameCommand.StartWalker(ActionRef.PeekSiteRelics,
      StartPayload(p1)))
    nothingToUndo(service, "peek", peeked.nextSequence, p1)

  test("finishing Rest hands the turn over, so neither player can undo"):
    val table = Table.start.turn(p1, Phase.Act).banditsRefilled
    val (service, repository) = table.service()
    val act = table.situation(Situation.journaled(service, catalog,
      repository, "rest"))
    val passed = act.after(GameCommand.BeginRest(p1))
    // Rest asks p1 nothing here, so Begin Rest runs on into Finish Rest.
    assertEquals(passed.ready.game.current.turn.activePlayer, p2)
    nothingToUndo(service, "rest", passed.nextSequence, p1)
    nothingToUndo(service, "rest", passed.nextSequence, p2)
