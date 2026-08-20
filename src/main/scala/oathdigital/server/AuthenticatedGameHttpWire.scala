package oathdigital.server

import scala.util.control.NonFatal

import oathdigital.application.{
  BootstrapParticipant,
  CardDecisionResolution,
  FirstGameBootstrapConfig
}
import oathdigital.model._
import oathdigital.serialization.GameEventWire
import oathdigital.setup.{PlayerColor, TradeResource, WakeResource}

sealed trait GameIntent extends Product with Serializable
object GameIntent {
  final case class PlacePawn(siteId: SiteId) extends GameIntent
  final case class ChooseAdviser(adviserId: DenizenId) extends GameIntent
  final case class TakeWealth(resource: WakeResource) extends GameIntent
  case object EndWake extends GameIntent
  case object BeginRest extends GameIntent
  case object FinishRest extends GameIntent
  final case class Travel(destinationSiteId: SiteId) extends GameIntent
  final case class Muster(target: EconomyTargetRef) extends GameIntent
  final case class Trade(target: EconomyTargetRef, resource: TradeResource)
      extends GameIntent
  final case class BeginSearch(source: SearchSource) extends GameIntent
  case object BeginRecover extends GameIntent
  final case class AddRecoverDice(decision: DecisionId) extends GameIntent
  final case class StopRecover(decision: DecisionId) extends GameIntent
  final case class BeginCampaignConquest(
      targetSiteIds: Vector[SiteId],
      attackDiceCount: Int
  ) extends GameIntent
  object BeginCampaignConquest {
    def apply(targetSiteId: SiteId, attackDiceCount: Int): BeginCampaignConquest =
      new BeginCampaignConquest(Vector(targetSiteId), attackDiceCount)
  }
  final case class ChooseCampaignPlan(
      decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource
  ) extends GameIntent
  final case class FinishCampaignPlans(decision: DecisionId) extends GameIntent
  final case class ChooseCampaignSacrifice(
      decision: DecisionId,
      count: Int
  ) extends GameIntent
  final case class PlaceCampaignForce(
      decision: DecisionId,
      allocations: Vector[CampaignForceAllocation]
  ) extends GameIntent
  final case class ChooseOathkeeperRecipient(
      decision: DecisionId, recipient: PlayerId) extends GameIntent
  final case class CompleteSearch(
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ) extends GameIntent
  final case class ResolveCardDecision(
      decision: DecisionId,
      resolution: CardDecisionResolution
  ) extends GameIntent
}

final case class AuthenticatedCommandRequest(
    expectedNextSequence: Long,
    intent: GameIntent
)

final case class AuthenticatedBootstrapRequest(
    expectedNextSequence: Long,
    config: FirstGameBootstrapConfig
)

