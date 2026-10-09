package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.model._

/** What the Search source powers share (catalog batch 6, slice 3). Each
  * rewrites the source decision, which its window (`SearchSourceSelection`)
  * folds as `Vector(decide)`. Every other operation is left alone.
  */
private[search] object SearchSources:

  /** `operations` with each of `regions`' discard piles added to the source
    * decision, after the options it offers, unless it offers that pile
    * already. */
  def withPiles(operations: Vector[Operation], regions: Vector[Region])
      : Vector[Operation] = changed(operations) { options =>
    val offered = options.map(_.ref).toSet
    options ++ regions.map(region =>
      SearchSource.option(SearchSource.RegionalDiscard(region)))
      .filterNot(option => offered(option.ref))
  }

  /** `operations` with each available source `warns` names warned with
    * `reason`. It stays choosable. */
  def warned(operations: Vector[Operation],
      warns: DecisionOptionRef => Boolean, reason: String)
      : Vector[Operation] = changed(operations)(_.map(option =>
    if option.available && warns(option.ref) then
      DecisionOption.Warned(option, OptionReason.Written(reason))
    else option))

  /** `operations` with each available source `withholds` names unavailable,
    * with `reason`. */
  def withheld(operations: Vector[Operation],
      withholds: DecisionOptionRef => Boolean, reason: String)
      : Vector[Operation] = changed(operations)(_.map(option =>
    if option.available && withholds(option.ref) then
      DecisionOption.Unavailable(option, OptionReason.Written(reason))
    else option))

  private def changed(operations: Vector[Operation])(
      change: Vector[DecisionOption] => Vector[DecisionOption])
      : Vector[Operation] = operations.map {
    case decide: Decide
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      decide.query match
        case one: DecisionQuery.ChooseOne =>
          decide.copy(query = one.copy(options = change(one.options)))
        case _ => decide
    case other => other
  }
