package oathdigital.gameplay.actions.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.dice.RollStep
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.walker.{ModifierChoice, WalkerRollFeedback}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Declared Recover procedure tree for the [[oathdigital.gameplay.walker.ProcedureWalker]]}.
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                  // the modifiers, one per
  *                                            // pass, then "Start Recover"
  *   Sequence(                                // window = RecoverActionEligibility
  *     ModifyDicePool("recover", +2),         // window = RecoverBeforeFirstRoll
  *     Repeat(guard = not succeeded && lastChoice != Stop,
  *       Sequence(
  *         SpendSupply(actor, 1),            // window = RecoverCost
  *         Roll("recover", Defense, Automatic), // window = RecoverRoll;
  *                                              // rolls in the same command
  *         Branch(choice when not yet success) // -> Decide("recover.choice") or nothing
  *       )),
  *     Branch(if success ->
  *       Vector(Decide("recover.relic"),
  *         BuildOps(move chosen relic facedown)), // window = RecoverAfterRelic
  *       else Vector.empty)))                 // stopped: ends with no relic
  * }}}
  *
  * Descriptions of PowerWindows:
  * `RecoverActionEligibility` - At the start of the Recover itself, after the modifier decision
  * `RecoverBeforeFirstRoll` - Before the very first roll
  * `RecoverAfterRelic` - After the relic is chosen
  * `RecoverRoll` - Each roll, before its reroll choice
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
  * `build` validates the action-specific necessity: the actor's
  * current site has a Recover difficulty. Generic supply feasibility belongs
  * to `OperationPipeline`; role, foundation state, and relic availability do
  * not gate Recover.
  */
object RecoverProcedure:
  val recoverPool: PoolKey = PoolKey("recover")
  val choiceDecisionId: String = "recover.choice"
  val relicDecisionId: String = "recover.relic"

  /** Synthetic decision id surfaced when the walker parks on a Roll node
    * itself (a `Roll` leaf carries no `decisionId` of its own — that concept
    * only exists on `Decide` nodes). Client-facing identity for "answer this
    * with `RollWalker`, not `ResolveWalker`".
    *
    * Recover's own roll is automatic and never parks, so this names a roll
    * a power folded into the tree. It stays declared because the client has
    * no other way to answer one.
    */
  val rollDecisionId: String = "walker.recover.roll"

  /** Every Recover decision's id starts here, and so does the id of a
    * question a Recover modifier asks (Silver Charm's). */
  val decisionPrefix: String = "recover."

  private val supplyCost: Int = 1

  /** Every park of a Recover but its modifier decision shows the same
    * thing: the pool rolled so far and the site's difficulty, which is worth
    * showing before the first roll too. A question a Recover modifier asks
    * shows it as well, since its id starts with [[decisionPrefix]]. */
  def rollFeedback(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, decisionId: String): Option[WalkerRollFeedback] =
    Option.when(decisionId == rollDecisionId ||
        (decisionId.startsWith(decisionPrefix) &&
          !ModifierPicks.isDecision(decisionId)))(
      WalkerRollFeedback(recoverPool, target = actorSite(ready, actor)
        .flatMap(RecoverRules.difficulty(catalog, _)))).filter(_.target.nonEmpty)

  /** The actor's current pawn site -- the single definition `build`,
    * `rebuild`, and [[actorFacedownRelics]] all read, so nothing in this
    * module (or a caller outside it) can derive "the Recover site" a
    * different way and silently disagree with the others.
    */
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

  /** "Start Recover": the modifier decision's button. */
  val startLabel: String = "Start Recover"

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId)
      : Either[OathViolation, Operation] = for
    built <- rebuild(catalog, state, activePlayer)
  yield built

  /** The Recover itself, after its modifier decision, behind the start's
    * gates. */
  def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] = for
    rest <- recoveryAt(catalog, state, activePlayer)
  yield rest

  /** Rebuilds the same command-local tree for an already-started Recover:
    * the modifier decision, then the Recover. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] =
    recoveryAt(catalog, state, activePlayer).map(rest =>
      Sequence(Vector(ModifierChoice.loop(activePlayer,
        PowerWindow.RecoverModifierSelection, rest, startLabel), rest)))

  /** The Recover at the actor's site, which must have a Recover
    * difficulty. */
  private def recoveryAt(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for
    siteId <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  yield recovery(actor, siteId, difficulty)

  /** Tree closes only over command-stable actor, site, and difficulty. */
  private def recovery(actor: PlayerId, siteId: SiteId,
      difficulty: Int): Operation =
    def scoreOf(ready: ReadyGame): Int =
      ready.game.current.rollOutcomes.get(recoverPool).fold(0)(_.score)

    def succeeded(ready: ReadyGame): Boolean = scoreOf(ready) >= difficulty

    // The two buttons the continue/stop decision offers, declared once: the
    // query the player is shown, the set the walker accepts an answer from,
    // and the `Repeat` guard below all read these same two references.
    val continueOption = DecisionOptionRef.Button("continue")
    val stopOption = DecisionOptionRef.Button("stop")

    def stopped(pending: PendingTree): Boolean =
      pending.answered.lastOption.exists:
        case Answered(_, ChooseOneAnswer(selected), _) => selected == stopOption
        case _ => false

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
      Sequence(Vector(SpendSupply(actor, supplyCost)),
        Some(PowerWindow.RecoverCost)),
      RollStep(recoverPool, DiceSpec(DiceKind.Defense), Some(actor),
        Some(PowerWindow.RecoverRoll)),
      Branch((ready, _) =>
        if succeeded(ready) then Vector.empty else Vector(choiceDecide)))

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
      if succeeded(ready) && relics.nonEmpty then
        Vector(Decide(
          decisionId = relicDecisionId,
          owner = actor,
          query = DecisionQuery.ChooseOne(relics.map(relic =>
            DecisionOption.Relic(DecisionOptionRef.Relic(relic.id))),
            heading = Some("Take a relic"), confirmVerb = Some("Take"))),
          moveRelic)
      else Vector.empty
    })

    Sequence(
      ModifyDicePool(recoverPool, +2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      Repeat(repeatGuard, body),
      afterLoop
    ).copy(window = Some(PowerWindow.RecoverActionEligibility))
