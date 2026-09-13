package oathdigital.gameplay

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx, PowerResolution, PowerWindow}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerCompleted, WalkerParked, WalkerPowers, WalkerProcedureRegistry,
  WalkerStepRecorded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model._

/** Task 3 wiring at the aggregate boundary: `OathRules` gathers restrictions
  * once per command, at command entry, and rejects the whole command before
  * any node runs.
  *
  * These tests drive REAL commands (`startWalker` and a real resume) instead
  * of re-composing the check the way a unit test of
  * `ProcedureWalker.restrictionViolations` would: delete
  * `OathRules.checkRestrictions` from either call site and a `Left` here
  * becomes a walk, failing the test.
  *
  * No action declares a `window` yet (Task 4 wires Recover's), so the tree a
  * command walks is supplied through `OathRules`' `walkerTree` seam -- the
  * same injectable-default shape `campaignLosingForceRegistry` and
  * `warExhaustionRandomPort` already use, and the production default is what
  * every other walker suite exercises. The wiring under test is generic; the
  * tree only has to carry a hookable node.
  */
object OathRulesWalkerPowerSuite {
  /** A `PlayerSelected` power applicable at exactly one window and nowhere
    * else -- so "which window did the call site consult?" is directly
    * observable from whether the power is offered. Declared here rather than
    * inside the suite class so it carries no outer reference.
    */
  final case class WindowScopedPower(id: PowerId, at: PowerWindow)
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    override def applicable(ctx: PowerCtx): Boolean = ctx.window == at
  }

}

class OathRulesWalkerPowerSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

  private val window: PowerWindow = PowerWindow.RecoverModifierSelection

  private val violation: OathViolation = OathViolation.RecoverUnavailable(
    "a test power forbids this action")

  /** A ready game in the Act phase. The command never reaches Recover's own
    * eligibility gates -- the injected tree source replaces them.
    */
  private def actable: (ReadyGame, PlayerId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(turn = base.game.current.turn.copy(
        phase = Phase.Act))))
    (ready, ready.game.current.turn.activePlayer)
  }

  /** `Sequence(WindowedNode(window, Vector(decide, decide)))`: two decisions,
    * so resolving the first parks again instead of completing the action and
    * the assertions stay inside the walker's own surface. Their ids are the
    * two `OathRules` maps to a client continuation.
    */
  private def hookedTree(actor: PlayerId): Operation = {
    def decide(id: String) = Decide(
      decisionId = id,
      owner = actor,
      query = DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(ProcedureWalkerSuite.continueOption,
          "Continue"))))
    Sequence(ProcedureWalkerSuite.WindowedNode(window, Vector(
      decide(RecoverProcedure.choiceDecisionId),
      decide(RecoverProcedure.relicDecisionId))))
  }

  private def rules(actor: PlayerId, powers: WalkerPowers): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerTree = (_, _, _, _, _, _) => Right(hookedTree(actor)))

  private def forbidding: WalkerPowers = WalkerPowers(Vector(
    ProcedureWalkerSuite.TestRestrictionPower(PowerId("test.forbid"), window,
      (_, _) => Some(violation))))

  test("startWalker rejects a requester who is not the active player before " +
      "building anything") {
    val (ready, actor) = actable
    val intruder = ready.game.current.players.map(_.player).find(_ != actor).get
    // The injected tree would build and park for anyone, so a rejection here
    // is the walker's own requester check and not a procedure's gate.
    assertEquals(
      rules(actor, WalkerPowers.empty).startWalker(Ready(ready),
        ActionRef.Recover, intruder),
      Left(OathViolation.WrongPlayer(actor, intruder)))
  }

  test("a resolved answer records who answered, on the step and in pending") {
    val (ready, actor) = actable
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor).toOption.get
    val resumed = rules(actor, WalkerPowers.empty).resolveWalker(started.state,
      actor, RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption))
      .toOption.get
    assert(resumed.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == actor
      case _ => false
    })
    val Ready(after) = resumed.state: @unchecked
    assertEquals(after.game.current.walkerPending.map(_.answered.map(_.by)),
      Some(Vector(actor)))
  }

  test("a restriction rejects a real startWalker command, appending no events") {
    val (ready, actor) = actable

    // Control: with no power the same command runs and appends events.
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted command to run, got $other")
    }
    assert(started.events.nonEmpty)
    assert(started.events.last.isInstanceOf[WalkerParked])

    // A restriction gathered at the tree's window rejects the whole command.
    // A `Left` carries no transition at all: nothing is appended and the
    // caller's state is untouched.
    assertEquals(
      rules(actor, forbidding).startWalker(Ready(ready), ActionRef.Recover,
        actor),
      Left(violation))
  }

  test("a restriction rejects a real walker resume command, appending no events") {
    val (ready, actor) = actable
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    }
    val answer = Answered(RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption),
      actor)

    // Control: the resume is legal from this parked state.
    val resumed = rules(actor, WalkerPowers.empty)
      .resolveWalker(started.state, actor, answer.decisionId, answer.answer)
    val events = resumed match {
      case Right(transition) => transition.events
      case other => fail(s"expected the unrestricted resume to run, got $other")
    }
    assert(events.exists {
      case step: WalkerStepRecorded => step.payload.isInstanceOf[ChoicePayload]
      case _ => false
    })

    assertEquals(rules(actor, forbidding).resolveWalker(started.state, actor,
      answer.decisionId, answer.answer),
      Left(violation))
  }

  // -------------------------------------------------------------------------
  // C1: the requester bound by the transport must match the rebuilt
  // decision's owner. `Authorization.authorizeCommand` only proves the
  // caller is SOME seated player in this game -- without this check, any
  // other seated player could resolve or roll for the actor actually parked.
  // -------------------------------------------------------------------------

  test("a seated non-active player's ResolveWalker against another " +
      "player's parked decision is rejected, and appends nothing") {
    val (ready, actor) = actable
    val intruder = ready.game.current.players.map(_.player)
      .find(_ != actor).get
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    }
    val answer = Answered(RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption),
      actor)

    // The intruder is rejected with WrongPlayer -- a `Left` carries no
    // transition, so nothing is appended to the actor's parked action.
    assertEquals(
      rules(actor, WalkerPowers.empty).resolveWalker(started.state, intruder,
        answer.decisionId, answer.answer),
      Left(OathViolation.WrongPlayer(actor, intruder)))

    // The actual actor can still resolve their own parked decision: the
    // check rejects the wrong caller, not the command shape.
    val resumed = rules(actor, WalkerPowers.empty)
      .resolveWalker(started.state, actor, answer.decisionId, answer.answer)
    assert(resumed.isRight,
      s"the actual actor's own resume must still succeed, got $resumed")
  }

  test("a seated non-active player's RollWalker against another player's " +
      "parked pool is rejected, and appends nothing") {
    val fixture = CatacombsContributionSuite.relicSite(setup)
    val intruder = fixture.ready.game.current.players.map(_.player)
      .find(_ != fixture.actor).get
    val rulesInstance = new OathRules(catalog,
      walkerPowerCatalog = oathdigital.gameplay.powers.WalkerPowerCatalog
        .default(catalog))
    val started = rulesInstance.startWalker(Ready(fixture.ready),
        ActionRef.Recover, fixture.actor, Vector.empty) match {
      case Right(transition) => transition
      case other => fail(s"expected the walker start to run, got $other")
    }

    assertEquals(
      rulesInstance.rollWalkerPrepared(started.state, intruder,
        RecoverProcedure.recoverPool)(count => Right(
          Vector.fill(count)(DefenseDieFace.Blank))),
      Left(OathViolation.WrongPlayer(fixture.actor, intruder)))
  }

  test("a non-active player's RollWalker is rejected before the tree is " +
      "rebuilt, so a restriction cannot mask the wrong player") {
    val (ready, actor) = actable
    val intruder = ready.game.current.players.map(_.player)
      .find(_ != actor).get
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    }

    // `forbidding` rejects the rebuilt tree, so only a requester check that
    // runs before the rebuild can answer WrongPlayer here.
    assertEquals(
      rules(actor, forbidding).rollWalkerPrepared(started.state, intruder,
        RecoverProcedure.recoverPool)(_ =>
          fail("an intruder's roll must never prepare faces")),
      Left(OathViolation.WrongPlayer(actor, intruder)))
  }

  // -------------------------------------------------------------------------
  // Fix-round ruling I: a player-selected `StartWalker` modifier must persist
  // across a resume -- `walkerResumeContext` reads
  // `CurrentGameState.walkerModifiers` (restored from the durable
  // `WalkerParked` fact), not an empty vector, so the SAME fold applies on
  // every command of one action.
  // -------------------------------------------------------------------------

  private def insertingPower(id: PowerId, actor: PlayerId,
      resolution: PowerResolution): ProcedureWalkerSuite.TestTransformPower =
    ProcedureWalkerSuite.TestTransformPower(id, window,
      (_, ops) => AdjustSupply(actor, -1) +: ops, resolution)

  test("a player-selected modifier chosen at StartWalker is still folded on " +
      "resume, so the resumed park addresses the leaf that actually parked") {
    val (ready, actor) = actable
    val powerId = PowerId("test.insert-adjust")
    val power = insertingPower(powerId, actor, PowerResolution.PlayerSelected)
    val rulesInstance = rules(actor, WalkerPowers(Vector(power)))

    val started = rulesInstance.startWalker(Ready(ready), ActionRef.Recover,
        actor, Vector(powerId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the modifier-selected start to run, got $other")
    }
    // The transform fired at start: the inserted AdjustSupply ran before the
    // first Decide, so the park sits one index deeper than the bare tree
    // would (folded index 1, not declared index 0) -- proof the modifier was
    // in effect for this command.
    val Ready(atFirstPark) = started.state: @unchecked
    assertEquals(atFirstPark.game.current.walkerPending.map(_.at),
      Some(Vector("0", "1")))
    assertEquals(atFirstPark.game.current.walkerModifiers, Vector(powerId))

    val answer = Answered(RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption),
      actor)
    // Without ruling I's fix, `walkerResumeContext` would fold this command
    // with an EMPTY modifiers vector: the AdjustSupply would no longer be
    // prepended, the folded vector would shift back by one, and this resume
    // would address the SECOND Decide instead of the first -- a decisionId
    // mismatch, rejected with InvalidEventOrder instead of resolving cleanly.
    val resumed = rulesInstance.resolveWalker(started.state, actor,
      answer.decisionId, answer.answer) match {
      case Right(transition) => transition
      case other => fail(
        s"expected the resume to address the parked Decide, got $other")
    }
    assert(resumed.events.exists {
      case step: WalkerStepRecorded => step.payload.isInstanceOf[ChoicePayload]
      case _ => false
    })
  }

  test("the persisted modifiers survive a replay of the event stream, " +
      "without the walker being re-run") {
    val (ready, actor) = actable
    val powerId = PowerId("test.insert-adjust")
    val power = insertingPower(powerId, actor, PowerResolution.PlayerSelected)

    val started = rules(actor, WalkerPowers(Vector(power)))
      .startWalker(Ready(ready), ActionRef.Recover, actor,
        Vector(powerId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the modifier-selected start to run, got $other")
    }

    // Replay applies recorded facts only, through `ProcedureWalker
    // .applyRecorded` -- the identical dispatch `OathRules.evolve` uses for
    // these event types in production (spec decision 5: replay never
    // re-gathers, re-transforms, or re-walks). Reconstructing purely from
    // `started.events` must reach `walkerModifiers == Vector(powerId)`
    // without invoking the walker or the power at all.
    val replayed = started.events.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))) {
      case (Right(state), event: WalkerEvent) =>
        ProcedureWalker.applyRecorded(state, event)
      case (Right(state), _) => Right(state)
      case (left, _) => left
    }
    assertEquals(replayed, Right(started.state))
    val Right(Ready(replayedReady)) = replayed: @unchecked
    assertEquals(replayedReady.game.current.walkerModifiers, Vector(powerId))
  }

  test("WalkerCompleted clears walkerModifiers along with walkerPending and " +
      "walkerProcedure") {
    val (ready, actor) = actable
    val powerId = PowerId("test.insert-adjust")
    val power = insertingPower(powerId, actor, PowerResolution.PlayerSelected)
    val rulesInstance = rules(actor, WalkerPowers(Vector(power)))

    val started = rulesInstance.startWalker(Ready(ready), ActionRef.Recover,
        actor, Vector(powerId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the modifier-selected start to run, got $other")
    }
    val Ready(atFirstPark) = started.state: @unchecked
    assertEquals(atFirstPark.game.current.walkerModifiers, Vector(powerId))

    val firstAnswer = Answered(RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption),
      actor)
    val afterFirst = rulesInstance.resolveWalker(started.state, actor,
        firstAnswer.decisionId, firstAnswer.answer) match {
      case Right(transition) => transition
      case other => fail(
        s"expected the first resume to park at the second Decide, got $other")
    }
    val Ready(atSecondPark) = afterFirst.state: @unchecked
    assertEquals(atSecondPark.game.current.walkerModifiers, Vector(powerId))

    val secondAnswer = Answered(RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(ProcedureWalkerSuite.continueOption),
      actor)
    val finished = rulesInstance.resolveWalker(afterFirst.state, actor,
        secondAnswer.decisionId, secondAnswer.answer) match {
      case Right(transition) => transition
      case other => fail(s"expected the second resume to finish the tree, got $other")
    }
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    val Ready(afterCompletion) = finished.state: @unchecked
    assertEquals(afterCompletion.game.current.walkerModifiers,
      Vector.empty[PowerId])
    assert(afterCompletion.game.current.walkerProcedure.isEmpty)
    assert(afterCompletion.game.current.walkerPending.isEmpty)
  }

  // -------------------------------------------------------------------------
  // The untested `validateModifiers` branch: a known, PlayerSelected power
  // id whose `applicable` returns false must reject distinctly from an
  // unknown id (already covered in GameApplicationServiceSuite), before the
  // walk ever starts.
  // -------------------------------------------------------------------------

  test("StartWalker rejects a known but inapplicable modifier id before the " +
      "walk runs") {
    val (ready, actor) = actable
    val powerId = PowerId("test.inapplicable")
    val inapplicable = new ContributingPower {
      def id: PowerId = powerId
      def source: RuleSourceRef = RuleSourceRef.GameRule(powerId.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
      override def resolution: PowerResolution = PowerResolution.PlayerSelected
      override def applicable(ctx: PowerCtx): Boolean = false
    }

    rules(actor, WalkerPowers(Vector(inapplicable)))
      .startWalker(Ready(ready), ActionRef.Recover, actor,
        Vector(powerId)) match {
      case Left(rejection: OathViolation.InvalidEventOrder) =>
        assert(rejection.detail.contains("is not applicable"),
          s"violation detail '${rejection.detail}' should mention " +
            "inapplicability")
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")
    }
  }

  // -------------------------------------------------------------------------
  // Batch-1 Task 1: the modifier-selection window is per-action, read from the
  // registered `WalkerProcedureRegistry.Entry`, not the literal
  // `PowerWindow.RecoverModifierSelection` both call sites used to name.
  //
  // These tests use the same `registrations`-parameter precedent
  // `WalkerProcedureRegistry.build`/`rebuild` already set (see
  // `WalkerProcedureRegistrySuite`'s doc): the trailing `registrations` map
  // stands in for a second action's entry, so the behaviour that differs
  // BETWEEN actions is provable while Recover is still the only registered
  // one. Production call sites pass nothing.
  // -------------------------------------------------------------------------

  private def windowScoped(id: PowerId, at: PowerWindow): ContributingPower =
    OathRulesWalkerPowerSuite.WindowScopedPower(id, at)

  /** A registry entry that differs from Recover's in nothing but its
    * `modifierWindow`. `build`/`rebuild` are never invoked on these paths --
    * `offerableWalkerPowers`/`validateModifiers` read the window and stop.
    */
  private def entryWindowed(modifierWindow: Option[PowerWindow])
      : WalkerProcedureRegistry.Entry =
    WalkerProcedureRegistry.Entry(
      fallbackKind = Some(MajorActionKind.Recover),
      rollDecisionId = Some(RecoverProcedure.rollDecisionId),
      modifierWindow = modifierWindow,
      continuationFor = (_, _, _) => None,
      build = (_, _, _, _) =>
        Left(OathViolation.InvalidEventOrder("build is not exercised here")),
      rebuild = (_, _, _, _) =>
        Left(OathViolation.InvalidEventOrder("rebuild is not exercised here")))

  private def registered(modifierWindow: Option[PowerWindow])
      : Map[ProcedureRef, WalkerProcedureRegistry.Entry] =
    Map(ActionRef.Recover -> entryWindowed(modifierWindow))

  private val forgeWindowed = PowerId("test.forge-windowed")

  test("offerableWalkerPowers reads the registered entry's modifier window: a " +
      "power applicable only at another action's window is offered for that " +
      "action and not for Recover") {
    val (ready, actor) = actable
    val power = windowScoped(forgeWindowed,
      PowerWindow.ForgeModifierSelection)
    val rulesInstance = rules(actor, WalkerPowers(Vector(power)))

    // Offered for the entry whose modifierWindow is ForgeModifierSelection.
    assertEquals(
      rulesInstance.offerableWalkerPowers(ready, actor, ActionRef.Recover,
        registered(Some(PowerWindow.ForgeModifierSelection))).map(_.map(_.id)),
      Right(Vector(forgeWindowed)))

    // NOT offered for Recover's own production entry, whose modifierWindow is
    // RecoverModifierSelection. Before this task both calls consulted the
    // Recover literal, so both sides returned the same set.
    assertEquals(
      rulesInstance.offerableWalkerPowers(ready, actor, ActionRef.Recover)
        .map(_.map(_.id)),
      Right(Vector.empty[PowerId]))
  }

  test("an entry declaring no modifier window offers nothing and rejects a " +
      "modifier id a windowed entry accepts") {
    val (ready, actor) = actable
    val power = windowScoped(forgeWindowed,
      PowerWindow.ForgeModifierSelection)
    val rulesInstance = rules(actor, WalkerPowers(Vector(power)))
    val windowed = registered(Some(PowerWindow.ForgeModifierSelection))
    val windowless = registered(None)

    // Control: the windowed entry accepts this id.
    assertEquals(rulesInstance.validateModifiers(ready, actor,
      ActionRef.Recover, Vector(forgeWindowed), windowed), Right(()))

    assertEquals(rulesInstance.offerableWalkerPowers(ready, actor,
      ActionRef.Recover, windowless), Right(Vector.empty[ContributingPower]))
    rulesInstance.validateModifiers(ready, actor, ActionRef.Recover,
        Vector(forgeWindowed), windowless) match {
      case Left(rejection: OathViolation.InvalidEventOrder) =>
        assert(rejection.detail.contains("is not applicable"),
          s"violation detail '${rejection.detail}' should mention " +
            "inapplicability")
        // Ruling R3: the message names the action, not "Recover".
        assert(rejection.detail.contains(ActionRef.Recover.key),
          s"violation detail '${rejection.detail}' should name the action")
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")
    }
  }

  test("Recover parity: its offerable set is what the Recover-window literal " +
      "returned, for an empty and a populated walkerPowerCatalog") {
    val (ready, actor) = actable

    assertEquals(rules(actor, WalkerPowers.empty)
      .offerableWalkerPowers(ready, actor, ActionRef.Recover),
      Right(Vector.empty[ContributingPower]))

    val recoverWindowed = PowerId("test.recover-windowed")
    val populated = WalkerPowers(Vector(
      windowScoped(recoverWindowed, PowerWindow.RecoverModifierSelection),
      windowScoped(forgeWindowed, PowerWindow.ForgeModifierSelection)))
    assertEquals(rules(actor, populated)
      .offerableWalkerPowers(ready, actor, ActionRef.Recover)
      .map(_.map(_.id)),
      Right(Vector(recoverWindowed)))
  }

  // -------------------------------------------------------------------------
  // Batch-1 Task 3, ruling R18 (P4), first consulting call site.
  //
  // `WalkerProcedureRegistry.rollDecisionId` returns a typed `Left` for an
  // action whose entry declares none, and `WalkerProcedureRegistrySuite` pins
  // that value. What that pin does NOT prove is that anyone honours it: a
  // call site that recovered with `.getOrElse("")` -- or that had been
  // handed a sentinel id instead of an Option in the first place -- would
  // keep the accessor's test green while parking the player on a decision
  // id no tree ever declares, which is precisely the failure R18 exists to
  // prevent.
  //
  // `parkedContinue`'s Roll branch is only reachable from a tree carrying a
  // `Roll` node, and Forge's real tree has none, so the injected
  // `walkerTree` seam supplies one. The control below is the same tree
  // under `ActionRef.Recover`: the ONLY difference between the two calls is
  // which action's entry the accessor is asked about.
  // -------------------------------------------------------------------------

  test("a Roll park under an action declaring no roll decision id rejects " +
      "the whole command with the accessor's typed Left, appending nothing") {
    val (ready, actor) = actable
    val rollTree: Operation = Sequence(
      Roll(PoolKey("test.roll"), DiceSpec(DiceKind.Defense)))
    val rulesInstance = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers.empty,
      walkerTree = (_, _, _, _, _, _) => Right(rollTree))

    // Control: the identical tree under Recover, whose entry DOES declare a
    // roll decision id, parks and is handed that id's continuation.
    val started = rulesInstance.startWalker(Ready(ready), ActionRef.Recover,
        actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the Recover roll park to run, got $other")
    }
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)))
    assert(started.events.nonEmpty)

    // Forge declares none: the rejection is carried through as the command's
    // own `Left`, so no `WalkerParked` is appended and the client is never
    // handed a decision id to send back.
    assertEquals(
      rulesInstance.startWalker(Ready(ready), ActionRef.Forge, actor),
      Left(OathViolation.InvalidEventOrder(
        "walker procedure forge declares no roll decision id")))
  }

  // -------------------------------------------------------------------------
  // Task 5: off-turn ownership and any-phase resume.
  // -------------------------------------------------------------------------

  /** A single decision owned by `owner`, which need not be the active player. */
  private def ownedTree(owner: PlayerId): Operation = Sequence(Decide(
    decisionId = RecoverProcedure.choiceDecisionId,
    owner = owner,
    query = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(ProcedureWalkerSuite.continueOption, "Continue")))))

  private def ownedRules(owner: PlayerId): OathRules =
    new OathRules(catalog, walkerTree = (_, _, _, _, _, _) =>
      Right(ownedTree(owner)))

  private val continue = DecisionAnswer.ChooseOneAnswer(
    ProcedureWalkerSuite.continueOption)

  test("an off-turn decision is answered by its owner, and the active player " +
      "is rejected") {
    val (ready, actor) = actable
    val owner = ready.game.current.players.map(_.player).find(_ != actor).get
    val started = ownedRules(owner).startWalker(Ready(ready), ActionRef.Recover,
      actor).toOption.get
    assertEquals(started.continue, OathContinue.AwaitingRecoverRoll(owner,
      DecisionId(RecoverProcedure.choiceDecisionId)))
    assertEquals(ownedRules(owner).resolveWalker(started.state, actor,
      RecoverProcedure.choiceDecisionId, continue),
      Left(OathViolation.WrongPlayer(owner, actor)))
    val answered = ownedRules(owner).resolveWalker(started.state, owner,
      RecoverProcedure.choiceDecisionId, continue)
    assert(answered.isRight, s"the owner's answer must be accepted, got $answered")
  }

  test("a walker parks and resumes outside the Act phase") {
    val (act, actor) = actable
    val wake = act.copy(game = act.game.copy(current = act.game.current.copy(
      turn = act.game.current.turn.copy(phase = Phase.Wake))))
    val started = rules(actor, WalkerPowers.empty).startWalker(Ready(wake),
      ActionRef.Recover, actor).toOption.get
    assert(started.events.last.isInstanceOf[WalkerParked])
    val resumed = rules(actor, WalkerPowers.empty).resolveWalker(started.state,
      actor, RecoverProcedure.choiceDecisionId, continue)
    assert(resumed.isRight, s"a Wake resume must not be phase-gated, got $resumed")
  }

  test("a procedure completing in a phase with no walker continuation is " +
      "still a typed rejection") {
    val (act, actor) = actable
    val rest = act.copy(game = act.game.copy(current = act.game.current.copy(
      turn = act.game.current.turn.copy(phase = Phase.Rest))))
    val flat = new OathRules(catalog, walkerTree = (_, _, _, _, _, _) =>
      Right(Sequence(Vector.empty)))
    assertEquals(flat.startWalker(Ready(rest), ActionRef.Recover, actor),
      Left(OathViolation.InvalidEventOrder("a walker procedure completed in " +
        "the Rest phase, which has no walker continuation")))
  }
}
