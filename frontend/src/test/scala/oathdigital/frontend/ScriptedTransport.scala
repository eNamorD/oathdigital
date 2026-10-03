package oathdigital.frontend

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/** One request a test transport received. */
private[frontend] final case class RecordedRequest(method: String,
    url: String, body: Option[String])

/** Spy and stub of `JsonTransport`: records (method, url, body) of every
  * request, in order, into `requests`, then replies with whatever `script`
  * returns for it. The reply logic stays in the test that passes the script,
  * which can also close over its own counters. A test that wants to share
  * the record passes its own `requests`.
  */
private[frontend] final class ScriptedTransport(
    script: ScriptedTransport.Script,
    val requests: ArrayBuffer[RecordedRequest] = ArrayBuffer.empty
) extends JsonTransport:
  def request(method: String, url: String, body: Option[String])
      : Future[Either[GameClientFailure, TransportResponse]] =
    requests += RecordedRequest(method, url, body)
    script(method, url, body)

private[frontend] object ScriptedTransport:
  /** The reply to a request, from its method, URL and body. */
  type Script = (String, String, Option[String]) =>
    Future[Either[GameClientFailure, TransportResponse]]
