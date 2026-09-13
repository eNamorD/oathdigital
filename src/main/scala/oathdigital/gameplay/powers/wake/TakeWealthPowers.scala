package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.{OathViolation, RuleSourceRef}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Take Wealth may be used once per turn at each site (batch-1 Task 6).
  *
  * The limit is a `Restriction` and nothing else: it reads
  * `TurnState.usedPowers` through the `ReadyGame` every contribution already
  * sees, so the walker keeps no notion of a power having been used and
  * `PowerCtx` grows no field for one. That is the spec's standing decision
  * about `usedPowers` surviving contact with a walker action, and it survived
  * without an engine change.
  *
  * One object, not one per site. Per-site scope lives in the `PowerUseRef`
  * this builds -- `PowerSourceRef.Site` names the site, so a second take at a
  * site already taken from is blocked while a take at another site the same
  * turn is not. Per-site instances would have bought the same behaviour at the
  * price of a fabricated `PowerId` per site, since the collector keys powers
  * by id and instances sharing one would attribute a restriction's context to
  * another site's instance.
  *
  * The source is a game rule rather than a site: taking wealth is a standing
  * Wake-phase option in the rulebook, not a power printed on any site.
  */
case object TakeWealthLimit extends ContributingPower {
  val id: PowerId = PowerId("site.take-wealth")
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.WakeTakeWealth -> Vector(
      Restriction((ctx, _) => blocked(ctx))))

  /** The one spelling of "this site's take wealth was used this turn",
    * shared with whatever records it: a read side and a write side that
    * spelled it differently would leave the limit silent.
    */
  def useRef(site: SiteId): PowerUseRef =
    PowerUseRef(PowerTiming.Wake, PowerSourceRef.Site(site), id)

  /** The site is the actor's pawn site, the way the take itself is sited, and
    * not a `Take` read out of the tree: the declared tree builds its take at
    * execution, so no concrete operation naming a site exists at the command
    * entry where restrictions run. A missing pawn site says nothing here --
    * that is the action's own start gate, and a second spelling of it would
    * report the wrong reason.
    */
  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    ctx.state.game.current.players.find(_.player == ctx.actor)
      .flatMap(_.pawnSite).map(useRef)
      .filter(ctx.state.game.current.turn.usedPowers.contains)
      .map(OathViolation.PowerAlreadyUsed)
}
