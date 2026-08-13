package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup.OathState.{InProgress, NoGame, Ready}
import oathdigital.setup.FirstGameParticipant
import oathdigital.setup.ReadyGame
import oathdigital.setup.WakeResource
import oathdigital.gameplay.TakeWealthRules
import oathdigital.gameplay.actions.{Economy, SearchRules, TravelRules}
import oathdigital.gameplay.phases.Rest

final case class SetupPlayerProjection(
    playerId: String,
    displayName: String,
    role: String,
    colorToken: String
)
final case class CardDetailsProjection(
    cardId: String,
    cardKind: String,
    name: String,
    suit: Option[String] = None,
    restrictions: Option[String] = None,
    rulesText: Option[String] = None,
    orientation: Option[String] = None,
    side: Option[String] = None,
    favor: Int = 0,
    secrets: Int = 0,
    relicValue: Option[Int] = None,
    defense: Option[Int] = None,
    hidden: Boolean = false
)
final case class SiteCardProjection(
    cardId: String,
    label: String,
    details: Option[CardDetailsProjection] = None)
final case class SiteRelicsProjection(facedownCount: Int)
final case class ForgeCostProjection(favor: Int, secrets: Int)
final case class SetupSiteProjection(
    siteId: String,
    label: String,
    looseFavor: Int,
    looseSecrets: Int,
    denizenCapacity: Int,
    relicCapacity: Int,
    denizens: Vector[SiteCardProjection],
    relics: SiteRelicsProjection,
    defense: Int = 0,
    recoverDifficulty: Option[Int] = None,
    forgeCost: Option[ForgeCostProjection] = None,
    powers: Vector[SitePowerProjection] = Vector.empty
)
final case class SetupRegionProjection(
    regionId: String,
    sites: Vector[SetupSiteProjection],
    discardCount: Int = 0,
    discardTopCardKind: Option[String] = None
)
final case class SitePowerProjection(kind: String, label: String, description: Option[String])
final case class PawnLocationProjection(playerId: String, siteId: String)
final case class ActivePlayerResourcesProjection(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    supply: Int
)
final case class CurrentSiteResourcesProjection(
    siteId: String,
    favor: Int,
    secrets: Int
)
final case class LegalTravelDestinationProjection(siteId: String, supplyCost: Int)
final case class LegalSearchSourceProjection(kind: String, region: Option[String], supplyCost: Int)
final case class LegalMusterProjection(
    targetKind: String, targetId: String, label: String, suit: String,
    supplyCost: Int, warbandsGained: Int)
final case class LegalTradeProjection(
    targetKind: String, targetId: String, label: String, suit: String,
    resource: String, supplyCost: Int, gained: Int)
final case class CardResolutionProjection(
    kind: String,
    orientation: Option[String] = None,
    replacementRequired: Boolean = false,
    replacementTargets: Vector[CardDetailsProjection] = Vector.empty)
final case class PendingCardDecisionProjection(
    decisionId: String,
    kind: String,
    actorPlayerId: String,
    prompt: String,
    instructions: Vector[String],
    cards: Vector[CardDetailsProjection],
    keepMinimum: Int,
    keepMaximum: Int,
    orderingRequired: Boolean,
    resolutionsByCard: Map[String, Vector[CardResolutionProjection]]
)
final case class PlayerBoardProjection(
    playerId: String,
    warbands: Int,
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    supply: Int,
    pawnSiteId: Option[String],
    advisers: Vector[CardDetailsProjection],
    relics: Vector[CardDetailsProjection],
    revealedVision: Option[CardDetailsProjection]
)

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
    pendingCardDecision: Option[PendingCardDecisionProjection] = None,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoardProjection] = Vector.empty
)

final class GameProjector(catalog: ExecutableCatalog) {
  private val siteNames =
    catalog.sites.map(site => site.id -> site.name).toMap
  private val denizenNames =
    catalog.denizens.map(d => DenizenId(d.id.value) -> d.name).toMap
  private val edificeNames = catalog.edifices.map { edifice =>
    EdificeId(edifice.id.value) ->
      (edifice.intact.name -> edifice.ruined.name)
  }.toMap
  private val siteDefinitions = catalog.sites.map(site => site.id -> site).toMap

  def project(
      gameId: String,
      loaded: LoadedGame,
      requestingPlayer: PlayerId
  ): GameProjection = projectFor(gameId, loaded, Some(requestingPlayer))

  def projectPublic(
      gameId: String,
      loaded: LoadedGame
  ): GameProjection = projectFor(gameId, loaded, None)

