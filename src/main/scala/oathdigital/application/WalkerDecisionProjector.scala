package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{BannerRules, RecoverRules}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers,
  WalkerProcedureRegistry, WalkerSimulation}
import oathdigital.model._
import oathdigital.protocol.projection.{CardDetailsProjection,
  DecisionOptionProjection, DecisionQueryProjection,
  DecisionSectionProjection, DecisionSlotProjection, WalkerDecisionProjection,
  WalkerRollOutcomeProjection, WalkerWaitingProjection}

/** Projects a parked generic-walker position (`CurrentGameState.walkerPending`
  * + `walkerProcedure`, Task 6) into the small owner-private
  * [[WalkerDecisionProjection]]. The tree rebuild below dispatches through
  * [[oathdigital.gameplay.walker.WalkerProcedureRegistry]] (Task 8) -- the
  * same keyed lookup `OathRules.buildWalker` resumes through -- rather than
  * matching on [[ProcedureRef]] itself, so this projector needs no edit when
  * a second procedure registers.
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
    walkerPowerCatalog: WalkerPowers,
    rebuildTree: WalkerDecisionProjector.TreeSource =
      WalkerDecisionProjector.declaredTree,
    phasePowers: PhasePowers = PhasePowers.empty) {

  def this(catalog: ExecutableCatalog, presentation: GamePresentationProjector) =
    this(catalog, presentation, WalkerPowerCatalog.default(catalog),
      WalkerDecisionProjector.declaredTree, PhasePowerCatalog.default(catalog))

  private def parkedPosition(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjector.Parked] = {
    import WalkerDecisionProjector.Parked
    for {
      pending <- context.current.walkerPending
      procedure <- context.current.walkerProcedure
      tree <- rebuild(context.ready, procedure,
        context.current.turn.activePlayer,
        context.current.walkerStartArgs).toOption
      powers = WalkerPowers.selected(walkerPowerCatalog,
        context.current.walkerModifiers)
      awaited <- ProcedureWalker.awaitedPlayer(context.ready, tree, pending,
        powers)
    } yield Parked(procedure, tree, pending, powers, awaited)
  }

  /** The full owner-private projection, for the awaited player only. */
  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] = for {
    parked <- parkedPosition(context)
    if context.viewer.contains(parked.awaited)
    projection <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited)
  } yield projection

  /** The public "who is this waiting on" projection, for every viewer
    * except the awaited player -- Task 5's counterpart to `project`. Built
    * off the same [[WalkerDecisionProjector.Parked]] position, so a viewer
    * who is not the awaited player always sees a projection naming exactly
    * who is.
    */
  def waiting(context: ScopedProjectionContext)
      : Option[WalkerWaitingProjection] = for {
    parked <- parkedPosition(context)
    if !context.viewer.contains(parked.awaited)
    _ <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited, previewed = false)
  } yield WalkerWaitingProjection(parked.awaited.value,
    ProcedureWalker.parkedDecide(context.ready, parked.tree, parked.pending,
      parked.powers).flatMap(_.query.heading))

  private def rebuild(ready: ReadyGame, procedure: ProcedureRef,
      activePlayer: PlayerId, args: Vector[DecisionOptionRef]) =
    tree(procedure, ready, activePlayer, args)

  private def tree(procedure: ProcedureRef, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]) = procedure match {
    case _: ActionRef.UsePower => WalkerProcedureRegistry.rebuild(procedure,
      catalog, ready, actor, args, phasePowers)
    case _ => rebuildTree(catalog, procedure, ready, actor, args)
  }

  private def parked(procedure: ProcedureRef, tree: Operation,
      ready: ReadyGame, pending: PendingTree, powers: WalkerPowers,
      awaited: PlayerId, previewed: Boolean = true)
      : Option[WalkerDecisionProjection] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match {
      // R18: a procedure whose entry declares no roll decision id has no
      // answer to "which id is this Roll park", so the accessor's typed
      // rejection is carried through as "there is nothing to project" --
      // never as a projection naming a sentinel the client would then
      // send back as a `ResolveWalker` decision id.
      case Some((pool, count)) =>
        WalkerProcedureRegistry.rollDecisionId(procedure).toOption.map(
          rollId => WalkerDecisionProjection(procedure.key, rollId, "roll",
            pool = Some(pool.value), count = Some(count),
            rollOutcome = rollOutcome(ready, awaited)))
      // Task 4: no `decisionId` comparison and no candidate discovery.
      // Whatever the parked `Decide` declares -- after every power
      // transform, since `parkedDecide` resolves the node through the same
      // fold the walk applied -- is described verbatim. A procedure that
      // changes what it offers changes this projection in the same edit,
      // and an engine that grew a per-procedure branch here would be
      // exactly the drift this replaced.
      //
      // `flatMap`, not `map`: an unpresentable option omits the whole
      // projection (see [[queryProjection]]).
      case None => ProcedureWalker.parkedDecide(ready, tree, pending, powers)
        .flatMap { decide =>
          val (query, details) =
            if (previewed) playable(procedure, ready, tree, pending, powers,
              decide)
            else (decide.query, Map.empty[DecisionOptionRef, Vector[String]])
          queryProjection(ready, Some(awaited), query, details).map(projected =>
            WalkerDecisionProjection(procedure.key, decide.decisionId, "decide",
              query = Some(projected),
              rollOutcome = rollOutcome(ready, awaited)))
        }
    }

  /** The decision's query as this viewer is offered it. For a procedure that
    * opts in (`Entry.requiresPlayableOption`), only the options its own
    * preview accepts, each with the consequences that answering it records;
    * for every other procedure the declared query, untouched. A preview that
    * cannot run leaves the declared query, so a projection is never lost to
    * it.
    */
  private def playable(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, pending: PendingTree, powers: WalkerPowers,
      decide: Decide): (DecisionQuery, Map[DecisionOptionRef, Vector[String]]) =
    if (!WalkerProcedureRegistry.requiresPlayableOption(procedure))
      (decide.query, Map.empty)
    else WalkerSimulation.previewParked(ready, tree, pending, powers) match {
      case Left(_) => (decide.query, Map.empty)
      case Right(previewed) =>
        val accepted = previewed.flatMap(option => option.outcome.toOption
          .map(outcome => option.option -> outcome))
        val query = decide.query match {
          case one: DecisionQuery.ChooseOne =>
            one.copy(options = accepted.map(_._1))
          case other => other
        }
        (query, accepted.map { case (option, outcome) =>
          option.ref -> OperationDetails.of(outcome.operations) }.toMap)
    }

  /** Describes a declared query, or `None` when any single option's identity
    * cannot be presented.
    *
    * The all-or-nothing rule is the spec's: a half-described option is a
    * blank control the client would render and then submit, which is worse
    * than no prompt at all. Suppressing the whole decision instead makes a
    * tree that declares a target absent from authoritative state a visible
    * failure rather than a silently broken button.
    *
    * The projector never filters an option it merely dislikes. Staleness is
    * already handled structurally -- the tree carrying this query was
    * rebuilt against `ready` on this very command, so an option that no
    * longer exists is absent from the query and never reaches here. What
    * remains is the genuine authoring bug, and that suppresses.
    *
    * One exception is by declaration: a procedure whose registry entry sets
    * `requiresPlayableOption` has its query narrowed by `playable` to the
    * options its own preview accepts.
    */
  private def queryProjection(ready: ReadyGame, viewer: Option[PlayerId],
      query: DecisionQuery,
      details: Map[DecisionOptionRef, Vector[String]])
      : Option[DecisionQueryProjection] = {
    // One index per projection, shared by every option: a Forge partition
    // asks about three denizens and a Recover pick about every site relic.
    val index = CardIndex.from(ready.game).toOption
    def described(options: Vector[DecisionOption])
        : Option[Vector[DecisionOptionProjection]] = {
      val projected = options.flatMap(option => optionProjection(ready, viewer,
        index, option, details.getOrElse(option.ref, Vector.empty)))
      Option.when(projected.size == options.size)(projected)
    }
    // The panel copy rides through untouched, exactly as the options do:
    // it is the action's own declaration, and the projector's whole job
    // here is to describe the transformed query rather than to author
    // anything. A query that declares none projects none, and the client
    // supplies its own generic fallback.
    query match {
      case DecisionQuery.ChooseOne(options, heading) =>
        described(options).map(DecisionQueryProjection("choose-one", _,
          heading = heading))
      case DecisionQuery.Partition(sections, options, heading, confirmLabel) =>
        described(options).map(DecisionQueryProjection("partition", _,
          sections.map(section => DecisionSectionProjection(section.key,
            section.label, section.minRequired, section.maxAllowed)),
          heading = heading, confirmLabel = confirmLabel))
      case DecisionQuery.Distribute(slots, total, heading, confirmLabel) =>
        described(slots.flatMap(slot => DecisionOption.forRef(slot.ref)))
          .filter(_.size == slots.size).map(options =>
            DecisionQueryProjection("distribute", Vector.empty,
              heading = heading, confirmLabel = Some(confirmLabel),
              slots = slots.zip(options).map { case (slot, option) =>
                DecisionSlotProjection(option, slot.minimum, slot.maximum,
                  slot.suggested) },
              total = Some(total)))
    }
  }

  /** One option, as its stable reference plus display detail.
    *
    * A button carries the query's own declarative label and nothing else --
    * it has no game object whose name could be resolved, which is the only
    * reason the model holds any prompt copy at all. Every other variant is
    * presented from authoritative state: a card through
    * [[GamePresentationProjector.cardDetails]], inheriting the disclosure
    * rules every other card projection already follows, and a player or
    * site through the same label accessors the rest of this layer uses.
    * That is what keeps game-object naming out of gameplay: an option
    * declares a reference, and the name is resolved here.
    *
    * `None` means "absent from authoritative state", which the caller turns
    * into a suppressed decision. A `Deck` is a closed four-case enum, a
    * favor bank is a closed six-case enum, and a button is its own identity,
    * so none can be absent.
    */
  private[application] def optionProjection(ready: ReadyGame, viewer: Option[PlayerId],
      index: Option[CardIndex], option: DecisionOption,
      details: Vector[String] = Vector.empty): Option[DecisionOptionProjection] = {
    val ref = option.ref
    def row(label: String, card: Option[CardDetailsProjection] = None) =
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card, details))
    option match {
      case DecisionOption.Button(_, label) => row(label)
      case DecisionOption.Player(player) =>
        if (ready.game.current.players.exists(_.player == player.id))
          row(presentation.safeLabel(player.id.value)) else None
      case DecisionOption.Site(site) =>
        if (ready.game.current.map.sites.contains(site.id))
          row(presentation.siteLabel(site.id)) else None
      case DecisionOption.Denizen(denizen) =>
        card(ready, viewer, index, denizen.id)
          .flatMap(details => row(details.name, Some(details)))
      case DecisionOption.Relic(relic) =>
        card(ready, viewer, index, relic.id)
          .flatMap(details => row(details.name, Some(details)))
      case DecisionOption.Vision(vision) =>
        card(ready, viewer, index, vision.id)
          .flatMap(details => row(details.name, Some(details)))
      case DecisionOption.Edifice(edifice) =>
        index.flatMap(_.stateOf(edifice.id)).collect {
          case state: EdificeState => state
        }.flatMap(state => row(presentation.edificeLabel(state.id, state.side),
          Some(presentation.edificeCardDetails(state))))
      case DecisionOption.RelicSlot(slot) =>
        if (ready.game.current.players.find(_.player == slot.owner)
            .exists(_.relics.isDefinedAt(slot.slot)))
          row(s"${presentation.safeLabel(slot.owner.value)} facedown relic")
        else None
      case DecisionOption.Banner(held) =>
        BannerRules.holder(ready.game.current, held.banner).flatMap(holder =>
          row(s"${presentation.safeLabel(holder.value)} " +
            presentation.safeLabel(held.banner.key)))
      case DecisionOption.Deck(deck) =>
        row(presentation.safeLabel(deck.id.key))
      case DecisionOption.FavorBank(bank) =>
        row(presentation.safeLabel(bank.suit.key))
    }
  }

  /** A card option's presentation, or `None` when the card is nowhere in
    * authoritative state OR is there but this viewer may not be told which
    * card it is. The orientation comes from the card's own located state
    * rather than being assumed by the caller, so a facedown site relic and a
    * faceup denizen each present as what they are.
    *
    * Both rejections are the same failure to the caller -- the decision is
    * suppressed -- and that is the point. An option cannot be redacted the
    * way a board slot can: its reference is the card's identity, the client
    * answers by sending that reference back, and `DecisionOptionProjection`
    * carries the real id even when its card details are withheld. So
    * projecting a `hiddenCard` beside a live reference would hide the name
    * and disclose the identity in the same breath. Offering nothing is the
    * only honest answer, and it makes the spec's "builders are responsible
    * for constructing targets that exist in their authoritative state"
    * enforceable rather than advisory.
    *
    * The entitlement question itself is not answered here. It is
    * [[GamePresentationProjector.identifiesCard]], the same rule
    * `playerBoards` redacts by, so the walker path inherits the layer's
    * disclosure handling instead of asserting `hidden = false` over it.
    * `viewer` is the decision's own owner -- `project` has already gated on
    * that -- which is why a permissive answer here is still owner-private.
    */
  private def card(ready: ReadyGame, viewer: Option[PlayerId],
      index: Option[CardIndex], id: CardId): Option[CardDetailsProjection] =
    index.flatMap(_.get(id)).filter(located => presentation.identifiesCard(
      ready, viewer, id, orientationOf(located.state),
      located.location.container)).map(located => presentation.cardDetails(id,
        orientationOf(located.state), hidden = false))

  private def orientationOf(state: Option[CardState]): Option[Orientation] =
    state match {
      case Some(DenizenState(_, orientation, _)) => Some(orientation)
      case Some(VisionState(_, orientation)) => Some(orientation)
      case Some(RelicState(_, orientation, _)) => Some(orientation)
      case _ => None
    }

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
    * This reads `RecoverProcedure`/`RecoverRules` directly, and since Task 4
    * replaced the projector's candidate discovery with `query` it is now the
    * ONLY action-specific expression left in this file. That is deliberate
    * rather than leftover: roll feedback is Recover's own pool, site and
    * difficulty story, and a second rolling action would need its own. It is
    * also why it is worth keeping separate from the decision projection
    * above, which must stay generic -- a `decisionId` or `ProcedureRef`
    * comparison deciding what to OFFER belongs nowhere in this layer.
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

