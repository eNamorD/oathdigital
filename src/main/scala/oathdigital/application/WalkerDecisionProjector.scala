package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerActionRegistry,
  WalkerPowers}
import oathdigital.model.{ActionRef, DefenseDieFace, Orientation, PendingTree,
  PlayerId}
import oathdigital.protocol.projection.{CardDetailsProjection,
  WalkerDecisionProjection, WalkerRollOutcomeProjection}

/** Projects a parked generic-walker position (`CurrentGameState.walkerPending`
  * + `walkerAction`, Task 6) into the small owner-private
  * [[WalkerDecisionProjection]]. The tree rebuild below dispatches through
  * [[oathdigital.gameplay.walker.WalkerActionRegistry]] (Task 8) -- the same
  * keyed lookup `OathRules.buildWalker` resumes through -- rather than
  * matching on [[ActionRef]] itself, so this projector needs no edit when a
  * second action registers.
  *
  * Reuses [[ProcedureWalker.parkedRoll]]/[[ProcedureWalker.parkedDecide]] —
  * the same reorder-safe, decisionId-keyed introspection `OathRules`
  * dispatches on to pick a live command's `OathContinue` — rather than
  * inspecting `PendingTree.at` directly, for the identical reason: a
  * structural path match would silently point at the wrong node if
  * `RecoverProcedure`'s tree shape ever changes.
  *
  * `walkerPowerCatalog` (Task 5) is the same full catalog `OathRules`
  * offers a live command; `context.current.walkerModifiers` -- the durable
  * fact persisted from the `StartWalker` that parked here -- narrows it down
  * through `WalkerPowers.selected`, the SAME projection `OathRules
  * .walkerPowers` applies at command time. Without this, a power that
  * inserts operations at a shared window (Catacombs at
  * `RecoverActionEligibility`) would make this projector re-fold the tree
  * differently from the walker that actually parked it, misreporting the
  * parked node.
  */
private[application] final class WalkerDecisionProjector(
    catalog: ExecutableCatalog, presentation: GamePresentationProjector,
    walkerPowerCatalog: WalkerPowers) {

  def this(catalog: ExecutableCatalog, presentation: GamePresentationProjector) =
    this(catalog, presentation, WalkerPowerCatalog.default(catalog))

  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] =
    for {
      pending <- context.current.walkerPending
      action <- context.current.walkerAction
      if context.viewer.contains(pending.actor)
      tree <- rebuild(context.ready, action, pending.actor).toOption
      powers = WalkerPowers.selected(walkerPowerCatalog,
        context.current.walkerModifiers)
      projection <- parked(action, tree, context.ready, pending, powers)
    } yield projection

  private def rebuild(ready: ReadyGame, action: ActionRef, actor: PlayerId) =
    WalkerActionRegistry.rebuild(action, catalog, ready, actor)

  private def parked(action: ActionRef, tree: Operation, ready: ReadyGame,
      pending: PendingTree, powers: WalkerPowers)
      : Option[WalkerDecisionProjection] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match {
      // R18: an action whose entry declares no roll decision id has no
      // answer to "which id is this Roll park", so the accessor's typed
      // rejection is carried through as "there is nothing to project" --
      // never as a projection naming a sentinel the client would then
      // send back as a `ResolveWalker` decision id.
      case Some((pool, count)) =>
        WalkerActionRegistry.rollDecisionId(action).toOption.map(rollId =>
          WalkerDecisionProjection(action.key, rollId, "roll",
            pool = Some(pool.value), count = Some(count),
            rollOutcome = rollOutcome(ready, pending.actor)))
      case None => ProcedureWalker.parkedDecide(ready, tree, pending,
          powers).map { decide =>
        val candidates = if (decide.decisionId == RecoverProcedure.relicDecisionId)
          relicCandidates(ready, pending.actor) else Vector.empty
        WalkerDecisionProjection(action.key, decide.decisionId, "decide",
          relicCandidates = candidates,
          rollOutcome = rollOutcome(ready, pending.actor))
      }
    }

  /** The relic Decide's only legal answer is a facedown relic currently at
    * the actor's site. Rather than re-deriving "the actor's site" and
    * filtering it independently here, this calls
    * [[RecoverProcedure.actorFacedownRelics]] -- the exact method
    * `validateRelic` also calls at resolve time -- so the projected
    * candidate set and the set the resolver accepts are the SAME live
    * expression, not two expressions that happen to agree only because
    * nothing (yet) can move the actor's pawn while a decision is parked.
    */
  private def relicCandidates(ready: ReadyGame, actor: PlayerId)
      : Vector[CardDetailsProjection] =
    RecoverProcedure.actorFacedownRelics(ready, actor).map(relic =>
      presentation.cardDetails(relic.id, Some(Orientation.FaceDown),
        hidden = false))

  /** The accumulated roll feedback for `actor`'s parked Recover (I5): the
    * dice faces and derived score `ProcedureWalker` has written into
    * `CurrentGameState.rollOutcomes` for `RecoverProcedure.recoverPool` SO
    * FAR (empty/zero before the first roll -- the difficulty is still worth
    * showing then), plus the actor's current site's Recover difficulty --
    * the same two values `RecoverProcedure.build`/`rebuild` read to size the
    * tree and `RecoverRules.difficulty` exposes.
    *
    * `None` only when the actor has no pawn site or that site has no
    * configured difficulty, which should not happen for an already-started
    * Recover (`RecoverProcedure.build` requires both) -- this mirrors that
    * method's own `Option`-returning reads rather than asserting.
    *
    * Reads `RecoverProcedure`/`RecoverRules` directly, same as
    * `relicCandidates` above: this projector is already action-specific
    * (Recover is the only registered action), not a generic walker-wide
    * concept -- a second action's roll feedback would need its own pool/
    * site/difficulty story here.
    */
  private def rollOutcome(ready: ReadyGame, actor: PlayerId)
      : Option[WalkerRollOutcomeProjection] = for {
    site <- RecoverProcedure.actorSite(ready, actor)
    difficulty <- RecoverRules.difficulty(catalog, site)
  } yield {
    val outcome = ready.game.current.rollOutcomes.get(RecoverProcedure.recoverPool)
    WalkerRollOutcomeProjection(
      faces = outcome.fold(Vector.empty[DefenseDieFace])(_.faces.collect {
        case face: DefenseDieFace => face
      }).map(defenseFaceName),
      score = outcome.fold(0)(_.score),
      difficulty = difficulty)
  }

  /** Local duplicate of `WalkerEventCodec`'s (serialization-layer)
    * `encodeDefenseFace` vocabulary: the application layer may not import
    * the serialization layer (`BackendArchitectureSuite`), and
    * `PendingProcedureProjector.defenseFaceName` already establishes this
    * exact precedent for Campaign's dice projections.
    */
  private def defenseFaceName(value: DefenseDieFace): String = value match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }
}
