package oathdigital.serialization

import oathdigital.model.CatalogRef

sealed trait WireError extends Product with Serializable {
  def path: String
  def message: String
}

object WireError {
  final case class MalformedJson(path: String, message: String) extends WireError
  final case class MissingField(path: String, message: String) extends WireError
  final case class WrongType(path: String, message: String) extends WireError
  final case class UnsupportedFormatVersion(path: String, actual: Int, supported: Int)
      extends WireError {
    override val message: String =
      s"unsupported format version $actual; supported version is $supported"
  }
  final case class UnknownEventType(path: String, eventType: String)
      extends WireError {
    override val message: String = s"unknown event type '$eventType'"
  }
  final case class InvalidValue(path: String, message: String) extends WireError
  final case class CatalogMismatch(path: String, expected: CatalogRef, actual: CatalogRef)
      extends WireError {
    override val message: String =
      s"catalog ${actual.ruleset}@${actual.version} does not match " +
        s"${expected.ruleset}@${expected.version}"
  }
  final case class InvalidSequence(path: String, expected: Long, actual: Long)
      extends WireError {
    override val message: String =
      s"expected contiguous sequence position $expected but found $actual"
  }
}

/** Carries a fully-typed [[WireError]] out of an encoder that genuinely
  * cannot express its input as data (I8) -- thrown only by
  * `WalkerEventCodec.encodeOperation`'s tree-control arms (`Decide`/
  * `BuildOps`/`Repeat`/`Branch`/`Sequence`: three close over a Scala
  * function value, and none can ever legally appear as an
  * ALREADY-APPLIED recorded operation -- see that method's doc). Every
  * other `CoreOperation`/`Piece` case is genuinely total: real data,
  * encoded and decoded exactly.
  *
  * `GameEventWire.encodePayloadSafe`'s `NonFatal` boundary special-cases
  * this exception and unwraps its carried `WireError` directly, instead of
  * falling through to the generic "$.payload" / stringified-message
  * fallback every OTHER encoder throw still gets -- so a genuinely
  * unencodable operation surfaces the same typed, specific error an
  * ordinary decode failure would, rather than an opaque append-time
  * exception message.
  */
private[serialization] final case class UnencodableOperation(error: WireError)
    extends RuntimeException(error.message)
