package oathdigital.application

import oathdigital.model._
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.gameplay.WalkerDiceFixture
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  RelicAcquired, SupplySpent}
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.OathRules
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import ParkedServiceFixture.{parkedAssertions, replayingSecond,
  setupViaService}

/** The application service drives a walker Recover, its dice and Catacombs
  * through persisted parks and reloads.
  */
class GameApplicationServiceRecoverSuite extends munit.FunSuite:
  private val catacombsId = DenizenId(catalog.denizens.find(_.powers.exists(
    _.id.value == "denizen.catacombs")).get.id.value)

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
    val walkerSetup = setupViaService(walkerService, "walker-cross-roll-doubler",
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
    // Its printed cost travels with it, so the panel can show it.
    assertEquals(modifier.cost, Some("1 secret"))

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
