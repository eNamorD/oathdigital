package oathdigital.gameplay

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.model._

/** A Search driven past its source pick, for the suites that test what a
  * Search does rather than the pick. */
object SearchPlay:
  /** The answer that picks `pile` as the source. */
  def source(pile: SearchSource): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(SearchSource.ref(pile))

  /** `actor` starts a Search with `modifiers` and picks `pile`. The events
    * are both commands'. */
  def searched(rules: OathRules, state: OathState, actor: PlayerId,
      pile: SearchSource = SearchSource.WorldDeck,
      modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] = for
    started <- rules.startWalker(state, ActionRef.Search, actor, modifiers)
    picked <- rules.resolveWalker(started.state, actor,
      SearchProcedure.sourceDecisionId, source(pile))
  yield picked.copy(events = started.events ++ picked.events)
