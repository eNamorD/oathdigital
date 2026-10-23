package oathdigital.application

import oathdigital.testkit.MenuPlay
import oathdigital.model._
import oathdigital.gameplay.{ModifierPlay, OathRules, WalkerDiceFixture}
import oathdigital.testkit.CatacombsGame
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.protocol.projection.{DecisionOptionProjection,
  DecisionQueryProjection, DecisionSectionProjection, DieFace => RolledFace,
  GameProjectionCodec, WalkerDecisionProjection,
  WalkerRollOutcomeProjection}
import oathdigital.testkit.{CatalogNames, Dice}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}
import oathdigital.application.ParkedServiceFixture.loaded
import oathdigital.testkit.Outcomes

/** Task 7 (Recover slice, controller ruling (b)) established the
  * application-projector surface: [[WalkerDecisionProjector]] plus the
  * `phase` effect on [[PendingProjector]]. Task 7a promotes
  * [[WalkerDecisionProjection]] itself onto the wire -- it is now the same
  * type `GameProjection.walkerDecision` carries (`shared/.../
  * ActionProjectionDtos.scala`).
  *
  * Task 4 replaced the hand-discovered relic-candidate field with
  * `query`: the projector no longer derives what to offer, it describes the
  * transformed `Decide.query` it is already parked on. So the choice park
  * and the relic park below each assert the projected query VERBATIM
  * against the options `RecoverProcedure` declares -- the two declared
  * buttons with their declared labels, and one relic option per live
  * facedown site relic with its presentation card details. Every test still
  * asserts the owner-private application-layer projection directly, and the
  * roll/relic tests additionally assert the real wire-facing
  * [[GameProjector]] output AND round-trip it through
  * `GameProjectionCodec.encode`/`decode` -- the roll test covers the
  * codec's handling of a populated `pool`/`count` (`Option[String]`/
  * `Option[Int]`), the relic test covers a populated `query` (where
  * `pool`/`count` are both `None`) -- so a codec regression on any field of
  * the `walkerDecision` projection would fail here.
  */
