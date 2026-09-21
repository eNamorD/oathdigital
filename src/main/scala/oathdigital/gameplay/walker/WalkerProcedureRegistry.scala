package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure, PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.actions.negotiation.NegotiationProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.phases.wake.{EndWakeProcedure, TakeWealthProcedure}
import oathdigital.gameplay.phases.rest.{BeginRestProcedure, FinishRestProcedure}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model.{ActionRef, DecisionId, DecisionOptionRef, ActionKind, OathContinue, OathViolation, Operation, PhaseTransitionRef, PlayerId, PowerId, PowerWindow, ProcedureRef, ReadyGame, StartableRef, TriggeredProcedureRef}

/** The one place a procedure registers its walker tree-building functions
  * (Task 8; re-keyed by [[ProcedureRef]] family at Task 4). Before this,
  * `OathRules.buildWalker` and `WalkerDecisionProjector.rebuild` each
  * carried their own `case ActionRef.Recover =>` match, so adding a second
  * procedure meant editing both call sites -- and a missing branch there
  * was a runtime `MatchError`, not a typed rejection. Both dispatch through
  * `entries` here instead: registering a second procedure is one entry, and
  * a procedure absent from it produces `Left(OathViolation
  * .InvalidEventOrder(...))`.
  *
  * `build` and `rebuild` stay separate because a fresh start runs the
  * procedure's start gates while resume reconstructs an already-started
  * tree without them.
  */
object WalkerProcedureRegistry {

