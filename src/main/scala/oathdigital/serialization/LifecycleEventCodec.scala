package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent._

private[serialization] trait LifecycleEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val lifecycleDiscriminator: PartialFunction[OathEvent, String] = {
      case _: FirstGameStarted => FirstGameStartedType
      case _: GamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
      case _: IgnoredRulesRecorded => IgnoredRulesRecordedType
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
      case IgnoredRulesRecorded(player, action, diagnostics) => ujson.Obj(
        "playerId" -> player.value,
        "action" -> action.key,
        "diagnostics" -> ujson.Arr.from(diagnostics.map(d => ujson.Obj(
          "source" -> d.source.stableKey,
          "handlerId" -> d.handlerId,
          "timing" -> d.timing.key,
          "reason" -> d.reason))))
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
        case IgnoredRulesRecordedType => for {
          action <- MajorActionKind.fromKey(payload("action").str).toRight(
            InvalidValue(s"$path.action", "unknown major action"))
          diagnostics <- payload("diagnostics").arr.toVector.foldLeft[
            Either[WireError, Vector[IgnoredRuleDiagnostic]]](Right(Vector.empty)) {
            case (Right(acc), value) => for {
              source <- RuleSourceRef.parse(value("source").str).toRight(
                InvalidValue(s"$path.diagnostics.source", "unknown rule source"))
              timing <- Vector(RuleTiming.Start, RuleTiming.Persistent,
                RuleTiming.Trigger, RuleTiming.BattlePlan, RuleTiming.Inherent)
                .find(_.key == value("timing").str).toRight(
                  InvalidValue(s"$path.diagnostics.timing", "unknown timing"))
            } yield acc :+ IgnoredRuleDiagnostic(source, value("handlerId").str,
              action, timing, value("reason").str)
            case (left @ Left(_), _) => left
          }
        } yield IgnoredRulesRecorded(PlayerId(payload("playerId").str), action,
          diagnostics)
    }
    decoder.lift(eventType)
  }

}
