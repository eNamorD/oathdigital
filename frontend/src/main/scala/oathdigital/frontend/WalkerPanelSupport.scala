package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import org.scalajs.dom

/** The client half of the generic walker decision contract, extracted from
  * `ServerUiSupport` (which sat at its line bound) when Task 4 re-sourced
  * both panels from the projected query.
  *
  * Everything here reads `WalkerDecisionState.query` -- the projected,
  * power-transformed `Decide` -- rather than deriving what to offer. So the
  * option set a panel renders and the option set the engine accepts are the
  * same set, and a power that changes one changes the other. What the
  * renderers still supply is interpretation of a KNOWN option: Recover's
  * Continue button keeps its supply-aware label and disabled state, which is
  * a richer interaction over an option the engine declared, not an
  * independent judgement about who is eligible.
  */
private[frontend] object WalkerPanelSupport {
  import ServerUiSupport.{ViewerPresentation, button, element, text}

  /** Which control the panel should render for a parked walker decision.
    * `WalkerDecisionState.kind` alone cannot tell the two "decide" parks
    * apart (both `"recover.choice"` and `"recover.relic"` share it) -- only
    * `decisionId` does, so that comparison lives here rather than being
    * re-derived at each call site. The two decision id literals mirror
    * `RecoverProcedure.choiceDecisionId`/`.relicDecisionId`
    * (`src/main/scala/oathdigital/gameplay/actions/recover/
    * RecoverProcedure.scala`) as plain strings: that object lives in the
    * JVM-only application sources the frontend cannot depend on, and a
    * `decisionId` already rides the wire as an uninterpreted string on
    * every walker/decision command (see
    * `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`).
    */
  private[frontend] val recoverChoiceDecisionId = "recover.choice"
  private[frontend] val recoverRelicDecisionId = "recover.relic"

  /** Mirrors `SetupProcedure.pawnDecisionId`'s prefix (`setup.pawn-
    * placement.<player>`) the same way the two ids above mirror Recover's --
    * a plain string, since the frontend cannot depend on the JVM-only
    * application sources that declare it.
    */
  private[frontend] val pawnPlacementDecisionIdPrefix = "setup.pawn-placement."

  /** The two button keys Recover's continue/stop query declares. Read to
    * decide which projected option gets the supply-aware treatment below --
    * never to decide whether that option exists.
    */
  private[frontend] val continueOptionKey = "continue"
  private[frontend] val stopOptionKey = "stop"

  private[frontend] sealed trait RecoverWalkerStep
  private[frontend] object RecoverWalkerStep {
    /** A Roll park asks nothing, so it has no query behind it -- and that is
      * why its heading is the one Recover string still written here.
      */
    final case class Roll(pool: String) extends RecoverWalkerStep
    /** The projected continue/stop query: its options in declared order,
      * and (Task 5b) the heading the action declared above them.
      */
    final case class Choice(query: DecisionQueryState)
        extends RecoverWalkerStep
    /** The projected relic query, the same way. */
    final case class Relic(query: DecisionQueryState)
        extends RecoverWalkerStep
  }

  /** A decide park's projected choose-one query, or `None` when the
    * projection carries no query -- which the engine does on purpose when an
    * option could not be presented. Rendering nothing is then correct: there
    * is no answer the client could safely build.
    */
  private[frontend] def chooseOneQuery(decision: WalkerDecisionState)
      : Option[DecisionQueryState] =
    decision.query.filter(_.form == "choose-one")

  private[frontend] def recoverWalkerStep(decision: WalkerDecisionState)
      : Option[RecoverWalkerStep] =
    if (decision.action != "recover") None
    else decision.kind match {
      case "roll" => decision.pool.map(RecoverWalkerStep.Roll)
      case "decide" if decision.decisionId == recoverChoiceDecisionId =>
        chooseOneQuery(decision).map(RecoverWalkerStep.Choice)
      case "decide" if decision.decisionId == recoverRelicDecisionId =>
        chooseOneQuery(decision).map(RecoverWalkerStep.Relic)
      case _ => None
    }

  /** The answer for any projected choose-one option, built from the option's
    * own `kind`/`id` pair. One builder serves Recover's buttons and its
    * relics because a generic `ChooseOneWire` is all either needs -- the
    * projection carries the identity, so the client never has to know which
    * variant it is holding.
    */
  private[frontend] def resolveChooseOneCommand(decision: WalkerDecisionState,
      option: DecisionOptionState): GameCommand.ResolveWalker =
    GameCommand.ResolveWalker(decision.decisionId,
      DecisionAnswerWire.ChooseOneWire(option.kind, option.id))

  /** Renders the parked Recover's accumulated roll feedback (I5) -- the
    * dice faces rolled so far, the derived score, and the site's Recover
    * difficulty -- the same information the legacy (deleted)
    * `RecoverProjection`-backed panel showed, now sourced from
    * `WalkerDecisionState.rollOutcome`. Before any roll `faces` is empty:
    * the difficulty is still worth showing so the player knows the target
    * before rolling.
    */
  private[frontend] def rollOutcomeSummary(outcome: WalkerRollOutcomeState): String =
    if (outcome.faces.isEmpty)
      s"Need ${outcome.difficulty} shields to succeed."
    else
      s"Rolled ${outcome.faces.mkString(", ")} -- ${outcome.score} shields " +
        s"so far (need ${outcome.difficulty})."

  /** The public line shown to every viewer a parked walker position is NOT
    * waiting on (Task 5): who it awaits, and the question's heading when it
    * has one -- `None` for a parked Roll, which asks nothing. `None` here
    * (no `walkerWaiting` at all) means either nothing is parked or this
    * viewer IS the one it awaits, in which case `renderRecoverPanel`/
    * `renderPartitionPanel` above render the decision itself instead.
    */
  private[frontend] def waitingNotice(value: GameProjection): Option[String] =
    value.walkerWaiting.map { waiting =>
      val name = value.players.find(_.playerId == waiting.playerId)
        .map(_.displayName).getOrElse(waiting.playerId)
      waiting.heading.fold(s"Waiting for $name")(heading =>
        s"Waiting for $name: $heading")
    }

  private[frontend] def renderWaitingNotice(value: GameProjection,
      panel: dom.Element): Unit =
    waitingNotice(value).foreach(notice =>
      panel.appendChild(text("p", "walker-waiting", notice)))

  /** Renders the Recover panel for whichever of the parks
    * (`recoverWalkerStep`) the walker is at. Shows `rollOutcomeSummary`
    * (I5) above each park's controls, and gates buying more dice on the
    * player actually having supply -- as the legacy (deleted) Recover panel
    * did. Recover rolls as the walker walks, so the continue answer both
    * buys the dice and throws them and its label says so; the Roll park
    * below it is reached only if a power folds a parked roll into the tree,
    * which is why the panel still knows how to answer one.
    */
  private[frontend] def renderRecoverPanel(value: GameProjection,
      presentation: ViewerPresentation, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit = {
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => recoverWalkerStep(decision).map(decision -> _))
        .foreach {
      case (decision, RecoverWalkerStep.Roll(pool)) =>
        // The one heading still written here. A Roll park is not a `Decide`
        // -- it asks no question, carries a synthetic decision id and has no
        // query behind it -- so there is nothing to read a title from, and
        // inventing a query for a node that asks nothing would be worse
        // than this literal.
        panel.appendChild(text("h2", "", "Recover"))
        rollFeedback(decision, panel)
        val roll = button("Roll the dice", "recover-roll")
        roll.disabled = !canControl
        roll.onclick = _ => ui.submitCommand(GameCommand.RollWalker(pool))
        panel.appendChild(roll)
      case (decision, RecoverWalkerStep.Choice(query)) =>
        panel.appendChild(text("h2", "", decisionHeading(query)))
        rollFeedback(decision, panel)
        val hasSupply = value.activePlayerResources.exists(_.supply >= 1)
        // One control per PROJECTED option, so a power that drops Continue
        // or adds a third choice changes this panel with no edit here. The
        // two known keys keep their richer copy and the supply gate; any
        // other option falls back to the query's own declared label.
        query.options.foreach { option =>
          val spendsSupply = option.id == continueOptionKey
          val (label, className) =
            if (spendsSupply) ("Roll two more dice (1 Supply)", "recover-add")
            else if (option.id == stopOptionKey) ("Stop Recover", "recover-stop")
            else (option.label, "recover-choice")
          val control = button(label, className)
          control.disabled = !canControl || (spendsSupply && !hasSupply)
          control.onclick = _ => ui.submitCommand(
            resolveChooseOneCommand(decision, option))
          panel.appendChild(control)
        }
      case (decision, RecoverWalkerStep.Relic(query)) =>
        panel.appendChild(text("h2", "", decisionHeading(query)))
        rollFeedback(decision, panel)
        // A relic at the site the actor stands on is one they can read, so
        // the option is the card itself. The button takes it; a click on the
        // card still opens the inspector, as everywhere else.
        val relics = element("div", "card-choices")
        query.options.foreach { option =>
          val choice = element("div", "card-choice")
          option.card.foreach(card => choice.appendChild(
            CardFace.render(card.copy(orientation = Some("face-up")))))
          val choose = button("Take facedown", "recover-relic-choice")
          choose.setAttribute("aria-label", s"Take ${option.label} facedown")
          choose.disabled = !canControl
          choose.onclick = _ => ui.submitCommand(
            resolveChooseOneCommand(decision, option))
          choice.appendChild(choose)
          relics.appendChild(choice)
        }
        panel.appendChild(relics)
    }
  }

  /** A choose-one decision no action-specific panel claims. Recover keeps its
    * own panel for its richer copy; Setup's pawn placement is answered by
    * clicking the site directly on the board (`WorldBoardRenderer.world`,
    * via `pawnPlacementStep`) instead of a button list; everything else is
    * answered here, from the projected options alone.
    */
  private[frontend] def chooseOneStep(decision: WalkerDecisionState)
      : Option[DecisionQueryState] =
    if (decision.action == "recover" || decision.kind != "decide" ||
        decision.decisionId.startsWith(pawnPlacementDecisionIdPrefix)) None
    else chooseOneQuery(decision)

  /** The pawn-placement Decide's own choose-one query, or `None` for any
    * other decision -- the board renderer's counterpart to `chooseOneStep`
    * above, keyed off the same decision id prefix.
    */
  private[frontend] def pawnPlacementStep(decision: WalkerDecisionState)
      : Option[DecisionQueryState] =
    if (decision.decisionId.startsWith(pawnPlacementDecisionIdPrefix))
      chooseOneQuery(decision)
    else None

  private[frontend] def renderChooseOnePanel(value: GameProjection,
      presentation: ViewerPresentation, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
      .flatMap(decision => chooseOneStep(decision).map(decision -> _))
      .foreach { case (decision, query) =>
        panel.appendChild(text("h2", "", decisionHeading(query)))
        query.options.foreach { option =>
          val label = if (option.kind == "player")
            value.players.find(_.playerId == option.id).map(_.displayName)
              .getOrElse(option.label)
          else option.label
          val choose = button(label, "walker-choice")
          // A favor bank is named by its suit, and a suit is read as its
          // symbol everywhere else on the table.
          if (option.kind == "favor-bank")
            choose.insertBefore(RulesTextRenderer.glyph(s"suit-${option.id}"),
              choose.firstChild)
          choose.disabled = !canControl
          choose.onclick = _ => ui.submitCommand(
            resolveChooseOneCommand(decision, option))
          panel.appendChild(choose)
          if (option.details.nonEmpty) panel.appendChild(text("p",
            "walker-choice-details", option.details.mkString(" · ")))
        }
      }

  /** The faces as the symbols printed on them, with the sentence they used
    * to be written as kept for a reader who cannot see the symbols.
    */
  private def rollFeedback(decision: WalkerDecisionState,
      panel: dom.Element): Unit =
    decision.rollOutcome.foreach { outcome =>
      val line = element("p", "recover-roll-outcome")
      line.setAttribute("aria-label", rollOutcomeSummary(outcome))
      if (outcome.faces.isEmpty)
        line.appendChild(dom.document.createTextNode(
          s"Need ${outcome.difficulty} shields to succeed."))
      else {
        line.appendChild(dom.document.createTextNode("Rolled "))
        line.appendChild(DieFace.roll(outcome.faces))
        line.appendChild(dom.document.createTextNode(
          s" — ${outcome.score} shields so far (need ${outcome.difficulty})."))
      }
      panel.appendChild(line)
    }

  /** What a parked decision's panel calls itself, and what the control that
    * submits a partition is called.
    *
    * Task 5b: both come off the query. They used to branch on
    * `decision.action` to produce Forge's copy, which was the last
    * action-shaped string in a panel whose interaction had already stopped
    * being Forge-specific -- so the panel recognised the decisions it had
    * been written for and had nothing to say about any other. Now the
    * action that declares the question declares what to call it, and these
    * two read it the same way every other projected field is read.
    *
    * The fallbacks are the whole of what this layer still authors: generic
    * copy for a query that declares none, so a panel is untitled rather
    * than unusable. Never action-specific copy, and never a lookup table
    * that would grow one entry per action.
    */
  private[frontend] def decisionHeading(query: DecisionQueryState): String =
    query.heading.getOrElse("Resolve decision")

  private[frontend] def partitionConfirmLabel(query: DecisionQueryState)
      : String = query.confirmLabel.getOrElse("Confirm")

  /** A query that keeps exactly one thing says which one on its button.
    *
    * Read off the section's cap rather than its name: a section bounded at
    * one, holding one, is the answer in miniature, so the button can name
    * it -- "Keep Old Oak" -- instead of a generic confirmation. Its label
    * supplies the verb, so nothing here knows what a Keep is. Any other
    * shape falls back to what the query calls its own confirmation.
    */
  private[frontend] def partitionConfirmLabel(query: DecisionQueryState,
      draft: WalkerPartitionDraft): String =
    query.sections.filter(_.maxAllowed.contains(1))
      .flatMap(section => draft.optionsIn(section.key) match {
        case Vector(only) => Some(s"${section.label} ${only.label}")
        case _ => None
      }).headOption.getOrElse(partitionConfirmLabel(query))

  /** Whether a zone's order is part of the answer the player should be told
    * about: the zone that takes whatever is left over, with no minimum to
    * meet and no cap to hit, holding enough cards for an order to exist.
    *
    * Keyed on that shape rather than on the word "Discard". A name match
    * would stop matching the day the section is renamed -- silently, and
    * exactly when a discard still exists -- where this at worst explains an
    * order in some future leftover zone that has none, which is visible.
    */
  private[frontend] def ordered(section: DecisionSectionState,
      held: Int): Boolean =
    section.minRequired == 0 && section.maxAllowed.isEmpty && held > 1

  /** The instruction line, assembled from the query's own sections. */
  private[frontend] def partitionInstruction(query: DecisionQueryState): String =
    s"Assign every option: ${query.sections.map(section =>
      s"${section.label} (${section.minRequired})").mkString(", ")}."

  /** Renders a parked partition decision as the shared two-zone
    * interaction: one zone per projected section, holding the options the
    * player has put there, each movable by drag or by an accessible button
    * naming the section it would move to.
    *
    * Every label and every minimum comes from the query, and confirmation
    * is `PartitionDecisionState.canConfirm` -- so nothing here knows Forge's
    * printed cost, and a power that adds or removes an option changes this
    * panel with no edit. This is the same interaction Keep/Discard runs,
    * borrowed rather than reimplemented.
    */
  private[frontend] def renderPartitionPanel(value: GameProjection,
      presentation: ViewerPresentation, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => decision.query.filter(_.form == "partition")
          .map(decision -> _))
        .foreach { case (decision, query) =>
      panel.appendChild(text("h2", "", decisionHeading(query)))
      panel.appendChild(text("p", "partition-instruction",
        partitionInstruction(query)))
      ui.currentWalkerPartition.filter(_.decisionId == decision.decisionId)
          .foreach { draft =>
        val zones = element("div", "decision-zones partition-zones")
        query.sections.foreach(section =>
          zones.appendChild(partitionZone(section, query, draft, ui)))
        panel.appendChild(zones)
        val confirm = button(partitionConfirmLabel(query, draft),
          "partition-confirm")
        confirm.disabled = !canControl || !draft.canConfirm
        confirm.onclick = _ =>
          draft.command(ui.currentPlayerId).foreach(ui.submitCommand)
        panel.appendChild(confirm)
      }
    }

  private def partitionZone(section: DecisionSectionState,
      query: DecisionQueryState, draft: WalkerPartitionDraft,
      ui: ServerUiView): dom.Element = {
    val zone = element("section", "decision-zone partition-zone")
    zone.setAttribute("data-section-key", section.key)
    zone.appendChild(text("h3", "", section.label))
    zone.appendChild(text("p", "decision-zone-helper",
      s"At least ${section.minRequired}."))
    val held = draft.optionsIn(section.key)
    if (ordered(section, held.size))
      zone.appendChild(text("p", "decision-zone-order",
        s"${section.label} happens in the order shown."))
    // Own row: a zone that holds heading and options together measures as
    // wide as all of them laid end to end, whatever it can wrap to.
    val options = element("div", "partition-options")
    draft.optionsIn(section.key).foreach(option =>
      options.appendChild(partitionOption(option, section, query, draft, ui)))
    zone.appendChild(options)
    zone.addEventListener("dragover",
      (event: dom.Event) => event.preventDefault())
    zone.addEventListener("drop", (event: dom.Event) => {
      event.preventDefault()
      moveOption(draft, event.asInstanceOf[dom.DragEvent].dataTransfer
        .getData("text/plain"), section.key, ui)
    })
    zone
  }

  private def partitionOption(option: DecisionOptionState,
      section: DecisionSectionState, query: DecisionQueryState,
      draft: WalkerPartitionDraft, ui: ServerUiView): dom.Element = {
    val item = WalkerPartitionDraft.itemId(option)
    val node = element("article", "decision-option")
    node.setAttribute("draggable", "true")
    node.setAttribute("data-option-id", item)
    node.setAttribute("aria-label", option.label)
    // The article has no keydown handler and never did: the keyboard path for
    // moving an option is the move-option buttons below. The tab stop was a
    // duplicate announcement, and in front of a modal trigger it is a dead
    // stop the user has to pass through to reach the card.
    DragClickGuard.attach(node.asInstanceOf[dom.html.Element])
    node.appendChild(option.card.fold[dom.Element](
      text("span", "option-summary", option.label))(CardFace.render))
    node.addEventListener("dragstart", (event: dom.Event) =>
      event.asInstanceOf[dom.DragEvent].dataTransfer
        .setData("text/plain", item))
    // A drop on an option places the dragged one in front of it, which is
    // how order is set by pointer; the zone's own handler would append, so
    // this one stops before reaching it.
    node.addEventListener("dragover",
      (event: dom.Event) => event.preventDefault())
    node.addEventListener("drop", (event: dom.Event) => {
      event.preventDefault()
      event.stopPropagation()
      update(draft.moveBefore(event.asInstanceOf[dom.DragEvent].dataTransfer
        .getData("text/plain"), item), draft, ui)
    })
    // The keyboard-reachable counterpart to the drag: one button per other
    // section, naming where it would move the option to.
    query.sections.filterNot(_.key == section.key).foreach { destination =>
      val label = s"Move ${option.label} to ${destination.label}"
      val move = button(destination.label, "move-option")
      move.setAttribute("aria-label", label)
      move.setAttribute("title", label)
      move.onclick = _ => moveOption(draft, item, destination.key, ui)
      node.appendChild(move)
    }
    // Order within a section is part of the answer, so it needs a keyboard
    // path of its own. Both buttons are always drawn and disabled at the
    // ends, so working an option along a row never reflows it.
    val (index, size) = draft.positionOf(item).getOrElse((0, 1))
    val reorder = element("div", "option-reorder")
    Vector(("move-earlier", "◀", "earlier", -1, index <= 0),
      ("move-later", "▶", "later", 1, index >= size - 1))
      .foreach { case (cssClass, glyph, word, delta, atEnd) =>
        val label = s"Move ${option.label} $word in ${section.label}"
        val control = button(glyph, cssClass)
        control.setAttribute("aria-label", label)
        control.setAttribute("title", label)
        control.disabled = atEnd
        control.onclick = _ => update(draft.shift(item, delta), draft, ui)
        reorder.appendChild(control)
      }
    node.appendChild(reorder)
    node
  }

  private def moveOption(draft: WalkerPartitionDraft, item: String,
      sectionKey: String, ui: ServerUiView): Unit =
    update(draft.move(item, sectionKey), draft, ui)

  private def update(moved: WalkerPartitionDraft, draft: WalkerPartitionDraft,
      ui: ServerUiView): Unit =
    if (moved != draft) {
      ui.currentWalkerPartition = Some(moved)
      ui.rerender()
    }
}
