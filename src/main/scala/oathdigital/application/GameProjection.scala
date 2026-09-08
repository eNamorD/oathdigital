package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathState.{InProgress, NoGame, Ready}
import oathdigital.gameplay.PlayerSecretSummary
import oathdigital.gameplay.setup.{FirstGameParticipant, FirstGameSetupMaterializer}
import oathdigital.model._
import oathdigital.protocol.projection._

/** Assembles a player-scoped projection from authoritative state. */
final class GameProjector(catalog: ExecutableCatalog) {
  private val presentation = new GamePresentationProjector(catalog)
  private val walkerDecisions = new WalkerDecisionProjector(catalog, presentation)
  private val legalActions = new LegalActionProjector(catalog, presentation,
    walkerDecisions)
  private val pendingProcedures = new PendingProcedureProjector(catalog,
    presentation, walkerDecisions)
  private val setupMaterializer = new FirstGameSetupMaterializer(catalog)

  def project(gameId: String, loaded: LoadedGame,
      requestingPlayer: PlayerId): GameProjection =
    projectFor(gameId, loaded, Some(requestingPlayer))

  def projectPublic(gameId: String, loaded: LoadedGame): GameProjection =
    projectFor(gameId, loaded, None)

  private def projectFor(gameId: String, loaded: LoadedGame,
      requestingPlayer: Option[PlayerId]): GameProjection = loaded.state match {
    case NoGame => GameProjection(gameId, loaded.nextSequence, "not-started", None,
      Vector.empty, Vector.empty, Vector.empty, Vector.empty,
      ready = false, completed = false)
    case progress: InProgress => setupProjection(
      gameId, loaded.nextSequence, progress, requestingPlayer)
    case Ready(ready) =>
      val context = ScopedProjectionContext(ready, requestingPlayer)
      readyProjection(gameId, loaded.nextSequence, context)
  }

  private def setupProjection(gameId: String, sequence: Long,
      progress: InProgress, viewer: Option[PlayerId]): GameProjection = {
    val material = setupMaterializer.materialize(progress.plan,
      progress.placements, progress.adviserChoices)
    val order = turnOrder(progress.plan.participants, progress.plan.firstPlayer)
    val awaitingAdviser = progress.placements.size == progress.adviserChoices.size + 1
    val active = if (awaitingAdviser) order(progress.adviserChoices.size).playerId
      else order(progress.placements.size).playerId
    val controls = if (!viewer.contains(active)) Vector.empty
      else if (awaitingAdviser) Vector("chooseAdviser") else Vector("placePawn")
    val privateCards = if (viewer.contains(active)) {
      progress.temporaryHands.getOrElse(active, Vector.empty)
        .map(presentation.cardDetails(_,
          Some(Orientation.FaceUp), hidden = false))
    } else Vector.empty
    val decision = Option.when(privateCards.nonEmpty && awaitingAdviser)(PendingCardDecisionProjection(
      CardDecisionIds.startingAdviser(active, progress.adviserChoices.size).value,
      "starting-adviser", active.value, "Choose your starting adviser",
      Vector("Move exactly one adviser to Keep.",
        "The remaining candidates are discarded."), privateCards,
      1, 1, orderingRequired = false,
      privateCards.map(card => card.cardId ->
        Vector(CardResolutionProjection("starting-adviser"))).toMap))

    GameProjection(gameId, sequence,
      if (awaitingAdviser) "awaiting-adviser" else "awaiting-pawn",
      Some(active.value), presentation.setupPlayers(progress.plan.participants),
      presentation.setupWorld(material),
      progress.placements.map(placement => PawnLocationProjection(
        placement.playerId.value, placement.siteId.value)), controls,
      ready = false, completed = false, pendingCardDecision = decision,
      boardTargetActions = Option.when(controls.contains("placePawn"))(
        BoardTargetActionProjection("place-pawn", "Choose a starting site",
          1, 1, autoActivate = true,
          progress.plan.orderedSites.map(site => BoardTargetCandidateProjection(
            BoardTargetRefProjection.Site(site.value),
            presentation.siteLabel(site))))).toVector,
      worldDeckCount = material.commonCards.worldDeck.size,
      worldDeckTopCardKind = material.commonCards.worldDeck.headOption
        .map(presentation.cardKind),
      playerBoards = presentation.setupPlayerBoards(material),
      banners = Vector(
        BannerProjection("peoples-favor", "mob", None, 1),
        BannerProjection("darkest-secret", "wandering-flame", None, 1)),
      favorBanks = Suit.all.map(suit => FavorBankProjection(
        suit.key, material.favorBanks(suit))),
      tracks = Some(GameTracksProjection(1, 0, usurperLimited = true, 4,
        progress.plan.firstPlayer.value)),
      relicDeckCount = material.commonCards.relicDeck.size,
      privateAdviserPreview = Option.when(!awaitingAdviser)(privateCards)
        .getOrElse(Vector.empty))
  }