object AuthenticatedGameHttpWire {
  def decodeBootstrap(
      json: String
  ): Either[HttpInputError, AuthenticatedBootstrapRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        _ <- exactFields(
          root,
          Set("expectedNextSequence", "participants", "firstPlayer"),
          "$"
        )
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        participantsValue <- field(root, "participants", "$")
        participantValues <- arrayValue(participantsValue, "$.participants")
        participants <- traverse(participantValues.zipWithIndex) {
          case (value, index) =>
            val path = s"$$.participants[$index]"
            for {
              obj <- objectValue(value, path)
              _ <- exactFields(
                obj,
                Set("playerId", "lineageId", "color"),
                path
              )
              playerId <- stringField(obj, "playerId", path)
              lineageId <- stringField(obj, "lineageId", path)
              color <- stringField(obj, "color", path)
            } yield BootstrapParticipant(
              PlayerId(playerId),
              LineageId(lineageId),
              PlayerColor(color)
            )
        }
        firstPlayer <- stringField(root, "firstPlayer", "$")
      } yield AuthenticatedBootstrapRequest(
        expected,
        FirstGameBootstrapConfig(participants, PlayerId(firstPlayer))
      )
    } catch {
      case NonFatal(error) => Left(HttpInputError(
        "$",
        Option(error.getMessage).getOrElse("malformed JSON")
      ))
    }

  def decodeCommand(
      json: String
  ): Either[HttpInputError, AuthenticatedCommandRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        _ <- exactFields(root, Set("expectedNextSequence", "intent"), "$")
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        intentValue <- field(root, "intent", "$")
        intentObject <- objectValue(intentValue, "$.intent")
        intent <- decodeIntent(intentObject)
      } yield AuthenticatedCommandRequest(expected, intent)
    } catch {
      case NonFatal(error) => Left(HttpInputError(
        "$",
        Option(error.getMessage).getOrElse("malformed JSON")
      ))
    }

  private def decodeIntent(
      obj: ujson.Obj
  ): Either[HttpInputError, GameIntent] =
    stringField(obj, "type", "$.intent").flatMap {
      case "placePawn" =>
        exactFields(obj, Set("type", "siteId"), "$.intent")
          .flatMap(_ => stringField(obj, "siteId", "$.intent"))
          .map(value => GameIntent.PlacePawn(SiteId(value)))
      case "chooseAdviser" =>
        Left(HttpInputError("$.intent.type", "use resolveCardDecision"))
      case "takeWealth" =>
        exactFields(obj, Set("type", "resource"), "$.intent")
          .flatMap(_ => stringField(obj, "resource", "$.intent"))
          .flatMap {
            case "favor" => Right(GameIntent.TakeWealth(WakeResource.Favor))
            case "secret" => Right(GameIntent.TakeWealth(WakeResource.Secret))
            case other => Left(HttpInputError(
              "$.intent.resource",
              s"unknown wealth resource '$other'"
            ))
          }
      case "endWake" =>
        exactFields(obj, Set("type"), "$.intent").map(_ =>
          GameIntent.EndWake)
      case "beginRest" =>
        exactFields(obj, Set("type"), "$.intent").map(_ => GameIntent.BeginRest)
      case "finishRest" =>
        exactFields(obj, Set("type"), "$.intent").map(_ => GameIntent.FinishRest)
      case "travel" =>
        exactFields(obj, Set("type", "destinationSiteId"), "$.intent")
          .flatMap(_ => stringField(obj, "destinationSiteId", "$.intent"))
          .map(value => GameIntent.Travel(SiteId(value)))
      case "muster" =>
        exactFields(obj, Set("type", "target"), "$.intent")
          .flatMap(_ => field(obj, "target", "$.intent"))
          .flatMap(value => decodeEconomyTarget(value, "$.intent.target"))
          .map(GameIntent.Muster)
      case "trade" =>
        for {
          _ <- exactFields(obj, Set("type", "target", "resource"), "$.intent")
          targetValue <- field(obj, "target", "$.intent")
          target <- decodeEconomyTarget(targetValue, "$.intent.target")
          value <- stringField(obj, "resource", "$.intent")
          resource <- value match {
            case "favor" => Right(TradeResource.Favor)
            case "secret" => Right(TradeResource.Secret)
            case other => Left(HttpInputError("$.intent.resource",
              s"unknown Trade resource '$other'"))
          }
        } yield GameIntent.Trade(target, resource)
      case "beginSearch" =>
        exactFields(obj, Set("type", "source", "region"), "$.intent")
          .flatMap(_ => stringField(obj, "source", "$.intent"))
          .flatMap {
            case "world" => Right(GameIntent.BeginSearch(SearchSource.WorldDeck))
            case "regional-discard" => stringField(obj, "region", "$.intent")
              .flatMap(value => Region.all.find(_.key == value).toRight(
                HttpInputError("$.intent.region", "unknown region")))
              .map(region => GameIntent.BeginSearch(
                SearchSource.RegionalDiscard(region)))
            case _ => Left(HttpInputError("$.intent.source", "unknown Search source"))
          }
      case "completeSearch" =>
        Left(HttpInputError("$.intent.type", "use resolveCardDecision"))
      case "beginRecover" =>
        exactFields(obj, Set("type"), "$.intent").map(_ => GameIntent.BeginRecover)
      case "addRecoverDice" =>
        exactFields(obj, Set("type", "decisionId"), "$.intent")
          .flatMap(_ => stringField(obj, "decisionId", "$.intent"))
          .map(id => GameIntent.AddRecoverDice(DecisionId(id)))
      case "stopRecover" =>
        exactFields(obj, Set("type", "decisionId"), "$.intent")
          .flatMap(_ => stringField(obj, "decisionId", "$.intent"))
          .map(id => GameIntent.StopRecover(DecisionId(id)))
      case "beginCampaignConquest" => for {
        _ <- exactFields(obj,
          Set("type", "targetSiteIds", "attackDiceCount"), "$.intent")
        sitesValue <- field(obj, "targetSiteIds", "$.intent")
        siteValues <- arrayValue(sitesValue, "$.intent.targetSiteIds")
        sites <- siteValues.zipWithIndex.foldLeft[
          Either[HttpInputError, Vector[SiteId]]](Right(Vector.empty)) {
            case (result, (value, index)) => result.flatMap(existing => value match {
              case ujson.Str(site) => Right(existing :+ SiteId(site))
              case _ => Left(HttpInputError(
                s"$$.intent.targetSiteIds[$index]", "expected a string"))
            })
          }
        countValue <- field(obj, "attackDiceCount", "$.intent")
        count <- nonNegativeInt(countValue, "$.intent.attackDiceCount")
      } yield GameIntent.BeginCampaignConquest(sites, count)
      case "chooseCampaignPlan" => for {
        _ <- exactFields(obj, Set("type", "decisionId", "source"), "$.intent")
        decision <- stringField(obj, "decisionId", "$.intent")
        sourceValue <- field(obj, "source", "$.intent")
        source <- decodeCampaignPlanSource(sourceValue, "$.intent.source")
        selected <- source.toRight(HttpInputError("$.intent.source", "plan source is required"))
      } yield GameIntent.ChooseCampaignPlan(DecisionId(decision), selected)
      case "finishCampaignPlans" => for {
        _ <- exactFields(obj, Set("type", "decisionId"), "$.intent")
        decision <- stringField(obj, "decisionId", "$.intent")
      } yield GameIntent.FinishCampaignPlans(DecisionId(decision))
      case "chooseCampaignSacrifice" => for {
        _ <- exactFields(obj, Set("type", "decisionId", "count"), "$.intent")
        decision <- stringField(obj, "decisionId", "$.intent")
        countValue <- field(obj, "count", "$.intent")
        count <- nonNegativeInt(countValue, "$.intent.count")
      } yield GameIntent.ChooseCampaignSacrifice(DecisionId(decision), count)
      case "placeCampaignForce" => for {
        _ <- exactFields(obj, Set("type", "decisionId", "allocations"), "$.intent")
        decision <- stringField(obj, "decisionId", "$.intent")
        values <- field(obj, "allocations", "$.intent").flatMap(
          arrayValue(_, "$.intent.allocations"))
        allocations <- values.zipWithIndex.foldLeft[
          Either[HttpInputError, Vector[CampaignForceAllocation]]](
          Right(Vector.empty)) { case (result, (value, index)) =>
            val itemPath = s"$$.intent.allocations[$index]"
            result.flatMap(existing => objectValue(value, itemPath).flatMap { item =>
              for {
                _ <- exactFields(item, Set("siteId", "count"), itemPath)
                site <- stringField(item, "siteId", itemPath)
                countValue <- field(item, "count", itemPath)
                count <- nonNegativeInt(countValue, s"$itemPath.count")
              } yield existing :+ CampaignForceAllocation(SiteId(site), count)
            })
          }
      } yield GameIntent.PlaceCampaignForce(DecisionId(decision), allocations)
      case "chooseOathkeeperRecipient" => for {
        _ <- exactFields(obj,
          Set("type", "decisionId", "recipientPlayerId"), "$.intent")
        decision <- stringField(obj, "decisionId", "$.intent")
        recipient <- stringField(obj, "recipientPlayerId", "$.intent")
      } yield GameIntent.ChooseOathkeeperRecipient(
        DecisionId(decision), PlayerId(recipient))
      case "resolveCardDecision" =>
        exactFields(obj, Set("type", "decisionId", "resolution"), "$.intent")
          .flatMap { _ => for {
            decision <- stringField(obj, "decisionId", "$.intent")
            value <- field(obj, "resolution", "$.intent")
            resolution <- decodeDecisionResolution(value, "$.intent.resolution")
          } yield GameIntent.ResolveCardDecision(DecisionId(decision), resolution) }
      case other => Left(HttpInputError(
        "$.intent.type",
        s"unknown intent type '$other'"
      ))
    }

  private def decodeDecisionResolution(value: ujson.Value, path: String)
      : Either[HttpInputError, CardDecisionResolution] = objectValue(value, path).flatMap { obj =>
    stringField(obj, "kind", path).flatMap {
      case "starting-adviser" => for {
        _ <- exactFields(obj, Set("kind", "adviserId"), path)
        id <- stringField(obj, "adviserId", path)
      } yield CardDecisionResolution.StartingAdviser(DenizenId(id))
      case "search" => for {
        _ <- exactFields(obj, Set("kind", "kept", "discardedInOrder", "placement"), path)
        keptValue <- field(obj, "kept", path)
        kept <- decodeWorldCard(keptValue, s"$path.kept")
        discardedValue <- field(obj, "discardedInOrder", path)
        discardedArray <- arrayValue(discardedValue, s"$path.discardedInOrder")
        discarded <- traverse(discardedArray.zipWithIndex) { case (card, index) =>
          decodeWorldCard(card, s"$path.discardedInOrder[$index]")
        }
        placementValue <- field(obj, "placement", path)
        placement <- decodePlacement(placementValue, s"$path.placement")
      } yield CardDecisionResolution.Search(kept, discarded, placement)
      case "take-facedown-relic" => for {
        _ <- exactFields(obj, Set("kind", "relicId"), path)
        id <- stringField(obj, "relicId", path)
      } yield CardDecisionResolution.TakeFacedownRelic(RelicId(id))
      case other => Left(HttpInputError(s"$path.kind", s"unknown decision resolution '$other'"))
    }
  }

  private def exactFields(
      obj: ujson.Obj,
      allowed: Set[String],
      path: String
  ): Either[HttpInputError, Unit] =
    obj.value.keys.find(key => !allowed.contains(key)) match {
      case Some(key) => Left(HttpInputError(
        s"$path.$key",
        "field is not accepted"
      ))
      case None => Right(())
    }

  private def decodeEconomyTarget(value: ujson.Value, path: String)
      : Either[HttpInputError, EconomyTargetRef] = for {
    obj <- objectValue(value, path)
    _ <- exactFields(obj, Set("kind", "id"), path)
    kind <- stringField(obj, "kind", path)
    id <- stringField(obj, "id", path)
    target <- kind match {
      case "denizen" => Right(EconomyTargetRef.Denizen(DenizenId(id)))
      case "edifice" => Right(EconomyTargetRef.Edifice(EdificeId(id)))
      case other => Left(HttpInputError(s"$path.kind",
        s"unsupported Economy target kind '$other'"))
    }
  } yield target

  private def decodeWorldCard(value: ujson.Value, path: String)
      : Either[HttpInputError, WorldCardId] = objectValue(value, path).flatMap { obj =>
    for {
      kind <- stringField(obj, "kind", path)
      id <- stringField(obj, "id", path)
      card <- kind match {
        case "denizen" => Right(DenizenId(id): WorldCardId)
        case "vision" => Right(VisionId(id): WorldCardId)
        case _ => Left(HttpInputError(s"$path.kind", "unknown world card kind"))
      }
    } yield card
  }

  private def decodeCampaignPlanSource(value: ujson.Value, path: String)
      : Either[HttpInputError, Option[PendingProcedure.CampaignPlanSource]] = value match {
    case ujson.Null => Right(None)
    case _ => objectValue(value, path).flatMap { obj =>
      stringField(obj, "kind", path).flatMap {
        case "adviser" => for {
          _ <- exactFields(obj, Set("kind", "playerId", "cardId"), path)
          player <- stringField(obj, "playerId", path)
          card <- stringField(obj, "cardId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.Adviser(
          PlayerId(player), DenizenId(card)))
        case "site-card" => for {
          _ <- exactFields(obj, Set("kind", "siteId", "cardId"), path)
          site <- stringField(obj, "siteId", path)
          card <- stringField(obj, "cardId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.SiteCard(
          SiteId(site), DenizenId(card)))
        case "relic" => for {
          _ <- exactFields(obj, Set("kind", "playerId", "cardId"), path)
          player <- stringField(obj, "playerId", path)
          card <- stringField(obj, "cardId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.Relic(
          PlayerId(player), RelicId(card)))
        case "title" => for {
          _ <- exactFields(obj, Set("kind", "playerId"), path)
          player <- stringField(obj, "playerId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.Title(PlayerId(player)))
        case other => Left(HttpInputError(s"$path.kind",
          s"unknown Campaign plan source '$other'"))
      }
    }
  }

  private def decodePlacement(value: ujson.Value, path: String)
      : Either[HttpInputError, SearchPlacement] = objectValue(value, path).flatMap { obj =>
    val replacement = obj.value.get("replace") match {
      case None | Some(ujson.Null) => Right(None)
      case Some(value) => decodeCard(value, s"$path.replace").map(Some(_))
    }
    stringField(obj, "kind", path).flatMap {
      case "discard" => Right(SearchPlacement.Discard)
      case "site" => replacement.map(SearchPlacement.Site)
      case "adviser-face-up" => replacement.map(SearchPlacement.Adviser(
        Orientation.FaceUp, _))
      case "adviser-face-down" => replacement.map(SearchPlacement.Adviser(
        Orientation.FaceDown, _))
      case _ => Left(HttpInputError(s"$path.kind", "unknown Search placement"))
    }
  }

  private def decodeCard(value: ujson.Value, path: String)
      : Either[HttpInputError, CardId] = objectValue(value, path).flatMap { obj =>
    for {
      kind <- stringField(obj, "kind", path)
      id <- stringField(obj, "id", path)
      card <- kind match {
        case "denizen" => Right(DenizenId(id): CardId)
        case "vision" => Right(VisionId(id): CardId)
        case "edifice" => Right(EdificeId(id): CardId)
        case _ => Left(HttpInputError(s"$path.kind", "unsupported replacement card kind"))
      }
    } yield card
  }

  private def safeSequence(value: ujson.Value, path: String) = value match {
    case ujson.Num(number)
        if number.isFinite && number == Math.rint(number) && number >= 0 &&
          number <= GameEventWire.MaxSafeSequence => Right(number.toLong)
    case _: ujson.Num => Left(HttpInputError(path, "expected a safe non-negative integer"))
    case _ => Left(HttpInputError(path, "expected a number"))
  }

  private def nonNegativeInt(value: ujson.Value, path: String)
      : Either[HttpInputError, Int] = value match {
    case ujson.Num(number) if number.isFinite && number == Math.rint(number) &&
        number >= 0 && number <= Int.MaxValue => Right(number.toInt)
    case _: ujson.Num => Left(HttpInputError(path,
      "expected a non-negative 32-bit integer"))
    case _ => Left(HttpInputError(path, "expected a number"))
  }

  private def stringField(obj: ujson.Obj, name: String, path: String) =
    field(obj, name, path).flatMap {
      case ujson.Str(value) if value.trim.nonEmpty => Right(value)
      case ujson.Str(_) => Left(HttpInputError(s"$path.$name", "must not be blank"))
      case _ => Left(HttpInputError(s"$path.$name", "expected a string"))
    }

  private def field(obj: ujson.Obj, name: String, path: String) =
    obj.value.get(name).toRight(HttpInputError(s"$path.$name", "field is required"))

  private def arrayValue(value: ujson.Value, path: String) = value match {
    case array: ujson.Arr => Right(array.value.toVector)
    case _ => Left(HttpInputError(path, "expected an array"))
  }

  private def traverse[A, B](values: Vector[A])(
      decode: A => Either[HttpInputError, B]
  ): Either[HttpInputError, Vector[B]] =
    values.foldLeft[Either[HttpInputError, Vector[B]]](Right(Vector.empty)) {
      case (Right(accumulated), value) =>
        decode(value).map(accumulated :+ _)
      case (failure @ Left(_), _) => failure
    }

  private def objectValue(value: ujson.Value, path: String) = value match {
    case obj: ujson.Obj => Right(obj)
    case _ => Left(HttpInputError(path, "expected an object"))
  }
}
