package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.engine.RecordedEvent
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathEvent._

final case class GameEventEnvelope(
    formatVersion: Int,
    gameId: String,
    sequence: Long,
    catalog: CatalogRef,
    eventType: String,
    event: OathEvent
)

/**
 * Explicit mixed vocabulary: v2 setup followed by v3 gameplay events.
 *
 * V1 remains owned by `SetupEventWire`; this dual reader/writer boundary keeps
 * its checked-in bytes unchanged instead of reinterpreting old payloads.
 */
object GameEventWire {
  import WireError._

  val FormatVersion: Int = 2
  val GameplayFormatVersion: Int = 3
  val SearchFormatVersion: Int = 4
  val RestFormatVersion: Int = 5
  val EconomyFormatVersion: Int = 6
  val RecoverFormatVersion: Int = 7
  val ForgeFormatVersion: Int = 8
  val BannerFormatVersion: Int = 9
  val MinorActionFormatVersion: Int = 10
  val NegotiationFormatVersion: Int = 11
  val VisionFormatVersion: Int = 12
  val RoundEndFormatVersion: Int = 13
  val MaxSafeSequence: Long = SetupEventWire.MaxSafeSequence
  val FirstGameStartedType = "setup.first-game-started"
  val PawnPlacedType = "setup.first-game-pawn-placed"
  val AdviserChosenType = "setup.starting-adviser-chosen"
  val FirstGameCompletedType = "setup.first-game-completed"
  val TakeWealthType = "gameplay.take-wealth"
  val WakeEndedType = "gameplay.wake-ended"
  val TraveledType = "gameplay.traveled"
  val MusteredType = "gameplay.mustered"
  val TradedType = "gameplay.traded"
  val SearchStartedType = "gameplay.search-started"
  val SearchCompletedType = "gameplay.search-completed"
  val RestStartedType = "gameplay.rest-started"
  val RestCompletedType = "gameplay.rest-completed"
  val RecoverRolledType = "gameplay.recover-rolled"
  val RecoverStoppedType = "gameplay.recover-stopped"
  val RelicRecoveredType = "gameplay.relic-recovered"
  val ForgeStartedType = "gameplay.forge-started"
  val ForgeCompletedType = "gameplay.forge-completed"
  val BannerChallengeStartedType = "gameplay.banner-challenge-started"
  val BannerRibbonChoiceMadeType = "gameplay.banner-ribbon-choice-made"
  val BannerChallengeCompletedType = "gameplay.banner-challenge-completed"
  val BannerResourcePlacedType = "gameplay.banner-resource-placed"
  val FacedownAdviserDiscardedType = "gameplay.facedown-adviser-discarded"
  val FacedownAdviserPlayedType = "gameplay.facedown-adviser-played"
  val SiteRelicsPeekedType = "gameplay.site-relics-peeked"
  val OwnedRelicRevealedType = "gameplay.owned-relic-revealed"
  val WarbandsMovedType = "gameplay.warbands-moved"
  val NegotiationStartedType = "gameplay.negotiation-started"
  val NegotiationTermsReplacedType = "gameplay.negotiation-terms-replaced"
  val NegotiationAcceptedType = "gameplay.negotiation-accepted"
  val NegotiationDeclinedType = "gameplay.negotiation-declined"
  val NegotiationCompletedType = "gameplay.negotiation-completed"
  val CampaignStartedType = "gameplay.campaign-started"
  val CampaignPlanChosenType = "gameplay.campaign-plan-chosen"
  val CampaignPlansFinishedType = "gameplay.campaign-plans-finished"
  val CampaignSacrificedType = "gameplay.campaign-sacrificed"
  val CampaignConqueredType = "gameplay.campaign-conquered"
  val CampaignRaidedType = "gameplay.campaign-raided"
  val CampaignRaidPawnRelocatedType = "gameplay.campaign-raid-pawn-relocated"
  val BanditsRefilledType = "gameplay.bandits-refilled"
  val OathkeeperChangedType = "gameplay.oathkeeper-changed"
  val OathkeeperRecipientChoiceStartedType =
    "gameplay.oathkeeper-recipient-choice-started"
  val OathkeeperRecipientChosenType =
    "gameplay.oathkeeper-recipient-chosen"
  val UsurperFlippedType = "gameplay.usurper-flipped"
  val UsurperVictoryType = "gameplay.usurper-victory"
  val VisionRevealedType = "gameplay.vision-revealed"
  val ConspiracyStartedType = "gameplay.conspiracy-started"
  val ConspiracySecretSiteChosenType = "gameplay.conspiracy-secret-site-chosen"
  val ConspiracyCompletedType = "gameplay.conspiracy-completed"
  val VisionVictoryType = "gameplay.vision-victory"
  val RoundEndedType = "gameplay.round-ended"
  val WarExhaustionResolvedType = "gameplay.war-exhaustion-resolved"

  /** Encodes one event at its absolute position in the game stream. */
  def encodeEvent(
      gameId: String,
      catalog: CatalogRef,
      sequence: Long,
      event: OathEvent
  ): Either[WireError, ujson.Value] =
    encode(
      GameEventEnvelope(
        formatVersion(event),
        gameId,
        sequence,
        catalog,
        discriminator(event),
        event
      )
    )

