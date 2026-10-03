package oathdigital.application

import java.nio.file.Files

import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.model._
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.model.DecisionAnswer.{AcceptDeal, ProposeTerms}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.{Look, Outcomes}
import oathdigital.testkit.Unwrap.accepted
import ParkedServiceFixture.loaded
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.present

/** A game reopened from HSQL keeps its Rest cleanup, private relic
  * knowledge and Negotiation state.
  */
class GameApplicationServiceReopenSuite extends munit.FunSuite:
  test("HSQL reopen preserves completed Rest cleanup and secret summary"):
    val path = Files.createTempDirectory(
      "oathdigital-rest-reopen-").resolve("journal")
    val gameId = "game-hsql-rest-cleanup"
    // Broken Peaks' secrets give p2's coming Wake an option, so it waits.
    val table = Table.start.siteTokens("Broken Peaks", secrets = 2)
    val first = accepted(OwnedHsqldbEventStreamRepository.open(path))
    // p1 rests.
    val done = try
      val service = new GameApplicationService(catalog, first,
        genesis = table.state)
      accepted(service.handle(gameId, 0L, GameCommand.BeginRest(p1)))
    finally first.close()
    val reopened = accepted(OwnedHsqldbEventStreamRepository.open(path))
    try
      val stored = loaded(new GameApplicationService(catalog, reopened,
        genesis = table.state).load(gameId))
      assertEquals(stored.state, done.state)
      val ready = Outcomes.ready(stored.state)
      // Begin Rest finished the Rest and woke the next player.
      assertEquals(ready.game.current.turn.phase, Phase.Wake)
      assertNotEquals(ready.game.current.turn.activePlayer, p1)
      val summary = accepted(oathdigital.gameplay.PlayerSecretSummary
        .derive(ready, p1))
      assertEquals(summary.facedown, 0)
      assertEquals(summary.totalSecrets, summary.available + summary.committed)
    finally reopened.close()

  test("HSQL reopen preserves private minor-action relic knowledge"):
    val path = Files.createTempDirectory(
      "oathdigital-minor-reopen-").resolve("journal")
    val gameId = "game-hsql-minor-relics"
    // p2 acts at Broken Peaks, where a relic lies.
    val table = Table.start.turn(p2, Phase.Act)
      .relicAt("Sticky Fire", "Broken Peaks")
    val first = accepted(OwnedHsqldbEventStreamRepository.open(path))
    val peeked = try
      val service = new GameApplicationService(catalog, first,
        genesis = table.state)
      accepted(service.handle(gameId, 0L, GameCommand.PeekSiteRelics(p2)))
    finally first.close()

    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen minor-action repository: $error"), identity)
    try
      val stored = loaded(new GameApplicationService(catalog, reopened,
        genesis = table.state).load(gameId))
      assertEquals(stored.state, peeked.state)
      val ready = Outcomes.ready(stored.state)
      val actor = ready.game.current.turn.activePlayer
      val other = ready.game.current.players.find(_.player != actor).get.player
      val siteId = Look(ready).player(actor).pawnSite.get
      val projector = new GameProjector(catalog)
      assert(projector.project(gameId, stored, actor).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics.nonEmpty)
      assertEquals(projector.project(gameId, stored, other).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)
      assertEquals(projector.projectPublic(gameId, stored).world.flatMap(_.sites)
        .find(_.siteId == siteId.value).get.relics.knownRelics, Vector.empty)
    finally reopened.close()

  /** p1 holds Wizard's Conclave facedown and will travel to p2's site to
    * negotiate. p3 stands at Buried Giant, away from them, so p2 is the only
    * player p1 can negotiate with. */
  private val negotiationTable = Table.start
    .adviser(p1, "Wizard's Conclave", facedown = true)
  private val wizardsConclave = CatalogNames.denizen("Wizard's Conclave")

  private def negotiationService(repository: EventStreamRepository)
      : GameApplicationService =
    new GameApplicationService(catalog, repository,
      genesis = negotiationTable.state)

  /** p1 travels to Broken Peaks, p2's site, and opens a Negotiation with
    * p2, the only player there: no negotiators decision is asked. */
  private def negotiationOpened(service: GameApplicationService,
      gameId: String)(using munit.Location): GameAccepted =
    val traveled = accepted(service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(p1,
        Vector.empty, Vector(DecisionOptionRef.Site(
          CatalogNames.site("Broken Peaks")))))))
    assertEquals(NegotiationDeal.eligible(Outcomes.ready(traveled.state), p1),
      Vector(p2))
    service.handle(gameId, traveled.nextSequence,
      GameCommand.StartWalker(ActionRef.Negotiation, StartPayload(p1)))
      .fold(error => fail(s"Negotiation must start: $error"), identity)

  /** `by` answers the open deal decision with `answer`. */
  private def answerDeal(service: GameApplicationService, gameId: String,
      from: GameAccepted, by: PlayerId, answer: DecisionAnswer) =
    service.handle(gameId, from.nextSequence, GameCommand.ResolveWalker(by,
      TreeDecision(NegotiationDeal.dealDecisionId, answer)))

  test("HSQL reopen preserves a parked Negotiation deal"):
    val path = Files.createTempDirectory("oathdigital-negotiation-parked-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation-parked"
    val first = accepted(OwnedHsqldbEventStreamRepository.open(path))
    val parked = try
      val service = negotiationService(first)
      val opened = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        p2, NegotiationDisclosureRef.Adviser(p1, wizardsConclave))))
      answerDeal(service, gameId, opened, p1, ProposeTerms(terms))
        .fold(error => fail(s"terms must persist: $error"), identity)
    finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try
      val stored = loaded(negotiationService(reopened)
        .load(gameId))
      assertEquals(stored.state, parked.state)
      val seen = present(new GameProjector(catalog).project(gameId, stored, p2)
        .walkerDecision.flatMap(_.query).collect {
          case negotiate: DecisionQueryProjection.Negotiate => negotiate.deal
        }, "p2's Negotiate deal")
      assertEquals(seen.disclosures.map(d => (d.authorPlayerId, d.card)),
        Vector((p1.value, None)))
    finally reopened.close()

  test("HSQL reopen preserves completed Negotiation disclosure knowledge"):
    val path = Files.createTempDirectory("oathdigital-negotiation-reopen-")
      .resolve("journal")
    val gameId = "game-hsql-negotiation"
    val first = accepted(OwnedHsqldbEventStreamRepository.open(path))
    val completed = try
      val service = negotiationService(first)
      val opened = negotiationOpened(service, gameId)
      val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(
        p2, NegotiationDisclosureRef.Adviser(p1, wizardsConclave))))
      val changed = answerDeal(service, gameId, opened, p1, ProposeTerms(terms))
        .fold(error => fail(s"failed to persist Negotiation terms: $error"),
          identity)
      assertEquals(answerDeal(service, gameId, opened, p1, AcceptDeal)
        .left.toOption, Some(GameApplicationError.StaleClientPosition(
          opened.nextSequence, changed.nextSequence)))
      val actorAccepted =
        accepted(answerDeal(service, gameId, changed, p1, AcceptDeal))
      accepted(answerDeal(service, gameId, actorAccepted, p2, AcceptDeal))
    finally first.close()
    val reopened = OwnedHsqldbEventStreamRepository.open(path).fold(
      error => fail(s"failed to reopen Negotiation repository: $error"), identity)
    try
      val stored = loaded(negotiationService(reopened)
        .load(gameId))
      assertEquals(stored.state, completed.state)
      val projector = new GameProjector(catalog)
      assertEquals(projector.project(gameId, stored, p1).walkerDecision, None)
      val recipientBoard = present(projector.project(gameId, stored, p2)
        .playerBoards.find(_.playerId == p1.value), "p1's board")
      assert(recipientBoard.advisers.exists(card =>
        card.cardId == wizardsConclave.value && !card.hidden))
      val publicBoard = present(projector.projectPublic(gameId, stored)
        .playerBoards.find(_.playerId == p1.value), "p1's public board")
      assert(publicBoard.advisers.forall(card =>
        card.cardId != wizardsConclave.value || card.hidden))
      assertEquals(projector.projectPublic(gameId, stored).walkerWaiting, None)
    finally reopened.close()
