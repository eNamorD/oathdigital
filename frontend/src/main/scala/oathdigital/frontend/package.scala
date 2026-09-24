package oathdigital

package object frontend {
  type CampaignResultState = protocol.projection.CampaignResultProjection
  val CampaignResultState = protocol.projection.CampaignResultProjection
  type GameProjection = protocol.projection.GameProjection
  val GameProjection = protocol.projection.GameProjection
  type GamePlayer = protocol.projection.SetupPlayerProjection
  val GamePlayer = protocol.projection.SetupPlayerProjection
  type CardDetails = protocol.projection.CardDetailsProjection
  val CardDetails = protocol.projection.CardDetailsProjection
  type GameSiteCard = protocol.projection.SiteCardProjection
  val GameSiteCard = protocol.projection.SiteCardProjection
  type GameSiteRelics = protocol.projection.SiteRelicsProjection
  val GameSiteRelics = protocol.projection.SiteRelicsProjection
  type ForgeCost = protocol.projection.ForgeCostProjection
  val ForgeCost = protocol.projection.ForgeCostProjection
  type SiteForces = protocol.projection.SiteForcesProjection
  val SiteForces = protocol.projection.SiteForcesProjection
  type GameSite = protocol.projection.SetupSiteProjection
  val GameSite = protocol.projection.SetupSiteProjection
  type SitePower = protocol.projection.SitePowerProjection
  val SitePower = protocol.projection.SitePowerProjection
  type GameRegion = protocol.projection.SetupRegionProjection
  val GameRegion = protocol.projection.SetupRegionProjection
  type GamePawn = protocol.projection.PawnLocationProjection
  val GamePawn = protocol.projection.PawnLocationProjection
  type ActivePlayerResources = protocol.projection.ActivePlayerResourcesProjection
  val ActivePlayerResources = protocol.projection.ActivePlayerResourcesProjection
  type CurrentSiteResources = protocol.projection.CurrentSiteResourcesProjection
  val CurrentSiteResources = protocol.projection.CurrentSiteResourcesProjection
  type LegalTravelDestination = protocol.projection.LegalTravelDestinationProjection
  val LegalTravelDestination = protocol.projection.LegalTravelDestinationProjection
  type LegalSearchSource = protocol.projection.LegalSearchSourceProjection
  val LegalSearchSource = protocol.projection.LegalSearchSourceProjection
  type BoardTargetRef = protocol.projection.BoardTargetRefProjection
  val BoardTargetRef = protocol.projection.BoardTargetRefProjection
  type BoardTargetCandidate = protocol.projection.BoardTargetCandidateProjection
  val BoardTargetCandidate = protocol.projection.BoardTargetCandidateProjection
  type BoardTargetAction = protocol.projection.BoardTargetActionProjection
  val BoardTargetAction = protocol.projection.BoardTargetActionProjection
  type CardResolution = protocol.projection.CardResolutionProjection
  val CardResolution = protocol.projection.CardResolutionProjection
  type MinorAdviserPlacement = protocol.projection.CardResolutionProjection
  object MinorAdviserPlacement {
    def apply(kind: String,
        replacement: Option[CardDetails] = None): MinorAdviserPlacement =
      protocol.projection.CardResolutionProjection(kind, None,
        replacement.nonEmpty, replacement.toVector)
  }
  type PendingCardDecision = protocol.projection.PendingCardDecisionProjection
  val PendingCardDecision = protocol.projection.PendingCardDecisionProjection
  type DecisionQueryState = protocol.projection.DecisionQueryProjection
  val DecisionQueryState = protocol.projection.DecisionQueryProjection
  type DecisionOptionState = protocol.projection.DecisionOptionProjection
  val DecisionOptionState = protocol.projection.DecisionOptionProjection
  type PhasePowerState = protocol.projection.PhasePowerProjection
  val PhasePowerState = protocol.projection.PhasePowerProjection
  type DecisionSectionState = protocol.projection.DecisionSectionProjection
  val DecisionSectionState = protocol.projection.DecisionSectionProjection
  type DecisionSlotState = protocol.projection.DecisionSlotProjection
  val DecisionSlotState = protocol.projection.DecisionSlotProjection
  type BannerState = protocol.projection.BannerProjection
  val BannerState = protocol.projection.BannerProjection
  type FavorBankState = protocol.projection.FavorBankProjection
  val FavorBankState = protocol.projection.FavorBankProjection
  type MinorAdviser = protocol.projection.MinorAdviserProjection
  val MinorAdviser = protocol.projection.MinorAdviserProjection
  type MinorActionsState = protocol.projection.MinorActionsProjection
  val MinorActionsState = protocol.projection.MinorActionsProjection
  type NegotiationTransferState = protocol.projection.NegotiationTransferProjection
  val NegotiationTransferState = protocol.projection.NegotiationTransferProjection
  type NegotiationDisclosureState = protocol.projection.NegotiationDisclosureProjection
  val NegotiationDisclosureState = protocol.projection.NegotiationDisclosureProjection
  type NegotiationSiteRelicState = protocol.projection.NegotiationSiteRelicProjection
  val NegotiationSiteRelicState = protocol.projection.NegotiationSiteRelicProjection
  type NegotiationEditingState = protocol.projection.NegotiationEditingProjection
  val NegotiationEditingState = protocol.projection.NegotiationEditingProjection
  type NegotiationDealState = protocol.projection.NegotiationDealProjection
  val NegotiationDealState = protocol.projection.NegotiationDealProjection
  type PlayerBoard = protocol.projection.PlayerBoardProjection
  val PlayerBoard = protocol.projection.PlayerBoardProjection
  type OathkeeperStatus = protocol.projection.OathkeeperProjection
  val OathkeeperStatus = protocol.projection.OathkeeperProjection
  type WalkerDecisionState = protocol.projection.WalkerDecisionProjection
  val WalkerDecisionState = protocol.projection.WalkerDecisionProjection
  type WalkerRollOutcomeState = protocol.projection.WalkerRollOutcomeProjection
  val WalkerRollOutcomeState = protocol.projection.WalkerRollOutcomeProjection
  type WalkerWaitingState = protocol.projection.WalkerWaitingProjection
  val WalkerWaitingState = protocol.projection.WalkerWaitingProjection
}
