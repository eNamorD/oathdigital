package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.model.{ActionRef, PlayerId}

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

  private[walker] final case class Entry(
      build: (ExecutableCatalog, ReadyGame, PlayerId, Boolean) =>
        Either[OathViolation, Operation],
      rebuild: (ExecutableCatalog, ReadyGame, PlayerId) =>
        Either[OathViolation, Operation])

  /** `private[walker]`, not `private`: [[WalkerActionRegistrySuite]] asserts
    * this map's keys cover `ActionRef.all` (catching a registered action
    * missing its entry) and its type is referenced when the suite calls
    * `build`/`rebuild` with a `registrations` map that omits a real,
    * registered-in-production action -- `ActionRef` is sealed with exactly
    * one inhabitant today, so there is no other way to exercise the
    * "absent from the registrations" branch without a genuinely
    * unregistered `ActionRef`, which cannot be constructed outside
    * `ActionRef.scala`.
    */
  private[walker] val entries: Map[ActionRef, Entry] = Map(
    ActionRef.Recover -> Entry(
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

  /** Whether `action` runs on the generic walker at all (Task 9a). The
    * pre-start modifier preview asks this to decide whether to offer
    * `ContributingPower`s from the walker catalog or fall back to the legacy
    * `PowerRuntime` machinery -- this registry stays the single place that
    * knows which actions are walker-driven, so that decision needs no
    * second `ActionRef` match at the preview call site.
    */
  def isRegistered(action: ActionRef): Boolean = entries.contains(action)
}
