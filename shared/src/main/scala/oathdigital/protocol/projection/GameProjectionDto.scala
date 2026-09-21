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
    boardTargetActions: Vector[BoardTargetActionProjection] = Vector.empty,
    pendingCardDecision: Option[PendingCardDecisionProjection] = None,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoardProjection] = Vector.empty,
    oathkeeper: Option[OathkeeperProjection] = None
    ,banners: Vector[BannerProjection] = Vector.empty
    ,minorActions: Option[MinorActionsProjection] = None
    ,favorBanks: Vector[FavorBankProjection] = Vector.empty
    ,tracks: Option[GameTracksProjection] = None
    ,relicDeckCount: Int = 0
    ,privateAdviserPreview: Vector[CardDetailsProjection] = Vector.empty
    ,walkerDecision: Option[WalkerDecisionProjection] = None
    ,walkerWaiting: Option[WalkerWaitingProjection] = None
    ,phasePowers: Vector[PhasePowerProjection] = Vector.empty
    ,lastCampaign: Option[CampaignResultProjection] = None
    ,viewerPlayerId: Option[String] = None
)
