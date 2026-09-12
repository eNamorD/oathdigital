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
  import ServerUiSupport.{ViewerPresentation, button, text}

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

  /** The heading for a parked partition decision.
    *
    * Forge is the only action declaring one today, and "Forge a relic" is
    * its copy rather than the query's -- a query carries prompt copy for
    * its buttons and sections, not for the panel. Task 5 replaces this
    * whole panel with the shared two-zone interaction; this is the one
    * action-named string that survives until then.
    */
  private[frontend] def partitionHeading(action: String): String =
    if (action == "forge") "Forge a relic" else "Resolve decision"

  /** Renders a parked partition decision: one control per projected option,
    * choosing among the projected sections, confirmable only when the
    * projected minima are met.
    *
    * The instruction line and every dropdown label come from the query's
    * own sections, so nothing here knows Forge's printed cost. Moved out of
    * `ActionDecisionRenderer.actionsPanel` alongside the Recover panel, and
    * keeping Forge's current dropdown-per-option interaction for one more
    * task.
    */
  private[frontend] def renderPartitionPanel(value: GameProjection,
      presentation: ViewerPresentation, canControl: Boolean,
      panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => decision.query.filter(_.form == "partition")
          .map(decision -> _))
        .foreach { case (decision, query) =>
      panel.appendChild(text("h2", "", partitionHeading(decision.action)))
      panel.appendChild(text("p", "forge-instruction",
        s"Assign every option: ${query.sections.map(section =>
          s"${section.label} (${section.minRequired})").mkString(", ")}."))
      val confirm = button("Complete Forge", "forge-complete")
      def refreshConfirm(): Unit =
        confirm.disabled = !canControl ||
          !ui.currentForgeAssignment.exists(_.canConfirm)
      query.options.zipWithIndex.foreach { case (option, index) =>
        val label = dom.document.createElement("label")
          .asInstanceOf[dom.html.Label]
        label.textContent = option.label + " "
        val select = dom.document.createElement("select")
          .asInstanceOf[dom.html.Select]
        select.setAttribute("aria-label", s"Section for ${option.label}")
        query.sections.foreach { section =>
          val choice = dom.document.createElement("option")
            .asInstanceOf[dom.html.Option]
          choice.value = section.key; choice.text = section.label
          choice.selected = ui.currentForgeAssignment.exists(
            _.assignments.lift(index).contains(section.key))
          select.appendChild(choice)
        }
        select.onchange = _ => {
          ui.currentForgeAssignment = ui.currentForgeAssignment.map(
            _.choose(index, select.value))
          refreshConfirm()
        }
        label.appendChild(select); panel.appendChild(label)
      }
      refreshConfirm()
      confirm.onclick = _ => ui.currentForgeAssignment.flatMap(
        _.command(ui.currentPlayerId)).foreach(ui.submitCommand)
      panel.appendChild(confirm)
    }
}
