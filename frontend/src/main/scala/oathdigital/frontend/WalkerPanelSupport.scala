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

  /** The two button keys Recover's continue/stop query declares. Read to
    * decide which projected option gets the supply-aware treatment below --
    * never to decide whether that option exists.
    */
  private[frontend] val continueOptionKey = "continue"
  private[frontend] val stopOptionKey = "stop"

  private[frontend] sealed trait RecoverWalkerStep
  private[frontend] object RecoverWalkerStep {
    final case class Roll(pool: String) extends RecoverWalkerStep
    /** The projected button options, in declared order. */
    final case class Choice(options: Vector[DecisionOptionState])
        extends RecoverWalkerStep
    /** The projected relic options, in declared order. */
    final case class Relic(options: Vector[DecisionOptionState])
        extends RecoverWalkerStep
  }

  /** A decide park's projected choose-one options, or `None` when the
    * projection carries no query -- which the engine does on purpose when an
    * option could not be presented. Rendering nothing is then correct: there
    * is no answer the client could safely build.
    */
  private[frontend] def chooseOneOptions(decision: WalkerDecisionState)
      : Option[Vector[DecisionOptionState]] =
    decision.query.filter(_.form == "choose-one").map(_.options)

  private[frontend] def recoverWalkerStep(decision: WalkerDecisionState)
      : Option[RecoverWalkerStep] =
    if (decision.action != "recover") None
    else decision.kind match {
      case "roll" => decision.pool.map(RecoverWalkerStep.Roll)
      case "decide" if decision.decisionId == recoverChoiceDecisionId =>
        chooseOneOptions(decision).map(RecoverWalkerStep.Choice)
      case "decide" if decision.decisionId == recoverRelicDecisionId =>
        chooseOneOptions(decision).map(RecoverWalkerStep.Relic)
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

  /** Renders the Recover panel for whichever of the three parks
    * (`recoverWalkerStep`) the walker is at. Shows `rollOutcomeSummary`
    * (I5) above each park's controls, and gates "Spend 1 Supply for two
    * dice" on the player actually having supply -- as the legacy (deleted)
    * Recover panel did.
    */
  private[frontend] def renderRecoverPanel(value: GameProjection,
      presentation: ViewerPresentation, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit = {
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => recoverWalkerStep(decision).map(decision -> _))
        .foreach {
      case (decision, RecoverWalkerStep.Roll(pool)) =>
        panel.appendChild(text("h2", "", "Recover"))
        rollFeedback(decision, panel)
        val roll = button("Roll", "recover-roll")
        roll.disabled = !canControl
        roll.onclick = _ => ui.submitCommand(GameCommand.RollWalker(pool))
        panel.appendChild(roll)
      case (decision, RecoverWalkerStep.Choice(options)) =>
        panel.appendChild(text("h2", "", "Recover"))
        rollFeedback(decision, panel)
        val hasSupply = value.activePlayerResources.exists(_.supply >= 1)
        // One control per PROJECTED option, so a power that drops Continue
        // or adds a third choice changes this panel with no edit here. The
        // two known keys keep their richer copy and the supply gate; any
        // other option falls back to the query's own declared label.
        options.foreach { option =>
          val spendsSupply = option.id == continueOptionKey
          val (label, className) =
            if (spendsSupply) ("Spend 1 Supply for two dice", "recover-add")
            else if (option.id == stopOptionKey) ("Stop Recover", "recover-stop")
            else (option.label, "recover-choice")
          val control = button(label, className)
          control.disabled = !canControl || (spendsSupply && !hasSupply)
          control.onclick = _ => ui.submitCommand(
            resolveChooseOneCommand(decision, option))
          panel.appendChild(control)
        }
      case (decision, RecoverWalkerStep.Relic(options)) =>
        panel.appendChild(text("h2", "", "Take a relic"))
        rollFeedback(decision, panel)
        options.foreach { option =>
          val choose = button(s"Take ${option.label} facedown",
            "recover-relic-choice")
          choose.disabled = !canControl
          choose.onclick = _ => ui.submitCommand(
            resolveChooseOneCommand(decision, option))
          panel.appendChild(choose)
        }
    }
  }

  private def rollFeedback(decision: WalkerDecisionState,
      panel: dom.Element): Unit =
    decision.rollOutcome.foreach(outcome => panel.appendChild(
      text("p", "recover-roll-outcome", rollOutcomeSummary(outcome))))

  /** The heading and confirm copy for a parked partition decision.
    *
    * Prompt copy for the panel itself, which a query does not carry: it
    * declares labels for its buttons and its sections, not for the frame
    * around them. So the panel names the action the same way the Recover
    * panel does, and falls back to generic copy for any other action that
    * declares a partition -- the interaction below is what had to stop
    * being Forge-specific, not the title above it.
    */
  private[frontend] def partitionHeading(action: String): String =
    if (action == "forge") "Forge a relic" else "Resolve decision"

  private[frontend] def partitionConfirmLabel(action: String): String =
    if (action == "forge") "Complete Forge" else "Confirm"

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
      panel.appendChild(text("h2", "", partitionHeading(decision.action)))
      panel.appendChild(text("p", "partition-instruction",
        partitionInstruction(query)))
      ui.currentWalkerPartition.filter(_.decisionId == decision.decisionId)
          .foreach { draft =>
        val zones = element("div", "decision-zones partition-zones")
        query.sections.foreach(section =>
          zones.appendChild(partitionZone(section, query, draft, ui)))
        panel.appendChild(zones)
        val confirm = button(partitionConfirmLabel(decision.action),
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
    draft.optionsIn(section.key).foreach(option =>
      zone.appendChild(partitionOption(option, section, query, draft, ui)))
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
    node.setAttribute("tabindex", "0")
    node.setAttribute("draggable", "true")
    node.setAttribute("data-option-id", item)
    node.setAttribute("aria-label", option.label)
    node.appendChild(option.card.fold(
      text("span", "option-summary", option.label))(
      ServerUiSupport.cardDetailsPopover))
    node.addEventListener("dragstart", (event: dom.Event) =>
      event.asInstanceOf[dom.DragEvent].dataTransfer
        .setData("text/plain", item))
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
    node
  }

  private def moveOption(draft: WalkerPartitionDraft, item: String,
      sectionKey: String, ui: ServerUiView): Unit = {
    val moved = draft.move(item, sectionKey)
    if (moved != draft) {
      ui.currentWalkerPartition = Some(moved)
      ui.rerender()
    }
  }
}
