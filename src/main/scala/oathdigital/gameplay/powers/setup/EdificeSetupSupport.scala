package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.setup.SetupProcedure
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

  /** The site the acting player just chose for their pawn, read from the
    * decisions answered so far this walk. Only meaningful at
    * `PowerWindow.SetupPawnPlaced`; a future `WhenExplored` fire needs its
    * own read of the explored site, not built by this slice.
    */
  def pawnPlacementSite(ctx: PowerCtx): Option[SiteId] =
    ctx.answered.collectFirst {
      case Answered(decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Site(site)), _)
        if decisionId == SetupProcedure.pawnDecisionId(ctx.activePlayer) => site
    }
}
