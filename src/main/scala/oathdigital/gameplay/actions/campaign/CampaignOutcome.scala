package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** What follows the victor. Read from the durable [[CampaignResult]] only:
  * this is selected again after the losses have changed the board.
  */
private[campaign] object CampaignOutcome {
  def steps(ready: ReadyGame, catalog: ExecutableCatalog, actor: PlayerId,
      result: CampaignResult): Vector[Operation] = {
    val losses: Operation = Sequence(Vector[Operation](BuildOps((ready, _) =>
      CampaignBattle.losses(ready, result))), Some(PowerWindow.CampaignLosses))
    val resolution: Vector[Operation] =
      if (!result.victorious) Vector.empty
      else result.kind match {
        case CampaignKind.Conquest => CampaignConquest.steps(actor, result)
        case CampaignKind.Raid => CampaignRaid.steps(ready, actor, result)
      }
    losses +: resolution
  }
}
