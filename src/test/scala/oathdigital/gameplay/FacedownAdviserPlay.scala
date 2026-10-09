package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.model._
import oathdigital.testkit.PlacementPicks

/** The facedown-adviser play driven up to its placement, for the suites that
  * test the placement rather than the pick. */
object FacedownAdviserPlay:
  /** `actor` starts the play, picks `modifiers` in its modifier decision,
    * and picks `card`, which they hold as a facedown adviser: the walk is
    * parked on the card's placement. The events are every command's. */
  def parkedOnPlacement(rules: OathRules, state: OathState, actor: PlayerId,
      card: WorldCardId, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] = for
    started <- ModifierPlay.started(rules, state,
      ActionRef.PlayFacedownAdviser, actor,
      PowerWindow.SearchModifierSelection, modifiers)
    picked <- rules.resolveWalker(started.state, actor,
      CardPlayProcedure.facedownAdviserDecisionId,
      DecisionAnswer.ChooseOneAnswer(PlacementPicks.optionNaming(card)))
  yield picked.copy(events = started.events ++ picked.events)
