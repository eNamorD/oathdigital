package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** Step 9 for a Conquest victory: place surviving warbands on the conquered
  * targets. Unplaced survivors simply stay on the board.
  */
private[campaign] object CampaignConquest {
  def steps(actor: PlayerId, result: CampaignResult): Vector[Operation] = {
    val survivors = result.force - result.skullLosses - result.sacrificed
    if (survivors <= 0) Vector.empty
    else Vector(Sequence(Vector[Operation](
      Decide(CampaignIds.placement, actor, query(result, survivors)),
      BuildOps((ready, pending) => placements(ready, actor, result, pending))),
      Some(PowerWindow.CampaignPlacement)))
  }

  private def query(result: CampaignResult, survivors: Int): DecisionQuery =
    result.targetSites match {
      case Vector(_) => DecisionQuery.ChooseAmount(0, survivors,
        Some(s"Place up to $survivors surviving warband${if (survivors == 1) "" else "s"} " +
          "on the conquered site; the rest stay on your board"), "Place warbands")
      case sites => DecisionQuery.Distribute(sites.map(site => DistributeSlot(
        DecisionOptionRef.Site(site), 0, survivors, None)), 0, survivors,
        Some(s"Place up to $survivors surviving warbands across the conquered " +
          "sites; the rest stay on your board"), "Place warbands")
    }

  private def placements(ready: ReadyGame, actor: PlayerId, result: CampaignResult,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.players.find(_.player == actor).toRight(
      OathViolation.InvalidEventOrder("the Campaign's attacker is not in the game"))
      .map(attacker => CampaignAnswers.placements(pending, result.targetSites)
        .filter(_._2 > 0).map { case (site, count) => Move(
          Piece.Warbands(ForceKind.Exile(attacker.lineage), count),
          PositionedLocation(Location.PlayArea(actor)),
          PositionedLocation(Location.Site(site))): CoreOperation })
}
