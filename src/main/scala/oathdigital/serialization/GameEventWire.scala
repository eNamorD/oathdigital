package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.engine.RecordedEvent
import oathdigital.model._
import oathdigital.gameplay._

final case class GameEventEnvelope(
    formatVersion: Int,
    gameId: String,
    sequence: Long,
    catalog: CatalogRef,
    eventType: String,
    event: OathEvent
)

/**
 * Explicit current game-event vocabulary, including first-game setup.
 */
object GameEventWire extends GameEventJsonSupport with LifecycleEventCodec
    with ActionEventCodec with CampaignEventCodec with EndingEventCodec
    with WalkerEventCodec {
  import WireError._

  /** The pre-release stream has one current format and no compatibility reader. */
  val FormatVersion: Int = 1
  val MaxSafeSequence: Long = 9007199254740991L
  val FirstGameStartedType = "setup.first-game-started"
  val PawnPlacedType = "setup.first-game-pawn-placed"
  val AdviserChosenType = "setup.starting-adviser-chosen"
  val FirstGameCompletedType = "setup.first-game-completed"
  val TakeWealthType = "gameplay.take-wealth"
  val WakeEndedType = "gameplay.wake-ended"
  val IgnoredRulesRecordedType = "diagnostic.ignored-rules-recorded"
  val TraveledType = "gameplay.traveled"
  val MusteredType = "gameplay.mustered"
  val TradedType = "gameplay.traded"
  val SearchStartedType = "gameplay.search-started"
  val SearchCompletedType = "gameplay.search-completed"
  val RestStartedType = "gameplay.rest-started"
  val LeagueTreatyDecisionStartedType =
    "gameplay.league-treaty-decision-started"
  val LeagueTreatyResolvedType = "gameplay.league-treaty-resolved"
  val LeagueTreatyDeclinedType = "gameplay.league-treaty-declined"
  val RestCompletedType = "gameplay.rest-completed"
  val RecoverRolledType = "gameplay.recover-rolled"
  val CatacombsResolvedType = "gameplay.catacombs-resolved"
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
  val ConspiracyCompletedType = "gameplay.conspiracy-completed"
  val VisionVictoryType = "gameplay.vision-victory"
  val RoundEndedType = "gameplay.round-ended"
  val WarExhaustionResolvedType = "gameplay.war-exhaustion-resolved"
  val WalkerStepRecordedType = "walker.step-recorded"
  val WalkerParkedType = "walker.parked"
  val WalkerCompletedType = "walker.completed"

  /** Encodes one event at its absolute position in the game stream. */
  def encodeEvent(
      gameId: String,
      catalog: CatalogRef,
      sequence: Long,
      event: OathEvent
  ): Either[WireError, ujson.Value] =
    encode(
      GameEventEnvelope(
        FormatVersion,
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
    validateEnvelope(envelope).flatMap { _ =>
      encodePayloadSafe(envelope.event).map { payload =>
        ujson.Obj(
          "formatVersion" -> envelope.formatVersion,
          "gameId" -> envelope.gameId,
          "sequence" -> ujson.Num(envelope.sequence.toDouble),
          "catalog" -> encodeCatalog(envelope.catalog),
          "eventType" -> envelope.eventType,
          "payload" -> payload
        )
      }
    }

  /** Encoders in this vocabulary are total for every event this build knows
    * how to construct, but `WalkerStepPayload`/`DeltaMeaning`/`CoreOperation`
    * are open (or bounded to a slice's current variants), so an unencodable
    * value reaching this append path must surface as a typed [[WireError]]
    * rather than escape as a raw exception (matches the decode side's
    * `NonFatal` boundary in `decodePayload`).
    */
  private def encodePayloadSafe(
      event: OathEvent
  ): Either[WireError, ujson.Value] =
    try Right(encodePayload(event))
    catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            "$.payload",
            Option(error.getMessage).getOrElse("invalid payload")
          )
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
            if (version == FormatVersion)
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
        if (envelope.formatVersion == FormatVersion) Right(())
        else
          Left(
            UnsupportedFormatVersion(
              "$.formatVersion",
              envelope.formatVersion,
              FormatVersion
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

  private val discriminatorDispatch = lifecycleDiscriminator
    .orElse(actionDiscriminator).orElse(campaignDiscriminator)
    .orElse(endingDiscriminator).orElse(walkerDiscriminator)

  private val encoderDispatch = lifecycleEncoder.orElse(actionEncoder)
    .orElse(campaignEncoder).orElse(endingEncoder).orElse(walkerEncoder)

  private def discriminator(event: OathEvent): String =
    discriminatorDispatch(event)

  private def encodePayload(event: OathEvent): ujson.Value =
    encoderDispatch(event)

  private def decodePayload(
      eventType: String,
      payload: ujson.Value,
      path: String,
      envelopeCatalog: CatalogRef
  ): Either[WireError, OathEvent] =
    try {
      lifecycleDecode(eventType, payload, path, envelopeCatalog)
        .orElse(actionDecode(eventType, payload, path, envelopeCatalog))
        .orElse(campaignDecode(eventType, payload, path, envelopeCatalog))
        .orElse(endingDecode(eventType, payload, path, envelopeCatalog))
        .orElse(walkerDecode(eventType, payload, path, envelopeCatalog))
        .getOrElse(Left(UnknownEventType(s"$path.eventType", eventType)))
    } catch {
      case NonFatal(error) => Left(InvalidValue(path,
        Option(error.getMessage).getOrElse("invalid payload")))
    }


}
