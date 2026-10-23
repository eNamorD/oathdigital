package oathdigital.gameplay

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerParked, WalkerPowers, WalkerProcedureRegistry,
  WalkerStepRecorded}
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.testkit.{MenuPlay, Outcomes, PowerStubs, Table, WalkerStubs}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Wiring at the aggregate boundary: `OathRules` gathers restrictions
  * once per command, at command entry, and rejects the whole command before
  * any node runs.
  *
  * These tests drive REAL commands (`startWalker` and a real resume) instead
  * of re-composing the check the way a unit test of
  * `ProcedureWalker.restrictionViolations` would: delete
  * `OathRules.checkRestrictions` from either call site and a `Left` here
  * becomes a walk, failing the test.
  *
  * The tree a command walks is supplied through `OathRules`' `walkerTree`
  * seam rather than read from a real action -- the same injectable-default
  * shape `warExhaustionRandomPort` already uses, and the production default
  * is what every other walker suite exercises. The wiring under test is
  * generic; the tree only has to carry a hookable node.
  */
class OathRulesWalkerPowerSuite extends munit.FunSuite:
  private val window: PowerWindow = PowerWindow.RecoverModifierSelection

  private val violation: OathViolation = OathViolation.RecoverUnavailable(
    "a test power forbids this action")

  /** `Sequence(WindowedNode(window, Vector(decide, decide)))`: two decisions,
    * so resolving the first parks again instead of completing the action and
    * the assertions stay inside the walker's own surface. Their ids are the
    * two `OathRules` maps to a client continuation.
    */
  private def hookedTree(actor: PlayerId): Operation =
    def decide(id: String) = Decide(
      decisionId = id,
      owner = actor,
      query = DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(WalkerStubs.continueOption,
          "Continue"))))
    Sequence(WalkerStubs.WindowedNode(window, Vector(
      decide(RecoverProcedure.choiceDecisionId),
      decide(RecoverProcedure.relicDecisionId))))

  private def rules(actor: PlayerId, powers: WalkerPowers): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerTree = (_, _, _, _, _, _) => Right(hookedTree(actor)))

  private def forbidding: WalkerPowers = WalkerPowers(Vector(
    PowerStubs.RestrictionStub(PowerId("test.forbid"), window,
      (_, _) => Some(violation))))

  test("startWalker rejects a requester who is not the active player before " +
      "building anything"):
    val ready = Table.start.ready
    val intruder = p2
    // The injected tree would build and park for anyone, so a rejection here
    // is the walker's own requester check and not a procedure's gate.
    assertEquals(
      rules(p1, WalkerPowers.empty).startWalker(Ready(ready),
        ActionRef.Recover, intruder),
      Left(OathViolation.WrongPlayer(p1, intruder)))

  test("a resolved answer records who answered, on the step and in pending"):
    val ready = Table.start.ready
    val started = accepted(rules(p1, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, p1))
    val resumed = accepted(rules(p1, WalkerPowers.empty)
      .resolveWalker(started.state, p1, RecoverProcedure.choiceDecisionId,
        DecisionAnswer.ChooseOneAnswer(WalkerStubs.continueOption)))
    assert(resumed.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _) => by == p1
      case _ => false
    })
    val after = Outcomes.ready(resumed.state)
    assertEquals(after.game.current.walkerPending.map(_.answered.map(_.by)),
      Some(Vector(p1)))

  test("a restriction rejects a real startWalker command, appending no events"):
    val ready = Table.start.ready

    // Control: with no power the same command runs and appends events.
    val started = rules(p1, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, p1) match
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted command to run, got $other")
    assertEquals(started.events, Vector[OathEvent](WalkerParked(
      ActionRef.Recover, Vector("0", "0"), Vector.empty, Vector.empty)))

    // A restriction gathered at the tree's window rejects the whole command.
    // A `Left` carries no transition at all: nothing is appended and the
    // caller's state is untouched.
    assertEquals(
      rules(p1, forbidding).startWalker(Ready(ready), ActionRef.Recover, p1),
      Left(violation))

  test("a restriction rejects a real walker resume command, appending no " +
      "events"):
    val ready = Table.start.ready
    val started = rules(p1, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, p1) match
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    val answer = Answered(RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(WalkerStubs.continueOption), p1)

    // Control: the resume is legal from this parked state.
    val resumed = rules(p1, WalkerPowers.empty)
      .resolveWalker(started.state, p1, answer.decisionId, answer.answer)
    val events = resumed match
      case Right(transition) => transition.events
      case other => fail(s"expected the unrestricted resume to run, got $other")
    assert(events.exists {
      case step: WalkerStepRecorded => step.payload.isInstanceOf[ChoicePayload]
      case _ => false
    })

    assertEquals(rules(p1, forbidding).resolveWalker(started.state, p1,
      answer.decisionId, answer.answer),
      Left(violation))

  // -------------------------------------------------------------------------
  // C1: the requester bound by the transport must match the rebuilt
  // decision's owner. `Authorization.authorizeCommand` only proves the
  // caller is SOME seated player in this game -- without this check, any
  // other seated player could resolve or roll for the actor actually parked.
  // -------------------------------------------------------------------------

  test("a seated non-active player's ResolveWalker against another " +
      "player's parked decision is rejected, and appends nothing"):
    val ready = Table.start.ready
    val intruder = p2
    val started = rules(p1, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, p1) match
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    val answer = Answered(RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(WalkerStubs.continueOption), p1)

    // The intruder is rejected with WrongPlayer -- a `Left` carries no
    // transition, so nothing is appended to the actor's parked action.
    assertEquals(
      rules(p1, WalkerPowers.empty).resolveWalker(started.state, intruder,
        answer.decisionId, answer.answer),
      Left(OathViolation.WrongPlayer(p1, intruder)))

    // The actual actor can still resolve their own parked decision: the
    // check rejects the wrong caller, not the command shape.
    val resumed = rules(p1, WalkerPowers.empty)
      .resolveWalker(started.state, p1, answer.decisionId, answer.answer)
    // The actual actor's own resume still succeeds, and is recorded as theirs.
    assertEquals(Outcomes.ready(accepted(resumed).state).game.current
      .walkerPending.map(_.answered.map(_.by)), Some(Vector(p1)))

  test("a non-active player's RollWalker is rejected before the tree is " +
      "rebuilt, so a restriction cannot mask the wrong player"):
    val ready = Table.start.ready
    val intruder = p2
    val started = rules(p1, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, p1) match
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")

    // `forbidding` rejects the rebuilt tree, so only a requester check that
    // runs before the rebuild can answer WrongPlayer here.
    assertEquals(
      rules(p1, forbidding).rollWalkerPrepared(started.state, intruder,
        RecoverProcedure.recoverPool)(_ =>
          fail("an intruder's roll must never prepare faces")),
      Left(OathViolation.WrongPlayer(p1, intruder)))

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
    PowerStubs.WindowScopedStub(id, at)

  /** A registry entry that differs from Recover's in nothing but its
    * `modifierWindow`. `build`/`rebuild` are never invoked on these paths --
    * `offerableWalkerPowers` reads the window and stops.
    */
  private def entryWindowed(modifierWindow: Option[PowerWindow])
      : WalkerProcedureRegistry.Entry =
    WalkerProcedureRegistry.Entry(
      fallbackKind = Some(ActionKind.Recover),
      rollDecisionId = Some(RecoverProcedure.rollDecisionId),
      modifierWindow = modifierWindow,
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
      "action and not for Recover"):
    val ready = Table.start.ready
    val power = windowScoped(forgeWindowed,
      PowerWindow.ForgeModifierSelection)
    val rulesInstance = rules(p1, WalkerPowers(Vector(power)))

    // Offered for the entry whose modifierWindow is ForgeModifierSelection.
    assertEquals(
      rulesInstance.offerableWalkerPowers(ready, p1, ActionRef.Recover,
        registered(Some(PowerWindow.ForgeModifierSelection))).map(_.map(_.id)),
      Right(Vector(forgeWindowed)))

    // NOT offered for Recover's own production entry, whose modifierWindow is
    // RecoverModifierSelection. Before this task both calls consulted the
    // Recover literal, so both sides returned the same set.
    assertEquals(
      rulesInstance.offerableWalkerPowers(ready, p1, ActionRef.Recover)
        .map(_.map(_.id)),
      Right(Vector.empty[PowerId]))

  test("an entry declaring no modifier window offers nothing"):
    val ready = Table.start.ready
    val power = windowScoped(forgeWindowed,
      PowerWindow.ForgeModifierSelection)
    val rulesInstance = rules(p1, WalkerPowers(Vector(power)))
    val windowless = registered(None)

    assertEquals(rulesInstance.offerableWalkerPowers(ready, p1,
      ActionRef.Recover, windowless), Right(Vector.empty[ContributingPower]))

  test("Recover's own entry offers a power at its modifier window, and not " +
      "one at another action's"):
    val ready = Table.start.ready
    val recoverWindowed = PowerId("test.recover-windowed")
    val populated = WalkerPowers(Vector(
      windowScoped(recoverWindowed, PowerWindow.RecoverModifierSelection),
      windowScoped(forgeWindowed, PowerWindow.ForgeModifierSelection)))
    assertEquals(rules(p1, populated)
      .offerableWalkerPowers(ready, p1, ActionRef.Recover)
      .map(_.map(_.id)),
      Right(Vector(recoverWindowed)))

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
  // `checkAnswerable`'s Roll branch is only reachable from a tree carrying a
  // `Roll` node, and Forge's real tree has none, so the injected
  // `walkerTree` seam supplies one. The control below is the same tree
  // under `ActionRef.Recover`: the ONLY difference between the two calls is
  // which action's entry the accessor is asked about.
  // -------------------------------------------------------------------------

  test("a Roll park under an action declaring no roll decision id rejects " +
      "the whole command with the accessor's typed Left, appending nothing"):
    val ready = Table.start.ready
    val rollTree: Operation = Sequence(
      Roll(PoolKey("test.roll"), DiceSpec(DiceKind.Defense)))
    val rulesInstance = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers.empty,
      walkerTree = (_, _, _, _, _, _) => Right(rollTree))

    // Control: the identical tree under Recover, whose entry DOES declare a
    // roll decision id, parks and is handed that id's continuation.
    val started = rulesInstance.startWalker(Ready(ready), ActionRef.Recover,
        p1) match
      case Right(transition) => transition
      case other => fail(s"expected the Recover roll park to run, got $other")
    val startedReady = Outcomes.ready(started.state)
    assertEquals(startedReady.game.current.walkerProcedure,
      Some(ActionRef.Recover))
    val pending = startedReady.game.current.walkerPending.get
    assertEquals(ProcedureWalker.awaitedPlayer(startedReady, rollTree, pending,
      WalkerPowers.empty), Some(p1))
    assertEquals(started.events, Vector[OathEvent](WalkerParked(
      ActionRef.Recover, Vector("0"), Vector.empty, Vector.empty)))

    // Forge declares none: the rejection is carried through as the command's
    // own `Left`, so no `WalkerParked` is appended and the client is never
    // handed a decision id to send back.
    assertEquals(
      rulesInstance.startWalker(Ready(ready), ActionRef.Forge, p1),
      Left(OathViolation.InvalidEventOrder(
        "walker procedure forge declares no roll decision id")))

  // -------------------------------------------------------------------------
  // Task 5: off-turn ownership and any-phase resume.
  // -------------------------------------------------------------------------

  /** A single decision owned by `owner`, which need not be the active player. */
  private def ownedTree(owner: PlayerId): Operation = Sequence(Decide(
    decisionId = RecoverProcedure.choiceDecisionId,
    owner = owner,
    query = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(WalkerStubs.continueOption, "Continue")))))

  private def ownedRules(owner: PlayerId): OathRules =
    new OathRules(catalog, walkerTree = (_, _, _, _, _, _) =>
      Right(ownedTree(owner)))

  private val continue = DecisionAnswer.ChooseOneAnswer(
    WalkerStubs.continueOption)

  test("an off-turn decision is answered by its owner, and the active player " +
      "is rejected"):
    val ready = Table.start.ready
    val owner = p2
    val started = accepted(
      ownedRules(owner).startWalker(Ready(ready), ActionRef.Recover, p1))
    // `ownedRules` injects a synthetic tree through the same `walkerTree` seam
    // as the Roll-park control above, so `ParkedDecisionAssertions` cannot
    // rebuild it either: it would rebuild the real Recover tree, which the
    // injected tree's pending path does not address. The decision id and the
    // owner are instead proven directly below, by the `WrongPlayer` the
    // active player draws and the owner's own successful answer.
    val startedReady = Outcomes.ready(started.state)
    assertEquals(startedReady.game.current.walkerProcedure,
      Some(ActionRef.Recover))
    assertEquals(startedReady.game.current.walkerPending,
      Some(PendingTree(Vector("0"), Vector.empty)))
    assertEquals(ownedRules(owner).resolveWalker(started.state, p1,
      RecoverProcedure.choiceDecisionId, continue),
      Left(OathViolation.WrongPlayer(owner, p1)))
    val answered = ownedRules(owner).resolveWalker(started.state, owner,
      RecoverProcedure.choiceDecisionId, continue)
    // The owner's answer is accepted and finishes the one-decision tree.
    assertEquals(MenuPlay.pendingBeyondMenu(
      Outcomes.ready(accepted(answered).state)), None)

  test("a walker parks and resumes outside the Act phase"):
    val wake = Table.start.turn(p1, Phase.Wake).ready
    val started = accepted(rules(p1, WalkerPowers.empty)
      .startWalker(Ready(wake), ActionRef.Recover, p1))
    assertEquals(started.events, Vector[OathEvent](WalkerParked(
      ActionRef.Recover, Vector("0", "0"), Vector.empty, Vector.empty)))
    val resumed = rules(p1, WalkerPowers.empty).resolveWalker(started.state,
      p1, RecoverProcedure.choiceDecisionId, continue)
    // A Wake resume is not phase-gated: it is accepted and stays in Wake.
    assertEquals(Outcomes.ready(accepted(resumed).state).game.current.turn
      .phase, Phase.Wake)