  def encode(
      envelope: GameEventEnvelope
  ): Either[WireError, ujson.Value] =
    validateEnvelope(envelope).map { _ =>
      ujson.Obj(
        "formatVersion" -> envelope.formatVersion,
        "gameId" -> envelope.gameId,
        "sequence" -> ujson.Num(envelope.sequence.toDouble),
        "catalog" -> encodeCatalog(envelope.catalog),
        "eventType" -> envelope.eventType,
        "payload" -> encodePayload(envelope.event)
      )
    }

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      events: Vector[RecordedEvent[OathEvent]]
  ): Either[WireError, String] = {
    val startSequence = events.headOption.map(_.index).getOrElse(0L)
    encodeStream(gameId, catalog, startSequence, events)
  }

  def encodeStream(
      gameId: String,
      catalog: CatalogRef,
      startSequence: Long,
      events: Vector[RecordedEvent[OathEvent]]
  ): Either[WireError, String] =
    validateSequence(startSequence, "$[*].sequence").flatMap { _ =>
      traverse(events.zipWithIndex) { case (record, position) =>
        val expected =
          if (startSequence > MaxSafeSequence - position.toLong)
            None
          else Some(startSequence + position.toLong)
        if (!expected.contains(record.index))
          Left(
            InvalidSequence(
              s"$$[$position].sequence",
              expected.getOrElse(MaxSafeSequence),
              record.index
            )
          )
        else
          encodeEvent(gameId, catalog, record.index, record.event)
      }.map(values => ujson.write(ujson.Arr.from(values), indent = 2))
    }

  def decodeStream(
      json: String
  ): Either[WireError, Vector[GameEventEnvelope]] =
    try {
      ujson.read(json) match {
        case array: ujson.Arr =>
          traverse(array.value.zipWithIndex.toVector) {
            case (value, position) => decode(value, s"$$[$position]")
          }.flatMap(validateStream)
        case _ => Left(WrongType("$", "expected an array"))
      }
    } catch {
      case NonFatal(error) =>
        Left(
          MalformedJson(
            "$",
            Option(error.getMessage).getOrElse("invalid JSON")
          )
        )
    }

  def decode(
      value: ujson.Value,
      path: String = "$"
  ): Either[WireError, GameEventEnvelope] = {
    value match {
      case obj: ujson.Obj =>
        for {
          version <- formatVersionField(obj, path)
          _ <-
            if (version == FormatVersion || version == GameplayFormatVersion ||
                version == SearchFormatVersion || version == RestFormatVersion ||
                version == EconomyFormatVersion || version == RecoverFormatVersion ||
                version == ForgeFormatVersion || version == BannerFormatVersion ||
                version == MinorActionFormatVersion || version == NegotiationFormatVersion ||
                version == VisionFormatVersion || version == RoundEndFormatVersion)
              Right(())
            else
              Left(
                UnsupportedFormatVersion(
                  s"$path.formatVersion",
                  version,
                  FormatVersion
                )
              )
          gameId <- stringField(obj, "gameId", path)
          _ <-
            if (gameId.trim.nonEmpty) Right(())
            else Left(InvalidValue(s"$path.gameId", "gameId must not be blank"))
          sequence <- safeIntegerField(obj, "sequence", path)
          refValue <- requiredField(obj, "catalog", path)
          ref <- decodeCatalog(refValue, s"$path.catalog")
          eventType <- stringField(obj, "eventType", path)
          payload <- requiredField(obj, "payload", path)
          event <- decodePayload(
            eventType,
            payload,
            s"$path.payload",
            ref
          )
          _ <- validateEventVersion(version, eventType, path)
        } yield GameEventEnvelope(
          version,
          gameId,
          sequence,
          ref,
          eventType,
          event
        )
      case _ => Left(WrongType(path, "expected an object"))
    }
  }

  private def validateStream(
      envelopes: Vector[GameEventEnvelope]
  ): Either[WireError, Vector[GameEventEnvelope]] =
    envelopes.headOption match {
      case None => Right(envelopes)
      case Some(first) =>
        envelopes.zipWithIndex.collectFirst {
          case (envelope, index)
              if first.sequence > MaxSafeSequence - index.toLong ||
                envelope.sequence != first.sequence + index.toLong =>
            InvalidSequence(
              s"$$[$index].sequence",
              first.sequence + index.toLong,
              envelope.sequence
            )
          case (envelope, index) if envelope.gameId != first.gameId =>
            InvalidValue(
              s"$$[$index].gameId",
              s"must match stream game ID '${first.gameId}'"
            )
          case (envelope, index) if envelope.catalog != first.catalog =>
            CatalogMismatch(
              s"$$[$index].catalog",
              first.catalog,
              envelope.catalog
            )
        }.toLeft(envelopes)
    }

  private def validateEnvelope(
      envelope: GameEventEnvelope
  ): Either[WireError, Unit] =
    for {
      _ <-
        if (envelope.formatVersion == formatVersion(envelope.event)) Right(())
        else
          Left(
            UnsupportedFormatVersion(
              "$.formatVersion",
              envelope.formatVersion,
              formatVersion(envelope.event)
            )
          )
      _ <-
        if (envelope.gameId.trim.nonEmpty) Right(())
        else Left(InvalidValue("$.gameId", "gameId must not be blank"))
      _ <- validateSequence(envelope.sequence, "$.sequence")
      _ <-
        if (envelope.eventType == discriminator(envelope.event)) Right(())
        else
          Left(
            InvalidValue(
              "$.eventType",
              s"must be '${discriminator(envelope.event)}' for this event"
            )
          )
      _ <- validateEventCatalog(envelope.event, envelope.catalog, "$")
    } yield ()

  private def discriminator(event: OathEvent): String =
    event match {
      case _: FirstGameStarted => FirstGameStartedType
      case _: GamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
      case _: WealthTaken => TakeWealthType
      case _: WakeEnded => WakeEndedType
      case _: Traveled => TraveledType
      case _: Mustered => MusteredType
      case _: Traded => TradedType
      case _: SearchStarted => SearchStartedType
      case _: SearchCompleted => SearchCompletedType
      case _: RestStarted => RestStartedType
      case _: RestCompleted => RestCompletedType
      case _: RoundEnded => RoundEndedType
      case _: WarExhaustionResolved => WarExhaustionResolvedType
      case _: RecoverRolled => RecoverRolledType
      case _: RecoverStopped => RecoverStoppedType
      case _: RelicRecovered => RelicRecoveredType
      case _: ForgeStarted => ForgeStartedType
      case _: ForgeCompleted => ForgeCompletedType
      case _: BannerChallengeStarted => BannerChallengeStartedType
      case _: BannerRibbonChoiceMade => BannerRibbonChoiceMadeType
      case _: BannerChallengeCompleted => BannerChallengeCompletedType
      case _: BannerResourcePlaced => BannerResourcePlacedType
      case _: FacedownAdviserDiscarded => FacedownAdviserDiscardedType
      case _: FacedownAdviserPlayed => FacedownAdviserPlayedType
      case _: SiteRelicsPeeked => SiteRelicsPeekedType
      case _: OwnedRelicRevealed => OwnedRelicRevealedType
      case _: WarbandsMoved => WarbandsMovedType
      case _: NegotiationStarted => NegotiationStartedType
      case _: NegotiationTermsReplaced => NegotiationTermsReplacedType
      case _: NegotiationAccepted => NegotiationAcceptedType
      case _: NegotiationDeclined => NegotiationDeclinedType
      case _: NegotiationCompleted => NegotiationCompletedType
      case _: CampaignStarted => CampaignStartedType
      case _: CampaignPlanChosen => CampaignPlanChosenType
      case _: CampaignPlansFinished => CampaignPlansFinishedType
      case _: CampaignSacrificed => CampaignSacrificedType
      case _: CampaignConquered => CampaignConqueredType
      case _: CampaignRaided => CampaignRaidedType
      case _: CampaignRaidPawnRelocated => CampaignRaidPawnRelocatedType
      case _: BanditsRefilled => BanditsRefilledType
      case _: OathkeeperChanged => OathkeeperChangedType
      case _: OathkeeperRecipientChoiceStarted =>
        OathkeeperRecipientChoiceStartedType
      case _: OathkeeperRecipientChosen => OathkeeperRecipientChosenType
      case _: UsurperFlipped => UsurperFlippedType
      case _: UsurperVictory => UsurperVictoryType
      case _: VisionRevealed => VisionRevealedType
      case _: ConspiracyStarted => ConspiracyStartedType
      case _: ConspiracySecretSiteChosen => ConspiracySecretSiteChosenType
      case _: ConspiracyCompleted => ConspiracyCompletedType
      case _: VisionVictory => VisionVictoryType
    }

  private def formatVersion(event: OathEvent): Int = event match {
    case _: RoundEnded | _: WarExhaustionResolved => RoundEndFormatVersion
    case _: VisionRevealed | _: ConspiracyStarted |
        _: ConspiracySecretSiteChosen | _: ConspiracyCompleted |
        _: VisionVictory => VisionFormatVersion
    case _: NegotiationStarted | _: NegotiationTermsReplaced |
        _: NegotiationAccepted | _: NegotiationDeclined | _: NegotiationCompleted =>
      NegotiationFormatVersion
    case _: FacedownAdviserDiscarded | _: FacedownAdviserPlayed |
        _: SiteRelicsPeeked | _: OwnedRelicRevealed | _: WarbandsMoved =>
      MinorActionFormatVersion
    case _: BannerChallengeStarted | _: BannerRibbonChoiceMade |
        _: BannerChallengeCompleted | _: BannerResourcePlaced => BannerFormatVersion
    case _: ForgeStarted | _: ForgeCompleted => ForgeFormatVersion
    case _: WealthTaken | _: WakeEnded | _: Traveled => GameplayFormatVersion
    case _: Mustered | _: Traded => EconomyFormatVersion
    case _: SearchStarted | _: SearchCompleted => SearchFormatVersion
    case _: RestStarted | _: RestCompleted => RestFormatVersion
    case _: RecoverRolled | _: RecoverStopped | _: RelicRecovered |
        _: CampaignStarted | _: CampaignPlanChosen | _: CampaignPlansFinished | _: CampaignSacrificed | _: CampaignConquered | _: CampaignRaided | _: CampaignRaidPawnRelocated |
        _: BanditsRefilled => RecoverFormatVersion
    case _: OathkeeperChanged | _: OathkeeperRecipientChoiceStarted |
        _: OathkeeperRecipientChosen | _: UsurperFlipped | _: UsurperVictory =>
      RecoverFormatVersion
    case _ => FormatVersion
  }

  private def encodePayload(event: OathEvent): ujson.Value =
    event match {
      case FirstGameStarted(plan) => encodePlan(plan)
      case GamePawnPlaced(playerId, siteId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value
        )
      case StartingAdviserChosen(playerId, adviserId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "adviserId" -> adviserId.value
        )
      case FirstGameCompleted => ujson.Obj()
      case WealthTaken(playerId, siteId, resource) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value,
          "resource" -> (resource match {
            case WakeResource.Favor => "favor"
            case WakeResource.Secret => "secret"
          })
        )
      case WakeEnded(playerId) =>
        ujson.Obj("playerId" -> playerId.value)
      case Traveled(playerId, source, destination, supplySpent) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "sourceSiteId" -> source.value,
          "destinationSiteId" -> destination.value,
          "supplySpent" -> supplySpent
        )
      case Mustered(playerId, site, target, suit, spent, gained) =>
        ujson.Obj("playerId" -> playerId.value, "siteId" -> site.value,
          "target" -> encodeCardRef(target.id), "suit" -> suit.key,
          "supplySpent" -> spent, "warbandsGained" -> gained)
      case Traded(playerId, site, target, suit, resource, spent, gained) =>
        ujson.Obj("playerId" -> playerId.value, "siteId" -> site.value,
          "target" -> encodeCardRef(target.id), "suit" -> suit.key,
          "resource" -> (resource match {
            case TradeResource.Favor => "favor"
            case TradeResource.Secret => "secret"
          }), "supplySpent" -> spent, "gained" -> gained)
      case SearchStarted(playerId, decision, source, origin, spent, drawn) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "decisionId" -> decision.value,
          "source" -> encodeSearchSource(source),
          "origin" -> origin.key,
          "supplySpent" -> spent,
          "drawn" -> ujson.Arr.from(drawn.map(encodeWorldCard))
        )
      case SearchCompleted(playerId, decision, kept, discarded, placement) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "decisionId" -> decision.value,
          "kept" -> encodeWorldCard(kept),
          "discardedInOrder" -> ujson.Arr.from(discarded.map(encodeWorldCard)),
          "placement" -> encodeSearchPlacement(placement)
        )
      case RestStarted(playerId) => ujson.Obj("playerId" -> playerId.value)
      case RestCompleted(playerId, favor, secrets, supply, active, round, limited) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "returnedFavor" -> ujson.Obj.from(favor.toVector.sortBy(_._1.key)
            .map { case (suit, amount) => suit.key -> ujson.Num(amount) }),
          "returnedSecrets" -> secrets,
          "refreshedSupply" -> supply,
          "postRestActivePlayerId" -> active.value,
          "completedRound" -> round,
          "usurperLimited" -> limited
        )
      case RoundEnded(completed, next) => ujson.Obj(
        "completedRound" -> completed,
        "nextRound" -> next.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
      case WarExhaustionResolved(winner, kind, vision, candidates) => ujson.Obj(
        "winnerPlayerId" -> winner.value,
        "victoryKind" -> kind.key,
        "visionId" -> vision.fold[ujson.Value](ujson.Null)(v => ujson.Str(v.value)),
        "randomCandidatePlayerIds" -> stringArray(candidates.map(_.value)))
      case RecoverRolled(player, decision, site, spent, dice) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "siteId" -> site.value, "supplySpent" -> spent,
        "dice" -> ujson.Arr.from(dice.map(face => ujson.Str(encodeDefenseFace(face)))))
      case RecoverStopped(player, decision) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value)
      case RelicRecovered(player, decision, site, relic) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "siteId" -> site.value, "relicId" -> relic.value)
      case ForgeStarted(player, decision, site, targets, cost, spent) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "siteId" -> site.value, "supplySpent" -> spent,
        "cost" -> ujson.Obj("favor" -> cost.favor, "secrets" -> cost.secrets),
        "targets" -> ujson.Arr.from(targets.map(t => ujson.Obj(
          "siteId" -> t.siteId.value, "denizenId" -> t.denizenId.value))))
      case ForgeCompleted(player, decision, site, assignments, relic) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "siteId" -> site.value, "relicId" -> relic.value,
        "assignments" -> ujson.Arr.from(assignments.map(a => ujson.Obj(
          "siteId" -> a.target.siteId.value,
          "denizenId" -> a.target.denizenId.value,
          "resource" -> a.resource.key))))
      case BannerChallengeStarted(player, decision, banner, holder, prior, spent,
          favor, sites) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "banner" -> banner.key,
        "priorHolderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p => ujson.Str(p.value)),
        "priorResources" -> prior, "supplySpent" -> spent,
        "automaticFavorReturns" -> ujson.Arr.from(favor.map(s => ujson.Str(s.key))),
        "automaticSecretSites" -> ujson.Arr.from(sites.map(s => ujson.Str(s.value))))
      case BannerRibbonChoiceMade(player, decision, banner, site, sites) =>
        ujson.Obj("playerId" -> player.value, "decisionId" -> decision.value,
          "banner" -> banner.key,
          "secretSiteId" -> site.value,
          "automaticSecretSites" -> ujson.Arr.from(sites.map(s => ujson.Str(s.value))))
      case BannerChallengeCompleted(player, decision, banner, holder, prior,
          placed, favor, sites, returned) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "banner" -> banner.key,
        "priorHolderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p => ujson.Str(p.value)),
        "priorResources" -> prior, "placedResources" -> placed,
        "favorReturnOrder" -> ujson.Arr.from(favor.map(s => ujson.Str(s.key))),
        "secretSiteOrder" -> ujson.Arr.from(sites.map(s => ujson.Str(s.value))),
        "secretsReturnedToHolder" -> returned)
      case BannerResourcePlaced(player, banner, amount) => ujson.Obj(
        "playerId" -> player.value, "banner" -> banner.key, "amount" -> amount)
      case FacedownAdviserDiscarded(player, adviser, destination) =>
        ujson.Obj("playerId" -> player.value, "adviser" -> encodeWorldCard(adviser),
          "destination" -> destination.key)
      case FacedownAdviserPlayed(player, adviser, placement, favor, world, edifices) =>
        ujson.Obj("playerId" -> player.value, "adviser" -> encodeWorldCard(adviser),
          "placement" -> encodeSearchPlacement(placement), "favorGained" -> favor,
          "discardedWorld" -> ujson.Arr.from(world.map(encodeWorldCard)),
          "discardedEdifices" -> ujson.Arr.from(edifices.map(e => ujson.Str(e.value))))
      case SiteRelicsPeeked(player, site, relics) =>
        ujson.Obj("playerId" -> player.value, "siteId" -> site.value,
          "relics" -> ujson.Arr.from(relics.map(r => ujson.Str(r.value))))
      case OwnedRelicRevealed(player, relic) =>
        ujson.Obj("playerId" -> player.value, "relicId" -> relic.value)
      case WarbandsMoved(player, site, toSite, amount, board, atSite) =>
        ujson.Obj("playerId" -> player.value, "siteId" -> site.value,
          "toSite" -> toSite, "amount" -> amount,
          "priorBoardWarbands" -> board, "priorSiteWarbands" -> atSite)
      case NegotiationStarted(player, decision, site, participants) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "siteId" -> site.value, "participants" -> stringArray(participants.map(_.value)))
      case NegotiationTermsReplaced(player, decision, terms) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "terms" -> encodeNegotiationTerms(terms))
      case NegotiationAccepted(player, decision) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value)
      case NegotiationDeclined(player, decision) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value)
      case NegotiationCompleted(player, decision, participants, terms) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "participants" -> stringArray(participants.map(_.value)),
        "terms" -> ujson.Arr.from(participants.map(author => ujson.Obj(
          "authorPlayerId" -> author.value,
          "terms" -> encodeNegotiationTerms(terms(author))))))
      case CampaignStarted(player, decision, sites, defender, spent, force,
          kind, raidTargets) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "campaignKind" -> kind.key,
        "targetSiteIds" -> ujson.Arr.from(sites.map(site => ujson.Str(site.value))),
        "raidTargets" -> ujson.Arr.from(raidTargets.map(encodeCampaignRaidTarget)),
        "defender" -> (defender match {
          case CampaignDefender.Bandits => ujson.Obj("kind" -> "bandits")
          case CampaignDefender.Player(id) => ujson.Obj(
            "kind" -> "player", "playerId" -> id.value)
        }),
        "supplySpent" -> spent, "force" -> force)
      case CampaignPlanChosen(player, decision, source, handler, side, costs,
          effects) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "source" -> encodeCampaignPlanSource(source),
        "handlerId" -> handler, "side" -> encodeCampaignPlanSide(side),
        "costs" -> ujson.Arr.from(costs.map(encodeCampaignPlanCost)),
        "effects" -> ujson.Arr.from(effects.map(encodeCampaignPlanEffect)))
      case CampaignPlansFinished(player, decision, side, sources, handlerIds, effects,
          dice, attack, skulls) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "side" -> encodeCampaignPlanSide(side),
        "orderedSources" -> ujson.Arr.from(sources.map(encodeCampaignPlanSource)),
        "orderedHandlerIds" -> ujson.Arr.from(handlerIds.map(ujson.Str(_))),
        "effects" -> ujson.Arr.from(effects.map(encodeCampaignPlanEffect)),
        "attackDice" -> ujson.Arr.from(dice.map(d => ujson.Str(encodeAttackFace(d)))),
        "attack" -> attack, "skullLosses" -> skulls)
      case CampaignSacrificed(player, decision, sacrificed, dice, attack,
          defense, skulls, victorious, policyId, losses) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "sacrificed" -> sacrificed,
        "defenseDice" -> ujson.Arr.from(dice.map(d => ujson.Str(encodeDefenseFace(d)))),
        "attack" -> attack, "defense" -> defense, "skullLosses" -> skulls,
        "victorious" -> victorious,
        "losingForcePolicyId" -> policyId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
        "losingForces" -> ujson.Arr.from(losses.map(encodeLosingForceEffect)))
      case CampaignConquered(player, decision, policyId, losses, allocations) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "losingForcePolicyId" -> policyId,
        "losingForces" -> ujson.Arr.from(losses.map(encodeLosingForceEffect)),
        "allocations" -> ujson.Arr.from(allocations.map { allocation =>
          ujson.Obj("siteId" -> allocation.site.value,
            "count" -> allocation.count)
        }))
      case CampaignRaided(player, decision, policyId, loss, relics, banners,
          advisers, adviserRegion, boxedConspiracy, discardedRelics, burned,
          returned, darkestSecretBurned) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "losingForcePolicyId" -> policyId,
        "defenderLoss" -> ujson.Obj("playerId" -> loss.playerId.value,
          "killed" -> loss.killed, "returned" -> loss.returned),
        "takenRelics" -> ujson.Arr.from(relics.map(r => ujson.Str(r.value))),
        "takenBanners" -> ujson.Arr.from(banners.map(b => ujson.Str(b.key))),
        "discardedAdvisers" -> ujson.Arr.from(advisers.map(encodeWorldCard)),
        "adviserDiscardRegion" -> adviserRegion.key,
        "boxedConspiracy" -> boxedConspiracy.fold[ujson.Value](ujson.Null)(id =>
          ujson.Str(id.value)),
        "discardedRelics" -> ujson.Arr.from(discardedRelics.map(r => ujson.Str(r.value))),
        "favorBurned" -> burned,
        "darkestSecretBurned" -> darkestSecretBurned,
        "bannerFavorReturned" -> ujson.Obj.from(returned.toVector.sortBy(_._1.key)
          .map { case (suit, amount) => suit.key -> ujson.Num(amount) }))
      case CampaignRaidPawnRelocated(player, decision, defender, origin, destination) =>
        ujson.Obj("playerId" -> player.value, "decisionId" -> decision.value,
          "defenderPlayerId" -> defender.value, "originSiteId" -> origin.value,
          "destinationSiteId" -> destination.value)
      case BanditsRefilled(sites) => ujson.Obj("sites" -> ujson.Arr.from(
        sites.map { case (site, count) =>
          ujson.Obj("siteId" -> site.value, "count" -> count)
        }))
      case OathkeeperChanged(holder) => ujson.Obj(
        "holderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p => ujson.Str(p.value)))
      case OathkeeperRecipientChoiceStarted(actor, decision, candidates) =>
        ujson.Obj("actorPlayerId" -> actor.value,
          "decisionId" -> decision.value,
          "candidatePlayerIds" -> ujson.Arr.from(
            candidates.map(p => ujson.Str(p.value))))
      case OathkeeperRecipientChosen(actor, decision, recipient) =>
        ujson.Obj("actorPlayerId" -> actor.value,
          "decisionId" -> decision.value,
          "recipientPlayerId" -> recipient.value)
      case UsurperFlipped(player) => ujson.Obj("playerId" -> player.value)
      case UsurperVictory(player) => ujson.Obj("playerId" -> player.value)
      case VisionRevealed(player, vision, replaced, destination) => ujson.Obj(
        "playerId" -> player.value, "visionId" -> vision.value,
        "replacedVisionId" -> replaced.fold[ujson.Value](ujson.Null)(v => ujson.Str(v.value)),
        "discardRegion" -> destination.key)
      case ConspiracyStarted(player, decision, source, target, sites, favor) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "sourceVisionId" -> source.value,
        "target" -> target.fold[ujson.Value](ujson.Null)(encodeConspiracyTarget),
        "automaticSecretSites" -> stringArray(sites.map(_.value)),
        "automaticFavorReturns" -> stringArray(favor.map(_.key)))
      case ConspiracySecretSiteChosen(player, decision, site, sites) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "secretSiteId" -> site.value,
        "automaticSecretSites" -> stringArray(sites.map(_.value)))
      case ConspiracyCompleted(player, decision, source, target, sites, favor) => ujson.Obj(
        "playerId" -> player.value, "decisionId" -> decision.value,
        "sourceVisionId" -> source.value,
        "target" -> target.fold[ujson.Value](ujson.Null)(encodeConspiracyTarget),
        "secretSites" -> stringArray(sites.map(_.value)),
        "favorReturnOrder" -> stringArray(favor.map(_.key)))
      case VisionVictory(player, vision) => ujson.Obj(
        "playerId" -> player.value, "visionId" -> vision.value)
    }

  private def decodePayload(
      eventType: String,
      payload: ujson.Value,
      path: String,
      envelopeCatalog: CatalogRef
  ): Either[WireError, OathEvent] =
    try {
      eventType match {
        case FirstGameStartedType =>
          decodePlan(payload, path).flatMap { plan =>
            if (plan.catalog == envelopeCatalog)
              Right(FirstGameStarted(plan))
            else
              Left(
                CatalogMismatch(
                  s"$path.catalog",
                  envelopeCatalog,
                  plan.catalog
                )
              )
          }
        case PawnPlacedType =>
          Right(
            GamePawnPlaced(
              PlayerId(payload("playerId").str),
              SiteId(payload("siteId").str)
            )
          )
        case AdviserChosenType =>
          Right(
            StartingAdviserChosen(
              PlayerId(payload("playerId").str),
              DenizenId(payload("adviserId").str)
            )
          )
        case FirstGameCompletedType => Right(FirstGameCompleted)
        case TakeWealthType =>
          val resource = payload("resource").str match {
            case "favor" => Right(WakeResource.Favor)
            case "secret" => Right(WakeResource.Secret)
            case other => Left(InvalidValue(
              s"$path.resource",
              s"unknown wealth resource '$other'"
            ))
          }
          resource.map(WealthTaken(
            PlayerId(payload("playerId").str),
            SiteId(payload("siteId").str),
            _
          ))
        case WakeEndedType =>
          Right(WakeEnded(PlayerId(payload("playerId").str)))
        case TraveledType =>
          val spent = payload("supplySpent").num
          if (!spent.isFinite || spent != Math.rint(spent) || spent < 0 ||
              spent > Int.MaxValue)
            Left(InvalidValue(s"$path.supplySpent",
              "must be a non-negative integer"))
          else Right(Traveled(
            PlayerId(payload("playerId").str),
            SiteId(payload("sourceSiteId").str),
            SiteId(payload("destinationSiteId").str),
            spent.toInt
          ))
        case MusteredType => for {
          target <- decodeEconomyTarget(payload("target"), s"$path.target")
          suit <- decodeSuit(payload("suit").str, s"$path.suit")
          spent <- safeIntField(payload.obj, "supplySpent", path)
          gained <- safeIntField(payload.obj, "warbandsGained", path)
        } yield Mustered(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), target, suit, spent, gained)
        case TradedType => for {
          target <- decodeEconomyTarget(payload("target"), s"$path.target")
          suit <- decodeSuit(payload("suit").str, s"$path.suit")
          resource <- payload("resource").str match {
            case "favor" => Right(TradeResource.Favor)
            case "secret" => Right(TradeResource.Secret)
            case other => Left(InvalidValue(s"$path.resource",
              s"unknown Trade resource '$other'"))
          }
          spent <- safeIntField(payload.obj, "supplySpent", path)
          gained <- safeIntField(payload.obj, "gained", path)
        } yield Traded(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), target, suit, resource, spent, gained)
        case SearchStartedType =>
          for {
            source <- decodeSearchSource(payload("source"), s"$path.source")
            origin <- decodeRegion(payload("origin").str, s"$path.origin")
            drawn <- traverse(payload("drawn").arr.toVector)(decodeWorldCard(_, s"$path.drawn"))
            spent = payload("supplySpent").num
            _ <- if (spent.isFinite && spent == Math.rint(spent) && spent >= 0 &&
              spent <= Int.MaxValue) Right(()) else Left(InvalidValue(
              s"$path.supplySpent", "must be a non-negative integer"))
          } yield SearchStarted(
            PlayerId(payload("playerId").str),
            DecisionId(payload("decisionId").str), source, origin,
            spent.toInt, drawn)
        case SearchCompletedType =>
          for {
            kept <- decodeWorldCard(payload("kept"), s"$path.kept")
            discarded <- traverse(payload("discardedInOrder").arr.toVector)(
              decodeWorldCard(_, s"$path.discardedInOrder"))
            placement <- decodeSearchPlacement(payload("placement"), s"$path.placement")
          } yield SearchCompleted(
            PlayerId(payload("playerId").str),
            DecisionId(payload("decisionId").str), kept, discarded, placement)
        case RestStartedType =>
          Right(RestStarted(PlayerId(payload("playerId").str)))
        case RestCompletedType =>
          val favorObject = payload("returnedFavor").obj
          val favor = favorObject.toVector.map { case (key, value) =>
            Suit.all.find(_.key == key).toRight(InvalidValue(
              s"$path.returnedFavor.$key", "unknown suit")).flatMap { suit =>
              safeInt(value, s"$path.returnedFavor.$key").map(suit -> _)
            }
          }
          for {
            entries <- favor.foldLeft[Either[WireError, Vector[(Suit, Int)]]](
              Right(Vector.empty)) {
              case (Right(acc), Right(entry)) => Right(acc :+ entry)
              case (Left(error), _) => Left(error)
              case (_, Left(error)) => Left(error)
            }
            returnedSecrets <- safeIntField(payload.obj, "returnedSecrets", path)
            refreshedSupply <- safeIntField(payload.obj, "refreshedSupply", path)
            completedRound <- safeIntField(payload.obj, "completedRound", path)
            limited = payload("usurperLimited").bool
          } yield RestCompleted(
            PlayerId(payload("playerId").str), entries.toMap,
            returnedSecrets, refreshedSupply,
            PlayerId(payload("postRestActivePlayerId").str), completedRound, limited)
        case RoundEndedType => for {
          completed <- safeIntField(payload.obj, "completedRound", path)
          next <- payload("nextRound") match {
            case ujson.Null => Right(None)
            case value => safeInt(value, s"$path.nextRound").map(Some(_))
          }
        } yield RoundEnded(completed, next)
        case WarExhaustionResolvedType => for {
          kind <- payload("victoryKind").str match {
            case "usurper" => Right(VictoryKind.Usurper)
            case "visionary" => Right(VictoryKind.Visionary)
            case "oathkeeper" => Right(VictoryKind.Oathkeeper)
            case "random-selection" => Right(VictoryKind.RandomSelection)
            case other => Left(InvalidValue(s"$path.victoryKind",
              s"unknown victory kind $other"))
          }
        } yield WarExhaustionResolved(PlayerId(payload("winnerPlayerId").str),
          kind, payload("visionId") match {
            case ujson.Null => None
            case value => Some(VisionId(value.str))
          }, payload("randomCandidatePlayerIds").arr.toVector.map(v => PlayerId(v.str)))
        case RecoverRolledType => for {
          spent <- safeIntField(payload.obj, "supplySpent", path)
          dice <- traverse(payload("dice").arr.toVector)(v =>
            decodeDefenseFace(v.str, s"$path.dice"))
        } yield RecoverRolled(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), SiteId(payload("siteId").str),
          spent, dice)
        case RecoverStoppedType => Right(RecoverStopped(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str)))
        case RelicRecoveredType => Right(RelicRecovered(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str),
          SiteId(payload("siteId").str), RelicId(payload("relicId").str)))
        case ForgeStartedType => for {
          spent <- safeIntField(payload.obj, "supplySpent", path)
          favor <- safeIntField(payload("cost").obj, "favor", s"$path.cost")
          secrets <- safeIntField(payload("cost").obj, "secrets", s"$path.cost")
          targets <- traverse(payload("targets").arr.toVector)(v => Right(
            SiteDenizenTarget(SiteId(v("siteId").str), DenizenId(v("denizenId").str))))
        } yield ForgeStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), SiteId(payload("siteId").str),
          targets, Tokens(favor, secrets), spent)
        case ForgeCompletedType => for {
          assignments <- traverse(payload("assignments").arr.toVector) { v =>
            val resource = v("resource").str match {
              case "favor" => Right(ForgeResource.Favor)
              case "secret" => Right(ForgeResource.Secret)
              case other => Left(InvalidValue(s"$path.assignments.resource", s"unknown Forge resource $other"))
            }
            resource.map(r => ForgeResourceAssignment(
              SiteDenizenTarget(SiteId(v("siteId").str), DenizenId(v("denizenId").str)), r))
          }
        } yield ForgeCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), SiteId(payload("siteId").str),
          assignments, RelicId(payload("relicId").str))
        case BannerChallengeStartedType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          prior <- safeIntField(payload.obj, "priorResources", path)
          spent <- safeIntField(payload.obj, "supplySpent", path)
          favor <- traverse(payload("automaticFavorReturns").arr.toVector)(v =>
            decodeSuit(v.str, s"$path.automaticFavorReturns"))
          sites = payload("automaticSecretSites").arr.toVector.map(v => SiteId(v.str))
          holder = payload("priorHolderPlayerId") match {
            case ujson.Null => None
            case value => Some(PlayerId(value.str))
          }
        } yield BannerChallengeStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), banner, holder, prior, spent,
          favor, sites)
        case BannerRibbonChoiceMadeType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          site = SiteId(payload("secretSiteId").str)
          sites = payload("automaticSecretSites").arr.toVector.map(v => SiteId(v.str))
        } yield BannerRibbonChoiceMade(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), banner, site, sites)
        case BannerChallengeCompletedType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          prior <- safeIntField(payload.obj, "priorResources", path)
          placed <- safeIntField(payload.obj, "placedResources", path)
          returned <- safeIntField(payload.obj, "secretsReturnedToHolder", path)
          favor <- traverse(payload("favorReturnOrder").arr.toVector)(v =>
            decodeSuit(v.str, s"$path.favorReturnOrder"))
          sites = payload("secretSiteOrder").arr.toVector.map(v => SiteId(v.str))
          holder = payload("priorHolderPlayerId") match {
            case ujson.Null => None
            case value => Some(PlayerId(value.str))
          }
        } yield BannerChallengeCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), banner, holder, prior, placed,
          favor, sites, returned)
        case BannerResourcePlacedType => for {
          banner <- decodeBanner(payload("banner").str, s"$path.banner")
          amount <- safeIntField(payload.obj, "amount", path)
        } yield BannerResourcePlaced(PlayerId(payload("playerId").str), banner, amount)
        case FacedownAdviserDiscardedType => for {
          adviser <- decodeWorldCard(payload("adviser"), s"$path.adviser")
          destination <- decodeRegion(payload("destination").str, s"$path.destination")
        } yield FacedownAdviserDiscarded(PlayerId(payload("playerId").str),
          adviser, destination)
        case FacedownAdviserPlayedType => for {
          adviser <- decodeWorldCard(payload("adviser"), s"$path.adviser")
          placement <- decodeSearchPlacement(payload("placement"), s"$path.placement")
          favor <- safeIntField(payload.obj, "favorGained", path)
          world <- traverse(payload("discardedWorld").arr.toVector)(value =>
            decodeWorldCard(value, s"$path.discardedWorld"))
          edifices = payload("discardedEdifices").arr.toVector.map(value =>
            EdificeId(value.str))
        } yield FacedownAdviserPlayed(PlayerId(payload("playerId").str), adviser,
          placement, favor, world, edifices)
        case SiteRelicsPeekedType => Right(SiteRelicsPeeked(
          PlayerId(payload("playerId").str), SiteId(payload("siteId").str),
          payload("relics").arr.toVector.map(value => RelicId(value.str))))
        case OwnedRelicRevealedType => Right(OwnedRelicRevealed(
          PlayerId(payload("playerId").str), RelicId(payload("relicId").str)))
        case WarbandsMovedType => for {
          amount <- safeIntField(payload.obj, "amount", path)
          board <- safeIntField(payload.obj, "priorBoardWarbands", path)
          atSite <- safeIntField(payload.obj, "priorSiteWarbands", path)
        } yield WarbandsMoved(PlayerId(payload("playerId").str),
          SiteId(payload("siteId").str), payload("toSite").bool, amount, board, atSite)
        case NegotiationStartedType => Right(NegotiationStarted(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str),
          SiteId(payload("siteId").str), payload("participants").arr.toVector
            .map(v => PlayerId(v.str))))
        case NegotiationTermsReplacedType => decodeNegotiationTerms(
          payload("terms"), s"$path.terms").map(terms => NegotiationTermsReplaced(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str), terms))
        case NegotiationAcceptedType => Right(NegotiationAccepted(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str)))
        case NegotiationDeclinedType => Right(NegotiationDeclined(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str)))
        case NegotiationCompletedType => for {
          participants <- Right(payload("participants").arr.toVector.map(v => PlayerId(v.str)))
          rows <- traverse(payload("terms").arr.toVector)(row =>
            decodeNegotiationTerms(row("terms"), s"$path.terms").map(
              PlayerId(row("authorPlayerId").str) -> _))
        } yield NegotiationCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), participants, rows.toMap)
        case CampaignStartedType => for {
          spent <- safeIntField(payload.obj, "supplySpent", path)
          force <- safeIntField(payload.obj, "force", path)
          kind <- decodeCampaignKind(payload("campaignKind"), s"$path.campaignKind")
          sites <- traverse(payload("targetSiteIds").arr.toVector)(value =>
            Right(SiteId(value.str)))
          raidTargets <- traverse(payload("raidTargets").arr.toVector)(value =>
            decodeCampaignRaidTarget(value, s"$path.raidTargets"))
          defender <- payload("defender")("kind").str match {
            case "bandits" => Right(CampaignDefender.Bandits)
            case "player" => Right(CampaignDefender.Player(
              PlayerId(payload("defender")("playerId").str)))
            case other => Left(InvalidValue(s"$path.defender.kind",
              s"unknown Campaign defender '$other'"))
          }
        } yield CampaignStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), sites,
          defender, spent, force, kind, raidTargets)
        case CampaignPlanChosenType => for {
          source <- decodeCampaignPlanSource(payload("source"), s"$path.source")
          side <- decodeCampaignPlanSide(payload("side"), s"$path.side")
          costs <- traverse(payload("costs").arr.toVector)(v =>
            decodeCampaignPlanCost(v, s"$path.costs"))
          effects <- traverse(payload("effects").arr.toVector)(v =>
            decodeCampaignPlanEffect(v, s"$path.effects"))
        } yield CampaignPlanChosen(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), source,
          payload("handlerId").str, side, costs, effects)
        case CampaignPlansFinishedType => for {
          side <- decodeCampaignPlanSide(payload("side"), s"$path.side")
          sources <- traverse(payload("orderedSources").arr.toVector)(v =>
            decodeCampaignPlanSource(v, s"$path.orderedSources"))
          _ <- Either.cond(sources.distinct.size == sources.size, (),
            InvalidValue(s"$path.orderedSources", "duplicate Campaign plan source"))
          handlerIds = payload("orderedHandlerIds").arr.toVector.map(_.str)
          _ <- Either.cond(handlerIds.size == sources.size, (), InvalidValue(
            s"$path.orderedHandlerIds", "handler IDs must match Campaign plan sources"))
          effects <- traverse(payload("effects").arr.toVector)(v =>
            decodeCampaignPlanEffect(v, s"$path.effects"))
          attack <- safeIntField(payload.obj, "attack", path)
          skulls <- safeIntField(payload.obj, "skullLosses", path)
          dice <- traverse(payload("attackDice").arr.toVector)(v =>
            decodeAttackFace(v.str, s"$path.attackDice"))
        } yield CampaignPlansFinished(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), side, sources, handlerIds,
          effects, dice, attack, skulls)
        case CampaignSacrificedType => for {
          sacrificed <- safeIntField(payload.obj, "sacrificed", path)
          attack <- safeIntField(payload.obj, "attack", path)
          defense <- safeIntField(payload.obj, "defense", path)
          skulls <- safeIntField(payload.obj, "skullLosses", path)
          dice <- traverse(payload("defenseDice").arr.toVector)(v =>
            decodeDefenseFace(v.str, s"$path.defenseDice"))
          policyId = payload("losingForcePolicyId") match {
            case ujson.Null => None
            case value => Some(value.str)
          }
          losses <- traverse(payload("losingForces").arr.toVector)(value =>
            decodeLosingForceEffect(value, s"$path.losingForces"))
        } yield CampaignSacrificed(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), sacrificed, dice, attack,
          defense, skulls, payload("victorious").bool, policyId, losses)
        case CampaignConqueredType => for {
          losses <- traverse(payload("losingForces").arr.toVector)(value =>
            decodeLosingForceEffect(value, s"$path.losingForces"))
          allocations <- traverse(payload("allocations").arr.toVector) { value =>
            val allocationPath = s"$path.allocations"
            safeIntField(value.obj, "count", allocationPath).map(count =>
              CampaignForceAllocation(SiteId(value("siteId").str), count))
          }
        } yield CampaignConquered(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str),
          payload("losingForcePolicyId").str, losses, allocations)
        case CampaignRaidedType => for {
          killed <- safeIntField(payload("defenderLoss").obj, "killed",
            s"$path.defenderLoss")
          returnedCount <- safeIntField(payload("defenderLoss").obj, "returned",
            s"$path.defenderLoss")
          relics = payload("takenRelics").arr.toVector.map(v => RelicId(v.str))
          banners <- traverse(payload("takenBanners").arr.toVector) { value =>
            value.str match {
              case "peoples-favor" => Right(CampaignBanner.PeoplesFavor)
              case "darkest-secret" => Right(CampaignBanner.DarkestSecret)
              case other => Left(InvalidValue(s"$path.takenBanners",
                s"unknown Campaign banner '$other'"))
            }
          }
          advisers <- traverse(payload("discardedAdvisers").arr.toVector)(value =>
            decodeWorldCard(value, s"$path.discardedAdvisers"))
          adviserRegion <- decodeRegion(payload("adviserDiscardRegion").str,
            s"$path.adviserDiscardRegion")
          boxedConspiracy = payload("boxedConspiracy") match {
            case ujson.Null => None
            case value => Some(VisionId(value.str))
          }
          discardedRelics = payload("discardedRelics").arr.toVector.map(v => RelicId(v.str))
          burned <- safeIntField(payload.obj, "favorBurned", path)
          darkestSecretBurned <- safeIntField(payload.obj, "darkestSecretBurned", path)
          favorEntries <- traverse(payload("bannerFavorReturned").obj.toVector) {
            case (key, value) => Suit.all.find(_.key == key).toRight(InvalidValue(
              s"$path.bannerFavorReturned.$key", "unknown suit")).flatMap(suit =>
              safeInt(value, s"$path.bannerFavorReturned.$key").map(suit -> _))
          }
        } yield CampaignRaided(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str), payload("losingForcePolicyId").str,
          CampaignRaidBoardLoss(PlayerId(payload("defenderLoss")("playerId").str),
            killed, returnedCount), relics, banners, advisers, adviserRegion,
          boxedConspiracy, discardedRelics, burned, favorEntries.toMap,
          darkestSecretBurned)
        case CampaignRaidPawnRelocatedType => Right(CampaignRaidPawnRelocated(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str),
          PlayerId(payload("defenderPlayerId").str), SiteId(payload("originSiteId").str),
          SiteId(payload("destinationSiteId").str)))
        case BanditsRefilledType => for {
          sites <- traverse(payload("sites").arr.toVector) { value => for {
            count <- safeIntField(value.obj, "count", s"$path.sites")
          } yield SiteId(value("siteId").str) -> count }
        } yield BanditsRefilled(sites)
        case OathkeeperChangedType =>
          payload("holderPlayerId") match {
            case ujson.Null => Right(OathkeeperChanged(None))
            case value => Right(OathkeeperChanged(Some(PlayerId(value.str))))
          }
        case OathkeeperRecipientChoiceStartedType =>
          Right(OathkeeperRecipientChoiceStarted(
            PlayerId(payload("actorPlayerId").str),
            DecisionId(payload("decisionId").str),
            payload("candidatePlayerIds").arr.toVector.map(value =>
              PlayerId(value.str))))
        case OathkeeperRecipientChosenType =>
          Right(OathkeeperRecipientChosen(
            PlayerId(payload("actorPlayerId").str),
            DecisionId(payload("decisionId").str),
            PlayerId(payload("recipientPlayerId").str)))
        case UsurperFlippedType =>
          Right(UsurperFlipped(PlayerId(payload("playerId").str)))
        case UsurperVictoryType =>
          Right(UsurperVictory(PlayerId(payload("playerId").str)))
        case VisionRevealedType => for {
          destination <- decodeRegion(payload("discardRegion").str,
            s"$path.discardRegion")
        } yield VisionRevealed(PlayerId(payload("playerId").str),
          VisionId(payload("visionId").str), payload("replacedVisionId") match {
            case ujson.Null => None
            case value => Some(VisionId(value.str))
          }, destination)
        case ConspiracyStartedType => for {
          target <- decodeOptionalConspiracyTarget(payload("target"), s"$path.target")
          favor <- traverse(payload("automaticFavorReturns").arr.toVector)(v =>
            Suit.all.find(_.key == v.str).toRight(InvalidValue(
              s"$path.automaticFavorReturns", "unknown suit")))
        } yield ConspiracyStarted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str),
          VisionId(payload("sourceVisionId").str), target,
          payload("automaticSecretSites").arr.toVector.map(v => SiteId(v.str)), favor)
        case ConspiracySecretSiteChosenType => Right(ConspiracySecretSiteChosen(
          PlayerId(payload("playerId").str), DecisionId(payload("decisionId").str),
          SiteId(payload("secretSiteId").str),
          payload("automaticSecretSites").arr.toVector.map(v => SiteId(v.str))))
        case ConspiracyCompletedType => for {
          target <- decodeOptionalConspiracyTarget(payload("target"), s"$path.target")
          favor <- traverse(payload("favorReturnOrder").arr.toVector)(v =>
            Suit.all.find(_.key == v.str).toRight(InvalidValue(
              s"$path.favorReturnOrder", "unknown suit")))
        } yield ConspiracyCompleted(PlayerId(payload("playerId").str),
          DecisionId(payload("decisionId").str),
          VisionId(payload("sourceVisionId").str), target,
          payload("secretSites").arr.toVector.map(v => SiteId(v.str)), favor)
        case VisionVictoryType => Right(VisionVictory(
          PlayerId(payload("playerId").str), VisionId(payload("visionId").str)))
        case other => Left(UnknownEventType(s"$path.eventType", other))
      }
    } catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid payload")
          )
        )
    }

  private def validateEventVersion(
      version: Int,
      eventType: String,
      path: String
  ): Either[WireError, Unit] = {
    val expected =
      if (eventType == RoundEndedType || eventType == WarExhaustionResolvedType)
        RoundEndFormatVersion
      else if (eventType == VisionRevealedType || eventType == ConspiracyStartedType ||
          eventType == ConspiracySecretSiteChosenType ||
          eventType == ConspiracyCompletedType || eventType == VisionVictoryType)
        VisionFormatVersion
      else if (eventType == NegotiationStartedType ||
          eventType == NegotiationTermsReplacedType ||
          eventType == NegotiationAcceptedType || eventType == NegotiationDeclinedType ||
          eventType == NegotiationCompletedType) NegotiationFormatVersion
      else if (eventType == FacedownAdviserDiscardedType ||
          eventType == FacedownAdviserPlayedType ||
          eventType == SiteRelicsPeekedType ||
          eventType == OwnedRelicRevealedType ||
          eventType == WarbandsMovedType) MinorActionFormatVersion
      else if (eventType == BannerChallengeStartedType ||
          eventType == BannerRibbonChoiceMadeType ||
          eventType == BannerChallengeCompletedType ||
          eventType == BannerResourcePlacedType) BannerFormatVersion
      else if (eventType == ForgeStartedType || eventType == ForgeCompletedType)
        ForgeFormatVersion
      else if (eventType == RecoverRolledType || eventType == RecoverStoppedType ||
          eventType == RelicRecoveredType || eventType == CampaignStartedType ||
          eventType == CampaignPlanChosenType ||
          eventType == CampaignPlansFinishedType ||
          eventType == CampaignSacrificedType || eventType == CampaignConqueredType ||
          eventType == CampaignRaidedType || eventType == CampaignRaidPawnRelocatedType ||
          eventType == BanditsRefilledType ||
          eventType == OathkeeperChangedType ||
          eventType == OathkeeperRecipientChoiceStartedType ||
          eventType == OathkeeperRecipientChosenType ||
          eventType == UsurperFlippedType || eventType == UsurperVictoryType)
        RecoverFormatVersion
      else if (eventType == MusteredType || eventType == TradedType)
        EconomyFormatVersion
      else if (eventType == RestStartedType || eventType == RestCompletedType)
        RestFormatVersion
      else if (eventType == SearchStartedType || eventType == SearchCompletedType)
        SearchFormatVersion
      else if (eventType == TakeWealthType || eventType == WakeEndedType ||
          eventType == TraveledType)
        GameplayFormatVersion
      else FormatVersion
    if (version == expected) Right(())
    else Left(InvalidValue(
      s"$path.formatVersion",
      s"event type '$eventType' requires format version $expected"
    ))
  }

  private def decodeBanner(value: String, path: String): Either[WireError, Banner] =
    Banner.fromKey(value).toRight(InvalidValue(path, s"unknown banner '$value'"))

  private def encodeNegotiationTerms(terms: NegotiationTerms): ujson.Value = ujson.Obj(
    "transfers" -> ujson.Arr.from(terms.transfers.map(transfer => ujson.Obj(
      "recipientPlayerId" -> transfer.recipient.value, "favor" -> transfer.favor,
      "relicIds" -> stringArray(transfer.relics.map(_.value))))),
    "disclosures" -> ujson.Arr.from(terms.disclosures.map { disclosure =>
      val information = disclosure.information match {
        case NegotiationDisclosureRef.Adviser(owner, card) => ujson.Obj(
          "kind" -> "adviser", "ownerPlayerId" -> owner.value,
          "card" -> encodeWorldCard(card))
        case NegotiationDisclosureRef.HeldRelic(owner, relic) => ujson.Obj(
          "kind" -> "held-relic", "ownerPlayerId" -> owner.value,
          "relicId" -> relic.value)
        case NegotiationDisclosureRef.SiteRelic(site, relic) => ujson.Obj(
          "kind" -> "site-relic", "siteId" -> site.value, "relicId" -> relic.value)
      }
      ujson.Obj("recipientPlayerId" -> disclosure.recipient.value,
        "information" -> information)
    }))

  private def decodeNegotiationTerms(value: ujson.Value,
      path: String): Either[WireError, NegotiationTerms] = try {
    for {
      transfers <- traverse(value("transfers").arr.toVector) { row => for {
        favor <- safeIntField(row.obj, "favor", s"$path.transfers")
      } yield NegotiationTransfer(PlayerId(row("recipientPlayerId").str), favor,
        row("relicIds").arr.toVector.map(v => RelicId(v.str))) }
      disclosures <- traverse(value("disclosures").arr.toVector) { row =>
        val info = row("information")
        val decoded: Either[WireError, NegotiationDisclosureRef] = info("kind").str match {
          case "adviser" => decodeWorldCard(info("card"), s"$path.disclosures.card")
            .map(card => NegotiationDisclosureRef.Adviser(
              PlayerId(info("ownerPlayerId").str), card))
          case "held-relic" => Right(NegotiationDisclosureRef.HeldRelic(
            PlayerId(info("ownerPlayerId").str), RelicId(info("relicId").str)))
          case "site-relic" => Right(NegotiationDisclosureRef.SiteRelic(
            SiteId(info("siteId").str), RelicId(info("relicId").str)))
          case other => Left(InvalidValue(s"$path.disclosures.kind",
            s"unknown disclosure kind '$other'"))
        }
        decoded.map(NegotiationDisclosure(PlayerId(row("recipientPlayerId").str), _))
      }
    } yield NegotiationTerms(transfers, disclosures)
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Negotiation terms"))) }

  private def encodePlan(plan: FirstGameSetupPlan): ujson.Value =
    ujson.Obj(
      "catalog" -> encodeCatalog(plan.catalog),
      "participants" -> ujson.Arr.from(plan.participants.map { participant =>
        ujson.Obj(
          "playerId" -> participant.playerId.value,
          "lineageId" -> participant.lineageId.value,
          "color" -> participant.color.value
        )
      }),
      "firstPlayer" -> plan.firstPlayer.value,
      "oathkeeperGoal" -> plan.oathkeeperGoal.key,
      "orderedSites" -> stringArray(plan.orderedSites.map(_.value)),
      "denizenOrder" -> stringArray(plan.denizenOrder.map(_.value)),
      "worldDeckOrder" -> ujson.Arr.from(
        plan.worldDeckOrder.map {
          case id: DenizenId =>
            ujson.Obj("kind" -> "denizen", "id" -> id.value)
          case id: VisionId =>
            ujson.Obj("kind" -> "vision", "id" -> id.value)
        }
      ),
      "relicOrder" -> stringArray(plan.relicOrder.map(_.value)),
      "homelandEdifices" -> ujson.Arr.from(
        plan.homelandEdifices.map { case (siteId, edificeId) =>
          ujson.Obj(
            "siteId" -> siteId.value,
            "edificeId" -> edificeId.value
          )
        }
      )
    )

  private def decodePlan(
      value: ujson.Value,
      path: String
  ): Either[WireError, FirstGameSetupPlan] =
    try {
      val obj = value.obj
      for {
        catalog <- decodeCatalog(obj("catalog"), s"$path.catalog")
        participants <- traverse(
          obj("participants").arr.zipWithIndex.toVector
        ) { case (participant, _) =>
          Right(
            FirstGameParticipant(
              PlayerId(participant("playerId").str),
              LineageId(participant("lineageId").str),
              PlayerColor(participant("color").str)
            )
          )
        }
        world <- traverse(obj("worldDeckOrder").arr.toVector) { item =>
          item("kind").str match {
            case "denizen" =>
              Right(DenizenId(item("id").str): WorldCardId)
            case "vision" =>
              Right(VisionId(item("id").str): WorldCardId)
            case kind =>
              Left(
                InvalidValue(
                  s"$path.worldDeckOrder",
                  s"unknown card kind $kind"
                )
              )
          }
        }
        oathkeeperGoal <- OathkeeperGoal.all
          .find(_.key == obj("oathkeeperGoal").str)
          .toRight(InvalidValue(s"$path.oathkeeperGoal",
            s"unknown Oathkeeper goal '${obj("oathkeeperGoal").str}'"))
      } yield FirstGameSetupPlan(
        catalog,
        participants,
        PlayerId(obj("firstPlayer").str),
        obj("orderedSites").arr.toVector.map(v => SiteId(v.str)),
        obj("denizenOrder").arr.toVector.map(v => DenizenId(v.str)),
        world,
        obj("relicOrder").arr.toVector.map(v => RelicId(v.str)),
        obj("homelandEdifices").arr.toVector.map { entry =>
          SiteId(entry("siteId").str) -> EdificeId(entry("edificeId").str)
        },
        oathkeeperGoal
      )
    } catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid setup plan")
          )
        )
    }

  private def validateEventCatalog(
      event: OathEvent,
      catalog: CatalogRef,
      path: String
  ): Either[WireError, Unit] =
    event match {
      case FirstGameStarted(plan) if plan.catalog != catalog =>
        Left(CatalogMismatch(s"$path.payload.catalog", catalog, plan.catalog))
      case _ => Right(())
    }

  private def encodeCatalog(ref: CatalogRef): ujson.Value =
    ujson.Obj("ruleset" -> ref.ruleset, "version" -> ref.version)

  private def decodeCatalog(
      value: ujson.Value,
      path: String
  ): Either[WireError, CatalogRef] =
    try Right(CatalogRef(value("ruleset").str, value("version").str))
    catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid catalog")
          )
        )
    }

  private def requiredField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, ujson.Value] =
    obj.value.get(name).toRight(
      MissingField(s"$path.$name", "field is required")
    )

  private def stringField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, String] =
    requiredField(obj, name, path).flatMap {
      case ujson.Str(value) => Right(value)
      case _ => Left(WrongType(s"$path.$name", "expected a string"))
    }

  private def formatVersionField(
      obj: ujson.Obj,
      path: String
  ): Either[WireError, Int] =
    requiredField(obj, "formatVersion", path)
      .flatMap(value => safeInteger(value, s"$path.formatVersion"))
      .flatMap { value =>
        if (value <= Int.MaxValue.toLong) Right(value.toInt)
        else
          Left(
            InvalidValue(
              s"$path.formatVersion",
              s"must be between 0 and ${Int.MaxValue} inclusive"
            )
          )
      }

  private def safeIntegerField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, Long] =
    requiredField(obj, name, path)
      .flatMap(value => safeInteger(value, s"$path.$name"))

  private def safeIntField(obj: ujson.Obj, name: String, path: String)
      : Either[WireError, Int] =
    requiredField(obj, name, path).flatMap(value =>
      safeInt(value, s"$path.$name"))

  private def safeInt(value: ujson.Value, path: String)
      : Either[WireError, Int] =
    safeInteger(value, path).flatMap { number =>
      if (number <= Int.MaxValue.toLong) Right(number.toInt)
      else Left(InvalidValue(path,
        s"must be between 0 and ${Int.MaxValue} inclusive"))
    }

  private def safeInteger(
      value: ujson.Value,
      path: String
  ): Either[WireError, Long] =
    value match {
      case ujson.Num(number)
          if !number.isNaN &&
            !number.isInfinity &&
            number == math.rint(number) &&
            number >= 0 &&
            number <= MaxSafeSequence.toDouble =>
        Right(number.toLong)
      case ujson.Num(number)
          if !number.isNaN &&
            !number.isInfinity &&
            number == math.rint(number) =>
        Left(
          InvalidValue(
            path,
            s"must be between 0 and $MaxSafeSequence inclusive"
          )
        )
      case _ => Left(WrongType(path, "expected an integer"))
    }

  private def validateSequence(
      sequence: Long,
      path: String
  ): Either[WireError, Unit] =
    if (sequence >= 0 && sequence <= MaxSafeSequence) Right(())
    else
      Left(
        InvalidValue(
          path,
          s"must be between 0 and $MaxSafeSequence inclusive"
        )
      )

  private def stringArray(values: Vector[String]): ujson.Value =
    ujson.Arr.from(values.map(ujson.Str(_)))

  private def encodeConspiracyTarget(target: ConspiracyTarget): ujson.Value =
    target match {
      case ConspiracyTarget.Relic(owner, relic) => ujson.Obj(
        "kind" -> "relic", "ownerPlayerId" -> owner.value,
        "relicId" -> relic.value)
      case ConspiracyTarget.Banner(owner, banner) => ujson.Obj(
        "kind" -> "banner", "ownerPlayerId" -> owner.value,
        "banner" -> banner.key)
    }

  private def decodeOptionalConspiracyTarget(value: ujson.Value, path: String)
      : Either[WireError, Option[ConspiracyTarget]] = value match {
    case ujson.Null => Right(None)
    case other => try other("kind").str match {
      case "relic" => Right(Some(ConspiracyTarget.Relic(
        PlayerId(other("ownerPlayerId").str), RelicId(other("relicId").str))))
      case "banner" => decodeBanner(other("banner").str, s"$path.banner").map(
        banner => Some(ConspiracyTarget.Banner(
          PlayerId(other("ownerPlayerId").str), banner)))
      case kind => Left(InvalidValue(s"$path.kind",
        s"unknown Conspiracy target '$kind'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Conspiracy target"))) }
  }

  private def encodeForceKind(force: ForceKind): ujson.Value = force match {
    case ForceKind.Bandit => ujson.Obj("kind" -> "bandit")
    case ForceKind.Imperial => ujson.Obj("kind" -> "imperial")
    case ForceKind.Exile(lineage) => ujson.Obj(
      "kind" -> "exile", "lineageId" -> lineage.value)
  }

  private def decodeForceKind(value: ujson.Value, path: String)
      : Either[WireError, ForceKind] = try value("kind").str match {
    case "bandit" => Right(ForceKind.Bandit)
    case "imperial" => Right(ForceKind.Imperial)
    case "exile" => Right(ForceKind.Exile(LineageId(value("lineageId").str)))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown force kind '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid force kind"))) }

  private def encodeLosingForceEffect(
      effect: CampaignLosingForceEffect): ujson.Value = {
    val base = ujson.Obj(
      "siteId" -> effect.site.value,
      "force" -> encodeForceKind(effect match {
        case CampaignLosingForceEffect.Remove(_, force, _) => force
        case CampaignLosingForceEffect.Preserve(_, force, _) => force
        case CampaignLosingForceEffect.Relocate(_, _, force, _) => force
        case CampaignLosingForceEffect.Replace(_, force, _, _, _) => force
        case CampaignLosingForceEffect.ReturnToBoard(_, _, force, _) => force
        case CampaignLosingForceEffect.KillCommitted(_, _, force, _) => force
        case CampaignLosingForceEffect.RelocateCommitted(_, _, force, _) => force
        case CampaignLosingForceEffect.PreserveCommitted(_, _, force, _) => force
      }),
      "count" -> (effect match {
        case CampaignLosingForceEffect.Remove(_, _, count) => count
        case CampaignLosingForceEffect.Preserve(_, _, count) => count
        case CampaignLosingForceEffect.Relocate(_, _, _, count) => count
        case CampaignLosingForceEffect.Replace(_, _, count, _, _) => count
        case CampaignLosingForceEffect.ReturnToBoard(_, _, _, count) => count
        case CampaignLosingForceEffect.KillCommitted(_, _, _, count) => count
        case CampaignLosingForceEffect.RelocateCommitted(_, _, _, count) => count
        case CampaignLosingForceEffect.PreserveCommitted(_, _, _, count) => count
      }))
    effect match {
      case _: CampaignLosingForceEffect.Remove => base("kind") = "remove"
      case _: CampaignLosingForceEffect.Preserve => base("kind") = "preserve"
      case CampaignLosingForceEffect.Relocate(_, destination, _, _) =>
        base("kind") = "relocate"
        base("destinationSiteId") = destination.value
      case CampaignLosingForceEffect.Replace(_, _, _, replacement, count) =>
        base("kind") = "replace"
        base("replacementForce") = replacement.map(encodeForceKind)
          .getOrElse(ujson.Null)
        base("replacementCount") = count
      case CampaignLosingForceEffect.ReturnToBoard(_, player, _, _) =>
        base("kind") = "return-to-board"
        base("playerId") = player.value
      case CampaignLosingForceEffect.KillCommitted(_, player, _, _) =>
        base("kind") = "kill-committed"
        base("playerId") = player.value
      case CampaignLosingForceEffect.RelocateCommitted(_, player, _, _) =>
        base("kind") = "relocate-committed"
        base("playerId") = player.value
      case CampaignLosingForceEffect.PreserveCommitted(_, player, _, _) =>
        base("kind") = "preserve-committed"
        base("playerId") = player.value
    }
    base
  }

  private def decodeLosingForceEffect(value: ujson.Value, path: String)
      : Either[WireError, CampaignLosingForceEffect] = try {
    val obj = value.obj
    for {
      kind <- stringField(obj, "kind", path)
      site <- stringField(obj, "siteId", path).map(SiteId(_))
      forceValue <- requiredField(obj, "force", path)
      force <- decodeForceKind(forceValue, s"$path.force")
      count <- safeIntField(obj, "count", path)
      _ <- Either.cond(count > 0, (), InvalidValue(
        s"$path.count", "expected a positive integer"))
      effect <- kind match {
        case "remove" => Right(CampaignLosingForceEffect.Remove(site, force, count))
        case "preserve" => Right(CampaignLosingForceEffect.Preserve(site, force, count))
        case "relocate" => stringField(obj, "destinationSiteId", path).flatMap {
          destination => Either.cond(destination != site.value,
            CampaignLosingForceEffect.Relocate(site, SiteId(destination), force, count),
            InvalidValue(s"$path.destinationSiteId",
              "relocation requires a different site"))
        }
        case "replace" => for {
          replacementCount <- safeIntField(obj, "replacementCount", path)
          replacementValue <- requiredField(obj, "replacementForce", path)
          replacement <- replacementValue match {
            case ujson.Null => Right(None)
            case other => decodeForceKind(other, s"$path.replacementForce").map(Some(_))
          }
          _ <- Either.cond(replacement.nonEmpty == (replacementCount > 0), (),
            InvalidValue(s"$path.replacementForce",
              "replacement force and count must agree"))
        } yield CampaignLosingForceEffect.Replace(site, force, count,
          replacement, replacementCount)
        case "return-to-board" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.ReturnToBoard(site,
            PlayerId(player), force, count))
        case "kill-committed" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.KillCommitted(site,
            PlayerId(player), force, count))
        case "relocate-committed" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.RelocateCommitted(site,
            PlayerId(player), force, count))
        case "preserve-committed" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.PreserveCommitted(site,
            PlayerId(player), force, count))
        case other => Left(InvalidValue(s"$path.kind",
          s"unknown losing-force effect '$other'"))
      }
    } yield effect
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid losing-force effect"))) }

  private def encodeDefenseFace(face: DefenseDieFace): String = face match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }

  private def encodeAttackFace(face: AttackDieFace): String = face match {
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  }

  private def decodeAttackFace(value: String, path: String) = value match {
    case "hollow-sword" => Right(AttackDieFace.HollowSword)
    case "one-sword" => Right(AttackDieFace.OneSword)
    case "two-swords-skull" => Right(AttackDieFace.TwoSwordsSkull)
    case other => Left(InvalidValue(path, s"unknown attack die face '$other'"))
  }

  private def decodeDefenseFace(value: String, path: String) = value match {
    case "blank" => Right(DefenseDieFace.Blank)
    case "one-shield" => Right(DefenseDieFace.OneShield)
    case "two-shields" => Right(DefenseDieFace.TwoShields)
    case "doubler" => Right(DefenseDieFace.Doubler)
    case other => Left(InvalidValue(path, s"unknown defense die face '$other'"))
  }

  private def encodeWorldCard(id: WorldCardId): ujson.Value = id match {
    case value: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> value.value)
    case value: VisionId => ujson.Obj("kind" -> "vision", "id" -> value.value)
  }

  private def decodeCampaignKind(value: ujson.Value, path: String)
      : Either[WireError, CampaignKind] = value.str match {
    case "conquest" => Right(CampaignKind.Conquest)
    case "raid" => Right(CampaignKind.Raid)
    case other => Left(InvalidValue(path, s"unknown Campaign kind '$other'"))
  }

  private def encodeCampaignRaidTarget(target: CampaignRaidTarget): ujson.Value =
    target match {
      case CampaignRaidTarget.Pawn(player) => ujson.Obj(
        "kind" -> "pawn", "playerId" -> player.value)
      case CampaignRaidTarget.Relic(player, relic) => ujson.Obj(
        "kind" -> "relic", "playerId" -> player.value,
        "relicId" -> relic.value)
      case CampaignRaidTarget.Banner(player, banner) => ujson.Obj(
        "kind" -> "banner", "playerId" -> player.value,
        "banner" -> banner.key)
    }

  private def decodeCampaignRaidTarget(value: ujson.Value, path: String)
      : Either[WireError, CampaignRaidTarget] = try value("kind").str match {
    case "pawn" => Right(CampaignRaidTarget.Pawn(
      PlayerId(value("playerId").str)))
    case "relic" => Right(CampaignRaidTarget.Relic(
      PlayerId(value("playerId").str), RelicId(value("relicId").str)))
    case "banner" => value("banner").str match {
      case "peoples-favor" => Right(CampaignRaidTarget.Banner(
        PlayerId(value("playerId").str), CampaignBanner.PeoplesFavor))
      case "darkest-secret" => Right(CampaignRaidTarget.Banner(
        PlayerId(value("playerId").str), CampaignBanner.DarkestSecret))
      case other => Left(InvalidValue(s"$path.banner",
        s"unknown Campaign banner '$other'"))
    }
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown Campaign Raid target '$other'"))
  } catch { case error: Exception => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Campaign Raid target"))) }

  private def encodeCampaignPlanSource(
      source: PendingProcedure.CampaignPlanSource): ujson.Value = source match {
    case PendingProcedure.CampaignPlanSource.Adviser(player, id) => ujson.Obj(
      "kind" -> "adviser", "playerId" -> player.value, "cardId" -> id.value)
    case PendingProcedure.CampaignPlanSource.SiteCard(site, id) => ujson.Obj(
      "kind" -> "site-card", "siteId" -> site.value, "cardId" -> id.value)
    case PendingProcedure.CampaignPlanSource.Relic(player, id) => ujson.Obj(
      "kind" -> "relic", "playerId" -> player.value, "cardId" -> id.value)
    case PendingProcedure.CampaignPlanSource.Title(player) => ujson.Obj(
      "kind" -> "title", "playerId" -> player.value)
  }

  private def decodeCampaignPlanSource(value: ujson.Value, path: String)
      : Either[WireError, PendingProcedure.CampaignPlanSource] =
    try value("kind").str match {
      case "adviser" => Right(PendingProcedure.CampaignPlanSource.Adviser(
        PlayerId(value("playerId").str), DenizenId(value("cardId").str)))
      case "site-card" => Right(PendingProcedure.CampaignPlanSource.SiteCard(
        SiteId(value("siteId").str), DenizenId(value("cardId").str)))
      case "relic" => Right(PendingProcedure.CampaignPlanSource.Relic(
        PlayerId(value("playerId").str), RelicId(value("cardId").str)))
      case "title" => Right(PendingProcedure.CampaignPlanSource.Title(
        PlayerId(value("playerId").str)))
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown Campaign plan source '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Campaign plan source"))) }

  private def encodeCampaignPlanSide(side: PendingProcedure.CampaignPlanSide) =
    ujson.Str(side match {
      case PendingProcedure.CampaignPlanSide.Attacker => "attacker"
      case PendingProcedure.CampaignPlanSide.Defender => "defender"
    })
  private def decodeCampaignPlanSide(value: ujson.Value, path: String) = value.str match {
    case "attacker" => Right(PendingProcedure.CampaignPlanSide.Attacker)
    case "defender" => Right(PendingProcedure.CampaignPlanSide.Defender)
    case other => Left(InvalidValue(path, s"unknown Campaign plan side '$other'"))
  }
  private def encodeCampaignPlanCost(cost: PendingProcedure.CampaignPlanCost) = cost match {
    case PendingProcedure.CampaignPlanCost.Favor(n) => ujson.Obj("kind" -> "favor", "count" -> n)
    case PendingProcedure.CampaignPlanCost.Secret(n) => ujson.Obj("kind" -> "secret", "count" -> n)
  }
  private def decodeCampaignPlanCost(value: ujson.Value, path: String) = value("kind").str match {
    case "favor" => Right(PendingProcedure.CampaignPlanCost.Favor(value("count").num.toInt))
    case "secret" => Right(PendingProcedure.CampaignPlanCost.Secret(value("count").num.toInt))
    case other => Left(InvalidValue(path, s"unknown Campaign plan cost '$other'"))
  }
  private def encodeCampaignPlanEffect(effect: PendingProcedure.CampaignPlanEffect) = effect match {
    case PendingProcedure.CampaignPlanEffect.AddAttackDice(n) => ujson.Obj("kind" -> "add-attack-dice", "count" -> n)
    case PendingProcedure.CampaignPlanEffect.AddDefenseDice(n) => ujson.Obj("kind" -> "add-defense-dice", "count" -> n)
    case PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls => ujson.Obj("kind" -> "ignore-attack-skulls")
    case PendingProcedure.CampaignPlanEffect.RevealSource => ujson.Obj("kind" -> "reveal-source")
    case PendingProcedure.CampaignPlanEffect.TransformAttackResult(id) => ujson.Obj("kind" -> "transform-attack-result", "handlerId" -> id)
    case PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy(id) => ujson.Obj("kind" -> "replace-losing-force-policy", "policyId" -> id)
    case PendingProcedure.CampaignPlanEffect.Suspend(kind) => ujson.Obj("kind" -> "suspend", "decisionKind" -> kind)
  }
  private def decodeCampaignPlanEffect(value: ujson.Value, path: String) = value("kind").str match {
    case "add-attack-dice" => Right(PendingProcedure.CampaignPlanEffect.AddAttackDice(value("count").num.toInt))
    case "add-defense-dice" => Right(PendingProcedure.CampaignPlanEffect.AddDefenseDice(value("count").num.toInt))
    case "ignore-attack-skulls" => Right(PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls)
    case "reveal-source" => Right(PendingProcedure.CampaignPlanEffect.RevealSource)
    case "transform-attack-result" => Right(PendingProcedure.CampaignPlanEffect.TransformAttackResult(value("handlerId").str))
    case "replace-losing-force-policy" => Right(PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy(value("policyId").str))
    case "suspend" => Right(PendingProcedure.CampaignPlanEffect.Suspend(value("decisionKind").str))
    case other => Left(InvalidValue(path, s"unknown Campaign plan effect '$other'"))
  }

  private def decodeWorldCard(value: ujson.Value, path: String)
      : Either[WireError, WorldCardId] = try value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case other => Left(InvalidValue(s"$path.kind", s"unknown world card kind '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid world card"))) }

  private def encodeSearchSource(source: SearchSource): ujson.Value = source match {
    case SearchSource.WorldDeck => ujson.Obj("kind" -> "world")
    case SearchSource.RegionalDiscard(region) =>
      ujson.Obj("kind" -> "regional-discard", "region" -> region.key)
  }

  private def decodeSearchSource(value: ujson.Value, path: String)
      : Either[WireError, SearchSource] = try value("kind").str match {
    case "world" => Right(SearchSource.WorldDeck)
    case "regional-discard" => decodeRegion(value("region").str, s"$path.region")
      .map(SearchSource.RegionalDiscard)
    case other => Left(InvalidValue(s"$path.kind", s"unknown Search source '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Search source"))) }

  private def decodeRegion(value: String, path: String): Either[WireError, Region] =
    Region.all.find(_.key == value).toRight(InvalidValue(path, s"unknown region '$value'"))

  private def decodeSuit(value: String, path: String): Either[WireError, Suit] =
    Suit.all.find(_.key == value).toRight(InvalidValue(path, s"unknown suit '$value'"))

  private def encodeCardRef(id: CardId): ujson.Value = id match {
    case value: DenizenId => encodeWorldCard(value)
    case value: VisionId => encodeWorldCard(value)
    case value: EdificeId => ujson.Obj("kind" -> "edifice", "id" -> value.value)
    case value: RelicId => ujson.Obj("kind" -> "relic", "id" -> value.value)
    case value: LegacyId => ujson.Obj("kind" -> "legacy", "id" -> value.value)
  }

  private def decodeCardRef(value: ujson.Value, path: String): Either[WireError, CardId] =
    try value("kind").str match {
      case "denizen" => Right(DenizenId(value("id").str))
      case "vision" => Right(VisionId(value("id").str))
      case "edifice" => Right(EdificeId(value("id").str))
      case "relic" => Right(RelicId(value("id").str))
      case "legacy" => Right(LegacyId(value("id").str))
      case other => Left(InvalidValue(s"$path.kind", s"unknown card kind '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid card reference"))) }

  private def decodeEconomyTarget(value: ujson.Value, path: String)
      : Either[WireError, EconomyTargetRef] =
    decodeCardRef(value, path).flatMap { id =>
      EconomyTargetRef.fromCard(id).toRight(InvalidValue(path,
        "Economy target must be a denizen or edifice"))
    }

  private def encodeSearchPlacement(value: SearchPlacement): ujson.Value = value match {
    case SearchPlacement.Discard => ujson.Obj("kind" -> "discard")
    case SearchPlacement.Site(replace) => ujson.Obj(
      "kind" -> "site", "replace" -> replace.fold[ujson.Value](ujson.Null)(encodeCardRef))
    case SearchPlacement.Adviser(orientation, replace) => ujson.Obj(
      "kind" -> "adviser",
      "orientation" -> (if (orientation == Orientation.FaceUp) "face-up" else "face-down"),
      "replace" -> replace.fold[ujson.Value](ujson.Null)(encodeCardRef))
  }

  private def decodeSearchPlacement(value: ujson.Value, path: String)
      : Either[WireError, SearchPlacement] = {
    def replacement: Either[WireError, Option[CardId]] = value("replace") match {
      case ujson.Null => Right(None)
      case card => decodeCardRef(card, s"$path.replace").map(Some(_))
    }
    try value("kind").str match {
      case "discard" => Right(SearchPlacement.Discard)
      case "site" => replacement.map(SearchPlacement.Site)
      case "adviser" => for {
        orientation <- value("orientation").str match {
          case "face-up" => Right(Orientation.FaceUp)
          case "face-down" => Right(Orientation.FaceDown)
          case other => Left(InvalidValue(s"$path.orientation", s"unknown orientation '$other'"))
        }
        replace <- replacement
      } yield SearchPlacement.Adviser(orientation, replace)
      case other => Left(InvalidValue(s"$path.kind", s"unknown placement '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Search placement"))) }
  }

  private def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