  /** `fallbackKind` (I4) is the [[ActionKind]] `OathRules.startWalker`
    * runs `PowerRuntime.ignored` fallback-diagnostics against for this
    * procedure -- previously a bare `ActionKind.Recover` literal at the
    * `startWalker` call site regardless of which action was actually
    * starting. `None` for a triggered procedure, which declares no fallback
    * kind at all (Task 4): the `fallbackKind` accessor turns that into a
    * typed rejection rather than handing a sentinel kind onward.
    *
    * `rollDecisionId` (I4) is the synthetic client-facing decision id
    * surfaced when the walker parks on this procedure's Roll node itself (a
    * `Roll` leaf carries no `decisionId` of its own -- see
    * `RecoverProcedure`'s doc). Exposed here so both `OathRules
    * .parkedContinue` and `WalkerDecisionProjector` read the same
    * per-procedure value instead of each importing `RecoverProcedure`
    * directly.
    *
    * It is `Option` (batch-1 Task 3, ruling R18) because a procedure whose
    * tree carries no `Roll` node can never park on one: Forge is two
    * `BuildOps` around a single `Decide`. The alternative -- a placeholder
    * id no tree ever parks on -- would compile and pass while turning both
    * consulting call sites into a silent match against a string with no
    * meaning. `None` is therefore a rule, and the `rollDecisionId`
    * accessor below turns it into a typed rejection rather than handing a
    * sentinel onward.
    *
    * `continuationFor` (I4) maps ANY of this procedure's decision ids --
    * `rollDecisionId` included -- to the client-facing [[OathContinue]] it
    * produces, keyed by the id string alone (never by tree path, for the
    * same reorder-safety reason `ProcedureWalker.parkedDecide` dispatches
    * on `decisionId`). `None` for an id this procedure does not recognise.
    * This is the single place `OathRulesWalker.parkedContinue` consults, so
    * it carries no `RecoverProcedure`-specific match of its own.
    *
    * `modifierWindow` (batch-1 Task 1) is the [[PowerWindow]] at which a
    * player-selected `ContributingPower` is offered as a `StartWalker`
    * modifier for this procedure -- previously a bare
    * `PowerWindow.RecoverModifierSelection` literal at both
    * `OathRules.offerableWalkerPowers` and `OathRules.validateModifiers`,
    * which would have filtered every procedure's offers through Recover's
    * window the moment a second one registered.
    *
    * It is `Option` because not every procedure has such a window:
    * `PowerWindow` carries a `*ModifierSelection` case for each of the eight
    * [[oathdigital.model.MajorActionType]]s and Take Wealth
    * is not one of them -- its only window is `WakeTakeWealth`, an
    * `OtherWindow` whose `associatedMajorAction` is `None`. `None` here means
    * the procedure offers no player-selected powers at all: `offerableWalkerPowers`
    * returns empty and `validateModifiers` rejects every id. Inventing a
    * `WakeModifierSelection` case purely to keep this field total would put a
    * window in the audited vocabulary that no rulebook clause backs.
    *
    * `build`/`rebuild` receive the player's start selections (batch-1 Task
    * 5): what they chose before the walk began, for a procedure whose tree
    * cannot be built without it. Recover and Forge derive their whole tree
    * from the actor's pawn site and select nothing, so they take an empty
    * vector and reject anything else; Travel cannot name a route without one.
    *
    * They are `DecisionOptionRef`s -- the same game-object vocabulary a
    * decision option names, already spelled for the wire once in
    * `DecisionOptionRef.kind`/`wireId` -- and NOT a per-procedure payload
    * type. That is deliberate and is the constraint this batch is held to:
    * nothing outside the declaring procedure may learn what its start
    * selection means, so the model, the journal codec and the walker all
    * handle a vector of references and none of them names a procedure.
    * Interpreting the vector -- "exactly one site, and it is the
    * destination" -- is the procedure's own job, in its `build`, where a
    * wrong shape is a typed rejection.
    *
    * The limit worth stating: a selection that is not a game-object reference
    * (a warband count, say) has no spelling here. The first procedure that
    * needs one widens this vocabulary rather than growing a case per
    * procedure, which is the shape the retired legacy pending procedures had
    * and the walker migration existed to end.
    *
    * `rebuild` receives the selections `startWalker` was given, read back from
    * the durable `CurrentGameState.walkerStartArgs`, for the same reason
    * `walkerModifiers` is carried: a resumed command must rebuild the tree the
    * start built, and a selection is not re-derivable from state.
    *
    * `requiresPlayableOption` opts a procedure in to the preview gate:
    * `StartWalker` rejects a start whose first decision has no option the
    * procedure's own answer would accept, and the parked decision is shown
    * with only such options, each annotated with what answering it records.
    * Procedures that do not opt in are untouched.
    */
  private[gameplay] final case class Entry(
      fallbackKind: Option[ActionKind],
      rollDecisionId: Option[String],
      modifierWindow: Option[PowerWindow],
      continuationFor: (String, PlayerId, DecisionId) => Option[OathContinue],
      build: (ExecutableCatalog, ReadyGame, PlayerId,
        Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
      rebuild: (ExecutableCatalog, ReadyGame, PlayerId,
        Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
      requiresPlayableOption: Boolean = false)

  /** `private[gameplay]`, not `private`: [[WalkerProcedureRegistrySuite]]
    * asserts this map's keys cover `ProcedureRef.all` (catching a registered
    * procedure missing its entry) and its type is referenced when the suite
    * calls `build`/`rebuild` with a `registrations` map that omits a real,
    * registered-in-production procedure.
    *
    * Widened from `private[walker]` at batch-1 Task 1: `OathRules`
    * (`oathdigital.gameplay`) now takes the same `registrations` parameter on
    * `offerableWalkerPowers`/`validateModifiers`, for the same reason and by
    * the same precedent, so the `Entry` type has to be nameable one package
    * up. It stays out of reach of every other package.
    */
  private[gameplay] val entries: Map[ProcedureRef, Entry] = Map(
    ActionRef.PlayFacedownAdviser -> Entry(
      fallbackKind = Some(ActionKind.Search),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.SearchModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(decisionId.startsWith("cardplay."))(
          OathContinue.AwaitingSearchDecision(actor, decision)),
      build = CardPlayProcedure.buildFacedown,
      rebuild = CardPlayProcedure.rebuildFacedown),

    ActionRef.Search -> Entry(
      fallbackKind = Some(ActionKind.Search),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.SearchModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(decisionId == SearchProcedure.cardDecisionId ||
          decisionId.startsWith("cardplay."))(
          OathContinue.AwaitingSearchDecision(actor, decision)),
      build = SearchProcedure.build,
      rebuild = SearchProcedure.rebuild),

    ActionRef.Recover -> Entry(
      fallbackKind = Some(ActionKind.Recover),
      rollDecisionId = Some(RecoverProcedure.rollDecisionId),
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
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Recover,
        args).flatMap(_ => RecoverProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Recover,
        args).flatMap(_ => RecoverProcedure.rebuild(catalog, state, activePlayer))),

    /** Batch-1 Task 3. Forge has no `Roll` node, so `rollDecisionId` is
      * `None` (R18).
      */
    ActionRef.Forge -> Entry(
      fallbackKind = Some(ActionKind.Forge),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.ForgeModifierSelection),
      continuationFor = (decisionId, actor, decision) => decisionId match {
        case ForgeProcedure.assignmentDecisionId =>
          Some(OathContinue.AwaitingForgeAssignment(actor, decision))
        case _ => None
      },
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Forge,
        args).flatMap(_ => ForgeProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Forge,
        args).flatMap(_ => ForgeProcedure.rebuild(catalog, state, activePlayer))),