  private def readyProjection(gameId: String, sequence: Long,
      context: ScopedProjectionContext): GameProjection = {
    val current = context.current
    val active = context.active
    val legal = legalActions.project(context)
    val pending = pendingProcedures.project(context)
    val site = context.activeSite

    GameProjection(
      gameId, sequence, pending.phase, Some(current.turn.activePlayer.value),
      presentation.readyPlayers(context.ready),
      presentation.readyWorld(context.ready, context.viewer),
      current.players.flatMap(player => player.pawnSite.map(site =>
        PawnLocationProjection(player.player.value, site.value))),
      if (current.result.nonEmpty) Vector.empty else legal.controls,
      ready = true, completed = true,
      activePlayerResources = Some({
        val secrets = PlayerSecretSummary.derive(context.ready, active.player)
          .fold(error => throw new IllegalStateException(error), identity)
        ActivePlayerResourcesProjection(
        active.board.favor, active.board.faceUpSecrets,
        active.board.faceDownSecrets, secrets.committed, secrets.totalSecrets,
        active.board.supply.supply)
      }),
      currentSiteResources = active.pawnSite.flatMap(siteId => site.map(state =>
        CurrentSiteResourcesProjection(siteId.value, state.tokens.favor,
          state.tokens.secrets))),
      actionSelectionOpen = current.result.isEmpty &&
        current.turn.phase == Phase.Act && current.pending.isEmpty &&
        current.walkerPending.isEmpty,
      actionFamilies = if (current.result.isEmpty && current.turn.phase == Phase.Act &&
        current.walkerPending.isEmpty)
        Vector("Search", "Travel", "Campaign", "Muster", "Trade", "Forge",
          "Recover", "Challenge", "Minor Actions") else Vector.empty,
      legalTravelDestinations = legal.travel,
      legalSearchSources = legal.search,
      legalMusters = legal.musters,
      legalTrades = legal.trades,
      boardTargetActions = legal.boardTargets,
      pendingCardDecision = pending.cardDecision,
      recover = pending.recover,
      forge = pending.forge,
      campaign = pending.campaign,
      worldDeckCount = current.commonCards.worldDeck.size,
      worldDeckTopCardKind = current.commonCards.worldDeck.headOption
        .map(presentation.cardKind),
      playerBoards = presentation.playerBoards(context.ready, context.viewer),
      oathkeeper = Some(OathkeeperProjection(
        context.ready.game.campaign.oathkeeperGoal.key,
        current.title.holder.map(_.value), current.title.side match {
          case TitleSide.Oathkeeper => "oathkeeper"
          case TitleSide.Usurper => "usurper"
        }, current.tracks.usurperLimited, current.result.map(_.winner.value),
        current.result.map(_.kind.key))),
      oathkeeperRecipient = pending.oathkeeperRecipient,
      campaignRaidRelocation = pending.campaignRaidRelocation,
      banners = presentation.banners(context.ready).filter(_.holderPlayerId.isEmpty),
      challenge = pending.challenge,
      minorActions = legal.minorActions,
      negotiation = pending.negotiation,
      negotiationWaiting = current.result.isEmpty && pending.negotiationWaiting,
      favorBanks = Suit.all.map(suit => FavorBankProjection(suit.key,
        context.ready.banks.favor.getOrElse(suit, 0))),
      tracks = Some(GameTracksProjection(current.tracks.round,
        current.tracks.visionsDrawn, current.tracks.usurperLimited, 4,
        context.ready.setup.firstPlayer.value)),
      relicDeckCount = current.commonCards.relicDeck.size)
      .copy(restPower = pending.restPower,
        restPowerWaiting = current.result.isEmpty && pending.restPowerWaiting,
        walkerDecision = pending.walkerDecision)
  }

  private def turnOrder(participants: Vector[FirstGameParticipant],
      firstPlayer: PlayerId): Vector[FirstGameParticipant] = {
    val index = participants.indexWhere(_.playerId == firstPlayer)
    participants.drop(index) ++ participants.take(index)
  }
}
