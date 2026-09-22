package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Shared reads for the slice-3 SETUP/WHEN-EXPLORED edifice powers (E02,
  * E06, E22).
  */
object EdificeSetupSupport {
  /** Where `edifice`'s `side` currently sits, if it is on the board at all
    * -- a Homeland outside the first 8 Chronicle sites keeps its edifice in
    * storage, so a game may not place it anywhere (design spec, "The
    * first-game generator").
    */
  def siteOf(ready: ReadyGame, edifice: EdificeId, side: EdificeSide)
      : Option[SiteId] =
    ready.game.current.map.sites.collectFirst {
      case (site, state) if state.denizens.exists {
        case card: EdificeState => card.id == edifice && card.side == side
        case _ => false
      } => site
    }

  /** The player and site of the pawn placement that was just answered,
    * reached at a `PowerWindow.SetupPawnPlaced` fold -- always the LAST
    * entry of `ctx.answered`, since `SetupProcedure`'s tree runs a
    * `BuildOps(placePawn, window = Some(SetupPawnPlaced))` immediately
    * after that player's own pawn `Decide`, with nothing else answered in
    * between.
    *
    * `ctx.activePlayer` is NOT this: it stays `ready.setup.firstPlayer` for
    * the whole Setup walk (`PowerCtx`'s own doc, "not a parked decision's
    * owner"), so a power that placed the actor from `ctx.activePlayer`
    * would attribute every player's placement to the first player alone.
    */
  def pawnPlacement(ctx: PowerCtx): Option[(PlayerId, SiteId)] =
    ctx.answered.lastOption.collect {
      case Answered(_, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Site(site)), by) => by -> site
    }
}
