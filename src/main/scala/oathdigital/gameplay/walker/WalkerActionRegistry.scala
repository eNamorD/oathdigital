package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.{MajorActionKind, OathContinue, OathViolation,
  ReadyGame}
import oathdigital.model.{ActionRef, DecisionId, PlayerId}

/** The one place an action registers its walker tree-building functions
  * (Task 8). Before this, `OathRules.buildWalker` and
  * `WalkerDecisionProjector.rebuild` each carried their own
  * `case ActionRef.Recover =>` match, so adding a second action meant
  * editing both call sites -- and a missing branch there was a runtime
  * `MatchError`, not a typed rejection. Both dispatch through `entries`
  * here instead: registering a second action is one entry, and an action
  * absent from it produces `Left(OathViolation.InvalidEventOrder(...))`.
  *
  * `build` and `rebuild` stay two methods rather than a `starting: Boolean`
  * flag threaded through one, because they are not simply "the same call
  * with a flag": `OathRules.startWalker` computes `eligibilityRelaxed`
  * itself, BEFORE any tree exists, by gathering `ContributingPower`s
  * against the `RecoverActionEligibility` window (ruling B/C -- see
  * `OathRules.eligibilityGathered`'s doc). That gathering needs the power
  * catalog and a `PowerCtx`, machinery this registry -- and the action
  * modules it dispatches to -- has no reason to import. So `build` accepts
  * the already-computed boolean as a plain flag (mirroring
  * `RecoverProcedure.build`'s own parameter), rather than recomputing the
  * eligibility decision here or pushing the power-gathering into the action
  * module. `rebuild` never re-runs start gates at all, so it carries no such
  * flag -- resuming an already-started action must not re-decide
  * eligibility.
  */
object WalkerActionRegistry {

  /** `fallbackKind` (I4) is the [[MajorActionKind]] `OathRules.startWalker`
    * runs `PowerRuntime.ignored` fallback-diagnostics against for this
    * action -- previously a bare `MajorActionKind.Recover` literal at the
    * `startWalker` call site regardless of which action was actually
    * starting.
    *
    * `rollDecisionId` (I4) is the synthetic client-facing decision id
    * surfaced when the walker parks on this action's Roll node itself (a
    * `Roll` leaf carries no `decisionId` of its own -- see
    * `RecoverProcedure`'s doc). Exposed here so both `OathRules
    * .parkedContinue` and `WalkerDecisionProjector` read the same
    * per-action value instead of each importing `RecoverProcedure`
    * directly.
    *
    * `continuationFor` (I4) maps ANY of this action's decision ids --
    * `rollDecisionId` included -- to the client-facing [[OathContinue]] it
    * produces, keyed by the id string alone (never by tree path, for the
    * same reorder-safety reason `ProcedureWalker.parkedDecide` dispatches
    * on `decisionId`). `None` for an id this action does not recognise.
    * This is the single place `OathRules.parkedContinue` consults, so it
    * carries no `RecoverProcedure`-specific match of its own.
    *
    * `modifierWindow` (batch-1 Task 1) is the [[PowerWindow]] at which a
    * player-selected `ContributingPower` is offered as a `StartWalker`
    * modifier for this action -- previously a bare
    * `PowerWindow.RecoverModifierSelection` literal at both
    * `OathRules.offerableWalkerPowers` and `OathRules.validateModifiers`,
    * which would have filtered every action's offers through Recover's
    * window the moment a second action registered.
    *
    * It is `Option` because not every action has such a window:
    * `PowerWindow` carries a `*ModifierSelection` case for each of the eight
    * [[oathdigital.gameplay.powerresolver.MajorActionType]]s and Take Wealth
    * is not one of them -- its only window is `WakeTakeWealth`, an
    * `OtherWindow` whose `associatedMajorAction` is `None`. `None` here means
    * the action offers no player-selected powers at all: `offerableWalkerPowers`
    * returns empty and `validateModifiers` rejects every id. Inventing a
    * `WakeModifierSelection` case purely to keep this field total would put a
    * window in the audited vocabulary that no rulebook clause backs.
    */
  private[gameplay] final case class Entry(
      fallbackKind: MajorActionKind,
      rollDecisionId: String,
      modifierWindow: Option[PowerWindow],
      continuationFor: (String, PlayerId, DecisionId) => Option[OathContinue],
      build: (ExecutableCatalog, ReadyGame, PlayerId, Boolean) =>
        Either[OathViolation, Operation],
      rebuild: (ExecutableCatalog, ReadyGame, PlayerId) =>
        Either[OathViolation, Operation])

