package oathdigital.frontend

private[frontend] final case class CampaignPlacement(
    siteId: String, count: Int)

private[frontend] final case class CampaignPlacementState(
    context: BoardSelectionContext,
    decisionId: String,
    targets: Vector[CampaignPlacementTarget],
    maximum: Int,
    allocations: Vector[CampaignPlacement]
) {
  def total: Int = allocations.map(_.count).sum
  def remaining: Int = maximum - total

  def count(siteId: String): Int =
    allocations.find(_.siteId == siteId).map(_.count).getOrElse(0)

  def increment(siteId: String): CampaignPlacementState =
    if (remaining <= 0 || !targets.exists(_.siteId == siteId)) this
    else update(siteId, count(siteId) + 1)

  def decrement(siteId: String): CampaignPlacementState =
    if (count(siteId) <= 0) this else update(siteId, count(siteId) - 1)

  def reset: CampaignPlacementState = copy(allocations =
    targets.map(target => CampaignPlacement(target.siteId, 0)))

  private def update(siteId: String, count: Int): CampaignPlacementState =
    copy(allocations = allocations.map(allocation =>
      if (allocation.siteId == siteId) allocation.copy(count = count)
      else allocation))
}

private[frontend] object CampaignPlacementState {
  def reconcile(previous: Option[CampaignPlacementState],
      context: BoardSelectionContext, campaign: Option[CampaignState])
      : Option[CampaignPlacementState] = campaign.filter(
        _.victorious.contains(true)).map { current =>
        previous.filter(state => state.context == context &&
          state.decisionId == current.decisionId &&
          state.targets == current.placementTargets &&
          state.maximum == current.maxPlacement).getOrElse(
          CampaignPlacementState(context, current.decisionId,
            current.placementTargets, current.maxPlacement,
            current.placementTargets.map(target =>
              CampaignPlacement(target.siteId, 0))))
      }
}
