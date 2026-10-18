package oathdigital.application

import oathdigital.testkit.MenuPlay
import oathdigital.model._
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.gameplay.ModifierPlay
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.OathRules
import oathdigital.testkit.{CatalogNames, Dice, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}
import ParkedServiceFixture.{firstPlayer, loaded, parkedAssertions,
  replayingSecond, setupViaService}

/** The application service drives a walker Recover, its dice and Catacombs
  * through persisted parks and reloads.
  */
class GameApplicationServiceRecoverSuite extends munit.FunSuite:
  private val catacombs = CatalogNames.denizen("Catacombs")
  /** The power printed on Catacombs. */
  private val catacombsPower = present(
    catalog.denizens.find(_.id.value == catacombs.value), "Catacombs")
    .powers.head.id

  /** `target` swapped into the in-play 8, whether or not it was already one
    * of the fixture's own 8 sites: the actual map (not merely the client's
    * pick) determines which sites Recover/Muster/etc. see in play, so a
    * test that needs a specific site in play must put it in `chronicle`'s
    * own `atlasBox`, not just choose it as a placement.
    */
  private final case class ChronicleWithSiteInPlay(chronicle: Chronicle,
      orderedSites: Vector[SiteId])

  private def withSiteInPlay(target: SiteId): ChronicleWithSiteInPlay =
    val stored = chronicle.atlasBox.find(_.site == target)
      .getOrElse(StoredSite(target))
    val newChronicle = chronicle.copy(atlasBox =
      stored +: chronicle.atlasBox.filterNot(_.site == target).take(7))
    ChronicleWithSiteInPlay(newChronicle,
      newChronicle.atlasBox.take(8).map(_.site))

  /** The relic deck's top. */
  private val dowsingSticks = CatalogNames.relic("Dowsing Sticks")

  /** p1 stands at Dunes, which has one relic slot and no relic, beside a
    * faceup Catacombs: a Recover there that names Catacombs puts the top of
    * the relic deck, Dowsing Sticks, on the site and pays a secret for it. */
  private val catacombsTable = Table.start.pawn(p1, "Dunes")
    .denizen("Catacombs", "Dunes").relicDeckTop(dowsingSticks)

  /** Two shields and a Doubler: they reach the difficulty of Broken Peaks,
    * and Recover rolls them as the walker walks, so the dice come from the
    * walker's own source -- the campaign port -- rather than from the port a
    * parked roll command would have asked. */
  private val meetingTheDifficulty =
    Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler)

  test("walker Recover persists every park and replays to the same final state"):
    // p1 stands at Broken Peaks, difficulty 4, with two facedown relics.
    val recoverSite = CatalogNames.site("Broken Peaks")
    val actor = p1

    val walkerDice = new Dice.CountingDefense(meetingTheDifficulty)
    val (walkerService, walkerRepository) = ParkedServiceFixture.recoverTable
      .service(campaignDice = walkerDice)

    // The start rolls on its way through: two shields and a doubler reach
    // this site's difficulty, so the command that begins Recover is also the
    // one that parks on the relic it won.
    val started = accepted(walkerService.handle("walker-recover", 0L,
      MenuPlay.starting(actor, ActionRef.Recover)))
    val atRelic = Outcomes.ready(started.state)
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(walkerDice.calls, 1)
    assertEquals(atRelic.game.current.walkerProcedure, Some(ActionRef.Recover))
    parkedAssertions.assertParked(started.state, ActionRef.Recover,
      RecoverProcedure.relicDecisionId, actor)

    walkerService.handle("walker-recover", started.nextSequence,
      MenuPlay.starting(actor, PhaseTransitionRef.BeginRest)) match
      case Left(GameApplicationError.CommandRejected(
          _: oathdigital.model.OathViolation.InvalidEventOrder)) => ()
      case other => fail(s"a start command should be blocked by walker park: $other")
    assertEquals(loaded(walkerService.load("walker-recover"))
      .nextSequence, started.nextSequence)

    val reloadedAtRelic = loaded(
      replayingSecond(walkerService, walkerRepository).load("walker-recover"))
    assertEquals(reloadedAtRelic.state, started.state)
    // Sticky Fire lies first at Broken Peaks, before Cursed Cauldron.
    val relic = CatalogNames.relic("Sticky Fire")
    assertEquals(atRelic.game.current.map.sites(recoverSite).relics.head.id,
      relic)

    val finished = accepted(walkerService.handle("walker-recover",
      reloadedAtRelic.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Relic(relic))))))
    val afterWalker = Outcomes.ready(finished.state)
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    parkedAssertions.assertResumed(finished.state, Phase.Act, actor)
    assert(MenuPlay.pendingBeyondMenu(afterWalker).isEmpty)
    assert(MenuPlay.procedureBeyondMenu(afterWalker).isEmpty)
    assertEquals(afterWalker.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    assertEquals(afterWalker.game.current.rollOutcomes,
      Map.empty[PoolKey, RollOutcome])

    // The walker is now the only Recover path, so the final state is
    // asserted directly rather than against a legacy run: the relic moves
    // facedown into the actor's play area and off the site.
    val actorState = Look(afterWalker).player(actor)
    assertEquals(actorState.relics.map(r => r.id -> r.orientation),
      Vector(relic -> Orientation.FaceDown))
    assertEquals(afterWalker.game.current.map.sites(recoverSite).relics.map(_.id),
      atRelic.game.current.map.sites(recoverSite).relics.map(_.id)
        .filterNot(_ == relic))

    val replayed = loaded(walkerService.load("walker-recover"))
    assertEquals(replayed.state, finished.state)
    assertEquals(replayed.nextSequence, finished.nextSequence)

    // The menu's pick queued the Recover; the Recover's own steps follow.
    val recordedOps = (started.events ++ finished.events)
      .collect { case step: WalkerStepRecorded => step.ops }.flatten
      .filterNot(_.isInstanceOf[QueueProcedure])
    assertEquals(recordedOps, Vector[CoreOperation](
      ModifyDicePool(RecoverProcedure.recoverPool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      SpendSupply(actor, 1),
      Move(Piece.Card(relic),
        PositionedLocation(Location.Site(recoverSite)),
        PositionedLocation(Location.PlayArea(actor)),
        resultingOrientation = Some(Orientation.FaceDown), by = Some(actor))))

  test("walker Continue answer and the roll it buys survive reload"):
    val dice = new Dice.CountingDefense(
      Vector(DefenseDieFace.Blank, DefenseDieFace.Blank))
    val (service, repository) = ParkedServiceFixture.recoverTable
      .service(campaignDice = dice)
    val actor = p1
    // Both dice come up blank, so the start's own roll fails and parks the
    // Continue/Stop Decide.
    val failed = accepted(service.handle("walker-continue", 0L,
      MenuPlay.starting(actor, ActionRef.Recover)))
    assertEquals(dice.calls, 1)
    parkedAssertions.assertParked(failed.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    // Continue buys two more dice and rolls them inside the same command,
    // which fails again and parks the same choice: the continuation names
    // the parked node's own decision id either way.
    val continued = accepted(service.handle("walker-continue",
      failed.nextSequence, GameCommand.ResolveWalker(actor, TreeDecision(
        RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))))))
    assertEquals(dice.calls, 2)
    parkedAssertions.assertParked(continued.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    val ready = Outcomes.ready(continued.state)
    assertEquals(ready.game.current.walkerPending.toVector.flatMap(_.answered),
      Vector(Answered(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)))
    assertEquals(ready.game.current.walkerProcedure, Some(ActionRef.Recover))
    assertEquals(loaded(replayingSecond(service, repository)
      .load("walker-continue")).state, continued.state)
    assert(loaded(repository.load("walker-continue")).records
      .exists(record => record.contains(
        "\"procedure\":{\"family\":\"action\",\"key\":\"recover\"}") &&
        record.contains("\"answered\"")))

    val failedAgain = continued
    val afterSecondRoll = Outcomes.ready(failedAgain.state)
    val accumulated = afterSecondRoll.game.current.rollOutcomes(
      RecoverProcedure.recoverPool)
    assertEquals(accumulated.count, 4)
    assertEquals(accumulated.faces.size, 4)
    assertEquals(loaded(replayingSecond(service, repository)
      .load("walker-continue")).state, failedAgain.state)

  test("a malformed pending path is rejected"):
    val park = ParkedServiceFixture.recoverChoicePark("walker-malformed-path")
    val ready = Outcomes.ready(park.game.accepted.state)
    val actor = park.actor
    val rules = new OathRules(catalog)
    Vector(Vector("not-a-node"), Vector("999999999999999999999")).foreach:
      path =>
        // The pending path is the subject, so it is patched directly.
        val malformed = ready.updateCurrent(_.copy(walkerPending =
          ready.game.current.walkerPending.map(_.copy(at = path))))
        val rejected = rules.rollWalkerPrepared(Ready(malformed), actor,
          RecoverProcedure.recoverPool)(_ => Right(
            Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)))
        assert(rejected.left.toOption.exists(
          _.isInstanceOf[oathdigital.model.OathViolation.InvalidEventOrder]))

  test("walker Recover accumulates and reloads when a later Doubler " +
      "multiplies shields from an earlier roll"):
    // Stays on the replayed setup: Salt Flats is not in play on a Table's
    // map, and no Table step changes the map.
    val saltFlats = CatalogNames.site("Salt Flats")
    assertEquals(RecoverRules.difficulty(catalog, saltFlats), Some(2))
    val ChronicleWithSiteInPlay(saltFlatsChronicle, orderedSites) =
      withSiteInPlay(saltFlats)
    val actor = firstPlayer
    val rolls = Vector[Vector[DefenseDieFace]](
      Vector(DefenseDieFace.OneShield, DefenseDieFace.Blank),
      Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank))

    val walkerRepository = new InMemoryEventStreamRepository
    val walkerDice = new Dice.ScriptedDefense(rolls)
    val walkerService = new GameApplicationService(catalog, walkerRepository,
      campaignDicePort = walkerDice)
    // Replays Setup: Salt Flats comes into play through the Chronicle.
    val walkerSetup = setupViaService(walkerService,
      "walker-cross-roll-doubler", orderedSites, saltFlatsChronicle)
    val walkerAct = ParkedServiceFixture.endingWake(walkerService,
      "walker-cross-roll-doubler", walkerSetup, actor)
    val walkerFirst = accepted(walkerService.handle("walker-cross-roll-doubler",
      walkerAct.nextSequence,
      MenuPlay.starting(actor, ActionRef.Recover)))
    val walkerAfterFirst = Outcomes.ready(walkerFirst.state)
    assertEquals(walkerAfterFirst.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 1)
    parkedAssertions.assertParked(walkerFirst.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    val walkerFirstReloaded = loaded(new GameApplicationService(catalog,
      walkerRepository).load("walker-cross-roll-doubler"))
    assertEquals(walkerFirstReloaded.state, walkerFirst.state)
    val walkerAfterFirstReload = Outcomes.ready(walkerFirstReloaded.state)
    assertEquals(walkerAfterFirstReload.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 1)

    // Continuing buys the second pair and rolls them in the same command:
    // the Doubler that lands there multiplies the shield from the first
    // roll, which is the accumulation under test.
    val walkerSecond = accepted(walkerService.handle(
      "walker-cross-roll-doubler", walkerFirstReloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue"))))))
    val walkerAfterSecond = Outcomes.ready(walkerSecond.state)
    assertEquals(walkerAfterSecond.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 2)
    parkedAssertions.assertParked(walkerSecond.state, ActionRef.Recover,
      RecoverProcedure.relicDecisionId, actor)
    val walkerSecondReloaded = loaded(new GameApplicationService(catalog,
      walkerRepository).load("walker-cross-roll-doubler"))
    assertEquals(walkerSecondReloaded.state, walkerSecond.state)
    val walkerAfterSecondReload = Outcomes.ready(walkerSecondReloaded.state)
    assertEquals(walkerAfterSecondReload.game.current.rollOutcomes(
      RecoverProcedure.recoverPool).score, 2)

    // The walker is now the only Recover path: finish the walk and assert
    // its own final state directly rather than against a legacy run.
    // Setup lays Sticky Fire at Salt Flats.
    val walkerRelic = CatalogNames.relic("Sticky Fire")
    assertEquals(walkerAfterSecond.game.current.map.sites(saltFlats)
      .relics.head.id, walkerRelic)
    val walkerFinished = accepted(walkerService.handle(
      "walker-cross-roll-doubler", walkerSecondReloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(walkerRelic))))))
    val walkerFinal = Outcomes.ready(walkerFinished.state)
    val actorState = Look(walkerFinal).player(actor)
    assertEquals(actorState.relics.map(r => r.id -> r.orientation),
      Vector(walkerRelic -> Orientation.FaceDown))
    assertEquals(walkerFinal.game.current.map.sites(saltFlats).relics.map(_.id),
      walkerAfterSecond.game.current.map.sites(saltFlats).relics.map(_.id)
        .filterNot(_ == walkerRelic))
    assertEquals(walkerDice.calls, 2)

  test("RollWalker with a pool key that does not match the parked pool " +
      "never rolls the campaign dice"):
    // rollWalkerPrepared validates the parked pool against the command's
    // pool key BEFORE invoking prepareFaces (OathRules.scala): this is the
    // entire mechanism the authoritative-dice property relies on to keep a
    // caller from steering which dice get rolled. Pin the ordering directly
    // by proving the port is untouched on a mismatch, not just that the
    // command is rejected.
    val dice = new Dice.CountingDefense(meetingTheDifficulty)
    val (service, _) = ParkedServiceFixture.recoverTable
      .service(campaignDice = dice)
    val actor = p1
    val started = accepted(service.handle("walker-pool-mismatch", 0L,
      MenuPlay.starting(actor, ActionRef.Recover)))

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
      .service(campaignDice = new Dice.CountingDefense(meetingTheDifficulty))
    val gameId = "catacombs-walker-persisted"
    val actor = p1
    val before = Outcomes.ready(catacombsTable.state)
    val beforePlayer = Look(before).player(actor)
    val siteId = beforePlayer.pawnSite.get
    val topRelic = dowsingSticks

    val begun = accepted(service.handle(gameId, 0L,
      MenuPlay.starting(actor, ActionRef.Recover)))
    // Catacombs is the one modifier p1 can pick, so the start asks for it,
    // and picking it starts the Recover.
    val started = service.handle(gameId, begun.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("recover.modifiers",
        ModifierPlay.pick(catacombsPower)))) match
      case Right(accepted) => accepted
      case Left(error) =>
        fail(s"picking Catacombs must persist, got $error")

    // The pick's payment onto the card, then Catacombs' relic move off the
    // top of the relic deck, verbatim, survived encoding.
    assertEquals(started.events.collect {
      case step: WalkerStepRecorded => step
    }.flatMap(_.ops).take(2), Vector[CoreOperation](
      PayCost(actor, Location.OnCard(catacombs), Cost(secret = 1),
        matchingBank = catalog.suitOf(catacombs)),
      Move(Piece.Card(topRelic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(siteId)),
        resultingOrientation = Some(Orientation.FaceDown))))
    assert(started.events.collect { case step: WalkerStepRecorded =>
      step.contributions }.contains(Vector(catacombsPower)),
      "the recorded step must name the contribution that produced it")

    val after = Outcomes.ready(started.state)
    val afterPlayer = Look(after).player(actor)
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
    val reloaded = loaded(replayingSecond(service, repository).load(gameId))
    assertEquals(reloaded.state, started.state)
    assertEquals(reloaded.nextSequence, started.nextSequence)

    // And the walk still finishes on the reloaded stream.
    val atRelic = Outcomes.ready(reloaded.state)
    assertEquals(atRelic.game.current.map.sites(siteId).relics.head.id,
      topRelic)
    val finished = accepted(service.handle(gameId, reloaded.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(topRelic))))))
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    assertEquals(loaded(replayingSecond(service, repository).load(gameId)
      ).state, finished.state)
