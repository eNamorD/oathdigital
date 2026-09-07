package oathdigital.gameplay.actions.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.operations._
import oathdigital.gameplay.walker.{OwnerQuery, WalkerCtx}
import oathdigital.gameplay.{DiceKind, DiceSpec, OathState, OathViolation,
  ReadyGame}
import oathdigital.model.DecisionPayload.{RecoverChoice,
  RecoverChoicePayload, RecoverRelicPayload}
import oathdigital.model.{Answered, DecisionPayload, Orientation, PendingTree,
  PlayerId, PoolKey, RelicId, RelicState, SiteId}

/** Declared Recover procedure tree for the walker (Task 5).
  *
  * Reproduces the legacy `Recover.handle`/`Recover.evolve` observable flow on
  * the generic walker:
  *
  * {{{
  * Sequence(
  *   ModifyDicePool("recover", +2),
  *   Repeat(guard = not succeeded && lastChoice != Stop,
  *     Sequence(
  *       Roll("recover", Defense),          // parks; faces ride `roll()`
  *       BuildOps(pay 1 supply),            // AdjustSupply(actor, -1) per roll
  *       Branch(choice when not yet success) // -> Decide("recover.choice") or nothing
  *     )),
  *   Branch(if success ->
  *     Vector(Decide("recover.relic"), BuildOps(move chosen relic facedown)),
  *     else Vector.empty))                  // stopped: ends with no relic
  * }}}
  *
  * Semantics (ruling 5.5 + legacy parity):
  *  - Each roll = 2 defense dice (pool count fixed to 2 by the head
  *    `ModifyDicePool`) and costs 1 supply, debited by the body `BuildOps`.
  *  - Success = cumulative `DefenseDieFace.score` over every roll of the
  *    "recover" pool (the walker accumulates roll outcomes per pool) reaching
  *    `RecoverRules.difficulty(catalog, site)`; site = the actor's pawn site.
  *  - A FAILED roll parks the continue/stop choice: Continue rolls again
  *    (validated: not-yet-successful and supply remains to pay for the next
  *    roll), Stop abandons with no relic (validated: not-yet-successful).
  *  - A SUCCESSFUL roll skips the choice and parks the success-only relic
  *    decision; TakeRelic is validated against the site's facedown relics and
  *    moves the chosen relic facedown to the actor's play area.
  *
  * `build` needs the [[ExecutableCatalog]] to read the site difficulty, so its
  * signature is `build(catalog, state, action)` rather than the brief's
  * `build(ctx)` (documented deviation, pre-approved by the task ruling). Start
  * eligibility mirrors the legacy start gate: `RecoverRules.validateAction`
  * (Act context, pawn at the site, difficulty present, supply >= 1, exile-only
  * unaltered foundations) PLUS a facedown-relic-at-the-site check — the same
  * relic-presence condition legacy `RecoverRules.validate` enforces on every
  * roll. The latter is what makes a started Recover always have a legal relic
  * answer once it succeeds: without it a successful roll on a relic-less site
  * would park at `"recover.relic"` with no legal resolution and no exit (a
  * deadlock legacy fails cleanly at start).
  */
object RecoverProcedure {
  val recoverPool: PoolKey = PoolKey("recover")
  val choiceDecisionId: String = "recover.choice"
  val relicDecisionId: String = "recover.relic"

