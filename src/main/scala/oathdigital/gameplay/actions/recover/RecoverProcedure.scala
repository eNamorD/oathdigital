package oathdigital.gameplay.actions.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.operations._
import oathdigital.gameplay.walker.{OwnerQuery, WalkerCtx}
import oathdigital.gameplay.{DiceKind, DiceSpec, OathLifecycle, OathViolation,
  ReadyGame}
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.model.DecisionPayload.{RecoverChoice,
  RecoverChoicePayload, RecoverRelicPayload}
import oathdigital.model.{Answered, DecisionPayload, Orientation, PendingTree,
  PlayerId, PoolKey, RelicState, SiteId}

/** Declared Recover procedure tree for the walker (Task 5).
  *
  * Reproduces the legacy `Recover.handle`/`Recover.evolve` observable flow on
  * the generic walker:
  *
  * {{{
  * Sequence(                                // window = RecoverActionEligibility
  *   ModifyDicePool("recover", +2),         // window = RecoverBeforeFirstRoll
  *   Repeat(guard = not succeeded && lastChoice != Stop,
  *     Sequence(
  *       AdjustSupply(actor, -1),           // validated by OperationPipeline
  *       Roll("recover", Defense),          // parks; faces ride `roll()`
  *       Branch(choice when not yet success) // -> Decide("recover.choice") or nothing
  *     )),
  *   Branch(if success ->
  *     Vector(Decide("recover.relic"),
  *       BuildOps(move chosen relic facedown)), // window = RecoverAfterRelic
  *     else Vector.empty))                  // stopped: ends with no relic
  * }}}
  *
  * Task 4 windows (reusing `PowerModel.scala`'s existing vocabulary, no power
  * ported yet -- `OathRules.walkerPowers` legitimately offers none until
  * Task 5): the whole tree's root carries `RecoverActionEligibility`
  * (eligibility-shaped restrictions/relaxations gather here); the head
  * `ModifyDicePool` -- the first node the walker ever executes -- carries
  * `RecoverBeforeFirstRoll`; the `BuildOps` that moves the chosen relic
  * carries `RecoverAfterRelic`. `RecoverModifierSelection` is not a tree node:
  * it is the window a player-selected power is offered at, answered by
  * `StartWalker`'s `modifiers` rather than by anything in this tree (see
  * `OathRules.startWalker`/`walkerPowers`). No other node in this tree
  * carries a window.
  *
  * Semantics (ruling 5.5 + legacy parity):
  *  - Each roll = 2 defense dice (pool count fixed to 2 by the head
  *    `ModifyDicePool`) and costs 1 supply, debited by the body `BuildOps`.
  *  - Success = `DefenseDieFace.score` over the combined faces from every roll
  *    of the "recover" pool (the walker accumulates roll outcomes per pool)
  *    reaching `RecoverRules.difficulty(catalog, site)`; site = the actor's
  *    pawn site. Thus a Doubler on a later roll multiplies earlier shields.
  *  - A FAILED roll parks the continue/stop choice: Continue rolls again
  *    (validated: not-yet-successful), Stop abandons with no relic.
  *  - A successful roll parks a relic decision only when a facedown relic is
  *    available. Otherwise Recover finishes as a legal wasted action.
  *
  * `build` validates Act phase and the action-specific necessity: the actor's
  * current site has a Recover difficulty. Generic supply feasibility belongs
  * to `OperationPipeline`; role, foundation state, and relic availability do
  * not gate Recover.
  */
object RecoverProcedure {
  val recoverPool: PoolKey = PoolKey("recover")
  val choiceDecisionId: String = "recover.choice"
  val relicDecisionId: String = "recover.relic"

  /** Synthetic decision id surfaced on the `AwaitingRecoverRoll` continuation
    * when the walker parks on the Roll node itself (a `Roll` leaf carries no
    * `decisionId` of its own — that concept only exists on `Decide` nodes).
    * Client-facing identity for "answer this with `RollWalker`, not
    * `ResolveWalker`".
    */
  val rollDecisionId: String = "walker.recover.roll"

  private val supplyCost: Int = 1

