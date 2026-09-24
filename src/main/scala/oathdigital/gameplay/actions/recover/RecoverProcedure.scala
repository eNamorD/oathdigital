package oathdigital.gameplay.actions.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.walker.{WalkerPowers, WalkerRollFeedback,
  WalkerSimulation}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Declared Recover procedure tree for the [[oathdigital.gameplay.walker.ProcedureWalker]]}.
  *
  * {{{
  * Sequence(                                // window = RecoverActionEligibility
  *   ModifyDicePool("recover", +2),         // window = RecoverBeforeFirstRoll
  *   Repeat(guard = not succeeded && lastChoice != Stop,
  *     Sequence(
  *       SpendSupply(actor, 1),            // validated by OperationPipeline
  *       Roll("recover", Defense, Automatic), // rolls in the same command
  *       Branch(choice when not yet success) // -> Decide("recover.choice") or nothing
  *     )),
  *   Branch(if success ->
  *     Vector(Decide("recover.relic"),
  *       BuildOps(move chosen relic facedown)), // window = RecoverAfterRelic
  *     else Vector.empty))                  // stopped: ends with no relic
  * }}}
  *
  * Descriptions of PowerWindows:
  * `RecoverActionEligibility` - At the start of the action, for checking prerequisites
  * `RecoverBeforeFirstRoll` - Before the very first roll
  * `RecoverAfterRelic` - After the relic is chosen
  *
  * Semantics:
  *  - Each roll = 2 defense dice (pool count fixed to 2 by the head
  *    `ModifyDicePool`) and costs 1 supply, debited by `SpendSupply`.
  *  - The roll is automatic: the supply is spent before the dice are asked
  *    for, so a park in front of them would ask the player to confirm a
  *    roll they have already paid for and cannot decline. The walk rolls
  *    and carries on to the first thing that is a question.
  *  - Success = `DefenseDieFace.score` over the combined faces from every roll
  *    of the "recover" pool (the walker accumulates roll outcomes per pool)
  *    reaching `RecoverRules.difficulty(catalog, site)`; site = the actor's
  *    pawn site. Thus a Doubler on a later roll multiplies earlier shields.
  *  - A FAILED roll parks the continue/stop choice: Continue rolls again,
  *    Stop abandons with no relic. The choice is only reachable while the
  *    recovery has not yet succeeded, because the `Branch` carrying it
  *    declares nothing once it has.
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
    * when the walker parks on a Roll node itself (a `Roll` leaf carries no
    * `decisionId` of its own — that concept only exists on `Decide` nodes).
    * Client-facing identity for "answer this with `RollWalker`, not
    * `ResolveWalker`".
    *
    * Recover's own roll is automatic and never parks, so this names a roll
    * a power folded into the tree. It stays declared because the client has
    * no other way to answer one.
    */
  val rollDecisionId: String = "walker.recover.roll"

  private val supplyCost: Int = 1

  /** The actor's current pawn site -- the single definition `build`,
    * `rebuild`, and [[actorFacedownRelics]] all read, so nothing in this
    * module (or a caller outside it) can derive "the Recover site" a
    * different way and silently disagree with the others.
    */
  /** Every park of a Recover shows the same thing: the pool rolled so far and
    * the site's difficulty, which is worth showing before the first roll too.
    */
  def rollFeedback(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, decisionId: String): Option[WalkerRollFeedback] =
    Option.when(decisionId == rollDecisionId ||
        decisionId == relicDecisionId || decisionId == choiceDecisionId)(
      WalkerRollFeedback(recoverPool, target = actorSite(ready, actor)
        .flatMap(RecoverRules.difficulty(catalog, _)))).filter(_.target.nonEmpty)

  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  /** The facedown relics at the actor's current site -- exactly the options
    * the relic decision declares, read live off `state.game.current.map.sites`
    * rather than off the tree's closed-over `siteId`. The application-layer
    * projector projects that declared query, so the candidates a client is
    * offered and the set the resolver accepts cannot drift apart: there is
    * exactly one definition of "the actor's recoverable relics", not two
    * expressions that merely happen to agree today.
    */
  def actorFacedownRelics(state: ReadyGame, actor: PlayerId)
      : Vector[RelicState] =
    actorSite(state, actor).flatMap(state.game.current.map.sites.get).fold(
      Vector.empty[RelicState])(_.relics.filter(
      _.orientation == Orientation.FaceDown))

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId)
      : Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(oathdigital.model.OathState.Ready(state),
      activePlayer)
    siteId <- actorSite(state, activePlayer).toRight(
      OathViolation.PawnSiteMissing(activePlayer))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  } yield tree(activePlayer, siteId, difficulty)

  /** Whether Recover could start now: the gates pass and the first walk (the
    * Supply cost, up to the first roll) is accepted, so a player without the
    * Supply is not offered it.
    */
  def startable(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers): Boolean =
    build(catalog, state, activePlayer)
      .exists(WalkerSimulation.starts(_, state, powers))

  /** Rebuilds the same command-local tree for an already-started Recover. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] = for {
    siteId <- actorSite(state, activePlayer).toRight(
      OathViolation.PawnSiteMissing(activePlayer))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  } yield tree(activePlayer, siteId, difficulty)

  /** Tree closes only over command-stable actor, site, and difficulty. */
  private def tree(actor: PlayerId, siteId: SiteId,
      difficulty: Int): Operation = {
    def scoreOf(ready: ReadyGame): Int =
      ready.game.current.rollOutcomes.get(recoverPool).fold(0)(_.score)

    def succeeded(ready: ReadyGame): Boolean = scoreOf(ready) >= difficulty

    // The two buttons the continue/stop decision offers, declared once: the
    // query the player is shown, the set the walker accepts an answer from,
    // and the `Repeat` guard below all read these same two references.
    val continueOption = DecisionOptionRef.Button("continue")
    val stopOption = DecisionOptionRef.Button("stop")

    def stopped(pending: PendingTree): Boolean =
      pending.answered.lastOption.exists {
        case Answered(_, ChooseOneAnswer(selected), _) => selected == stopOption
        case _ => false
      }

    // No `validate` closure guards this node, and that is not a lost check.
    // Its whole body rejected a continue-or-stop answer once the recovery had
    // already succeeded -- and the `Branch` carrying this node already omits
    // it in exactly that case, so on a rebuilt tree a stale choice answer
    // finds no matching `Decide` to resume at and is rejected before any
    // query is consulted.
    val choiceDecide = Decide(
      decisionId = choiceDecisionId,
      owner = actor,
      query = DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(continueOption, "Continue"),
        DecisionOption.Button(stopOption, "Stop")),
        // The panel's title, declared here with the button copy it frames
        // (plan ruling R4). The Roll park above it keeps a frontend literal
        // instead: a `Roll` node asks nothing, so it has no query to carry
        // a heading on.
        heading = Some("Recover")))

    val moveRelic = BuildOps((ready, pending) =>
      pending.answered.lastOption match {
        case Some(Answered(_, ChooseOneAnswer(
            DecisionOptionRef.Relic(relicId)), _)) =>
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
      SpendSupply(actor, supplyCost),
      Roll(recoverPool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
      Branch((ready, _) =>
        if (succeeded(ready)) Vector.empty else Vector(choiceDecide)))

    val repeatGuard: (ReadyGame, PendingTree) => Boolean =
      (ready, pending) => !succeeded(ready) && !stopped(pending)

    // Empty-site success is legal and finishes without a decision.
    //
    // The relic query is built from `actorFacedownRelics(ready, actor)` --
    // the actor's LIVE pawn site, re-derived from `ready` on every command --
    // rather than from this closure's own `siteId`, which froze at
    // build/rebuild time. That one method is also what the projector reads to
    // offer a client its candidates, so the offered set and the accepted set
    // are the SAME expression rather than two that agree only by convention.
    // A relic that left the site between the park and the answer is simply
    // absent from the rebuilt query, so the answer naming it is rejected.
    val afterLoop = Branch((ready, _) => {
      val relics = actorFacedownRelics(ready, actor)
      if (succeeded(ready) && relics.nonEmpty)
        Vector(Decide(
          decisionId = relicDecisionId,
          owner = actor,
          query = DecisionQuery.ChooseOne(relics.map(relic =>
            DecisionOption.Relic(DecisionOptionRef.Relic(relic.id))),
            heading = Some("Take a relic"))),
          moveRelic)
      else Vector.empty
    })

    Sequence(
      ModifyDicePool(recoverPool, +2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      Repeat(repeatGuard, body),
      afterLoop
    ).copy(window = Some(PowerWindow.RecoverActionEligibility))
  }
}
