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
    legalSearchSources: Vector[LegalSearchSourceProjection] = Vector.empty,
    boardTargetActions: Vector[BoardTargetActionProjection] = Vector.empty,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoardProjection] = Vector.empty,
    oathkeeper: Option[OathkeeperProjection] = None
    ,banners: Vector[BannerProjection] = Vector.empty
    ,minorActions: Option[MinorActionsProjection] = None
    ,favorBanks: Vector[FavorBankProjection] = Vector.empty
    ,tracks: Option[GameTracksProjection] = None
    ,relicDeckCount: Int = 0
    ,temporaryHandPreview: Vector[CardDetailsProjection] = Vector.empty
    ,walkerDecision: Option[WalkerDecisionProjection] = None
    ,walkerWaiting: Option[WalkerWaitingProjection] = None
    ,phasePowers: Vector[PhasePowerProjection] = Vector.empty
    ,viewerPlayerId: Option[String] = None
    ,supplyMaximum: Int = 0
    ,restSupplyGain: Option[Int] = None
    ,supplyBands: Vector[SupplyBandProjection] = Vector.empty
    ,undo: Option[StepOfferProjection] = None
    ,logRevision: Option[Long] = None
)

/** The step the viewer may take back, with the hover note that says what
  * it is (undo design, "Controls"). Present only for the step's issuer. */
final case class StepOfferProjection(note: String)
