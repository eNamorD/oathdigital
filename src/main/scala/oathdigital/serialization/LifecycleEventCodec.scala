package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._

private[serialization] trait LifecycleEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val lifecycleDiscriminator: PartialFunction[OathEvent, String] = {
      case _: FirstGameStarted => FirstGameStartedType
      case _: GamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
      case _: WealthTaken => TakeWealthType
      case _: WakeEnded => WakeEndedType
      case _: RestStarted => RestStartedType
      case _: RestCompleted => RestCompletedType
  }

  protected final val lifecycleEncoder: PartialFunction[OathEvent, ujson.Value] = {
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
  }

  protected final def lifecycleDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef): Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
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
    }
    decoder.lift(eventType)
  }
}
