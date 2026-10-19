package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{ConsentPowers, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.walker.{OptionWarnings, ProcedureWalker,
  WalkerPowers, WalkerProcedureRegistry, WalkerSimulation}
import oathdigital.model._
import oathdigital.protocol.projection.{CardDetailsProjection,
  ConsentAlternativeProjection, ConsentAskProjection,
  CountedConfirmProjection, DecisionOptionProjection, DecisionQueryProjection,
  DecisionSectionProjection, DecisionSlotProjection, DecisionSummaryProjection,
  DecisionTallyProjection, DecisionTallyValueProjection,
  WalkerDecisionProjection, WalkerRollOutcomeProjection,
  WalkerWaitingProjection}

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
  * dispatches on to check that a live command's park is answerable — rather
  * than inspecting `PendingTree.at` directly, for the identical reason: a
  * structural path match would silently point at the wrong node if
  * `RecoverProcedure`'s tree shape ever changes.
  *
  * `walkerPowerCatalog` (Task 5) is the same full catalog `OathRules`
  * offers a live command, with the modifiers the walk's answers pick added
  * (`WalkerPowers.withAnswers`), the SAME powers `OathRules.walkerPowers`
  * walks with at command time. Without this, a power that inserts
  * operations at a shared window (Catacombs at `RecoverActionEligibility`)
  * would make this projector re-fold the tree differently from the walker
  * that actually parked it, misreporting the parked node.
  */
private[application] final class WalkerDecisionProjector(
    catalog: ExecutableCatalog, presentation: GamePresentationProjector,
    walkerPowerCatalog: WalkerPowers,
    rebuildTree: WalkerDecisionProjector.TreeSource =
      WalkerDecisionProjector.declaredTree,
    phasePowers: PhasePowers = PhasePowers.empty):
  private val deals = new NegotiationDealProjector(presentation)
  private lazy val reasons = new gamelog.NoteReasons(catalog, presentation)
  private lazy val consentPowers = ConsentPowers.default(catalog)
  private lazy val consentNames = new ConsentNames(presentation,
    consentPowers)

  def this(catalog: ExecutableCatalog, presentation: GamePresentationProjector) =
    this(catalog, presentation, WalkerPowerCatalog.default(catalog),
      WalkerDecisionProjector.declaredTree, PhasePowerCatalog.default(catalog))

  private def parkedPosition(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjector.Parked] =
    import WalkerDecisionProjector.Parked
    for
      pending <- context.current.walkerPending
      procedure <- context.current.walkerProcedure
      tree <- rebuild(context.ready, procedure,
        context.current.walkActor,
        context.current.walkerStartArgs).toOption
      // The modifiers picked in the walk join, so their warnings show.
      powers = WalkerPowers.selected(walkerPowerCatalog, Vector.empty)
        .withAnswers(pending.answered)
      awaited <- ProcedureWalker.awaitedPlayer(context.ready, tree, pending,
        powers)
      owners = ProcedureWalker.awaitedPlayers(context.ready, tree, pending,
        powers)
    yield Parked(procedure, tree, pending, powers, awaited, owners)

  /** The `Decide` the walker is parked on in `ready`, after every power's
    * transform: the declared query, for the game log to read a button's
    * label off of. Skips the restriction look-ahead: `ChoiceWords` reads
    * this only for an answer already recorded, and a legally recorded answer
    * was never among the options the look-ahead would have hidden.
    */
  def parkedDecide(ready: ReadyGame): Option[Decide] =
    val current = ready.game.current
    for
      pending <- current.walkerPending
      procedure <- current.walkerProcedure
      tree <- rebuild(ready, procedure, current.walkActor,
        current.walkerStartArgs).toOption
      decide <- ProcedureWalker.parkedDecide(ready, tree, pending,
        WalkerPowers.selected(walkerPowerCatalog, Vector.empty),
        probing = false)
    yield decide

  /** The full owner-private projection, for every owner of the parked
    * decision: the awaited player and any co-owners.
    */
  def project(context: ScopedProjectionContext)
      : Option[WalkerDecisionProjection] = for
    parked <- parkedPosition(context)
    if context.viewer.exists(parked.owners.contains)
    projection <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited, context.viewer)
  yield projection

  /** The public "who is this waiting on" projection, for every viewer
    * except the awaited player -- Task 5's counterpart to `project`. Built
    * off the same [[WalkerDecisionProjector.Parked]] position, so a viewer
    * who is not the awaited player always sees a projection naming exactly
    * who is.
    *
    * Gated on the awaited player's prompt, not this viewer's: a decision
    * whose options this viewer may not identify (a pick among the actor's
    * facedown advisers) still tells the table whom it waits on, while a
    * decision suppressed even for its owner tells no one.
    */
  def waiting(context: ScopedProjectionContext)
      : Option[WalkerWaitingProjection] = for
    parked <- parkedPosition(context)
    if !context.viewer.exists(parked.owners.contains)
    _ <- this.parked(parked.procedure, parked.tree, context.ready,
      parked.pending, parked.powers, parked.awaited, Some(parked.awaited),
      previewed = false)
  yield
    val decide = ProcedureWalker.parkedDecide(context.ready, parked.tree,
      parked.pending, parked.powers)
    WalkerWaitingProjection(parked.awaited.value,
      decide.flatMap(_.query.heading),
      parked.owners.filter(_ != parked.awaited).toVector.map(_.value).sorted,
      decide.map(_.query).collect {
        case negotiate: DecisionQuery.Negotiate =>
          deals.project(context.ready, context.viewer, negotiate)
      })

  private def rebuild(ready: ReadyGame, procedure: ProcedureRef,
      actor: PlayerId, args: Vector[DecisionOptionRef]) =
    tree(procedure, ready, actor, args)

  private def tree(procedure: ProcedureRef, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]) = procedure match
    case _: ActionRef.UsePower | TriggeredProcedureRef.ForcedWake |
        TriggeredProcedureRef.FollowUpOffer |
        TriggeredProcedureRef.WakeMenu | TriggeredProcedureRef.RestMenu |
        TriggeredProcedureRef.ActMenu =>
      WalkerProcedureRegistry.rebuild(procedure, catalog, ready, actor, args,
        phasePowers, walkerPowers = WalkerPowers.selected(walkerPowerCatalog,
          Vector.empty))
    case _ => rebuildTree(catalog, procedure, ready, actor, args)

  private def parked(procedure: ProcedureRef, tree: Operation,
      ready: ReadyGame, pending: PendingTree, powers: WalkerPowers,
      awaited: PlayerId, viewer: Option[PlayerId], previewed: Boolean = true)
      : Option[WalkerDecisionProjection] =
    ProcedureWalker.parkedRoll(ready, tree, pending, powers) match
      // R18: a procedure whose entry declares no roll decision id has no
      // answer to "which id is this Roll park", so the accessor's typed
      // rejection is carried through as "there is nothing to project" --
      // never as a projection naming a sentinel the client would then
      // send back as a `ResolveWalker` decision id.
      case Some((pool, count)) =>
        WalkerProcedureRegistry.rollDecisionId(procedure).toOption.map(
          rollId => WalkerDecisionProjection(procedure.key, rollId, "roll",
            pool = Some(pool.value), count = Some(count),
            rollOutcome = rollOutcome(procedure, ready, awaited, rollId)))
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
            if previewed then playable(procedure, ready, tree, pending, powers,
              decide)
            else (decide.query, Map.empty[DecisionOptionRef, Vector[String]])
          queryProjection(ready, viewer, query, details).map(projected =>
            WalkerDecisionProjection(procedure.key, decide.decisionId, "decide",
              query = Some(headed(ready, viewer, decide.decisionId, projected)),
              rollOutcome = rollOutcome(procedure, ready, awaited,
                decide.decisionId),
              answeredOptions = answeredOptions(ready, viewer, pending,
                decide.decisionId),
              presentation = decide.presentation.key))
        }

  /** The decision's query as this viewer is offered it. For a procedure that
    * opts in (`Entry.requiresPlayableOption`), only the options its own
    * preview accepts, each with the consequences that answering it records;
    * for every other procedure the declared query, untouched. A preview that
    * cannot run leaves the declared query, so a projection is never lost to
    * it. A procedure that prices its options shows them all, as its pricing
    * words them.
    */
  private def playable(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, pending: PendingTree, powers: WalkerPowers,
      decide: Decide): (DecisionQuery, Map[DecisionOptionRef, Vector[String]]) =
    val (query, details) =
      WalkerProcedureRegistry.optionPricing(procedure) match
        case Some(price) => (decide.query match
          case one: DecisionQuery.ChooseOne =>
            one.copy(options = price(ready, tree, pending, powers, decide))
          case other => other,
          Map.empty[DecisionOptionRef, Vector[String]])
        case None => accepting(procedure, ready, tree, pending, powers, decide)
    (query match
      case one: DecisionQuery.ChooseOne =>
        one.copy(options = OptionWarnings.marked(powers, decide, one.options))
      case other => other, details)

  private def accepting(procedure: ProcedureRef, ready: ReadyGame,
      tree: Operation, pending: PendingTree, powers: WalkerPowers,
      decide: Decide)
      : (DecisionQuery, Map[DecisionOptionRef, Vector[String]]) =
    if !WalkerProcedureRegistry.requiresPlayableOption(procedure) then
      (decide.query, Map.empty)
    else WalkerSimulation.previewParked(ready, tree, pending, powers) match
      case Left(_) => (decide.query, Map.empty)
      case Right(previewed) =>
        val accepted = previewed.flatMap(option => option.outcome.toOption
          .map(outcome => option.option -> outcome))
        val query = decide.query match
          case one: DecisionQuery.ChooseOne =>
            // An unavailable option was never previewed, and keeps its place
            // and its reason.
            val playable = accepted.map(_._1.ref).toSet
            one.copy(options = one.options.filter(option =>
              !option.available || playable(option.ref)))
          case other => other
        (query, accepted.map { case (option, outcome) =>
          option.ref -> OperationDetails.of(outcome.operations) }.toMap)

  /** Describes a declared query, or `None` when any single option's identity
    * cannot be presented.
    *
    * The all-or-nothing rule is the spec's: a half-described option is a
    * blank control the client would render and then submit, which is worse
    * than no prompt at all. Suppressing the whole decision instead makes a
    * tree that declares a target absent from authoritative state a visible
    * failure rather than a silently broken button.
    *
    * An unavailable option is the exception: it is shown, never answered, so
    * one that cannot be presented drops itself.
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
      : Option[DecisionQueryProjection] =
    // One index per projection, shared by every option: a Forge partition
    // asks about three denizens and a Recover pick about every site relic.
    val index = ready.game.cardIndex.toOption
    // An available option that cannot be presented suppresses the whole
    // decision. An unavailable one is only shown, never answered, so it
    // drops itself instead.
    def described(options: Vector[DecisionOption])
        : Option[Vector[DecisionOptionProjection]] =
      val projected = options.map(option => option -> optionProjection(ready,
        viewer, index, option, details.getOrElse(option.ref, Vector.empty)))
      Option.when(projected.forall((option, row) =>
        row.nonEmpty || !option.available))(projected.flatMap(_._2))
    // The panel copy rides through untouched, exactly as the options do:
    // it is the action's own declaration, and the projector's whole job
    // here is to describe the transformed query rather than to author
    // anything. A query that declares none projects none, and the client
    // supplies its own generic fallback.
    query match
      case DecisionQuery.ChooseOne(options, heading, confirmVerb) =>
        described(options).map(projected =>
          DecisionQueryProjection.ChooseOne(projected, heading, confirmVerb))
      case many: DecisionQuery.ChooseMany =>
        for
          offered <- described(many.options)
          fixed <- described(many.locked)
        yield DecisionQueryProjection.ChooseMany(
          fixed.map(_.copy(state = "locked")) ++ offered, many.min, many.max,
          many.heading,
          many.tallies.map(tally => DecisionTallyProjection(tally.label,
            tally.unit, tally.perOption.toVector.map((ref, value) =>
              DecisionTallyValueProjection(ref.kind, ref.wireId, value))
              .sortBy(value => (value.kind, value.id)), tally.limit)),
          many.summary.map(summary => DecisionSummaryProjection(summary.verb,
            summary.one, summary.many)),
          many.confirm.map(confirm => CountedConfirmProjection(confirm.verb,
            confirm.one, confirm.many, confirm.none, confirm.countsLocked)))
      case negotiate: DecisionQuery.Negotiate =>
        Some(DecisionQueryProjection.Negotiate(
          deals.project(ready, viewer, negotiate), negotiate.heading))
      case DecisionQuery.Inspect(cards, heading, doneLabel) =>
        Some(DecisionQueryProjection.Inspect(
          cards.map(shown(ready, viewer, index, _)),
          DecisionOptionProjection(DecisionQuery.Inspect.Done.kind,
            DecisionQuery.Inspect.Done.wireId, doneLabel), heading))
      case DecisionQuery.ChooseAmount(min, max, heading, confirmLabel,
          suggested) =>
        Some(DecisionQueryProjection.ChooseAmount(min, max, suggested,
          confirmLabel, heading))
      case DecisionQuery.Partition(sections, options, heading, confirmLabel) =>
        described(options).map(projected =>
          DecisionQueryProjection.Partition(
            sections.map(section => DecisionSectionProjection(section.key,
              section.label, section.minRequired, section.maxAllowed)),
            projected, confirmLabel, heading))
      case DecisionQuery.Distribute(slots, minTotal, maxTotal, heading,
          confirmLabel) =>
        described(slots.flatMap(slot => DecisionOption.forRef(slot.ref)))
          .filter(_.size == slots.size).map(options =>
            DecisionQueryProjection.Distribute(
              slots.zip(options).map { case (slot, option) =>
                DecisionSlotProjection(option, slot.minimum, slot.maximum,
                  slot.suggested) },
              minTotal, maxTotal, confirmLabel, heading))

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
      details: Vector[String] = Vector.empty): Option[DecisionOptionProjection] =
    val ref = option.ref
    def row(label: String, card: Option[CardDetailsProjection] = None,
        extra: Vector[String] = Vector.empty, badge: Option[String] = None) =
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card,
        details ++ extra, badge))
    option match
      case DecisionOption.Priced(inner, price) => optionProjection(ready,
        viewer, index, inner, details ++ PriceDetails.of(price))
      // The badge is set after the wrapped option is described, so it survives
      // whichever order a caller wraps price and badge in.
      case DecisionOption.Badged(inner, badge) => optionProjection(ready,
        viewer, index, inner, details).map(_.copy(badge = Some(badge)))
      // The band is set after the wrapped option is described, for the reason
      // the badge is: a caller may wrap band and price in either order.
      case DecisionOption.Grouped(inner, group) => optionProjection(ready,
        viewer, index, inner, details).map(_.copy(group = Some(group.key)))
      // Unavailable, but its rulers may be asked (consent design,
      // "Projection"): shown as askable, with whom to ask.
      case DecisionOption.Unavailable(inner,
          reason @ OptionReason.Askable(_, _, asks, _)) =>
        optionProjection(ready, viewer, index, inner, details).map(_.copy(
          state = "askable", reason = Some(reasonText(ready, viewer, reason)),
          consent = Some(askProjection(asks))))
      case DecisionOption.Unavailable(inner, reason) => optionProjection(ready,
        viewer, index, inner, details).map(_.copy(state = "unavailable",
          reason = Some(reasonText(ready, viewer, reason))))
      // `OptionWarnings` never warns on an option that cannot be chosen.
      case DecisionOption.Warned(inner, reason) => optionProjection(ready,
        viewer, index, inner, details).map(_.copy(state = "warning",
          reason = Some(reasonText(ready, viewer, reason))))
      // Named after the wrapped option is described, so the label says what
      // the play discards and where the card goes.
      case DecisionOption.Replacing(inner, card) =>
        val replaced = shown(ready, viewer, index, card)
        optionProjection(ready, viewer, index, inner, details).map(row =>
          row.copy(label = replacingLabel(inner.ref, replaced.name),
            replaces = Some(replaced)))
      case DecisionOption.Burying(inner, card) =>
        val buried = shown(ready, viewer, index, card)
        val name = if buried.hidden then s"a ${buried.name.toLowerCase}"
          else buried.name
        optionProjection(ready, viewer, index, inner, details).map(row =>
          row.copy(label = buryingLabel(inner.ref, name),
            replaces = Some(buried), buries = true))
      case DecisionOption.Button(_, label) => row(label)
      case DecisionOption.Player(player) =>
        if ready.game.current.players.exists(_.player == player.id) then
          row(presentation.playerLabel(player.id)) else None
      case DecisionOption.Site(site) =>
        if ready.game.current.map.sites.contains(site.id) then
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
        if ready.game.current.players.find(_.player == slot.owner)
            .exists(_.relics.isDefinedAt(slot.slot)) then
          row(s"${presentation.playerLabel(slot.owner)} facedown relic")
        else None
      // The relic when the viewer may identify it, else its back: a slot
      // never discloses a facedown relic.
      case DecisionOption.SiteRelicSlot(slot) =>
        ready.game.current.map.sites.get(slot.site)
          .flatMap(_.relics.lift(slot.index)).flatMap(held =>
            card(ready, viewer, index, held.id) match
              case Some(details) => row(details.name, Some(details))
              case None => row(
                s"Facedown relic at ${presentation.siteLabel(slot.site)}",
                Some(presentation.hiddenCard("relic"))))
      // The card when the viewer may identify it, else its back: a slot
      // never discloses a facedown adviser, but the panel still draws a card.
      case DecisionOption.AdviserSlot(slot) =>
        ready.game.current.players.find(_.player == slot.owner)
          .flatMap(_.advisers.lift(slot.slot)).flatMap { held =>
            val owner = presentation.playerLabel(slot.owner)
            card(ready, viewer, index, held.id) match
              case Some(details) => row(details.name, Some(details),
                extra = Vector(s"$owner's adviser"))
              case None => row(s"$owner's facedown adviser ${slot.slot + 1}",
                Some(presentation.hiddenCard(presentation.cardKind(held.id))))
          }
      case DecisionOption.Banner(held) =>
        // The banner is one object on the table and the board says who holds
        // it, so the option names the banner alone -- and counts what that
        // banner actually takes.
        val current = ready.game.current
        row(BannerRules.displayName(held.banner),
          extra = Vector(s"Currently ${BannerRules.resources(current,
            held.banner)} ${BannerRules.resourceName(held.banner)}"))
      case DecisionOption.Deck(deck) =>
        row(s"${presentation.safeLabel(deck.id.key)} deck")
      case DecisionOption.FavorBank(bank) =>
        row(presentation.safeLabel(bank.suit.key))
      case DecisionOption.SiteSlot(slot) =>
        val fits = catalog.site(slot.site).exists(slot.index < _.capacity)
        if fits && ready.game.current.map.sites.contains(slot.site) then
          row(s"Empty slot at ${presentation.siteLabel(slot.site)}")
        else None
      case DecisionOption.AdviserPlay(play) =>
        if ready.game.current.players.exists(_.player == play.owner) then
          row(s"Adviser slot ${play.index + 1}, ${facing(play.faceup)}")
            .map(_.copy(variant = Some(facing(play.faceup).capitalize)))
        else None
      case DecisionOption.RevealedVision(spot) =>
        if ready.game.current.players.exists(_.player == spot.owner) then
          row("Revealed Vision")
        else None
      // A pile names itself: a card play discards to it and a Search draws
      // from it, so the label says neither.
      case DecisionOption.DiscardPile(pile) =>
        row(SearchSource.name(SearchSource.RegionalDiscard(pile.region)))

  /** Whom an askable option asks: its required asks to ignore a power,
    * each alternative on its own, and each required ask to pay on its own
    * (catalog batch 8, slice 15). */
  private def askProjection(asks: Vector[ConsentAsk]): ConsentAskProjection =
    def paying(ask: ConsentAsk) = ask.powers.forall(consentPowers.paying)
    def one(ask: ConsentAsk) = ConsentAlternativeProjection(
      ask.granter.value, ask.powers.map(consentNames.power), paying(ask))
    val (payers, ignoring) = asks.filterNot(_.alternative).partition(paying)
    ConsentAskProjection(ignoring.map(_.granter.value),
      ignoring.flatMap(_.powers).distinct.map(consentNames.power),
      asks.filter(_.alternative).map(one), payers.map(one))

  /** A Replace option's label: the card it discards, and where the played
    * card goes. */
  private def replacingLabel(ref: DecisionOptionRef, name: String): String =
    ref match
      case DecisionOptionRef.SiteSlot(site, _) =>
        s"Replace $name at ${presentation.siteLabel(site)}"
      case DecisionOptionRef.AdviserPlay(_, _, faceup) =>
        s"Replace $name, ${facing(faceup)}"
      case _: DecisionOptionRef.RevealedVision =>
        s"Replace $name as revealed Vision"
      case _ => s"Replace $name"

  /** A bury option's label: the card it buries, and where. */
  private def buryingLabel(ref: DecisionOptionRef, name: String): String =
    CardPlayProcedure.siteOf(ref).fold(s"Bury $name")(site =>
      s"Bury $name at ${presentation.siteLabel(site)}")

  private def facing(faceup: Boolean): String =
    if faceup then "faceup" else "facedown"

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

  /** The cards a decision is about, read from its own id.
    *
    * A card-play decision is the one question in the walker whose subject is
    * not among its options: `cardplay.place.*` offers card slots and
    * `cardplay.discard.*` site cards, and both name the card played only in
    * the id they were built with. The id is the tree's own
    * spelling of `WorldCardId` (`kind` then `value`), so this parses what the
    * procedure wrote rather than reaching into the tree for it.
    *
    * Not a field of the decision projection: `GameProjector` draws the
    * subject with the cards in the viewer's hand (`temporaryHandPreview`),
    * where a Search's kept card already is, so one card is never drawn as
    * two.
    *
    * A card that cannot be found, or that this viewer may not identify,
    * projects hidden rather than being dropped: unlike an option, a subject
    * carries no reference the client answers with, so showing a back is both
    * honest and useful. "Hidden" means the same redacted substitute
    * [[GamePresentationProjector.hiddenCard]] gives an unidentifiable board
    * slot -- a generic back, not the real id and name with a flag set over
    * them -- since `cardDetails` fills in the real catalog name and id
    * whatever `hidden` is passed as, and the client trusts `hidden` to mean
    * the rest of the row is safe to skip reading.
    */
  private[application] def subjectCards(ready: ReadyGame,
      viewer: Option[PlayerId], decisionId: String)
      : Vector[CardDetailsProjection] =
    val index = ready.game.cardIndex.toOption
    val prefixes = Vector("cardplay.place.", "cardplay.discard.",
      "cardplay.replaced.", "cardplay.bury.")
    prefixes.find(decisionId.startsWith).toVector.flatMap { prefix =>
      decisionId.stripPrefix(prefix).split("\\.", 2).toVector match
        case Vector("denizen", value) => Vector(DenizenId(value): CardId)
        case Vector("vision", value) => Vector(VisionId(value): CardId)
        case _ => Vector.empty[CardId]
    }.map(shown(ready, viewer, index, _))

  /** A card play's heading names its card or its site, and its Replace
    * options name the card they discard and the card played
    * ([[replacing]]); a game object's name is resolved here, not by the
    * engine. Every other decision keeps its own.
    */
  private def headed(ready: ReadyGame, viewer: Option[PlayerId],
      decisionId: String, projected: DecisionQueryProjection)
      : DecisionQueryProjection =
    val played =
      if decisionId.startsWith("cardplay.place.") then
        subjectCards(ready, viewer, decisionId).headOption.filterNot(_.hidden)
      else None
    val query = played.fold(projected)(replacing(projected, _))
    val heading =
      if decisionId.startsWith("cardplay.place.") then
        played.map(card => s"Play ${card.name}")
      else if decisionId.startsWith("cardplay.discard.") then
        followUpSite(ready, projected).map(site =>
          s"Discard more cards from ${presentation.siteLabel(site)}")
      else if decisionId.startsWith("cardplay.bury.") then
        followUpSite(ready, projected).map(site =>
          s"Bury a card at ${presentation.siteLabel(site)}")
      else None
    heading.fold(query)(text => query match
      case one: DecisionQueryProjection.ChooseOne =>
        one.copy(heading = Some(text))
      case many: DecisionQueryProjection.ChooseMany =>
        many.copy(heading = Some(text))
      case other => other)

  /** The site a card play's follow-up is about: the site holding the cards
    * it offers, its locked card among them. */
  private def followUpSite(ready: ReadyGame,
      query: DecisionQueryProjection): Option[SiteId] =
    val rows = query match
      case many: DecisionQueryProjection.ChooseMany => many.options
      case _ => Vector.empty
    rows.iterator.flatMap(row =>
      DecisionOptionRef.fromWire(row.kind, row.id).flatMap {
        case DecisionOptionRef.SiteRelicSlot(site, _) => Some(site)
        case _ => ready.game.current.map.sites.collectFirst {
          case (site, state)
              if state.denizens.exists(_.id.value == row.id) => site }
      }).nextOption()

  /** A card play's Replace options as the board's summary reads them:
    * "Discard {replaced}; play {played} here", with the facing after an
    * adviser play. A replaced card this viewer may not identify keeps the
    * option's own label, as does every option that replaces nothing.
    */
  private[application] def replacing(query: DecisionQueryProjection,
      played: CardDetailsProjection): DecisionQueryProjection =
    def worded(option: DecisionOptionProjection) = option.replaces
      .filterNot(_.hidden).fold(option)(replaced => option.copy(label =
        s"${if option.buries then "Bury" else "Discard"} ${replaced.name}; " +
          s"play ${played.name} here" +
          option.variant.fold("")(word => s", ${word.toLowerCase}")))
    query match
      case one: DecisionQueryProjection.ChooseOne =>
        one.copy(options = one.options.map(worded))
      case other => other

  /** An unavailable option's reason as player text: a builder's own words,
    * or the note of the power that hid it, worded as the Game Log words it.
    */
  private def reasonText(ready: ReadyGame, viewer: Option[PlayerId],
      reason: OptionReason): String = reason match
    case OptionReason.Written(text) => text
    case OptionReason.Noted(power, note) =>
      reasons.text(power, note, ready, viewer).getOrElse("Not on offer now")
    case OptionReason.Askable(power, note, _, _) =>
      reasons.text(power, note, ready, viewer).getOrElse("Not on offer now")

  /** A card as its viewer may see it: its face when they may identify it,
    * else the redacted back [[GamePresentationProjector.hiddenCard]] gives an
    * unidentifiable board slot. */
  private def shown(ready: ReadyGame, viewer: Option[PlayerId],
      index: Option[CardIndex], id: CardId): CardDetailsProjection =
    index.flatMap(_.get(id)) match
      case Some(located) =>
        val orientation = orientationOf(located.state)
        if presentation.identifiesCard(ready, viewer, id, orientation,
            located.location.container) then
          located.state match
            case Some(edifice: EdificeState) =>
              presentation.edificeCardDetails(edifice)
            case _ => presentation.cardDetails(id, orientation, hidden = false)
        else presentation.hiddenCard(presentation.cardKind(id))
      case None => presentation.hiddenCard(presentation.cardKind(id))

  /** Every answer already recorded at `decisionId`, in answer order, described
    * the way the decision's own options are. See `answeredOptions`' doc on the
    * projection for why a button is dropped rather than labelled.
    */
  private def answeredOptions(ready: ReadyGame, viewer: Option[PlayerId],
      pending: PendingTree, decisionId: String)
      : Vector[DecisionOptionProjection] =
    val index = ready.game.cardIndex.toOption
    pending.answered.collect {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    }.flatMap(DecisionOption.forRef)
      .flatMap(optionProjection(ready, viewer, index, _))

  private def orientationOf(state: Option[CardState]): Option[Orientation] =
    GamePresentationProjector.orientationOf(state)

  /** The roll the parked decision declares it is about, with the faces
    * accumulated in that pool so far.
    *
    * The procedure says which pool and why (`WalkerRollFeedback`); this reads
    * the outcome and spells the faces. `RecoverRules` and `ActionRef.Recover`
    * no longer appear here, which is the point: the last action-specific
    * expression in this file moved into the registry beside the procedure's
    * other declarations.
    */
  private def rollOutcome(procedure: ProcedureRef, ready: ReadyGame,
      actor: PlayerId, decisionId: String)
      : Option[WalkerRollOutcomeProjection] =
    WalkerProcedureRegistry.rollFeedback(procedure, catalog, ready, actor,
      decisionId).map { feedback =>
      val outcome = ready.game.current.rollOutcomes.get(feedback.pool)
      WalkerRollOutcomeProjection(
        pool = feedback.pool.value,
        faces = outcome.fold(Vector.empty[DieFace])(_.faces)
          .flatMap(WalkerDecisionProjector.faceName),
        score = outcome.fold(0)(_.score),
        target = feedback.target,
        detail = feedback.detail)
    }

