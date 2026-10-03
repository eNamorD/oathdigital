package oathdigital.application

import oathdigital.protocol.projection.{DecisionQueryProjection,
  DecisionSectionProjection, GameProjection}
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
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.present
import ParkedServiceFixture.{loaded, parkedAssertions, replayingSecond}

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

  /** The parked Forge's journal and the position it stopped at. */
  private final case class ParkedForgeRun(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, started: GameAccepted)

  /** p1's Forge at [[forgeSite]] under [[mixedForgeCostCatalog]], parked on
    * its assignment decision. The only non-homeland forgeable site prints
    * three favor, which is a forced split the engine resolves without
    * prompting (the last test covers that). Overriding just that site's
    * printed cost to 2 favor and 1 secret is what gives the PARKED flow a
    * real end-to-end run: the site, its denizens and every other rule stay
    * exactly as shipped. */
  private def parkedForgeRun(gameId: String)(using munit.Location)
      : ParkedForgeRun =
    val (service, repository) = forgeService(mixedForgeCostCatalog)
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(p1)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)
    ParkedForgeRun(service, repository, started)

  /** The Forge's parked decision, rebuilt against the mixed-cost override:
    * `parkedAssertions` (built on `catalog`) would rebuild the unmodified,
    * unparked tree instead. */
  private val forgeParkedAssertions = new ParkedDecisionAssertions(
    mixedForgeCostCatalog, WalkerPowerCatalog.default(mixedForgeCostCatalog),
    PhasePowerCatalog.default(mixedForgeCostCatalog))

  /** The projection `viewer` gets at the stream's current position. */
  private def projected(run: ParkedForgeRun, gameId: String,
      viewer: PlayerId)(using munit.Location): GameProjection =
    new GameProjector(mixedForgeCostCatalog).project(gameId,
      loaded(run.service.load(gameId)), viewer)

  /** p1's partition prompt in `projection`. */
  private def promptIn(projection: GameProjection)(using munit.Location)
      : DecisionQueryProjection.Partition =
    val decision = projection.walkerDecision.getOrElse(
      fail("the parked actor must be offered the Forge assignment prompt"))
    assertEquals(decision.decisionId, ForgeProcedure.assignmentDecisionId)
    decision.query match
      case Some(partition: DecisionQueryProjection.Partition) => partition
      case other => fail(s"expected a Forge partition, got $other")

  /** The mixed printed cost: 2 favor and 1 secret. */
  private val printed = present(present(mixedForgeCostCatalog.sites
    .find(_.id == forgeSite), "the Forge site").forgeRequirements,
    "a printed Forge cost")

  /** The answer the UI builds from the prompt: the offered targets, in
    * order, taking the printed counts. */
  private def placementsFor(prompt: DecisionQueryProjection.Partition)
      : Vector[DecisionPlacement] =
    val sections = Vector.fill(printed.favor)(ForgeProcedure.favorSectionKey) ++
      Vector.fill(printed.secrets)(ForgeProcedure.secretSectionKey)
    prompt.options.zip(sections).map { case (option, section) =>
      DecisionPlacement(DecisionOptionRef.Denizen(DenizenId(option.id)),
        section) }

  /** `run`'s Forge answered with the projected prompt's placements. */
  private def answered(run: ParkedForgeRun, gameId: String)(
      using munit.Location): GameAccepted =
    val placements = placementsFor(promptIn(projected(run, gameId, p1)))
    run.service.handle(gameId, run.started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements))))
      .fold(error => fail(s"walker Forge answer rejected: $error"), identity)

  /** forgeTable tops the relic deck with Dowsing Sticks. */
  private val dowsingSticks = CatalogNames.relic("Dowsing Sticks")

  test("a parked Forge projects its assignment prompt to the actor only"):
    val gameId = "game-walker-forge-prompt"
    val run = parkedForgeRun(gameId)
    val actor = p1
    val supplyBefore = Look(Outcomes.ready(forgeTable.state)).supply(actor)
    assert(run.started.events.last.isInstanceOf[WalkerParked])
    forgeParkedAssertions.assertParked(run.started.state, ActionRef.Forge,
      ForgeProcedure.assignmentDecisionId, actor)
    val parked = Outcomes.ready(run.started.state)
    assertEquals(parked.game.current.walkerProcedure, Some(ActionRef.Forge))
    assertEquals(Look(parked).supply(actor), supplyBefore - 1)

    // The prompt the client actually answers from, reloaded through the
    // codec rather than read off the in-memory transition.
    val parkedLoaded = loaded(run.service.load(gameId))
    assertEquals(parkedLoaded.state, run.started.state)
    val projector = new GameProjector(mixedForgeCostCatalog)
    val owner = projector.project(gameId, parkedLoaded, actor)
    // Task 4: the prompt IS the projected query. There is no Forge-shaped
    // projection any more -- two declared sections carrying the printed
    // minima, and one denizen option per live eligible target, described
    // from the same `Decide` the walker is parked on.
    val prompt = promptIn(owner)
    assertEquals(prompt.sections, Vector(
      DecisionSectionProjection(ForgeProcedure.favorSectionKey, "Pay Favor",
        printed.favor),
      DecisionSectionProjection(ForgeProcedure.secretSectionKey, "Pay Secret",
        printed.secrets)))
    // forgeTable places three denizens at the Forge site.
    assertEquals(prompt.options.size, 3)
    assertEquals(prompt.options.map(_.kind).distinct, Vector("denizen"))
    // The options are the live eligible targets at the Forge site, with
    // their presentation card details -- never a set the projector derived
    // by consulting `ForgeProcedure` a second time.
    assertEquals(prompt.options.map(_.id).toSet,
      ForgeProcedure.eligibleTargets(parked, actor)
        .map(_.denizenId.value).toSet)
    assert(prompt.options.forall(_.card.nonEmpty))
    // The mixed cost: 2 favor and 1 secret.
    assertEquals(printed.favor + printed.secrets, 3)
    assertEquals(owner.phase, "forge-walker-decision")
    assertEquals(owner.legalControls, Vector("resolveWalkerDecision"))
    // Forge has no dice, and its parked decision says so. (This is a
    // client-facing fact, not R18's proof: no site in the catalog carries
    // both a printed Forge cost and a Recover difficulty, so `rollOutcome`
    // is `None` here for want of a difficulty either way. R18's projector
    // call site is proven in `WalkerDecisionProjectorSuite`.)
    assertEquals(owner.walkerDecision.flatMap(_.rollOutcome), None)
    assertEquals(projector.project(gameId, parkedLoaded, p2)
      .walkerDecision, None)
    assertEquals(projector.projectPublic(gameId, parkedLoaded)
      .walkerDecision, None)

  test("a parked Forge refuses every other command and appends nothing"):
    val gameId = "game-walker-forge-refusals"
    val run = parkedForgeRun(gameId)
    val actor = p1
    val placements = placementsFor(promptIn(projected(run, gameId, actor)))
    val beforeRejected = loaded(run.repository.load(gameId)).records
    Vector[GameCommand](
      GameCommand.ResolveWalker(actor, TreeDecision("forge.stale",
        PartitionAnswer(placements))),
      GameCommand.ResolveWalker(p2, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements))),
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId,
        PartitionAnswer(placements.updated(1, placements.head)))),
      GameCommand.ResolveWalker(actor, TreeDecision(
        ForgeProcedure.assignmentDecisionId, PartitionAnswer(
          placements.map(_.copy(sectionKey = ForgeProcedure.secretSectionKey))))),
      GameCommand.BeginRest(actor)
    ).foreach(command => assert(run.service.handle(gameId,
      run.started.nextSequence, command).isLeft,
      s"$command must be rejected while Forge is parked"))
    assertEquals(loaded(run.repository.load(gameId)).records,
      beforeRejected, "a rejected command must append nothing")

  test("walker Forge completes through StartWalker/ResolveWalker alone and " +
      "replays to the same final state"):
    val gameId = "game-walker-forge"
    val run = parkedForgeRun(gameId)
    val actor = p1
    val beforeStart = Outcomes.ready(forgeTable.state)
    val banksBefore = beforeStart.banks.favor
    val actorBefore = Look(beforeStart).player(actor)

    val finished = answered(run, gameId)
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    val after = Outcomes.ready(finished.state)

    // The same final state the deleted legacy path produced: three
    // denizens each carrying exactly one resource, the relic facedown in the
    // actor's play area, the deck advanced by one, one Supply spent in total,
    // and nothing pending on either mechanism.
    assertEquals(after.game.current.map.sites(forgeSite).denizens.collect {
      case d: DenizenState => d.tokens.favor + d.tokens.secrets }, Vector(1, 1, 1))
    assertEquals(Look(after).player(actor).relics.last,
      RelicState(dowsingSticks, Orientation.FaceDown, Tokens.empty))
    assertEquals(after.game.current.commonCards.relicDeck,
      beforeStart.game.current.commonCards.relicDeck.drop(1))
    assertEquals(Look(after).supply(actor), actorBefore.board.supply.supply - 1)
    assert(after.game.current.walkerPending.isEmpty)
    assert(after.game.current.walkerProcedure.isEmpty)

    // The actor funded the whole printed cost out of their own play area,
    // and no suit bank moved at all. This reverses the pre-walker behaviour
    // of drawing each favor from the target denizen's own suit bank.
    assertEquals(after.banks.favor, banksBefore)
    val actorAfter = Look(after).player(actor)
    assertEquals(actorAfter.board.favor,
      actorBefore.board.favor - printed.favor)
    assertEquals(actorAfter.board.faceUpSecrets,
      actorBefore.board.faceUpSecrets - printed.secrets)

    // P2: reconstructing purely from the journal reproduces that state.
    val replayed = loaded(new GameApplicationService(mixedForgeCostCatalog,
      run.repository, genesis = run.service.genesis).load(gameId))
    assertEquals(replayed.state, finished.state)

  test("the forged relic stays private to its owner"):
    val gameId = "game-walker-forge-private"
    val run = parkedForgeRun(gameId)
    answered(run, gameId)
    val projector = new GameProjector(mixedForgeCostCatalog)
    val replayed = loaded(run.service.load(gameId))
    // p1 holds the forged Dowsing Sticks, which no one else may see.
    assertEquals(Look(Outcomes.ready(replayed.state)).player(p1).relics.last.id,
      dowsingSticks)
    val otherJson = GameHttpWire.encodeProjection(
      projector.project(gameId, replayed, p2))
    assert(!otherJson.contains(dowsingSticks.value))
    assert(!GameHttpWire.encodeProjection(
      projector.projectPublic(gameId, replayed)).contains(dowsingSticks.value))

  test("a Forge whose printed cost is three of one resource completes in " +
      "the command that starts it, with no decision to answer"):
    val (service, repository) = forgeService()
    val gameId = "game-walker-forge-forced"
    val actor = p1
    val beforeStart = Outcomes.ready(forgeTable.state)
    val cost = present(present(catalog.sites.find(_.id == forgeSite),
      "the Forge site").forgeRequirements, "a printed Forge cost")
    assert(cost.favor == 0 || cost.secrets == 0,
      s"this test needs a single-resource printed cost, got $cost")
    val actorBefore = Look(beforeStart).player(actor)
    val banksBefore = beforeStart.banks.favor

    val finished = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)

    // One command: the action completes, nothing parks, and the client is
    // never asked to confirm a split it could not have got wrong.
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assert(!finished.events.exists(_.isInstanceOf[WalkerParked]))
    parkedAssertions.assertResumed(finished.state, Phase.Act, actor)
    val after = Outcomes.ready(finished.state)
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
    val actorAfter = Look(after).player(actor)
    assertEquals(actorAfter.board.favor, actorBefore.board.favor - cost.favor)
    assertEquals(actorAfter.board.faceUpSecrets,
      actorBefore.board.faceUpSecrets - cost.secrets)
    assertEquals(actorAfter.relics.last,
      RelicState(dowsingSticks, Orientation.FaceDown, Tokens.empty))

    // The journal replays to the same state even though it carries a
    // completion that was never preceded by a park.
    assertEquals(loaded(replayingSecond(service, repository)
      .load(gameId)).state, finished.state)
