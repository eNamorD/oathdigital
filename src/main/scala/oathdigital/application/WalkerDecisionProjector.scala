package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerActionRegistry,
  WalkerPowers}
import oathdigital.model._
import oathdigital.protocol.projection.{CardDetailsProjection,
  DecisionOptionProjection, DecisionQueryProjection,
  DecisionSectionProjection, WalkerDecisionProjection,
  WalkerRollOutcomeProjection}

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
    walkerPowerCatalog: WalkerPowers,
    rebuildTree: WalkerDecisionProjector.TreeSource =
      WalkerDecisionProjector.declaredTree) {

  def this(catalog: ExecutableCatalog, presentation: GamePresentationProjector) =
    this(catalog, presentation, WalkerPowerCatalog.default(catalog))

  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] =
    for {
      pending <- context.current.walkerPending
      action <- context.current.walkerAction
      if context.viewer.contains(context.current.turn.activePlayer)
      tree <- rebuild(context.ready, action, context.current.turn.activePlayer,
        context.current.walkerStartArgs).toOption
      powers = WalkerPowers.selected(walkerPowerCatalog,
        context.current.walkerModifiers)
      projection <- parked(action, tree, context.ready, pending, powers)
    } yield projection

  private def rebuild(ready: ReadyGame, action: ActionRef, actor: PlayerId,
      args: Vector[DecisionOptionRef]) =
    rebuildTree(catalog, action, ready, actor, args)

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
            rollOutcome = rollOutcome(ready,
              ready.game.current.turn.activePlayer)))
      // Task 4: no `decisionId` comparison and no candidate discovery.
      // Whatever the parked `Decide` declares -- after every power
      // transform, since `parkedDecide` resolves the node through the same
      // fold the walk applied -- is described verbatim. An action that
      // changes what it offers changes this projection in the same edit,
      // and an engine that grew a per-action branch here would be exactly
      // the drift this replaced.
      //
      // `flatMap`, not `map`: an unpresentable option omits the whole
      // projection (see [[queryProjection]]).
      case None => ProcedureWalker.parkedDecide(ready, tree, pending, powers)
        .flatMap(decide => queryProjection(ready,
          Some(ready.game.current.turn.activePlayer), decide.query).map(query =>
          WalkerDecisionProjection(action.key, decide.decisionId, "decide",
            query = Some(query),
            rollOutcome = rollOutcome(ready,
              ready.game.current.turn.activePlayer))))
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
    */
  private def queryProjection(ready: ReadyGame, viewer: Option[PlayerId],
      query: DecisionQuery): Option[DecisionQueryProjection] = {
    // One index per projection, shared by every option: a Forge partition
    // asks about three denizens and a Recover pick about every site relic.
    val index = CardIndex.from(ready.game).toOption
    def described(options: Vector[DecisionOption])
        : Option[Vector[DecisionOptionProjection]] = {
      val projected = options.flatMap(optionProjection(ready, viewer, index, _))
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
            section.label, section.minRequired)),
          heading = heading, confirmLabel = confirmLabel))
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
    * into a suppressed decision. A `Deck` is a closed four-case enum and a
    * button is its own identity, so neither can be absent.
    */
  private def optionProjection(ready: ReadyGame, viewer: Option[PlayerId],
      index: Option[CardIndex],
      option: DecisionOption): Option[DecisionOptionProjection] = {
    val ref = option.ref
    def row(label: String, card: Option[CardDetailsProjection] = None) =
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card))
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
      case DecisionOption.Deck(deck) =>
        row(presentation.safeLabel(deck.id.key))
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
    * above, which must stay generic -- a `decisionId` or `ActionRef`
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
    * `WalkerActionRegistry`'s production entries, so substituting a tree
    * cannot also substitute the answer under test.
    */
  type TreeSource =
    (ExecutableCatalog, ActionRef, ReadyGame, PlayerId,
      Vector[DecisionOptionRef]) => Either[OathViolation, Operation]

  val declaredTree: TreeSource = (catalog, action, ready, actor, args) =>
    WalkerActionRegistry.rebuild(action, catalog, ready, actor, args)
}