  private val supplyCost: Int = 1

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for {
    siteId <- state.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(actor))
    _ <- RecoverRules.validateAction(catalog, OathState.Ready(state), actor,
      siteId)
    _ <- gateFacedownRelic(state, siteId)
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  } yield tree(state, actor, siteId, difficulty)

  /** Rebuilds the same command-local tree for an already-started Recover.
    * Start-only gates (notably supply >= 1) do not re-run: a player who spent
    * their last supply on a failed roll must still be able to resolve Stop.
    */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for {
    siteId <- state.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(actor))
    difficulty <- RecoverRules.difficulty(catalog, siteId).toRight(
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
  } yield tree(state, actor, siteId, difficulty)

  /** Build rejects a site with no facedown relic: the walker's only legal
    * answer at the success-only `"recover.relic"` decision is a facedown site
    * relic, so starting without one would leave the resolved action with no
    * legal choice (legacy `RecoverRules.validate` fails the same start the
    * same way).
    */
  private def gateFacedownRelic(state: ReadyGame,
      siteId: SiteId): Either[OathViolation, Unit] =
    state.game.current.map.sites.get(siteId) match {
      case Some(site) if site.relics.exists(
          _.orientation == Orientation.FaceDown) => Right(())
      case _ => Left(OathViolation.RecoverUnavailable(
        "site has no facedown relic"))
    }

  /** The tree closes only over command-stable data (actor, site, difficulty,
    * and the site's current facedown relic as a payload marker — the actual
    * chosen relic rides the resolve answer), so the walker can re-derive the
    * same tree each command.
    */
  private def tree(state: ReadyGame, actor: PlayerId, siteId: SiteId,
      difficulty: Int): Operation = {
    // Payload markers: a Decide's `payload` only type-tags the choice; the
    // concrete answer rides `resolve`. The relic marker carries one known
    // facedown site relic id (Replay-safe: the marker never leaves the tree).
    val markerRelic: RelicId =
      state.game.current.map.sites.get(siteId).flatMap(_.relics.headOption)
        .fold(RelicId(""))(_.id)

    def supplyOf(ready: ReadyGame): Int =
      ready.game.current.players.find(_.player == actor)
        .fold(0)(_.board.supply.supply)

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
          else if (supplyOf(ready) < supplyCost)
            Left(OathViolation.InsufficientSupply(supplyCost, supplyOf(ready)))
          else Right(())
        case RecoverChoicePayload(RecoverChoice.Stop) =>
          if (succeeded(ready)) Left(OathViolation.RecoverOutcomeMismatch(
            "a successful Recover cannot be stopped"))
          else Right(())
        case other => Left(OathViolation.InvalidEventOrder(
          s"$choiceDecisionId received an unexpected payload: $other"))
      }

    def validateRelic(ready: ReadyGame, pending: PendingTree,
        payload: DecisionPayload): Either[OathViolation, Unit] =
      payload match {
        case RecoverRelicPayload(relicId) =>
          val siteRelics = ready.game.current.map.sites.get(siteId)
            .fold(Vector.empty[RelicState])(_.relics)
          if (siteRelics.exists(relic => relic.id == relicId &&
              relic.orientation == Orientation.FaceDown)) Right(())
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

    val relicDecide = Decide(
      payload = RecoverRelicPayload(markerRelic),
      owner = RecoverProcedure.ActiveOwner,
      decisionId = relicDecisionId,
      validate = Some(validateRelic))

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
      })

    // Loop body: a roll parks (faces ride roll()), the roll's 1-supply
    // payment runs, then — only while the roll did NOT reach the difficulty —
    // the continue/stop choice parks. A succeeding roll walks past the choice
    // so the Repeat guard (checked at the pass boundary) exits the loop.
    val body = Sequence(
      Roll(recoverPool, DiceSpec(DiceKind.Defense)),
      BuildOps((_, _) => Right(Vector[CoreOperation](
        AdjustSupply(actor, -supplyCost)))),
      Branch((ready, _) =>
        if (succeeded(ready)) Vector.empty else Vector(choiceDecide)))

    val repeatGuard: (ReadyGame, PendingTree) => Boolean =
      (ready, pending) => !succeeded(ready) && !stopped(pending)

    // After the loop: success walks the relic choice + facedown move; a stop
    // (or any non-success exit) walks nothing, ending the action relic-free.
    val afterLoop = Branch((ready, _) =>
      if (succeeded(ready)) Vector(relicDecide, moveRelic)
      else Vector.empty)

    Sequence(
      ModifyDicePool(recoverPool, +2),
      Repeat(repeatGuard, body),
      afterLoop)
  }

  /** Recover decisions are resolved by the active player (Recover is an Act
    * action of the active player in this slice).
    */
  private object ActiveOwner extends OwnerQuery {
    def owner(ctx: WalkerCtx): Option[PlayerId] =
      Some(ctx.ready.game.current.turn.activePlayer)
  }
}