  /** The actor's current pawn site -- the single definition `build`,
    * `rebuild`, and [[actorFacedownRelics]] all read, so nothing in this
    * module (or a caller outside it) can derive "the Recover site" a
    * different way and silently disagree with the others.
    */
  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  /** The facedown relics at the actor's current site -- exactly the set
    * `validateRelic` (below) accepts an answer against, read live off
    * `state.game.current.map.sites` rather than off the tree's closed-over
    * `siteId` or its inert placeholder marker (see `tree`'s doc comment).
    * The application-layer projector calls this SAME method to build the
    * candidate list a client is offered, so the projected candidates and
    * the set the resolver accepts cannot drift apart: there is exactly one
    * definition of "the actor's recoverable relics", not two expressions
    * that merely happen to agree today.
    */
  def actorFacedownRelics(state: ReadyGame, actor: PlayerId)
      : Vector[RelicState] =
    actorSite(state, actor).flatMap(state.game.current.map.sites.get).fold(
      Vector.empty[RelicState])(_.relics.filter(
      _.orientation == Orientation.FaceDown))

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId)
      : Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(oathdigital.gameplay.OathState.Ready(state),
      actor)
    siteId <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  } yield tree(actor, siteId, difficulty)

  /** Rebuilds the same command-local tree for an already-started Recover. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for {
    siteId <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  } yield tree(actor, siteId, difficulty)

  /** Tree closes only over command-stable actor, site, and difficulty. */
  private def tree(actor: PlayerId, siteId: SiteId,
      difficulty: Int): Operation = {
    def scoreOf(ready: ReadyGame): Int =
      ready.game.current.rollOutcomes.get(recoverPool).fold(0)(_.score)

    def succeeded(ready: ReadyGame): Boolean = scoreOf(ready) >= difficulty

    def stopped(pending: PendingTree): Boolean =
      pending.answered.lastOption.exists {
        case Answered(_, RecoverChoicePayload(RecoverChoice.Stop)) => true
        case _ => false
      }

    def validateChoice(ready: ReadyGame, pending: PendingTree,
        payload: DecisionPayload): Either[OathViolation, Unit] =
      payload match {
        case RecoverChoicePayload(RecoverChoice.Continue) =>
          if (succeeded(ready)) Left(OathViolation.RecoverOutcomeMismatch(
            "Recover already succeeded"))
          else Right(())
        case RecoverChoicePayload(RecoverChoice.Stop) =>
          if (succeeded(ready)) Left(OathViolation.RecoverOutcomeMismatch(
            "a successful Recover cannot be stopped"))
          else Right(())
        case other => Left(OathViolation.InvalidEventOrder(
          s"$choiceDecisionId received an unexpected payload: $other"))
      }

    // Reads `actorFacedownRelics(ready, actor)` -- the actor's LIVE pawn
    // site, re-derived from `ready` on every call -- rather than this
    // closure's own `siteId` (frozen at tree-build/rebuild time). This is
    // the same method the projector calls to build the candidate list a
    // client is offered (Task 7a finding I1): one shared definition of
    // "the actor's recoverable relics" instead of two independently
    // written expressions that could silently diverge if a future power
    // let Recover target a site other than the actor's pawn site.
    def validateRelic(ready: ReadyGame, pending: PendingTree,
        payload: DecisionPayload): Either[OathViolation, Unit] =
      payload match {
        case RecoverRelicPayload(relicId) =>
          if (actorFacedownRelics(ready, actor).exists(_.id == relicId))
            Right(())
          else Left(OathViolation.RecoverOutcomeMismatch(
            "chosen relic is not a facedown relic at the site"))
        case other => Left(OathViolation.InvalidEventOrder(
          s"$relicDecisionId received an unexpected payload: $other"))
      }

    val choiceDecide = Decide(
      payload = RecoverChoicePayload(RecoverChoice.Continue),
      owner = RecoverProcedure.ActiveOwner,
      decisionId = choiceDecisionId,
      validate = Some(validateChoice))

    val moveRelic = BuildOps((ready, pending) =>
      pending.answered.lastOption match {
        case Some(Answered(_, RecoverRelicPayload(relicId))) =>
          Right(Vector[CoreOperation](Move(
            Piece.Card(relicId),
            PositionedLocation(Location.Site(siteId)),
            PositionedLocation(Location.PlayArea(actor)),
            resultingOrientation = Some(Orientation.FaceDown))))
        case _ => Left(OathViolation.InvalidEventOrder(
          "no recovered relic answer is recorded"))
      }, window = Some(PowerWindow.RecoverAfterRelic))

    // Payment precedes Roll so OperationPipeline rejects insufficient supply
    // before randomness is requested.
    val body = Sequence(
      AdjustSupply(actor, -supplyCost),
      Roll(recoverPool, DiceSpec(DiceKind.Defense)),
      Branch((ready, _) =>
        if (succeeded(ready)) Vector.empty else Vector(choiceDecide)))

    val repeatGuard: (ReadyGame, PendingTree) => Boolean =
      (ready, pending) => !succeeded(ready) && !stopped(pending)

    // Empty-site success is legal and finishes without a decision.
    val afterLoop = Branch((ready, _) => {
      val relics = actorFacedownRelics(ready, actor)
      if (succeeded(ready) && relics.nonEmpty)
        Vector(Decide(
          payload = RecoverRelicPayload(relics.head.id),
          owner = RecoverProcedure.ActiveOwner,
          decisionId = relicDecisionId,
          validate = Some(validateRelic)), moveRelic)
      else Vector.empty
    })

    Sequence(
      ModifyDicePool(recoverPool, +2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      Repeat(repeatGuard, body),
      afterLoop
    ).copy(window = Some(PowerWindow.RecoverActionEligibility))
  }

  /** Recover decisions are resolved by the active player (Recover is an Act
    * action of the active player in this slice).
    */
  private object ActiveOwner extends OwnerQuery {
    def owner(ctx: WalkerCtx): Option[PlayerId] =
      Some(ctx.ready.game.current.turn.activePlayer)
  }
}
