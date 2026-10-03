package oathdigital.application

import oathdigital.protocol.projection.{DecisionQueryProjection,
  DecisionSectionProjection}
import oathdigital.model._
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted,
  WalkerParked}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model.DecisionAnswer.PartitionAnswer
import oathdigital.server.GameHttpWire
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.application.ForgeWalkerFixture.{forgeService, forgeSite,
  forgeTable, mixedForgeCostCatalog}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Table.p1
import ParkedServiceFixture.{parkedAssertions, replayingSecond}

/** The application service drives a walker Forge through
  * `StartWalker`/`ResolveWalker` alone, and its journal replays to the same
  * state.
  */
class GameApplicationServiceForgeSuite extends munit.FunSuite:
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
