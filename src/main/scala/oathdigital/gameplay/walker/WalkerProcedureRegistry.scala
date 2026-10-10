package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure, PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.actions.minor.MinorActionProcedures
import oathdigital.gameplay.actions.negotiation.NegotiationProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.{SearchOptions, SearchProcedure}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.travel.{TravelOptions, TravelProcedure}
import oathdigital.gameplay.phases.wake.{EndWakeProcedure, ForcedWakeProcedure,
  TakeWealthProcedure}
import oathdigital.gameplay.phases.rest.{BeginRestProcedure, FinishRestProcedure}
import oathdigital.gameplay.phases.{PhaseMenu, PhasePowerProcedure}
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model.{ActionRef, Decide, DecisionOption, DecisionOptionRef, ActionKind, OathViolation, Operation, PendingTree, PhaseTransitionRef, PlayerId, PowerId, PowerWindow, ProcedureRef, ReadyGame, StartableRef, TriggeredProcedureRef}

/** The one place a procedure registers its walker tree-building functions
  * (Task 8; re-keyed by [[oathdigital.model.ProcedureRef]] family at Task 4). Before this,
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
object WalkerProcedureRegistry:

  /** The options a parked decision shows, from its position: see
    * `Entry.optionPricing`. The `Decide` is the walk's own, with the options
    * its look-ahead hid already left out. */
  type OptionPricing = (ReadyGame, Operation, PendingTree, WalkerPowers,
    Decide) => Vector[DecisionOption]

  /** `fallbackKind` (I4) is the [[oathdigital.model.ActionKind]] `OathRules.startWalker`
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
    * `RecoverProcedure`'s doc). Exposed here so both `OathRulesWalker
    * .checkAnswerable` and `WalkerDecisionProjector` read the same
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
    * `modifierWindow` is the [[oathdigital.model.PowerWindow]] at which the
    * procedure's modifier decision (`ModifierChoice`) offers player-selected
    * `ContributingPower`s, and at which `ModifierOffers.offerable` looks.
    *
    * It is `Option` because not every procedure has such a window:
    * `PowerWindow` carries a `*ModifierSelection` case for each of the eight
    * [[oathdigital.model.MajorActionType]]s and Take Wealth
    * is not one of them -- its only window is `WakeTakeWealth`, an
    * `OtherWindow` whose `associatedMajorAction` is `None`. `None` here means
    * the procedure offers no player-selected powers at all: `offerableWalkerPowers`
    * returns empty. Inventing a
    * `WakeModifierSelection` case purely to keep this field total would put a
    * window in the audited vocabulary that no rulebook clause backs.
    *
    * `build`/`rebuild` receive the player's start selections (batch-1 Task
    * 5): what they chose before the walk began, for a procedure whose tree
    * cannot be built without it. Recover and Forge derive their whole tree
    * from the actor's pawn site and select nothing, so they take an empty
    * vector and reject anything else. No registered procedure takes a start
    * selection now: Search and Travel ask theirs inside the walk.
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
    * the durable `CurrentGameState.walkerStartArgs`: a resumed command must
    * rebuild the tree the start built, and a selection is not re-derivable
    * from state.
    *
    * `requiresPlayableOption` opts a procedure in to the preview gate:
    * `OathRules.startWalker` rejects a start whose first decision has no option the
    * procedure's own answer would accept, and the parked decision is shown
    * with only such options, each annotated with what answering it records.
    * Procedures that do not opt in are untouched.
    */
  private[gameplay] final case class Entry(
      fallbackKind: Option[ActionKind],
      rollDecisionId: Option[String],
      modifierWindow: Option[PowerWindow],
      build: (ExecutableCatalog, ReadyGame, PlayerId,
        Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
      rebuild: (ExecutableCatalog, ReadyGame, PlayerId,
        Vector[DecisionOptionRef]) => Either[OathViolation, Operation],
      requiresPlayableOption: Boolean = false,
      /** What the engine derives at a fresh start and stores as
        * `walkerStartFacts` (travel destination design, ruling 6). A
        * procedure's `rebuild` reads them from the state it is handed. None
        * by default. */
      startFacts: (ExecutableCatalog, ReadyGame, PlayerId) => Vector[DecisionOptionRef] =
        (_, _, _) => Vector.empty,
      /** The roll to show beside a parked decision of this procedure, by that
        * decision's id. `None` for a decision no roll belongs beside, which is
        * the default and every procedure that rolls nothing.
        */
      rollFeedback: (ExecutableCatalog, ReadyGame, PlayerId, String) => Option[WalkerRollFeedback] =
        (_, _, _, _) => None,
      /** Replaces the playable-option filter in the projector: an entry that
        * prices its options shows every option, each priced or explained.
        * Travel's destinations. */
      optionPricing: Option[OptionPricing] = None)

  /** `private[gameplay]`, not `private`: `WalkerProcedureRegistrySuite`
    * asserts this map's keys cover `ProcedureRef.all` (catching a registered
    * procedure missing its entry) and its type is referenced when the suite
    * calls `build`/`rebuild` with a `registrations` map that omits a real,
    * registered-in-production procedure.
    *
    * Widened from `private[walker]` at batch-1 Task 1: `OathRules`
    * (`oathdigital.gameplay`) now takes the same `registrations` parameter on
    * `offerableWalkerPowers`, for the same reason and by
    * the same precedent, so the `Entry` type has to be nameable one package
    * up. It stays out of reach of every other package.
    */
  private[gameplay] val entries: Map[ProcedureRef, Entry] = Map(
    ActionRef.PlayFacedownAdviser -> Entry(
      fallbackKind = Some(ActionKind.Search),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.SearchModifierSelection),
      build = CardPlayProcedure.buildFacedown,
      rebuild = CardPlayProcedure.rebuildFacedown),

    /** Search asks for its modifiers, then parks on its source (search
      * source design; modifier walker design). Nothing runs before those
      * decisions, so the start is previewed through the playable-option
      * gate. Its source options are priced: each shows what it costs, or why
      * it cannot be searched. */
    ActionRef.Search -> Entry(
      fallbackKind = Some(ActionKind.Search),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.SearchModifierSelection),
      build = SearchProcedure.build,
      rebuild = SearchProcedure.rebuild,
      requiresPlayableOption = true,
      optionPricing = Some(SearchOptions.priced)),

    ActionRef.Recover -> Entry(
      fallbackKind = Some(ActionKind.Recover),
      rollDecisionId = Some(RecoverProcedure.rollDecisionId),
      modifierWindow = Some(PowerWindow.RecoverModifierSelection),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Recover,
        args).flatMap(_ => RecoverProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Recover,
        args).flatMap(_ => RecoverProcedure.rebuild(catalog, state, activePlayer)),
      rollFeedback = RecoverProcedure.rollFeedback),

    /** Batch-1 Task 3. Forge has no `Roll` node, so `rollDecisionId` is
      * `None` (R18).
      */
    ActionRef.Forge -> Entry(
      fallbackKind = Some(ActionKind.Forge),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.ForgeModifierSelection),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Forge,
        args).flatMap(_ => ForgeProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Forge,
        args).flatMap(_ => ForgeProcedure.rebuild(catalog, state, activePlayer))),

    /** Travel asks for its modifiers, then parks on its destination (travel
      * destination design; modifier walker design). Nothing runs before
      * those decisions, so the start is previewed through the
      * playable-option gate. Its source is a start fact: a resume after the
      * pawn moved reads it back rather than from the pawn.
      */
    ActionRef.Travel -> Entry(
      fallbackKind = Some(ActionKind.Travel),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.TravelModifierSelection),
      build = TravelProcedure.build,
      rebuild = TravelProcedure.rebuild,
      requiresPlayableOption = true,
      startFacts = TravelProcedure.startFacts,
      optionPricing = Some(TravelOptions.priced)),

    /** Economy. Muster and Trade ask for their modifiers, then park on their
      * source (modifier walker design). Nothing runs before those decisions,
      * so the start is previewed through the playable-option gate. Their
      * source decision shows only the sources a start could complete; it is
      * not priced.
      */
    ActionRef.Muster -> Entry(
      fallbackKind = Some(ActionKind.Muster),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.MusterModifierSelection),
      build = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
        args).flatMap(_ => MusterProcedure.build(catalog, state, activePlayer)),
      rebuild = (catalog, state, activePlayer, args) => noStartArgs(ActionRef.Muster,
        args).flatMap(_ => MusterProcedure.rebuild(catalog, state, activePlayer)),
      requiresPlayableOption = true),

    ActionRef.Trade -> Entry(
      fallbackKind = Some(ActionKind.Trade),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.TradeModifierSelection),
      build = TradeProcedure.build,
      rebuild = TradeProcedure.rebuild,
      requiresPlayableOption = true),

    /** Challenge. Its first step after the modifier decision spends the
      * Supply, so a start runs an operation before its first decision and cannot use the playable-option
      * preview gate; the banner decision's own option filter is the gate.
      */
    ActionRef.Challenge -> Entry(
      fallbackKind = Some(ActionKind.Challenge),
      rollDecisionId = None,
      modifierWindow = Some(PowerWindow.ChallengeModifierSelection),
      build = ChallengeProcedure.build,
      rebuild = ChallengeProcedure.rebuild),

    /** Place Banner Resource is not a major action: it has no fallback kind
      * and, like Take Wealth, no modifier window.
      */
    ActionRef.PlaceBannerResource -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
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
      build = CampaignProcedure.build,
      rebuild = CampaignProcedure.rebuild,
      rollFeedback = CampaignProcedure.rollFeedback),

    /** The legacy minor actions (walker-driven game loop design, slice 3).
      * They are actions, so the boundary follows each, but like the legacy
      * handler they take no modifiers and record no fallback diagnostics.
      */
    ActionRef.PeekSiteRelics -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = MinorActionProcedures.buildPeek,
      rebuild = MinorActionProcedures.rebuildPeek),
    ActionRef.RevealOwnedRelic -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = MinorActionProcedures.buildReveal,
      rebuild = MinorActionProcedures.rebuildReveal),
    ActionRef.MoveWarbands -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = MinorActionProcedures.buildMove,
      rebuild = MinorActionProcedures.rebuildMove),

    /** Batch-1 Task 7, and the first entry for an action outside the Act
      * phase. Nothing here says so: the phase is a gate inside
      * `TakeWealthProcedure.build` and a fact about the state the completed
      * action leaves behind, neither of which this registry carries.
      *
      * `modifierWindow` is `None` -- Take Wealth offers no player-selected
      * powers, which is the case Task 1 made the field optional for. Its tree
      * has no `Roll` and no `Decide`, so `rollDecisionId` is `None` and it
      * never parks at all.
      */
    ActionRef.TakeWealth -> Entry(
      fallbackKind = Some(ActionKind.Wake),
      rollDecisionId = None,
      modifierWindow = None,
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
      build = EndWakeProcedure.build,
      rebuild = EndWakeProcedure.build),

    /** Records the Rest timing's ignored-rule diagnostics, as the legacy
      * `withFallback(ActionKind.Rest)` wrapper does.
      */
    PhaseTransitionRef.BeginRest -> Entry(
      fallbackKind = Some(ActionKind.Rest),
      rollDecisionId = None,
      modifierWindow = None,
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
      build = OathkeeperProcedure.build,
      rebuild = OathkeeperProcedure.build),
    TriggeredProcedureRef.Setup -> Entry(
      fallbackKind = None,
      rollDecisionId = None,
      modifierWindow = None,
      build = oathdigital.gameplay.setup.SetupProcedure.build,
      rebuild = oathdigital.gameplay.setup.SetupProcedure.build))

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
    * production call site is unaffected; `WalkerProcedureRegistrySuite`
    * overrides it with a map that omits a procedure to drive this exact
    * entry point down the missing-registration branch, rather than testing
    * `lookup` as an extracted stand-in.
    */
  def build(procedure: ProcedureRef, catalog: ExecutableCatalog,
      state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef] = Vector.empty,
      phasePowers: PhasePowers = PhasePowers.empty,
      registrations: Map[ProcedureRef, Entry] = entries,
      walkerPowers: WalkerPowers = WalkerPowers.empty)
      : Either[OathViolation, Operation] =
    lookup(procedure, registrations, phasePowers, walkerPowers).flatMap(
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
      registrations: Map[ProcedureRef, Entry] = entries,
      walkerPowers: WalkerPowers = WalkerPowers.empty)
      : Either[OathViolation, Operation] =
    lookup(procedure, registrations, phasePowers, walkerPowers).flatMap(
      _.rebuild(catalog, state, activePlayer, args))

  /** Every `UsePower` shares one entry shape, built for its id. A parked
    * decision inside the power is a generic power decision, so the registry
    * names no power.
    */
  private def usePowerEntry(id: PowerId, powers: PhasePowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = PhasePowerProcedure.build(id, powers),
    rebuild = PhasePowerProcedure.rebuild(id, powers))

  /** The forced Wake step reads the phase powers, as `UsePower` does. It is
    * started by the turn boundary, so it has no fallback kind, no modifier
    * window and no start selection. */
  private def forcedWakeEntry(powers: PhasePowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = ForcedWakeProcedure.build(powers),
    rebuild = ForcedWakeProcedure.build(powers))

  /** The follow-up offer prices its action by a dry run of the action's
    * start, which needs the walk's powers. It is started by the driver, so
    * it has no fallback kind, no modifier window and no start selection. */
  private def followUpOfferEntry(phasePowers: PhasePowers,
      powers: WalkerPowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = FollowUpOffer.build(phasePowers, powers),
    rebuild = FollowUpOffer.build(phasePowers, powers))

  /** A phase menu lists the phase powers and checks each option's start,
    * which needs the walk's powers. The driver starts it, so it has no
    * fallback kind, no modifier window and no start selection. */
  private def menuEntry(menu: PhaseMenu.Menu, phasePowers: PhasePowers,
      powers: WalkerPowers): Entry = Entry(
    fallbackKind = None,
    rollDecisionId = None,
    modifierWindow = None,
    build = PhaseMenu.build(menu, phasePowers, powers),
    rebuild = PhaseMenu.build(menu, phasePowers, powers))

  private def lookup(procedure: ProcedureRef,
      registrations: Map[ProcedureRef, Entry],
      powers: PhasePowers = PhasePowers.empty,
      walkerPowers: WalkerPowers = WalkerPowers.empty)
      : Either[OathViolation, Entry] =
    procedure match
      case ActionRef.UsePower(id) => Right(usePowerEntry(id, powers))
      case TriggeredProcedureRef.ForcedWake => Right(forcedWakeEntry(powers))
      case TriggeredProcedureRef.FollowUpOffer =>
        Right(followUpOfferEntry(powers, walkerPowers))
      case TriggeredProcedureRef.WakeMenu =>
        Right(menuEntry(PhaseMenu.Menu.Wake, powers, walkerPowers))
      case TriggeredProcedureRef.RestMenu =>
        Right(menuEntry(PhaseMenu.Menu.Rest, powers, walkerPowers))
      case TriggeredProcedureRef.ActMenu =>
        Right(menuEntry(PhaseMenu.Menu.Act, powers, walkerPowers))
      case _ => registrations.get(procedure).toRight(OathViolation
        .InvalidEventOrder(s"no walker procedure registered for ${procedure.key}"))

  /** `procedure`'s [[oathdigital.model.ActionKind]] for the `PowerRuntime.ignored`
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
    * Both `OathRulesWalker.checkAnswerable` and `WalkerDecisionProjector` read
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

  /** The roll `procedure` declares for its parked `decisionId`, or, for a
    * reroll decision any procedure asks, its pool; `None` when it declares
    * none or is not registered -- see `Entry`'s doc.
    * `WalkerDecisionProjector` reads this instead of naming a procedure.
    */
  def rollFeedback(procedure: ProcedureRef, catalog: ExecutableCatalog,
      ready: ReadyGame, actor: PlayerId, decisionId: String,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Option[WalkerRollFeedback] =
    registrations.get(procedure).flatMap(_.rollFeedback(catalog, ready, actor,
      decisionId)).orElse(RerollChoice.poolOf(decisionId)
        .map(WalkerRollFeedback(_)))

  /** `procedure`'s modifier-selection [[oathdigital.model.PowerWindow]], or `None` when it
    * offers no player-selected powers at all -- see `Entry`'s doc.
    * `ModifierOffers.offerable` reads this.
    *
    * `registrations` defaults to the production `entries` map -- see `build`'s
    * doc for why it is a parameter at all.
    */
  def modifierWindow(procedure: ProcedureRef,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Either[OathViolation, Option[PowerWindow]] =
    lookup(procedure, registrations).map(_.modifierWindow)

  /** Whether `procedure` runs on the generic walker at all. */
  def isRegistered(procedure: ProcedureRef): Boolean =
    procedure.isInstanceOf[ActionRef.UsePower] ||
      procedure == TriggeredProcedureRef.ForcedWake ||
      procedure == TriggeredProcedureRef.FollowUpOffer ||
      PhaseMenu.asking(procedure).nonEmpty ||
      entries.contains(procedure)

  /** Whether `procedure` opts in to the playable-option gate (see `Entry`). */
  def requiresPlayableOption(procedure: ProcedureRef): Boolean =
    lookup(procedure, entries).exists(_.requiresPlayableOption)

  /** How `procedure` prices the options of a parked decision, if it does. */
  def optionPricing(procedure: ProcedureRef): Option[OptionPricing] =
    lookup(procedure, entries).toOption.flatMap(_.optionPricing)

  /** The start facts `procedure` derives at a fresh start, empty for one that
    * declares none. */
  def startFacts(procedure: ProcedureRef, catalog: ExecutableCatalog,
      state: ReadyGame, actor: PlayerId): Vector[DecisionOptionRef] =
    lookup(procedure, entries).toOption.fold(Vector.empty)(
      _.startFacts(catalog, state, actor))