class WalkerDecisionProjectionSuite extends munit.FunSuite:
  private def presentation = new GamePresentationProjector(catalog)
  private def walkerDecisions = new WalkerDecisionProjector(catalog, presentation)
  /** The continue/stop query as `RecoverProcedure` declares it: two button
    * options, in declaration order, carrying the action's own prompt copy.
    * A button has no game object behind it, so it projects no card details.
    *
    * Task 5b added the `heading`. It is the panel's own title, authored
    * where the decision is authored, and it is asserted here rather than
    * in the frontend because the projector is what has to carry it through
    * from the transformed query untouched. A partition additionally
    * declares a `confirmLabel`; a choose-one submits on the click, so there
    * is no confirm step to name and the field does not exist on that shape.
    */
  private val choiceQuery = DecisionQueryProjection.ChooseOne(Vector(
    DecisionOptionProjection("button", "continue", "Continue"),
    DecisionOptionProjection("button", "stop", "Stop")),
    heading = Some("Recover"))

  /** The same difficulty `WalkerDecisionProjector.rollOutcome` reads (I5):
    * derived live from `ready`/`catalog` rather than hardcoded, so this
    * stays correct if `ParkedServiceFixture.recoverTable`'s site ever changes.
    */
  private def expectedDifficulty(ready: ReadyGame, actor: PlayerId): Int =
    RecoverProcedure.actorSite(ready, actor)
      .flatMap(site => RecoverRules.difficulty(catalog, site))
      .getOrElse(fail(s"${actor.value}'s site has no Recover difficulty"))

  /** A Recover started on ParkedServiceFixture's recoverTable, and the
    * service that journaled it. */
  private final case class StartedRecover(service: GameApplicationService,
      started: GameAccepted)

  /** Starts p1's Recover with `dice` in the cup: the roll happens inside the
    * start command, so where the walk parks is settled by those faces.
    */
  private def startedRolling(gameId: String, dice: CampaignDicePort)(
      using munit.Location): StartedRecover =
    val (service, _) = ParkedServiceFixture.recoverTable
      .service(campaignDice = dice)
    StartedRecover(service, accepted(service.handle(gameId, 0L,
      MenuPlay.starting(p1, ActionRef.Recover))))

  /** Both dice come up Blank: score 0, still short of the difficulty, so the
    * walk parks on the continue/stop choice. */
  private def recoverFailingItsRoll(gameId: String)(using munit.Location) =
    startedRolling(gameId,
      Dice.defending(Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)))

  /** The choice park's roll feedback: this is exactly what the
    * continue/stop choice needs (I5). */
  private def failedRollOutcome(ready: ReadyGame) =
    WalkerRollOutcomeProjection("recover",
      Vector(RolledFace.Blank, RolledFace.Blank), 0,
      Some(expectedDifficulty(ready, p1)))

  test("a failed roll parks the continue/stop Decide with its own decision " +
      "id, not the Roll's, for its owner only"):
    val ready = Outcomes.ready(
      recoverFailingItsRoll("walker-projection-choice").started.state)
    assertEquals(walkerDecisions.project(ScopedProjectionContext(ready,
      Some(p1))), Some(WalkerDecisionProjection(ActionRef.Recover.key,
      RecoverProcedure.choiceDecisionId, presentation = "board", query = Some(choiceQuery),
      rollOutcome = Some(failedRollOutcome(ready)))))
    assertEquals(walkerDecisions.project(ScopedProjectionContext(ready,
      Some(p2))), None)

  test("a parked continue/stop choice names the decision phase"):
    val ready = Outcomes.ready(
      recoverFailingItsRoll("walker-projection-choice-phase")
        .started.state)
    val owner = ScopedProjectionContext(ready, Some(p1))
    val viewer = ScopedProjectionContext(ready, Some(p2))
    val pendingProjector = new PendingProjector(catalog, presentation,
      walkerDecisions)
    assertEquals(pendingProjector.project(owner).phase, "recover-walker-decision")
    assertEquals(pendingProjector.project(viewer).phase, "recover-walker-waiting")

  test("the wire carries the continue/stop choice to its owner only"):
    val gameId = "walker-projection-choice-wire"
    val rolled = recoverFailingItsRoll(gameId)
    // End to end: the wire projection carries the choice decisionId (not
    // the roll's, and not the relic's -- all three are distinguishable by
    // `decisionId` alone), and the two buttons rather than a relic list.
    val stored = loaded(rolled.service.load(gameId))
    val projector = new GameProjector(catalog)
    assertEquals(projector.project(gameId, stored, p1).walkerDecision,
      Some(WalkerDecisionProjection(ActionRef.Recover.key,
        RecoverProcedure.choiceDecisionId, presentation = "board", query = Some(choiceQuery),
        rollOutcome = Some(failedRollOutcome(Outcomes.ready(stored.state))))))
    assertEquals(projector.project(gameId, stored, p2).walkerDecision, None)

  test("the engine accepts a projected continue/stop button"):
    val gameId = "walker-projection-choice-answer"
    val rolled = recoverFailingItsRoll(gameId)
    // The projected labels are the query's own declarative copy, and they
    // never affect legality: the engine accepts the button KEY.
    val stopped = rolled.service.handle(gameId, rolled.started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(
        RecoverProcedure.choiceDecisionId, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Button(choiceQuery.options.last.id)))))
    // The engine accepts a projected button option.
    accepted(stopped)

  /** Two shields and a Doubler make 4, meeting Broken Peaks' difficulty 4,
    * so the walk parks on the relic choice. */
  private def recoverMeetingItsDifficulty(gameId: String)(
      using munit.Location) =
    startedRolling(gameId, Dice.defending(
      Vector(DefenseDieFace.TwoShields, DefenseDieFace.Doubler)))

  /** The relic park's query: one option per facedown relic at Broken Peaks,
    * Sticky Fire and Cursed Cauldron, each carrying its stable reference
    * (kind plus id), its presentation name, and the SAME `cardDetails`
    * output every other card projection uses -- so the walker path inherits
    * the existing disclosure rules rather than restating them. Two relics,
    * not one: the tree's placeholder marker relic
    * (`RecoverProcedure.tree`'s `markerRelic`, `_.relics.headOption`) is
    * always ONE specific relic at this site, so a projector that echoed that
    * marker instead of reading live site state would yield a one-element
    * vector and fail the equality check. */
  private val relicQuery = DecisionQueryProjection.ChooseOne(
    Vector("Sticky Fire", "Cursed Cauldron").map(CatalogNames.relic)
      .map(relic => DecisionOptionProjection("relic", relic.value,
        presentation.relicLabel(relic),
        Some(presentation.cardDetails(relic, Some(Orientation.FaceDown),
          hidden = false)))),
    heading = Some("Take a relic"), confirmVerb = Some("Take"))

  /** TwoShields + Doubler = 4 shields, meeting the difficulty (I5): the relic
    * park's feedback shows the successful roll that got here. */
  private def expectedRelicDecision(ready: ReadyGame) =
    WalkerDecisionProjection(
      ActionRef.Recover.key, RecoverProcedure.relicDecisionId, presentation = "board",
      query = Some(relicQuery),
      rollOutcome = Some(WalkerRollOutcomeProjection("recover",
        Vector(RolledFace.TwoShields, RolledFace.Doubler), 4,
        Some(expectedDifficulty(ready, p1)))))

  test("a successful roll parks the relic Decide and lists the site's " +
      "facedown relics for the actor only, never the tree's placeholder " +
      "relic marker"):
    val ready = Outcomes.ready(
      recoverMeetingItsDifficulty("walker-projection-relic")
        .started.state)
    val ownerDecision =
      walkerDecisions.project(ScopedProjectionContext(ready, Some(p1)))
    assertEquals(ownerDecision, Some(expectedRelicDecision(ready)))
    // The marker relic the tree closes over is never the thing surfaced:
    // assert on the actual site relics, not a hardcoded single id, so this
    // would fail if the projector ever fell back to echoing a closure
    // instead of describing the query rebuilt against live site state.
    val facedownRelics = ready.game.current.map
      .sites(CatalogNames.site("Broken Peaks")).relics
      .filter(_.orientation == Orientation.FaceDown)
    assert(ownerDecision.flatMap(_.query)
      .exists(_.offeredOptions.map(_.id).toSet ==
        facedownRelics.map(_.id.value).toSet))
    // A choose-one is a type with no sections, so there is nothing left to
    // assert about: the old `sections` read is gone with the field.
    assert(ownerDecision.flatMap(_.query)
      .exists(_.isInstanceOf[DecisionQueryProjection.ChooseOne]))
    assertEquals(walkerDecisions.project(ScopedProjectionContext(ready,
      Some(p2))), None)

  test("a parked relic choice names the decision phase"):
    val ready = Outcomes.ready(
      recoverMeetingItsDifficulty("walker-projection-relic-phase")
        .started.state)
    val owner = ScopedProjectionContext(ready, Some(p1))
    val viewer = ScopedProjectionContext(ready, Some(p2))
    val pendingProjector = new PendingProjector(catalog, presentation,
      walkerDecisions)
    assertEquals(pendingProjector.project(owner).phase, "recover-walker-decision")
    assertEquals(pendingProjector.project(viewer).phase, "recover-walker-waiting")

  test("the relic choice reaches the wire for its owner only and round-trips " +
      "through the codec"):
    val gameId = "walker-projection-relic-wire"
    val rolled = recoverMeetingItsDifficulty(gameId)
    // End to end through the real wire-facing projector and its codec: the
    // actor sees the candidates, every other viewer sees no walkerDecision
    // at all (not a redacted copy with an empty list).
    val stored = loaded(rolled.service.load(gameId))
    val projector = new GameProjector(catalog)
    val ownerWire = projector.project(gameId, stored, p1)
    assertEquals(ownerWire.walkerDecision,
      Some(expectedRelicDecision(Outcomes.ready(stored.state))))
    assertEquals(projector.project(gameId, stored, p2).walkerDecision, None)
    val roundTripped = accepted(GameProjectionCodec.decode(
      GameProjectionCodec.encode(ownerWire)))
    assertEquals(roundTripped.walkerDecision, ownerWire.walkerDecision)

  test("the engine accepts a projected relic"):
    val gameId = "walker-projection-relic-answer"
    val rolled = recoverMeetingItsDifficulty(gameId)
    // Every candidate the projection offers is one the engine actually
    // accepts at resolve time -- guards against the projected candidate set
    // diverging from the option set the declared query accepts.
    val chosen = RelicId(relicQuery.options.head.id)
    val resolved = rolled.service.handle(gameId, rolled.started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(
        RecoverProcedure.relicDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Relic(chosen)))))
    accepted(resolved)

  // ---------------------------------------------------------------------
  // Task 5 binding carry-in: the projector must fold a shared window with
  // the SAME powers the walker command that parked here used, or it
  // misreports the park the moment a power inserts operations at that
  // window (Catacombs, at `RecoverActionEligibility`, is the first one).
  // ---------------------------------------------------------------------

  test("the projector reports the decision the walker actually parked at " +
      "with Catacombs in effect, and would misreport it if it folded " +
      "without the power"):
    val fixture = CatacombsGame.reliclessSite(secrets = 2)
    val rules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = WalkerDiceFixture.blanks)
    val started = accepted(ModifierPlay.started(rules, Ready(fixture.ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection,
      Vector(CatacombsGame.catacombsId)))
    val ready = Outcomes.ready(started.state)
    val context = ScopedProjectionContext(ready, Some(p1))

    // Wired with the same catalog the walker used: the fold matches, and
    // the projector reports the very park the walk left behind (shifted one
    // index deeper than the bare tree by Catacombs' inserted node) -- the
    // continue-or-stop choice the blank roll earned.
    val wired = new WalkerDecisionProjector(catalog, presentation,
      WalkerPowerCatalog.default(catalog))
    assertEquals(wired.project(context), Some(WalkerDecisionProjection(
      ActionRef.Recover.key, RecoverProcedure.choiceDecisionId, presentation = "board",
      query = Some(choiceQuery),
      rollOutcome = Some(WalkerRollOutcomeProjection("recover",
        Vector(RolledFace.Blank, RolledFace.Blank), 0,
        Some(expectedDifficulty(ready, p1)))))))

    // The pre-Task-5 hardcoded `WalkerPowers.empty` folds the bare tree --
    // one node short of what the walker actually folded -- and can no
    // longer resolve the recorded park path at all.
    val unwired = new WalkerDecisionProjector(catalog, presentation, WalkerPowers.empty)
    assertEquals(unwired.project(context), None)

  /** Task 5b: the other query shape, and the only one with a confirm step
    * to name.
    *
    * Forge is driven to a real park through `ForgeWalkerFixture` -- the
    * same board and the same commands `GameApplicationServiceForgeSuite`
    * runs its end-to-end Forge against -- rather than through a hand-built
    * `Decide`, because the fact under test is that copy authored in
    * `ForgeProcedure` survives the rebuild, the power fold and the
    * projection untouched. A synthetic query would assert the pass-through
    * and skip the authoring.
    */
  test("a parked Forge projects the heading and the confirm label its " +
      "procedure declares, beside the sections it already declared"):
    val ForgeWalkerFixture.ParkedForge(forgeCatalog, started) =
      ForgeWalkerFixture.parkedForge("walker-projection-forge")
    // Ancient City, where p1 stands.
    val forgeSite = ForgeWalkerFixture.forgeSite
    val ready = Outcomes.ready(started.state)
    val projector = new WalkerDecisionProjector(forgeCatalog,
      new GamePresentationProjector(forgeCatalog))
    val decision = projector.project(ScopedProjectionContext(ready,
      Some(p1))).getOrElse(
        fail("the parked actor must be offered the Forge decision"))
    assertEquals(decision.decisionId, ForgeProcedure.assignmentDecisionId)
    val query = decision.query match
      case Some(partition: DecisionQueryProjection.Partition) => partition
      case other => fail(s"expected a Forge partition, got $other")

    assertEquals(query.heading, Some("Forge a relic"))
    assertEquals(query.confirmLabel, Some("Complete Forge"))
    // The copy arrives beside the sections, not instead of them: one prompt
    // frame and the minima it frames, from the same declaration.
    val printed = present(present(forgeCatalog.sites.find(_.id == forgeSite),
      "the Forge site").forgeRequirements, "a printed Forge cost")
    assertEquals(query.sections, Vector(
      DecisionSectionProjection(ForgeProcedure.favorSectionKey, "Pay Favor",
        printed.favor),
      DecisionSectionProjection(ForgeProcedure.secretSectionKey, "Pay Secret",
        printed.secrets)))

    // And it is owner-private like every other field of this projection:
    // copy is prompt text for the player being asked, not public table talk.
    assertEquals(projector.project(ScopedProjectionContext(ready,
      Some(p2))), None)

  test("a Campaign force decision carries no roll"):
    // CampaignFixture's board: p1, with 5 warbands on its board and a Supply
    // of 7, campaigns against the 2 Bandits at its origin.
    val b = oathdigital.gameplay.CampaignFixture.board(warbands = 5, supply = 7)
    val game = oathdigital.gameplay.CampaignFixture.rules()
    val started = accepted(game.startWalker(OathState.Ready(b.ready),
      ActionRef.Campaign, p1))
    val projection = new GameProjector(catalog).project("campaign",
      LoadedGame(started.state, 12), p1)
    assertEquals(projection.walkerDecision.map(_.rollOutcome), Some(None))
