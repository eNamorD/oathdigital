package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathState.{NoGame, Ready}
import oathdigital.gameplay.PlayerSecretSummary
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model._
import oathdigital.protocol.projection._

/** Assembles a player-scoped projection from authoritative state. */
final class GameProjector(catalog: ExecutableCatalog, phasePowers: PhasePowers) {
  def this(catalog: ExecutableCatalog) =
    this(catalog, PhasePowerCatalog.default(catalog))

  private val presentation = new GamePresentationProjector(catalog)
  private val walkerDecisions = new WalkerDecisionProjector(catalog,
    presentation, WalkerPowerCatalog.default(catalog),
    WalkerDecisionProjector.declaredTree, phasePowers)
  private val phasePowerProjector = new PhasePowerProjector(catalog,
    walkerDecisions, phasePowers)
  private val legalActions = new LegalActionProjector(catalog, presentation,
    walkerDecisions, phasePowerProjector)
  private val pendingProjector = new PendingProjector(catalog,
    presentation, walkerDecisions)

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
    case Ready(ready) =>
      val context = ScopedProjectionContext(ready, requestingPlayer)
      readyProjection(gameId, loaded.nextSequence, context)
  }

  private def readyProjection(gameId: String, sequence: Long,
      context: ScopedProjectionContext): GameProjection = {
    val current = context.current
    val active = context.active
    val projectedPhasePowers = phasePowerProjector.project(context)
    val legal = legalActions.project(context, projectedPhasePowers)
    val pending = pendingProjector.project(context)
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
        current.turn.phase == Phase.Act && current.walkerPending.isEmpty,
      actionFamilies = if (current.result.isEmpty && current.turn.phase == Phase.Act &&
        current.walkerPending.isEmpty)
        Vector("Search", "Travel", "Campaign", "Muster", "Trade", "Forge",
          "Recover", "Challenge", "Minor Actions") else Vector.empty,
      legalTravelDestinations = legal.travel,
      legalSearchSources = legal.search,
      boardTargetActions = legal.boardTargets,
      pendingCardDecision = pending.cardDecision,
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
      banners = presentation.banners(context.ready).filter(_.holderPlayerId.isEmpty),
      minorActions = legal.minorActions,
      favorBanks = Suit.all.map(suit => FavorBankProjection(suit.key,
        context.ready.banks.favor.getOrElse(suit, 0))),
      tracks = Some(GameTracksProjection(current.tracks.round,
        current.tracks.visionsDrawn, current.tracks.usurperLimited, 4,
        context.ready.setup.firstPlayer.value)),
      relicDeckCount = current.commonCards.relicDeck.size)
      .copy(walkerDecision = pending.walkerDecision,
        walkerWaiting = pending.walkerWaiting,
        phasePowers = projectedPhasePowers,
        lastCampaign = CampaignResultProjector.project(context.ready))
  }
}
