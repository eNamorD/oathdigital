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
    ,redo: Option[StepOfferProjection] = None
    ,logRevision: Option[Long] = None
    ,followUp: Option[FollowUpProjection] = None
    ,consentWaiting: Option[ConsentWaitingProjection] = None
    ,consentPrompt: Option[ConsentPromptProjection] = None
    ,consentPreferences: Vector[ConsentPreferencesProjection] = Vector.empty
)

/** The queue's head, as every viewer sees it (follow-up actions design,
  * "Projection and UI"). `startable` is false when the action cannot start
  * now, and `reason` says why. `queuedAfter` names the actions behind it, in
  * order. */
final case class FollowUpProjection(playerId: String, action: String,
    actionLabel: String, source: String, startable: Boolean,
    reason: Option[String] = None,
    queuedAfter: Vector[String] = Vector.empty)

/** A step the viewer may take back or restore, with the hover note that
  * says what it is (undo design, "Controls"). Present only for the step's
  * issuer. */
final case class StepOfferProjection(note: String)
