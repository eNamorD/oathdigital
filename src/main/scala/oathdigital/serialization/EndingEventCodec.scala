package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._

private[serialization] trait EndingEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val endingDiscriminator: PartialFunction[OathEvent, String] = {
      case _: RoundEnded => RoundEndedType
      case _: WarExhaustionResolved => WarExhaustionResolvedType
      case _: BanditsRefilled => BanditsRefilledType
      case _: OathkeeperChanged => OathkeeperChangedType
      case _: OathkeeperRecipientChoiceStarted =>
        OathkeeperRecipientChoiceStartedType
      case _: OathkeeperRecipientChosen => OathkeeperRecipientChosenType
      case _: UsurperFlipped => UsurperFlippedType
      case _: UsurperVictory => UsurperVictoryType
      case _: VisionVictory => VisionVictoryType
  }

  protected final val endingEncoder: PartialFunction[OathEvent, ujson.Value] = {
      case RoundEnded(completed, next) => ujson.Obj(
        "completedRound" -> completed,
        "nextRound" -> next.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
      case WarExhaustionResolved(winner, kind, vision, candidates) => ujson.Obj(
        "winnerPlayerId" -> winner.value,
        "victoryKind" -> kind.key,
        "visionId" -> vision.fold[ujson.Value](ujson.Null)(v => ujson.Str(v.value)),
        "randomCandidatePlayerIds" -> stringArray(candidates.map(_.value)))
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
      case VisionVictory(player, vision) => ujson.Obj(
        "playerId" -> player.value, "visionId" -> vision.value)
  }

  protected final def endingDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef): Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
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
        case VisionVictoryType => Right(VisionVictory(
          PlayerId(payload("playerId").str), VisionId(payload("visionId").str)))
    }
    decoder.lift(eventType)
  }
}