private[application] object WalkerDecisionProjector {
  /** How this projector obtains the tree it resolves a parked position
    * against -- the projection-side twin of `OathRules.WalkerTreeSource`,
    * and injectable for the same reason that one is.
    *
    * [[declaredTree]] is the production value and every production caller
    * takes it by default. A suite substitutes it to reach a branch no
    * production tree can: R18's `rollDecisionId` rejection in `parked`
    * runs only on a `Roll` park, and the only registered action declaring
    * no roll decision id (Forge) also declares a tree with no `Roll` node.
    *
    * Note what this seam deliberately does NOT reach. Only the tree comes
    * from here; `parked` still reads the roll decision id from
    * `WalkerProcedureRegistry`'s production entries, so substituting a tree
    * cannot also substitute the answer under test.
    */
  type TreeSource =
    (ExecutableCatalog, ProcedureRef, ReadyGame, PlayerId,
      Vector[DecisionOptionRef]) => Either[OathViolation, Operation]

  val declaredTree: TreeSource = (catalog, procedure, ready, actor, args) =>
    WalkerProcedureRegistry.rebuild(procedure, catalog, ready, actor, args)

  /** A parked position, fully resolved: the rebuilt+power-folded tree, and
    * `awaited` -- a parked `Decide`'s owner, or the active player for a
    * `Roll` -- read off it by [[ProcedureWalker.awaitedPlayer]] (Task 5).
    * `project` and `waiting` share this so the two projections never
    * recompute the owner differently.
    *
    * Lives here, on the companion object, rather than nested in the class:
    * a case class nested in a class or trait carries a path-dependent outer
    * type, and scalac's auto-generated `equals`/`canEqual` for it trips
    * "the outer reference in this type test cannot be checked at run time"
    * (a case class once nested in the `OathRulesWalker` trait hit exactly
    * this before Task 8 deleted it). A case class on a singleton object
    * carries no such outer instance, so it triggers no warning.
    */
  private final case class Parked(procedure: ProcedureRef, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, awaited: PlayerId)
}
