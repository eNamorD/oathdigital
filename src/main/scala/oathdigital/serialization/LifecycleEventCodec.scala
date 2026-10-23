package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent._

private[serialization] trait LifecycleEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._

  protected final val lifecycleDiscriminator: PartialFunction[OathEvent, String] =
      case _: GameStarted => GameStartedType

  protected final val lifecycleEncoder: PartialFunction[OathEvent, ujson.Value] =
      case GameStarted(chronicle, orders) => ujson.Obj(
        "chronicle" -> encodeChronicle(chronicle),
        "orders" -> encodeSetupOrders(orders))

  protected final def lifecycleDecode(eventType: String, payload: ujson.Value,
      path: String): Option[Either[WireError, OathEvent]] =
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] =
        case GameStartedType => for
          chronicle <- decodeChronicle(payload("chronicle"), s"$path.chronicle")
          orders <- decodeSetupOrders(payload("orders"), s"$path.orders")
        yield GameStarted(chronicle, orders)
    decoder.lift(eventType)

}
