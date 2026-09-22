package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent._

private[serialization] trait LifecycleEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val lifecycleDiscriminator: PartialFunction[OathEvent, String] = {
      case _: GameStarted => GameStartedType
      case _: IgnoredRulesRecorded => IgnoredRulesRecordedType
  }

  protected final val lifecycleEncoder: PartialFunction[OathEvent, ujson.Value] = {
      case GameStarted(chronicle, orders) => ujson.Obj(
        "chronicle" -> encodeChronicle(chronicle),
        "orders" -> encodeSetupOrders(orders))
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
        case GameStartedType => for {
          chronicle <- decodeChronicle(payload("chronicle"), s"$path.chronicle")
          orders <- decodeSetupOrders(payload("orders"), s"$path.orders")
        } yield GameStarted(chronicle, orders)
        case IgnoredRulesRecordedType => for {
          action <- ActionKind.fromKey(payload("action").str).toRight(
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
