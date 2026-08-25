package oathdigital.serialization

import oathdigital.application.{DecodedGameEvent, EventCodecFailure, GameEventCodec}
import oathdigital.gameplay.OathEvent
import oathdigital.model.CatalogRef

final class GameEventCodecAdapter extends GameEventCodec {
  def decodeStream(json: String) = GameEventWire.decodeStream(json)
    .left.map(toFailure)
    .map(_.map(value => DecodedGameEvent(value.gameId, value.sequence,
      value.eventType, value.event)))

  def encodeEvent(gameId: String, catalog: CatalogRef, sequence: Long,
      event: OathEvent) = GameEventWire.encodeEvent(gameId, catalog, sequence, event)
    .left.map(toFailure).map(ujson.write(_))

  private def toFailure(value: WireError): EventCodecFailure =
    EventCodecFailure(value.productPrefix, productPath(value), value.toString)

  private def productPath(value: Product): String =
    value.productIterator.collectFirst {
      case path: String if path.startsWith("$") => path
    }.getOrElse("$")
}
