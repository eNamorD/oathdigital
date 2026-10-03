package oathdigital.application

import java.nio.file.Files

import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.model._
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseManyAnswer,
  ProposeTerms}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** A game reopened from HSQL keeps its Rest cleanup, private relic
  * knowledge and Negotiation state.
  */
class GameApplicationServiceReopenSuite extends munit.FunSuite:
  test("HSQL reopen preserves completed Rest cleanup and secret summary"):
    val path = Files.createTempDirectory("oathdigital-rest-reopen-").resolve("journal")
    val gameId = "game-hsql-rest-cleanup"
    // Broken Peaks' secrets give p2's coming Wake an option, so it waits.
    val table = Table.start.siteTokens("Broken Peaks", secrets = 2)
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val finished = try
      val service = new GameApplicationService(catalog, first,
        genesis = table.state)
      val actor = p1
      val begun = service.handle(gameId, 0L,
        GameCommand.BeginRest(actor)).toOption.get
      (begun, actor)
    finally first.close()
    val (done, restedActor) = finished
    val reopened = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    try
      val loaded = new GameApplicationService(catalog, reopened,
        genesis = table.state).load(gameId).toOption.flatten.get
      assertEquals(loaded.state, done.state)
      val Ready(ready) = loaded.state: @unchecked
      // Begin Rest finished the Rest and woke the next player.
      assertEquals(ready.game.current.turn.phase, Phase.Wake)
      assertNotEquals(ready.game.current.turn.activePlayer, restedActor)
      val rested = ready.game.current.players.find(_.player !=
        ready.game.current.turn.activePlayer).get
      val summary = oathdigital.gameplay.PlayerSecretSummary
        .derive(ready, rested.player).toOption.get
      assertEquals(summary.facedown, 0)
      assertEquals(summary.totalSecrets, summary.available + summary.committed)
    finally reopened.close()

  test("HSQL reopen preserves private minor-action relic knowledge"):
    val path = Files.createTempDirectory("oathdigital-minor-reopen-").resolve("journal")
    val gameId = "game-hsql-minor-relics"
    // p2 acts at Broken Peaks, where a relic lies.
    val table = Table.start.turn(p2, Phase.Act)
      .relicAt("Sticky Fire", "Broken Peaks")
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val peeked = try
      val service = new GameApplicationService(catalog, first,
        genesis = table.state)
      service.handle(gameId, 0L, GameCommand.PeekSiteRelics(p2)).toOption.get
    finally first.close()

    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen minor-action repository: $error"), identity)
    try
      val loaded = new GameApplicationService(catalog, reopened,
        genesis = table.state).load(gameId).toOption.flatten.get
      assertEquals(loaded.state, peeked.state)
      val Ready(ready) = loaded.state: @unchecked
      val actor = ready.game.current.turn.activePlayer
      val other = ready.game.current.players.find(_.player != actor).get.player
      val siteId = ready.game.current.players.find(_.player == actor).get.pawnSite.get
      val projector = new GameProjector(catalog)
      assert(projector.project(gameId, loaded, actor).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics.nonEmpty)
      assertEquals(projector.project(gameId, loaded, other).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)
      assertEquals(projector.projectPublic(gameId, loaded).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)
    finally reopened.close()

  /** p1 holds a facedown adviser and will travel to p2's site to negotiate. */
  private val negotiationTable = Table.start
    .adviser(p1, "Wizard's Conclave", facedown = true)

  private def negotiationService(repository: EventStreamRepository)
      : GameApplicationService =
    new GameApplicationService(catalog, repository,
      genesis = negotiationTable.state)

  private def negotiationOpened(service: GameApplicationService, gameId: String)
      : (GameAccepted, PlayerId, PlayerId, WorldCardId) =
    val Ready(ready) = negotiationTable.state: @unchecked
    val actor = p1
    val other = ready.game.current.players.find(_.player == p2).get
    val traveled = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Site(other.pawnSite.get)))))
      .toOption.get
    val adviser = ready.game.current.players.find(_.player == actor).get
      .advisers.head.id.asInstanceOf[WorldCardId]
    val started = service.handle(gameId, traveled.nextSequence,
      GameCommand.StartWalker(ActionRef.Negotiation, StartPayload(actor)))
      .fold(error => fail(s"Negotiation must start: $error"), identity)
    val Ready(afterTravel) = traveled.state: @unchecked
    val opened =
      if NegotiationDeal.eligible(afterTravel, actor).size < 2 then started
      else service.handle(gameId, started.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(
          NegotiationDeal.negotiatorsDecisionId, ChooseManyAnswer(
            Vector(DecisionOptionRef.Player(other.player))))))
        .fold(error => fail(s"negotiators must be accepted: $error"), identity)
    (opened, actor, other.player, adviser)

  private def say(service: GameApplicationService, gameId: String,
      from: GameAccepted, by: PlayerId, answer: DecisionAnswer) =
    service.handle(gameId, from.nextSequence, GameCommand.ResolveWalker(by,
      TreeDecision(NegotiationDeal.dealDecisionId, answer)))

  test("HSQL reopen preserves a parked Negotiation deal"):
    val path = Files.createTempDirectory("oathdigital-negotiation-parked-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation-parked"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (parked, actor, other, adviser) = try
      val service = negotiationService(first)
      val (opened, actor, other, adviser) = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other, NegotiationDisclosureRef.Adviser(actor, adviser))))
      (say(service, gameId, opened, actor, ProposeTerms(terms))
        .fold(error => fail(s"terms must persist: $error"), identity),
        actor, other, adviser)
    finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try
      val loaded = negotiationService(reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, parked.state)
      val seen = new GameProjector(catalog).project(gameId, loaded, other)
        .walkerDecision.flatMap(_.query).collect {
          case negotiate: DecisionQueryProjection.Negotiate => negotiate.deal
        }.get
      assertEquals(seen.disclosures.map(d => (d.authorPlayerId, d.card)),
        Vector((actor.value, None)))
    finally reopened.close()

  test("HSQL reopen preserves completed Negotiation disclosure knowledge"):
    val path = Files.createTempDirectory("oathdigital-negotiation-reopen-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation"
    val first = OwnedHsqldbEventStreamRepository.open(path).toOption.get
    val (completed, actor, other, adviser) = try
      val service = negotiationService(first)
      val (opened, actor, other, adviser) = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        other, NegotiationDisclosureRef.Adviser(actor, adviser))))
      val changed = say(service, gameId, opened, actor, ProposeTerms(terms))
        .fold(error => fail(s"failed to persist Negotiation terms: $error"), identity)
      assertEquals(say(service, gameId, opened, actor, AcceptDeal).left.toOption,
        Some(GameApplicationError.StaleClientPosition(
          opened.nextSequence, changed.nextSequence)))
      val actorAccepted = say(service, gameId, changed, actor, AcceptDeal)
        .toOption.get
      val completed = say(service, gameId, actorAccepted, other, AcceptDeal)
        .toOption.get
      (completed, actor, other, adviser)
    finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try
      val loaded = negotiationService(reopened)
        .load(gameId).toOption.flatten.get
      assertEquals(loaded.state, completed.state)
      val projector = new GameProjector(catalog)
      assertEquals(projector.project(gameId, loaded, actor).walkerDecision, None)
      val recipientBoard = projector.project(gameId, loaded, other).playerBoards
        .find(_.playerId == actor.value).get
      assert(recipientBoard.advisers.exists(card =>
        card.cardId == adviser.value && !card.hidden))
      val publicBoard = projector.projectPublic(gameId, loaded).playerBoards
        .find(_.playerId == actor.value).get
      assert(publicBoard.advisers.forall(card =>
        card.cardId != adviser.value || card.hidden))
      assertEquals(projector.projectPublic(gameId, loaded).walkerWaiting, None)
    finally reopened.close()