    /** Batch-1 Task 5. Travel has no `Roll` and no `Decide`: its tree is a
      * pay node and a pawn move, so it runs to the end inside the command
      * that starts it and `continuationFor` is never consulted. It is the
      * first action to declare a start argument, and `build` and `rebuild`
      * are the same function because every gate Travel has is a fact about
      * the route rather than a start-only cost.
      */
    ActionRef.Travel -> Entry(
      fallbackKind = Some(ActionKind.Travel),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.TravelModifierSelection),
      continuationFor = (_, _, _) => None,
      build = TravelProcedure.build,
      rebuild = TravelProcedure.build),

    /** Economy. The first walker actions whose first node is a `Decide`
      * and that opt in to the playable-option gate: nothing runs before the
      * source decision, so the start can be previewed from the fresh tree.
      */
    ActionRef.Muster -> Entry(
      fallbackKind = Some(ActionKind.Muster),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.MusterModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        if (CampaignProcedure.isDecision(decisionId))
          Some(OathContinue.AwaitingCampaignDecision(actor, decision))
        else Option.when(decisionId.startsWith(MusterProcedure.decisionPrefix))(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
        args).flatMap(_ => MusterProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
        args).flatMap(_ => MusterProcedure.rebuild(catalog, state, activePlayer)),
      requiresPlayableOption = true),

    ActionRef.Trade -> Entry(
      fallbackKind = Some(ActionKind.Trade),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.TradeModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(decisionId == TradeProcedure.decisionId)(
          OathContinue.AwaitingEconomyDecision(actor, decision)),
      build = TradeProcedure.build,
      rebuild = TradeProcedure.rebuild,
      requiresPlayableOption = true),

    /** Challenge. Its first step spends the Supply, so a start runs an
      * operation before its first decision and cannot use the playable-option
      * preview gate; the banner decision's own option filter is the gate.
      */
    ActionRef.Challenge -> Entry(
      fallbackKind = Some(ActionKind.Challenge),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.ChallengeModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(ChallengeProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingBannerDecision(actor, decision)),
      build = ChallengeProcedure.build,
      rebuild = ChallengeProcedure.rebuild),

    /** Place Banner Resource is not a major action: it has no fallback kind
      * and, like Take Wealth, no modifier window.
      */
    ActionRef.PlaceBannerResource -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, actor, decision) =>
        Option.when(PlaceBannerResourceProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingBannerDecision(actor, decision)),
      build = PlaceBannerResourceProcedure.build,
      rebuild = PlaceBannerResourceProcedure.rebuild),

    /** Negotiation is a minor action: no modifier window (none of its powers
      * is player-selected), and an `ActionKind` of its own so a start records
      * the Negotiation rules it ignores.
      */
    ActionRef.Negotiation -> Entry(
      fallbackKind = Some(ActionKind.Negotiation),
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, actor, decision) =>
        Option.when(NegotiationProcedure.decisionIds.contains(decisionId))(
          OathContinue.AwaitingNegotiation(actor, decision)),
      build = NegotiationProcedure.build,
      rebuild = NegotiationProcedure.rebuild),

    /** Campaign. Its first step spends the Supply, so a start runs an operation
      * before its first decision and cannot use the playable-option gate. No
      * roll parks: both dice rolls are automatic, so `rollDecisionId` is
      * `None`.
      */
    ActionRef.Campaign -> Entry(
      fallbackKind = Some(ActionKind.Campaign),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.CampaignModifierSelection),
      continuationFor = (decisionId, actor, decision) =>
        Option.when(CampaignProcedure.isDecision(decisionId))(
          OathContinue.AwaitingCampaignDecision(actor, decision)),
      build = CampaignProcedure.build,
      rebuild = CampaignProcedure.rebuild),

    /** Batch-1 Task 7, and the first entry for an action outside the Act
      * phase. Nothing here says so: the phase is a gate inside
      * `TakeWealthProcedure.build` and a fact about the state the completed
      * action leaves behind, neither of which this registry carries.
      *
      * `modifierWindow` is `None` -- Take Wealth offers no player-selected
      * powers, which is the case Task 1 made the field optional for. Its tree
      * has no `Roll` and no `Decide`, so `rollDecisionId` is `None` and
      * `continuationFor` is never consulted.
      */
    ActionRef.TakeWealth -> Entry(
      fallbackKind = Some(ActionKind.Wake),
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (_, _, _) => None,
      build = TakeWealthProcedure.build,
      rebuild = TakeWealthProcedure.build),

    /** Batch-1 Task 7, and the first registration that is not an action: it
      * is the Wake phase's transition to Act -- a [[PhaseTransitionRef]]
      * since Task 4. Nothing here marks that difference, because nothing
      * here needs to -- an entry says how to build a tree, and ending Wake
      * has one. What follows from it being a phase transition rather than
      * an action is decided where the difference is visible: the action
      * boundary runs only after a completed `ActionRef`, whatever phase it
      * ran in (`OathRulesWalker.runsActionBoundary`, Task 8) -- End Wake is
      * a `PhaseTransitionRef`, so it never runs one.
      *
      * `fallbackKind` is `ActionKind.Wake`, which is the kind the
      * deleted `Wake` object's `withFallback` wrapper used, so the Wake
      * timing's ignored-rule diagnostics are recorded exactly as before.
      */
    PhaseTransitionRef.EndWake -> Entry(
      fallbackKind = Some(ActionKind.Wake),
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (_, _, _) => None,
      build = EndWakeProcedure.build,
      rebuild = EndWakeProcedure.build),

    /** Records the Rest timing's ignored-rule diagnostics, as the legacy
      * `withFallback(ActionKind.Rest)` wrapper does.
      */
    PhaseTransitionRef.BeginRest -> Entry(
      fallbackKind = Some(ActionKind.Rest),
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (_, _, _) => None,
      build = BeginRestProcedure.build,
      rebuild = BeginRestProcedure.build),

    /** Begin Rest already recorded the Rest diagnostics, so this declares no
      * fallback kind. Any off-turn decision a power parks inside it is a
      * generic Rest decision, so this entry names no concrete power.
      */
    PhaseTransitionRef.FinishRest -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (_, awaited, decision) =>
        Some(OathContinue.AwaitingRestDecision(awaited, decision)),
      build = FinishRestProcedure.build,
      rebuild = FinishRestProcedure.build),

    /** The first triggered procedure. It is started by the action boundary,
      * never by a client, so it has no fallback kind (the boundary recorded
      * its diagnostics), no modifier window and no start selection.
      */
    TriggeredProcedureRef.Oathkeeper -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      continuationFor = (decisionId, awaited, decision) => decisionId match {
        case OathkeeperProcedure.recipientDecisionId =>
          Some(OathContinue.AwaitingOathkeeperRecipient(awaited, decision))
        case _ => None
      },
      build = OathkeeperProcedure.build,
      rebuild = OathkeeperProcedure.build))

  /** Rejects start selections handed to an action that makes none.
    *
    * Silently ignoring them would let a client attach a Travel destination to
    * a Recover and have the command succeed as though it had not.
    */
  private def noStartArgs(action: ActionRef, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${action.key} takes no start selection, got " +
        args.map(_.kind).mkString(", ")))

  /** Builds `procedure`'s tree for a fresh start: its start gates run.
    *
    * `registrations` defaults to the production `entries` map, so every
    * production call site is unaffected; [[WalkerProcedureRegistrySuite]]
    * overrides it with a map that omits a procedure to drive this exact
    * entry point down the missing-registration branch, rather than testing
    * `lookup` as an extracted stand-in.
    */
  def build(procedure: ProcedureRef, catalog: ExecutableCatalog,
      state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef] = Vector.empty,
      phasePowers: PhasePowers = PhasePowers.empty,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Either[OathViolation, Operation] =
    lookup(procedure, registrations, phasePowers).flatMap(
      _.build(catalog, state, activePlayer, args))

  /** Rebuilds `procedure`'s tree to resume an already-started walker
    * position. Start-only gates do not re-run.
    *
    * `registrations` defaults to the production `entries` map -- see
    * `build`'s doc for why.
    */
  def rebuild(procedure: ProcedureRef, catalog: ExecutableCatalog,
      state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef] = Vector.empty,
      phasePowers: PhasePowers = PhasePowers.empty,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Either[OathViolation, Operation] =
    lookup(procedure, registrations, phasePowers).flatMap(
      _.rebuild(catalog, state, activePlayer, args))

  /** Every `UsePower` shares one entry shape, built for its id. A parked
    * decision inside the power is a generic power decision, so the registry
    * names no power.
    */
  private def usePowerEntry(id: PowerId, powers: PhasePowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    continuationFor = (_, awaited, decision) =>
      Some(OathContinue.AwaitingPowerDecision(awaited, decision)),
    build = PhasePowerProcedure.build(id, powers),
    rebuild = PhasePowerProcedure.rebuild(id, powers))

  private def lookup(procedure: ProcedureRef,
      registrations: Map[ProcedureRef, Entry],
      powers: PhasePowers = PhasePowers.empty): Either[OathViolation, Entry] =
    procedure match {
      case ActionRef.UsePower(id) => Right(usePowerEntry(id, powers))
      case _ => registrations.get(procedure).toRight(OathViolation
        .InvalidEventOrder(s"no walker procedure registered for ${procedure.key}"))
    }

  /** `procedure`'s [[ActionKind]] for the `PowerRuntime.ignored`
    * fallback diagnostics `OathRules.startWalker` records alongside the
    * command (I4) -- queried here instead of a bare `ActionKind
    * .Recover` literal at the `startWalker` call site, so a second
    * registered procedure supplies its own kind without editing
    * `OathRules`.
    *
    * Typed `StartableRef`, not `ProcedureRef` (Task 4): only a startable
    * procedure ever reaches `startWalker`'s fallback diagnostics. `None`
    * means the start records no fallback diagnostics.
    */
  def fallbackKind(procedure: StartableRef)
      : Either[OathViolation, Option[ActionKind]] =
    lookup(procedure, entries).map(_.fallbackKind)

  /** `procedure`'s synthetic Roll-park decision id (I4) -- see `Entry`'s doc.
    * Both `OathRules.parkedContinue` and `WalkerDecisionProjector` read
    * this instead of `RecoverProcedure.rollDecisionId` directly.
    *
    * An entry declaring no roll decision id (R18: a procedure whose tree has
    * no `Roll` node, such as Forge) is a typed `Left` here, flattened at
    * the accessor rather than handed onward as a `None` each call site
    * would have to interpret for itself -- and never a sentinel string.
    * Both call sites reach the accessor while asking "which id did this
    * procedure's Roll park just produce", a question a procedure with no
    * Roll node has no answer to; both therefore reject rather than proceed.
    *
    * `registrations` defaults to the production map -- see `build`'s doc.
    */
  def rollDecisionId(procedure: ProcedureRef,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Either[OathViolation, String] =
    lookup(procedure, registrations).flatMap(_.rollDecisionId.toRight(
      OathViolation.InvalidEventOrder(
        s"walker procedure ${procedure.key} declares no roll decision id")))

  /** `procedure`'s modifier-selection [[PowerWindow]], or `None` when it
    * offers no player-selected powers at all -- see `Entry`'s doc.
    * `OathRules.offerableWalkerPowers` and `OathRules.validateModifiers` read
    * this instead of naming `PowerWindow.RecoverModifierSelection`.
    *
    * `registrations` defaults to the production `entries` map -- see `build`'s
    * doc for why it is a parameter at all.
    */
  def modifierWindow(procedure: ProcedureRef,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Either[OathViolation, Option[PowerWindow]] =
    lookup(procedure, registrations).map(_.modifierWindow)

  /** `procedure`'s client-facing continuation for `decisionId` (I4) -- see
    * `Entry`'s doc. `OathRulesWalker.parkedContinue` is the sole caller: it
    * reports `None` onward as its own `InvalidEventOrder`, since only it
    * knows the parked-position context worth naming in that message.
    */
  def continuationFor(procedure: ProcedureRef, decisionId: String,
      actor: PlayerId, decision: DecisionId)
      : Either[OathViolation, Option[OathContinue]] =
    lookup(procedure, entries).map(_.continuationFor(decisionId, actor,
      decision))

  /** Whether `procedure` runs on the generic walker at all (Task 9a). The
    * pre-start modifier preview asks this to decide whether to offer
    * `ContributingPower`s from the walker catalog or fall back to the legacy
    * `PowerRuntime` machinery -- this registry stays the single place that
    * knows which procedures are walker-driven, so that decision needs no
    * second `ProcedureRef` match at the preview call site.
    */
  def isRegistered(procedure: ProcedureRef): Boolean =
    procedure.isInstanceOf[ActionRef.UsePower] || entries.contains(procedure)

  /** Whether `procedure` opts in to the playable-option gate (see `Entry`). */
  def requiresPlayableOption(procedure: ProcedureRef): Boolean =
    lookup(procedure, entries).exists(_.requiresPlayableOption)
}
