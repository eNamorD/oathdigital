package oathdigital.application

import oathdigital.model._
import oathdigital.gameplay.{CatacombsContributionSuite, OathRules}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers

/** Task 7 (Recover slice, controller ruling (b)): the walker path never
  * populates the legacy `PendingProcedure.Recover` projections, so a parked
  * walker decision needs its own server-side projection surface. Pinned
  * here at the application-projector level (no frontend wiring in this
  * slice): [[WalkerDecisionProjector]] plus the `phase`/`legalControls`
  * effects on [[PendingProcedureProjector]]/[[LegalActionProjector]], all of
  * which flow through the real [[GameProjector]] wire-level `phase` and
  * `legalControls` string fields for free (both are plain strings, so no
  * codec change is needed to observe them end to end).
  */
class WalkerDecisionProjectionSuite extends munit.FunSuite {
  private def presentation = new GamePresentationProjector(catalog)
  private def walkerDecisions = new WalkerDecisionProjector(catalog)
  private def setupRules = new FirstGameSetupRules(catalog)

  private final class FixedRecoverDice(faces: Vector[DefenseDieFace])
      extends DefenseDicePort {
    def rollTwo() = faces
  }

  private def recoverSiteWithDifficulty(maxDifficulty: Int) =
    catalog.sites.find(site =>
      site.recoverDifficulty.exists(d => d > 0 && d <= maxDifficulty) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id

  private def execute(
      service: GameApplicationService,
      gameId: String,
      placementSites: Vector[SiteId],
      setupPlan: oathdigital.gameplay.setup.FirstGameSetupPlan
  ): GameAccepted = {
    var accepted = service.handle(gameId, 0L, GameCommand.Begin(setupPlan))
      .toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.PlacePawn(playerId, placementSites(index))).toOption.get
      val participantIndex = setupPlan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.ChooseAdviser(playerId,
          setupPlan.denizenOrder(6 + participantIndex * 3))).toOption.get
    }
    accepted
  }

  private def startedAtRoll(gameId: String, dice: DefenseDicePort,
      maxDifficulty: Int = 8) = {
    val recoverSite = recoverSiteWithDifficulty(maxDifficulty)
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val actor = recoverPlan.firstPlayer
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      defenseDicePort = dice)
    val setup = execute(service, gameId, recoverPlan.orderedSites, recoverPlan)
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val started = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    (service, actor, started)
  }

  test("a parked Roll projects an owner-private roll decision and blocks " +
      "ordinary Act controls") {
    val (service, actor, started) = startedAtRoll("walker-projection-roll",
      new FixedRecoverDice(Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)))
    val Ready(ready) = started.state: @unchecked
    val other = ready.game.current.players.map(_.player).find(_ != actor).get

    val owner = ScopedProjectionContext(ready, Some(actor))
    assertEquals(walkerDecisions.project(owner), Some(WalkerDecisionProjection(
      ActionRef.Recover.key, RecoverProcedure.rollDecisionId, "roll",
      pool = Some(RecoverProcedure.recoverPool.value), count = Some(2))))

    val viewer = ScopedProjectionContext(ready, Some(other))
    assertEquals(walkerDecisions.project(viewer), None)

    val pendingProjector = new PendingProcedureProjector(catalog,
      presentation, walkerDecisions)
    assertEquals(pendingProjector.project(owner).phase, "recover-walker-roll")
    assertEquals(pendingProjector.project(viewer).phase, "recover-walker-waiting")

    val legal = new LegalActionProjector(catalog, presentation, walkerDecisions)
    assertEquals(legal.project(owner).controls, Vector("rollWalker"))
    assertEquals(legal.project(viewer).controls, Vector.empty)

    // Same effect end to end through the real wire-facing projector, whose
    // `phase`/`legalControls` fields are plain strings needing no codec
    // change to carry this through.
    val loaded = service.load("walker-projection-roll").toOption.flatten.get
    val projector = new GameProjector(catalog)
    val ownerProjection = projector.project("walker-projection-roll", loaded, actor)
    assertEquals(ownerProjection.phase, "recover-walker-roll")
    assertEquals(ownerProjection.legalControls, Vector("rollWalker"))
    assert(!ownerProjection.actionSelectionOpen)
    assertEquals(ownerProjection.actionFamilies, Vector.empty)
    val otherProjection = projector.project("walker-projection-roll", loaded, other)
    assertEquals(otherProjection.phase, "recover-walker-waiting")
    assertEquals(otherProjection.legalControls, Vector.empty)
    assert(!otherProjection.actionSelectionOpen)
    assertEquals(otherProjection.actionFamilies, Vector.empty)
  }

  test("a failed roll parks the continue/stop Decide with its own decision " +
      "id, not the Roll's") {
    val (service, actor, started) = startedAtRoll("walker-projection-choice",
      new FixedRecoverDice(Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)))
    val rolled = service.handle("walker-projection-choice",
      started.nextSequence,
      GameCommand.RollWalker(RecoverProcedure.recoverPool)).toOption.get

    val Ready(ready) = rolled.state: @unchecked
    val other = ready.game.current.players.map(_.player).find(_ != actor).get
    val owner = ScopedProjectionContext(ready, Some(actor))
    assertEquals(walkerDecisions.project(owner), Some(WalkerDecisionProjection(
      ActionRef.Recover.key, RecoverProcedure.choiceDecisionId, "decide")))

    val viewer = ScopedProjectionContext(ready, Some(other))
    assertEquals(walkerDecisions.project(viewer), None)

    val pendingProjector = new PendingProcedureProjector(catalog, presentation,
      walkerDecisions)
    assertEquals(pendingProjector.project(owner).phase, "recover-walker-decision")
    assertEquals(pendingProjector.project(viewer).phase, "recover-walker-waiting")
    val legal = new LegalActionProjector(catalog, presentation, walkerDecisions)
    assertEquals(legal.project(owner).controls, Vector("resolveWalkerDecision"))
    assertEquals(legal.project(viewer).controls, Vector.empty)
  }

  test("a successful roll parks the relic Decide without leaking the " +
      "tree's placeholder relic marker") {
    val (service, actor, started) = startedAtRoll("walker-projection-relic",
      new FixedRecoverDice(Vector(DefenseDieFace.TwoShields,
        DefenseDieFace.Doubler)), maxDifficulty = 4)
    val rolled = service.handle("walker-projection-relic",
      started.nextSequence,
      GameCommand.RollWalker(RecoverProcedure.recoverPool)).toOption.get
    val Ready(ready) = rolled.state: @unchecked
    val other = ready.game.current.players.map(_.player).find(_ != actor).get
    val owner = ScopedProjectionContext(ready, Some(actor))

    assertEquals(walkerDecisions.project(owner), Some(WalkerDecisionProjection(
      ActionRef.Recover.key, RecoverProcedure.relicDecisionId, "decide")))

    val viewer = ScopedProjectionContext(ready, Some(other))
    assertEquals(walkerDecisions.project(viewer), None)

    val pendingProjector = new PendingProcedureProjector(catalog, presentation,
      walkerDecisions)
    assertEquals(pendingProjector.project(owner).phase, "recover-walker-decision")
    assertEquals(pendingProjector.project(viewer).phase, "recover-walker-waiting")
    val legal = new LegalActionProjector(catalog, presentation, walkerDecisions)
    assertEquals(legal.project(owner).controls, Vector("resolveWalkerDecision"))
    assertEquals(legal.project(viewer).controls, Vector.empty)
  }

  // ---------------------------------------------------------------------
  // Task 5 binding carry-in: the projector must fold a shared window with
  // the SAME powers the walker command that parked here used, or it
  // misreports the park the moment a power inserts operations at that
  // window (Catacombs, at `RecoverActionEligibility`, is the first one).
  // ---------------------------------------------------------------------

  test("the projector reports the roll the walker actually parked at with " +
      "Catacombs in effect, and would misreport it if it folded without " +
      "the power") {
    val fixture = CatacombsContributionSuite.reliclessSite(setupRules)
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
    val started = rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
        fixture.actor, Vector(CatacombsContributionSuite.catacombsId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the Catacombs start to run, got $other")
    }
    val Ready(ready) = started.state: @unchecked
    val context = ScopedProjectionContext(ready, Some(fixture.actor))

    // Wired with the same catalog the walker used: the fold matches, and the
    // projector reports the very roll the walker parked at (shifted one
    // index deeper than the bare tree by Catacombs' inserted node).
    val wired = new WalkerDecisionProjector(catalog,
      WalkerPowerCatalog.default(catalog))
    assertEquals(wired.project(context), Some(WalkerDecisionProjection(
      ActionRef.Recover.key, RecoverProcedure.rollDecisionId, "roll",
      pool = Some(RecoverProcedure.recoverPool.value), count = Some(2))))

    // The pre-Task-5 hardcoded `WalkerPowers.empty` folds the bare tree --
    // one node short of what the walker actually folded -- and can no
    // longer resolve the recorded park path at all.
    val unwired = new WalkerDecisionProjector(catalog, WalkerPowers.empty)
    assertEquals(unwired.project(context), None)
  }
}