  private def projectFor(
      gameId: String,
      loaded: LoadedGame,
      requestingPlayer: Option[PlayerId]
  ): GameProjection =
    loaded.state match {
      case NoGame =>
        GameProjection(
          gameId,
          loaded.nextSequence,
          "not-started",
          None,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          ready = false,
          completed = false
        )
      case progress: InProgress =>
        val order = turnOrder(progress.plan.participants,
          progress.plan.firstPlayer)
        val awaitingAdviser =
          progress.placements.size == progress.adviserChoices.size + 1
        val active =
          if (awaitingAdviser)
            order(progress.adviserChoices.size).playerId
          else order(progress.placements.size).playerId
        val phase =
          if (awaitingAdviser) "awaiting-adviser" else "awaiting-pawn"
        val controls =
          if (!requestingPlayer.contains(active)) Vector.empty
          else if (awaitingAdviser) Vector("chooseAdviser")
          else Vector("placePawn")
        val privateCards =
          if (requestingPlayer.contains(active) && awaitingAdviser) {
            val participantIndex = progress.plan.participants
              .indexWhere(participant =>
                requestingPlayer.contains(participant.playerId))
            progress.plan.denizenOrder
              .slice(6 + participantIndex * 3, 9 + participantIndex * 3)
              .map(id => cardDetails(id, Some(Orientation.FaceUp), hidden = false))
          } else Vector.empty
        val decision = Option.when(privateCards.nonEmpty)(
          PendingCardDecisionProjection(
            CardDecisionIds.startingAdviser(active,
              progress.adviserChoices.size).value,
            "starting-adviser", active.value,
            "Choose your starting adviser",
            Vector("Move exactly one adviser to Keep.",
              "The remaining candidates are discarded."),
            privateCards,
            1, 1, orderingRequired = false,
            privateCards.map(card => card.cardId ->
              Vector(CardResolutionProjection("starting-adviser"))).toMap))
        GameProjection(
          gameId,
          loaded.nextSequence,
          phase,
          Some(active.value),
          players(progress.plan.participants),
          world(progress.plan.orderedSites),
          progress.placements.map(placement =>
            PawnLocationProjection(
              placement.playerId.value,
              placement.siteId.value
            )),
          controls,
          ready = false,
          completed = false,
          pendingCardDecision = decision
        )
      case Ready(value) =>
        val current = value.game.current
        val active = current.players.find(
          _.player == current.turn.activePlayer).get
        val site = active.pawnSite.flatMap(current.map.sites.get)
        val controls =
          if (!requestingPlayer.contains(active.player)) Vector.empty
          else if (Rest.validateBegin(Ready(value), active.player).isRight)
            Vector("beginRest")
          else if (current.turn.phase == Phase.Rest && current.pending.isEmpty)
            Vector("finishRest")
          else if (current.turn.phase != Phase.Wake) Vector.empty
          else {
            val takeControls = active.pawnSite.toVector.flatMap { siteId =>
              Vector(
                Option.when(TakeWealthRules.validate(value, active, siteId,
                  WakeResource.Favor).isRight)("takeFavor"),
                Option.when(TakeWealthRules.validate(value, active, siteId,
                  WakeResource.Secret).isRight)("takeSecret")
              ).flatten
            }
            takeControls :+ "endWake"
          }
        val setupPlayers = value.game.current.players.map { player =>
          SetupPlayerProjection(
            player.player.value,
            safeLabel(player.player.value),
            "exile",
            value.playerColors(player.player).value
          )
        }
        val pendingDecision = current.pending.collect {
          case search: PendingProcedure.Search
              if requestingPlayer.contains(search.actor) =>
            PendingCardDecisionProjection(
              search.decision.value, "search", search.actor.value,
              "Resolve Search",
              Vector("Move exactly one card to Keep.",
                "Remaining cards are discarded from left to right."),
              search.drawn.map(cardDetails(_, Some(Orientation.FaceUp), hidden = false)),
              1, 1, orderingRequired = true,
              search.drawn.map { card => card.value ->
                groupedResolutions(SearchRules.legalPlacements(
                  catalog, value, search, card))
              }.toMap)
        }
        GameProjection(
          gameId,
          loaded.nextSequence,
          current.pending match {
            case Some(_: PendingProcedure.Search) if pendingDecision.nonEmpty =>
              "search-decision"
            case Some(_: PendingProcedure.Search) => "search-waiting"
            case _ => current.turn.phase match {
            case Phase.Wake => "wake"
            case Phase.Act => "act-action-selection"
            case Phase.Rest => "rest"
            }
          },
          Some(value.game.current.turn.activePlayer.value),
          setupPlayers,
          Vector(
            region("cradle", value.game.current.map.cradle,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Cradle)),
            region("provinces", value.game.current.map.provinces,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Provinces)),
            region("hinterland", value.game.current.map.hinterland,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Hinterland))
          ),
          value.game.current.players.flatMap(player =>
            player.pawnSite.map(site =>
              PawnLocationProjection(player.player.value, site.value))),
          controls,
          ready = true,
          completed = true,
          Some(ActivePlayerResourcesProjection(
            active.board.favor,
            active.board.faceUpSecrets,
            active.board.faceDownSecrets,
            active.board.supply.supply
          )),
          active.pawnSite.flatMap(siteId => site.map(state =>
            CurrentSiteResourcesProjection(
              siteId.value,
              state.tokens.favor,
              state.tokens.secrets
            ))),
          actionSelectionOpen = current.turn.phase == Phase.Act && current.pending.isEmpty,
          actionFamilies =
            if (current.turn.phase == Phase.Act)
              Vector("Search", "Travel", "Campaign", "Muster", "Trade",
                "Forge", "Recover", "Challenge")
            else Vector.empty,
          legalTravelDestinations =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              TravelRules.legalDestinations(catalog, value, active).map {
                case (siteId, cost) =>
                  LegalTravelDestinationProjection(siteId.value, cost)
              }
            else Vector.empty,
          legalSearchSources =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              active.pawnSite.flatMap(current.map.regionOf).toVector.flatMap { origin =>
                Vector(SearchSource.WorldDeck,
                  SearchSource.RegionalDiscard(origin)).flatMap { source =>
                  SearchRules.cost(value, source, origin).toOption
                    .filter(_ <= active.board.supply.supply)
                    .filter(_ => SearchRules.draw(value, source, origin)
                      .exists(_.nonEmpty)).map { cost => source match {
                      case SearchSource.WorldDeck =>
                        LegalSearchSourceProjection("world", None, cost)
                      case SearchSource.RegionalDiscard(region) =>
                        LegalSearchSourceProjection(
                          "regional-discard", Some(region.key), cost)
                    }}
                }
              }
            else Vector.empty,
          legalMusters =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              Economy.legalMuster(catalog, value, active).map { result =>
                  LegalMusterProjection(result.target.kind, result.target.id.value,
                    economyLabel(result.target), result.suit.key, result.supplySpent,
                    result.warbandsGained)
              }
            else Vector.empty,
          legalTrades =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              Economy.legalTrades(catalog, value, active).map { result =>
                  LegalTradeProjection(result.target.kind, result.target.id.value,
                    economyLabel(result.target), result.suit.key,
                    result.resource match {
                      case oathdigital.setup.TradeResource.Favor => "favor"
                      case oathdigital.setup.TradeResource.Secret => "secret"
                    }, result.supplySpent, result.gained)
              }
            else Vector.empty,
          pendingCardDecision = pendingDecision,
          worldDeckCount = current.commonCards.worldDeck.size,
          // Card backs/types are public; the World Deck top is its head.
          worldDeckTopCardKind = current.commonCards.worldDeck.headOption.map(cardKind),
          playerBoards = viewerOrderedBoards(value, requestingPlayer)
        )
    }

  private def economyLabel(target: EconomyTargetRef): String = target match {
    case EconomyTargetRef.Denizen(id) =>
      denizenNames.getOrElse(id, safeLabel(id.value))
    case EconomyTargetRef.Edifice(id) =>
      edificeNames.get(id).map(_._2).getOrElse(safeLabel(id.value))
  }

  private def players(
      participants: Vector[FirstGameParticipant]
  ): Vector[SetupPlayerProjection] =
    participants.map { participant =>
      SetupPlayerProjection(
        participant.playerId.value,
        safeLabel(participant.playerId.value),
        "exile",
        participant.color.value
      )
    }

  private def world(sites: Vector[SiteId]): Vector[SetupRegionProjection] =
    Vector(
      region("cradle", sites.take(2)),
      region("provinces", sites.slice(2, 5)),
      region("hinterland", sites.slice(5, 8))
    )

  private def region(
      id: String,
      sites: Vector[SiteId],
      states: Map[SiteId, SiteState] = Map.empty,
      discard: Vector[CardId] = Vector.empty
  ): SetupRegionProjection =
    SetupRegionProjection(
      id,
      sites.map(site =>
        siteProjection(site, states.get(site))),
      discard.size,
      // Regional discards are faceup public piles; the final element is top.
      discard.lastOption.map(cardKind)
    )

  private def siteProjection(
      siteId: SiteId,
      state: Option[SiteState]
  ): SetupSiteProjection = {
    val definition = siteDefinitions.get(siteId)
    SetupSiteProjection(
      siteId.value,
      siteNames.getOrElse(siteId, safeLabel(siteId.value)),
      state.fold(0)(_.tokens.favor),
      state.fold(0)(_.tokens.secrets),
      definition.fold(0)(_.capacity),
      definition.fold(0)(_.relicSlots),
      state.toVector.flatMap(_.denizens).map { denizen =>
        val label = denizen match {
          case value: DenizenState =>
            denizenNames.getOrElse(value.id, safeLabel(value.id.value))
          case value: EdificeState =>
            edificeNames.get(value.id).fold(safeLabel(value.id.value)) {
              case (intact, ruined) => value.side match {
                case EdificeSide.Intact => intact
                case EdificeSide.Ruined => ruined
              }
            }
        }
        val details = denizen match {
          case value: DenizenState => Some(cardDetails(value.id,
            Some(value.orientation), hidden = false).copy(
              favor = value.tokens.favor, secrets = value.tokens.secrets))
          case value: EdificeState => Some(CardDetailsProjection(
            value.id.value, "edifice", label,
            suit = catalog.edifices.find(_.id.value == value.id.value).map(_.suit.value),
            side = Some(value.side match {
              case EdificeSide.Intact => "intact"
              case EdificeSide.Ruined => "ruined"
            }), favor = value.tokens.favor, secrets = value.tokens.secrets))
        }
        SiteCardProjection(denizen.id.value, label, details)
      },
      // Site relics are facedown (CR pp. 6, 25; NF p. 14). Public and
      // player projections expose only their count; recovery's peek does not
      // yet have an authorized private projection boundary.
      SiteRelicsProjection(state.fold(0)(_.relics.size)),
      definition.fold(0)(_.defense),
      definition.flatMap(site => Option.when(site.forgeRequirements.isEmpty)(site.recoverDifficulty).flatten),
      definition.flatMap(_.forgeRequirements).map(tokens =>
        ForgeCostProjection(tokens.favor, tokens.secrets)),
      definition.toVector.flatMap(_.handlers).map(sitePower)
    )
  }

  private def cardKind(card: CardId): String = card match {
    case _: VisionId => "vision"
    case _ => "denizen"
  }

  private def sitePower(handler: String): SitePowerProjection = {
    val kind = handler.split('.').lastOption.getOrElse(handler)
    // Display-only vocabulary: Combined Rulebook p.31 and New Foundations
    // pp.10-11. Executable behavior remains in typed rule handlers.
    val known = Map(
      "coast" -> ("Coast", "Travel along the Coast route."),
      "mountain" -> ("Mountain", "Travel here costs additional Supply."),
      "river" -> ("River", "Part of the River route."),
      "island" -> ("Island", "Travel here follows Island travel rules."),
      "pass" -> ("Pass", "Travel through the Pass is restricted."),
      "plains" -> ("Plains", "This site has the Plains site power."))
    known.get(kind).fold(SitePowerProjection(kind, safeLabel(kind), None)) {
      case (label, description) => SitePowerProjection(kind, label, Some(description))
    }
  }

  private def groupedResolutions(
      placements: Vector[SearchPlacement]
  ): Vector[CardResolutionProjection] = {
    val orderedKeys = placements.map {
      case SearchPlacement.Discard => "discard" -> None
      case SearchPlacement.Site(_) => "site" -> Some("face-up")
      case SearchPlacement.Adviser(orientation, _) =>
        "adviser" -> Some(orientationName(orientation))
    }.distinct
    orderedKeys.map { case (kind, orientation) =>
      val matching = placements.filter {
        case SearchPlacement.Discard => kind == "discard"
        case SearchPlacement.Site(_) => kind == "site"
        case SearchPlacement.Adviser(value, _) =>
          kind == "adviser" && orientation.contains(orientationName(value))
      }
      val replacements = matching.flatMap {
        case SearchPlacement.Site(replace) => replace
        case SearchPlacement.Adviser(_, replace) => replace
        case SearchPlacement.Discard => None
      }.distinct
      val required = matching.nonEmpty && matching.forall {
        case SearchPlacement.Site(replace) => replace.nonEmpty
        case SearchPlacement.Adviser(_, replace) => replace.nonEmpty
        case SearchPlacement.Discard => false
      }
      CardResolutionProjection(kind, orientation, required,
        if (required) replacements.map(cardDetails(_, None, hidden = false))
        else Vector.empty)
    }
  }

  private def viewerOrderedBoards(
      ready: ReadyGame,
      viewer: Option[PlayerId]
  ): Vector[PlayerBoardProjection] = {
    val players = ready.game.current.players
    val start = viewer.flatMap(id => Option(players.indexWhere(_.player == id))
      .filter(_ >= 0)).getOrElse(0)
    (players.drop(start) ++ players.take(start)).map { player =>
      val owns = viewer.contains(player.player)
      PlayerBoardProjection(player.player.value, player.board.warbands,
        player.board.favor, player.board.faceUpSecrets, player.board.faceDownSecrets,
        player.board.supply.supply, player.pawnSite.map(_.value),
        player.advisers.map(card => if (adviserOrientation(card) == Orientation.FaceDown && !owns)
          hiddenCard(card.id, "adviser") else cardDetails(card.id,
            Some(adviserOrientation(card)), hidden = false)),
        player.relics.map(card => if (card.orientation == Orientation.FaceDown && !owns)
          hiddenCard(card.id, "relic") else cardDetails(card.id,
            Some(card.orientation), hidden = false)),
        player.revealedVision.map(card => cardDetails(card.id,
          Some(card.orientation), hidden = false)))
    }
  }

  private def hiddenCard(id: CardId, kind: String) = CardDetailsProjection(
    "hidden", kind, s"Facedown $kind", orientation = Some("face-down"), hidden = true)

  private def adviserOrientation(card: AdviserState): Orientation = card match {
    case value: DenizenState => value.orientation
    case value: VisionState => value.orientation
  }

  private def orientationName(value: Orientation): String = value match {
    case Orientation.FaceUp => "face-up"
    case Orientation.FaceDown => "face-down"
  }

  private def cardDetails(
      id: CardId,
      orientation: Option[Orientation],
      hidden: Boolean
  ): CardDetailsProjection = id match {
    case value: DenizenId => catalog.denizens.find(_.id.value == value.value).fold(
      CardDetailsProjection(value.value, "denizen", worldCardLabel(value),
        orientation = orientation.map(orientationName), hidden = hidden)) { d =>
      CardDetailsProjection(value.value, "denizen", d.name, Some(d.suit.value),
        Some(restrictionName(d.restrictions)), Some(d.rulesText),
        orientation.map(orientationName), hidden = hidden)
    }
    case value: VisionId => CardDetailsProjection(value.value, "vision",
      safeLabel(value.value), orientation = orientation.map(orientationName), hidden = hidden)
    case value: RelicId => catalog.relics.find(_.id.value == value.value).fold(
      CardDetailsProjection(value.value, "relic", safeLabel(value.value),
        orientation = orientation.map(orientationName), hidden = hidden)) { r =>
      CardDetailsProjection(value.value, "relic", r.name, rulesText = Some(r.rulesText),
        orientation = orientation.map(orientationName), relicValue = Some(r.value),
        defense = Some(r.defense), hidden = hidden)
    }
    case other => CardDetailsProjection(other.value, other.getClass.getSimpleName,
      safeLabel(other.value), orientation = orientation.map(orientationName), hidden = hidden)
  }

  private def restrictionName(value: oathdigital.catalog.CardRestrictions): String = value match {
    case oathdigital.catalog.CardRestrictions.Unrestricted => "unrestricted"
    case oathdigital.catalog.CardRestrictions.SiteOnly => "site-only"
    case oathdigital.catalog.CardRestrictions.AdviserOnly => "adviser-only"
    case oathdigital.catalog.CardRestrictions.LockedAdviserOnly => "locked-adviser-only"
  }

  private def turnOrder(
      participants: Vector[FirstGameParticipant],
      firstPlayer: PlayerId
  ): Vector[FirstGameParticipant] = {
    val start = participants.indexWhere(_.playerId == firstPlayer)
    participants.drop(start) ++ participants.take(start)
  }

  private def safeLabel(id: String): String =
    id.split(":").lastOption.getOrElse(id)
      .split("-").map(_.capitalize).mkString(" ")

  private def worldCardLabel(id: WorldCardId): String = id match {
    case value: DenizenId => denizenNames.getOrElse(value, safeLabel(value.value))
    case value: VisionId => safeLabel(value.value)
  }

}
