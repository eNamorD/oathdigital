package oathdigital.gameplay.actions.travel

import oathdigital.gameplay.walker.{PricedOptions, WalkerPowers}
import oathdigital.model._

/** The destination decision as its player is shown it (travel destination
  * design, rulings 3 and 4), priced by [[PricedOptions]]. Every other
  * decision of a Travel is shown as declared. */
object TravelOptions:
  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide): Vector[DecisionOption] =
    if decide.decisionId != TravelProcedure.destinationDecisionId then
      PricedOptions.options(decide)
    else PricedOptions.priced(state, tree, pending, powers, decide,
      fallback = "Cannot travel there now")
