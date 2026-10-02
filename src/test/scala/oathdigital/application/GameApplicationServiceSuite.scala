package oathdigital.application

import oathdigital.protocol.projection.{BoardTargetRefProjection,
  DecisionQueryProjection, DecisionSectionProjection, SiteForcesProjection}

import java.nio.file.Files

import oathdigital.model._
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.walker.{ChoicePayload, ParkedDecisionAssertions,
  WalkerCompleted, WalkerParked, WalkerPowers, WalkerStepRecorded}
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.WalkerDiceFixture
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  RelicAcquired, SupplySpent}
import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.model.DecisionAnswer.{AcceptDeal, ChooseAmountAnswer, ChooseManyAnswer, ChooseOneAnswer, PartitionAnswer, ProposeTerms}
import oathdigital.persistence.OwnedHsqldbEventStreamRepository
import oathdigital.serialization.GameEventWire
import oathdigital.server.GameHttpWire
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.application.ForgeWalkerFixture.{forgeService, forgeSite,
  forgeTable, mixedForgeCostCatalog}
import oathdigital.model.OathViolation.WrongPlayer
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.OathRules
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

class GameApplicationServiceSuite extends munit.FunSuite:
  private val catacombsId = DenizenId(catalog.denizens.find(_.powers.exists(
    _.id.value == "denizen.catacombs")).get.id.value)

  /** The parked decision, as this suite rebuilds it: the same catalog and
    * power catalogs `GameApplicationService` builds its rules with
    * (`GameApplicationService.scala:87-90`).
    */
  private val parkedAssertions = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** A second service over the same journal, replaying it from the same
    * start as `service`. */
  private def replayingSecond(service: GameApplicationService,
      repository: InMemoryEventStreamRepository): GameApplicationService =
    new GameApplicationService(catalog, repository, genesis = service.genesis)

  /** `target` swapped into the in-play 8, whether or not it was already one
    * of the fixture's own 8 sites: the actual map (not merely the client's
    * pick) determines which sites Recover/Muster/etc. see in play, so a
    * test that needs a specific site in play must put it in `chronicle`'s
    * own `atlasBox`, not just choose it as a placement.
    */
  private def withSiteInPlay(target: SiteId): (Chronicle, Vector[SiteId]) =
    val stored = chronicle.atlasBox.find(_.site == target)
      .getOrElse(StoredSite(target))
    val newChronicle = chronicle.copy(atlasBox =
      stored +: chronicle.atlasBox.filterNot(_.site == target).take(7))
    (newChronicle, newChronicle.atlasBox.take(8).map(_.site))

  test("beginRest through the service parks the off-turn League Treaty ruler " +
      "and survives reload"):
    val (game, active, ruler) = ParkedServiceFixture.leagueTreatyPark(
      "game-league-treaty")
    val (service, parked) = (game.service, game.accepted)
    val reloaded = new GameApplicationService(catalog, game.repository,
      genesis = service.genesis).load("game-league-treaty").toOption.flatten.get
    assertEquals(reloaded.state, parked.state)
    val decision = parkedAssertions.parkedDecision(parked.state).get.decision
    val decline = GameCommand.ResolveWalker(_: PlayerId, TreeDecision(
      decision, ChooseOneAnswer(DecisionOptionRef.Button("decline"))))
    assert(service.handle("game-league-treaty", parked.nextSequence,
      decline(active)).isLeft)
    val finished = service.handle("game-league-treaty", parked.nextSequence,
      decline(ruler)).toOption.get
    val Ready(after) = finished.state: @unchecked
    assertEquals(after.game.current.turn.phase, Phase.Wake)
    assertNotEquals(after.game.current.turn.activePlayer, active)

  /** p1 stands at Dunes, which has one relic slot and no relic, beside a
    * faceup Catacombs: a Recover there that names Catacombs puts the top of
    * the relic deck on the site and pays a secret for it. */
  private val catacombsTable = Table.start.pawn(p1, "Dunes")
    .denizen("Catacombs", "Dunes")

  /** Recover rolls as the walker walks, so its dice come from the walker's
    * own source -- the campaign port -- rather than from the port a parked
    * roll command would have asked. `calls` counts the defense rolls, which
    * is what every Recover test here cares about.
    */
  private final class CountingRecoverDice extends CampaignDicePort:
    var calls = 0
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      calls += 1
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler)

  private final class FixedRecoverDice(faces: Vector[DefenseDieFace])
      extends CampaignDicePort:
    var calls = 0
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      calls += 1
      faces

  private final class ScriptedRecoverDice(
      rolls: Vector[Vector[DefenseDieFace]]) extends CampaignDicePort:
    var calls = 0
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      val faces = rolls(calls)
      calls += 1
      faces

  test("walker Recover persists every park and replays to the same final state"):
    // p1 stands at Broken Peaks, difficulty 4, with two facedown relics.
    val recoverSite = CatalogNames.site("Broken Peaks")
    val actor = p1

    val walkerDice = new CountingRecoverDice
    val (walkerService, walkerRepository) = ParkedServiceFixture.recoverTable
      .service(campaignDice = walkerDice)

    // The start rolls on its way through: two shields and a doubler reach
    // this site's difficulty, so the command that begins Recover is also the
    // one that parks on the relic it won.
    val started = walkerService.handle("walker-recover", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    val Ready(atRelic) = started.state: @unchecked
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(walkerDice.calls, 1)
    assertEquals(atRelic.game.current.walkerProcedure, Some(ActionRef.Recover))
    assert(atRelic.game.current.walkerPending.nonEmpty)

    walkerService.handle("walker-recover", started.nextSequence,
      GameCommand.PeekSiteRelics(actor)) match
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => ()
      case other => fail(s"legacy command should be blocked by walker park: $other")
    assertEquals(walkerService.load("walker-recover").toOption.flatten.get
      .nextSequence, started.nextSequence)

    val reloadedAtRelic = replayingSecond(walkerService, walkerRepository)
      .load("walker-recover").toOption.flatten.get
    assertEquals(reloadedAtRelic.state, started.state)
    val relic = atRelic.game.current.map.sites(recoverSite).relics.head.id

    val finished = walkerService.handle("walker-recover",
      reloadedAtRelic.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Relic(relic))))).toOption.get
    val Ready(afterWalker) = finished.state: @unchecked
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    parkedAssertions.assertResumed(finished.state, Phase.Act, actor)
    assert(afterWalker.game.current.walkerPending.isEmpty)
    assert(afterWalker.game.current.walkerProcedure.isEmpty)
    assertEquals(afterWalker.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    assertEquals(afterWalker.game.current.rollOutcomes,
      Map.empty[PoolKey, RollOutcome])

    // The walker is now the only Recover path, so the final state is
    // asserted directly rather than against a legacy run: the relic moves
    // facedown into the actor's play area and off the site.
    val actorState = afterWalker.game.current.players.find(
      _.player == actor).get
    assertEquals(actorState.relics.map(r => r.id -> r.orientation),
      Vector(relic -> Orientation.FaceDown))
    assertEquals(afterWalker.game.current.map.sites(recoverSite).relics.map(_.id),
      atRelic.game.current.map.sites(recoverSite).relics.map(_.id)
        .filterNot(_ == relic))

    val replayed = walkerService.load("walker-recover").toOption.flatten.get
    assertEquals(replayed.state, finished.state)
    assertEquals(replayed.nextSequence, finished.nextSequence)

    val recordedOps = (started.events ++ finished.events)
      .collect { case step: WalkerStepRecorded => step.ops }.flatten
    assertEquals(recordedOps, Vector[CoreOperation](
      ModifyDicePool(RecoverProcedure.recoverPool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      SpendSupply(actor, 1),
      Move(Piece.Card(relic),
        PositionedLocation(Location.Site(recoverSite)),
        PositionedLocation(Location.PlayArea(actor)),
        resultingOrientation = Some(Orientation.FaceDown))))
    assertEquals((started.events ++ finished.events).collect {
      case WalkerStepRecorded(_, DeltaRecorded(semantic), _, _) => semantic
    }, Vector(
      DicePoolModified(RecoverProcedure.recoverPool, 2),
      SupplySpent(actor, 1),
      RelicAcquired(actor, relic, recoverSite)))

  test("StartWalker rejects an unknown power id in modifiers, appending no " +
      "events, while empty modifiers still starts Recover exactly as today"):
    val actor = p1
    val (service, _) = ParkedServiceFixture.recoverTable.service(
      campaignDice = new FixedRecoverDice(Vector(DefenseDieFace.TwoShields,
        DefenseDieFace.Doubler)))

    // No `ContributingPower` is registered yet (Task 5 ports the first one),
    // so any non-empty `modifiers` names an id the catalog cannot recognize:
    // rejected before the walk ever starts, with nothing appended.
    service.handle("walker-unknown-modifier", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor,
        Vector(PowerId("power.does-not-exist"))))) match
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => ()
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")
    assertEquals(service.load("walker-unknown-modifier").toOption.flatten.get
      .nextSequence, 0L, "the rejected StartWalker must append no events")

    // The exact same command with an empty `modifiers` (every walker Recover
    // before this task) starts and parks exactly as the suite's other walker
    // tests already pin.
    val started = service.handle("walker-unknown-modifier", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get
    assert(started.events.nonEmpty)
    assert(started.events.last.isInstanceOf[WalkerParked])
    parkedAssertions.assertParked(started.state, ActionRef.Recover,
      RecoverProcedure.relicDecisionId, actor)

  test("walker Continue answer and the roll it buys survive reload"):
    val dice = new FixedRecoverDice(Vector(DefenseDieFace.Blank,
      DefenseDieFace.Blank))
    val (service, repository) = ParkedServiceFixture.recoverTable
      .service(campaignDice = dice)
    val actor = p1
    // Both dice come up blank, so the start's own roll fails and parks the
    // Continue/Stop Decide.
    val failed = service.handle("walker-continue", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    assertEquals(dice.calls, 1)
    parkedAssertions.assertParked(failed.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    // Continue buys two more dice and rolls them inside the same command,
    // which fails again and parks the same choice: the continuation names
    // the parked node's own decision id either way.
    val continued = service.handle("walker-continue", failed.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))))).toOption.get
    assertEquals(dice.calls, 2)
    parkedAssertions.assertParked(continued.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    val Ready(ready) = continued.state: @unchecked
    assertEquals(ready.game.current.walkerPending.toVector.flatMap(_.answered),
      Vector(Answered(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)))
    assertEquals(ready.game.current.walkerProcedure, Some(ActionRef.Recover))
    assertEquals(replayingSecond(service, repository)
      .load("walker-continue").toOption.flatten.get.state, continued.state)
    assert(repository.load("walker-continue").toOption.flatten.get.records
      .exists(record => record.contains(
        "\"procedure\":{\"family\":\"action\",\"key\":\"recover\"}") &&
        record.contains("\"answered\"")))

    val failedAgain = continued
    val Ready(afterSecondRoll) = failedAgain.state: @unchecked
    val accumulated = afterSecondRoll.game.current.rollOutcomes(
      RecoverProcedure.recoverPool)
    assertEquals(accumulated.count, 4)
    assertEquals(accumulated.faces.size, 4)
    assertEquals(replayingSecond(service, repository)
      .load("walker-continue").toOption.flatten.get.state, failedAgain.state)

    val rules = new OathRules(catalog)
    Vector(Vector("not-a-node"), Vector("999999999999999999999")).foreach:
      path =>
        val malformed = ready.updateCurrent(_.copy(walkerPending = ready.game.current.walkerPending
            .map(_.copy(at = path))))
        val rejected = rules.rollWalkerPrepared(Ready(malformed), actor,
          RecoverProcedure.recoverPool)(_ => Right(
            Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)))
        assert(rejected.left.toOption.exists(
          _.isInstanceOf[oathdigital.model.OathViolation.InvalidEventOrder]))

  test("walker Recover accumulates and reloads when a later Doubler " +
      "multiplies shields from an earlier roll"):
    // Stays on the replayed setup: Salt Flats is not in play on a Table's
    // map, and no Table step changes the map.
    val saltFlats = SiteId("site:salt-flats")
    assertEquals(RecoverRules.difficulty(catalog, saltFlats), Some(2))
    val (saltFlatsChronicle, orderedSites) = withSiteInPlay(saltFlats)
    val actor = orders.firstPlayer
    val rolls = Vector[Vector[DefenseDieFace]](
      Vector(DefenseDieFace.OneShield, DefenseDieFace.Blank),
      Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank))

    val walkerRepository = new InMemoryEventStreamRepository
    val walkerDice = new ScriptedRecoverDice(rolls)
    val walkerService = new GameApplicationService(catalog, walkerRepository,
      campaignDicePort = walkerDice)
    val walkerSetup = execute(walkerService, "walker-cross-roll-doubler",
      orderedSites, saltFlatsChronicle)
    val walkerAct = ParkedServiceFixture.endingWake(walkerService, "walker-cross-roll-doubler", walkerSetup, actor)
    val walkerFirst = walkerService.handle("walker-cross-roll-doubler",
      walkerAct.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get
    val Ready(walkerAfterFirst) = walkerFirst.state: @unchecked
    assertEquals(walkerAfterFirst.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 1)
    parkedAssertions.assertParked(walkerFirst.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    val walkerFirstReloaded = new GameApplicationService(catalog,
      walkerRepository).load("walker-cross-roll-doubler").toOption.flatten.get
    assertEquals(walkerFirstReloaded.state, walkerFirst.state)
    val Ready(walkerAfterFirstReload) = walkerFirstReloaded.state: @unchecked
    assertEquals(walkerAfterFirstReload.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 1)

    // Continuing buys the second pair and rolls them in the same command:
    // the Doubler that lands there multiplies the shield from the first
    // roll, which is the accumulation under test.
    val walkerSecond = walkerService.handle("walker-cross-roll-doubler",
      walkerFirstReloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))))).toOption.get
    val Ready(walkerAfterSecond) = walkerSecond.state: @unchecked
    assertEquals(walkerAfterSecond.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 2)
    parkedAssertions.assertParked(walkerSecond.state, ActionRef.Recover,
      RecoverProcedure.relicDecisionId, actor)
    val walkerSecondReloaded = new GameApplicationService(catalog,
      walkerRepository).load("walker-cross-roll-doubler").toOption.flatten.get
    assertEquals(walkerSecondReloaded.state, walkerSecond.state)
    val Ready(walkerAfterSecondReload) = walkerSecondReloaded.state: @unchecked
    assertEquals(walkerAfterSecondReload.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 2)

    // The walker is now the only Recover path: finish the walk and assert
    // its own final state directly rather than against a legacy run.
    val walkerRelic = walkerAfterSecond.game.current.map.sites(saltFlats)
      .relics.head.id
    val walkerFinished = walkerService.handle("walker-cross-roll-doubler",
      walkerSecondReloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(walkerRelic))))).toOption.get
    val Ready(walkerFinal) = walkerFinished.state: @unchecked
    val actorState = walkerFinal.game.current.players.find(
      _.player == actor).get
    assertEquals(actorState.relics.map(r => r.id -> r.orientation),
      Vector(walkerRelic -> Orientation.FaceDown))
    assertEquals(walkerFinal.game.current.map.sites(saltFlats).relics.map(_.id),
      walkerAfterSecond.game.current.map.sites(saltFlats).relics.map(_.id)
        .filterNot(_ == walkerRelic))
    assertEquals(walkerDice.calls, 2)

  test("RollWalker with a pool key that does not match the parked pool " +
      "never calls defenseDicePort"):
    // rollWalkerPrepared validates the parked pool against the command's
    // pool key BEFORE invoking prepareFaces (OathRules.scala): this is the
    // entire mechanism the authoritative-dice property relies on to keep a
    // caller from steering which dice get rolled. Pin the ordering directly
    // by proving the port is untouched on a mismatch, not just that the
    // command is rejected.
    val dice = new CountingRecoverDice
    val (service, _) = ParkedServiceFixture.recoverTable
      .service(campaignDice = dice)
    val actor = p1
    val started = service.handle("walker-pool-mismatch", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
      .toOption.get

    // The start rolled once on its way to the park; a roll command naming
    // a pool the walker is not parked on must add nothing to that count.
    assertEquals(dice.calls, 1)
    val rejected = service.handle("walker-pool-mismatch", started.nextSequence,
      GameCommand.RollWalker(actor, PoolKey("not-the-parked-pool")))
    assert(rejected match {
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => true
      case _ => false
    }, s"expected InvalidEventOrder, got $rejected")
    assertEquals(dice.calls, 1)

  test("StartWalker drives Catacombs through the full persisted path: its " +
      "recorded ops encode, append and replay (Task 9b prerequisite)"):
    // The only end-to-end way to use Catacombs once Task 9b deletes the
    // legacy object. `service.handle` is the whole path -- rules, event
    // encoding, append, replay -- so it is the layer that pins the gap the
    // rules-only assertion above cannot see: Catacombs records a `Move` out
    // of `Location.Deck(CardDeck.Relic)` and a `PayCost` at
    // `Location.OnCard`, none of which the walker codec used to encode.
    val (service, repository) = catacombsTable
      .service(campaignDice = new CountingRecoverDice)
    val gameId = "catacombs-walker-persisted"
    val actor = p1
    val Ready(before) = catacombsTable.state: @unchecked
    val beforePlayer = before.game.current.players.find(_.player == actor).get
    val siteId = beforePlayer.pawnSite.get
    val topRelic = before.game.current.commonCards.relicDeck.head

    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor,
        Vector(PowerId("denizen.catacombs"))))) match
      case Right(accepted) => accepted
      case Left(error) =>
        fail(s"StartWalker with Catacombs must persist, got $error")

    // Catacombs' own batch, verbatim, survived encoding: the relic move off
    // the top of the relic deck and the 1-secret payment onto the card.
    assertEquals(started.events.collect {
      case step: WalkerStepRecorded => step
    }.flatMap(_.ops).take(2), Vector[CoreOperation](
      Move(Piece.Card(topRelic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(siteId)),
        resultingOrientation = Some(Orientation.FaceDown)),
      PayCost(actor, Location.OnCard(catacombsId), Cost(secret = 1),
        matchingBank = catalog.suitOf(catacombsId))))
    assert(started.events.collect { case step: WalkerStepRecorded =>
      step.contributions }.contains(Vector(PowerId("denizen.catacombs"))),
      "the recorded step must name the contribution that produced it")

    val Ready(after) = started.state: @unchecked
    val afterPlayer = after.game.current.players.find(_.player == actor).get
    assertEquals(after.game.current.commonCards.relicDeck,
      before.game.current.commonCards.relicDeck.tail)
    assertEquals(after.game.current.map.sites(siteId).relics.map(relic =>
      relic.id -> relic.orientation), Vector(topRelic -> Orientation.FaceDown))
    assertEquals(afterPlayer.board.faceUpSecrets,
      beforePlayer.board.faceUpSecrets - 1)
    // Catacombs put the relic there and the start's own roll won it, so the
    // command parks on taking it.
    parkedAssertions.assertParked(started.state, ActionRef.Recover,
      RecoverProcedure.relicDecisionId, actor)

    // Replay from the journal alone reproduces the same state: the ops did
    // not merely encode, they decode back to the operations that built it.
    val reloaded = replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, started.state)
    assertEquals(reloaded.nextSequence, started.nextSequence)

    // And the walk still finishes on the reloaded stream.
    val Ready(atRelic) = reloaded.state: @unchecked
    val recovered = atRelic.game.current.map.sites(siteId).relics.head.id
    val finished = service.handle(gameId, reloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(recovered))))).toOption.get
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assertEquals(replayingSecond(service, repository).load(gameId)
      .toOption.flatten.get.state, finished.state)

  test("preview offers the walker Catacombs contribution, and " +
      "OathRules.startWalker accepts exactly the offered id (Task 9a)"):
    val (service, _) = catacombsTable
      .service(campaignDice = new CountingRecoverDice)
    val gameId = "catacombs-walker-preview"
    val actor = p1
    val preview = service.preview(gameId, 0L, actor,
      ActionKind.Recover, Vector.empty).toOption.get
    val offeredIds = preview.options.map(v => PowerId(v.handlerId))
    assertEquals(offeredIds, Vector(PowerId("denizen.catacombs")))

    // Each offered modifier travels as the card it is printed on and the
    // action it modifies, not as its handler id.
    val modifier = preview.modifiers.head
    assertEquals(modifier.card.map(_.cardKind), Some("denizen"))
    assertEquals(modifier.card.map(_.hidden), Some(false))
    assertEquals(modifier.card.map(_.name), Some(modifier.description))
    assertNotEquals(modifier.description, "denizen.catacombs")
    assertEquals(modifier.modifies, Some("recover"))

    // The property that matters: the id the preview just offered is exactly
    // the id `OathRules.startWalker` will accept -- not merely "some id
    // that happens to work". A defect here (offered-but-rejected, or
    // accepted-but-never-offered) is precisely what Task 9a exists to
    // prevent. Asserted directly against `OathRules` because that is the
    // layer owning `validateModifiers`, the accepting predicate; the
    // persisted path the preview actually feeds is covered separately by
    // "StartWalker drives Catacombs through the full persisted path" above.
    // (That test exists because this rules-only assertion once hid a real
    // defect: `WalkerEventCodec` could not encode the `Location.Deck` in
    // Catacombs' relic move, so `StartWalker` passed here and failed at
    // append. The codec is now total over `Location`.)
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = WalkerDiceFixture.blanks)
    val started = rules.startWalker(catacombsTable.state, ActionRef.Recover,
      actor, offeredIds)
    assert(started.isRight,
      s"expected StartWalker to accept the previewed id, got $started")

    // A legacy action's preview is untouched: still resolved through
    // `PowerRuntime`, not the walker catalog.
    val travelPreview = service.preview(gameId, 0L, actor,
      ActionKind.Travel, Vector.empty)
    assert(travelPreview.isRight)

  test("all-Exile powered game persists and replays through round-eight victory"):
    // Stays on the replayed setup: the full game from Setup to round eight is
    // the end-to-end smoke test.
    val repository = new InMemoryEventStreamRepository
    val whenPlayedPower = DenizenId(catalog.denizenWithPower(PowerId("denizen.revelation")).get.id.value)
    def place(order: Vector[DenizenId], index: Int, id: DenizenId) =
      val current = order.indexWhere(_.value == id.value)
      if current < 0 then order.updated(index, id)
      else order.updated(index, id).updated(current, order(index))
    val p2Index = 6 + orders.participants.indexWhere(_.playerId == PlayerId("p2")) * 3
    val poweredWorldDeck = place(chronicle.worldDeck, p2Index, whenPlayedPower)
    val poweredChronicle = chronicle.copy(worldDeck = poweredWorldDeck)
    val poweredOrders = ChronicleFirstGamePlan.dealOrder(poweredChronicle,
      FirstGameBootstrapConfig(orders.participants, orders.firstPlayer))
    var service = new GameApplicationService(catalog, repository,
      warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.head
      })
    var accepted = execute(service, "powered-playability", sites,
      poweredChronicle, poweredOrders)
    var played = Set.empty[PlayerId]
    var safety = 0
    while {
      val Ready(ready) = accepted.state: @unchecked
      ready.game.current.result.isEmpty
    } do
      safety += 1
      assert(safety <= 24, "all-Exile game should finish after eight rounds")
      val Ready(wake) = accepted.state: @unchecked
      val actor = wake.game.current.turn.activePlayer
      accepted = ParkedServiceFixture.endingWake(service, "powered-playability", accepted, actor)
      if !played(actor) then
        val Ready(act) = accepted.state: @unchecked
        val adviser = act.game.current.players.find(_.player == actor).get.advisers.head.id
          .asInstanceOf[WorldCardId]
        accepted = service.handle("powered-playability", accepted.nextSequence,
          GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
            StartPayload(actor, Vector.empty, Vector(adviser match {
              case id: DenizenId => DecisionOptionRef.Denizen(id)
              case id: VisionId => DecisionOptionRef.Vision(id)
            })))).toOption.get
        accepted = service.handle("powered-playability", accepted.nextSequence,
          GameCommand.ResolveWalker(actor, TreeDecision(
            s"cardplay.place.${adviser.kind}.${adviser.value}",
            ChooseOneAnswer(DecisionOptionRef.Button("adviser-faceup")))))
          .toOption.get
        played += actor
      accepted = service.handle("powered-playability", accepted.nextSequence,
        GameCommand.BeginRest(actor)).toOption.get
      service = new GameApplicationService(catalog, repository,
        warExhaustionRandomPort = new oathdigital.gameplay.phases.rest.WarExhaustionRandomPort {
          def choose(candidates: Vector[PlayerId]) = candidates.head
        })
      val reopened = service.load("powered-playability").toOption.flatten.get
      assertEquals(reopened.state, accepted.state)
      assertEquals(reopened.nextSequence, accepted.nextSequence)
    val Ready(finished) = accepted.state: @unchecked
    assert(finished.game.current.result.nonEmpty)
    val projector = new GameProjector(catalog)
    finished.game.current.players.foreach { player =>
      val view = projector.project("powered-playability",
        LoadedGame(accepted.state, accepted.nextSequence),
        player.player)
      assertEquals(view.phase, "game-over")
      assertEquals(view.legalControls, Vector.empty)
      assert(!view.actionSelectionOpen)
      assertEquals(view.oathkeeper.flatMap(_.winnerPlayerId),
        finished.game.current.result.map(_.winner.value))
      assert(view.world.flatMap(_.sites).nonEmpty)
    }
    val raw = repository.load("powered-playability").toOption.flatten.get.records
    assert(raw.exists(_.contains("diagnostic.ignored-rules-recorded")))
    assert(raw.exists(_.contains("reviewed-unimplemented-pre-alpha-fallback")))
  test("major-action preview is stateless stale-safe and rejects unavailable modifiers"):
    // p1 holds a facedown adviser and starts in Wake, so ending Wake gives
    // the stream a record to be stale against.
    val table = Table.start.turn(p1, Phase.Wake)
      .adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, repository) = table.service()
    val Ready(ready) = table.state: @unchecked
    val actor = p1
    val act = service.handle("game-preview", 0L, GameCommand.EndWake(actor))
      .toOption.get
    val before = repository.load("game-preview").toOption.flatten.get.records
    val preview = service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).toOption.get
    assertEquals(preview.loaded.nextSequence, act.nextSequence)
    assertEquals(repository.load("game-preview").toOption.flatten.get.records, before)
    assert(service.preview("game-preview", act.nextSequence - 1, actor,
      oathdigital.model.ActionKind.Travel, Vector.empty).left.toOption
      .exists(_.isInstanceOf[GameApplicationError.StaleClientPosition]))
    val forged = oathdigital.model.OrderedRuleInvocation(
      oathdigital.model.RuleSourceRef.GameRule("forged"), "unknown")
    assert(service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Travel, Vector(forged)).isLeft)
    val adviser = ready.game.current.players.find(_.player == actor).get.advisers.head.id
      .asInstanceOf[WorldCardId]
    assert(service.handle("game-preview", act.nextSequence,
      GameCommand.WithModifiers(GameCommand.StartWalker(
        ActionRef.PlayFacedownAdviser, StartPayload(actor, Vector.empty,
          Vector(adviser match {
            case id: DenizenId => DecisionOptionRef.Denizen(id)
            case id: VisionId => DecisionOptionRef.Vision(id)
          }))), Vector(forged))).isLeft)
    val challenge = service.preview("game-preview", act.nextSequence, actor,
      oathdigital.model.ActionKind.Challenge, Vector.empty).toOption.get
    assertEquals(challenge.options, Vector.empty)
    assertEquals(challenge.ignored, Vector.empty)
  test("minor adviser action persists and reloads through authoritative replay"):
    val table = Table.start.adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, repository) = table.service()
    val Ready(ready) = table.state: @unchecked
    val actor = p1
    val adviser = ready.game.current.players.find(_.player == actor).get.advisers.head.id
      .asInstanceOf[WorldCardId]
    val started = service.handle("game-minor-replay", 0L,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(adviser match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        })))).toOption.get
    // The parked walk reloads to the same state before it is answered.
    assertEquals(replayingSecond(service, repository)
      .load("game-minor-replay").toOption.flatten.get.state, started.state)
    val discarded = service.handle("game-minor-replay", started.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${adviser.kind}.${adviser.value}",
        ChooseOneAnswer(DecisionOptionRef.Button("discard"))))).toOption.get
    val beforeRetry = repository.load("game-minor-replay").toOption.flatten.get.records
    assert(service.handle("game-minor-replay", discarded.nextSequence,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty, Vector(adviser match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        })))).isLeft)
    assertEquals(repository.load("game-minor-replay").toOption.flatten.get.records,
      beforeRetry)
    val reloaded = replayingSecond(service, repository)
      .load("game-minor-replay").toOption.flatten.get
    assertEquals(reloaded.state, discarded.state)
    assertEquals(reloaded.nextSequence, discarded.nextSequence)

  test("Challenge persists its walker park and reloads deterministic Mob completion"):
    // p1 holds the 2 favor the Challenge's amount decision will spend.
    val (service, repository) = Table.start.favor(p1, 2).service()
    val gameId = "game-challenge-persistence"
    val actor = p1
    var accepted = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Challenge, StartPayload(actor)))
      .toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("challenge.banner",
        ChooseOneAnswer(DecisionOptionRef.Banner(Banner.PeoplesFavor)))))
      .toOption.get
    val reloaded = replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, accepted.state)
    val Ready(pendingReady) = reloaded.state: @unchecked
    assertEquals(pendingReady.game.current.walkerProcedure,
      Some(ActionRef.Challenge))
    val other = pendingReady.game.current.players.find(_.player != actor).get.player
    val projector = new GameProjector(catalog)
    assertEquals(projector.project(gameId, reloaded, actor).legalControls,
      Vector("resolveWalkerDecision"))
    assertEquals(projector.project(gameId, reloaded, other).legalControls,
      Vector.empty)
    val completed = replayingSecond(service, repository).handle(gameId,
      reloaded.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
        "challenge.amount", ChooseAmountAnswer(2)))).toOption.get
    val Ready(after) = completed.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 2)
    val claimed = projector.project(gameId,
      LoadedGame(completed.state, completed.nextSequence), actor)
    assert(!claimed.banners.exists(_.key == "peoples-favor"))
    assertEquals(claimed.playerBoards.find(_.playerId == actor.value).toVector
      .flatMap(_.banners).map(banner => banner.key -> banner.resources),
      Vector("peoples-favor" -> 2))

  // ---------------------------------------------------------------------------
  // Forge runs end to end on the generic walker, through
  // `StartWalker`/`ResolveWalker` alone, and its journal replays to the same
  // state. The replay is what proves the answer codec: a `PartitionAnswer`
  // rides both a `WalkerStepRecorded` ChoicePayload and the `WalkerParked`
  // fact, so the encoder is reached on append and the decoder on every
  // reload -- the in-memory run alone would not necessarily touch either.
  // ---------------------------------------------------------------------------

  test("walker Forge completes through StartWalker/ResolveWalker alone and " +
      "replays to the same final state"):
    // The only non-homeland forgeable site prints three favor, which is a
    // forced split the engine resolves without prompting (the next test
    // covers that). Overriding just that site's printed cost is what gives
    // the PARKED flow a real end-to-end run: the site, its denizens and
    // every other rule stay exactly as shipped.
    val forgeCatalog = mixedForgeCostCatalog
    // This test's own board runs under the mixed-cost override, not the
    // shared fixture `catalog`, so its parked decision must be rebuilt
    // against that same override -- `parkedAssertions` (built on `catalog`)
    // would rebuild the unmodified, unparked tree instead.
    val parkedAssertions = new ParkedDecisionAssertions(forgeCatalog,
      WalkerPowerCatalog.default(forgeCatalog), PhasePowerCatalog.default(forgeCatalog))
    val (service, repository) = forgeService(forgeCatalog)
    val gameId = "game-walker-forge"
    val actor = p1
    val Ready(beforeStart) = forgeTable.state: @unchecked
    val supplyBefore = beforeStart.game.current.players
      .find(_.player == actor).get.board.supply.supply
    val relic = beforeStart.game.current.commonCards.relicDeck.head
    val banksBefore = beforeStart.banks.favor
    val actorBefore = beforeStart.game.current.players
      .find(_.player == actor).get
    val favorBefore = actorBefore.board.favor
    val secretsBefore = actorBefore.board.faceUpSecrets

    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)
    assert(started.events.last.isInstanceOf[WalkerParked])
    parkedAssertions.assertParked(started.state, ActionRef.Forge,
      ForgeProcedure.assignmentDecisionId, actor)
    val Ready(parked) = started.state: @unchecked
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Forge))
    assertEquals(parked.game.current.players.find(_.player == actor).get
      .board.supply.supply, supplyBefore - 1)

    // The prompt the client actually answers from, reloaded through the
    // codec rather than read off the in-memory transition.
    val parkedLoaded = service.load(gameId).toOption.flatten.get
    assertEquals(parkedLoaded.state, started.state)
    val projector = new GameProjector(forgeCatalog)
    val owner = projector.project(gameId, parkedLoaded, actor)
    val other = parked.game.current.players.find(_.player != actor).get.player
    // Task 4: the prompt IS the projected query. There is no Forge-shaped
    // projection any more -- two declared sections carrying the printed
    // minima, and one denizen option per live eligible target, described
    // from the same `Decide` the walker is parked on.
    val decision = owner.walkerDecision.getOrElse(
      fail("the parked actor must be offered the Forge assignment prompt"))
    assertEquals(decision.decisionId, ForgeProcedure.assignmentDecisionId)
    val prompt = decision.query match
      case Some(partition: DecisionQueryProjection.Partition) => partition
      case other => fail(s"expected a Forge partition, got $other")
    val printed = forgeCatalog.sites.find(_.id == forgeSite).get
      .forgeRequirements.get
    assertEquals(prompt.sections, Vector(
      DecisionSectionProjection(ForgeProcedure.favorSectionKey, "Pay Favor",
        printed.favor),
      DecisionSectionProjection(ForgeProcedure.secretSectionKey, "Pay Secret",
        printed.secrets)))
    assertEquals(prompt.options.size, 3)
    assertEquals(prompt.options.map(_.kind).distinct, Vector("denizen"))
    // The options are the live eligible targets at the Forge site, with
    // their presentation card details -- never a set the projector derived
    // by consulting `ForgeProcedure` a second time.
    assertEquals(prompt.options.map(_.id).toSet,
      ForgeProcedure.eligibleTargets(parked, actor)
        .map(_.denizenId.value).toSet)
    assert(prompt.options.forall(_.card.nonEmpty))
    val favorMinimum = printed.favor
    val secretMinimum = printed.secrets
    assertEquals(favorMinimum + secretMinimum, 3)
    assertEquals(owner.phase, "forge-walker-decision")
    assertEquals(owner.legalControls, Vector("resolveWalkerDecision"))
    // Forge has no dice, and its parked decision says so. (This is a
    // client-facing fact, not R18's proof: no site in the catalog carries
    // both a printed Forge cost and a Recover difficulty, so `rollOutcome`
    // is `None` here for want of a difficulty either way. R18's projector
    // call site is proven in `WalkerDecisionProjectorSuite`.)
    assertEquals(owner.walkerDecision.flatMap(_.rollOutcome), None)
    assertEquals(projector.project(gameId, parkedLoaded, other)
      .walkerDecision, None)
    assertEquals(projector.projectPublic(gameId, parkedLoaded)
      .walkerDecision, None)

    // The answer is built from the projected prompt, exactly as the UI
    // builds it: the offered targets, in order, taking the offered counts.
    val sections = Vector.fill(favorMinimum)(ForgeProcedure.favorSectionKey) ++
      Vector.fill(secretMinimum)(ForgeProcedure.secretSectionKey)
    val placements = prompt.options.zip(sections).map { case (option, section) =>
      DecisionPlacement(DecisionOptionRef.Denizen(DenizenId(option.id)),
        section) }

    val beforeRejected = repository.load(gameId).toOption.flatten.get.records
    Vector[GameCommand](
      GameCommand.ResolveWalker(actor, TreeDecision("forge.stale",
        PartitionAnswer(placements))),
      GameCommand.ResolveWalker(other, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements))),
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements.updated(1, placements.head)))),
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId, PartitionAnswer(
          placements.map(_.copy(sectionKey = ForgeProcedure.secretSectionKey))))),
      GameCommand.BeginRest(actor)
    ).foreach(command => assert(service.handle(gameId, started.nextSequence,
      command).isLeft, s"$command must be rejected while Forge is parked"))
    assertEquals(repository.load(gameId).toOption.flatten.get.records,
      beforeRejected, "a rejected command must append nothing")

    val finished = service.handle(gameId, started.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements))))
      .fold(error => fail(s"walker Forge answer rejected: $error"), identity)
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    val Ready(after) = finished.state: @unchecked

    // The same final state the deleted legacy path produced: three
    // denizens each carrying exactly one resource, the relic facedown in the
    // actor's play area, the deck advanced by one, one Supply spent in total,
    // and nothing pending on either mechanism.
    assertEquals(after.game.current.map.sites(forgeSite).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets }, Vector(1, 1, 1))
    assertEquals(after.game.current.players.find(_.player == actor).get.relics.last,
      RelicState(relic, Orientation.FaceDown, Tokens.empty))
    assertEquals(after.game.current.commonCards.relicDeck,
      beforeStart.game.current.commonCards.relicDeck.drop(1))
    assertEquals(after.game.current.players.find(_.player == actor).get
      .board.supply.supply, supplyBefore - 1)
    assert(after.game.current.walkerPending.isEmpty)
    assert(after.game.current.walkerProcedure.isEmpty)

    // The actor funded the whole printed cost out of their own play area,
    // and no suit bank moved at all. This reverses the pre-walker behaviour
    // of drawing each favor from the target denizen's own suit bank.
    assertEquals(after.banks.favor, banksBefore)
    val actorAfter = after.game.current.players.find(_.player == actor).get
    assertEquals(actorAfter.board.favor, favorBefore - favorMinimum)
    assertEquals(actorAfter.board.faceUpSecrets,
      secretsBefore - secretMinimum)

    // P2: reconstructing purely from the journal reproduces that state.
    val replayed = new GameApplicationService(forgeCatalog, repository,
      genesis = service.genesis).load(gameId).toOption.flatten.get
    assertEquals(replayed.state, finished.state)

    // The forged relic stays private to its owner.
    val otherJson = GameHttpWire.encodeProjection(
      projector.project(gameId, replayed, other))
    assert(!otherJson.contains(relic.value))
    assert(!GameHttpWire.encodeProjection(
      projector.projectPublic(gameId, replayed)).contains(relic.value))

  test("a Forge whose printed cost is three of one resource completes in " +
      "the command that starts it, with no decision to answer"):
    val (service, repository) = forgeService()
    val gameId = "game-walker-forge-forced"
    val actor = p1
    val Ready(beforeStart) = forgeTable.state: @unchecked
    val cost = catalog.sites.find(_.id == forgeSite).get.forgeRequirements.get
    assert(cost.favor == 0 || cost.secrets == 0,
      s"this test needs a single-resource printed cost, got $cost")
    val relic = beforeStart.game.current.commonCards.relicDeck.head
    val actorBefore = beforeStart.game.current.players
      .find(_.player == actor).get
    val banksBefore = beforeStart.banks.favor

    val finished = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)

    // One command: the action completes, nothing parks, and the client is
    // never asked to confirm a split it could not have got wrong.
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assert(!finished.events.exists(_.isInstanceOf[WalkerParked]))
    parkedAssertions.assertResumed(finished.state, Phase.Act, actor)
    val Ready(after) = finished.state: @unchecked
    assert(after.game.current.walkerPending.isEmpty)
    assert(after.game.current.walkerProcedure.isEmpty)
    assertEquals(new GameProjector(catalog).project(gameId,
      LoadedGame(finished.state, finished.nextSequence), actor)
      .walkerDecision, None)

    // And the determined split really was applied, out of the actor's own
    // play area.
    assertEquals(after.game.current.map.sites(forgeSite).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets }, Vector(1, 1, 1))
    assertEquals(after.banks.favor, banksBefore)
    val actorAfter = after.game.current.players.find(_.player == actor).get
    assertEquals(actorAfter.board.favor, actorBefore.board.favor - cost.favor)
    assertEquals(actorAfter.board.faceUpSecrets,
      actorBefore.board.faceUpSecrets - cost.secrets)
    assertEquals(actorAfter.relics.last,
      RelicState(relic, Orientation.FaceDown, Tokens.empty))

    // The journal replays to the same state even though it carries a
    // completion that was never preceded by a park.
    assertEquals(replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get.state, finished.state)

  /** Setup driven through `service`, for the tests that stay on the real
    * start. */
  private def execute(
      service: GameApplicationService,
      gameId: String,
      placementSites: Vector[oathdigital.model.SiteId] = sites,
      setupChronicle: Chronicle = chronicle,
      setupOrders: SetupOrders = orders
  ): GameAccepted =
    ParkedServiceFixture.setUp(service, gameId, placementSites,
      setupChronicle, setupOrders)

  test("create advance and reload replay the complete persisted stream"):
    // Stays on the replayed setup: it checks the length of Setup's journal.
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val accepted = execute(service, "game-v2")
    val reloaded = new GameApplicationService(catalog, repository)
      .load("game-v2").toOption.flatten.get

    assertEquals(reloaded.state, accepted.state)
    // Setup now runs as an ordinary triggered walker procedure (2026-09-21
    // Chronicle design, slice 2): `GameStarted` plus a `WalkerParked`/
    // `WalkerStepRecorded`/`WalkerCompleted` fact per player decision and
    // delta, not one event per legacy setup command. The first Wake has
    // nothing to decide, so its End Wake step and completion follow.
    assertEquals(reloaded.nextSequence, 29L)
    assert(reloaded.state.isInstanceOf[Ready])
    val records = repository.load("game-v2").toOption.flatten.get.records
    assertEquals(records.size, 29)
    assert(records.forall(record =>
      ujson.read(record)("formatVersion").num.toInt ==
        GameEventWire.FormatVersion))

  test("gameplay appends at the absolute position and reloads equally"):
    // p2 wakes at Broken Peaks, whose 2 secrets are its only Wake option.
    val (service, repository) = Table.start.turn(p2, Phase.Wake)
      .siteTokens("Broken Peaks", secrets = 2)
      // Bandits at every empty site keep the refill out of the five events.
      .banditsRefilled.service()
    val active = p2
    val siteId = Table.homeOf(p2)

    val wealth = service.handle("game-wake", 0L,
      GameCommand.StartWalker(ActionRef.TakeWealth, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button("secret"))))).toOption.get
    // Take Wealth is one atomic walker command (batch-1 Task 7): the resource
    // move, the use record and the completion. It was Wake's only option, so
    // Wake ends in the same command: its phase-change step and completion
    // follow, and the stream holds five events.
    assertEquals(wealth.nextSequence, 5L)
    assertEquals(
      service.handle("game-wake", 0L, GameCommand.EndWake(active)),
      Left(GameApplicationError.StaleClientPosition(0L, 5L))
    )
    val reloaded = replayingSecond(service, repository)
      .load("game-wake").toOption.flatten.get
    val Ready(after) = reloaded.state: @unchecked

    assertEquals(reloaded.state, wealth.state)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    assert(after.game.current.turn.usedPowers.contains(
      oathdigital.gameplay.powers.wake.TakeWealthLimit.useRef(siteId)),
      "the replayed journal must restore the use limit it recorded")
    val records = repository.load("game-wake").toOption.flatten.get.records
    assertEquals(records.map(record =>
      ujson.read(record)("formatVersion").num.toInt), Vector(1, 1, 1, 1, 1))
    // Ending Wake is a walker procedure too (batch-1 Task 7), so the Wake
    // phase journals nothing of its own: the last two records are its
    // phase-change step and its completion, not a `gameplay.wake-ended`.
    assertEquals(records.map(record =>
      ujson.read(record)("eventType").str),
      Vector("walker.step-recorded", "walker.step-recorded",
        "walker.completed", "walker.step-recorded", "walker.completed"))

  test("Travel is one atomic walker command and reloads pawn Supply and Act"):
    // Bandits at every empty site, so the Travel's own events are the only
    // ones journaled: no refill follows it.
    val table = Table.start.banditsRefilled
    val (service, repository) = table.service()
    val active = p1
    val Ready(inAct) = table.state: @unchecked
    val before = inAct.game.current.players.find(_.player == active).get
    val destination = inAct.game.current.map.cradle.find(
      !before.pawnSite.contains(_)).getOrElse(
        inAct.game.current.map.provinces.head)
    val traveled = service.handle("game-travel", 0L,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Site(destination))))
      ).toOption.get
    val loaded = replayingSecond(service, repository)
      .load("game-travel").toOption.flatten.get
    val Ready(after) = loaded.state: @unchecked
    val moved = after.game.current.players.find(_.player == active).get

    assertEquals(loaded.state, traveled.state)
    assertEquals(moved.pawnSite, Some(destination))
    assert(moved.board.supply.supply < before.board.supply.supply)
    assertEquals(after.game.current.turn.phase, Phase.Act)
    // The single legacy travel event became the walker's own three: the pay
    // node, the pawn move, and the completion boundary. That is the whole
    // observable shape change of the port -- the state each side reaches is
    // identical, which is what the assertions above pin.
    assertEquals(loaded.nextSequence, 3L)
    assertEquals(traveled.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerStepRecorded", "WalkerCompleted"))
    // Nothing parked: a flat tree finishes inside the command that started it.
    assertEquals(after.game.current.walkerPending, None)
    assertEquals(after.game.current.walkerProcedure, None)
    val types = repository.load("game-travel").toOption.flatten.get.records
      .takeRight(3).map(ujson.read(_)("eventType").str)
    assertEquals(types, Vector("walker.step-recorded", "walker.step-recorded",
      "walker.completed"))

  /** An off-turn Oathkeeper tie, reached and resolved entirely through
    * `GameApplicationService`, survives reload on both sides of the park.
    *
    * `ParkedServiceFixture.oathkeeperTiePark` states the board as a table:
    * p3 holds the title and p1 and p2 each rule one site. With exactly three
    * players, the active player is one of the two tied leaders and the
    * holder is off-turn, so p1's Travel reaches `OathkeeperRules.outcome`'s
    * `Choose`.
    */
  test("an off-turn Oathkeeper tie parks through the application service " +
      "and survives reload"):
    val gameId = "game-oathkeeper-tie"
    val (game, active, holder, leaderB) =
      ParkedServiceFixture.oathkeeperTiePark(gameId)
    val (service, repository, parked) = (game.service, game.repository,
      game.accepted)
    assertEquals(
      repository.load(gameId).toOption.flatten.get.records.size.toLong,
      parked.nextSequence)

    val reloadedParked = service.load(gameId).toOption.flatten.get
    assertEquals(reloadedParked.state, parked.state)
    assertEquals(reloadedParked.nextSequence, parked.nextSequence)

    // The application gate rejects the active player's answer: only the
    // holder may resolve the recipient decision.
    assertEquals(
      service.handle(gameId, parked.nextSequence, GameCommand.ResolveWalker(
        active, TreeDecision(OathkeeperProcedure.recipientDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))),
      Left(GameApplicationError.CommandRejected(
        WrongPlayer(holder, active))))

    val resolved = service.handle(gameId, parked.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        OathkeeperProcedure.recipientDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Player(leaderB))))).toOption.get
    assert(resolved.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    }, "the recorded choice must be answered by the holder")
    parkedAssertions.assertResumed(resolved.state, Phase.Act, active)
    val Ready(afterResolved) = resolved.state: @unchecked
    assertEquals(afterResolved.game.current.title,
      OathkeeperState(Some(leaderB), TitleSide.Oathkeeper))

    val reloadedResolved = service.load(gameId).toOption.flatten.get
    assertEquals(reloadedResolved.state, resolved.state)
    assertEquals(reloadedResolved.nextSequence, resolved.nextSequence)

  test("a walker Muster on an edifice persists, reloads and replays with its kind"):
    val siteId = CatalogNames.site("Deep Woods")
    val edificeId = CatalogNames.edifice("Hiding Place")
    val (service, repository) = Table.start.pawn(p1, siteId)
      .edifice("Hiding Place", EdificeSide.Ruined, siteId).service()
    val active = p1
    val started = service.handle("game-walker-economy", 0L,
      GameCommand.StartWalker(ActionRef.Muster, StartPayload(active))).toOption.get
    parkedAssertions.assertParked(started.state, ActionRef.Muster,
      MusterProcedure.decisionId, active)
    val mustered = service.handle("game-walker-economy", started.nextSequence,
      GameCommand.ResolveWalker(active, TreeDecision(MusterProcedure.decisionId,
        ChooseOneAnswer(DecisionOptionRef.Edifice(edificeId))))).toOption.get
    val loaded = replayingSecond(service, repository)
      .load("game-walker-economy").toOption.flatten.get
    assertEquals(loaded.state, mustered.state)
    val Ready(after) = loaded.state: @unchecked
    assertEquals(after.game.current.map.sites(siteId).denizens.collectFirst {
      case value: EdificeState if value.id == edificeId => value.tokens
    }, Some(Tokens(1, 0)))
    val records = repository.load("game-walker-economy").toOption.flatten.get.records
    assert(records.exists(record => record.contains("edifice") &&
      record.contains(edificeId.value)),
      "the journalled answer must spell the edifice kind and id")

  test("Search draw port cannot inject card identities inconsistent with state"):
    val port = new SearchDrawPort:
      def prepare(ready: ReadyGame, source: SearchSource, origin: Region): Either[OathViolation, Vector[WorldCardId]] =
        Right(Vector(DenizenId("denizen:tampered")))
    // Built by hand: `Table#service` takes no draw port.
    val repository = new InMemoryEventStreamRepository
    val table = Table.start
    val service = new GameApplicationService(catalog, repository, port,
      genesis = table.state)
    val active = p1
    assertEquals(service.handle("game-search-tamper", 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .left.toOption, Some(GameApplicationError.CommandRejected(OathViolation.SearchDrawMismatch(
        "prepared draw does not match authoritative source order"))))
    assertEquals(service.load("game-search-tamper").toOption.flatten.get
      .nextSequence, 0L)
    assertEquals(repository.load("game-search-tamper"), Right(None))

  test("walker Search persists its card choice and completes after reload"):
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, repository) = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .service()
    val gameId = "game-walker-search"
    val actor = p1
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .toOption.get
    val reloaded = replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get
    assertEquals(reloaded.state, started.state)
    val Ready(afterDraw) = reloaded.state: @unchecked
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val chosen = if drawn.size == 1 then started else
      def ref(card: WorldCardId): DecisionOptionRef = card match
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      val assignments = Vector(DecisionPlacement(ref(drawn.head), "keep")) ++
        drawn.tail.map(card => DecisionPlacement(ref(card), "discard"))
      service.handle(gameId, reloaded.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
          DecisionAnswer.PartitionAnswer(assignments)))).toOption.get
    val completed = service.handle(gameId, chosen.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(
        s"cardplay.place.${drawn.head.kind}.${drawn.head.value}",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard")))))
      .toOption.get
    assertEquals(replayingSecond(service, repository)
      .load(gameId).toOption.flatten.get.state, completed.state)

  test("Wake projection is actor-private and Act boundary is informational"):
    // p2 wakes at Broken Peaks, where no other pawn stands. Its secrets are a
    // Wake option, so Wake waits for the player. Its relic, a bandit and
    // p2's facedown adviser (any facedown adviser will do) are what Act then
    // offers: Recover, a Campaign, a peek and the minor action.
    val table = Table.start.turn(p2, Phase.Wake)
      .siteTokens("Broken Peaks", secrets = 2)
      .relicAt("Sticky Fire", "Broken Peaks")
      .bandits("Broken Peaks", 1)
      .adviser(p2, "Birdsong", facedown = true)
    val (service, _) = table.service()
    val active = p2
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(table.state, 0L)

    val own = projector.project("game-projection-wake", loaded, active)
    val public = projector.projectPublic("game-projection-wake", loaded)
    assertEquals(own.phase, "wake")
    assert(own.legalControls.contains("endWake"))
    assertEquals(public.legalControls, Vector.empty)
    assert(own.activePlayerResources.nonEmpty)
    assert(own.currentSiteResources.nonEmpty)

    val ended = service.handle(
      "game-projection-wake",
      0L,
      GameCommand.EndWake(active)
    ).toOption.get
    val act = projector.project(
      "game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence),
      active
    )
    assertEquals(act.phase, "act-action-selection")
    assert(act.actionSelectionOpen)
    // Broken Peaks also holds a relic: Recover and a peek are offered.
    assertEquals(act.legalControls, Vector("beginRest", "beginRecover",
      "beginCampaign", "facedownAdviserMinorAction", "peekSiteRelics"))
    assert(act.boardTargetActions.exists(_.actionKind == "travel"))
    val travel = act.boardTargetActions.find(_.actionKind == "travel").get
    assertEquals(travel.minimum -> travel.maximum, 1 -> 1)
    assert(!travel.autoActivate)
    assert(travel.candidates.forall(_.target.isInstanceOf[
      BoardTargetRefProjection.Site]))
    assert(travel.candidates.forall(_.details.exists(_.endsWith("Supply"))))
    // Each destination is priced by dry-running the Travel tree.
    val Ready(atAct) = ended.state: @unchecked
    assertEquals(
      travel.candidates.map(candidate => candidate.target -> candidate.details),
      TravelProcedure.candidates(catalog, atAct, active,
        WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty))
        .map((site, cost) =>
          BoardTargetRefProjection.Site(site.value) -> Vector(s"$cost Supply")))
    assertEquals(projector.projectPublic("game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence)).boardTargetActions,
      Vector.empty)

  test("site projection exposes ordered public properties without relic identity"):
    // A projection needs only a ready game, so the base is the quiet table
    // and every fact the test asserts is patched in below. `Table` has no
    // step for Imperial warbands or for a site's loose favor and secrets.
    val ready = Table.start.ready
    val siteId = ready.game.current.map.cradle.head
    val emptySiteId = ready.game.current.map.cradle(1)
    val imperialSiteId = ready.game.current.map.provinces.head
    val banditSiteId = ready.game.current.map.provinces(1)
    val otherPlayerSiteId = ready.game.current.map.provinces(2)
    val definition = catalog.sites.find(_.id == siteId).get
    val denizenDefinitions = catalog.denizens.take(2)
    val relicDefinitions = catalog.relics.take(2)
    val activePlayer = ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get
    val otherPlayer = ready.game.current.players.find(_ != activePlayer).get
    val populated = ready.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(activePlayer.lineage), 2),
      denizens = denizenDefinitions.reverse.map(definition =>
        DenizenState(
          DenizenId(definition.id.value),
          Orientation.FaceUp,
          Tokens.empty
        )),
      relics = relicDefinitions.map(definition =>
        RelicState(
          RelicId(definition.id.value),
          Orientation.FaceDown,
          Tokens.empty
        )),
      tokens = Tokens(2, 1)
    )
    val current = ready.game.current.copy(
      commonCards = ready.game.current.commonCards.copy(
        regionalDiscards = Map(
          Region.Cradle -> Vector(DenizenId(denizenDefinitions.head.id.value)),
          Region.Provinces -> Vector(VisionId("vision:conquest")),
          Region.Hinterland -> Vector.empty)),
      map = ready.game.current.map.copy(
        sites = ready.game.current.map.sites
          .updated(siteId, populated)
          .updated(
            emptySiteId,
            ready.game.current.map.sites(emptySiteId).copy(
              forces = SiteForces.Empty,
              denizens = Vector.empty,
              relics = Vector.empty,
              tokens = Tokens.empty
            )
          )
          .updated(imperialSiteId, ready.game.current.map.sites(imperialSiteId)
            .copy(forces = SiteForces.Occupied(ForceKind.Imperial, 1)))
          .updated(banditSiteId, ready.game.current.map.sites(banditSiteId)
            .copy(forces = SiteForces.Occupied(ForceKind.Bandit, 3)))
          .updated(otherPlayerSiteId, ready.game.current.map.sites(otherPlayerSiteId)
            .copy(forces = SiteForces.Occupied(
              ForceKind.Exile(otherPlayer.lineage), 4)))
      )
    )
    val loaded = LoadedGame(
      Ready(ready.copy(game = ready.game.copy(current = current))),
      0L
    )
    val projector = new GameProjector(catalog)
    val own = projector.project("game-site-details", loaded,
      current.turn.activePlayer)
    val public = projector.projectPublic("game-site-details", loaded)
    val site = own.world.flatMap(_.sites).find(_.siteId == siteId.value).get
    val empty = own.world.flatMap(_.sites)
      .find(_.siteId == emptySiteId.value).get

    assertEquals(site.looseFavor, 2)
    assertEquals(site.looseSecrets, 1)
    assertEquals(site.denizenCapacity, definition.capacity)
    assertEquals(site.relicCapacity, definition.relicSlots)
    assertEquals(
      site.denizens.map(card => card.cardId -> card.label),
      denizenDefinitions.reverse.map(definition =>
        definition.id.value -> definition.name)
    )
    assertEquals(site.relics.facedownCount, 2)
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
    assertEquals(site.forces, Some(SiteForcesProjection.Exile(2,
      activePlayer.player.value, ready.playerColors(activePlayer.player),
      s"${ready.playerColors(activePlayer.player).key.capitalize} Warbands")))
    assertEquals(empty.forces, None)
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == imperialSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection.Imperial(1,
      "Imperial Warbands")))
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == banditSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection.Bandit(3,
      "Bandit Warbands")))
    val otherColor = ready.playerColors(otherPlayer.player)
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == otherPlayerSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection.Exile(4,
      otherPlayer.player.value, otherColor,
      s"${otherColor.key.capitalize} Warbands")))
    val wire = ujson.read(GameHttpWire.encodeProjection(own))
    val wireSites = wire("world").arr.flatMap(_("sites").arr)
    val wirePlayerForces = wireSites.find(_("siteId").str == siteId.value)
      .get("forces")
    assertEquals(wirePlayerForces.obj.keySet,
      Set("forceKind", "count", "rulerKind", "rulerPlayerId", "label",
        "colorToken"))
    assertEquals(wirePlayerForces("rulerPlayerId").str, activePlayer.player.value)
    assertEquals(wireSites.find(_("siteId").str == emptySiteId.value)
      .get("forces"), ujson.Null)
    assertEquals(public.world, own.world)
    assertEquals(own.world.map(region => region.regionId ->
      region.discardTopCardKind).toMap,
      Map("cradle" -> Some("denizen"), "provinces" -> Some("vision"),
        "hinterland" -> None))
    assertEquals(own.worldDeckTopCardKind,
      current.commonCards.worldDeck.headOption.map(_.kind))
    def worldTop(deck: Vector[WorldCardId]) = projector.project(
      "game-world-top", LoadedGame(Ready(ready.copy(game = ready.game.copy(
        current = current.copy(commonCards = current.commonCards.copy(
          worldDeck = deck))))), 0L), current.turn.activePlayer)
      .worldDeckTopCardKind
    assertEquals(worldTop(Vector(VisionId("vision:conquest"))), Some("vision"))
    assertEquals(worldTop(Vector.empty), None)
    own.world.flatMap(_.sites).foreach { projected =>
      val source = catalog.sites.find(_.id.value == projected.siteId).get
      if source.capacity == 3 then
        assertEquals(projected.recoverDifficulty, None)
        assertEquals(projected.forgeCost.map(cost =>
          Tokens(cost.favor, cost.secrets)), source.forgeRequirements)
      else
        assertEquals(projected.forgeCost, None)
        assertEquals(projected.recoverDifficulty, source.recoverDifficulty)
    }

    val json = oathdigital.server.GameHttpWire.encodeProjection(public)
    assert(json.contains("\"looseFavor\":2"))
    assert(json.contains("\"facedownCount\":2"))
    assert(json.contains("\"discardTopCardKind\":\"denizen\""))
    current.commonCards.worldDeck.headOption.foreach(card =>
      assert(json.contains(s"\"worldDeckTopCardKind\":\"${card.kind}\"")))
    relicDefinitions.foreach(relic => assert(!json.contains(relic.id.value)))

  test("stale expected position rejects a command legal on current state"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    service.handle("game-stale-v2", 0L, GameCommand.Begin(chronicle, orders))
    service.handle(
      "game-stale-v2",
      1L,
      GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
        SetupProcedure.pawnDecisionId(PlayerId("p2")),
        ChooseOneAnswer(DecisionOptionRef.Site(sites.head))))
    )

    assertEquals(
      service.handle(
        "game-stale-v2",
        1L,
        GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
          SetupProcedure.adviserDecisionId(PlayerId("p2")),
          ChooseOneAnswer(DecisionOptionRef.Denizen(DenizenId("92")))))
      ),
      Left(GameApplicationError.StaleClientPosition(1L, 2L))
    )
    assertEquals(
      repository.load("game-stale-v2").toOption.flatten.get.nextSequence,
      2L
    )

  test("malformed v1 envelopes are rejected without reinterpretation"):
    val repository = new InMemoryEventStreamRepository
    repository.seed(
      "game-v1",
      Vector(ujson.write(ujson.Obj("formatVersion" -> 1)))
    )
    val result = new GameApplicationService(catalog, repository)
      .load("game-v1")

    assert(result.left.toOption.get match {
      case GameApplicationError.CodecFailure(_) => true
      case _ => false
    })

  test("replay violations report the exact index and append nothing"):
    val repository = new InMemoryEventStreamRepository
    val badDiagnostics = Vector(IgnoredRuleDiagnostic(
      RuleSourceRef.Adviser(PlayerId("p2"), DenizenId("insomnia")),
      "denizen.insomnia", ActionKind.Rest, RuleTiming.Trigger,
      "corrupted for the test"))
    val records = Vector(
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        0L,
        OathEvent.GameStarted(chronicle, orders)
      ).toOption.get,
      GameEventWire.encodeEvent(
        "game-replay-corrupt",
        1L,
        OathEvent.IgnoredRulesRecorded(PlayerId("p2"), ActionKind.Rest,
          badDiagnostics)
      ).toOption.get
    ).map(ujson.write(_))
    repository.seed("game-replay-corrupt", records)

    assertEquals(
      new GameApplicationService(catalog, repository).handle(
        "game-replay-corrupt",
        2L,
        GameCommand.EndWake(PlayerId("p2"))
      ),
      Left(GameApplicationError.ReplayFailure(
        1L,
        oathdigital.model.OathViolation.InvalidEventOrder(
          "ignored-rule diagnostics do not match authoritative discovery")
      ))
    )
    assertEquals(
      repository.load("game-replay-corrupt").toOption.flatten.get.records,
      records
    )

  test("repository and envelope game identity mismatches append nothing"):
    def repositoryFor(
        storedGameId: String,
        envelopeGameId: String
    ): (EventStreamRepository, () => Int) =
      var appendCalls = 0
      val record = GameEventWire.encodeEvent(
        envelopeGameId,
        0L,
        OathEvent.GameStarted(chronicle, orders)
      ).toOption.get
      val repository = new EventStreamRepository:
        override def load(gameId: String): Either[RepositoryFailure, Option[StoredEventStream]] = Right(Some(StoredEventStream(
          storedGameId,
          Vector(ujson.write(record))
        )))
        override def append(
            gameId: String,
            expected: ExpectedStream,
            records: Vector[String]
        ): Either[RepositoryFailure, RepositoryAppendResult] =
          appendCalls += 1
          Right(RepositoryAppendResult.Appended(1L, records.size))
      repository -> (() => appendCalls)

    Vector(
      repositoryFor("game-b", "game-b"),
      repositoryFor("game-a", "game-b")
    ).foreach { case (repository, appendCalls) =>
      assertEquals(
        new GameApplicationService(catalog, repository).handle(
          "game-a",
          1L,
          GameCommand.EndWake(PlayerId("p2"))
        ),
        Left(GameApplicationError.StreamIdentityMismatch(
          "game-a",
          "game-b"
        ))
      )
      assertEquals(appendCalls(), 0)
    }

  test("authoritative history cannot omit sequence zero"):
    val repository = new InMemoryEventStreamRepository
    val record = GameEventWire.encodeEvent(
      "game-missing-zero",
      1L,
      OathEvent.GameStarted(chronicle, orders)
    ).toOption.get
    repository.seed("game-missing-zero", Vector(ujson.write(record)))

    assertEquals(
      new GameApplicationService(catalog, repository)
        .load("game-missing-zero"),
      Left(GameApplicationError.CodecFailure(
        EventCodecFailure("invalid-sequence", "$[0].sequence",
          "expected 0 but found 1")
      ))
    )

  /** The hand is a preview, not a question: it is drawn while the walker is
    * asking about something else (the starting site), and goes quiet the
    * moment the same cards become the options of a decision, so the panel
    * never draws one card twice.
    */
  test("a temporary hand previews to its holder until it is asked about"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = service.handle("game-hand", 0L,
      GameCommand.Begin(chronicle, orders)).toOption.get
    val projector = new GameProjector(catalog)
    val waiting = LoadedGame(begun.state, begun.nextSequence)
    val holder = PlayerId("p2")
    val hand = projector.project("game-hand", waiting, holder)
      .temporaryHandPreview

    assertEquals(hand.size, 3)
    assert(hand.forall(_.orientation.contains("face-up")), hand.toString)
    assert(hand.forall(!_.hidden), hand.toString)
    // Every seat was dealt a hand, and each one is shown only its own.
    val otherHand = projector.project("game-hand", waiting, PlayerId("p1"))
      .temporaryHandPreview
    assertEquals(otherHand.size, 3)
    assertEquals(otherHand.map(_.cardId).intersect(hand.map(_.cardId)),
      Vector.empty)
    assertEquals(projector.projectPublic("game-hand", waiting)
      .temporaryHandPreview, Vector.empty)

    val placed = service.handle("game-hand", begun.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        SetupProcedure.pawnDecisionId(holder),
        ChooseOneAnswer(DecisionOptionRef.Site(sites.head))))).toOption.get
    val asked = projector.project("game-hand",
      LoadedGame(placed.state, placed.nextSequence), holder)

    assert(asked.walkerDecision.exists(
      _.query.exists(_.offeredOptions.nonEmpty)))
    assertEquals(asked.temporaryHandPreview, Vector.empty)

  /** A placement asks about a card with buttons, never with the card itself.
    * A Search's kept card is both in the temporary hand and the subject of
    * the parked placement, and the panel is handed it once.
    */
  test("a Search's kept card is previewed once while it is being placed"):
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, _) = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .service()
    val gameId = "game-search-preview"
    val actor = p1
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .toOption.get
    val Ready(afterDraw) = started.state: @unchecked
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val kept = drawn.head
    val chosen = if drawn.size == 1 then started else
      def ref(card: WorldCardId): DecisionOptionRef = card match
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      val assignments = Vector(DecisionPlacement(ref(kept), "keep")) ++
        drawn.tail.map(card => DecisionPlacement(ref(card), "discard"))
      service.handle(gameId, started.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
          DecisionAnswer.PartitionAnswer(assignments)))).toOption.get
    val placing = new GameProjector(catalog).project(gameId,
      LoadedGame(chosen.state, chosen.nextSequence), actor)

    assertEquals(placing.walkerDecision.map(_.decisionId),
      Some(s"cardplay.place.${kept.kind}.${kept.value}"))
    assertEquals(placing.temporaryHandPreview.map(_.cardId), Vector(kept.value))

  /** A facedown adviser is played from the board, so the hand holds nothing;
    * the card being placed is previewed all the same, from where it lies.
    */
  test("a facedown adviser being played is previewed from the board"):
    val table = Table.start.adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, _) = table.service()
    val gameId = "game-facedown-preview"
    val actor = p1
    val adviser = table.ready.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id
      }.get
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty,
          Vector(DecisionOptionRef.Denizen(adviser))))).toOption.get
    val Ready(parked) = started.state: @unchecked
    assertEquals(parked.game.current.temporaryHands.getOrElse(actor,
      Vector.empty), Vector.empty)
    val placing = new GameProjector(catalog).project(gameId,
      LoadedGame(started.state, started.nextSequence), actor)

    assertEquals(placing.walkerDecision.map(_.decisionId),
      Some(s"cardplay.place.${adviser.kind}.${adviser.value}"))
    assertEquals(placing.temporaryHandPreview.map(c => (c.cardId, c.hidden)),
      Vector((adviser.value, false)))

  test("player projection redacts other adviser hands and hidden orders"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = service.handle("game-private", 0L,
      GameCommand.Begin(chronicle, orders)).toOption.get
    val placed = service.handle(
      "game-private",
      begun.nextSequence,
      GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
        SetupProcedure.pawnDecisionId(PlayerId("p2")),
        ChooseOneAnswer(DecisionOptionRef.Site(sites.head))))
    ).toOption.get
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(placed.state, placed.nextSequence)
    val own = projector.project("game-private", loaded, PlayerId("p2"))
    val other = projector.project("game-private", loaded, PlayerId("p1"))
    val privateIds = own.walkerDecision.toVector.flatMap(_.query.toVector
      .flatMap(_.offeredOptions)).map(_.id)
    val otherJson = oathdigital.server.GameHttpWire
      .encodeProjection(other)

    assertEquals(privateIds.size, 3)
    assertEquals(own.temporaryHandPreview, Vector.empty)
    assertEquals(other.walkerDecision, None)
    val publicShape = ujson.read(otherJson).obj
    assert(!publicShape.contains("privateAdviserChoices"))
    assert(!publicShape.contains("pendingSearch"))
    assert(publicShape.contains("walkerWaiting"))
    privateIds.foreach(id => assert(!otherJson.contains(id)))
    val exposedValues = jsonStrings(ujson.read(otherJson))
    assertEquals(
      exposedValues.intersect(chronicle.relicDeck.map(_.value).toSet),
      Set.empty[String]
    )
    assertEquals(
      exposedValues.intersect(orders.worldDeckOrder.map(_.value).toSet),
      Set.empty[String]
    )
    val chosen = service.handle("game-private", placed.nextSequence,
      GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
        SetupProcedure.adviserDecisionId(PlayerId("p2")),
        PartitionAnswer(
          DecisionPlacement(DecisionOptionRef.Denizen(DenizenId(privateIds.head)),
            SetupProcedure.adviserKeepKey) +:
          privateIds.tail.map(id => DecisionPlacement(
            DecisionOptionRef.Denizen(DenizenId(id)),
            SetupProcedure.adviserDiscardKey))))))
      .toOption.get
    // Reveal Cards follows while p2 holds a facedown adviser it may reveal:
    // everyone else is told who is deciding, and nothing names the card.
    val kept = privateIds.head
    val revealing =
      if catalog.denizen(DenizenId(kept)).exists(_.isInstanceOf[oathdigital.catalog.SiteOnly]) then chosen
      else
        val waiting = projector.projectPublic("game-private",
          LoadedGame(chosen.state, chosen.nextSequence))
        assertEquals(waiting.walkerWaiting.map(_.playerId), Some("p2"))
        assert(!GameHttpWire.encodeProjection(waiting).contains(kept))
        service.handle("game-private", chosen.nextSequence,
          GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
            SetupProcedure.revealDecisionId(PlayerId("p2")),
            ChooseManyAnswer(Vector.empty)))).toOption.get
    val continuedPublic = projector.projectPublic("game-private",
      LoadedGame(revealing.state, revealing.nextSequence))
    val continued = projector.project("game-private",
      LoadedGame(revealing.state, revealing.nextSequence),
      PlayerId(continuedPublic.walkerWaiting.get.playerId))
    assertEquals(continued.phase, "setup-walker-decision")
    assertEquals(continued.world.map(_.discardCount).sum, 8)
    // The next park is the following player's own pawn placement (all 8
    // in-play sites), not another adviser choice.
    assertEquals(continued.walkerDecision.get.query.get.offeredOptions.size,
      8)

  private def jsonStrings(value: ujson.Value): Set[String] =
    value match
      case ujson.Str(text) => Set(text)
      case obj: ujson.Obj =>
        obj.value.valuesIterator.flatMap(jsonStrings).toSet
      case array: ujson.Arr =>
        array.value.iterator.flatMap(jsonStrings).toSet
      case _ => Set.empty

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
