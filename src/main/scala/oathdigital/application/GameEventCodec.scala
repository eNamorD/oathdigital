package oathdigital.application

import oathdigital.gameplay.OathEvent
import oathdigital.model.CatalogRef
import oathdigital.serialization.{GameEventEnvelope, GameEventWire, WireError}

/** Application-owned boundary around the persisted event representation. */
trait GameEventCodec {
  def decodeStream(json: String): Either[WireError, Vector[GameEventEnvelope]]
  def encodeEvent(gameId: String, catalog: CatalogRef, sequence: Long,
      event: OathEvent): Either[WireError, ujson.Value]
}

object GameEventCodec {
  val current: GameEventCodec = new GameEventCodec {
    def decodeStream(json: String) = GameEventWire.decodeStream(json)
    def encodeEvent(gameId: String, catalog: CatalogRef, sequence: Long,
        event: OathEvent) = GameEventWire.encodeEvent(gameId, catalog, sequence, event)
  }
}
