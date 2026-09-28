package oathdigital.gameplay.walker

import oathdigital.gameplay.{OathRules, ProcedureWalkerSuite, WalkerDiceFixture}
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.model.OathState.Ready

/** `ParkedDecisionAssertions` is tested here, driving a real `RecoverProcedure`
  * through a real `OathRules` rather than constructing a `PendingTree` by
  * hand -- the module's whole point is to derive the parked position the way
  * the walker itself derives it, so a hand-built tree would prove nothing
  * about the rebuild path it actually exercises.
  */
class ParkedDecisionAssertionsSuite extends munit.FunSuite:
  private val assertions = new ParkedDecisionAssertions(catalog)

  /** A ready game in the Act phase whose active player's pawn sits on an
    * in-play Recover site with difficulty <= 4 (reachable by
    * `WalkerDiceFixture.shields` in one roll) and no facedown relic, so a
    * successful roll finishes the whole action in one command -- mirrors
    * `RecoverProcedureSuite.recoverable`, minus the relic. `RecoverProcedure
    * .build`/`rebuild` require a site with a declared Recover difficulty, so
    * every test below -- not only the successful-completion one -- needs
    * this rather than `OathRulesWalkerPowerSuite`'s plain `actable`, whose
    * pawn site (wherever setup happened to place it) may have none.
    */
  private def recoverable: (ReadyGame, PlayerId) =
    val base = Table.start.ready
    val active = base.game.current.turn.activePlayer
    val candidates = base.game.current.map.inPlay.filter(siteId =>
      RecoverRules.difficulty(catalog, siteId).exists(d => d > 0 && d <= 4))
    assert(candidates.nonEmpty,
      "fixture needs an in-play Recover site with difficulty <= 4")
    val siteId = candidates.minBy(site => RecoverRules.difficulty(catalog,
      site).get)
    val site = base.game.current.map.sites(siteId).copy(relics = Vector.empty)
    val ready = base.updateCurrent(_.copy(
      players = base.game.current.players.map(p =>
        if p.player == active then p.copy(pawnSite = Some(siteId)) else p),
      map = base.game.current.map.copy(
        sites = base.game.current.map.sites.updated(siteId, site))))
    (ready, active)

  test("a Decide park reports its procedure, decision id and owner"):
    val (ready, actor) = recoverable
    val rules = new OathRules(catalog, walkerDice = WalkerDiceFixture.blanks)
    val started = rules.startWalker(Ready(ready), ActionRef.Recover,
        actor) match
      case Right(transition) => transition
      case other => fail(s"expected the failed-roll start to run, got $other")
    assertions.assertParked(started.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)

  // The Roll branch could silently return `None` and every Recover
  // assertion built on this module would weaken to nothing -- this is the
  // one test in this suite that must not be skipped or weakened. Recover's
  // own Roll node is `Automatic` and never parks (see `RecoverProcedure
  // .rollDecisionId`'s doc), so a Roll park is produced the way a power
  // would produce one: a `Transform` folds a `Parked` `Roll` node into the
  // REAL, registry-built Recover tree at its `RecoverActionEligibility`
  // window, ahead of the declared body.
  test("a Roll park reports the procedure's declared roll decision id"):
    val (ready, actor) = recoverable
    val insertRoll = ProcedureWalkerSuite.TestTransformPower(
      PowerId("test.insert-roll"), PowerWindow.RecoverActionEligibility,
      (_, ops) => Roll(PoolKey("test.recover-roll"),
        DiceSpec(DiceKind.Defense)) +: ops)
    val powers = WalkerPowers(Vector(insertRoll))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers,
      walkerDice = WalkerDiceFixture.blanks)
    val started = rules.startWalker(Ready(ready), ActionRef.Recover,
        actor) match
      case Right(transition) => transition
      case other => fail(s"expected the Roll-inserting start to run, got $other")
    new ParkedDecisionAssertions(catalog, walkerPowerCatalog = powers)
      .assertParked(started.state, ActionRef.Recover,
        RecoverProcedure.rollDecisionId, actor)

  // Same mechanism as the Roll park above -- a `Transform` folds an
  // off-turn-owned `Decide` into the real Recover tree -- because
  // `ParkedDecisionAssertions` always rebuilds through
  // `WalkerProcedureRegistry`'s production entry, which reconstructs
  // Recover's OWN tree regardless of what an injected `walkerTree` source
  // walked; a hand-built tree under the `Recover` identity would rebuild to
  // a shape the stored `PendingTree.at` path does not address. The inserted
  // node reuses Recover's own `choiceDecisionId`, and this park never
  // reaches the tree's OWN (actor-owned) choice decide -- the inserted node
  // is walked first.
  test("an off-turn owner is reported, not the active player"):
    val (ready, actor) = recoverable
    val owner = ready.game.current.players.map(_.player)
      .find(_ != actor).get
    val insertOwned = ProcedureWalkerSuite.TestTransformPower(
      PowerId("test.insert-owned-decide"), PowerWindow.RecoverActionEligibility,
      (_, ops) => Decide(
        decisionId = RecoverProcedure.choiceDecisionId,
        owner = owner,
        query = DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(ProcedureWalkerSuite.continueOption,
            "Continue")))) +: ops)
    val powers = WalkerPowers(Vector(insertOwned))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers,
      walkerDice = WalkerDiceFixture.blanks)
    val started = rules.startWalker(Ready(ready), ActionRef.Recover,
        actor) match
      case Right(transition) => transition
      case other => fail(s"expected the owned-decide start to run, got $other")
    new ParkedDecisionAssertions(catalog, walkerPowerCatalog = powers)
      .assertParked(started.state, ActionRef.Recover,
        RecoverProcedure.choiceDecisionId, owner)

  // Fix-round 1 finding: `parkedDecision` must fail, not silently report
  // "not parked", when `walkerPending` is set but the module cannot explain
  // it -- otherwise a rebuild that quietly stops working would turn every
  // `assertNotParked`/`assertResumed` call site into an assertion that
  // passes unconditionally. Reuses the off-turn park from the previous
  // test, but reads it with an assertions instance that does NOT carry the
  // inserting power: `WalkerProcedureRegistry.rebuild` still succeeds (it
  // is the same real Recover tree), but folding with no powers puts no
  // off-turn Decide at index 0 -- the stored `walkerPending.at` path (which
  // pointed at the inserted node) instead resolves to Recover's own
  // `ModifyDicePool` leaf, neither a Decide nor a Roll.
  test("a park the module cannot read fails, rather than reporting not " +
      "parked"):
    val (ready, actor) = recoverable
    val owner = ready.game.current.players.map(_.player)
      .find(_ != actor).get
    val insertOwned = ProcedureWalkerSuite.TestTransformPower(
      PowerId("test.insert-owned-decide-unread"),
      PowerWindow.RecoverActionEligibility,
      (_, ops) => Decide(
        decisionId = RecoverProcedure.choiceDecisionId,
        owner = owner,
        query = DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(ProcedureWalkerSuite.continueOption,
            "Continue")))) +: ops)
    val powers = WalkerPowers(Vector(insertOwned))
    val rules = new OathRules(catalog, walkerPowerCatalog = powers,
      walkerDice = WalkerDiceFixture.blanks)
    val started = rules.startWalker(Ready(ready), ActionRef.Recover,
        actor) match
      case Right(transition) => transition
      case other => fail(s"expected the owned-decide start to run, got $other")
    // No walkerPowerCatalog here: the rebuild succeeds but folds in nothing.
    intercept[munit.FailException]:
      new ParkedDecisionAssertions(catalog).assertNotParked(started.state)

  test("a completed action is not parked"):
    val (ready, actor) = recoverable
    val rules = new OathRules(catalog, walkerDice = WalkerDiceFixture.shields)
    val finished = rules.startWalker(Ready(ready), ActionRef.Recover,
        actor) match
      case Right(transition) => transition
      case other => fail(s"expected the successful Recover to run, got $other")
    assertions.assertNotParked(finished.state)

  test("a state that is not Ready is not parked"):
    assertions.assertNotParked(OathState.NoGame)
