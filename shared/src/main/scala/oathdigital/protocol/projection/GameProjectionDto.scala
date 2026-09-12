package oathdigital.protocol.projection

final case class GameProjection(
    gameId: String,
    nextSequence: Long,
    phase: String,
    activeParticipantId: Option[String],
    players: Vector[SetupPlayerProjection],
    world: Vector[SetupRegionProjection],
    pawnLocations: Vector[PawnLocationProjection],
    legalControls: Vector[String],
    ready: Boolean,
    completed: Boolean,
    activePlayerResources: Option[ActivePlayerResourcesProjection] = None,
    currentSiteResources: Option[CurrentSiteResourcesProjection] = None,
    actionSelectionOpen: Boolean = false,
    actionFamilies: Vector[String] = Vector.empty,
    legalTravelDestinations: Vector[LegalTravelDestinationProjection] =
      Vector.empty,
    legalSearchSources: Vector[LegalSearchSourceProjection] = Vector.empty,
    legalMusters: Vector[LegalMusterProjection] = Vector.empty,
    legalTrades: Vector[LegalTradeProjection] = Vector.empty,
    boardTargetActions: Vector[BoardTargetActionProjection] = Vector.empty,
    pendingCardDecision: Option[PendingCardDecisionProjection] = None,
    campaign: Option[CampaignProjection] = None,
    campaignRaidRelocation: Option[CampaignRaidRelocationProjection] = None,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoardProjection] = Vector.empty,
    oathkeeper: Option[OathkeeperProjection] = None,
    oathkeeperRecipient: Option[OathkeeperRecipientProjection] = None
    ,banners: Vector[BannerProjection] = Vector.empty
    ,challenge: Option[ChallengeProjection] = None
    ,minorActions: Option[MinorActionsProjection] = None
    ,negotiation: Option[NegotiationProjection] = None
    ,negotiationWaiting: Boolean = false
    ,favorBanks: Vector[FavorBankProjection] = Vector.empty
    ,tracks: Option[GameTracksProjection] = None
    ,relicDeckCount: Int = 0
    ,privateAdviserPreview: Vector[CardDetailsProjection] = Vector.empty
    ,restPower: Option[RestPowerProjection] = None
    ,restPowerWaiting: Boolean = false
    ,walkerDecision: Option[WalkerDecisionProjection] = None
)
