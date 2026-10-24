package oathdigital.frontend

import org.scalajs.dom

/** The route from a parked decision to the one surface a viewer sees it on.
  *
  * A `WalkerDecisionState` arrives as an action, a decision id and a
  * typed query: the query's own case IS its form. Which panel or board
  * control answers it used to be decided inside each panel, six times over,
  * each re-reading the projection and re-testing the form. This object reads
  * the projection once and says which surface, if any, shows the decision.
  * Each surface carries the query narrowed to the form it answers, so a
  * panel cannot be handed a question of the wrong shape.
  *
  * The words are `CONTEXT.md`'s: a parked decision has one form; a viewer
  * sees at most one surface for it, plus a waiting notice when it awaits
  * someone else. The projector guarantees the "at most one": a viewer
  * receives either `walkerDecision` or `walkerWaiting`, never both
  * (`WalkerDecisionProjector.project`/`.waiting` split on ownership).
  */
private[frontend] object ParkedDecision:
  import ServerUiSupport.{ViewerPresentation, text}

  /** The decision ids the route recognises by name. Only the id tells
    * Recover's two parks apart. The two Recover ids mirror `RecoverProcedure.choiceDecisionId`
    * and `.relicDecisionId`, as plain strings: those objects live in the JVM-only application sources
    * the frontend cannot depend on, and a `decisionId` already rides the
    * wire as an uninterpreted string on every walker command (see
    * `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`).
    */
  private[frontend] val recoverChoiceDecisionId = "recover.choice"
  private[frontend] val recoverRelicDecisionId = "recover.relic"
  private[frontend] val recoverModifiersDecisionId = "recover.modifiers"
  private[frontend] val recoverRerollDecisionPrefix = "recover.reroll."

  /** The two forms the selection panel answers: toggles for a choose-many,
    * a dropdown for a choose-amount. The query IS the form now, so the
    * surface carries one value rather than a query and a tag that could
    * disagree with it.
    */
  type SelectionForm =
    DecisionQueryState.ChooseMany | DecisionQueryState.ChooseAmount

  /** The two forms a board decision answers: a pick of one, or of several. */
  type BoardForm = DecisionQueryState.ChooseOne | DecisionQueryState.ChooseMany

  /** Which of its parks Recover's panel is at. */
  enum RecoverStep:
    /** The projected continue/stop query: its options in declared order,
      * and the heading the action declared above them.
      */
    case Choice(query: DecisionQueryState.ChooseOne)
    /** The projected relic query, the same way. */
    case Relic(query: DecisionQueryState.ChooseOne)

  /** Where a viewer sees the parked decision. Every case carries the whole
    * decision, so an attribute of any parked decision (its roll feedback,
    * its subject cards) is read by the surface that shows it, and a change
    * to what a decision carries edits one adapter and no route.
    */
  enum Surface:
    /** Recover's own panel, at whichever of its parks the walker sits. */
    case Recover(decision: WalkerDecisionState, step: RecoverStep)
    /** The generic choose-one button panel: a decide park no
      * action-specific surface claims.
      */
    case ChooseOne(decision: WalkerDecisionState,
        query: DecisionQueryState.ChooseOne)
    /** A phase menu: the grouped panel (walker-driven game loop design,
      * slice 4). */
    case Menu(decision: WalkerDecisionState,
        query: DecisionQueryState.ChooseOne)
    case Partition(decision: WalkerDecisionState,
        query: DecisionQueryState.Partition)
    case Distribute(decision: WalkerDecisionState,
        query: DecisionQueryState.Distribute)
    case Selection(decision: WalkerDecisionState, query: SelectionForm)
    /** Cards to look at and a Done button (catalog batch 2, N5). */
    case Inspect(decision: WalkerDecisionState,
        query: DecisionQueryState.Inspect)
    /** The deal summary every viewer sees; `editor` is the decision id and
      * the editable terms for the one viewer who may answer, `None` for an
      * observer of the parked deal or a viewer the deal is waiting on.
      */
    case Negotiate(deal: NegotiationDealState,
        editor: Option[(String, NegotiationEditingState)])
    /** A choose-one or choose-many answered on the table (board decisions
      * design): its options are drawn where they lie, by `BoardOptions`,
      * and the action pane states the selection and confirms it. Every
      * board-presented decision with an option on the table is routed here.
      */
    case BoardDecision(decision: WalkerDecisionState, query: BoardForm)

  /** What one render of one viewer's projection shows for the parked
    * decision: the surface, if this viewer sees one, and the public notice
    * when the decision awaits someone else.
    */
  final case class Routed(surface: Option[Surface], notice: Option[String])

  def route(value: GameProjection, presentation: ViewerPresentation): Routed =
    Routed(surface(value, presentation.showGameplayControls),
      waitingNotice(value))

  /** Draws the routed surface into the action pane, then the notice, in
    * that order. One exhaustive match: a new surface is a case here and
    * nothing elsewhere. A board decision is drawn twice over: its options by
    * the table's renderers through `BoardOptions`, its prompt and Confirm by
    * the pane here.
    *
    * Two panels read past the decision: Recover gates buying dice on the
    * acting player's supply, and the choose-one panel names a player option
    * by its display name. Both are the projection's, so it rides along.
    */
  def render(value: GameProjection, presentation: ViewerPresentation,
      routed: Routed, canControl: Boolean, panel: dom.Element,
      drafts: SessionDrafts, controls: TableControls): Unit =
    routed.surface.foreach:
      case surface: Surface.Recover => WalkerPanelSupport.renderRecoverPanel(
        surface, value, canControl, panel, controls)
      case surface: Surface.ChooseOne => WalkerPanelSupport.renderChooseOnePanel(
        surface, value, canControl, panel, controls)
      case surface: Surface.Menu => MenuPanel.render(surface, canControl,
        panel, controls)
      case surface: Surface.Partition => WalkerPanelSupport.renderPartitionPanel(
        surface, drafts.partition, presentation.playerId, canControl, panel,
        controls)
      case surface: Surface.Distribute => DistributePanelRenderer.render(
        surface, drafts.distribute, canControl, panel, controls)
      case surface: Surface.Negotiate => NegotiationDealPanel.render(
        surface, presentation.playerId, canControl, panel, controls)
      case surface: Surface.Selection => WalkerSelectionPanels.render(
        surface, drafts.selection, canControl, panel, controls)
      case surface: Surface.Inspect => InspectPanel.render(
        surface, canControl, panel, controls)
      case surface: Surface.BoardDecision => BoardDecisionPanel.render(
        surface, value, drafts, canControl, panel, controls)
    ConsentPanels.render(value, canControl, panel, controls)
    routed.notice.foreach(notice =>
      panel.appendChild(text("p", "walker-waiting", notice)))

  private def surface(value: GameProjection,
      showGameplayControls: Boolean): Option[Surface] =
    value.walkerDecision
      .flatMap(decision => decisionSurface(decision, value,
        showGameplayControls))
      .orElse(value.walkerWaiting.flatMap(_.deal)
        .map(Surface.Negotiate(_, None)))

  /** The surface for a decision this viewer owns. Everything but a
    * negotiation's summary is a control, so it is shown only to a viewer
    * with gameplay controls; the summary is public and its editor is not.
    */
  private def decisionSurface(decision: WalkerDecisionState,
      value: GameProjection, showGameplayControls: Boolean)
      : Option[Surface] =
    recoverStep(decision).map(Surface.Recover(decision, _))
      .orElse(decision.query.flatMap(query =>
        formSurface(decision, query, value, showGameplayControls)))
      .filter:
        case _: Surface.Negotiate => true
        case _ => showGameplayControls

  /** Recover keeps its own panel for its richer copy, so its parks are
    * claimed before the form is read. A park with nothing to render -- a
    * decide park whose query the engine suppressed because an option could
    * not be presented -- is `None`: there is no answer the client could
    * safely build.
    */
  private def recoverStep(decision: WalkerDecisionState)
      : Option[RecoverStep] =
    if decision.action != "recover" then None
    else if decision.decisionId == recoverChoiceDecisionId then
      chooseOneQuery(decision).map(RecoverStep.Choice.apply)
    else if decision.decisionId == recoverRelicDecisionId then
      chooseOneQuery(decision).map(RecoverStep.Relic.apply)
    else None

  /** A Recover park only Recover's panel may answer: every one but its
    * modifier decision, which the board draws as any action's is, and its
    * reroll choices (`RerollChoice.decisionId` of the Recover pool), which
    * the generic panel answers as any roll's are. */
  private def recoverPanelOnly(decision: WalkerDecisionState): Boolean =
    decision.action == "recover" &&
      decision.decisionId != recoverModifiersDecisionId &&
      !decision.decisionId.startsWith(recoverRerollDecisionPrefix)

  private def chooseOneQuery(decision: WalkerDecisionState)
      : Option[DecisionQueryState.ChooseOne] =
    decision.query.collect { case one: DecisionQueryState.ChooseOne => one }

  private def formSurface(decision: WalkerDecisionState,
      query: DecisionQueryState, value: GameProjection,
      showGameplayControls: Boolean): Option[Surface] =
    query match
      // A phase menu: one grouped panel for Wake, Act and Rest.
      case one: DecisionQueryState.ChooseOne
          if decision.decisionId == MenuPanel.decisionId =>
        Some(Surface.Menu(decision, one))
      // On the table: the decision says it is answered there, and the table
      // draws at least one of its options.
      case one: DecisionQueryState.ChooseOne if !recoverPanelOnly(decision) &&
          onTable(decision, one.options, value) =>
        Some(Surface.BoardDecision(decision, one))
      case many: DecisionQueryState.ChooseMany
          if onTable(decision, many.options, value) =>
        Some(Surface.BoardDecision(decision, many))
      // A Recover choose-one at a decision id Recover's panel does not
      // know is not handed to the generic panel either: there is no
      // answer this client could safely build for it.
      case _: DecisionQueryState.ChooseOne if recoverPanelOnly(decision) =>
        None
      case one: DecisionQueryState.ChooseOne =>
        Some(Surface.ChooseOne(decision, one))
      case partition: DecisionQueryState.Partition =>
        Some(Surface.Partition(decision, partition))
      case distribute: DecisionQueryState.Distribute =>
        Some(Surface.Distribute(decision, distribute))
      case many: DecisionQueryState.ChooseMany =>
        Some(Surface.Selection(decision, many))
      case amount: DecisionQueryState.ChooseAmount =>
        Some(Surface.Selection(decision, amount))
      case inspect: DecisionQueryState.Inspect =>
        Some(Surface.Inspect(decision, inspect))
      case negotiate: DecisionQueryState.Negotiate =>
        Some(Surface.Negotiate(negotiate.deal, negotiate.deal.editing
          .filter(_ => showGameplayControls).map(decision.decisionId -> _)))

  private def onTable(decision: WalkerDecisionState,
      options: Vector[DecisionOptionState], value: GameProjection): Boolean =
    decision.presentation == "board" &&
      options.exists(BoardHomes.has(value, _))

  /** The line shown to a requester waiting for consent. A walker wait has
    * none: the status line above already names who the decision awaits and
    * the question it asks.
    */
  private def waitingNotice(value: GameProjection): Option[String] =
    value.consentWaiting.map(ConsentPanels.waiting(value, _))
