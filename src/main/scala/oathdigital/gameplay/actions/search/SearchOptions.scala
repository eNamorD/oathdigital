package oathdigital.gameplay.actions.search

import oathdigital.gameplay.actions.SearchRules
import oathdigital.gameplay.walker.{PricedOptions, WalkerPowers}
import oathdigital.model._

/** The source decision as its player is shown it (search source design,
  * rulings 3 and 4), priced by [[PricedOptions]]. An empty pile says so
  * rather than what it would cost. Every other decision of a Search is shown
  * as declared. */
object SearchOptions:
  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide): Vector[DecisionOption] =
    if decide.decisionId != SearchProcedure.sourceDecisionId then
      PricedOptions.options(decide)
    else PricedOptions.priced(state, tree, pending, powers, decide,
      fallback = "Cannot search there now",
      explain = empty(state))

  /** "The world deck is empty", "The Cradle discard pile is empty". */
  private def empty(state: ReadyGame)(ref: DecisionOptionRef)
      : Option[String] =
    SearchSource.fromRef(ref).filter(SearchRules.draw(state, _).isEmpty)
      .map(source => s"The ${SearchSource.name(source)} is empty")
