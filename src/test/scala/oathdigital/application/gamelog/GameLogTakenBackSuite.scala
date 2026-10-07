package oathdigital.application.gamelog

import oathdigital.application.{GameAccepted, GameApplicationError,
  GameApplicationService, GameCommand, ParkedServiceFixture, StartPayload,
  TreeDecision}
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseOneAnswer,
  DeclineDeal, ProposeTerms}
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.formatter

/** What a reader sees of taken-back moves (undo design, "Game Log"). */
class GameLogTakenBackSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
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
    val moved = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
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
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accept(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    accept(service.handle("g", twice.nextSequence, GameCommand.Undo(p1)))
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

  test("a different answer after an undo reads as if it had come first"):
    def otherAnswer(park: ParkedServiceFixture.OathkeeperTiePark) =
      GameCommand.ResolveWalker(park.holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(park.mover))))
    val direct = ParkedServiceFixture.oathkeeperTiePark("direct")
    val answered = accept(direct.game.service.handle("direct",
      direct.game.accepted.nextSequence, otherAnswer(direct)))
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val first = accept(game.service.handle("tie", game.accepted.nextSequence,
      holderAnswer(park)))
    val undone = accept(game.service.handle("tie", first.nextSequence,
      GameCommand.Undo(park.holder)))
    accept(game.service.handle("tie", undone.nextSequence, otherAnswer(park)))
    def reading(entries: Vector[LogEntry], from: Long) = entries
      .filter(entry => entry.sequence >= from && entry.takenBack.isEmpty)
      .map(entry => (entry.kind, entry.depth, entry.spans))
    assertEquals(reading(log(game.service, "tie", park.holder),
        undone.nextSequence),
      reading(log(direct.game.service, "direct", direct.holder),
        direct.game.accepted.nextSequence))
    assert(answered.nextSequence > direct.game.accepted.nextSequence)

  test("a deal declined, taken back and then agreed reads as agreed at once"):
    // p1 and p2 share Ancient City; p1 proposes that p2 receives 1 favor.
    val table = Table.start.pawn(p2, "Ancient City").banditsRefilled
    def deal(by: PlayerId, answer: DecisionAnswer) = GameCommand.ResolveWalker(
      by, TreeDecision(NegotiationDeal.dealDecisionId, answer))
    def agreedAfter(service: GameApplicationService, gameId: String,
        at: Long): GameAccepted =
      val accepted = accept(service.handle(gameId, at, deal(p2, AcceptDeal)))
      accept(service.handle(gameId, accepted.nextSequence,
        deal(p1, AcceptDeal)))
    def proposed(gameId: String) =
      val (service, _) = table.service()
      val started = accept(service.handle(gameId, 0L, GameCommand.StartWalker(
        ActionRef.Negotiation, StartPayload(p1))))
      (service, accept(service.handle(gameId, started.nextSequence,
        deal(p1, ProposeTerms(NegotiationTerms(Vector(
          NegotiationTransfer(p2, 1, Vector.empty))))))))
    val (direct, directProposal) = proposed("direct")
    agreedAfter(direct, "direct", directProposal.nextSequence)
    val (service, proposal) = proposed("g")
    val declined = accept(service.handle("g", proposal.nextSequence,
      deal(p2, DeclineDeal)))
    val undone = accept(service.handle("g", declined.nextSequence,
      GameCommand.Undo(p2)))
    agreedAfter(service, "g", undone.nextSequence)
    def reading(entries: Vector[LogEntry], from: Long) = entries
      .filter(entry => entry.sequence >= from && entry.takenBack.isEmpty)
      .map(LogScripts.text)
    assertEquals(reading(log(service, "g", p1), undone.nextSequence),
      reading(log(direct, "direct", p1), directProposal.nextSequence))

  test("a Wake whose only step was taken back still reads as quiet"):
    val table = Table.start.turn(p2, Phase.Wake)
      .siteTokens("Broken Peaks", secrets = 2).banditsRefilled
    val (service, _) = table.service()
    val taken = accept(service.handle("g", 0L, GameCommand.StartWalker(
      ActionRef.TakeWealth, StartPayload(p2, Vector.empty,
        Vector(DecisionOptionRef.Button("secret"))))))
    val undone = accept(service.handle("g", taken.nextSequence,
      GameCommand.Undo(p2)))
    accept(service.handle("g", undone.nextSequence, GameCommand.EndWake(p2)))
    val live = log(service, "g", p2).filter(_.sequence >= undone.nextSequence)
      .map(LogScripts.text)
    assertEquals(live.takeRight(2), Vector("Nothing happened in Wake", "Act"))

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