private[application] object WalkerDecisionProjector:
  /** The decision wire's face vocabulary. It duplicates
    * `oathdigital.protocol.projection.DieFace`'s keys, and could read them
    * instead -- that package is not one this layer is forbidden
    * (`BackendArchitectureSuite` forbids only `server`, `persistence` and
    * `serialization`, and this file already imports `projection`). It stays
    * separate because the decision wire was out of the typed log slice's
    * scope (2026-09-25-decision-form-tag-separation-decision.md), and a
    * test in `WalkerDecisionProjectorSuite` holds the two spellings
    * together per die. `oathdigital.model.DieFace` is open, so a face
    * family neither pool rolls spells as nothing rather than as a guess.
    */
  private[application] def faceName(value: DieFace): Vector[String] =
    value match
      case DefenseDieFace.Blank => Vector("blank")
      case DefenseDieFace.OneShield => Vector("one-shield")
      case DefenseDieFace.TwoShields => Vector("two-shields")
      case DefenseDieFace.Doubler => Vector("doubler")
      case AttackDieFace.HollowSword => Vector("hollow-sword")
      case AttackDieFace.OneSword => Vector("one-sword")
      case AttackDieFace.TwoSwordsSkull => Vector("two-swords-skull")
      case _ => Vector.empty

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
      pending: PendingTree, powers: WalkerPowers, awaited: PlayerId,
      owners: Set[PlayerId])
