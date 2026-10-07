package oathdigital.application.gamelog

import oathdigital.application.{GameAccepted, GameApplicationError,
  GameApplicationService, GameCommand, ParkedServiceFixture, StartPayload,
  TreeDecision}
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.formatter

/** What a reader sees of taken-back moves (undo design, "Game Log"). */
class GameLogTakenBackSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def travel(to: PlayerId) = GameCommand.StartWalker(ActionRef.Travel,
    StartPayload(p1, Vector.empty,
      Vector(DecisionOptionRef.Site(Table.quietPawnSite(to)))))
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)
  private def log(service: GameApplicationService, gameId: String,
      viewer: PlayerId): Vector[LogEntry] =
    formatter.format(service.history(gameId).toOption.flatten
      .getOrElse(fail("no history")).steps, Some(viewer))
  private def header(entry: LogEntry): Option[String] =
    entry.takenBack.map(_.header.map(_.text).mkString)
  private def withoutMarks(entries: Vector[LogEntry]) =
    entries.map(_.copy(takenBack = None))
  private def holderAnswer(park: ParkedServiceFixture.OathkeeperTiePark) =
    GameCommand.ResolveWalker(park.holder, TreeDecision(
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(park.secondLeader))))

  test("taken-back lines read as before, grouped under who took them back"):
    val (service, _) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p3)))
    val before = log(service, "g", p2)
    accept(service.handle("g", moved.nextSequence, GameCommand.Undo(p1)))
    val after = log(service, "g", p2)
    val voided = after.filter(_.sequence < moved.nextSequence)
      .filter(_.takenBack.nonEmpty)
    assert(voided.nonEmpty, "the Travel's lines are still shown")
    assertEquals(withoutMarks(voided),
      before.filter(entry => voided.exists(_.sequence == entry.sequence)))
    assertEquals(voided.map(header).distinct,
      Vector(Some("p1 took back the following moves:")))
    assertEquals(voided.flatMap(_.takenBack).map(_.group).distinct.size, 1)

  test("consecutive undos make one group"):
    val (service, _) = table.service()
    val first = accept(service.handle("g", 0L, travel(p3)))
    val second = accept(service.handle("g", first.nextSequence, travel(p2)))
    val once = accept(service.handle("g", second.nextSequence,
      GameCommand.Undo(p1)))
    accept(service.handle("g", once.nextSequence, GameCommand.Undo(p1)))
    val groups = log(service, "g", p1).flatMap(_.takenBack).map(_.group).distinct
    assertEquals(groups.size, 1)

  test("a group names every issuer, in the order their moves appear"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val answered = accept(game.service.handle("tie", game.accepted.nextSequence,
      holderAnswer(park)))
    val holderUndone = accept(game.service.handle("tie", answered.nextSequence,
      GameCommand.Undo(park.holder)))
    accept(game.service.handle("tie", holderUndone.nextSequence,
      GameCommand.Undo(park.mover)))
    val headers = log(game.service, "tie", park.mover).flatMap(header).distinct
    assertEquals(headers, Vector(
      s"${park.mover.value} and ${park.holder.value} took back the following moves:"))

  test("an answer given again after an undo reads as it did the first time"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val first = accept(game.service.handle("tie", game.accepted.nextSequence,
      holderAnswer(park)))
    val undone = accept(game.service.handle("tie", first.nextSequence,
      GameCommand.Undo(park.holder)))
    accept(game.service.handle("tie", undone.nextSequence, holderAnswer(park)))
    val entries = log(game.service, "tie", park.holder)
    val firstTime = withoutMarks(entries.filter(entry =>
      entry.sequence >= game.accepted.nextSequence &&
        entry.sequence < first.nextSequence))
    val again = entries.filter(_.sequence >= undone.nextSequence)
    assert(firstTime.nonEmpty, "the first answer posts lines")
    assertEquals(again.map(entry => (entry.kind, entry.spans, entry.section)),
      firstTime.map(entry => (entry.kind, entry.spans, entry.section)))