  /** `private[gameplay]`, not `private`: [[WalkerActionRegistrySuite]] asserts
    * this map's keys cover `ActionRef.all` (catching a registered action
    * missing its entry) and its type is referenced when the suite calls
    * `build`/`rebuild` with a `registrations` map that omits a real,
    * registered-in-production action -- `ActionRef` is sealed with exactly
    * one inhabitant today, so there is no other way to exercise the
    * "absent from the registrations" branch without a genuinely
    * unregistered `ActionRef`, which cannot be constructed outside
    * `ActionRef.scala`.
    *
    * Widened from `private[walker]` at batch-1 Task 1: `OathRules`
    * (`oathdigital.gameplay`) now takes the same `registrations` parameter on
    * `offerableWalkerPowers`/`validateModifiers`, for the same reason and by
    * the same precedent, so the `Entry` type has to be nameable one package
    * up. It stays out of reach of every other package.
    */
  private[gameplay] val entries: Map[ActionRef, Entry] = Map(
    ActionRef.Recover -> Entry(
      fallbackKind = MajorActionKind.Recover,
      rollDecisionId = RecoverProcedure.rollDecisionId,
      modifierWindow = Some(PowerWindow.RecoverModifierSelection),
      continuationFor = (decisionId, actor, decision) => decisionId match {
        case RecoverProcedure.rollDecisionId =>
          Some(OathContinue.AwaitingRecoverRoll(actor, decision))
        case RecoverProcedure.relicDecisionId =>
          Some(OathContinue.AwaitingRecoverRelic(actor, decision))
        case RecoverProcedure.choiceDecisionId =>
          Some(OathContinue.AwaitingRecoverRoll(actor, decision))
        case _ => None
      },
      build = (catalog, state, actor, eligibilityRelaxed) =>
        RecoverProcedure.build(catalog, state, actor, eligibilityRelaxed),
      rebuild = (catalog, state, actor) =>
        RecoverProcedure.rebuild(catalog, state, actor)))

  /** Builds `action`'s tree for a fresh start: the action's full start
    * gates run, relaxed only as far as `eligibilityRelaxed` (computed by
    * the caller) allows.
    *
    * `eligibilityRelaxed` carries no default: the sole production caller
    * (`OathRules.declaredWalkerTree`) already passes it explicitly, and a
    * default here would let a future action wiring forget the flag and
    * silently get `false` instead of a compile error.
    *
    * `registrations` defaults to the production `entries` map, so every
    * production call site is unaffected; [[WalkerActionRegistrySuite]]
    * overrides it with a map that omits an action to drive this exact
    * entry point down the missing-registration branch, rather than testing
    * `lookup` as an extracted stand-in.
    */
  def build(action: ActionRef, catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, eligibilityRelaxed: Boolean,
      registrations: Map[ActionRef, Entry] = entries)
      : Either[OathViolation, Operation] =
    lookup(action, registrations).flatMap(
      _.build(catalog, state, actor, eligibilityRelaxed))

  /** Rebuilds `action`'s tree to resume an already-started walker position.
    * Start-only gates do not re-run.
    *
    * `registrations` defaults to the production `entries` map -- see
    * `build`'s doc for why.
    */
  def rebuild(action: ActionRef, catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, registrations: Map[ActionRef, Entry] = entries)
      : Either[OathViolation, Operation] =
    lookup(action, registrations).flatMap(_.rebuild(catalog, state, actor))

  private def lookup(action: ActionRef,
      registrations: Map[ActionRef, Entry]): Either[OathViolation, Entry] =
    registrations.get(action).toRight(OathViolation.InvalidEventOrder(
      s"no walker action registered for ${action.key}"))

  /** `action`'s [[MajorActionKind]] for the `PowerRuntime.ignored` fallback
    * diagnostics `OathRules.startWalker` records alongside the command (I4)
    * -- queried here instead of a bare `MajorActionKind.Recover` literal at
    * the `startWalker` call site, so a second registered action supplies
    * its own kind without editing `OathRules`.
    */
  def fallbackKind(action: ActionRef): Either[OathViolation, MajorActionKind] =
    lookup(action, entries).map(_.fallbackKind)

  /** `action`'s synthetic Roll-park decision id (I4) -- see `Entry`'s doc.
    * Both `OathRules.parkedContinue` and `WalkerDecisionProjector` read
    * this instead of `RecoverProcedure.rollDecisionId` directly.
    */
  def rollDecisionId(action: ActionRef): Either[OathViolation, String] =
    lookup(action, entries).map(_.rollDecisionId)

  /** `action`'s modifier-selection [[PowerWindow]], or `None` when the action
    * offers no player-selected powers at all -- see `Entry`'s doc.
    * `OathRules.offerableWalkerPowers` and `OathRules.validateModifiers` read
    * this instead of naming `PowerWindow.RecoverModifierSelection`.
    *
    * `registrations` defaults to the production `entries` map -- see `build`'s
    * doc for why it is a parameter at all.
    */
  def modifierWindow(action: ActionRef,
      registrations: Map[ActionRef, Entry] = entries)
      : Either[OathViolation, Option[PowerWindow]] =
    lookup(action, registrations).map(_.modifierWindow)

  /** `action`'s client-facing continuation for `decisionId` (I4) -- see
    * `Entry`'s doc. `OathRules.parkedContinue` is the sole caller: it
    * reports `None` onward as its own `InvalidEventOrder`, since only it
    * knows the parked-position context worth naming in that message.
    */
  def continuationFor(action: ActionRef, decisionId: String, actor: PlayerId,
      decision: DecisionId): Either[OathViolation, Option[OathContinue]] =
    lookup(action, entries).map(_.continuationFor(decisionId, actor, decision))

  /** Whether `action` runs on the generic walker at all (Task 9a). The
    * pre-start modifier preview asks this to decide whether to offer
    * `ContributingPower`s from the walker catalog or fall back to the legacy
    * `PowerRuntime` machinery -- this registry stays the single place that
    * knows which actions are walker-driven, so that decision needs no
    * second `ActionRef` match at the preview call site.
    */
  def isRegistered(action: ActionRef): Boolean = entries.contains(action)
}
