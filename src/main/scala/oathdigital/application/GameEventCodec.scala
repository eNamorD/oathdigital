package oathdigital.application

import oathdigital.model.CatalogRef
import oathdigital.model.OathEvent

final case class EventCodecFailure(code: String, path: String, message: String)
    extends Product with Serializable

final case class DecodedGameEvent(
    gameId: String,
    sequence: Long,
    eventType: String,
    event: OathEvent
)

/** Application-owned persisted-event boundary with representation-free values. */
trait GameEventCodec {
  def decodeStream(json: String): Either[EventCodecFailure, Vector[DecodedGameEvent]]
  def encodeEvent(gameId: String, catalog: CatalogRef, sequence: Long,
      event: OathEvent): Either[EventCodecFailure, String]
}

object GameEventCodec {
  lazy val default: GameEventCodec = {
    val providers = java.util.ServiceLoader.load(classOf[GameEventCodec]).iterator()
    if (providers.hasNext) providers.next()
    else throw new IllegalStateException("no GameEventCodec adapter is installed")
  }
}
