package oathdigital.frontend

import org.scalajs.dom

/** The route from a parked decision to the one surface a viewer sees it on.
  *
  * A `WalkerDecisionState` arrives as strings: an action, a kind, a decision
  * id, and a query whose `form` names the shape of answer it wants. Which
  * panel or board control answers it used to be decided inside each panel,
  * six times over, each re-reading the projection and re-testing the form.
  * This object reads the projection once and says which surface, if any,
  * shows the decision. The panels take a value the route has already
  * proven of the right form.
  *
  * The words are `CONTEXT.md`'s: a parked decision has one form; a viewer
  * sees at most one surface for it, plus a waiting notice when it awaits
  * someone else. The projector guarantees the "at most one": a viewer
  * receives either `walkerDecision` or `walkerWaiting`, never both
  * (`WalkerDecisionProjector.project`/`.waiting` split on ownership).
  */
private[frontend] object ParkedDecision:
  import ServerUiSupport.{ViewerPresentation, text}

  /** The decision ids the route recognises by name. `kind` alone cannot
    * tell Recover's two decide parks apart (both are `"decide"`); only the
    * id does. The two Recover ids mirror `RecoverProcedure.choiceDecisionId`
    * and `.relicDecisionId`, and the prefix mirrors
    * `SetupProcedure.pawnDecisionId`'s (`setup.pawn-placement.<player>`), as
    * plain strings: those objects live in the JVM-only application sources
    * the frontend cannot depend on, and a `decisionId` already rides the
    * wire as an uninterpreted string on every walker command (see
    * `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`).
    */
  private[frontend] val recoverChoiceDecisionId = "recover.choice"
  private[frontend] val recoverRelicDecisionId = "recover.relic"
  private val pawnPlacementDecisionIdPrefix = "setup.pawn-placement."

  /** A query's form, parsed once from the wire string. `Unknown` keeps the
    * raw spelling: a form this client has no surface for renders nothing
    * rather than something wrong, and the value can still say what arrived.
    */
  enum DecisionForm:
    case ChooseOne, ChooseMany, ChooseAmount, Partition, Distribute, Negotiate
    case Unknown(raw: String)

  object DecisionForm:
    def parse(raw: String): DecisionForm = raw match
      case "choose-one" => ChooseOne
      case "choose-many" => ChooseMany
      case "choose-amount" => ChooseAmount
      case "partition" => Partition
      case "distribute" => Distribute
      case "negotiate" => Negotiate
      case other => Unknown(other)

  /** The two forms the selection panel answers: toggles for a choose-many,
    * a dropdown for a choose-amount.
    */
  type SelectionForm =
    DecisionForm.ChooseMany.type | DecisionForm.ChooseAmount.type

  /** Which of its parks Recover's panel is at. */
  enum RecoverStep:
    /** A Roll park asks nothing, so it has no query behind it -- which is
      * why its heading is the one Recover string the panel still writes.
      */
    case Roll(pool: String)
    /** The projected continue/stop query: its options in declared order,
      * and the heading the action declared above them.
      */
    case Choice(query: DecisionQueryState)
    /** The projected relic query, the same way. */
    case Relic(query: DecisionQueryState)

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
    case ChooseOne(decision: WalkerDecisionState, query: DecisionQueryState)
    case Partition(decision: WalkerDecisionState, query: DecisionQueryState)
    case Distribute(decision: WalkerDecisionState, query: DecisionQueryState)
    case Selection(decision: WalkerDecisionState, query: DecisionQueryState,
        form: SelectionForm)
    /** The deal summary every viewer sees; `editor` is the decision id and
      * the editable terms for the one viewer who may answer, `None` for an
      * observer of the parked deal or a viewer the deal is waiting on.
      */
    case Negotiate(deal: NegotiationDealState,
        editor: Option[(String, NegotiationEditingState)])
    /** A choose-one answered by clicking a site on the board rather than a
      * button in the action pane. With `confirm`, the click only drafts the
      * pick and the pane's Confirm button submits it; without, the click
      * submits at once. Setup's pawn placement is the one decision routed
      * here today.
      */
    case Board(decision: WalkerDecisionState, query: DecisionQueryState,
        confirm: Boolean)

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
    * nothing elsewhere. The board surface is drawn twice over: its sites by
    * `WorldBoardRenderer`, which takes the `Board` case itself, and its
    * heading and Confirm by the pane here.
    *
    * Two panels read past the decision: Recover gates buying dice on the
    * acting player's supply, and the choose-one panel names a player option
    * by its display name. Both are the projection's, so it rides along.
    */
  def render(value: GameProjection, routed: Routed, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    routed.surface.foreach:
      case surface: Surface.Recover => WalkerPanelSupport.renderRecoverPanel(
        surface, value, canControl, panel, ui)
      case surface: Surface.ChooseOne => WalkerPanelSupport.renderChooseOnePanel(
        surface, value, canControl, panel, ui)
      case surface: Surface.Partition => WalkerPanelSupport.renderPartitionPanel(
        surface, canControl, panel, ui)
      case surface: Surface.Distribute =>
        DistributePanelRenderer.render(surface, canControl, panel, ui)
      case surface: Surface.Negotiate =>
        NegotiationDealPanel.render(surface, canControl, panel, ui)
      case surface: Surface.Selection =>
        WalkerSelectionPanels.render(surface, canControl, panel, ui)
      case surface: Surface.Board => WalkerPanelSupport.renderBoardPanel(
        surface, canControl, panel, ui)
    routed.notice.foreach(notice =>
      panel.appendChild(text("p", "walker-waiting", notice)))

  private def surface(value: GameProjection,
      showGameplayControls: Boolean): Option[Surface] =
    value.walkerDecision
      .flatMap(decision => decisionSurface(decision, showGameplayControls))
      .orElse(value.walkerWaiting.flatMap(_.deal)
        .map(Surface.Negotiate(_, None)))

  /** The surface for a decision this viewer owns. Everything but a
    * negotiation's summary is a control, so it is shown only to a viewer
    * with gameplay controls; the summary is public and its editor is not.
    */
  private def decisionSurface(decision: WalkerDecisionState,
      showGameplayControls: Boolean): Option[Surface] =
    recoverStep(decision).map(Surface.Recover(decision, _))
      .orElse(decision.query.flatMap(query =>
        formSurface(decision, query, showGameplayControls)))
      .filter:
        case _: Surface.Negotiate => true
        case _ => showGameplayControls

  /** Recover keeps its own panel for its richer copy, so its parks are
    * claimed before the form is read. A park with nothing to render -- a
    * roll with no projected pool, a decide park whose query the engine
    * suppressed because an option could not be presented -- is `None`:
    * there is no answer the client could safely build.
    */
  private def recoverStep(decision: WalkerDecisionState)
      : Option[RecoverStep] =
    if decision.action != "recover" then None
    else decision.kind match
      case "roll" => decision.pool.map(RecoverStep.Roll.apply)
      case "decide" if decision.decisionId == recoverChoiceDecisionId =>
        chooseOneQuery(decision).map(RecoverStep.Choice.apply)
      case "decide" if decision.decisionId == recoverRelicDecisionId =>
        chooseOneQuery(decision).map(RecoverStep.Relic.apply)
      case _ => None

  private def chooseOneQuery(decision: WalkerDecisionState)
      : Option[DecisionQueryState] =
    decision.query.filter(query =>
      DecisionForm.parse(query.form) == DecisionForm.ChooseOne)

  private def formSurface(decision: WalkerDecisionState,
      query: DecisionQueryState, showGameplayControls: Boolean)
      : Option[Surface] =
    DecisionForm.parse(query.form) match
      // Confirmed from the pane: a pawn is placed once a game and cannot be
      // moved back, so one click on a crowded board must not commit it.
      case DecisionForm.ChooseOne
          if decision.decisionId.startsWith(pawnPlacementDecisionIdPrefix) =>
        Some(Surface.Board(decision, query, confirm = true))
      // A Recover choose-one at a decision id Recover's panel does not
      // know is not handed to the generic panel either: there is no
      // answer this client could safely build for it.
      case DecisionForm.ChooseOne
          if decision.action == "recover" || decision.kind != "decide" =>
        None
      case DecisionForm.ChooseOne => Some(Surface.ChooseOne(decision, query))
      case DecisionForm.Partition => Some(Surface.Partition(decision, query))
      case DecisionForm.Distribute => Some(Surface.Distribute(decision, query))
      case DecisionForm.ChooseMany =>
        Some(Surface.Selection(decision, query, DecisionForm.ChooseMany))
      case DecisionForm.ChooseAmount =>
        Some(Surface.Selection(decision, query, DecisionForm.ChooseAmount))
      case DecisionForm.Negotiate => query.deal.map(deal =>
        Surface.Negotiate(deal, deal.editing
          .filter(_ => showGameplayControls).map(decision.decisionId -> _)))
      case DecisionForm.Unknown(_) => None

  /** The public line shown to every viewer a parked decision is NOT waiting
    * on: who it awaits, and the question's heading when it has one -- `None`
    * for a parked Roll, which asks nothing. `None` here means either nothing
    * is parked or this viewer is the one it awaits, in which case the
    * surface above shows the decision itself instead.
    */
  private def waitingNotice(value: GameProjection): Option[String] =
    value.walkerWaiting.map { waiting =>
      val name = value.players.find(_.playerId == waiting.playerId)
        .map(_.displayName).getOrElse(waiting.playerId)
      waiting.heading.fold(s"Waiting for $name")(heading =>
        s"Waiting for $name: $heading")
    }
