package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{ContributingPower, ContributionCollector, OfferHost, OptionRestriction, PowerCtx, Restriction, Waiver}
import oathdigital.model.{Answered, Branch, ConsentAnswer, ConsentAsk, ConsentKey, ConsentTurn, Decide, DecisionAnswer, DecisionOption, DecisionOptionRef, DecisionQuery, OathViolation, OfferedPlan, Operation, OptionReason, PendingTree, PlayerId, PowerId, PowerWindow, PrimitiveOperation, ProcedureRef, ReadyGame}

/** Task 3's power-gather/fold mechanics for [[ProcedureWalker]], split into
  * their own file to keep `ProcedureWalker.scala` under the project's
  * per-file line bound (`BackendArchitectureSuite`'s "all production Scala
  * files stay bounded"). Everything here is `private[walker]`:
  * `ProcedureWalker` is the sole caller, and the only power-shaped surface it
  * re-exports publicly is `WalkerPowers` (declared on `ProcedureWalker`),
  * `ProcedureWalker.restrictionViolations` (delegates to
  * [[restrictionViolations]] below), and `ProcedureWalker.parkedRoll`/
  * `ProcedureWalker.parkedDecide` (delegate to [[leafAt]] below, fix-round
  * ruling J).
  */
private[walker] object WalkerPowerGather:

  /** A restriction's violation with the power and context that produced it,
    * so the look-ahead can ask that restriction for its note. */
  private final case class Attributed(power: PowerId, ctx: PowerCtx,
      restriction: Restriction, violation: OathViolation)

  /** An option restriction that forbids an option, with the context it
    * judged in and its violation. */
  private final case class Forbidding(power: PowerId,
      restriction: OptionRestriction, ctx: PowerCtx, violation: OathViolation)

  /** One block on an option, from either kind of restriction: its power, its
    * waiver and the context it judged in. */
  private final case class Block(power: PowerId, waiver: Option[Waiver],
      ctx: PowerCtx)

  /** Gathers powers at `window` (a no-op for `None` -- spec decision 9's
    * third case: an engine-internal node contributes nothing) and folds
    * `ops` through the resulting transforms, in the deterministic order
    * `ContributionCollector.gather` produced. Returns the folded operations
    * (`== ops` unchanged when there is no window or nothing applicable) and
    * the gather's contribution order, to be recorded verbatim on whichever
    * `WalkerStepRecorded` this node's execution produces (Task 3 wiring
    * rules 1-3).
    *
    * `answered` are the decisions answered so far, which a contribution reads
    * from its `PowerCtx`. `resuming` is true when the walk is resuming inside
    * this node: an [[OfferHost]] then keeps its shape whatever it is offered.
    * When the node is an [[OfferHost]], the offers the window gathered, with
    * its option restrictions run over them ([[restrictOffers]]), are turned
    * into its children after the transforms have run. When the node is a
    * [[ModifierChoice]], its pass is expanded with these powers, and the
    * window's option restrictions apply to its decision.
    */
  def applyWindow(window: Option[PowerWindow], operation: Operation,
      state: ReadyGame,
      activePlayer: PlayerId, powers: WalkerPowers, path: Vector[String],
      ops: Vector[Operation], procedure: Option[oathdigital.model.ProcedureRef],
      answered: Vector[Answered] = Vector.empty, resuming: Boolean = false)
      : (Vector[Operation], Vector[PowerId]) =
    window match
      case None => (ops, Vector.empty)
      case Some(w) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, w, path, operation,
            procedure, answered)
        val gathered = ContributionCollector.gather(w, powers.windowIndex, ctxFor)
        val folded = gathered.transforms.foldLeft(ops):
          case (acc, (powerId, transform)) =>
            transform.fn(ctxFor(byId(powerId)), acc)
        operation match
          case _: Decide =>
            (restrictOptions(folded, gathered.optionRestrictions, ctxFor, byId),
              gathered.order)
          case host: OfferHost =>
            val offered = restrictOffers(
              gathered.offers.flatMap { case (powerId, offer) =>
                offer.plan(ctxFor(byId(powerId))).map(OfferedPlan(powerId, _))
              }, gathered.optionRestrictions, ctxFor, byId)
            (folded ++ host.expand(offered, OfferHost.Pass(state, answered,
              resuming, WalkerSimulation.applies(_, state, powers))),
              gathered.order)
          case choice: ModifierChoice =>
            (restrictOptions(folded ++ choice.expand(ModifierChoice.Pass(
              state, answered, resuming, powers)),
              gathered.optionRestrictions, ctxFor, byId), gathered.order)
          case _ => (folded, gathered.order)

  /** The options of a choose-one or choose-many decision that a player may
    * choose, in order. */
  private def optionRefs(decide: Decide): Vector[DecisionOptionRef] =
    WalkerSearch.choosable(decide)

  /** The offers an [[OfferHost]] gathered, with the window's option
    * restrictions run over each by its source's option (catalog batch 6,
    * "Option restrictions over offers"). A forbidden offer is withheld with
    * the first forbidding restriction's note, or dropped when that one has
    * none, as a forbidden `Decide` option is. An offer its own rule already
    * withheld keeps its reason. No plan is asked about, so a waiver is not
    * consulted.
    *
    * A restriction without a note must not forbid a plan already picked in
    * the window: the pass that applies the pick looks the plan up among
    * these offers, and a dropped plan fails that resume. A noted one is
    * safe, because the pick is applied whatever the offer's reason.
    */
  private def restrictOffers(offers: Vector[OfferedPlan],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[OfferedPlan] =
    if restrictions.isEmpty then offers
    else offers.flatMap { offered =>
      if offered.offer.withheld.nonEmpty then Vector(offered)
      else
        val ref = offered.offer.source.ref
        restrictions.iterator.flatMap { case (id, restriction) =>
          val ctx = ctxFor(byId(id))
          restriction.fn(ctx, ref).map(Forbidding(id, restriction, ctx, _))
        }.nextOption() match
          case None => Vector(offered)
          case Some(first) => first.restriction.note(first.ctx, ref).toVector
            .map(note => offered.copy(offer = offered.offer.copy(
              withheld = Some(OptionReason.Noted(first.power, note)))))
    }

  /** The restrictions that forbid `ref` of `decide`, in power order. A
    * waivable one is left out once a grant for its power, the decision's
    * owner, the decision and `ref` is remembered this turn (consent design,
    * "One ledger check"). The reference is the option itself, so the grant
    * is tied to its decision.
    */
  private def forbidding(decide: Decide,
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower])(ref: DecisionOptionRef)
      : Vector[Forbidding] =
    restrictions.flatMap { case (id, restriction) =>
      val ctx = ctxFor(byId(id))
      restriction.fn(ctx, ref)
        .filterNot(_ => restriction.waiver.nonEmpty && ctx.state.consent
          .remembered(ConsentKey(id, decide.owner, decide.decisionId, ref),
            ConsentTurn.of(ctx.state)).contains(ConsentAnswer.Granted))
        .map(Forbidding(id, restriction, ctx, _))
    }

  /** Removes the forbidden options from every `Decide` in `ops`. A
    * forbidden option is kept as askable when it may be asked about, else as
    * unavailable when the first restriction that forbids it notes why; see
    * [[OptionRestriction]] for what happens when nothing is left.
    */
  private def restrictOptions(ops: Vector[Operation],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[Operation] =
    if restrictions.isEmpty then ops
    else ops.flatMap:
      case decide: Decide =>
        val forbids = forbidding(decide, restrictions, ctxFor, byId)
        WalkerSearch.narrowed(decide, forbids(_).isEmpty, { ref =>
          val found = forbids(ref)
          optionAskable(decide, ref, found).orElse(found.headOption.flatMap(
            first => first.restriction.note(first.ctx, ref)
              .map(OptionReason.Noted(first.power, _))))
        }).toVector
      case other => Vector(other)

  /** `ref`'s consent ask on the option path. Only at a choose-many: a grant
    * there opens the option and answers nothing, so no request can be left
    * waiting on a grant that does not answer (consent slice 2). */
  private def optionAskable(decide: Decide, ref: DecisionOptionRef,
      found: Vector[Forbidding]): Option[OptionReason] =
    if !decide.query.isInstanceOf[DecisionQuery.ChooseMany] then None
    else found.headOption.flatMap(first => consentAsk(decide, ref,
      found.map(forbid => Block(forbid.power, forbid.restriction.waiver,
        forbid.ctx)), first.ctx.state))

  /** The asks of a parked choose-one's options that its own window's option
    * restrictions bar (consent slice 4). The fold cannot search, so it marks
    * each such option unavailable with the first restriction's note; here an
    * option every one of whose blocks is waivable (ruling 8) becomes askable
    * when `grantable` says a grant would open it. Empty for any other query
    * and for a decision with no window of its own. */
  def optionAsks(decide: Decide, state: ReadyGame, activePlayer: PlayerId,
      powers: WalkerPowers, path: Vector[String],
      procedure: Option[ProcedureRef], answered: Vector[Answered],
      grantable: (DecisionOptionRef, Vector[PowerId]) => Boolean)
      : Vector[(DecisionOptionRef, OptionReason)] =
    (decide.query, decide.window) match
      case (one: DecisionQuery.ChooseOne, Some(window)) =>
        val byId: Map[PowerId, ContributingPower] =
          powers.powers.map(power => power.id -> power).toMap
        def ctxFor(power: ContributingPower): PowerCtx =
          PowerCtx(state, activePlayer, power.source, window, path, decide,
            procedure, answered)
        val forbids = forbidding(decide, ContributionCollector.gather(window,
          powers.windowIndex, ctxFor).optionRestrictions, ctxFor, byId)
        one.options.collect {
          case DecisionOption.Unavailable(option,
              OptionReason.Noted(power, _)) => (option.ref, power)
        }.flatMap { (ref, noted) =>
          val found = forbids(ref)
          found.headOption.filter(_.power == noted).flatMap(first =>
            consentAsk(decide, ref, found.map(forbid => Block(forbid.power,
              forbid.restriction.waiver, forbid.ctx)), first.ctx.state))
            .filter(ask => grantable(ref, ask.asks.flatMap(_.powers).distinct))
            .map(ref -> _)
        }
      case _ => Vector.empty

  /** A required decision with every option forbidden cannot be answered: the
    * violation is the first restriction's, for the first option. A
    * choose-one's option that may be asked about does not count as
    * forbidden: the search at the decision judges whether a grant would open
    * it, so a payer who cannot pay may still reach the step to ask (consent
    * slice 4).
    */
  private def emptiedDecision(decide: Decide,
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctx: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[OathViolation] =
    val required: Option[Vector[DecisionOptionRef]] = decide.query match
      case _: DecisionQuery.ChooseOne => Some(optionRefs(decide))
      case many: DecisionQuery.ChooseMany if many.min >= 1 =>
        Some(optionRefs(decide))
      case _ => None
    required.filter(_.nonEmpty).toVector.flatMap { refs =>
      val verdicts = refs.map { ref =>
        val found = forbidding(decide, restrictions, ctx, byId)(ref)
        val askable = decide.query.isInstanceOf[DecisionQuery.ChooseOne] &&
          found.headOption.exists(first => consentAsk(decide, ref,
            found.map(forbid => Block(forbid.power, forbid.restriction.waiver,
              forbid.ctx)), first.ctx.state).nonEmpty)
        found.headOption.filterNot(_ => askable).map(_.violation)
      }
      if verdicts.forall(_.nonEmpty) then verdicts.head.toVector else Vector.empty
    }

  /** Collects every restriction violation from every windowed node in
    * `tree`, run against the tree root (spec decision 9's `Restriction`
    * kind), as `OathRules` requires the whole command to check before any
    * node runs (Task 3 wiring rule: restrictions run once per command, at
    * command entry, before the walk -- not per-node during the walk as
    * decision 10's general protocol describes, a deliberate simplification
    * since resolving every `Branch`'s dynamic children up front would
    * require walking before restrictions are known to pass). A `Branch` is
    * resolved the way the walk resolves it -- `select` against the current
    * state (fix-round ruling H) -- so restrictions declared inside a
    * branch's selected children are collected; `Branch.children` is
    * statically empty and reading it would silently skip them. `select` is
    * already required to be a pure function of state, and the traversal has
    * no answered decisions to offer it, so it passes an empty `PendingTree`
    * at the branch's own path. The traversal also reports a required
    * decision whose every option an `OptionRestriction` forbids, since such a
    * decision could never be answered.
    */
  def restrictionViolations(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId,
      answered: Vector[Answered] = Vector.empty): Vector[OathViolation] =
    val (rejected, emptied) = attributed(tree, powers.withAnswers(answered),
      state, activePlayer, answered)
    rejected.map(_.violation) ++ emptied

  /** [[restrictionViolations]], keeping who rejected: the restrictions'
    * violations with their power, and the emptied decisions. */
  private def attributed(tree: Operation, powers: WalkerPowers,
      state: ReadyGame, activePlayer: PlayerId, answered: Vector[Answered])
      : (Vector[Attributed], Vector[OathViolation]) =
    val byId: Map[PowerId, ContributingPower] =
      powers.powers.map(power => power.id -> power).toMap
    /** Each contribution's context carries `answered`, so a restriction can
      * read what was chosen.
      */
    def ctxFor(window: PowerWindow, path: Vector[String], operation: Operation)
        : ContributingPower => PowerCtx =
      power => PowerCtx(state, activePlayer, power.source, window, path,
        operation, state.game.current.walkerProcedure, answered)
    def windowsIn(node: Operation, path: Vector[String])
        : Vector[(PowerWindow, Vector[String], Operation)] =
      val own = node.window.map(w => Vector((w, path, node))).getOrElse(Vector.empty)
      def descend(children: Vector[Operation]) =
        children.zipWithIndex.flatMap { case (child, index) =>
          windowsIn(child, path :+ index.toString) }
      val nested = node match
        // A PrimitiveOperation's `children` is `Vector(this)` (leaf
        // self-reference, see `Operation.scala`); descending into it would
        // recurse forever, so leaves never contribute nested windows.
        case _: PrimitiveOperation => Vector.empty
        case branch: Branch => descend(applyWindow(branch.window, branch,
          state, activePlayer, powers, path, branch.select(state,
            PendingTree(at = path, answered = answered)),
          state.game.current.walkerProcedure, answered)._1)
        case _ => descend(applyWindow(node.window, node, state,
          activePlayer, powers, path, node.children,
          state.game.current.walkerProcedure, answered)._1)
      own ++ nested
    val windows = windowsIn(tree, Vector.empty)
    val rejected = windows.flatMap:
      case (window, path, operation) =>
        val ctx = ctxFor(window, path, operation)
        ContributionCollector.gather(window, powers.windowIndex, ctx).restrictions
          .flatMap { case (powerId, restriction) =>
            val at = ctx(byId(powerId))
            restriction.fn(at, tree).filterNot(_ => waived(powerId,
              restriction, at)).map(Attributed(powerId, at, restriction, _))
          }
    val emptied = windows.flatMap:
      case (window, path, decide: Decide) =>
        val ctx = ctxFor(window, path, decide)
        emptiedDecision(decide, ContributionCollector.gather(window,
          powers.windowIndex, ctx).optionRestrictions, ctx, byId)
      case _ => Vector.empty
    (rejected, emptied)

  /** Whether a remembered grant drops `power`'s breach (consent design, "One
    * ledger check"): the restriction is waivable, and a decision of this
    * walk was answered with an option its answerer was granted this turn.
    * Every caller of the tree check passes through here, so the walk, the
    * search and the previews agree. */
  private def waived(power: PowerId, restriction: Restriction,
      ctx: PowerCtx): Boolean =
    restriction.waiver.nonEmpty && {
      val turn = ConsentTurn.of(ctx.state)
      ctx.answered.exists:
        case Answered(decisionId, DecisionAnswer.ChooseOneAnswer(target),
            by) =>
          ctx.state.consent.remembered(ConsentKey(power, by, decisionId,
            target), turn).contains(ConsentAnswer.Granted)
        case _ => false
    }

  /** The tree-level `Restriction` violations of `root` with `answered`,
    * against the state as the walk sees it: no pending tree, the walk's
    * procedure. Empty without a power that restricts. The traversal runs on
    * the quiet powers, so a dry run inside it never searches.
    */
  def breaches(root: Operation, state: ReadyGame, activePlayer: PlayerId,
      powers: WalkerPowers, answered: Vector[Answered],
      procedure: Option[ProcedureRef]): Vector[OathViolation] =
    if !powers.hasRestrictions then Vector.empty
    else restrictionViolations(root, powers.quiet, state.updateCurrent(
      _.copy(walkerPending = None, walkerProcedure = procedure)),
      activePlayer, answered)

  /** Why each option `narrowed` no longer offers is hidden, read from the
    * restrictions whose violation answering it would add. It is askable
    * when every one of them is waivable by a player other than the
    * decision's owner and nothing on the option was refused this turn
    * (consent design, ruling 8), and `grantable` says a grant of their powers
    * would open it; otherwise it is the first one's note. Empty without a
    * power that restricts, since only a restriction can explain a hidden
    * option.
    */
  def lookAheadNoted(root: Operation, decide: Decide, narrowed: Decide,
      state: ReadyGame, activePlayer: PlayerId, powers: WalkerPowers,
      answered: Vector[Answered], procedure: Option[ProcedureRef],
      grantable: (DecisionOptionRef, Vector[PowerId]) => Boolean)
      : Vector[(DecisionOptionRef, OptionReason)] =
    val offered = optionRefs(narrowed).toSet
    val hidden = optionRefs(decide).filterNot(offered)
    if hidden.isEmpty || !powers.hasRestrictions then Vector.empty
    else
      val quiet = powers.quiet
      val seen = state.updateCurrent(_.copy(walkerPending = None,
        walkerProcedure = procedure))
      def found(answers: Vector[Answered]): Vector[Attributed] =
        attributed(root, quiet, seen, activePlayer, answers)._1
      val baseline = found(answered).map(_.violation).toSet
      hidden.flatMap { ref =>
        val answer = decide.query match
          case _: DecisionQuery.ChooseMany =>
            DecisionAnswer.ChooseManyAnswer(Vector(ref))
          case _ => DecisionAnswer.ChooseOneAnswer(ref)
        val fresh = found(answered :+ Answered(decide.decisionId, answer,
          decide.owner)).filterNot(breach => baseline(breach.violation))
        askable(decide, ref, fresh, seen, grantable).orElse(fresh.headOption.flatMap(
          breach => breach.restriction.note(breach.ctx, ref).map(note =>
            OptionReason.Noted(breach.power, note))))
          .map(ref -> _)
      }

  /** `ref`'s consent ask on the tree path, when a grant of its powers would
    * open it: a destination the traveller cannot pay for is not worth asking
    * about. Only a choose-one: a grant answers the decision with `ref` alone
    * (slice 1 plan decision 8). */
  private def askable(decide: Decide, ref: DecisionOptionRef,
      fresh: Vector[Attributed], state: ReadyGame,
      grantable: (DecisionOptionRef, Vector[PowerId]) => Boolean)
      : Option[OptionReason] =
    if !decide.query.isInstanceOf[DecisionQuery.ChooseOne] then None
    else consentAsk(decide, ref, fresh.map(breach => Block(breach.power,
      breach.restriction.waiver, breach.ctx)), state)
      .filter(ask => grantable(ref, ask.asks.flatMap(_.powers).distinct))

  /** The ask for `ref` when every block on it is waivable by a player other
    * than the decision's owner and none of their answers on it was refused
    * this turn (consent design, ruling 8). Its reason is the first block's
    * consent note, and it asks each granter about all of their powers. */
  private def consentAsk(decide: Decide, ref: DecisionOptionRef,
      blocks: Vector[Block], state: ReadyGame)
      : Option[OptionReason.Askable] =
    val requester = decide.owner
    val turn = ConsentTurn.of(state)
    val granters = blocks.map(_.waiver.flatMap(_.granter(state))
      .filter(_ != requester))
    val refused = blocks.exists(block => state.consent.remembered(
      ConsentKey(block.power, requester, decide.decisionId, ref), turn)
      .contains(ConsentAnswer.Refused))
    if blocks.isEmpty || granters.exists(_.isEmpty) || refused then None
    else
      val owed = blocks.map(_.power).zip(granters.flatten)
      val asks = owed.map(_._2).distinct.map(granter => ConsentAsk(granter,
        owed.collect { case (power, `granter`) => power }.distinct))
      val first = blocks.head
      first.waiver.map(waiver => OptionReason.Askable(first.power,
        waiver.note(first.ctx, ref), asks))

  /** Resolves the node addressed by `pending.at` (a child-index path rooted
    * at `action`), or `None` when a segment is non-numeric or out of range (a
    * fabricated or stale position). Each step down the path resolves children
    * through [[foldedChildrenAt]] -- the SAME fold the live walk applied on
    * its way to this park (fix-round ruling J) -- rather than the declared,
    * unfolded tree: a `Branch` is resolved by evaluating `select(state, ...)`
    * (with `at` set to the path consumed so far, matching `walkBranch`'s
    * `branchTree`), and any windowed node's children are folded through
    * `powers` exactly as `walkComposite`/`walkBranch`/`walkLeaf` do, so a
    * transform that inserts operations around a windowed node does not make
    * this address the wrong node. `powers` MUST be the same vector the live
    * walk that parked here used -- a `Transform` is required to be a pure
    * function of state (same invariant `Branch.select` already carries), so
    * re-folding here with the same `powers` reproduces the exact indices the
    * walk parked at. The callers are [[ProcedureWalker.parkedRoll]] and
    * [[ProcedureWalker.openDecisions]] (and through it `parkedDecide`), which
    * answer checks, projections and awaited-player lookups use. It lives here
    * (like [[applyWindow]]/[[restrictionViolations]] above) to keep
    * `ProcedureWalker.scala` under the project's line bound.
    *
    * When `powers.probing` is on, a `Decide` is narrowed by the search
    * ([[ProcedureWalker.narrowParked]]), exactly as the walk narrowed it
    * before parking. A position the walk would not have parked at, whose
    * search empties the decision, is returned as declared, and the
    * answer-time check refuses it.
    */
  def leafAt(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[Operation] =
    val picked = powers.withAnswers(pending.answered)
    resolveAt(state, pending, picked, action, pending.at, Vector.empty,
      Set.empty).map:
        case decide: Decide => ProcedureWalker.narrowParked(state, action,
          pending, picked, decide)
        case other => other

  private def resolveAt(state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, node: Operation, remaining: Vector[String],
      consumed: Vector[String], gathered: Set[PowerWindow]): Option[Operation] =
    remaining.headOption match
      case None => Some(node)
      case Some(segment) => segment.toIntOption.flatMap { index =>
        val (children, nextGathered) = foldedChildrenAt(state, pending,
          powers, node, consumed, gathered)
        if index >= 0 && index < children.size then
          resolveAt(state, pending, powers, children(index), remaining.tail,
            consumed :+ segment, nextGathered)
        else None
      }

  /** The children `node` presents at `path` for the purpose of navigating one
    * more path segment -- mirroring `walkBranch`/`walkLeaf`/`walkComposite`'s
    * dispatch, but computing only the folded children rather than executing
    * anything. `gathered` mirrors [[WalkerHooks.gathered]]: a windowed leaf's
    * own fold (applied to `Vector(leaf)`, since a `PrimitiveOperation`'s
    * `children` is a self-reference) is applied at most once per branch, so a
    * transform that hands the leaf back unchanged does not re-trigger its own
    * window on the next path segment. A composite's fold needs no such guard
    * -- its fold cannot reproduce the composite itself, only its children.
    */
  private def foldedChildrenAt(state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, node: Operation, path: Vector[String],
      gathered: Set[PowerWindow]): (Vector[Operation], Set[PowerWindow]) =
    val activePlayer = state.game.current.turn.activePlayer
    node match
      case branch: Branch =>
        val selected = branch.select(state, pending.copy(at = path))
        val (folded, _) = applyWindow(branch.window, branch, state,
          activePlayer, powers, path, selected, state.game.current.walkerProcedure,
          pending.answered, resuming = true)
        (folded, gathered)
      case leaf: PrimitiveOperation =>
        leaf.window match
          case Some(w) if !gathered.contains(w) =>
            val (folded, _) = applyWindow(Some(w), leaf, state,
              activePlayer, powers, path, Vector(leaf),
              state.game.current.walkerProcedure, pending.answered,
              resuming = true)
            (folded, gathered + w)
          case _ => (leaf.children, gathered)
      case composite =>
        val (folded, _) = applyWindow(composite.window, composite, state,
          activePlayer, powers, path, composite.children,
          state.game.current.walkerProcedure, pending.answered, resuming = true)
        (folded, gathered)

/** Power attribution and re-entry guard threaded DOWN one walk branch (unlike
  * the walker's `WalkCtx`, which threads FORWARD across siblings).
  *
  *  - `inherited` is the contribution order gathered by every enclosing
  *    windowed node, outermost first, de-duplicated with first occurrence
  *    winning. A leaf records exactly this on its event: a windowed composite
  *    emits no event of its own, so its transform reaches the journal only
  *    through the leaves it shaped (fix-round ruling F).
  *  - `gathered` holds the windows already folded on this branch. A leaf's
  *    fold is applied to `Vector(leaf)`, so the ordinary "insert an op"
  *    transform hands the leaf back inside its own folded vector; without this
  *    guard, walking that vector would gather the same window forever. A
  *    composite's fold cannot reproduce the composite (a transform only ever
  *    sees the children), so composites add nothing here.
  *  - `strict` is set inside a `required` composite (`PayCost`, `Draw`, ...).
  *    The walker runs such a composite as its `Move` children, which are
  *    best-effort on their own, so the composite's `required` has to ride down
  *    to the leaves it is walked as.
  */
private[walker] final case class WalkerHooks(inherited: Vector[PowerId],
    gathered: Set[PowerWindow], strict: Boolean = false):
  def withOrder(order: Vector[PowerId]): WalkerHooks =
    if order.isEmpty then this
    else copy(inherited = (inherited ++ order).distinct)

private[walker] object WalkerHooks:
  val none: WalkerHooks = WalkerHooks(Vector.empty, Set.empty)
