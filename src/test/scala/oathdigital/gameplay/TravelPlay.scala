package oathdigital.gameplay

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.model._

/** A Travel driven past its destination pick, for the suites that test what
  * a Travel does rather than the pick. */
object TravelPlay:
  /** The answer that picks `site` as the destination. */
  def destination(site: SiteId): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site))

  /** `actor` starts a Travel with `modifiers` and picks `destination`. The
    * events are both commands'. */
  def travelled(rules: OathRules, state: OathState, actor: PlayerId,
      destination: SiteId, modifiers: Vector[PowerId] = Vector.empty)
      : Either[OathViolation, OathTransition] = for
    started <- rules.startWalker(state, ActionRef.Travel, actor, modifiers)
    picked <- rules.resolveWalker(started.state, actor,
      TravelProcedure.destinationDecisionId, this.destination(destination))
  yield picked.copy(events = started.events ++ picked.events)
