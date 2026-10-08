package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx, Transform}
import oathdigital.model.{ActionRef, PowerId, PowerWindow, RuleSourceRef}

/** The engine's waiver for a queued action (follow-up actions design,
  * ruling 4): at the cost window of the action `walkerFollowUp` names, the
  * entry's player spends no Supply. The source power takes no part.
  *
  * `WalkerPowers.selected` offers it to every command, so every walker path
  * (start, every resume, the payment dry run, a start control's dry run, a
  * preview) folds it the same way. It applies only while the run was started
  * from the queue, which `applicable` reads from state; a command with no
  * entry folds nothing.
  */
object FollowUpSupplyWaiver extends ContributingPower:
  val id: PowerId = PowerId("engine.follow-up-supply")
  val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** Where each action pays its Supply. Recover pays inside its repeat with
    * no window, and every other procedure spends none. */
  def costWindow(action: ActionRef): Option[PowerWindow] = action match
    case ActionRef.Campaign => Some(PowerWindow.CampaignCost)
    case ActionRef.Travel => Some(PowerWindow.TravelCost)
    case ActionRef.Search => Some(PowerWindow.SearchCost)
    case ActionRef.Muster => Some(PowerWindow.MusterCost)
    case ActionRef.Trade => Some(PowerWindow.TradeCost)
    case ActionRef.Forge => Some(PowerWindow.ForgeCost)
    case ActionRef.Challenge => Some(PowerWindow.ChallengeCost)
    case _ => None

  private val windows: Vector[PowerWindow] = Vector(PowerWindow.CampaignCost,
    PowerWindow.TravelCost, PowerWindow.SearchCost, PowerWindow.MusterCost,
    PowerWindow.TradeCost, PowerWindow.ForgeCost, PowerWindow.ChallengeCost)

  lazy val contributions: Map[PowerWindow, Vector[Contribution]] =
    windows.map(window => window -> Vector[Contribution](Transform(
      (ctx, operations) => ctx.state.game.current.walkerFollowUp
        .fold(operations)(entry => SupplyStrip(entry.player, operations)))))
      .toMap

  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.walkerFollowUp.exists(entry =>
      costWindow(entry.action).contains(ctx.window))
